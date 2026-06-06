import time
import json
from amocna_cli.commands.benchmark.base import Scenario
from amocna_cli.commands.benchmark.registry import ScenarioRegistry
from amocna_cli.utils.ui import run, run_capture, info
from amocna_cli.utils.shell import (
    k8s_scale,
    k8s_patch,
    k8s_rollout_restart,
    k8s_get_jsonpath,
    ORDERS_CPU_RESET_PATCH,
)

@ScenarioRegistry.register
class Scenario2(Scenario):
    id = "2"
    name = "Vertical Scaling Remediation (Green Path)"
    allowed_intents = ["VerticalScalingAction"]

    def initialize(self) -> None:
        from amocna_cli.commands.benchmark import run_sparql, _load_sparql_query
        run_sparql(self.cfg, _load_sparql_query(self.cfg, "clean-anomalies.sparql"))
        run_sparql(self.cfg, _load_sparql_query(self.cfg, "clean-actions.sparql"))

    def setup_baseline(self) -> None:
        from amocna_cli.commands.benchmark import set_locust_load
        set_locust_load(200, 10)
        run(k8s_scale("default", "cluster-stress", 1), check=False)
        run(k8s_scale("sock-shop", "orders", 1), check=False)
        run(k8s_patch("sock-shop", "orders", ORDERS_CPU_RESET_PATCH), check=False)

    def trigger_anomaly(self) -> None:
        from amocna_cli.commands.benchmark import set_locust_load
        orders_node = run_capture(
            ["kubectl", "get", "pods", "-n", "sock-shop", "-l", "name=orders", "-o", "jsonpath={.items[0].spec.nodeName}"],
            check=False
        ).strip().replace("'", "")
        info(f"Orders pod is running on node: {orders_node}")

        stress_patch = json.dumps({
            "spec": {"template": {"spec": {
                "nodeSelector": {"kubernetes.io/hostname": orders_node},
                "containers": [{
                    "name": "stress",
                    "args": ["--cpu", "2", "--timeout", "600s"],
                    "resources": {
                        "requests": {"cpu": "100m", "memory": "200Mi"},
                        "limits": {"cpu": "1800m", "memory": "3000Mi"},
                    },
                }]
            }}}
        })

        self.logger.log("TRIGGER_ANOMALY", f"Patching cluster-stress to node {orders_node}")
        run(k8s_patch("default", "cluster-stress", stress_patch), check=False)
        run(k8s_scale("default", "cluster-stress", 3))
        set_locust_load(1500, 30)

    def observe_remediation(self) -> None:
        from amocna_cli.commands.benchmark import set_locust_load
        start_obs = time.time()
        remediation_detected = False
        while time.time() - start_obs < 360:
            cpu = run_capture(
                k8s_get_jsonpath("sock-shop", "deployment", "orders", "{.spec.template.spec.containers[0].resources.requests.cpu}"),
                check=False,
            )
            if cpu == "1" and not remediation_detected:
                self.logger.log("REMEDIATION_DETECTED", "Orders CPU requests patched to 1")
                self.logger.log("TRAFFIC_REBALANCE", "Restarting Locust workers...")
                run(k8s_rollout_restart("deployment/locust-worker", namespace="sock-shop"), check=False)
                time.sleep(30)
                self.logger.log("RESUME_TRAFFIC", "Re-triggering Locust swarm")
                set_locust_load(1500, 30)
                remediation_detected = True
                break
            time.sleep(10)

    def cleanup(self) -> None:
        from amocna_cli.commands.benchmark import stop_locust
        stop_locust()
        run(k8s_scale("default", "cluster-stress", 0), check=False)
        run(k8s_scale("sock-shop", "orders", 1), check=False)
        
        # Restore default stress settings
        stress_restore = json.dumps({
            "spec": {"template": {"spec": {
                "nodeSelector": {"kubernetes.io/hostname": "kube-worker-3"},
                "containers": [{
                    "name": "stress",
                    "args": ["--cpu", "2", "--io", "1", "--vm", "1", "--vm-bytes", "2350M", "--timeout", "600s"],
                    "resources": {
                        "requests": {"cpu": "1200m", "memory": "2400Mi"},
                        "limits": {"cpu": "1200m", "memory": "2450Mi"},
                    },
                }]
            }}}
        })
        run(k8s_patch("default", "cluster-stress", stress_restore), check=False)
        run(k8s_patch("sock-shop", "orders", ORDERS_CPU_RESET_PATCH), check=False)
