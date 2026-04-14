# Terraform Infrastructure Guide for Beginners

This guide explains the Terraform setup created for the Ecommerce Java project. It breaks down complex cloud infrastructure into understandable parts.

## 1. High-Level Architecture
In production, we don't run everything on one machine. We split it into managed services on AWS:

```text
                                  +---------------------------------------------------------+
                                  |                  Internet                               |
                                  +---------------------+-----------------------------------+
                                                        |
                                            +-----------v-----------+
                                            |   Internet Gateway    |
                                            +-----------+-----------+
                                                        |
      +-------------------------------------------------v-----------------------------------------------------+
      |                                        AWS Region (e.g., us-east-1)                                   |
      |                                                                                                       |
      |    +---------------------------------------------------------------------------------------------+    |
      |    |                                     VPC (10.0.0.0/16)                                       |    |
      |    |                                                                                             |    |
      |    |    +---------------------------------------+       +---------------------------------------+    |
      |    |    |        Public Subnet (AZ A)           |       |        Public Subnet (AZ B)           |    |
      |    |    |  +---------------------------------+  |       |  +---------------------------------+  |    |
      |    |    |  |       NAT Gateway (EIP)         |  |       |  |     (Optional) ALB / Ingress    |  |    |
      |    |    |  +---------------+-----------------+  |       |  +---------------------------------+  |    |
      |    |    +------------------|--------------------+       +---------------------------------------+    |
      |    |                       |                                                                     |    |
      |    |    +------------------v---------------------------------------------------------------------+    |
      |    |    |                                    Private Subnets                                     |    |
      |    |    |                                                                                       |    |
      |    |    |    +-----------------------------------------------------------------------------+    |    |
      |    |    |    |                          EKS Cluster (Kubernetes)                           |    |    |
      |    |    |    |                                                                             |    |    |
      |    |    |    |    +-------------------+    +-------------------+    +-------------------+  |    |    |
      |    |    |    |    |   Order Service   |    |  Payment Service  |    | Inventory Service |  |    |    |
      |    |    |    |    +---------+---------+    +---------+---------+    +---------+---------+  |    |    |
      |    |    |    |              |                        |                        |            |    |    |
      |    |    |    +--------------|------------------------|------------------------|------------+    |    |
      |    |    |                   |                        |                        |                   |    |
      |    |    |    +--------------v-----------+    +-------v-------+                       |    |
      |    |    |    |     MSK (Managed Kafka)  |    |   RDS (Postgres)  |                       |    |
      |    |    |    +--------------------------+    +---------------+                       |    |
      |    |    +---------------------------------------------------------------------------------------+    |
      |    +---------------------------------------------------------------------------------------------+    |
      |                                                                                                       |
      |    +---------------------------------------------------------------------------------------------+    |
      |    |                               ECR (Elastic Container Registry)                               |    |
      |    |    +----------------+  +----------------+  +----------------+  +----------------+  +--------+|    |
      |    |    |  Order Repo    |  |  Payment Repo  |  | Inventory Repo |  | Notification   |  | User   ||    |
      |    |    +----------------+  +----------------+  +----------------+  +----------------+  +--------+|    |
      |    +---------------------------------------------------------------------------------------------+    |
      +-------------------------------------------------------------------------------------------------------+
```

---

## 2. The Folder Structure
We use a **Modular Design**. Think of modules like Lego bricks: you build them once and reuse them.

```text
terraform/
├── modules/               # The "Lego Bricks" (Reusable code)
│   ├── vpc/               # Network setup
│   ├── eks/               # Kubernetes cluster
│   ├── rds/               # PostgreSQL database
│   ├── msk/               # Kafka cluster
│   ├── elasticache/       # Redis setup
│   ├── ecr/               # Docker image storage
│   └── iam/               # Security roles (IRSA)
└── infrastructure/
    └── prod/              # The "Finished Building" (Production environment)
        ├── main.tf        # Connects all the modules together
        ├── variables.tf   # The "Settings" (Region, names, etc.)
        ├── outputs.tf     # The "Receipt" (URLs, IDs created)
        └── terraform.tfvars.example # Template for your secrets
```

---

## 3. Explaining the "Magic" Concepts

### A. VPC (Virtual Private Cloud)
We split the network into **Public** and **Private** subnets:
- **Public**: Contains the "Load Balancer" (the front door).
- **Private**: Contains our Java apps and Databases. They are NOT accessible from the internet directly. We use a **NAT Gateway** so our private apps can "talk out" to the internet (e.g., to download updates) without the internet "talking in."

### B. IRSA (IAM Roles for Service Accounts)
This is the most secure way for a Java app to talk to AWS. 
- **Old way**: Putting "AWS_ACCESS_KEY" in your code (Dangerous!).
- **New way (IRSA)**: We give the Kubernetes "Service Account" a special IAM Role. AWS trusts the EKS cluster, so the app gets temporary keys automatically. No secrets needed in code!

### C. Security Groups
These are virtual firewalls. Our RDS (Database) has a rule: *"Only allow traffic from inside the VPC on port 5432."* This means even if someone found the DB URL, they couldn't connect from their home computer.

---

## 4. How to Use This Code

### Step 1: Configuration
Copy the example variables file:
```bash
cp terraform/infrastructure/prod/terraform.tfvars.example terraform/infrastructure/prod/terraform.tfvars
```
Open `terraform.tfvars` and set your `db_password`.

### Step 2: Initialize
Terraform needs to download "Providers" (the AWS plugins).
```bash
cd terraform/infrastructure/prod
terraform init
```

### Step 3: Plan (The "Preview")
Always run a plan to see what Terraform will do BEFORE it does it.
```bash
terraform plan
```

### Step 4: Apply (The "Build")
This actually creates the resources on AWS. **Warning: This costs money!**
```bash
terraform apply
```

---

## 5. Key Terraform Commands for Beginners
- `terraform fmt`: Automatically cleans up your code formatting.
- `terraform validate`: Checks if your code has syntax errors.
- `terraform output`: Shows you the URLs and IDs of what you built.
- `terraform destroy`: Deletes everything you built (Use with extreme caution!).

## 6. Why modules?
If you wanted to create a `dev` environment later, you wouldn't copy-paste the code. You would just create a `terraform/infrastructure/dev/` folder and call the same modules with different settings (e.g., a smaller database).
