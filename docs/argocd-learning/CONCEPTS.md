aws eks describe-nodegroup --cluster-name ecommerce-eks --nodegroup-name ecommerce-eks-node-group --region ap-southeast-1

## Concept 3: App-of-Apps Pattern vs Individual Applications

### Naive Approach (bad for many services)
```bash
# Must run this manually for each service
kubectl apply -f argocd/order-service.yaml
kubectl apply -f argocd/user-service.yaml
...
```
This is NOT GitOps — you're manually applying.

### App-of-Apps (correct GitOps approach)
```
You create ONE "root" application manually:
  kubectl apply -f argocd-learning/applications/app-of-apps.yaml

Root app watches: argocd-learning/applications/
Root app finds: order-service.yaml, user-service.yaml, ...
Root app creates those Applications automatically.

Now EVERYTHING is managed by ArgoCD — even the ArgoCD Applications themselves.
```

Adding a new service:
1. Create `argocd-learning/applications/new-service.yaml`
2. Push to git
3. Root app detects it → creates the new Application → ArgoCD deploys new-service

Zero manual `kubectl apply` after the initial bootstrap!

---

## Concept 4: Why External Secrets Operator instead of hardcoded env vars?

### The Problem with the original approach
```yaml
# BAD — From original argocd/application.yaml:
env:
  - name: SPRING_DATASOURCE_PASSWORD
    value: "secret"   # ← HARDCODED IN GIT! Anyone with repo access sees this
```

### External Secrets Operator (ESO) — Correct Approach

```
AWS Secrets Manager ← Source of Truth
  "ecommerce/prod/db-credentials": {"password": "RealPassword123!"}
        ↓
External Secrets Operator (controller running in k8s)
  Reads from Secrets Manager using IRSA (IAM role)
        ↓
Creates/updates Kubernetes Secret
  kubectl get secret ecommerce-db-credentials
        ↓
Pod references secret via secretKeyRef
  SPRING_DATASOURCE_PASSWORD → from secret → "RealPassword123!"
```

The password **never appears in Git**. Only in AWS Secrets Manager.
Git contains only a reference (`secretKeyRef: name: ecommerce-db-credentials`).

ESO also handles **automatic rotation**: when you rotate the secret in Secrets Manager,
ESO detects it (based on `refreshInterval`) and updates the Kubernetes Secret.

---

## Concept 5: sync-wave and resource ordering

Sometimes resources must be created in order. Example:
- ExternalSecret must exist BEFORE deployment (otherwise secretKeyRef fails)
- Namespace must exist BEFORE Application deploys to it

ArgoCD uses **sync-waves** to order resources:

```yaml
# ExternalSecret — create in wave 0 (first)
metadata:
  annotations:
    argocd.argoproj.io/sync-wave: "0"

# Deployment — create in wave 1 (after secrets exist)
metadata:
  annotations:
    argocd.argoproj.io/sync-wave: "1"
```

Wave 0 resources are applied and healthy before wave 1 starts.
