#!/bin/bash

# Exit on any error
set -e

# Scenarios to run
SCENARIOS=("1" "2" "5" "7")
# Number of times to repeat each scenario
ITERATIONS=10
# Load levels to test
LOAD_LEVELS=("none" "medium" "high")

# Output CSV file
OUTPUT_FILE="remediation_results.csv"

# Print CSV header
echo "Scenario,LoadLevel,Iteration,RemediationTimeSeconds,TotalDurationSeconds" > "$OUTPUT_FILE"

echo "======================================================"
echo " Starting AMoCNA Thesis Experiments Automation"
echo "======================================================"

# Loop through load levels
for LOAD in "${LOAD_LEVELS[@]}"; do
    echo ">> Testing Load Level: $LOAD"
    
    # Loop through each scenario
    for SCENARIO in "${SCENARIOS[@]}"; do
        echo "  -> Running Scenario $SCENARIO ($ITERATIONS iterations)"
        
        for ((i=1; i<=ITERATIONS; i++)); do
            echo "    -> Iteration $i / $ITERATIONS"
            
            # Start timer for the whole iteration run
            START_TIME=$(date +%s)
            
            # Run the scenario using the AMoCNA CLI
            # We allow it to fail, so we capture the exit code, but we set +e temporarily
            set +e
            ./amocna.py benchmark run --scenario "$SCENARIO" --load "$LOAD"
            EXIT_CODE=$?
            set -e
            
            if [ $EXIT_CODE -ne 0 ]; then
                echo "      [!] Scenario $SCENARIO (Load: $LOAD) failed on iteration $i. Skipping log extraction."
                continue
            fi
            
            # Find the latest benchmark log file for this scenario
            LATEST_LOG=$(ls -t benchmark_log_${SCENARIO}_*.json 2>/dev/null | head -n 1)
            
            if [ -z "$LATEST_LOG" ]; then
                echo "      [!] No log file found for Scenario $SCENARIO. Skipping extraction."
                continue
            fi
            
            # Extract remediation time using jq
            # We look for the event type "REMEDIATION_TIME_SECONDS"
            REMEDIATION_TIME=$(jq -r '.events[] | select(.type == "REMEDIATION_TIME_SECONDS") | .description' "$LATEST_LOG")
            TOTAL_DURATION=$(jq -r '.total_duration' "$LATEST_LOG")
            
            if [ -z "$REMEDIATION_TIME" ] || [ "$REMEDIATION_TIME" == "null" ]; then
                echo "      [!] REMEDIATION_TIME_SECONDS not found in $LATEST_LOG"
                REMEDIATION_TIME="N/A"
            fi
            
            echo "      [✓] Remediation Time: $REMEDIATION_TIME s"
            
            # Append to CSV
            echo "$SCENARIO,$LOAD,$i,$REMEDIATION_TIME,$TOTAL_DURATION" >> "$OUTPUT_FILE"
            
            # Brief cooldown between iterations
            sleep 10
        done
        
        echo "  -> Finished Scenario $SCENARIO under $LOAD load."
        echo "  -> Cooling down cluster for 30 seconds before next scenario..."
        sleep 30
    done
done

echo "======================================================"
echo " Experiments Complete!"
echo " Results saved to $OUTPUT_FILE"
echo "======================================================"
