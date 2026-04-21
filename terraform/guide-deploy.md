1. cd terraform-learning/bootstrap
   terraform init
   terraform apply
   # Note down the outputs (bucket name, table name, kms alias)

2. Copy terraform.tfvars.example → terraform.tfvars
   Fill in all values

3. cd terraform-learning/infrastructure/prod
   terraform init           # Downloads providers, connects to S3 backend
   terraform plan           # Preview what will be created
   terraform apply          # Create all resources (~20-30 min)

4. Deploy microservices via Helm + ArgoCD (see ../argocd/ and ../charts/)



## 4. Text-Based Architecture Overview

```text
###############################################################################
#             AWS ARCHITECTURE: ECOMMERCE MICROSERVICES PLATFORM             #
#                      Region: ap-southeast-1 (Singapore)                     #
###############################################################################

+-----------------------------------------------------------------------------+
| AWS CLOUD (Region: ap-southeast-1)                                          |
|                                                                             |
|  [GLOBAL/REGIONAL SERVICES]                                                 |
|  +-------------------+   +--------------------+   +---------------------+   |
|  | S3: State Bucket  |   | KMS: State Key     |   | ECR: Docker Repos   |   |
|  | (Versioning/Lock) |   | KMS: Secrets Key   |   | (Immutable Tags)    |   |
|  +-------------------+   +--------------------+   +---------------------+   |
|                                                                             |
|  +-----------------------------------------------------------------------+  |
|  | VPC (10.0.0.0/16)                                                     |  |
|  |                                                                       |  |
|  |  +------------------+         +-------------------------------------+  |  |
|  |  | [PUBLIC SUBNETS] |         | [PRIVATE SUBNETS]                   |  |  |
|  |  | (3 AZs)          |         | (3 AZs)                             |  |  |
|  |  |                  |         |                                     |  |  |
|  |  |  +------------+  |         |  +-------------------------------+  |  |  |
|  |  |  |    ALB     |  |         |  |  EKS CLUSTER (ecommerce-eks)  |  |  |  |
|  |  |  | (Ingress)  +--+---------+->|                               |  |  |  |
|  |  |  +------+-----+  |         |  |  [ Worker Nodes (t3.medium) ] |  |  |  |
|  |  |         |        |         |  |      |                        |  |  |  |
|  |  |  +------+-----+  |         |  |  [ Microservice Pods ]        |  |  |  |
|  |  |  |    NAT     |<-+---------+--+--- (Order, User, Pay, etc.)   |  |  |  |
|  |  |  |  Gateway   |  |         |  +--------------+---------+------+  |  |  |
|  |  |  +------+-----+  |         |                |         |          |  |  |
|  |  |         |        |         |                |         |          |  |  |
|  |  +---------+--------+         |  +-------------v--+   +--v----------+  |  |  |
|  |            |                  |  |  RDS POSTGRES  |   |  AMAZON MSK |  |  |  |
|  |     +------v-------+          |  |  (5 Instances) |   |  (Kafka)    |  |  |  |
|  |     |   Internet   |          |  +----------------+   +-------------+  |  |  |
|  |     |   Gateway    |          |                                     |  |  |
|  |     +------+-------+          +-------------------------------------+  |  |
|  |            |                                                          |  |
|  +------------+----------------------------------------------------------+  |
|               |                                                             |
|               v                                                             |
|         [ PUBLIC USER ]                                                     |
|                                                                             |
+-----------------------------------------------------------------------------+
```
