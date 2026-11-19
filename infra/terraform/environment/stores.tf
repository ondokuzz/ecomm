# RDS Postgres and ElastiCache Redis: the data that can't be rebuilt from a topic (ADR 0012). The
# cluster's `postgres` and `redis` Services point at them (infra/k8s/overlays/aws). Both are reached
# from the nodes only.

# --- Postgres -----------------------------------------------------------------------------------

# Generated here and handed to RDS and to Secrets Manager as write-only arguments, so it is in
# neither's state. `make aws-up` copies it from Secrets Manager into the postgres-password Secret.
# Letters and digits only: Temporal and the init scripts put it into connection strings unescaped.
ephemeral "random_password" "postgres" {
  length  = 32
  special = false
}

resource "aws_secretsmanager_secret" "postgres" {
  name        = "ecomm/${var.run}/postgres-password"
  description = "The RDS master password, which every service logs in with (ADR 0011)."
  # Deleted at once on destroy, not kept for a week, so its name is free for the next run.
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "postgres" {
  secret_id                = aws_secretsmanager_secret.postgres.id
  secret_string_wo         = ephemeral.random_password.postgres.result
  secret_string_wo_version = 1
}

resource "aws_db_subnet_group" "postgres" {
  name       = "ecomm"
  subnet_ids = module.vpc.private_subnets
}

resource "aws_security_group" "postgres" {
  name        = "ecomm-postgres"
  description = "Postgres, from the cluster's nodes"
  vpc_id      = module.vpc.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "postgres" {
  security_group_id            = aws_security_group.postgres.id
  description                  = "From the cluster's pods"
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  referenced_security_group_id = module.eks.node_security_group_id
}

# RDS for Postgres 15 and on refuses connections without TLS by default. Temporal's SQL plugin
# doesn't ask for TLS unless configured to, and the local stack's Postgres has none, so the
# environment allows plain connections too, inside the VPC's private subnets.
resource "aws_db_parameter_group" "postgres" {
  name   = "ecomm-postgres16"
  family = "postgres16"

  parameter {
    name         = "rds.force_ssl"
    value        = "0"
    apply_method = "pending-reboot"
  }

  # Twice the local Postgres's 200, as each of the eight Postgres-backed services runs two replicas
  # here, each with Hikari's pool of 10 (160 between them), besides Keycloak's, Temporal's, Apicurio's
  # and the init Jobs'. About what RDS's own default gives a db.t4g.medium.
  parameter {
    name         = "max_connections"
    value        = "400"
    apply_method = "pending-reboot"
  }
}

resource "aws_db_instance" "postgres" {
  identifier     = "ecomm"
  engine         = "postgres"
  engine_version = "16"
  instance_class = "db.t4g.medium"

  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true

  # The login every service shares, as locally (ADR 0011).
  username            = "ecomm"
  password_wo         = ephemeral.random_password.postgres.result
  password_wo_version = 1

  db_subnet_group_name   = aws_db_subnet_group.postgres.name
  vpc_security_group_ids = [aws_security_group.postgres.id]
  parameter_group_name   = aws_db_parameter_group.postgres.name
  publicly_accessible    = false

  # A test run: one zone, a day of backups, and nothing kept once it is destroyed.
  multi_az                 = false
  backup_retention_period  = 1
  skip_final_snapshot      = true
  delete_automated_backups = true
  deletion_protection      = false
  apply_immediately        = true
}

# --- Redis --------------------------------------------------------------------------------------

resource "aws_elasticache_subnet_group" "redis" {
  name       = "ecomm"
  subnet_ids = module.vpc.private_subnets
}

resource "aws_security_group" "redis" {
  name        = "ecomm-redis"
  description = "Redis, from the cluster's nodes"
  vpc_id      = module.vpc.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "redis" {
  security_group_id            = aws_security_group.redis.id
  description                  = "From the cluster's pods"
  ip_protocol                  = "tcp"
  from_port                    = 6379
  to_port                      = 6379
  referenced_security_group_id = module.eks.node_security_group_id
}

# Expired-key events, by which Checkout counts a Checkout Session Redis drops as expired, as the
# local Redis's --notify-keyspace-events Ex.
resource "aws_elasticache_parameter_group" "redis" {
  name   = "ecomm-redis7"
  family = "redis7"

  parameter {
    name  = "notify-keyspace-events"
    value = "Ex"
  }
}

# One node, without TLS, as the local Redis: the services speak plain Redis to it.
resource "aws_elasticache_cluster" "redis" {
  cluster_id           = "ecomm"
  engine               = "redis"
  engine_version       = "7.1"
  node_type            = "cache.t4g.small"
  num_cache_nodes      = 1
  port                 = 6379
  parameter_group_name = aws_elasticache_parameter_group.redis.name
  subnet_group_name    = aws_elasticache_subnet_group.redis.name
  security_group_ids   = [aws_security_group.redis.id]
  apply_immediately    = true
}
