#!/bin/sh
# SCIG Sensor Engine: generate SBOMs with Syft, scan CVE vulnerabilities with Grype, and store results in Redis.
# Keys:
#   sbom:repo:{repository}:{tag} -> syft-json (SBOM)
#   sbom:cve:{repository}:{tag}  -> JSON vulnerability scan report (list of CVEs, severities, fix versions)
#   sbom:meta:{repository}:{tag} -> JSON metadata (scannedAt, imageRef, packageCount, vulnerabilityCount, criticalCount, highCount, etc.)

set -eu

REDIS_HOST="${REDIS_HOST:-redis.redis.svc.cluster.local}"
REDIS_PORT="${REDIS_PORT:-6379}"
SBOM_TTL_SECONDS="${SBOM_TTL_SECONDS:-172800}"
IMAGE_LIST_FILE="${IMAGE_LIST_FILE:-/config/images.txt}"
DISCOVER_CLUSTER_IMAGES="${DISCOVER_CLUSTER_IMAGES:-false}"
SCAN_NAMESPACES="${SCAN_NAMESPACES:-sock-shop}"

log() { echo "[scig] $*"; }

require_tools() {
  for tool in syft redis-cli; do
    if ! command -v "$tool" >/dev/null 2>&1; then
      log "ERROR: missing required tool '$tool' in image"
      exit 1
    fi
  done

  if ! command -v grype >/dev/null 2>&1; then
    log "WARN: 'grype' not found in PATH, CVE vulnerability scanning will fallback to empty results"
  fi

  if [ "${DISCOVER_CLUSTER_IMAGES}" = "true" ] && ! command -v kubectl >/dev/null 2>&1; then
    log "ERROR: DISCOVER_CLUSTER_IMAGES=true requires kubectl in image"
    exit 1
  fi
}

normalize_image() {
  echo "$1" | sed 's/@sha256:.*//'
}

repo_of() {
  img="$(normalize_image "$1")"
  echo "${img%:*}"
}

tag_of() {
  img="$(normalize_image "$1")"
  case "$img" in
    *:*) echo "${img##*:}" ;;
    *) echo "latest" ;;
  esac
}

redis_key_repo() {
  echo "sbom:repo:${1}:${2}"
}

redis_key_cve() {
  echo "sbom:cve:${1}:${2}"
}

redis_key_meta() {
  echo "sbom:meta:${1}:${2}"
}

wait_for_redis() {
  log "Waiting for Redis at ${REDIS_HOST}:${REDIS_PORT}..."
  i=0
  while [ "$i" -lt 30 ]; do
    if redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" ping 2>/dev/null | grep -q PONG; then
      log "Redis is ready"
      return 0
    fi
    i=$((i + 1))
    sleep 2
  done
  log "ERROR: Redis not reachable"
  exit 1
}

collect_images() {
  TMP="$(mktemp)"
  if [ -f "${IMAGE_LIST_FILE}" ]; then
    grep -v '^[[:space:]]*$' "${IMAGE_LIST_FILE}" | grep -v '^[[:space:]]*#' >> "${TMP}" || true
  fi

  if [ "${DISCOVER_CLUSTER_IMAGES}" = "true" ]; then
    log "Discovering images in namespaces: ${SCAN_NAMESPACES}"
    OLD_IFS="$IFS"
    IFS=','
    for ns in ${SCAN_NAMESPACES}; do
      IFS="$OLD_IFS"
      ns="$(echo "$ns" | tr -d ' ')"
      [ -n "$ns" ] || continue
      kubectl get pods -n "$ns" \
        -o jsonpath='{range .items[*]}{range .spec.containers[*]}{.image}{"\n"}{end}{range .spec.initContainers[*]}{.image}{"\n"}{end}{end}' \
        2>/dev/null >> "${TMP}" || true
    done
    IFS="$OLD_IFS"
  fi

  sort -u "${TMP}" | grep -v '^[[:space:]]*$' || true
  rm -f "${TMP}"
}

scan_and_store() {
  image_ref="$1"
  repo="$(repo_of "$image_ref")"
  tag="$(tag_of "$image_ref")"
  key="$(redis_key_repo "$repo" "$tag")"
  cve_key="$(redis_key_cve "$repo" "$tag")"
  meta_key="$(redis_key_meta "$repo" "$tag")"
  out_sbom="$(mktemp)"
  out_cve="$(mktemp)"

  log "Scanning SBOM for ${image_ref} (repo=${repo} tag=${tag})..."
  if ! syft scan "${image_ref}" -o "syft-json=${out_sbom}" --quiet; then
    log "WARN: syft failed for ${image_ref}, skipping"
    rm -f "${out_sbom}" "${out_cve}"
    return 0
  fi

  pkg_count="$(grep -o '"id":' "${out_sbom}" | wc -l | tr -d ' ')"
  scanned_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

  # Scan vulnerabilities via Grype if available
  vuln_count=0
  critical_count=0
  high_count=0
  medium_count=0

  if command -v grype >/dev/null 2>&1; then
    log "Scanning CVE vulnerabilities for ${image_ref} with Grype..."
    if grype "sbom:${out_sbom}" -o json --quiet > "${out_cve}" 2>/dev/null; then
      if command -v jq >/dev/null 2>&1; then
        vuln_count="$(jq '.matches | length' "${out_cve}" 2>/dev/null || echo 0)"
        critical_count="$(jq '[.matches[] | select(.vulnerability.severity=="Critical")] | length' "${out_cve}" 2>/dev/null || echo 0)"
        high_count="$(jq '[.matches[] | select(.vulnerability.severity=="High")] | length' "${out_cve}" 2>/dev/null || echo 0)"
        medium_count="$(jq '[.matches[] | select(.vulnerability.severity=="Medium")] | length' "${out_cve}" 2>/dev/null || echo 0)"
      fi
    else
      log "WARN: grype scan failed for ${image_ref}, outputting empty CVE report"
      echo '{"matches":[]}' > "${out_cve}"
    fi
  else
    echo '{"matches":[]}' > "${out_cve}"
  fi

  # Scan vulnerabilities via Trivy if available
  trivy_vuln_count=0
  trivy_critical_count=0
  trivy_high_count=0
  trivy_medium_count=0
  out_trivy="$(mktemp)"

  if command -v trivy >/dev/null 2>&1; then
    log "Scanning CVE vulnerabilities for ${image_ref} with Trivy..."
    if trivy image --format json --scanners vuln "${image_ref}" --quiet > "${out_trivy}" 2>/dev/null; then
      if command -v jq >/dev/null 2>&1; then
        trivy_vuln_count="$(jq '[.Results[]?.Vulnerabilities // [] | length] | add // 0' "${out_trivy}" 2>/dev/null || echo 0)"
        trivy_critical_count="$(jq '[.Results[]?.Vulnerabilities // [] | map(select(.Severity=="CRITICAL")) | length] | add // 0' "${out_trivy}" 2>/dev/null || echo 0)"
        trivy_high_count="$(jq '[.Results[]?.Vulnerabilities // [] | map(select(.Severity=="HIGH")) | length] | add // 0' "${out_trivy}" 2>/dev/null || echo 0)"
        trivy_medium_count="$(jq '[.Results[]?.Vulnerabilities // [] | map(select(.Severity=="MEDIUM")) | length] | add // 0' "${out_trivy}" 2>/dev/null || echo 0)"
      fi
    else
      log "WARN: trivy scan failed for ${image_ref}, outputting empty report"
      echo '{"Results":[]}' > "${out_trivy}"
    fi
  else
    echo '{"Results":[]}' > "${out_trivy}"
  fi

  # Store SBOM in Redis
  redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" -x SET "${key}" < "${out_sbom}" >/dev/null
  redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" EXPIRE "${key}" "${SBOM_TTL_SECONDS}" >/dev/null

  # Store Grype CVE results in Redis
  redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" -x SET "${cve_key}" < "${out_cve}" >/dev/null
  redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" EXPIRE "${cve_key}" "${SBOM_TTL_SECONDS}" >/dev/null

  # Store Trivy CVE results in Redis
  trivy_key="sbom:trivy:${repo}:${tag}"
  redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" -x SET "${trivy_key}" < "${out_trivy}" >/dev/null
  redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" EXPIRE "${trivy_key}" "${SBOM_TTL_SECONDS}" >/dev/null

  # Store Metadata in Redis
  meta="{\"imageRef\":\"${image_ref}\",\"repository\":\"${repo}\",\"tag\":\"${tag}\",\"scannedAt\":\"${scanned_at}\",\"packageCount\":${pkg_count},\"vulnerabilityCount\":${vuln_count},\"criticalCount\":${critical_count},\"highCount\":${high_count},\"mediumCount\":${medium_count},\"trivyVulnerabilityCount\":${trivy_vuln_count},\"trivyCriticalCount\":${trivy_critical_count},\"trivyHighCount\":${trivy_high_count},\"trivyMediumCount\":${trivy_medium_count},\"ttlSeconds\":${SBOM_TTL_SECONDS}}"
  printf '%s' "${meta}" | redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" -x SET "${meta_key}" >/dev/null
  redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" EXPIRE "${meta_key}" "${SBOM_TTL_SECONDS}" >/dev/null

  log "Stored ${key} (packages=${pkg_count}, Grype CVEs=${vuln_count} [Crit:${critical_count}, High:${high_count}], Trivy CVEs=${trivy_vuln_count} [Crit:${trivy_critical_count}, High:${trivy_high_count}])"
  rm -f "${out_sbom}" "${out_cve}" "${out_trivy}"
}

main() {
  require_tools
  wait_for_redis

  IMG_FILE="$(mktemp)"
  collect_images > "${IMG_FILE}"
  if [ ! -s "${IMG_FILE}" ]; then
    log "ERROR: no images to scan (fill /config/images.txt or set DISCOVER_CLUSTER_IMAGES=true)"
    rm -f "${IMG_FILE}"
    exit 1
  fi

  log "Images to scan:"
  cat "${IMG_FILE}"

  while IFS= read -r img; do
    [ -n "${img}" ] || continue
    scan_and_store "${img}"
  done < "${IMG_FILE}"
  rm -f "${IMG_FILE}"

  log "Done. SCIG Redis keys created:"
  redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" --scan --pattern 'sbom:*' || true
}

main "$@"
