# How the environment is reached: an ALB over HTTPS, with an ACM certificate, at four names under
# the domain, each sending to one Service's pods.
#
# Terraform owns the ALB, its target groups and the Route 53 records, and the AWS Load Balancer
# Controller only registers each Service's pods in its target group, through a TargetGroupBinding in
# the aws overlay. So the records are made in the same apply as the ALB they name, and everything
# billable is in Terraform's state, for destroy to remove; the controller makes nothing of its own.

locals {
  # name under the domain => the Service it reaches, the port its pods listen on, and their health
  # check.
  sites = {
    shop  = { service = "storefront", port = 8080, health_path = "/", health_port = "traffic-port" }
    admin = { service = "admin-console", port = 8090, health_path = "/", health_port = "traffic-port" }
    api   = { service = "api-gateway", port = 8080, health_path = "/actuator/health/readiness", health_port = "traffic-port" }
    auth  = { service = "keycloak", port = 8080, health_path = "/health/ready", health_port = "9000" }
  }
}

data "aws_route53_zone" "domain" {
  name = var.domain
}

# --- Certificate --------------------------------------------------------------------------------

resource "aws_acm_certificate" "sites" {
  domain_name               = "shop.${var.domain}"
  subject_alternative_names = [for name in ["admin", "api", "auth"] : "${name}.${var.domain}"]
  validation_method         = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_route53_record" "certificate_validation" {
  for_each = {
    for option in aws_acm_certificate.sites.domain_validation_options : option.domain_name => option
  }

  zone_id = data.aws_route53_zone.domain.zone_id
  name    = each.value.resource_record_name
  type    = each.value.resource_record_type
  records = [each.value.resource_record_value]
  ttl     = 60
}

resource "aws_acm_certificate_validation" "sites" {
  certificate_arn         = aws_acm_certificate.sites.arn
  validation_record_fqdns = [for record in aws_route53_record.certificate_validation : record.fqdn]
}

# --- Load balancer ------------------------------------------------------------------------------

resource "aws_security_group" "alb" {
  name        = "ecomm-alb"
  description = "The ALB: HTTPS and HTTP from anywhere, to the pods it sends to"
  vpc_id      = module.vpc.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "alb" {
  for_each = { https = 443, http = 80 }

  security_group_id = aws_security_group.alb.id
  description       = "${upper(each.key)} from anywhere"
  ip_protocol       = "tcp"
  from_port         = each.value
  to_port           = each.value
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_egress_rule" "alb" {
  security_group_id = aws_security_group.alb.id
  description       = "To the pods, inside the VPC"
  ip_protocol       = "-1"
  cidr_ipv4         = module.vpc.vpc_cidr_block
}

resource "aws_lb" "sites" {
  name               = "ecomm"
  load_balancer_type = "application"
  subnets            = module.vpc.public_subnets
  security_groups    = [aws_security_group.alb.id]
  # Invalid header fields are dropped, not passed on to the pods.
  drop_invalid_header_fields = true
}

resource "aws_lb_target_group" "sites" {
  for_each = local.sites

  name        = "ecomm-${each.key}"
  port        = each.value.port
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = module.vpc.vpc_id
  # A pod's in-flight requests get 30 seconds to finish as it goes, not the default 5 minutes.
  deregistration_delay = 30

  health_check {
    path                = each.value.health_path
    port                = each.value.health_port
    matcher             = "200"
    interval            = 10
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.sites.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = aws_acm_certificate_validation.sites.certificate_arn

  # A name the certificate covers but no rule matches.
  default_action {
    type = "fixed-response"
    fixed_response {
      content_type = "text/plain"
      message_body = "Not found"
      status_code  = "404"
    }
  }
}

resource "aws_lb_listener_rule" "sites" {
  for_each = local.sites

  listener_arn = aws_lb_listener.https.arn
  priority     = index(keys(local.sites), each.key) + 1

  condition {
    host_header {
      values = ["${each.key}.${var.domain}"]
    }
  }

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.sites[each.key].arn
  }
}

# Plain HTTP only ever redirects to HTTPS, so no token travels in clear.
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.sites.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "redirect"
    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}

resource "aws_route53_record" "sites" {
  for_each = local.sites

  zone_id = data.aws_route53_zone.domain.zone_id
  name    = "${each.key}.${var.domain}"
  type    = "A"

  alias {
    name                   = aws_lb.sites.dns_name
    zone_id                = aws_lb.sites.zone_id
    evaluate_target_health = false
  }
}

# --- AWS Load Balancer Controller ---------------------------------------------------------------

# Only what registering pods in a target group takes, as it makes no load balancer of its own.
module "lb_controller_pod_identity" {
  source  = "terraform-aws-modules/eks-pod-identity/aws"
  version = "~> 2.0"

  name = "ecomm-lb-controller"

  attach_aws_lb_controller_targetgroup_binding_only_policy = true

  associations = {
    controller = {
      cluster_name    = module.eks.cluster_name
      namespace       = "kube-system"
      service_account = "aws-load-balancer-controller"
    }
  }

  tags = local.tags
}

resource "helm_release" "lb_controller" {
  name       = "aws-load-balancer-controller"
  namespace  = "kube-system"
  repository = "https://aws.github.io/eks-charts"
  chart      = "aws-load-balancer-controller"
  version    = "3.6.2"

  values = [jsonencode({
    clusterName = module.eks.cluster_name
    region      = var.region
    vpcId       = module.vpc.vpc_id
    serviceAccount = {
      name = "aws-load-balancer-controller"
    }
    # It serves the TargetGroupBindings only; it changes no Service of the stack's.
    enableServiceMutatorWebhook = false
  })]

  # The nodes to run on, and its credentials before it first starts.
  depends_on = [module.eks, module.lb_controller_pod_identity]
}
