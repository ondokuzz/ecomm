# A VPC across two zones: the nodes, RDS and ElastiCache in private subnets, the ALB and the one NAT
# gateway in public ones. One NAT gateway, not one per zone: a test run doesn't need to outlive a
# zone's loss, and each costs by the hour.

data "aws_availability_zones" "available" {
  state = "available"
  filter {
    name   = "opt-in-status"
    values = ["opt-in-not-required"]
  }
}

module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 6.0"

  name = "ecomm"
  cidr = "10.0.0.0/16"
  azs  = slice(data.aws_availability_zones.available.names, 0, 2)

  # Large private subnets, since every pod takes a VPC address.
  private_subnets = ["10.0.0.0/19", "10.0.32.0/19"]
  public_subnets  = ["10.0.64.0/24", "10.0.65.0/24"]

  enable_nat_gateway = true
  single_nat_gateway = true

  tags = local.tags
}
