# What `make aws-up` reads to write the run's overlay (infra/aws/run-overlay.py), and `make aws-down`
# to find the run again.

output "run" {
  description = "The run ID every resource is tagged with."
  value       = var.run
}

output "region" {
  description = "The region the environment is in."
  value       = var.region
}

output "domain" {
  description = "The domain the environment answers under."
  value       = var.domain
}

output "cluster_name" {
  description = "The EKS cluster's name, for `aws eks update-kubeconfig`."
  value       = module.eks.cluster_name
}

output "postgres_host" {
  description = "RDS's address, which the cluster's postgres Service names."
  value       = aws_db_instance.postgres.address
}

output "postgres_password_secret" {
  description = "The Secrets Manager secret holding the RDS master password."
  value       = aws_secretsmanager_secret.postgres.arn
}

output "redis_host" {
  description = "ElastiCache's address, which the cluster's redis Service names."
  value       = aws_elasticache_cluster.redis.cache_nodes[0].address
}

output "target_groups" {
  description = "Each Service the ALB reaches => its target group, which a TargetGroupBinding fills."
  value       = { for name, site in local.sites : site.service => aws_lb_target_group.sites[name].arn }
}

output "urls" {
  description = "Where the environment answers."
  value       = { for name, site in local.sites : site.service => "https://${name}.${var.domain}" }
}
