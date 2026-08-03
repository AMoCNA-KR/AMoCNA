"""Experiment S1: SBOM Generation and CVE Detection Performance Benchmark."""

import json
import os
import subprocess
import time
from pathlib import Path
from rich.console import Console

from .latex_generator import (
    generate_s1_app_summary_table,
    generate_s1_per_image_table,
    generate_s1_scanner_comparison_table,
)
from .metrics import jaccard_similarity

console = Console()

IMAGE_APP_MAP = {
    "weaveworksdemos": "Sock Shop",
    "istio": "BookInfo",
    "microservices-demo": "Online Boutique",
}

def detect_app(image: str) -> str:
    for key, app_name in IMAGE_APP_MAP.items():
        if key in image:
            return app_name
    return "Benchmark"

def detect_language(image: str) -> str:
    if "front-end" in image or "currencyservice" in image or "paymentservice" in image or "ratings" in image:
        return "Node.js"
    elif "orders" in image or "carts" in image or "shipping" in image or "reviews" in image or "adservice" in image:
        return "Java"
    elif "productpage" in image or "emailservice" in image or "recommendationservice" in image:
        return "Python"
    elif "details" in image:
        return "Ruby"
    elif "cartservice" in image:
        return "C#"
    else:
        return "Go"

def run_syft(image: str, tmp_dir: Path) -> tuple[Path, float]:
    out_file = tmp_dir / f"sbom_{os.getpid()}_{time.time_ns()}.json"
    t0 = time.perf_counter()
    subprocess.run(["syft", "scan", image, "-o", f"syft-json={out_file}", "--quiet"], check=True)
    latency_ms = (time.perf_counter() - t0) * 1000.0
    return out_file, latency_ms

def run_grype(sbom_file: Path) -> tuple[dict, float]:
    t0 = time.perf_counter()
    res = subprocess.run(["grype", f"sbom:{sbom_file}", "-o", "json", "--quiet"], capture_output=True, text=True)
    latency_ms = (time.perf_counter() - t0) * 1000.0
    try:
        data = json.loads(res.stdout) if res.returncode == 0 else {"matches": []}
    except Exception:
        data = {"matches": []}
    return data, latency_ms

def run_trivy(image: str) -> tuple[dict, float]:
    t0 = time.perf_counter()
    res = subprocess.run(["trivy", "image", "--format", "json", "--scanners", "vuln", "--quiet", image], capture_output=True, text=True)
    latency_ms = (time.perf_counter() - t0) * 1000.0
    try:
        data = json.loads(res.stdout) if res.returncode == 0 else {"Results": []}
    except Exception:
        data = {"Results": []}
    return data, latency_ms

def parse_grype_cve(grype_data: dict) -> tuple[dict, set]:
    matches = grype_data.get("matches", [])
    cve_ids = set()
    counts = {"total": len(matches), "critical": 0, "high": 0, "medium": 0, "low": 0}
    for m in matches:
        vuln = m.get("vulnerability", {})
        cve_id = vuln.get("id")
        if cve_id:
            cve_ids.add(cve_id)
        sev = vuln.get("severity", "").lower()
        if sev in counts:
            counts[sev] += 1
    return counts, cve_ids

def parse_trivy_cve(trivy_data: dict) -> tuple[dict, set]:
    results = trivy_data.get("Results", [])
    cve_ids = set()
    counts = {"total": 0, "critical": 0, "high": 0, "medium": 0, "low": 0}
    for r in results:
        vulns = r.get("Vulnerabilities", [])
        counts["total"] += len(vulns)
        for v in vulns:
            cve_id = v.get("VulnerabilityID")
            if cve_id:
                cve_ids.add(cve_id)
            sev = v.get("Severity", "").lower()
            if sev in counts:
                counts[sev] += 1
    return counts, cve_ids

def run_s1(images: list[str], iterations: int, output_dir: Path) -> dict:
    console.print(f"[bold green]Starting Experiment S1: SBOM & CVE Detection on {len(images)} images ({iterations} iterations)[/bold green]")
    tmp_dir = output_dir / "tmp_s1"
    tmp_dir.mkdir(parents=True, exist_ok=True)

    results = {}
    all_grype_ids = set()
    all_trivy_ids = set()

    for idx, img in enumerate(images, 1):
        console.print(f"[{idx}/{len(images)}] Processing {img}...")
        img_data = {
            "app": detect_app(img),
            "language": detect_language(img),
            "syft_latencies_ms": [],
            "grype_latencies_ms": [],
            "trivy_latencies_ms": [],
            "packages": 0,
            "grype_cves": {},
            "trivy_cves": {},
        }

        for it in range(iterations):
            try:
                sbom_file, syft_ms = run_syft(img, tmp_dir)
                img_data["syft_latencies_ms"].append(syft_ms)

                grype_data, grype_ms = run_grype(sbom_file)
                img_data["grype_latencies_ms"].append(grype_ms)

                trivy_data, trivy_ms = run_trivy(img)
                img_data["trivy_latencies_ms"].append(trivy_ms)

                if it == iterations - 1:
                    with open(sbom_file, "r") as f:
                        sbom_json = json.load(f)
                        img_data["packages"] = len(sbom_json.get("artifacts", []))
                    g_counts, g_ids = parse_grype_cve(grype_data)
                    t_counts, t_ids = parse_trivy_cve(trivy_data)
                    img_data["grype_cves"] = g_counts
                    img_data["trivy_cves"] = t_counts
                    all_grype_ids.update(g_ids)
                    all_trivy_ids.update(t_ids)

                if sbom_file.exists():
                    sbom_file.unlink()
            except Exception as e:
                console.print(f"[red]Error processing {img} in iteration {it}: {e}[/red]")

        results[img] = img_data

    scanner_comp = {
        "grype_total_unique": len(all_grype_ids),
        "trivy_total_unique": len(all_trivy_ids),
        "intersection": len(all_grype_ids & all_trivy_ids),
        "grype_only": len(all_grype_ids - all_trivy_ids),
        "trivy_only": len(all_trivy_ids - all_grype_ids),
        "jaccard_similarity": jaccard_similarity(all_grype_ids, all_trivy_ids),
    }

    # Save JSON and TeX outputs
    with open(output_dir / "s1_results.json", "w") as f:
        json.dump(results, f, indent=2)
    with open(output_dir / "s1_scanner_comparison.json", "w") as f:
        json.dump(scanner_comp, f, indent=2)

    with open(output_dir / "s1_per_image_table.tex", "w") as f:
        f.write(generate_s1_per_image_table(results))
    with open(output_dir / "s1_scanner_comparison.tex", "w") as f:
        f.write(generate_s1_scanner_comparison_table(scanner_comp))

    console.print(f"[bold green]Experiment S1 completed! Jaccard similarity: {scanner_comp['jaccard_similarity']:.2f}[/bold green]")
    return results
