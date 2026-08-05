"""Experiment S2: Multi-service E2E remediation via AMoCNA loop (no CLI self-patch)."""

from __future__ import annotations

import json
import time
from pathlib import Path

from rich.console import Console

from . import k8s_helpers as kh
from .latex_generator import generate_s2_remediation_latency_table

console = Console()

REMEDIATION_TARGETS = [
    {
        "namespace": "sock-shop",
        "deployment": "front-end",
        "container": "front-end",
        "vulnerable_image": "docker.io/weaveworksdemos/front-end:0.3.0",
        "expected_tag": "0.3.12",
        "policy": "PATCH",
        "severity": "HIGH",
    },
    {
        "namespace": "sock-shop",
        "deployment": "orders",
        "container": "orders",
        "vulnerable_image": "docker.io/weaveworksdemos/orders:0.4.0",
        "expected_tag": "0.4.7",
        "policy": "MINOR",
        "severity": "CRITICAL",
    },
    {
        "namespace": "sock-shop",
        "deployment": "carts",
        "container": "carts",
        "vulnerable_image": "docker.io/weaveworksdemos/carts:0.3.5",
        "expected_tag": "0.4.8",
        "policy": "MINOR",
        "severity": "HIGH",
    },
]


def _reset_vulnerable(target: dict) -> None:
    kh.set_deployment_image(
        target["namespace"],
        target["deployment"],
        target["container"],
        target["vulnerable_image"],
    )


def run_s2(
    iterations: int,
    output_dir: Path,
    redis_host: str = "localhost",
    redis_port: int = 6379,
    remediation_timeout_s: int = 420,
    scan_timeout_s: int = 3600,
    trigger_scig_scan: bool = False,
) -> dict:
    console.print(
        f"[bold green]S2: E2E remediation via Palamedes/Themis "
        f"({iterations} iterations, no CLI patch, "
        f"scig_scan={trigger_scig_scan})[/bold green]"
    )
    results: dict = {"iterations": [], "success_rates": {}, "trigger_scig_scan": trigger_scig_scan}

    for i in range(iterations):
        console.print(f"[bold]Iteration {i + 1}/{iterations}[/bold]")
        for target in REMEDIATION_TARGETS:
            console.print(f"  Reset {target['deployment']} → {target['vulnerable_image']}")
            _reset_vulnerable(target)
        # Allow Metis topology / rollout to settle on vulnerable tags
        time.sleep(30)

        t0 = time.perf_counter()
        scan_s = 0.0
        if trigger_scig_scan:
            console.print("  Triggering SCIG scan (wait for completion)...")
            try:
                _, scan_s = kh.run_scig_scan(
                    namespaces="sock-shop",
                    timeout_s=scan_timeout_s,
                    discover=True,
                    image_list_only=False,
                )
            except Exception as e:
                console.print(f"  [yellow]SCIG scan skipped/failed: {e}[/yellow]")
                scan_s = 0.0
        t_after_scan = time.perf_counter()

        # Wait for AMoCNA control loop to remediate each service (no kubectl set image)
        per_service: dict = {}
        for target in REMEDIATION_TARGETS:
            dep = target["deployment"]
            console.print(f"  Waiting for autonomous remediations of {dep} → {target['expected_tag']}...")
            t_wait0 = time.perf_counter()
            try:
                wait_s = kh.wait_for_image_tag(
                    target["namespace"],
                    dep,
                    target["expected_tag"],
                    timeout_s=remediation_timeout_s,
                )
                success = True
                final_image = kh.get_deployment_image(target["namespace"], dep)
            except TimeoutError as e:
                console.print(f"  [red]{e}[/red]")
                wait_s = time.perf_counter() - t_wait0
                success = False
                final_image = kh.get_deployment_image(target["namespace"], dep)

            per_service[dep] = {
                "success": success,
                "final_image": final_image,
                "expected_tag": target["expected_tag"],
                "policy": target["policy"],
                "severity": target["severity"],
                "execution_ms": 0.0,
                "rollout_ms": wait_s * 1000.0,
                "wait_after_scan_ms": wait_s * 1000.0,
            }

        t_end = time.perf_counter()
        iter_data = {
            "scig_scan_ms": scan_s * 1000.0,
            "redis_sync_ms": max(0.0, (t_after_scan - t0 - scan_s) * 1000.0),
            "planning_ms": 0.0,
            "total_e2e_ms": (t_end - t0) * 1000.0,
            "per_service": per_service,
            "redis_mb": kh.redis_used_memory_mb(redis_host, redis_port),
            "palamedes_metrics": kh.pod_metrics("palamedes", "app=palamedes"),
        }
        results["iterations"].append(iter_data)

    for target in REMEDIATION_TARGETS:
        dep = target["deployment"]
        oks = [
            1
            for it in results["iterations"]
            if it["per_service"].get(dep, {}).get("success")
        ]
        results["success_rates"][dep] = sum(oks) / max(len(results["iterations"]), 1)

    output_dir.mkdir(parents=True, exist_ok=True)
    with open(output_dir / "s2_results.json", "w") as f:
        json.dump(results, f, indent=2)
    with open(output_dir / "s2_remediation_latency.tex", "w") as f:
        f.write(generate_s2_remediation_latency_table(results))

    console.print("[bold green]Experiment S2 completed.[/bold green]")
    return results
