
# ? ECR: Elastic Container Registry
# Purpose: Create a private image registry for our application
# We use for_each meta argument to create one repo per service

# Workflow
# 1. Developer pushes code to Github
# 2. Github Actions builds Docker Image
# 3. Github Actions pushes image to ECR: <account>.dkr.ecr.<region>.amazonaws.com/<service>:<tag>
# 4. EKS pulls image from ECR when deploying pods

resource "aws_ecr_repository" "repo" {
  name = var.name

  # MUTABLE: Tags can be overwritten 
  # IMMUTABLE : Tags cannot be overwritten --> forces unique tags for every push
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    # Auto scan for known vulnerabilities(CVEs) when an image is pushed
    scan_on_push = true
  }

  tags = {
    Environment = var.environment
    Service     = var.name
  }
}


# LIFECYCLE POLICY — Automatically clean up old images
# ECR charges per GB stored --> if we do not clean --> old images accumulate indefinitely 
# this policy keeps only 10 recent images
resource "aws_ecr_lifecycle_policy" "repo" {
  repository = aws_ecr_repository.repo.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep last 10 images, delete older ones"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = {
        type = "expire"
      }
    }]
  })
}
