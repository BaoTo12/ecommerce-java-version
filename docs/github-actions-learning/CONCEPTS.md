# GitHub Actions + GitOps — Core Concepts Explained

---

## Concept 1: Why does ci.yaml have TWO jobs instead of one big job?

### The Problem with One Big Job
```yaml
# BAD: Everything in one job
jobs:
  everything:
    steps:
      - build
      - test
      - push
      - update-helm   ← runs even on PRs!
```
If a developer opens a PR, you do NOT want to push images to ECR or update Helm values.
That would deploy half-finished code to production with no review.

### Two-Job Design
```
Job 1: build-and-push    ← runs on: push to main AND pull_request
  - Build and TEST the code
  - Push image to ECR (only when code is ready to deploy)

Job 2: update-helm-tag   ← runs on: push to main ONLY
  if: github.ref == 'refs/heads/main' && github.event_name == 'push'
  - Update image tag in git
  - This triggers ArgoCD to deploy
```

### Why `needs: build-and-push` in Job 2?
`needs` creates a dependency: Job 2 only STARTS after Job 1 COMPLETES successfully.
If tests fail in Job 1, Job 2 never runs → production is never updated → safe!

---

## Concept 2: Why use matrix strategy for services?

### Without Matrix (Bad — Lots of Copy-Paste)
```yaml
jobs:
  build-order-service:
    steps: [...]
  build-payment-service:
    steps: [...]   # exactly the same steps!
  build-inventory-service:
    steps: [...]   # exactly the same steps!
```

### With Matrix (Good — DRY)
```yaml
jobs:
  build-and-push:
    strategy:
      matrix:
        service: [order-service, payment-service, inventory-service, ...]
    steps:
      - run: mvn package -pl ${{ matrix.service }}
```
GitHub creates one job per matrix value, running them IN PARALLEL.
5 services → 5 parallel jobs → faster overall pipeline.

---

## Concept 3: Why use git SHA for Docker image tags instead of `latest`?

### The Problem with `latest`
```yaml
image:
  tag: latest   # BAD
```
- You can NEVER rollback. "latest" always points to newest.
- If deploy fails, what was the previous image? You don't know.
- Multiple services may have different "latest" versions in flight.
- ECR IMMUTABLE tag policy BLOCKS overwriting tags — `latest` would fail.

### Git SHA Tags (Correct Approach)
```yaml
image:
  tag: main-a1b2c3d   # branch-shortSHA
```
- Every image version is uniquely identifiable
- Rollback = just set the tag back to a previous SHA
- ArgoCD sync history shows exactly which commit is deployed
- Works with ECR IMMUTABLE tag policy

---

## Concept 4: Why does Job 2 commit back to git?

The commit with updated Helm values IS the deployment trigger for ArgoCD.

```
Developer pushes code
    ↓ (Job 1)
New image pushed to ECR: order-service:main-a1b2c3d
    ↓ (Job 2)
Commit: "ci: update image tag to main-a1b2c3d [skip ci]"
file changed: charts/values/order-service.yaml
    ↓
ArgoCD detects git diff
    ↓
ArgoCD applies: kubectl set image ... order-service:main-a1b2c3d
    ↓
Rolling update completes
```

**Why `[skip ci]`** in the commit message?
Without it, the commit pushes → triggers the pipeline → builds again → commits → infinite loop!
`[skip ci]` tells GitHub Actions to NOT trigger workflows for this commit.

---

## Concept 5: Why a separate `terraform.yaml` pipeline?

Terraform and application code have VERY different deployment risks:
- App code: deployed continuously, automated rollback via ArgoCD
- Terraform: changes actual cloud infrastructure, mistakes can be costly (data loss, downtime)

```
terraform.yaml runs:
  - On: only when files in terraform/ change
  - terraform fmt --check   (formatting validation)
  - terraform validate      (syntax check)
  - terraform plan          (preview changes — shown as PR comment)
  - terraform apply         (ONLY on merge to main, requires manual approval)
```

The `terraform plan` output is posted as a comment on the PR so a human can review
what infrastructure changes will happen before approving the merge.

---

## Concept 6: What is the Helm chart structure and why one chart for all services?

### Why ONE chart (microservice/) instead of per-service charts?

All 5 services are Spring Boot apps. They all need:
- Deployment
- Service  
- HPA (autoscaling)
- Ingress
- ServiceAccount (with IRSA annotation)

The only differences are: image, port, env vars, resource limits, replica count.
These are ALL values — not structural differences.

```
One chart + different values files = DRY principle
        ↓
charts/microservice/     ← chart structure (same for all)
charts/values/
  order-service.yaml     ← order-service specific values
  user-service.yaml      ← user-service specific values
  ...
```

ArgoCD creates one Application per service, each pointing to the same chart
but with a different values file. Clean, maintainable, consistent.
