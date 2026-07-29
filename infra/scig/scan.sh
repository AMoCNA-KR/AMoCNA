#!/bin/sh
# SCIG PoC: generate SBOMs with Syft and store them in Redis.
# Keys:
#   sbom:repo:{repository}:{tag}  -> syft-json
#   sbom:meta:{repository}:{tag}  -> JSON metadata (scannedAt, imageRef, packageCount)
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
  meta_key="$(redis_key_meta "$repo" "$tag")"
  out="$(mktemp)"

  log "Scanning ${image_ref} (repo=${repo} tag=${tag})..."
  if ! syft scan "${image_ref}" -o "syft-json=${out}" --quiet; then
    log "WARN: syft failed for ${image_ref}, skipping"
    rm -f "${out}"
    return 0
  fi

  pkg_count="$(grep -o '"id":' "${out}" | wc -l | tr -d ' ')"
  scanned_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

  redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" -x SET "${key}" < "${out}" >/dev/null
  redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" EXPIRE "${key}" "${SBOM_TTL_SECONDS}" >/dev/null

  meta="{\"imageRef\":\"${image_ref}\",\"repository\":\"${repo}\",\"tag\":\"${tag}\",\"scannedAt\":\"${scanned_at}\",\"packageCount\":${pkg_count},\"ttlSeconds\":${SBOM_TTL_SECONDS}}"
  printf '%s' "${meta}" | redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" -x SET "${meta_key}" >/dev/null
  redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" EXPIRE "${meta_key}" "${SBOM_TTL_SECONDS}" >/dev/null

  log "Stored ${key} (id-fields≈${pkg_count}, ttl=${SBOM_TTL_SECONDS}s)"
  rm -f "${out}"
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

  log "Done. Redis keys:"
  redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" --scan --pattern 'sbom:*' || true
}

main "$@"
