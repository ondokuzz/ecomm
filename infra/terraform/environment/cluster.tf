# EKS, with one managed node group of m6i.large (amd64, as CI builds the images). The API is
# public, so the operator's kubectl reaches it, and the operator who applies is its admin.
#
# No KMS key of the module's own, and no control-plane log group: EKS already encrypts Secrets with
# a key of its own, and a key or log group outlives the cluster (a key waits a week to be deleted,
# and EKS writes its logs again after the group is gone), which `make aws-down` would then find.

module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 21.0"

  name               = "ecomm"
  kubernetes_version = var.kubernetes_version

  endpoint_public_access                   = true
  enable_cluster_creator_admin_permissions = true

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets

  create_kms_key              = false
  encryption_config           = null
  enabled_log_types           = []
  create_cloudwatch_log_group = false

  addons = {
    coredns    = {}
    kube-proxy = {}
    vpc-cni = {
      before_compute = true
    }
    eks-pod-identity-agent = {
      before_compute = true
    }
    # The StatefulSets' volumes. Each is tagged as the run, so `make aws-down` would find one left.
    aws-ebs-csi-driver = {
      pod_identity_association = [{
        role_arn        = module.ebs_csi_pod_identity.iam_role_arn
        service_account = "ebs-csi-controller-sa"
      }]
      configuration_values = jsonencode({
        controller = { extraVolumeTags = local.tags }
      })
    }
  }

  eks_managed_node_groups = {
    default = {
      instance_types = ["m6i.large"]
      ami_type       = "AL2023_x86_64_STANDARD"
      min_size       = var.node_count
      max_size       = var.node_count
      desired_size   = var.node_count
    }
  }

  # The ALB sends to pods' own addresses (target type ip), which have the nodes' security group:
  # the frontends', the gateway's and Keycloak's port, and Keycloak's management port for its
  # health check.
  node_security_group_additional_rules = {
    alb_http = {
      description              = "From the ALB to the pods it sends to"
      type                     = "ingress"
      protocol                 = "tcp"
      from_port                = 8080
      to_port                  = 8090
      source_security_group_id = aws_security_group.alb.id
    }
    alb_keycloak_health = {
      description              = "From the ALB to Keycloak's health checks"
      type                     = "ingress"
      protocol                 = "tcp"
      from_port                = 9000
      to_port                  = 9000
      source_security_group_id = aws_security_group.alb.id
    }
  }

  tags = local.tags
}

module "ebs_csi_pod_identity" {
  source  = "terraform-aws-modules/eks-pod-identity/aws"
  version = "~> 2.0"

  name                      = "ecomm-ebs-csi"
  attach_aws_ebs_csi_policy = true

  tags = local.tags
}
