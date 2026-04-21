# Creates the managed Kubernetes cluster where all microservices run.

# WHAT THIS MODULE CREATES:
#   - EKS Control Plane (managed by AWS — you don't see the master nodes)
#   - IAM Role for the control plane
#   - EKS Node Group (EC2 instances that run your pods)
#   - IAM Role for the worker nodes
#   - OIDC Provider (enables IRSA — IAM Roles for Service Accounts)

# EKS ARCHITECTURE:
#   Control Plane (AWS managed): API Server, etcd, scheduler
#       ↓
#   Worker Nodes (your EC2s): kubelet, kube-proxy, container runtime
#       ↓
#   Pods: your microservice containers
#
# CONTROL PLANE vs DATA PLANE:
#   Control plane = the "brain" of Kubernetes (API server, scheduler, etcd)
#                   AWS manages this — you pay ~$0.10/hr but never touch it
#   Data plane     = the worker nodes running your pods
#                   You manage these (or use Fargate for serverless)


# * EKS Cluster
resource "aws_eks_cluster" "main" {
  name     = var.cluster_name
  role_arn = aws_iam_role.cluster.arn

  vpc_config {
    subnet_ids = var.control_plane_subnet_ids

    # endpoint_private_access: kubectl can reach API server from within VPC
    endpoint_private_access = true
    # endpoint_public_access: kubectl can reach API server from internet
    endpoint_public_access = true

  }

  # Enable control plane logging to CloudWatch.
  # These logs help you debug authentication issues, slow API calls, etc.
  enabled_cluster_log_types = ["api", "audit", "authenticator", "controllerManager", "scheduler"]

  depends_on = [
    aws_iam_role_policy_attachment.cluster_AmazonEKSClusterPolicy,
  ]

  tags = {
    Name = var.cluster_name
  }
}

# IAM ROLE — For the EKS Control Plane
resource "aws_iam_role" "cluster" {
  name = "${var.cluster_name}-cluster-role"

  # assume_role_policy: Who is allowed to assume this role?
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "eks.amazonaws.com"
      }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "cluster_AmazonEKSClusterPolicy" {
  # AWS managed policy with all permissions the control plane needs
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
  role       = aws_iam_role.cluster.name
}

# EKS NODE GROUP
resource "aws_eks_node_group" "main" {
  cluster_name    = aws_eks_cluster.main.name
  node_group_name = "${var.cluster_name}-node-group"
  node_role_arn   = aws_iam_role.node.arn
  subnet_ids      = var.subnet_ids

  scaling_config {
    desired_size = var.desired_capacity
    max_size     = var.max_capacity
    min_size     = var.min_capacity
  }

  instance_types = var.instance_types

  depends_on = [
    aws_iam_role_policy_attachment.node_AmazonEKSWorkerNodePolicy,
    aws_iam_role_policy_attachment.node_AmazonEKS_CNI_Policy,
    aws_iam_role_policy_attachment.node_AmazonEC2ContainerRegistryReadOnly,
  ]
}


# IAM ROLE — For Worker Nodes

# Worker nodes need permissions to:
#   - Join the EKS cluster (AmazonEKSWorkerNodePolicy)
#   - Configure pod networking (AmazonEKS_CNI_Policy)
#   - Pull Docker images from ECR (AmazonEC2ContainerRegistryReadOnly)

resource "aws_iam_role" "node" {
  name = "${var.cluster_name}-node-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "ec2.amazonaws.com"
      }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "node_AmazonEKSWorkerNodePolicy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy"
  role       = aws_iam_role.node.name
}

resource "aws_iam_role_policy_attachment" "node_AmazonEKS_CNI_Policy" {
  # CNI = Container Network Interface — manages pod IP addresses
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy"
  role       = aws_iam_role.node.name
}

resource "aws_iam_role_policy_attachment" "node_AmazonEC2ContainerRegistryReadOnly" {
  # Allows nodes to pull images from ANY ECR repository in this account
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
  role       = aws_iam_role.node.name
}

# OIDC PROVIDER — Enables IRSA (IAM Roles for Service Accounts)

# WHY OIDC?
# IRSA lets pods assume IAM roles WITHOUT needing AWS access keys.
# How it works:
#   1. EKS creates an OIDC identity endpoint
#   2. We register that endpoint as an OIDC provider in IAM
#   3. IAM roles trust identities from this OIDC provider
#   4. Pods with the right ServiceAccount get temporary AWS credentials automatically

data "tls_certificate" "eks" {
  # Get the TLS thumbprint of the EKS OIDC issuer endpoint.
  # IAM needs this to verify the OIDC provider's identity.
  url = aws_eks_cluster.main.identity[0].oidc[0].issuer
}

resource "aws_iam_openid_connect_provider" "eks" {
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.eks.certificates[0].sha1_fingerprint]
  url             = aws_eks_cluster.main.identity[0].oidc[0].issuer
}
