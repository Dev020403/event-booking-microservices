# Kubernetes deployment

Runs the same stack as `docker-compose.yml` on Kubernetes, using [Kustomize](https://kustomize.io/) (built into `kubectl`).

## Layout

```
k8s/
  base/                    # environment-independent manifests
    app-config.yaml        # shared non-secret env (Eureka URL, Kafka, Redis, mail, JVM flags)
    infra/                 # postgres (StatefulSet + PVC), redis, zookeeper + kafka, mailpit
    apps/                  # Deployment + Service per Spring Boot service
  overlays/
    local/                 # kind: app-secrets from secrets.env, image tag "local"
  build-images.ps1         # docker build + kind load for every service
```

| docker-compose | Kubernetes |
|---|---|
| service with `build:` | `Deployment` + `Service` (in-cluster DNS name = service name) |
| `.env` secrets | `Secret` `app-secrets`, generated from `overlays/local/secrets.env` |
| other `environment:` | `ConfigMap` `app-config` + per-service `SPRING_DATASOURCE_URL` |
| `postgres_data` volume | `StatefulSet` `volumeClaimTemplates` (PVC `data-postgres-db-0`) |
| `./docker/init-db.sql` mount | `ConfigMap` `postgres-init` (keep it in sync with `docker/init-db.sql`) |
| `depends_on`, `healthcheck` | startup / liveness / readiness probes on `/actuator/health/*`; pods restart until dependencies are up |
| `ports:` | none; use `kubectl port-forward` (or an Ingress, see below) |

Eureka is still deployed so the gateway's `lb://` routes work unchanged.

## Prerequisites

- Docker Desktop, [kind](https://kind.sigs.k8s.io/), `kubectl`
- About 6 GB of memory for Docker (8 JVMs plus Kafka and Postgres)

## First-time setup

```powershell
# 1. Cluster and namespace
kind create cluster --name event-booking
kubectl create namespace event-booking
kubectl config set-context --current --namespace=event-booking

# 2. Secrets (gitignored). Fill in POSTGRES_PASSWORD and JWT_SECRET (openssl rand -base64 32)
Copy-Item k8s/overlays/local/secrets.env.example k8s/overlays/local/secrets.env

# 3. Build the images and load them into the kind node (kind can't see your local Docker images otherwise)
./k8s/build-images.ps1

# 4. Deploy everything
kubectl apply -k k8s/overlays/local

# 5. Wait until all pods are Running and 1/1 ready (first start takes a few minutes)
kubectl get pods -w
```

## Accessing the services

Each command blocks; run them in separate terminals.

```powershell
kubectl port-forward svc/api-gateway 8080:8080        # API:      http://localhost:8080/api/v1/...
kubectl port-forward svc/service-registry 8761:8761   # Eureka:   http://localhost:8761
kubectl port-forward svc/mailpit 8025:8025            # Mailpit:  http://localhost:8025
kubectl port-forward svc/postgres-db 5432:5432        # psql / DB tools
```

## Day-to-day

```powershell
# Rebuild and redeploy one service after a code change
./k8s/build-images.ps1 -Services booking-service
kubectl rollout restart deployment/booking-service

# Logs, status, debugging
kubectl logs -f deployment/booking-service
kubectl describe pod <pod-name>          # probe failures, OOMKilled, image errors
kubectl get events --sort-by=.lastTimestamp

# Changed a manifest or secrets.env
kubectl apply -k k8s/overlays/local

# Tear down (keeps the cluster). The Postgres PVC is deleted with the namespace.
kubectl delete -k k8s/overlays/local
kind delete cluster --name event-booking  # removes everything
```

## Notes and known limitations

- **Kafka and Zookeeper store data in `emptyDir`**, as in docker-compose: topics are lost when their pods restart.
- **`enableServiceLinks: false`** is set on every pod. Without it Kubernetes injects `KAFKA_PORT`-style env vars that break the Confluent images.
- **Containers run as UID 10001**, not root.
- **Adding a database** to `postgres-init` only applies to a fresh volume. Create it with `psql` instead, or delete the `data-postgres-db-0` PVC, which wipes all data.

## Towards production

Add a `k8s/overlays/prod` overlay that:

- pushes images to a registry (GHCR, ECR, ACR) and sets `images:` to immutable tags
- uses managed Postgres, Kafka (or Strimzi with KRaft) and Redis instead of `base/infra`
- sources `app-secrets` from a secrets manager (External Secrets, Sealed Secrets), not a local file
- adds an `Ingress` with TLS in front of `api-gateway`, replicas > 1, `PodDisruptionBudget`s and `HorizontalPodAutoscaler`s
- replaces Eureka with Kubernetes Services (`uri: http://booking-service:8085` in the gateway routes)
