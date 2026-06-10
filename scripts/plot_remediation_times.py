#!/usr/bin/env python3
"""Plot remediation latency from experiment_results.csv (§4.3.3)."""

from __future__ import annotations

import argparse
import csv
import statistics
import sys
from collections import defaultdict
from pathlib import Path

try:
    import matplotlib.pyplot as plt
    from matplotlib.patches import Patch
except ImportError:
    print(
        "matplotlib is required. Run:\n"
        "  uv run --with matplotlib scripts/plot_remediation_times.py",
        file=sys.stderr,
    )
    sys.exit(1)


SCENARIO_LABELS = {
    "5": "S1: Image remediation",
    "7": "S2: Registry credentials",
    "1": "S3: Horizontal scaling",
    "2": "S4: Vertical scaling",
}

LOAD_ORDER = ["none", "medium", "high"]
LOAD_COLORS = {"none": "#4C78A8", "medium": "#F58518", "high": "#E45756"}

PLOT_RCPARAMS = {
    "font.size": 16,
    "axes.titlesize": 18,
    "axes.labelsize": 16,
    "xtick.labelsize": 15,
    "ytick.labelsize": 15,
    "legend.fontsize": 15,
    "legend.title_fontsize": 16,
    "figure.titlesize": 20,
}


def configure_plot_style() -> None:
    plt.rcParams.update(PLOT_RCPARAMS)


def parse_remediation(value: str) -> float | None:
    value = (value or "").strip()
    if not value or value.upper() == "N/A":
        return None
    try:
        return float(value)
    except ValueError:
        return None


def load_rows(path: Path, status: str) -> list[dict]:
    rows: list[dict] = []
    with path.open(newline="", encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            remediation = parse_remediation(row.get("RemediationTimeSeconds", ""))
            if remediation is None:
                continue
            row_status = row.get("Status", "").strip().upper()
            if status == "success" and row_status != "SUCCESS":
                continue
            rows.append(
                {
                    "scenario": row["Scenario"].strip(),
                    "load": row["LoadLevel"].strip(),
                    "iteration": int(row["Iteration"]),
                    "status": row_status,
                    "remediation_s": remediation,
                }
            )
    return rows


def scenario_sort_key(scenario: str) -> tuple[int, str]:
    try:
        if scenario == "1":
            return (3, scenario)
        elif scenario == "2":
            return (4, scenario)
        elif scenario == "5":
            return (1, scenario)
        elif scenario == "7":
            return (2, scenario)
        return (int(scenario), scenario)
    except ValueError:
        return (999, scenario)


def label_for(scenario: str) -> str:
    return SCENARIO_LABELS.get(scenario, f"S{scenario}")


def ordered_loads(loads: set[str]) -> list[str]:
    ordered = [level for level in LOAD_ORDER if level in loads]
    ordered.extend(sorted(loads - set(ordered)))
    return ordered


def plot_boxplots_by_scenario(rows: list[dict], output: Path) -> None:
    by_scenario: dict[str, dict[str, list[float]]] = defaultdict(lambda: defaultdict(list))
    for row in rows:
        by_scenario[row["scenario"]][row["load"]].append(row["remediation_s"])

    scenarios = sorted(by_scenario.keys(), key=scenario_sort_key)
    if not scenarios:
        raise SystemExit("No remediation data to plot.")

    fig, axes = plt.subplots(
        1,
        len(scenarios),
        figsize=(3.8 * len(scenarios), 5.5),
        sharey=False,
        squeeze=False,
    )

    for ax, scenario in zip(axes[0], scenarios, strict=True):
        loads = ordered_loads(set(by_scenario[scenario]))
        data = [by_scenario[scenario][load] for load in loads]
        positions = list(range(1, len(loads) + 1))

        bp = ax.boxplot(
            data,
            positions=positions,
            widths=0.55,
            patch_artist=True,
            showfliers=True,
            medianprops={"color": "black", "linewidth": 1.5},
        )
        for patch, load in zip(bp["boxes"], loads, strict=True):
            patch.set_facecolor(LOAD_COLORS.get(load, "#72B7B2"))
            patch.set_alpha(0.75)

        for pos, values in zip(positions, data, strict=True):
            ax.scatter(
                [pos] * len(values),
                values,
                s=28,
                color="black",
                alpha=0.35,
                zorder=3,
            )

        ax.set_xticks(positions, loads)
        ax.set_title(label_for(scenario))
        ax.set_xlabel("Load level")
        ax.set_ylabel("Remediation time (s)")
        ax.grid(axis="y", linestyle="--", alpha=0.35)

    legend_handles = [
        Patch(facecolor=LOAD_COLORS[level], alpha=0.75, label=level)
        for level in LOAD_ORDER
        if any(row["load"] == level for row in rows)
    ]
    if legend_handles:
        fig.legend(handles=legend_handles, loc="upper center", ncol=len(legend_handles), frameon=False)

    fig.suptitle("Remediation latency by scenario and load level", y=1.02)
    fig.tight_layout()
    fig.savefig(output, dpi=200, bbox_inches="tight")
    plt.close(fig)


def plot_mean_with_ci(rows: list[dict], output: Path) -> None:
    """Overview bar chart: mean remediation time per scenario × load."""
    buckets: dict[tuple[str, str], list[float]] = defaultdict(list)
    for row in rows:
        buckets[(row["scenario"], row["load"])].append(row["remediation_s"])

    scenarios = sorted({scenario for scenario, _ in buckets}, key=scenario_sort_key)
    loads = ordered_loads({load for _, load in buckets})
    if not scenarios or not loads:
        raise SystemExit("No remediation data to plot.")

    x_base = range(len(scenarios))
    width = 0.8 / len(loads)

    fig, ax = plt.subplots(figsize=(max(10, 2.5 * len(scenarios)), 6))

    for i, load in enumerate(loads):
        means: list[float] = []
        errors: list[float] = []
        for scenario in scenarios:
            values = buckets.get((scenario, load), [])
            if len(values) >= 2:
                means.append(statistics.mean(values))
                errors.append(statistics.stdev(values))
            elif len(values) == 1:
                means.append(values[0])
                errors.append(0.0)
            else:
                means.append(float("nan"))
                errors.append(0.0)

        offset = (i - (len(loads) - 1) / 2) * width
        xs = [x + offset for x in x_base]
        ax.bar(
            xs,
            means,
            width=width * 0.9,
            yerr=errors,
            capsize=5,
            error_kw={"linewidth": 1.5, "capthick": 1.5},
            label=load,
            color=LOAD_COLORS.get(load, "#72B7B2"),
            alpha=0.85,
        )

    ax.set_xticks(list(x_base), [label_for(s) for s in scenarios], rotation=15, ha="right")
    ax.set_ylabel("Mean remediation time (s)")
    # ax.set_title("Mean remediation latency (± stdev) by scenario and load")
    ax.legend(title="Load level")
    ax.grid(axis="y", linestyle="--", alpha=0.35)
    fig.tight_layout()
    fig.savefig(output, dpi=200, bbox_inches="tight")
    plt.close(fig)


def print_summary(rows: list[dict]) -> None:
    buckets: dict[tuple[str, str], list[float]] = defaultdict(list)
    for row in rows:
        buckets[(row["scenario"], row["load"])].append(row["remediation_s"])

    print("Remediation time summary (seconds):")
    for scenario in sorted({s for s, _ in buckets}, key=scenario_sort_key):
        print(f"  {label_for(scenario)}")
        for load in ordered_loads({load for s, load in buckets if s == scenario}):
            values = buckets[(scenario, load)]
            if not values:
                continue
            mean = statistics.mean(values)
            stdev = statistics.stdev(values) if len(values) > 1 else 0.0
            print(f"    {load:6s}  n={len(values):2d}  mean={mean:6.2f}  stdev={stdev:5.2f}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--input",
        type=Path,
        default=Path("experiment_results.csv"),
        help="CSV produced by run_experiments.sh",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("figures"),
        help="Directory for PNG outputs",
    )
    parser.add_argument(
        "--status",
        choices=("success", "all"),
        default="success",
        help="Include only SUCCESS rows, or any row with a numeric remediation time",
    )
    args = parser.parse_args()

    if not args.input.is_file():
        print(f"Input file not found: {args.input}", file=sys.stderr)
        return 1

    rows = load_rows(args.input, status=args.status)
    if not rows:
        print("No rows with numeric RemediationTimeSeconds found.", file=sys.stderr)
        return 1

    args.output_dir.mkdir(parents=True, exist_ok=True)
    configure_plot_style()
    boxplot_path = args.output_dir / "remediation_time_by_scenario.png"
    mean_path = args.output_dir / "remediation_time_mean_by_scenario.png"

    plot_boxplots_by_scenario(rows, boxplot_path)
    plot_mean_with_ci(rows, mean_path)
    print_summary(rows)
    print(f"\nWrote {boxplot_path}")
    print(f"Wrote {mean_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
