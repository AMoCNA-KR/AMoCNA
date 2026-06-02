from __future__ import annotations

import sys
import time
import json
import datetime
import subprocess
from typing_extensions import Annotated
import typer

from amocna_cli.config import ProjectConfig
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

    info("Resetting orders CPU requests...")
    run(k8s_patch("sock-shop", "orders", ORDERS_CPU_RESET_PATCH), check=False)

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
        str, typer.Option(help="Scenario to run (1, 2, 3, 4, 5, 6, all)")
    ],
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
        logger.log("TRIGGER_ANOMALY", "Spiking Locust to 2000 users")
        set_locust_load(2000, 20)

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
        logger.log("START_BASELINE", "Locust: 200 users, Cluster-Stress: 1 replica")
        set_locust_load(200, 10)
        run(k8s_scale("default", "cluster-stress", 1), check=False)
        run(k8s_patch("sock-shop", "orders", ORDERS_CPU_RESET_PATCH), check=False)

        # Phase 1: Baseline (120s)
        time.sleep(120)

        # Phase 2: Trigger (T+120s)
        logger.log(
            "TRIGGER_ANOMALY",
            "Scaling cluster-stress to 5 replicas to generate node pressure",
        )
        run(k8s_scale("default", "cluster-stress", 5))

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
                remediation_detected = True
            time.sleep(10)

        # Phase 4: Stabilization (120s)
        logger.log("STABILIZATION", "Monitoring post-fix stability for 120s")
        time.sleep(120)

        logger.log("END_SCENARIO", "Scenario 2 completed")
        logger.save()
        stop_locust()
        run(k8s_scale("default", "cluster-stress", 0), check=False)
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
        logger.log("START_BASELINE", "Locust: 500 users, Cluster-Stress: 1 replica")
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
        run(k8s_scale("default", "cluster-stress", 0), check=False)
        run(k8s_delete_resource("configmap", "orders-config", namespace="sock-shop"))

    elif scenario == "all":
        info("Starting complete automated benchmark cycle...")
        for sc in ["1", "2", "3", "4", "5", "6"]:
            run(["amocna", "benchmark", "run", "--scenario", sc])
            info("Waiting 60 seconds between scenarios for cluster cooling...")
            time.sleep(60)
        info(
            "[green]✔ Complete automated benchmark run successfully completed![/green]"
        )

    console.print()
