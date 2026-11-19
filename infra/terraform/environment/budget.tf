# The budget alarm: an email when the account's costs this month pass 80% of the limit, and when
# they are forecast to pass it. It is over the whole account, not the run's tags, since a tag counts
# in a budget only once it is activated as a cost allocation tag, a day after it is first seen; so it
# also catches what a run leaves behind. It goes with the environment, as an environment that is
# gone costs nothing more.
resource "aws_budgets_budget" "monthly" {
  name         = "ecomm-${var.run}"
  budget_type  = "COST"
  limit_amount = tostring(var.budget_limit)
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 80
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = [var.budget_email]
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = [var.budget_email]
  }
}
