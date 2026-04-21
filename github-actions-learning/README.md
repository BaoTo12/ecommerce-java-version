# GitHub Actions CI/CD — Learning Reference

> **Use this folder as a reference.** Practice in `.github/workflows/` and `charts/`.

## What This Pipeline Does (The Full GitOps Loop)

```
Developer pushes code
        │
        ▼
┌───────────────────────────────────────────────────────┐
│              GITHUB ACTIONS (CI)                      │
│                                                       │
│  Job 1: build-and-push (matrix — runs for each svc)  │
│    ├── Compile Java with Maven                        │
│    ├── Run unit tests                                 │
│    ├── Build Docker image                             │
│    └── Push to ECR with git SHA tag                   │
│                                                       │
│  Job 2: update-helm-tag (runs after Job 1, main only) │
│    ├── Update charts/values/<service>.yaml            │
│    └── Commit & push to repo (triggers ArgoCD)        │
└───────────────────────────────────────────────────────┘
        │
        │  Git commit with new image tag
        ▼
┌───────────────────────────────────────────────────────┐
│              ARGOCD (CD — see argocd-learning/)       │
│                                                       │
│    Detects git change in charts/values/               │
│    Diffs desired state (git) vs actual state (k8s)    │
│    Applies the diff → rolling update in EKS           │
└───────────────────────────────────────────────────────┘
```

## Folder Structure

```
github-actions-learning/
│
├── README.md                          ← You are here
├── CONCEPTS.md                        ← Read this — explains the WHYs
│
├── workflows/
│   ├── ci.yaml                        ← Main CI pipeline (build + push + update)
│   ├── terraform.yaml                 ← Separate pipeline for Terraform changes
│   └── pr-check.yaml                  ← Pull request validation (tests only, no push)
│
└── helm-charts/                       ← The Helm chart deployed by ArgoCD
    ├── microservice/                  ← Single reusable chart for ALL services
    │   ├── Chart.yaml
    │   ├── values.yaml                ← Default values (overridden per service)
    │   └── templates/
    │       ├── _helpers.tpl           ← Reusable template functions
    │       ├── deployment.yaml        ← Kubernetes Deployment
    │       ├── service.yaml           ← Kubernetes Service
    │       ├── serviceaccount.yaml    ← Kubernetes ServiceAccount (with IRSA annotation)
    │       ├── hpa.yaml               ← HorizontalPodAutoscaler
    │       └── ingress.yaml           ← AWS Load Balancer Controller Ingress
    │
    └── values/                        ← Per-service value overrides
        ├── order-service.yaml
        ├── user-service.yaml
        ├── inventory-service.yaml
        ├── payment-service.yaml
        └── notification-service.yaml
```

## Bugs Fixed vs Original Code

| File | Bug | Fix |
|------|-----|-----|
| `ci.yaml` | Comments in Vietnamese mixed with English | All English |
| `ci.yaml` | `matrix` output `image-tag` only tracks one service | Fixed with per-service output |
| `ci.yaml` | `cache` tag pushed to IMMUTABLE ECR — fails! | Use separate mutable cache repo |
| `ci.yaml` | Builds ALL services even if only one changed | Added `paths-filter` step |
| `values/order-service.yaml` | DB password hardcoded as `"secret"` | Use External Secrets Operator |
| `values/order-service.yaml` | Region `us-east-1` doesn't match `ap-southeast-1` | Fixed |
| `deployment.yaml` | No `securityContext` | Added non-root user |
| `deployment.yaml` | Missing health probe `port: http` named port | Fixed |
| `argocd/application.yaml` | `image.tag: latest` — never do this | Fixed to use git SHA tag |
| `argocd/application.yaml` | Region `us-east-1` in ECR URL | Fixed to `ap-southeast-1` |

## GitHub Secrets Required

Set these in GitHub → Settings → Secrets and variables → Actions:

| Secret Name | Value | How to Get |
|------------|-------|-----------|
| `AWS_ROLE_ARN` | IAM role ARN for OIDC | From iam module output |
| `GH_PAT` | GitHub Personal Access Token | GitHub Settings → Developer → PAT (with `repo` scope) |

**Why `GH_PAT` and not `GITHUB_TOKEN`?**
`GITHUB_TOKEN` cannot trigger other workflows (security restriction).
When Job 2 pushes updated Helm values, we want ArgoCD to detect it.
`GITHUB_TOKEN` push events are intentionally ignored by GitHub Actions to prevent loops.
`GH_PAT` bypasses this — its push events DO trigger other workflows.

## GitHub OIDC Auth to AWS (Why No Access Keys)

The pipeline uses `aws-actions/configure-aws-credentials@v4` with `role-to-assume`.
This is the modern, secure way to authenticate GitHub Actions to AWS:

```
GitHub Actions runner
    │
    │ Requests OIDC JWT from GitHub
    ▼
GitHub OIDC Provider
    │
    │ JWT: "I am repo BaoTo12/ecommerce-java-version, branch main"
    ▼
AWS STS: AssumeRoleWithWebIdentity
    │
    │ Verifies JWT against GitHub's OIDC endpoint
    │ Checks trust policy: "Allow if repo = BaoTo12/ecommerce-java-version"
    ▼
Temporary credentials (15 min TTL)
    │
    ▼
GitHub Actions uses credentials for ECR push, helm update, etc.
```

No long-lived access keys stored anywhere. If credentials leak, they expire in 15 minutes.
