from __future__ import annotations

import os
import sys
import time
import json
import datetime
import subprocess
from typing_extensions import Annotated
import typer

from amocna_cli.config import ProjectConfig
from amocna_cli.commands.version import read_pom_version
from amocna_cli.utils.ui import console, header, info, error, run, run_capture
from amocna_cli.utils.shell import (
    LOCUST_SWARM_PYTHON_TEMPLATE,
    LOCUST_STOP_PYTHON_TEMPLATE,
    LOCUST_STATS_PYTHON_TEMPLATE,
    ORDERS_CPU_RESET_PATCH,
    k8s_run_pod,
    k8s_exec,
    k8s_scale,
    k8s_patch,
    k8s_set_image,
    k8s_rollout_restart,
    k8s_get_jsonpath,
    k8s_get_pods_jsonpath,
    k8s_delete_resource,
)

app = typer.Typer(no_args_is_help=True)

# Sock-shop front-end images (explicit docker.io avoids quay.io redirect / auth failures)
SOCK_SHOP_FRONTEND_IMAGE = "docker.io/weaveworksdemos/front-end"
SOCK_SHOP_FRONTEND_VULNERABLE_TAG = "0.3.0"
SOCK_SHOP_FRONTEND_PATCHED_TAG = "0.3.12"

S6_NAMESPACE = "sock-shop"
S6_SECRET_NAME = "regcred"
S6_SIBLING_DEPLOY = "s6-sibling"
S6_FAILING_DEPLOY = "s6-failing"
S6_PRIVATE_IMAGE_APP = "s7-private-demo"
S6_WORKLOADS_MANIFEST = "s6-registry-credential-workloads.yaml"

# ─── Benchmark helpers ─────────────────────────────────────────────


def _load_sparql_query(cfg: ProjectConfig, filename: str) -> str:
    """Dynamically load SPARQL query template from cli resources directory."""
    path = cfg.project_root / "cli" / "resources" / "sparql" / filename
    if not path.is_file():
        error(f"SPARQL template not found: {path}")
        sys.exit(1)
    return path.read_text().strip()


def run_sparql(cfg: ProjectConfig, update_query: str) -> None:
    """Execute a SPARQL Update query inside GraphDB using a temporary pod in K8s."""
    # Use sh -c to allow piping the query via stdin to curl, avoiding shell escaping hell with large queries
    curl_cmd = [
        "sh",
        "-c",
        "curl -s -X POST http://graphdb.graphdb.svc.cluster.local:7200/repositories/amocna/statements "
        "-H 'Content-Type: application/sparql-update' --data-binary @-",
    ]
    # We use subprocess.run directly here to manage the input stream
    console.print(f"  [dim]$ (sparql-update via pod) [/dim]")
    pod_cmd = k8s_run_pod("amocna-sparql-trigger", "curlimages/curl:8.12.1", curl_cmd)
    subprocess.run(pod_cmd, input=update_query, text=True, check=True)


def set_locust_load(users: int, rate: int) -> None:
    """Control Locust swarming programmatically."""
    info(f"Setting Locust traffic to {users} users (spawn rate {rate})...")
    python_snippet = LOCUST_SWARM_PYTHON_TEMPLATE.format(users=users, rate=rate)
    run(
        k8s_exec("sock-shop", "deploy/locust-master", ["python3", "-c", python_snippet])
    )


def stop_locust() -> None:
    """Stop Locust traffic swarming."""
    info("Stopping Locust traffic...")
    run(
        k8s_exec(
            "sock-shop",
            "deploy/locust-master",
            ["python3", "-c", LOCUST_STOP_PYTHON_TEMPLATE],
        )
    )


def get_locust_stats() -> None:
    """Get active load statistics from Locust."""
    run(
        k8s_exec(
            "sock-shop",
            "deploy/locust-master",
            ["python3", "-c", LOCUST_STATS_PYTHON_TEMPLATE],
        )
    )


class EventLogger:
    def __init__(self, scenario_id: str):
        self.scenario_id = scenario_id
        self.start_time = time.time()
        self.start_iso = datetime.datetime.now().isoformat()
        self.events = []
        self.log_file = f"benchmark_log_{scenario_id}_{int(self.start_time)}.json"

    def log(self, event_type: str, description: str):
        elapsed = time.time() - self.start_time
        timestamp = datetime.datetime.now().strftime("%H:%M:%S")
        self.events.append(
            {
                "timestamp_s": round(elapsed, 2),
                "wall_clock": timestamp,
                "type": event_type,
                "description": description,
            }
        )
        # Also print to console with elapsed time prefix
        console.print(
            f"[[bold cyan]{elapsed:06.1f}s[/bold cyan]] [bold]{event_type}[/bold]: {description}"
        )

    def save(self):
        data = {
            "scenario_id": self.scenario_id,
            "start_iso": self.start_iso,
            "total_duration": round(time.time() - self.start_time, 2),
            "events": self.events,
        }
        with open(self.log_file, "w") as f:
            json.dump(data, f, indent=2)
        info(f"Event log saved to {self.log_file}")
        # Also print to console with elapsed time prefix


def _load_k8s_manifest(cfg: ProjectConfig, filename: str, **replacements: str) -> str:
    """Load a K8s manifest template from cli/resources/k8s and substitute placeholders."""
    path = cfg.project_root / "cli" / "resources" / "k8s" / filename
    if not path.is_file():
        error(f"K8s manifest template not found: {path}")
        sys.exit(1)
    text = path.read_text()
    for key, value in replacements.items():
        text = text.replace("{" + key + "}", value)
    return text


def _require_registry_credentials() -> tuple[str, str]:
    """Return (username, PAT) from the environment or exit."""
    user = os.environ.get("AMOCNA_USER")
    pat = os.environ.get("AMOCNA_PAT")
    if not user:
        error(
            "AMOCNA_USER is not set. Export your GitHub username: export AMOCNA_USER=..."
        )
        sys.exit(1)
    if not pat:
        error("AMOCNA_PAT is not set. Export your GitHub PAT: export AMOCNA_PAT=...")
        sys.exit(1)
    return user, pat


def _s7_private_image(cfg: ProjectConfig) -> str:
    version = read_pom_version(cfg.project_root / cfg.parent_pom)
    return f"{cfg.registry}/{S6_PRIVATE_IMAGE_APP}:{version}"


def _s7_expected_version(cfg: ProjectConfig) -> str:
    return read_pom_version(cfg.project_root / cfg.parent_pom)


def _s7_deployment_image(namespace: str, deployment: str) -> str:
    return run_capture(
        k8s_get_jsonpath(
            namespace,
            "deployment",
            deployment,
            "{.spec.template.spec.containers[0].image}",
        ),
        check=False,
    )


def _s7_check_runtime_prerequisites(cfg: ProjectConfig) -> bool:
    """Verify control-loop services run the expected version before Scenario 7."""
    expected = _s7_expected_version(cfg)
    services = [
        ("metis", "metis"),
        ("palamedes", "palamedes"),
        ("themis", "themis"),
    ]
    mismatches: list[str] = []
    for namespace, deployment in services:
        image = _s7_deployment_image(namespace, deployment)
        if not image:
            mismatches.append(f"{deployment}: deployment/image not found")
            continue
        if f":{expected}" not in image:
            mismatches.append(
                f"{deployment}: running {image} (expected tag :{expected})"
            )
    if mismatches:
        error("Scenario 6 preflight failed: control-loop images are out of sync.")
        for mismatch in mismatches:
            console.print(f"    - {mismatch}")
        console.print(
            "    Hint: rebuild/push and redeploy metis, palamedes, themis to the same project version."
        )
        return False
    return True


def _s7_print_timeout_diagnostics() -> None:
    """Print concise diagnostics to explain why Scenario 6 may be stuck."""
    info("Collecting Scenario 6 diagnostics...")
    pod = run_capture(
        k8s_get_pods_jsonpath(
            S6_NAMESPACE, "app=s6-failing", "{.items[0].metadata.name}"
        ),
        check=False,
    )
    if pod:
        reason = run_capture(
            [
                "kubectl",
                "get",
                "pod",
                pod,
                "-n",
                S6_NAMESPACE,
                "-o=jsonpath={.status.containerStatuses[0].state.waiting.reason}",
            ],
            check=False,
        )
        msg = run_capture(
            [
                "kubectl",
                "get",
                "pod",
                pod,
                "-n",
                S6_NAMESPACE,
                "-o=jsonpath={.status.containerStatuses[0].state.waiting.message}",
            ],
            check=False,
        )
        console.print(f"    failing pod: {pod}")
        console.print(f"    pull reason: {reason or '(unknown)'}")
        if msg:
            console.print(f"    pull message: {msg}")

    dep_secret = run_capture(
        k8s_get_jsonpath(
            S6_NAMESPACE,
            "deployment",
            S6_FAILING_DEPLOY,
            "{.spec.template.spec.imagePullSecrets[0].name}",
        ),
        check=False,
    )
    console.print(f"    deployment imagePullSecret: {dep_secret or '(none)'}")

    pal_logs = run_capture(
        ["kubectl", "logs", "-n", "palamedes", "deployment/palamedes", "--since=5m"],
        check=False,
    )
    markers = [
        "Planned imagePullSecret patch",
        "AddImagePullSecretIntent",
        "RegistryCredentialPlanner",
    ]
    found = [m for m in markers if m in pal_logs]
    if found:
        console.print(f"    palamedes markers found: {', '.join(found)}")
    else:
        console.print("    palamedes markers found: none")


def _s7_wait_for_sibling_running(
    logger: EventLogger, timeout_seconds: int = 240
) -> bool:
    """Wait until sibling pod is Running so Palamedes can infer a working pull secret."""
    logger.log(
        "WAIT_SIBLING",
        f"Waiting for sibling workload to become Running (timeout {timeout_seconds}s)",
    )
    checks = max(1, timeout_seconds // 5)
    for i in range(checks):
        phase = run_capture(
            k8s_get_pods_jsonpath(
                S6_NAMESPACE, "app=s6-sibling", "{.items[0].status.phase}"
            ),
            check=False,
        )
        if phase == "Running":
            logger.log("SIBLING_READY", f"Sibling pod is Running after {i * 5}s")
            return True
        console.print(
            f"    Waiting for sibling to run (phase={phase or 'unknown'})... ({i * 5}s)"
        )
        time.sleep(5)
    return False


def _create_docker_registry_secret(
    namespace: str, name: str, username: str, password: str
) -> None:
    """Create or update a docker-registry pull secret (ghcr.io)."""
    create = subprocess.run(
        [
            "kubectl",
            "create",
            "secret",
            "docker-registry",
            name,
            "-n",
            namespace,
            "--docker-server=ghcr.io",
            f"--docker-username={username}",
            f"--docker-password={password}",
            "--dry-run=client",
            "-o",
            "yaml",
        ],
        capture_output=True,
        text=True,
        check=True,
    )
    subprocess.run(
        ["kubectl", "apply", "-f", "-"],
        input=create.stdout,
        text=True,
        check=True,
    )


def _apply_manifest_stdin(manifest: str) -> None:
    subprocess.run(
        ["kubectl", "apply", "-f", "-"],
        input=manifest,
        text=True,
        check=True,
    )


def _cleanup_s7_benchmark(cfg: ProjectConfig) -> None:
    """Remove Scenario 6 workloads, pull secret, and related GraphDB triples."""
    for deploy in (S6_FAILING_DEPLOY, S6_SIBLING_DEPLOY):
        run(
            k8s_delete_resource("deployment", deploy, namespace=S6_NAMESPACE),
            check=False,
        )
    run(
        k8s_delete_resource("secret", S6_SECRET_NAME, namespace=S6_NAMESPACE),
        check=False,
    )
    run(
        k8s_delete_resource("serviceaccount", "s6-sibling-sa", namespace=S6_NAMESPACE),
        check=False,
    )
    run(
        k8s_delete_resource("serviceaccount", "s6-failing-sa", namespace=S6_NAMESPACE),
        check=False,
    )
    clean_s7 = _load_sparql_query(cfg, "clean-s6.sparql")
    run_sparql(cfg, clean_s7)


def _poke_s7_failing_pod() -> None:
    """Trigger a pod update so Metis re-asserts ImagePullBackOffState after phase sensing."""
    pods_raw = run_capture(
        k8s_get_pods_jsonpath(
            S6_NAMESPACE,
            "app=s6-failing",
            "{range .items[*]}{.metadata.name}|{.metadata.deletionTimestamp}{'\\n'}{end}",
        ),
        check=False,
    )
    pod_name = None
    for line in (pods_raw or "").splitlines():
        if not line.strip():
            continue
        parts = line.split("|")
        name = parts[0] if len(parts) > 0 else ""
        deletion_ts = parts[1] if len(parts) > 1 else ""
        deletion_ts = deletion_ts.strip()
        if not deletion_ts:
            pod_name = name
            break
    if pod_name is None:
        # Fallback to first available pod if all are terminating.
        pod_name = run_capture(
            k8s_get_pods_jsonpath(
                S6_NAMESPACE, "app=s6-failing", "{.items[0].metadata.name}"
            ),
            check=False,
        )
    if not pod_name:
        return
    run(
        [
            "kubectl",
            "annotate",
            "pod",
            pod_name,
            "-n",
            S6_NAMESPACE,
            f"amocna.benchmark/s6-poke={int(time.time())}",
            "--overwrite",
        ],
        check=False,
    )


def _s7_failing_pod_snapshots() -> list[tuple[str, str, str, bool]]:
    """
    Return [(podName, phase, waitingReason, terminating), ...] for app=s6-failing.
    """
    raw = run_capture(
        k8s_get_pods_jsonpath(
            S6_NAMESPACE,
            "app=s6-failing",
            "{range .items[*]}{.metadata.name}|{.status.phase}|{.status.containerStatuses[0].state.waiting.reason}|{.metadata.deletionTimestamp}{'\\n'}{end}",
        ),
        check=False,
    )
    snapshots: list[tuple[str, str, str, bool]] = []
    for line in (raw or "").splitlines():
        if not line.strip():
            continue
        parts = line.split("|")
        name = parts[0] if len(parts) > 0 else ""
        phase = parts[1] if len(parts) > 1 else ""
        reason = parts[2] if len(parts) > 2 else ""
        deletion_ts = parts[3].strip() if len(parts) > 3 else ""
        snapshots.append((name, phase or "", reason or "", bool(deletion_ts)))
    return snapshots


# ─── Benchmark Subcommands ─────────────────────────────────────────


@app.command("status")
def benchmark_status(ctx: typer.Context):
    """Get current workload and cluster scaling status."""
    cfg: ProjectConfig = ctx.obj
    header("AMoCNA Benchmark Status")

    # 1. Pod replicas in sock-shop
    console.print(f"\n  [bold]Sock Shop Deployments:[/bold]")
    for deploy in ["front-end", "orders", "carts"]:
        try:
            replicas = run_capture(
                k8s_get_jsonpath(
                    "sock-shop",
                    "deployment",
                    deploy,
                    "{.spec.replicas}/{.status.readyReplicas}",
                ),
                check=False,
            )
            console.print(f"    {deploy:<15} : {replicas} replicas ready")
        except Exception:
            console.print(f"    {deploy:<15} : Not found or unreachable")

    # 2. Stress replica status
    console.print(f"\n  [bold]Load Stress Status:[/bold]")
    try:
        stress_replicas = run_capture(
            k8s_get_jsonpath(
                "default", "deployment", "cluster-stress", "{.spec.replicas}"
            ),
            check=False,
        )
        console.print(f"    cluster-stress  : {stress_replicas} replicas running")
    except Exception:
        console.print("    cluster-stress  : Not deployed")

    # 3. Active Locust stats
    console.print(f"\n  [bold]Locust Load Statistics:[/bold]")
    get_locust_stats()
    console.print()


@app.command("stop")
def benchmark_stop(ctx: typer.Context):
    """Instantly stop all load tests and tear down stress."""
    cfg: ProjectConfig = ctx.obj
    header("Stopping All Benchmark Elements")
    stop_locust()

    info("Scaling down cluster-stress to 0...")
    run(k8s_scale("default", "cluster-stress", 0), check=False)

    info("Scaling down front-end to 1...")
    run(k8s_scale("sock-shop", "front-end", 1), check=False)

    info("Resetting orders CPU requests and scaling to 1 replica...")
    run(k8s_patch("sock-shop", "orders", ORDERS_CPU_RESET_PATCH), check=False)
    run(k8s_scale("sock-shop", "orders", 1), check=False)

    # Clean up ConfigDriftState / SecurityVulnerabilityDetectedState if any
    info("Cleaning up GraphDB anomaly states...")
    clean_query = _load_sparql_query(cfg, "clean-anomalies.sparql")
    run_sparql(cfg, clean_query)

    # Restore RestartPodIntent instruction
    restore_query = _load_sparql_query(cfg, "restore-restart.sparql")
    run_sparql(cfg, restore_query)

    info("Deleting temporary benchmark ConfigMaps...")
    run(
        k8s_delete_resource("configmap", "orders-config", namespace="sock-shop"),
        check=False,
    )

    info("Removing Scenario 7 benchmark workloads and graph triples...")
    _cleanup_s7_benchmark(cfg)

    info("System successfully returned to baseline.")
    console.print()


@app.command("load")
def benchmark_load(
    ctx: typer.Context,
    users: Annotated[int, typer.Option(help="Number of concurrent users")] = 1000,
    rate: Annotated[int, typer.Option(help="User spawn rate per second")] = 10,
):
    """Directly generate load using Locust."""
    header(f"Starting Custom Load: {users} users")
    set_locust_load(users, rate)
    console.print()


@app.command("run")
def benchmark_run(
    ctx: typer.Context,
    scenario: Annotated[
        str, typer.Option(help="Scenario to run (1, 2, 3, 4, 5, 6, 7, all)")
    ],
    keep_on_failure: Annotated[
        bool,
        typer.Option(
            "--keep-on-failure",
            help="Keep scenario resources for debugging when a scenario fails.",
        ),
    ] = False,
):
    """Run a specific scenario or all benchmarks."""
    cfg: ProjectConfig = ctx.obj
    header(f"Running AMoCNA Benchmark: Scenario {scenario}")

    if scenario == "1":
        logger = EventLogger("1")
        logger.log("START_BASELINE", "Locust: 200 users, Cluster-Stress: 1 replica")
        set_locust_load(200, 10)
        run(k8s_scale("default", "cluster-stress", 1), check=False)
        run(k8s_scale("sock-shop", "front-end", 1), check=False)

        # Phase 1: Baseline (120s)
        time.sleep(120)

        # Phase 2: Trigger (T+120s)
        logger.log("TRIGGER_ANOMALY", "Spiking Locust to 1800 users")
        set_locust_load(1800, 50)

        # Phase 3: Observation (360s)
        start_obs = time.time()
        remediation_detected = False
        while time.time() - start_obs < 360:
            replicas = run_capture(
                k8s_get_jsonpath(
                    "sock-shop", "deployment", "front-end", "{.status.readyReplicas}"
                ),
                check=False,
            )
            if replicas == "3" and not remediation_detected:
                logger.log(
                    "REMEDIATION_DETECTED",
                    f"Frontend successfully scaled to 3 replicas in {time.time() - start_obs:.1f}s",
                )
                logger.log(
                    "TRAFFIC_REBALANCE",
                    "Restarting Locust workers to balance traffic across new replicas...",
                )
                run(
                    k8s_rollout_restart(
                        "deployment/locust-worker", namespace="sock-shop"
                    ),
                    check=False,
                )

                # Wait for workers to be ready and master to see them
                logger.log("WAIT_WORKERS", "Waiting 30s for workers to reconnect...")
                time.sleep(30)

                logger.log(
                    "RESUME_TRAFFIC",
                    "Re-triggering Locust swarm (1800 users, 50 rate)...",
                )
                set_locust_load(1800, 50)

                remediation_detected = True
            time.sleep(10)

        # Phase 4: Stabilization (120s)
        logger.log("STABILIZATION", "Monitoring post-fix stability for 120s")
        time.sleep(120)

        # Phase 5: Scale Down Trigger
        logger.log(
            "SCALE_DOWN_TRIGGER",
            "Reducing Locust back to 200 users to trigger scale down",
        )
        set_locust_load(200, 10)

        # Phase 6: Scale Down Observation (360s)
        logger.log(
            "SCALE_DOWN_OBSERVATION", "Monitoring scale down to 1 replica for 360s"
        )
        start_scale_down_obs = time.time()
        scale_down_detected = False
        while time.time() - start_scale_down_obs < 360:
            replicas = run_capture(
                k8s_get_jsonpath(
                    "sock-shop", "deployment", "front-end", "{.status.readyReplicas}"
                ),
                check=False,
            )
            if replicas == "1" and not scale_down_detected:
                logger.log(
                    "SCALE_DOWN_DETECTED",
                    f"Frontend successfully scaled back to 1 replica in {time.time() - start_scale_down_obs:.1f}s",
                )
                scale_down_detected = True
            time.sleep(10)

        logger.log("END_SCENARIO", "Scenario 1 completed")
        logger.save()
        stop_locust()
        run(k8s_scale("default", "cluster-stress", 0), check=False)

    elif scenario == "2":
        logger = EventLogger("2")
        logger.log(
            "START_BASELINE",
            "Locust: 200 users, Cluster-Stress: 1 replica, Orders: 1 replica",
        )
        set_locust_load(200, 10)
        run(k8s_scale("default", "cluster-stress", 1), check=False)
        run(k8s_scale("sock-shop", "orders", 1), check=False)
        run(k8s_patch("sock-shop", "orders", ORDERS_CPU_RESET_PATCH), check=False)

        # Phase 1: Baseline (120s)
        time.sleep(120)

        # Find the node where orders is running
        orders_node = (
            run_capture(
                [
                    "kubectl",
                    "get",
                    "pods",
                    "-n",
                    "sock-shop",
                    "-l",
                    "name=orders",
                    "-o",
                    "jsonpath={.items[0].spec.nodeName}",
                ],
                check=False,
            )
            .strip()
            .replace("'", "")
        )
        info(f"Orders pod is running on node: {orders_node}")

        # Prepare patches
        stress_patch = json.dumps(
            {
                "spec": {
                    "template": {
                        "spec": {
                            "nodeSelector": {"kubernetes.io/hostname": orders_node},
                            "containers": [
                                {
                                    "name": "stress",
                                    "args": ["--cpu", "2", "--timeout", "600s"],
                                    "resources": {
                                        "requests": {"cpu": "100m", "memory": "200Mi"},
                                        "limits": {"cpu": "1800m", "memory": "3000Mi"},
                                    },
                                }
                            ],
                        }
                    }
                }
            }
        )

        # Phase 2: Trigger (T+120s)
        logger.log(
            "TRIGGER_ANOMALY",
            f"Patching cluster-stress to node {orders_node}, scaling to 3 replicas, and spiking Locust load to 1500 users to generate pressure",
        )
        run(k8s_patch("default", "cluster-stress", stress_patch), check=False)
        run(k8s_scale("default", "cluster-stress", 3))
        set_locust_load(1500, 30)

        # Phase 3: Observation (360s)
        start_obs = time.time()
        remediation_detected = False
        while time.time() - start_obs < 360:
            cpu = run_capture(
                k8s_get_jsonpath(
                    "sock-shop",
                    "deployment",
                    "orders",
                    "{.spec.template.spec.containers[0].resources.requests.cpu}",
                ),
                check=False,
            )
            if cpu == "1" and not remediation_detected:
                logger.log(
                    "REMEDIATION_DETECTED",
                    f"Orders CPU requests autonomically patched to 1 in {time.time() - start_obs:.1f}s",
                )
                logger.log(
                    "TRAFFIC_REBALANCE",
                    "Restarting Locust workers to balance traffic across new pods...",
                )
                run(
                    k8s_rollout_restart(
                        "deployment/locust-worker", namespace="sock-shop"
                    ),
                    check=False,
                )

                # Wait for workers to be ready and master to see them
                logger.log("WAIT_WORKERS", "Waiting 30s for workers to reconnect...")
                time.sleep(30)

                logger.log(
                    "RESUME_TRAFFIC",
                    "Re-triggering Locust swarm (1500 users, 30 rate)...",
                )
                set_locust_load(1500, 30)

                remediation_detected = True
            time.sleep(10)

        # Phase 4: Stabilization (120s)
        logger.log("STABILIZATION", "Monitoring post-fix stability for 120s")
        time.sleep(120)

        logger.log("END_SCENARIO", "Scenario 2 completed")
        logger.save()
        stop_locust()

        # Cleanup
        run(k8s_scale("default", "cluster-stress", 0), check=False)
        run(k8s_scale("sock-shop", "orders", 1), check=False)

        # Restore cluster-stress settings
        stress_restore = json.dumps(
            {
                "spec": {
                    "template": {
                        "spec": {
                            "nodeSelector": {"kubernetes.io/hostname": "kube-worker-3"},
                            "containers": [
                                {
                                    "name": "stress",
                                    "args": [
                                        "--cpu",
                                        "2",
                                        "--io",
                                        "1",
                                        "--vm",
                                        "1",
                                        "--vm-bytes",
                                        "2350M",
                                        "--timeout",
                                        "600s",
                                    ],
                                    "resources": {
                                        "requests": {
                                            "cpu": "1200m",
                                            "memory": "2400Mi",
                                        },
                                        "limits": {"cpu": "1200m", "memory": "2450Mi"},
                                    },
                                }
                            ],
                        }
                    }
                }
            }
        )
        run(k8s_patch("default", "cluster-stress", stress_restore), check=False)
        run(k8s_patch("sock-shop", "orders", ORDERS_CPU_RESET_PATCH), check=False)

    elif scenario == "3":
        logger = EventLogger("3")
        logger.log("START_BASELINE", "Locust: 500 users, Cluster-Stress: 1 replica")
        set_locust_load(
            50, 10
        )  # Using 50 to avoid cluster exhaustion in image update scenario
        run(k8s_scale("default", "cluster-stress", 1), check=False)

        pod_name = run_capture(
            k8s_get_pods_jsonpath(
                "sock-shop", "name=front-end", "{.items[0].metadata.name}"
            )
        )
        if not pod_name:
            error("✖ ERROR: Active front-end pod not found. Aborting.")
            return

        # Phase 1: Baseline (120s)
        time.sleep(120)

        # Phase 2: Trigger (T+120s)
        logger.log(
            "TRIGGER_ANOMALY", f"Injecting Security Vulnerability for pod {pod_name}"
        )
        vulnerability_query = _load_sparql_query(
            cfg, "inject-vulnerability.sparql"
        ).replace("{pod_name}", pod_name)
        run_sparql(cfg, vulnerability_query)

        # Phase 3: Observation (360s)
        start_obs = time.time()
        remediation_detected = False
        while time.time() - start_obs < 360:
            image = run_capture(
                k8s_get_jsonpath(
                    "sock-shop",
                    "deployment",
                    "front-end",
                    "{.spec.template.spec.containers[0].image}",
                ),
                check=False,
            )
            if SOCK_SHOP_FRONTEND_PATCHED_TAG in image and not remediation_detected:
                logger.log(
                    "REMEDIATION_DETECTED",
                    f"Frontend successfully updated to patched version ({SOCK_SHOP_FRONTEND_PATCHED_TAG}) in {time.time() - start_obs:.1f}s",
                )
                remediation_detected = True
            time.sleep(10)

        # Phase 4: Stabilization (120s)
        logger.log("STABILIZATION", "Monitoring post-fix stability for 120s")
        time.sleep(120)

        logger.log("END_SCENARIO", "Scenario 3 completed")
        logger.save()
        run(k8s_scale("default", "cluster-stress", 0), check=False)

    elif scenario == "4":
        logger = EventLogger("4")
        header("Scenario 4: Multi-Step Saga Remediation (Red Path Rollback)")

        # Phase 0: Initialization (Ensure clean state)
        logger.log("INIT", "Cleaning up stale resources and states...")
        run(k8s_delete_resource("configmap", "orders-config", namespace="sock-shop"), check=False)
        run(k8s_scale("sock-shop", "orders", 1), check=False)
        run_sparql(cfg, _load_sparql_query(cfg, "clean-anomalies.sparql"))
        run_sparql(cfg, _load_sparql_query(cfg, "clean-actions.sparql"))
        run_sparql(cfg, _load_sparql_query(cfg, "restore-restart.sparql"))
        time.sleep(15) # Give Metis/Palamedes time to sync

        logger.log("START_BASELINE", "Locust: 50 users, Cluster-Stress: 1 replica")
        set_locust_load(50, 5)  # Low load for Saga rollback test
        run(k8s_scale("default", "cluster-stress", 1), check=False)

        # Deploy dummy ConfigMap
        create_cm = subprocess.run(
            [
                "kubectl",
                "create",
                "configmap",
                "orders-config",
                "-n",
                "sock-shop",
                "--from-literal=updated=false",
                "--dry-run=client",
                "-o",
                "yaml",
            ],
            capture_output=True,
            text=True,
            check=True,
        )
        subprocess.run(
            ["kubectl", "apply", "-f", "-"],
            input=create_cm.stdout,
            text=True,
            check=True,
        )

        # Phase 1: Baseline (120s)
        time.sleep(120)

        # Phase 2: Trigger (T+120s)
        logger.log(
            "TRIGGER_ANOMALY",
            "Injecting FAIL_NOW and ConfigDriftState for Saga rollback test",
        )
        fail_query = _load_sparql_query(cfg, "fail-restart.sparql")
        run_sparql(cfg, fail_query)
        drift_query = _load_sparql_query(cfg, "inject-drift.sparql")
        run_sparql(cfg, drift_query)

        # Phase 3: Observation (360s)
        start_obs = time.time()
        remediation_detected = False
        while time.time() - start_obs < 360:
            val = run_capture(
                k8s_get_jsonpath(
                    "sock-shop", "configmap", "orders-config", "{.data.updated}"
                ),
                check=False,
            )
            # We look for 'false' after it was briefly 'true' (not easily polled here but we check if it's currently 'false')
            if (
                val == "false"
                and (time.time() - start_obs > 20)
                and not remediation_detected
            ):
                logger.log(
                    "REMEDIATION_DETECTED",
                    "Saga successfully rolled back ConfigMap to original state",
                )
                remediation_detected = True
            time.sleep(10)

        # Phase 4: Stabilization (120s)
        logger.log("STABILIZATION", "Monitoring post-fix stability for 120s")
        time.sleep(120)

        logger.log("END_SCENARIO", "Scenario 4 completed")
        logger.save()
        run(k8s_scale("default", "cluster-stress", 0), check=False)
        run(k8s_delete_resource("configmap", "orders-config", namespace="sock-shop"))
        restore_query = _load_sparql_query(cfg, "restore-restart.sparql")
        run_sparql(cfg, restore_query)

    elif scenario == "5":
        logger = EventLogger("5")
        logger.log("START_BASELINE", "Locust: 500 users, Cluster-Stress: 1 replica")
        set_locust_load(50, 10)  # Low load for image update scenario
        run(k8s_scale("default", "cluster-stress", 1), check=False)

        # Phase 1: Baseline (120s)
        time.sleep(120)

        # Phase 2: Trigger (T+120s)
        logger.log(
            "TRIGGER_ANOMALY",
            f"Resetting front-end to vulnerable image {SOCK_SHOP_FRONTEND_IMAGE}:{SOCK_SHOP_FRONTEND_VULNERABLE_TAG}",
        )
        run(
            k8s_set_image(
                "sock-shop",
                "front-end",
                f"front-end={SOCK_SHOP_FRONTEND_IMAGE}:{SOCK_SHOP_FRONTEND_VULNERABLE_TAG}",
            ),
            check=False,
        )

        # Phase 3: Observation (360s)
        start_obs = time.time()
        remediation_detected = False
        while time.time() - start_obs < 360:
            image = run_capture(
                k8s_get_jsonpath(
                    "sock-shop",
                    "deployment",
                    "front-end",
                    "{.spec.template.spec.containers[0].image}",
                ),
                check=False,
            )
            if SOCK_SHOP_FRONTEND_PATCHED_TAG in image and not remediation_detected:
                logger.log(
                    "REMEDIATION_DETECTED",
                    f"Frontend autonomically patched to secure version ({SOCK_SHOP_FRONTEND_PATCHED_TAG}) in {time.time() - start_obs:.1f}s",
                )
                remediation_detected = True
            time.sleep(10)

        # Phase 4: Stabilization (120s)
        logger.log("STABILIZATION", "Monitoring post-fix stability for 120s")
        time.sleep(120)

        logger.log("END_SCENARIO", "Scenario 5 completed")
        logger.save()
        run(k8s_scale("default", "cluster-stress", 0), check=False)
    elif scenario == "6":
        logger = EventLogger("6")
        logger.log("START_BASELINE", "Locust: 200 users, Cluster-Stress: 1 replica")
        set_locust_load(200, 10)
        run(k8s_scale("default", "cluster-stress", 1), check=False)

        # Deploy dummy ConfigMap
        create_cm = subprocess.run(
            [
                "kubectl",
                "create",
                "configmap",
                "orders-config",
                "-n",
                "sock-shop",
                "--from-literal=updated=false",
                "--dry-run=client",
                "-o",
                "yaml",
            ],
            capture_output=True,
            text=True,
            check=True,
        )
        subprocess.run(
            ["kubectl", "apply", "-f", "-"],
            input=create_cm.stdout,
            text=True,
            check=True,
        )

        # Phase 1: Baseline (120s)
        time.sleep(120)

        # Phase 2: Trigger (T+120s)
        logger.log("TRIGGER_ANOMALY", "Injecting ConfigDriftState for Green Path test")
        drift_query = _load_sparql_query(cfg, "inject-drift.sparql")
        run_sparql(cfg, drift_query)

        # Phase 3: Observation (360s)
        start_obs = time.time()
        remediation_detected = False
        while time.time() - start_obs < 360:
            val = run_capture(
                k8s_get_jsonpath(
                    "sock-shop", "configmap", "orders-config", "{.data.updated}"
                ),
                check=False,
            )
            if val == "true" and not remediation_detected:
                logger.log(
                    "REMEDIATION_DETECTED",
                    "Autonomic system successfully patched ConfigMap to 'true'",
                )
                remediation_detected = True
            time.sleep(10)

        # Phase 4: Stabilization (120s)
        logger.log("STABILIZATION", "Monitoring post-fix stability for 120s")
        time.sleep(120)

        logger.log("END_SCENARIO", "Scenario 6 completed")
        logger.save()
        stop_locust()
        run(k8s_scale("default", "cluster-stress", 0), check=False)
        run(
            k8s_delete_resource("configmap", "orders-config", namespace="sock-shop"),
            check=False,
        )
    elif scenario == "7":
        logger = EventLogger("7")
        logger.log(
            "START_SCENARIO",
            "Registry Credential Remediation (infer imagePullSecret from healthy sibling and patch failing Deployment)",
        )
        private_image = _s7_private_image(cfg)
        logger.log("CONFIG", f"Using private image: {private_image}")

        logger.log("PRECHECK", "Checking AMoCNA service image versions...")
        if not _s7_check_runtime_prerequisites(cfg):
            logger.log(
                "PRECHECK_FAILED",
                "Control-loop services out of sync; aborting Scenario 7",
            )
            logger.save()
            return

        logger.log(
            "CLEANUP", "Step 1: Cleaning up any previous Scenario 7 resources..."
        )
        _cleanup_s7_benchmark(cfg)

        user, pat = _require_registry_credentials()
        logger.log(
            "STEP_2",
            f"Creating docker-registry secret '{S6_SECRET_NAME}' in {S6_NAMESPACE}...",
        )
        _create_docker_registry_secret(S6_NAMESPACE, S6_SECRET_NAME, user, pat)
        secret_type = run_capture(
            [
                "kubectl",
                "get",
                "secret",
                S6_SECRET_NAME,
                "-n",
                S6_NAMESPACE,
                "-o",
                "jsonpath={.type}",
            ],
            check=False,
        )
        if secret_type != "kubernetes.io/dockerconfigjson":
            logger.log(
                "STEP_2_FAILED",
                f"Secret verification failed (type={secret_type or 'missing'})",
            )
            if keep_on_failure:
                logger.log(
                    "KEEP_ON_FAILURE", "Keeping resources for post-failure debugging"
                )
            else:
                _cleanup_s7_benchmark(cfg)
            logger.save()
            error(
                f"✖ ERROR: Secret '{S6_SECRET_NAME}' missing or invalid in namespace '{S6_NAMESPACE}'."
            )
            return
        logger.log("STEP_2_DONE", "docker-registry secret created and verified")

        logger.log(
            "STEP_3",
            "Deploying workloads (failing starts immediately; sibling starts later)...",
        )
        manifest = _load_k8s_manifest(
            cfg, S6_WORKLOADS_MANIFEST, private_image=private_image
        )
        _apply_manifest_stdin(manifest)

        logger.log(
            "STEP_4",
            "Waiting for failing pod ImagePullBackOff before enabling sibling...",
        )
        pull_backoff = False
        failing_ran_without_pull_failure = False
        for i in range(18):
            time.sleep(5)
            snapshots = _s7_failing_pod_snapshots()
            active = [s for s in snapshots if not s[3]]
            pull_failure = next(
                (s for s in active if s[2] in ("ImagePullBackOff", "ErrImagePull")),
                None,
            )
            if pull_failure:
                logger.log(
                    "PULL_FAILURE_DETECTED",
                    f"Failing pod pull error detected on {pull_failure[0]}: {pull_failure[2]}",
                )
                pull_backoff = True
                break
            if active and all(s[1] == "Running" for s in active):
                logger.log(
                    "NO_PULL_FAILURE",
                    "All active failing pods reached Running before any pull failure (likely image cache hit)",
                )
                failing_ran_without_pull_failure = True
                break
            states = (
                ", ".join(
                    f"{name}:{phase or 'unknown'}/{reason or 'none'}{'(term)' if term else ''}"
                    for name, phase, reason, term in snapshots
                )
                or "none"
            )
            console.print(f"    Waiting for pull failure (pods={states})... ({i * 5}s)")

        if failing_ran_without_pull_failure:
            if keep_on_failure:
                logger.log(
                    "KEEP_ON_FAILURE", "Keeping resources for post-failure debugging"
                )
            else:
                _cleanup_s7_benchmark(cfg)
            logger.save()
            error(
                "✖ Scenario precondition failed: s6-failing became Running (no ImagePullBackOff). "
                "This usually means the image was already cached on the node."
            )
            return

        if not pull_backoff:
            logger.log(
                "TIMEOUT",
                "Failing workload did not reach ImagePullBackOff within 90s",
            )
            if keep_on_failure:
                logger.log(
                    "KEEP_ON_FAILURE", "Keeping resources for post-failure debugging"
                )
            else:
                _cleanup_s7_benchmark(cfg)
            logger.save()
            error(
                "✖ TIMEOUT: Failing workload did not reach ImagePullBackOff within 90s."
            )
            return

        logger.log(
            "STEP_5",
            "Scaling sibling to 1 replica to provide valid pull secret source...",
        )
        run(k8s_scale(S6_NAMESPACE, S6_SIBLING_DEPLOY, 1), check=False)
        if not _s7_wait_for_sibling_running(logger):
            logger.log(
                "TIMEOUT_SIBLING",
                "Sibling workload did not become Running after scale-up",
            )
            _s7_print_timeout_diagnostics()
            if keep_on_failure:
                logger.log(
                    "KEEP_ON_FAILURE", "Keeping resources for post-failure debugging"
                )
            else:
                _cleanup_s7_benchmark(cfg)
            logger.save()
            error(
                "✖ TIMEOUT: Sibling workload did not reach Running within 240s after scale-up."
            )
            return

        start_time = time.time()
        patched = False
        healed = False
        logger.log(
            "STEP_6", "Polling for autonomic imagePullSecrets patch and pod recovery..."
        )
        for i in range(24):
            time.sleep(5)
            secret = run_capture(
                k8s_get_jsonpath(
                    S6_NAMESPACE,
                    "deployment",
                    S6_FAILING_DEPLOY,
                    "{.spec.template.spec.imagePullSecrets[0].name}",
                ),
                check=False,
            )
            phase = run_capture(
                k8s_get_pods_jsonpath(
                    S6_NAMESPACE, "app=s6-failing", "{.items[0].status.phase}"
                ),
                check=False,
            )
            if secret == S6_SECRET_NAME:
                patched = True
            if phase == "Running":
                healed = True
            if patched and healed:
                duration = time.time() - start_time
                logger.log(
                    "REMEDIATION_DETECTED",
                    f"Deployment patched with '{S6_SECRET_NAME}' and failing pod is Running in {duration:.1f}s",
                )
                info(
                    f"[green]✔ SUCCESS: Deployment patched with '{S6_SECRET_NAME}' and "
                    f"failing pod is Running in {duration:.1f}s![/green]"
                )
                break
            console.print(
                f"    imagePullSecrets={secret or '(none)'}, pod phase={phase or 'unknown'}... "
                f"({i * 5}s)"
            )

        if not patched:
            logger.log(
                "TIMEOUT_NO_PATCH",
                "Autonomic loop did not patch imagePullSecrets within 120s",
            )
            error(
                "✖ TIMEOUT: Autonomic loop did not patch imagePullSecrets within 120s."
            )
            _s7_print_timeout_diagnostics()
            if keep_on_failure:
                logger.log(
                    "KEEP_ON_FAILURE", "Keeping resources for post-failure debugging"
                )
            else:
                _cleanup_s7_benchmark(cfg)
                logger.log("CLEANUP", "Cleaned up Scenario 7 resources after failure")
        elif not healed:
            logger.log(
                "TIMEOUT_NOT_HEALED",
                "Deployment patched but failing pod did not reach Running within 120s",
            )
            error(
                "✖ TIMEOUT: Deployment was patched but failing pod did not reach Running within 120s."
            )
            _s7_print_timeout_diagnostics()
            if keep_on_failure:
                logger.log(
                    "KEEP_ON_FAILURE", "Keeping resources for post-failure debugging"
                )
            else:
                _cleanup_s7_benchmark(cfg)
                logger.log("CLEANUP", "Cleaned up Scenario 7 resources after failure")
        else:
            logger.log("CLEANUP", "Cleaning up Scenario 7 resources...")
            _cleanup_s7_benchmark(cfg)
        logger.log("END_SCENARIO", "Scenario 7 completed")
        logger.save()

    elif scenario == "all":
        info("Starting complete automated benchmark cycle...")
        for sc in ["1", "2", "3", "4", "5", "6", "7"]:
            run(["amocna", "benchmark", "run", "--scenario", sc])
            info("Waiting 60 seconds between scenarios for cluster cooling...")
            time.sleep(60)
        info(
            "[green]✔ Complete automated benchmark run successfully completed![/green]"
        )

    console.print()
