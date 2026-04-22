### How to install ArgoCD on AWS EKS

EKS is running on AWS (infrastructure is find)
Assume we are on local machine so how to connect to EKS on AWS

> Local machine (kubectl) ──► ??? ──► EKS API Server (AWS Cloud)

- kubectl đọc file ~/.kube/config để biết cluster endpoint + credentials. - File này chưa có → mọi lệnh đều fail.

```
aws eks update-kubeconfig --region ap-southeast-1 --name <tên-eks-cluster-của-bạn>
```

--> Lệnh này tự động ghi vào ~/.kube/config:

- Cluster endpoint
- CA certificate
- Auth token mechanism (dùng AWS IAM)

### Verify kết nối

- kubectl cluster-info
    > Kubernetes control plane is running at https://XXXX.gr7.ap-southeast-1.eks.amazonaws.com

> kubectl get nodes
> -> This commands show nodes are running on that cluster
> -> Phải thấy nodes ở status Ready

### How to Install ArgoCD on EKS

#### Step 1 — Install ArgoCD

```bash
# Create dedicated namespace
kubectl create namespace argocd

# Install ArgoCD using official manifests
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# Wait for all pods to be ready (~2 minutes)
kubectl wait --for=condition=Ready pods --all -n argocd --timeout=300s
```

## Step 2 — Access ArgoCD UI

```bash
# Option A: Port-forward (easiest for development)
kubectl port-forward svc/argocd-server -n argocd 8080:443

# Then open: https://localhost:8080

# Option B: LoadBalancer service (for permanent access)
kubectl patch svc argocd-server -n argocd -p '{"spec": {"type": "LoadBalancer"}}'
kubectl get svc argocd-server -n argocd  # Wait for EXTERNAL-IP
```

## Step 3 — Get Initial Admin Password

```bash
# ArgoCD generates a random password stored in a secret
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath="{.data.password}" | base64 -d && echo

# Login with username: admin, password: <above>
```

## Step 4 — Connect to Your EKS Cluster

```bash
# Install ArgoCD CLI
# Windows: https://github.com/argoproj/argo-cd/releases/latest

# Login
argocd login localhost:8080 --username admin --password <your-password> --insecure

# Add your EKS cluster (if ArgoCD is running IN the cluster, this is optional)
# ArgoCD uses https://kubernetes.default.svc to refer to the local cluster
```

## Step 5 — Bootstrap App-of-Apps

```bash
# This one command creates ALL Application resources
kubectl apply -f argocd/applications/app-of-apps.yaml

# ArgoCD will discover and create:
# - order-service Application
# - user-service Application
# - inventory-service Application
# - payment-service Application
# - notification-service Application
```

## Step 6 — Verify

```bash
# List all Applications
argocd app list

# Check sync status of order-service
argocd app get order-service

# Manually trigger sync (if not using automated sync)
argocd app sync order-service
```

## Useful ArgoCD CLI Commands

```bash
# See what would change (dry run)
argocd app diff order-service

# Rollback to previous deployment
argocd app rollback order-service

# Set image manually (bypasses GitOps — only for emergencies)
argocd app set order-service --helm-set image.tag=main-a1b2c3d

# Get app history (all deployments)
argocd app history order-service

# Delete an app (with cascade=true to also delete k8s resources)
argocd app delete order-service --cascade
```

## GitHub Webhook (for instant sync instead of polling)

By default ArgoCD polls Git every 3 minutes. For faster sync:

1. In GitHub repo → Settings → Webhooks → Add webhook
2. Payload URL: `https://<argocd-server>/api/webhook`
3. Content type: `application/json`
4. Secret: generate a random string, save it
5. Events: Just the "push" event
