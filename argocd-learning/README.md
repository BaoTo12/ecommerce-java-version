# ArgoCD GitOps — Learning Reference

> **Use this folder as a reference.** Practice in `argocd/` in the project root.

## What is GitOps?

GitOps is a pattern where **Git is the single source of truth** for the desired state
of your infrastructure and applications.

```
Traditional Deployment:           GitOps Deployment:
Developer → kubectl apply         Developer → git push
                ↓                                ↓
         Kubernetes                   ArgoCD detects change
                                               ↓
                                      ArgoCD applies to k8s
```

**Key principle:** You never `kubectl apply` directly in production.
All changes go through Git → ArgoCD detects → ArgoCD applies. Always.

## How ArgoCD Works

```
┌──────────────────────────────────────────────────────────────┐
│                    ARGOCD SYNC LOOP                          │
│                                                              │
│   Desired State (Git)          Actual State (Kubernetes)     │
│   charts/values/               kubectl get deployment        │
│   order-service.yaml           order-service                 │
│   image.tag: main-a1b2c3d  vs  image: main-e5f6g7h          │
│                   └─────── DIFF ──────┘                      │
│                                ↓                             │
│              ArgoCD applies the diff                         │
│              kubectl set image ... main-a1b2c3d              │
└──────────────────────────────────────────────────────────────┘
```

ArgoCD continuously polls your Git repo (or uses webhooks for instant detection).
When it detects a difference between what's in Git and what's running in k8s, it syncs.

## Folder Structure

```
argocd-learning/
│
├── README.md                             ← You are here
├── CONCEPTS.md                           ← Deep dive into GitOps concepts
│
├── install/
│   └── argocd-install.md                 ← How to install ArgoCD on EKS
│
├── applications/
│   ├── app-of-apps.yaml                  ← Root application that manages all others
│   ├── order-service.yaml                ← Application for order-service
│   ├── user-service.yaml                 ← Application for user-service
│   ├── inventory-service.yaml
│   ├── payment-service.yaml
│   └── notification-service.yaml
│
└── external-secrets/
    ├── external-secrets-operator.yaml    ← Install ESO to sync AWS Secrets Manager
    ├── secret-store.yaml                 ← Connect ESO to AWS Secrets Manager
    └── external-secret.yaml             ← Define which secrets to sync
```

## App-of-Apps Pattern

Instead of manually applying each Application YAML, we use **App-of-Apps**:

```
argocd-root (App)              ← You create this ONCE manually
    │
    ├── order-service (App)    ← Managed by ArgoCD
    ├── user-service (App)     ← Managed by ArgoCD
    ├── inventory-service (App)
    ├── payment-service (App)
    └── notification-service (App)
```

Adding a new service = just add a new Application YAML to git → ArgoCD auto-creates it.

## Bugs Fixed vs Original argocd/application.yaml

| Issue | Original | Fixed |
|-------|---------|-------|
| `image.tag: latest` | Never rollback possible | Use git SHA tag |
| Region in ECR URL | `us-east-1` | `ap-southeast-1` |
| Only one service | Just order-service | App-of-Apps for all 5 |
| No External Secrets | Passwords as plain env vars | ESO syncs Secrets Manager |
| No health checks | ArgoCD doesn't know if healthy | Added healthCheck annotations |
