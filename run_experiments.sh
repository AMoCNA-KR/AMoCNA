#!/bin/bash
#
# Run thesis benchmark experiments and collect:
#   - remediation latency (§4.3.3)
#   - MRE-K and sock-shop resource usage (§4.3.4)
#
# Outputs:
#   experiment_results.csv  — combined latency + resource metrics per iteration
#
# Quick trial (1 scenario, 1 iteration):
#   TRIAL=1 ./run_experiments.sh

set -euo pipefail

SCENARIOS=("1" "2" "5" "7")
ITERATIONS=10
LOAD_LEVELS=("none" "medium" "high")

if [ "${TRIAL:-0}" = "1" ]; then
    SCENARIOS=("1")
    ITERATIONS=1
    LOAD_LEVELS=("medium")
    echo ">> TRIAL mode: scenario 1, load medium, 1 iteration"
fi

if ! [[ "$ITERATIONS" =~ ^[0-9]+$ ]] || [ "$ITERATIONS" -lt 1 ]; then
    echo "[!] ITERATIONS must be a positive integer (got: '$ITERATIONS')"
    echo "    Use ITERATIONS=10 or TRIAL=1. In bash, '1#10' is invalid — # starts a comment."
    exit 1
fi

OUTPUT_FILE="experiment_results.csv"
EXTRACT_SCRIPT="scripts/extract_experiment_results.py"

CSV_HEADER="Scenario,LoadLevel,Iteration,Status,RemediationTimeSeconds,TotalDurationSeconds,MrekCpuAvgCores,MrekCpuMaxCores,MrekMemoryAvgMiB,MrekMemoryMaxMiB,SockShopCpuAvgCores,SockShopCpuMaxCores,SockShopMemoryAvgMiB,SockShopMemoryMaxMiB,LogFile"

# Must not fail when no logs exist yet (pipefail + set -e would otherwise exit the script).
find_latest_log() {
    local scenario="$1"
    shopt -s nullglob
    local files=(benchmark_log_${scenario}_*.json)
    shopt -u nullglob
    if [ ${#files[@]} -eq 0 ]; then
        echo ""
        return 0
    fi
    ls -t "${files[@]}" | head -n 1
}

safe_benchmark_stop() {
    set +e
    ./amocna.py benchmark stop
    local code=$?
    set -e
    if [ "$code" -ne 0 ]; then
        echo "      [!] benchmark stop failed (exit $code) — check: kubectl cluster-info"
    fi
}

record_iteration() {
    local scenario="$1"
    local load="$2"
    local iteration="$3"
    local exit_code="$4"
    local log_file="${5:-}"

    if [ -z "$log_file" ] || [ ! -f "$log_file" ]; then
        log_file="$(find_latest_log "$scenario")"
    fi

    if [ -z "$log_file" ] || [ ! -f "$log_file" ]; then
        echo "      [!] No benchmark log found for scenario $scenario"
        echo "$scenario,$load,$iteration,FAILED,N/A,N/A,N/A,N/A,N/A,N/A,N/A,N/A,N/A,N/A,N/A" >> "$OUTPUT_FILE"
        return
    fi

    local row=""
    set +e
    row=$(uv run python "$EXTRACT_SCRIPT" \
        --scenario "$scenario" \
        --load "$load" \
        --iteration "$iteration" \
        --exit-code "$exit_code" \
        --log-file "$log_file")
    local extract_code=$?
    set -e

    if [ "$extract_code" -ne 0 ] || [ -z "$row" ]; then
        echo "      [!] Metric extraction failed for $log_file"
        echo "$scenario,$load,$iteration,FAILED,N/A,N/A,N/A,N/A,N/A,N/A,N/A,N/A,N/A,N/A,$log_file" >> "$OUTPUT_FILE"
        return
    fi

    echo "$row" >> "$OUTPUT_FILE"
    echo "      [✓] $row"
}

echo "======================================================"
echo " Starting AMoCNA Thesis Experiments Automation"
echo "======================================================"

if [ ! -f "$EXTRACT_SCRIPT" ]; then
    echo "[!] Missing $EXTRACT_SCRIPT"
    exit 1
fi

if ! kubectl cluster-info &>/dev/null; then
    echo "[!] kubectl cannot reach the cluster — fix connectivity before running experiments"
    exit 1
fi

echo "$CSV_HEADER" > "$OUTPUT_FILE"

echo ">> Resetting cluster to baseline before experiments..."
safe_benchmark_stop

TOTAL_RUNS=$(( ${#LOAD_LEVELS[@]} * ${#SCENARIOS[@]} * ITERATIONS ))
echo ">> Planned runs: $TOTAL_RUNS (${#SCENARIOS[@]} scenarios × ${#LOAD_LEVELS[@]} load levels × $ITERATIONS iterations)"
echo ">> Each benchmark takes ~10–15 min — use tmux/screen for the full batch"

for LOAD in "${LOAD_LEVELS[@]}"; do
    echo ">> Testing Load Level: $LOAD"

    for SCENARIO in "${SCENARIOS[@]}"; do
        echo "  -> Running Scenario $SCENARIO ($ITERATIONS iterations)"

        for ((i=1; i<=ITERATIONS; i++)); do
            echo "    -> Iteration $i / $ITERATIONS (expect ~10–15 min)"
            echo "       $(date '+%H:%M:%S') starting benchmark..."

            LOG_BEFORE="$(find_latest_log "$SCENARIO")"

            set +e
            PYTHONUNBUFFERED=1 ./amocna.py benchmark run --scenario "$SCENARIO" --load "$LOAD"
            EXIT_CODE=$?
            set -e

            LOG_AFTER="$(find_latest_log "$SCENARIO")"
            LOG_FILE="$LOG_AFTER"

            record_iteration "$SCENARIO" "$LOAD" "$i" "$EXIT_CODE" "$LOG_FILE"

            if [ "$EXIT_CODE" -ne 0 ]; then
                echo "      [!] Scenario $SCENARIO failed on iteration $i — running benchmark stop"
                safe_benchmark_stop
            fi

            echo "      -> Cooling down 30s before next iteration..."
            sleep 30
        done

        echo "  -> Finished Scenario $SCENARIO under $LOAD load."
        echo "  -> Running benchmark stop before next scenario..."
        safe_benchmark_stop
        sleep 15
    done
done

echo "======================================================"
echo " Experiments Complete!"
echo " Results saved to $OUTPUT_FILE"
echo "======================================================"
