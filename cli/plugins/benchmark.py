import argparse
import subprocess
import sys
import time
from cli.plugins.base import BasePlugin
from cli.core import (
    ProjectConfig,
    header,
    info,
    error,
    run,
    run_capture,
    _C,
)


class BenchmarkPlugin(BasePlugin):
    """Plugin to orchestrate autonomic computing benchmarks and scenarios."""

    def register(self, subparsers: argparse._SubParsersAction) -> None:
        p_bench = subparsers.add_parser(
            "benchmark", help="Automated benchmarking and scenario orchestration"
        )
        bench_sub = p_bench.add_subparsers(dest="bench_command", required=True)

        p_run = bench_sub.add_parser(
            "run", help="Run a specific scenario or all benchmarks"
        )
        p_run.add_argument(
            "--scenario",
            choices=["1", "2", "3", "4", "5", "all"],
            required=True,
            help="Scenario to run",
        )
        p_run.set_defaults(handler=self.execute)

        p_status = bench_sub.add_parser(
            "status", help="Get current workload and cluster scaling status"
        )
        p_status.set_defaults(handler=self.execute)

        p_stop = bench_sub.add_parser(
            "stop", help="Instantly stop all load tests and tear down stress"
        )
        p_stop.set_defaults(handler=self.execute)

        p_load = bench_sub.add_parser(
            "load", help="Directly generate load using Locust"
        )
        p_load.add_argument(
            "--users", type=int, default=1000, help="Number of concurrent users"
        )
        p_load.add_argument(
            "--rate", type=int, default=10, help="User spawn rate per second"
        )
        p_load.set_defaults(handler=self.execute)

    def execute(self, cfg: ProjectConfig, args: argparse.Namespace) -> None:
        if args.bench_command == "run":
            self.cmd_benchmark_run(cfg, args)
        elif args.bench_command == "status":
            self.cmd_benchmark_status(cfg)
        elif args.bench_command == "stop":
            self.cmd_benchmark_stop(cfg)
        elif args.bench_command == "load":
            self.cmd_benchmark_load(cfg, args)

    def _load_sparql_query(self, cfg: ProjectConfig, filename: str) -> str:
        """Dynamically load SPARQL query template from cli resources directory."""
        path = cfg.project_root / "cli" / "resources" / "sparql" / filename
        if not path.is_file():
            error(f"SPARQL template not found: {path}")
            sys.exit(1)
        return path.read_text().strip()

    def run_sparql(self, cfg: ProjectConfig, update_query: str) -> None:
        """Execute a SPARQL Update query inside GraphDB using a temporary pod in K8s."""
        run(
            [
                "kubectl",
                "run",
                "amocna-sparql-trigger",
                "--image=curlimages/curl:8.12.1",
                "--restart=Never",
                "--rm",
                "-i",
                "--",
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
        )

    def set_locust_load(self, users: int, rate: int) -> None:
        """Control Locust swarming programmatically."""
        info(f"Setting Locust traffic to {users} users (spawn rate {rate})...")
        python_snippet = f"""
import urllib.request, urllib.parse
data = urllib.parse.urlencode({{'user_count': {users}, 'spawn_rate': {rate}, 'host': 'http://front-end.sock-shop.svc.cluster.local'}}).encode()
req = urllib.request.Request('http://localhost:8089/swarm', data=data)
try:
    urllib.request.urlopen(req)
    print("Success")
except Exception as e:
    print("Error:", e)
"""
        run(
            [
                "kubectl",
                "exec",
                "-n",
                "sock-shop",
                "deploy/locust-master",
                "--",
                "python3",
                "-c",
                python_snippet,
            ]
        )

    def stop_locust(self) -> None:
        """Stop Locust traffic swarming."""
        info("Stopping Locust traffic...")
        python_snippet = """
import urllib.request
try:
    urllib.request.urlopen('http://localhost:8089/stop')
    print("Success")
except Exception as e:
    print("Error:", e)
"""
        run(
            [
                "kubectl",
                "exec",
                "-n",
                "sock-shop",
                "deploy/locust-master",
                "--",
                "python3",
                "-c",
                python_snippet,
            ]
        )

    def get_locust_stats(self) -> None:
        """Get active load statistics from Locust."""
        python_snippet = """
import urllib.request, json
try:
    res = urllib.request.urlopen('http://localhost:8089/stats/requests')
    data = json.loads(res.read().decode())
    print(f"    Active Users: {data.get('user_count', 0)}, RPS: {data.get('total_rps', 0):.2f}, Avg Response Time: {data.get('stats_average_response_time', 0):.2f}ms")
except Exception as e:
    print("    Locust is currently idle or stopped.")
"""
        run(
            [
                "kubectl",
                "exec",
                "-n",
                "sock-shop",
                "deploy/locust-master",
                "--",
                "python3",
                "-c",
                python_snippet,
            ]
        )

    def cmd_benchmark_status(self, cfg: ProjectConfig) -> None:
        header("AMoCNA Benchmark Status")

        # 1. Pod replicas in sock-shop
        print(f"\n  {_C.bold('Sock Shop Deployments:')}")
        for deploy in ["front-end", "orders", "carts"]:
            try:
                replicas = run_capture(
                    [
                        "kubectl",
                        "get",
                        "deployment",
                        deploy,
                        "-n",
                        "sock-shop",
                        "-o",
                        "jsonpath={.spec.replicas}/{.status.readyReplicas}",
                    ],
                    check=False,
                )
                print(f"    {deploy:<15} : {replicas} replicas ready")
            except Exception:
                print(f"    {deploy:<15} : Not found or unreachable")

        # 2. Stress replica status
        print(f"\n  {_C.bold('Load Stress Status:')}")
        try:
            stress_replicas = run_capture(
                [
                    "kubectl",
                    "get",
                    "deployment",
                    "cluster-stress",
                    "-n",
                    "default",
                    "-o",
                    "jsonpath={.spec.replicas}",
                ],
                check=False,
            )
            print(f"    cluster-stress  : {stress_replicas} replicas running")
        except Exception:
            print("    cluster-stress  : Not deployed")

        # 3. Active Locust stats
        print(f"\n  {_C.bold('Locust Load Statistics:')}")
        self.get_locust_stats()
        print()

    def cmd_benchmark_stop(self, cfg: ProjectConfig) -> None:
        header("Stopping All Benchmark Elements")
        self.stop_locust()
        info("Scaling down cluster-stress to 0...")
        run(
            [
                "kubectl",
                "scale",
                "deployment",
                "cluster-stress",
                "-n",
                "default",
                "--replicas=0",
            ],
            check=False,
        )
        info("Scaling down front-end to 1...")
        run(
            [
                "kubectl",
                "scale",
                "deployment",
                "front-end",
                "-n",
                "sock-shop",
                "--replicas=1",
            ],
            check=False,
        )
        info("Resetting front-end image to baseline 0.3.0...")
        run(
            [
                "kubectl",
                "set",
                "image",
                "deployment/front-end",
                "front-end=weaveworksdemos/frontend:0.3.0",
                "-n",
                "sock-shop",
            ],
            check=False,
        )

        info("Resetting orders CPU requests...")
        patch_spec = '{"spec": {"template": {"spec": {"containers": [{"name": "orders", "resources": {"requests": {"cpu": "100m"}}}]}}}}'
        run(
            [
                "kubectl",
                "patch",
                "deployment",
                "orders",
                "-n",
                "sock-shop",
                "--patch",
                patch_spec,
            ],
            check=False,
        )

        # Clean up ConfigDriftState / SecurityVulnerabilityDetectedState if any
        info("Cleaning up GraphDB anomaly states...")
        clean_query = self._load_sparql_query(cfg, "clean-anomalies.sparql")
        self.run_sparql(cfg, clean_query)

        # Restore RestartPodIntent instruction
        restore_query = self._load_sparql_query(cfg, "restore-restart.sparql")
        self.run_sparql(cfg, restore_query)

        info("System successfully returned to baseline.")
        print()

    def cmd_benchmark_load(self, cfg: ProjectConfig, args: argparse.Namespace) -> None:
        header(f"Starting Custom Load: {args.users} users")
        self.set_locust_load(args.users, args.rate)
        print()

    def cmd_benchmark_run(self, cfg: ProjectConfig, args: argparse.Namespace) -> None:
        scenario = args.scenario
        header(f"Running AMoCNA Benchmark: Scenario {scenario}")

        if scenario == "1":
            info("Initializing Scenario 1: Horizontal Scaling (Scale-Out)...")
            info("Scaling front-end back to 1 replica...")
            run(
                [
                    "kubectl",
                    "scale",
                    "deployment",
                    "front-end",
                    "-n",
                    "sock-shop",
                    "--replicas=1",
                ],
                check=False,
            )

            info("Step 1: Spawning baseline traffic (1000 users)...")
            self.set_locust_load(1000, 10)
            info("Waiting 20 seconds for baseline stabilization...")
            time.sleep(20)

            info("Step 2: Triggering SLA breach by increasing users to 3000...")
            self.set_locust_load(3000, 20)

            start_time = time.time()
            success = False
            info("Step 3: Polling frontend replica count for autonomic scale-out...")
            for i in range(24):  # 120 seconds max
                time.sleep(5)
                replicas = run_capture(
                    [
                        "kubectl",
                        "get",
                        "deployment",
                        "front-end",
                        "-n",
                        "sock-shop",
                        "-o",
                        "jsonpath={.status.readyReplicas}",
                    ],
                    check=False,
                )
                if replicas == "3":
                    duration = time.time() - start_time
                    info(
                        _C.green(
                            f"✔ SUCCESS: Front-end successfully scaled to 3 replicas in {duration:.1f}s!"
                        )
                    )
                    success = True
                    break
                else:
                    print(
                        f"    Current ready replicas: {replicas or '0'}/3... ({i * 5}s)"
                    )

            if not success:
                error("✖ TIMEOUT: Autonomic loop failed to scale frontend within 120s.")

            info("Cleaning up Scenario 1: Scaling load back to 1000...")
            self.set_locust_load(1000, 10)

        elif scenario == "2":
            info("Initializing Scenario 2: Vertical Scaling...")
            info("Scaling cluster-stress to 0...")
            run(
                [
                    "kubectl",
                    "scale",
                    "deployment",
                    "cluster-stress",
                    "-n",
                    "default",
                    "--replicas=0",
                ],
                check=False,
            )
            info("Resetting orders CPU requests to 100m...")
            patch_spec = '{"spec": {"template": {"spec": {"containers": [{"name": "orders", "resources": {"requests": {"cpu": "100m"}}}]}}}}'
            run(
                [
                    "kubectl",
                    "patch",
                    "deployment",
                    "orders",
                    "-n",
                    "sock-shop",
                    "--patch",
                    patch_spec,
                ],
                check=False,
            )

            info("Step 1: Spawning baseline traffic (1000 users)...")
            self.set_locust_load(1000, 10)
            time.sleep(10)

            info(
                "Step 2: Scaling cluster-stress to 3 replicas to generate node CPU pressure..."
            )
            run(
                [
                    "kubectl",
                    "scale",
                    "deployment",
                    "cluster-stress",
                    "-n",
                    "default",
                    "--replicas=3",
                ]
            )

            start_time = time.time()
            success = False
            info(
                "Step 3: Polling orders CPU requests (Persistent check requires 3 ticks / 30s)..."
            )
            for i in range(24):
                time.sleep(5)
                cpu = run_capture(
                    [
                        "kubectl",
                        "get",
                        "deployment",
                        "orders",
                        "-n",
                        "sock-shop",
                        "-o",
                        "jsonpath={.spec.template.spec.containers[0].resources.requests.cpu}",
                    ],
                    check=False,
                )
                if cpu == "1":
                    duration = time.time() - start_time
                    info(
                        _C.green(
                            f"✔ SUCCESS: Orders container CPU requests autonomically patched to 1 CPU core in {duration:.1f}s!"
                        )
                    )
                    success = True
                    break
                else:
                    print(
                        f"    Current orders CPU request: {cpu or '100m'} (Target: 1)... ({i * 5}s)"
                    )

            if not success:
                error(
                    "✖ TIMEOUT: Autonomic loop failed to vertically scale orders within 120s."
                )

            info("Cleaning up Scenario 2: Scaling down cluster-stress...")
            run(
                [
                    "kubectl",
                    "scale",
                    "deployment",
                    "cluster-stress",
                    "-n",
                    "default",
                    "--replicas=0",
                ],
                check=False,
            )

        elif scenario == "3":
            info("Initializing Scenario 3: Security Patching...")
            info("Step 1: Auto-discovering active front-end pod name...")
            pod_name = run_capture(
                [
                    "kubectl",
                    "get",
                    "pods",
                    "-n",
                    "sock-shop",
                    "-l",
                    "name=front-end",
                    "-o",
                    "jsonpath={.items[0].metadata.name}",
                ]
            )
            if not pod_name:
                error("✖ ERROR: Active front-end pod not found in sock-shop namespace.")
                return
            info(f"Active pod found: {pod_name}")

            # Capture current image
            orig_image = run_capture(
                [
                    "kubectl",
                    "get",
                    "deployment",
                    "front-end",
                    "-n",
                    "sock-shop",
                    "-o",
                    "jsonpath={.spec.template.spec.containers[0].image}",
                ]
            )
            info(f"Current front-end image: {orig_image}")

            info(
                "Step 2: Injecting SecurityVulnerabilityDetectedState into GraphDB for this pod..."
            )
            vulnerability_query = self._load_sparql_query(
                cfg, "inject-vulnerability.sparql"
            ).replace("{pod_name}", pod_name)
            self.run_sparql(cfg, vulnerability_query)

            start_time = time.time()
            success = False
            info(
                "Step 3: Polling front-end image version for security patching rollout..."
            )
            for i in range(24):
                time.sleep(5)
                image = run_capture(
                    [
                        "kubectl",
                        "get",
                        "deployment",
                        "front-end",
                        "-n",
                        "sock-shop",
                        "-o",
                        "jsonpath={.spec.template.spec.containers[0].image}",
                    ]
                )
                if "0.3.1" in image:
                    duration = time.time() - start_time
                    info(
                        _C.green(
                            f"✔ SUCCESS: Front-end container image successfully updated to secure patched version (0.3.1) in {duration:.1f}s!"
                        )
                    )
                    success = True
                    break
                else:
                    print(f"    Current image: {image}... ({i * 5}s)")

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
            run(["kubectl", "apply", "-f", "-"], input=create_cm.stdout)

            info(
                "Step 2: Injecting FAIL_NOW keyword into RestartPodIntent instruction in GraphDB..."
            )
            fail_query = self._load_sparql_query(cfg, "fail-restart.sparql")
            self.run_sparql(cfg, fail_query)

            info(
                "Step 3: Triggering Saga by injecting ConfigDriftState on orders service..."
            )
            drift_query = self._load_sparql_query(cfg, "inject-drift.sparql")
            self.run_sparql(cfg, drift_query)

            start_time = time.time()
            success = False
            info(
                "Step 4: Monitoring ConfigMap orders-config for compensation rollback (updated should revert to false)..."
            )
            for i in range(24):
                time.sleep(5)
                val = run_capture(
                    [
                        "kubectl",
                        "get",
                        "configmap",
                        "orders-config",
                        "-n",
                        "sock-shop",
                        "-o",
                        "jsonpath={.data.updated}",
                    ],
                    check=False,
                )

                if val == "false" and i > 2:
                    duration = time.time() - start_time
                    info(
                        _C.green(
                            f"✔ SUCCESS: Saga execution failed at Step 2 (due to FAIL_NOW) and rolled back Step 1 successfully! ConfigMap reverted to original state in {duration:.1f}s!"
                        )
                    )
                    success = True
                    break
                else:
                    print(
                        f"    Current ConfigMap state: updated={val or 'unknown'}... ({i * 5}s)"
                    )

            if not success:
                error("✖ TIMEOUT: Saga failed to execute rollback within 120s.")

            info("Cleaning up Scenario 4...")
            restore_query = self._load_sparql_query(cfg, "restore-restart.sparql")
            self.run_sparql(cfg, restore_query)
            run(
                [
                    "kubectl",
                    "delete",
                    "configmap",
                    "orders-config",
                    "-n",
                    "sock-shop",
                    "--ignore-not-found",
                ]
            )

        elif scenario == "5":
            info("Initializing Scenario 5: End-to-End Vulnerability Remediation...")
            info("Step 1: Resetting front-end to vulnerable image weaveworksdemos/frontend:0.3.0...")
            run(
                [
                    "kubectl",
                    "set",
                    "image",
                    "deployment/front-end",
                    "front-end=weaveworksdemos/frontend:0.3.0",
                    "-n",
                    "sock-shop",
                ],
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
            info("Step 3: Polling front-end image for autonomic security patch to 0.3.1...")
            for i in range(24):
                time.sleep(5)
                image = run_capture(
                    [
                        "kubectl",
                        "get",
                        "deployment",
                        "front-end",
                        "-n",
                        "sock-shop",
                        "-o",
                        "jsonpath={.spec.template.spec.containers[0].image}",
                    ]
                )
                if "0.3.1" in image:
                    duration = time.time() - start_time
                    info(
                        _C.green(
                            f"✔ SUCCESS: Front-end autonomically patched to secure version (0.3.1) in {duration:.1f}s!"
                        )
                    )
                    success = True
                    break
                else:
                    print(f"    Current image: {image}... ({i * 5}s)")

            if not success:
                error(
                    "✖ TIMEOUT: Autonomic vulnerability loop failed to patch front-end within 120s."
                )

        elif scenario == "all":
            info("Starting complete automated benchmark cycle...")
            for sc in ["1", "2", "3", "4", "5"]:
                run(["./amocna.py", "benchmark", "run", "--scenario", sc])
                info("Waiting 15 seconds between scenarios...")
                time.sleep(15)
            info(_C.green("✔ Complete automated benchmark run successfully completed!"))

        print()
