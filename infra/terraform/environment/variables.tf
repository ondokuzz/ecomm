variable "region" {
  description = "The AWS region the environment is built in."
  type        = string
  default     = "eu-central-1"
}

variable "run" {
  description = "This environment's run ID, tagged on every resource as run=<id>. `make aws-up` makes one from the time on the first apply and keeps it."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9-]{1,32}$", var.run))
    error_message = "A run ID is lowercase letters, digits and hyphens, at most 32."
  }
}

variable "domain" {
  description = "The Route 53 hosted zone's domain; the environment answers at shop., admin., api. and auth. under it."
  type        = string
}

variable "budget_email" {
  description = "Who the budget alarm emails."
  type        = string
}

variable "budget_limit" {
  description = "The account's monthly cost, in USD, the budget alarm is set at."
  type        = number
  default     = 25
}

variable "kubernetes_version" {
  description = "EKS's Kubernetes version; CI validates the manifests against the same one."
  type        = string
  default     = "1.35"
}

variable "node_count" {
  description = "How many m6i.large nodes the cluster runs. The platform requests about 17.5 GiB and 4.5 vCPUs; four nodes hold it with room for a rollout."
  type        = number
  default     = 4
}
