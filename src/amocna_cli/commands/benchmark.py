from __future__ import annotations

import os
import sys
import time
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
S6_PRIVATE_IMAGE_APP = "metis"
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
    curl_args = [
        "curl",
        "-s",
        "-X",
        "POST",
        "http://graphdb.graphdb.svc.cluster.local:7200/repositories/amocna/statements",
        "-H",
        "Content-Type: application/sparql-update",
        "--data",
        update_query,
    ]
    run(k8s_run_pod("amocna-sparql-trigger", "curlimages/curl:8.12.1", curl_args))

def set_locust_load(users: int, rate: int) -> None:
    """Control Locust swarming programmatically."""
    info(f"Setting Locust traffic to {users} users (spawn rate {rate})...")
    python_snippet = LOCUST_SWARM_PYTHON_TEMPLATE.format(users=users, rate=rate)
    run(k8s_exec("sock-shop", "deploy/locust-master", ["python3", "-c", python_snippet]))

def stop_locust() -> None:
    """Stop Locust traffic swarming."""
    info("Stopping Locust traffic...")
    run(k8s_exec("sock-shop", "deploy/locust-master", ["python3", "-c", LOCUST_STOP_PYTHON_TEMPLATE]))

def get_locust_stats() -> None:
    """Get active load statistics from Locust."""
    run(k8s_exec("sock-shop", "deploy/locust-master", ["python3", "-c", LOCUST_STATS_PYTHON_TEMPLATE]))


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
        error("AMOCNA_USER is not set. Export your GitHub username: export AMOCNA_USER=...")
        sys.exit(1)
    if not pat:
        error("AMOCNA_PAT is not set. Export your GitHub PAT: export AMOCNA_PAT=...")
        sys.exit(1)
    return user, pat


def _s6_private_image(cfg: ProjectConfig) -> str:
    version = read_pom_version(cfg.project_root / cfg.parent_pom)
    return f"{cfg.registry}/{S6_PRIVATE_IMAGE_APP}:{version}"


def _s6_expected_version(cfg: ProjectConfig) -> str:
    return read_pom_version(cfg.project_root / cfg.parent_pom)


def _s6_deployment_image(namespace: str, deployment: str) -> str:
    return run_capture(
        k8s_get_jsonpath(
            namespace,
            "deployment",
            deployment,
            "{.spec.template.spec.containers[0].image}",
        ),
        check=False,
    )


def _s6_check_runtime_prerequisites(cfg: ProjectConfig) -> bool:
    """Verify control-loop services run the expected version before Scenario 6."""
    expected = _s6_expected_version(cfg)
    services = [
        ("metis", "metis"),
        ("palamedes", "palamedes"),
        ("themis", "themis"),
    ]
    mismatches: list[str] = []
    for namespace, deployment in services:
        image = _s6_deployment_image(namespace, deployment)
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


def _s6_print_timeout_diagnostics() -> None:
    """Print concise diagnostics to explain why Scenario 6 may be stuck."""
    info("Collecting Scenario 6 diagnostics...")
    pod = run_capture(
        k8s_get_pods_jsonpath(S6_NAMESPACE, "app=s6-failing", "{.items[0].metadata.name}"),
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


def _create_docker_registry_secret(namespace: str, name: str, username: str, password: str) -> None:
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


def _cleanup_s6_benchmark(cfg: ProjectConfig) -> None:
    """Remove Scenario 6 workloads, pull secret, and related GraphDB triples."""
    for deploy in (S6_FAILING_DEPLOY, S6_SIBLING_DEPLOY):
        run(k8s_delete_resource("deployment", deploy, namespace=S6_NAMESPACE), check=False)
    run(k8s_delete_resource("secret", S6_SECRET_NAME, namespace=S6_NAMESPACE), check=False)
    clean_s6 = _load_sparql_query(cfg, "clean-s6.sparql")
    run_sparql(cfg, clean_s6)


def _poke_s6_failing_pod() -> None:
    """Trigger a pod update so Metis re-asserts ImagePullBackOffState after phase sensing."""
    pod_name = run_capture(
        k8s_get_pods_jsonpath(S6_NAMESPACE, "app=s6-failing", "{.items[0].metadata.name}"),
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
                k8s_get_jsonpath("sock-shop", "deployment", deploy, "{.spec.replicas}/{.status.readyReplicas}"),
                check=False,
            )
            console.print(f"    {deploy:<15} : {replicas} replicas ready")
        except Exception:
            console.print(f"    {deploy:<15} : Not found or unreachable")

    # 2. Stress replica status
    console.print(f"\n  [bold]Load Stress Status:[/bold]")
    try:
        stress_replicas = run_capture(
            k8s_get_jsonpath("default", "deployment", "cluster-stress", "{.spec.replicas}"),
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

    info("Resetting orders CPU requests...")
    run(k8s_patch("sock-shop", "orders", ORDERS_CPU_RESET_PATCH), check=False)

    # Clean up ConfigDriftState / SecurityVulnerabilityDetectedState if any
    info("Cleaning up GraphDB anomaly states...")
    clean_query = _load_sparql_query(cfg, "clean-anomalies.sparql")
    run_sparql(cfg, clean_query)

    # Restore RestartPodIntent instruction
    restore_query = _load_sparql_query(cfg, "restore-restart.sparql")
    run_sparql(cfg, restore_query)

    info("Removing Scenario 6 benchmark workloads and graph triples...")
    _cleanup_s6_benchmark(cfg)

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
    scenario: Annotated[str, typer.Option(help="Scenario to run (1, 2, 3, 4, 5, 6, all)")],
):
    """Run a specific scenario or all benchmarks."""
    cfg: ProjectConfig = ctx.obj
    header(f"Running AMoCNA Benchmark: Scenario {scenario}")

    if scenario == "1":
        info("Initializing Scenario 1: Horizontal Scaling (Scale-Out)...")
        info("Scaling front-end back to 1 replica...")
        run(k8s_scale("sock-shop", "front-end", 1), check=False)

        info("Step 1: Spawning baseline traffic (1000 users)...")
        set_locust_load(1000, 10)
        info("Waiting 20 seconds for baseline stabilization...")
        time.sleep(20)

        info("Step 2: Triggering SLA breach by increasing users to 3000...")
        set_locust_load(3000, 20)

        start_time = time.time()
        success = False
        info("Step 3: Polling frontend replica count for autonomic scale-out...")
        for i in range(24):  # 120 seconds max
            time.sleep(5)
            replicas = run_capture(
                k8s_get_jsonpath("sock-shop", "deployment", "front-end", "{.status.readyReplicas}"),
                check=False,
            )
            if replicas == "3":
                duration = time.time() - start_time
                info(
                    "[green]✔ SUCCESS: Front-end successfully scaled to 3 replicas in "
                    f"{duration:.1f}s![/green]"
                )
                success = True
                break
            else:
                console.print(
                    f"    Current ready replicas: {replicas or '0'}/3... ({i * 5}s)"
                )

        if not success:
            error("✖ TIMEOUT: Autonomic loop failed to scale frontend within 120s.")

        info("Cleaning up Scenario 1: Scaling load back to 1000...")
        set_locust_load(1000, 10)

    elif scenario == "2":
        info("Initializing Scenario 2: Vertical Scaling...")
        info("Scaling cluster-stress to 0...")
        run(k8s_scale("default", "cluster-stress", 0), check=False)
        
        info("Resetting orders CPU requests to 100m...")
        run(k8s_patch("sock-shop", "orders", ORDERS_CPU_RESET_PATCH), check=False)

        info("Step 1: Spawning baseline traffic (1000 users)...")
        set_locust_load(1000, 10)
        time.sleep(10)

        info(
            "Step 2: Scaling cluster-stress to 3 replicas to generate node CPU pressure..."
        )
        run(k8s_scale("default", "cluster-stress", 3))

        start_time = time.time()
        success = False
        info(
            "Step 3: Polling orders CPU requests (Persistent check requires 3 ticks / 30s)..."
        )
        for i in range(24):
            time.sleep(5)
            cpu = run_capture(
                k8s_get_jsonpath("sock-shop", "deployment", "orders", "{.spec.template.spec.containers[0].resources.requests.cpu}"),
                check=False,
            )
            if cpu == "1":
                duration = time.time() - start_time
                info(
                    "[green]✔ SUCCESS: Orders container CPU requests autonomically patched to 1 CPU core in "
                    f"{duration:.1f}s![/green]"
                )
                success = True
                break
            else:
                console.print(
                    f"    Current orders CPU request: {cpu or '100m'} (Target: 1)... ({i * 5}s)"
                )

        if not success:
            error(
                "✖ TIMEOUT: Autonomic loop failed to vertically scale orders within 120s."
            )

        info("Cleaning up Scenario 2: Scaling down cluster-stress...")
        run(k8s_scale("default", "cluster-stress", 0), check=False)

    elif scenario == "3":
        info("Initializing Scenario 3: Security Patching...")
        info("Step 1: Auto-discovering active front-end pod name...")
        pod_name = run_capture(
            k8s_get_pods_jsonpath("sock-shop", "name=front-end", "{.items[0].metadata.name}")
        )
        if not pod_name:
            error("✖ ERROR: Active front-end pod not found in sock-shop namespace.")
            return
        info(f"Active pod found: {pod_name}")

        # Capture current image
        orig_image = run_capture(
            k8s_get_jsonpath("sock-shop", "deployment", "front-end", "{.spec.template.spec.containers[0].image}")
        )
        info(f"Current front-end image: {orig_image}")

        info(
            "Step 2: Injecting SecurityVulnerabilityDetectedState into GraphDB for this pod..."
        )
        vulnerability_query = _load_sparql_query(
            cfg, "inject-vulnerability.sparql"
        ).replace("{pod_name}", pod_name)
        run_sparql(cfg, vulnerability_query)

        start_time = time.time()
        success = False
        info(
            "Step 3: Polling front-end image version for security patching rollout..."
        )
        for i in range(24):
            time.sleep(5)
            image = run_capture(
                k8s_get_jsonpath("sock-shop", "deployment", "front-end", "{.spec.template.spec.containers[0].image}")
            )
            if SOCK_SHOP_FRONTEND_PATCHED_TAG in image:
                duration = time.time() - start_time
                info(
                    f"[green]✔ SUCCESS: Front-end container image successfully updated to secure patched version ({SOCK_SHOP_FRONTEND_PATCHED_TAG}) in "
                    f"{duration:.1f}s![/green]"
                )
                success = True
                break
            else:
                console.print(f"    Current image: {image}... ({i * 5}s)")

        if not success:
            error(
                "✖ TIMEOUT: Autonomic loop failed to roll out the image patch within 120s."
            )

    elif scenario == "4":
        info(
            "Initializing Scenario 4: Multi-step Remediation (Red Path Rollback)..."
        )

        info(
            "Step 1: Deploying dummy orders-config ConfigMap in sock-shop namespace..."
        )
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
        # Apply the generated ConfigMap manifest.
        # Original: run(["kubectl", "apply", "-f", "-"], input=create_cm.stdout)
        subprocess.run(
            ["kubectl", "apply", "-f", "-"],
            input=create_cm.stdout,
            text=True,
            check=True
        )

        info(
            "Step 2: Injecting FAIL_NOW keyword into RestartPodIntent instruction in GraphDB..."
        )
        fail_query = _load_sparql_query(cfg, "fail-restart.sparql")
        run_sparql(cfg, fail_query)

        info(
            "Step 3: Triggering Saga by injecting ConfigDriftState on orders service..."
        )
        drift_query = _load_sparql_query(cfg, "inject-drift.sparql")
        run_sparql(cfg, drift_query)

        start_time = time.time()
        success = False
        info(
            "Step 4: Monitoring ConfigMap orders-config for compensation rollback (updated should revert to false)..."
        )
        for i in range(24):
            time.sleep(5)
            val = run_capture(
                k8s_get_jsonpath("sock-shop", "configmap", "orders-config", "{.data.updated}"),
                check=False,
            )

            if val == "false" and i > 2:
                duration = time.time() - start_time
                info(
                    "[green]✔ SUCCESS: Saga execution failed at Step 2 (due to FAIL_NOW) and rolled back Step 1 successfully! ConfigMap reverted to original state in "
                    f"{duration:.1f}s![/green]"
                )
                success = True
                break
            else:
                console.print(
                    f"    Current ConfigMap state: updated={val or 'unknown'}... ({i * 5}s)"
                )

        if not success:
            error("✖ TIMEOUT: Saga failed to execute rollback within 120s.")

        info("Cleaning up Scenario 4...")
        restore_query = _load_sparql_query(cfg, "restore-restart.sparql")
        run_sparql(cfg, restore_query)
        run(
            k8s_delete_resource("configmap", "orders-config", namespace="sock-shop")
        )

    elif scenario == "5":
        info("Initializing Scenario 5: End-to-End Vulnerability Remediation...")
        info(
            f"Step 1: Resetting front-end to vulnerable image "
            f"{SOCK_SHOP_FRONTEND_IMAGE}:{SOCK_SHOP_FRONTEND_VULNERABLE_TAG}..."
        )
        run(
            k8s_set_image(
                "sock-shop",
                "front-end",
                f"front-end={SOCK_SHOP_FRONTEND_IMAGE}:{SOCK_SHOP_FRONTEND_VULNERABLE_TAG}",
            ),
            check=False,
        )
        info("Waiting for rollout to settle...")
        time.sleep(15)

        info(
            "Step 2: Waiting for Metis topology sync and Palamedes catalog scan (45s)..."
        )
        time.sleep(45)

        start_time = time.time()
        success = False
        info(f"Step 3: Polling front-end image for autonomic security patch to {SOCK_SHOP_FRONTEND_PATCHED_TAG}...")
        for i in range(24):
            time.sleep(5)
            image = run_capture(
                k8s_get_jsonpath(
                    "sock-shop",
                    "deployment",
                    "front-end",
                    "{.spec.template.spec.containers[0].image}",
                ),
                check=False,
            )
            if SOCK_SHOP_FRONTEND_PATCHED_TAG in image:
                duration = time.time() - start_time
                info(
                    f"[green]✔ SUCCESS: Front-end autonomically patched to secure version ({SOCK_SHOP_FRONTEND_PATCHED_TAG}) in "
                    f"{duration:.1f}s![/green]"
                )
                success = True
                break
            else:
                console.print(f"    Current image: {image or 'unknown'}... ({i * 5}s)")

        if not success:
            error(
                "✖ TIMEOUT: Autonomic vulnerability loop failed to patch front-end within 120s."
            )

    elif scenario == "6":
        info("Initializing Scenario 6: Registry Credential Remediation...")
        private_image = _s6_private_image(cfg)
        info(f"Using private image: {private_image}")

        info("Preflight: checking AMoCNA service image versions...")
        if not _s6_check_runtime_prerequisites(cfg):
            return

        info("Step 1: Cleaning up any previous Scenario 6 resources...")
        _cleanup_s6_benchmark(cfg)

        user, pat = _require_registry_credentials()
        info(f"Step 2: Creating docker-registry secret '{S6_SECRET_NAME}' in {S6_NAMESPACE}...")
        _create_docker_registry_secret(S6_NAMESPACE, S6_SECRET_NAME, user, pat)

        info("Step 3: Deploying sibling (with regcred) and failing (without) workloads...")
        manifest = _load_k8s_manifest(
            cfg, S6_WORKLOADS_MANIFEST, private_image=private_image
        )
        _apply_manifest_stdin(manifest)

        info("Step 4: Waiting for failing pod ImagePullBackOff...")
        pull_backoff = False
        for i in range(18):
            time.sleep(5)
            reason = run_capture(
                k8s_get_pods_jsonpath(
                    S6_NAMESPACE,
                    "app=s6-failing",
                    "{.items[0].status.containerStatuses[0].state.waiting.reason}",
                ),
                check=False,
            )
            if reason in ("ImagePullBackOff", "ErrImagePull"):
                info(f"    Failing pod pull error detected: {reason}")
                pull_backoff = True
                break
            console.print(
                f"    Waiting for pull failure (current: {reason or 'pending'})... ({i * 5}s)"
            )

        if not pull_backoff:
            error("✖ TIMEOUT: Failing workload did not reach ImagePullBackOff within 90s.")
            _cleanup_s6_benchmark(cfg)
            return

        info("Step 5: Nudging failing pod so Metis asserts ImagePullBackOffState...")
        _poke_s6_failing_pod()
        time.sleep(8)

        start_time = time.time()
        patched = False
        healed = False
        info(
            "Step 6: Polling for autonomic imagePullSecrets patch and pod recovery..."
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
            error(
                "✖ TIMEOUT: Autonomic loop did not patch imagePullSecrets within 120s."
            )
            _s6_print_timeout_diagnostics()
        elif not healed:
            error(
                "✖ TIMEOUT: Deployment was patched but failing pod did not reach Running within 120s."
            )
            _s6_print_timeout_diagnostics()

        info("Cleaning up Scenario 6...")
        _cleanup_s6_benchmark(cfg)

    elif scenario == "all":
        info("Starting complete automated benchmark cycle...")
        for sc in ["1", "2", "3", "4", "5", "6"]:
            run(["amocna", "benchmark", "run", "--scenario", sc])
            info("Waiting 15 seconds between scenarios...")
            time.sleep(15)
        info("[green]✔ Complete automated benchmark run successfully completed![/green]")

    console.print()
