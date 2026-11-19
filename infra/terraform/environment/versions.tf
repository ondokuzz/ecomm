terraform {
  # Write-only arguments, so the RDS master password is never in the state (stores.tf).
  required_version = ">= 1.11"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 3.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.7"
    }
  }

  # The bucket and region are given at init (`make aws-up` gives them): the bootstrap's bucket.
  # S3's own lock file is the lock, so no two applies run at once.
  backend "s3" {
    key          = "environment.tfstate"
    encrypt      = true
    use_lockfile = true
  }
}

provider "aws" {
  region = var.region

  # On everything this creates, so a forgotten environment's resources are found by its run
  # (`make aws-down` checks for them).
  default_tags {
    tags = local.tags
  }
}

# Installs the AWS Load Balancer Controller into the cluster (ingress.tf).
provider "helm" {
  kubernetes = {
    host                   = module.eks.cluster_endpoint
    cluster_ca_certificate = base64decode(module.eks.cluster_certificate_authority_data)
    exec = {
      api_version = "client.authentication.k8s.io/v1beta1"
      command     = "aws"
      args        = ["eks", "get-token", "--cluster-name", module.eks.cluster_name, "--region", var.region]
    }
  }
}

locals {
  tags = {
    project = "ecomm"
    run     = var.run
  }
}
