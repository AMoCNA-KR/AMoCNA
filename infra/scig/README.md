# SCIG Syft sensor (PoC)

Generates Software Bills of Materials with [Syft](https://github.com/anchore/syft)
and stores them in Redis for the ASPOF SCIG pillar.

## Components

| Resource | Namespace | Role |
|----------|-----------|------|
| Redis Deployment/Service | `redis` | SBOM store |
| CronJob `scig-syft` | `amocna-scig` | Daily scan (`0 2 * * *` UTC) |

## Redis keys

- `sbom:repo:{repository}:{tag}` — Syft JSON
- `sbom:meta:{repository}:{tag}` — metadata (`scannedAt`, `imageRef`, `packageCount`, …)

TTL default: **48h** (`SBOM_TTL_SECONDS=172800`).

## Deploy

```bash
kubectl apply -k infra/redis
kubectl apply -k infra/scig
kubectl -n redis rollout status deployment/redis
```

Manual one-shot scan from the CronJob template:

```bash
kubectl -n amocna-scig create job scig-syft-manual-$(date +%s) --from=cronjob/scig-syft
kubectl -n amocna-scig logs -f job/scig-syft-manual-<id>
```

## Verify Redis

```bash
kubectl -n redis exec -it deploy/redis -- redis-cli KEYS 'sbom:*'
kubectl -n redis exec -it deploy/redis -- redis-cli GET 'sbom:meta:docker.io/weaveworksdemos/front-end:0.3.0'
```

## Image list

Edit [`images.txt`](images.txt) (default: Sock Shop `front-end:0.3.0`), then re-apply `infra/scig`.

To scan live pod images instead:

```bash
kubectl -n amocna-scig set env cronjob/scig-syft DISCOVER_CLUSTER_IMAGES=true
kubectl -n amocna-scig set env cronjob/scig-syft SCAN_NAMESPACES=sock-shop
```
