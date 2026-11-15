"""Prints whether Cart reminders bring Customers back: the reminders Notifications recorded, as
notifications_cart_reminders_total{outcome="sent"} counts them, and those it delivered; of the
delivered, how many were followed by an Order of the same Customer paid within DAYS (7 by default)
of the reminder, and before their next one, so a payment is credited to one reminder at most; and
how many of the Customers reminded paid so. Skipped abandonments are on the Services dashboard.

`make cart-reminders-report` runs it from the repo root, against the running stack. It reads
Notifications' ledger and Order Management's Order Status history from their Postgres databases,
and joins them here: no service joins them live (services/notifications/README.md).
"""

import bisect
import datetime
import os
import subprocess

DAYS = int(os.environ.get("DAYS", "7"))


def rows(database, sql):
    """The rows of `sql` against `database`, as lists of fields."""
    output = subprocess.run(
        ["docker", "compose", "exec", "-T", "postgres",
         "psql", "-U", "ecomm", "-d", database, "-v", "ON_ERROR_STOP=1", "-At", "-F", "\t", "-c", sql],
        check=True, capture_output=True, text=True,
    ).stdout
    return [line.split("\t") for line in output.splitlines() if line]


def instant(text):
    return datetime.datetime.fromisoformat(text)


recorded = int(rows("notifications", "SELECT count(*) FROM notification WHERE message_type = 'CART_ABANDONED'")[0][0])
reminders = {}
for customer, sent_at in rows(
    "notifications",
    "SELECT customer_id, to_char(sent_at AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.US+00:00')"
    " FROM notification WHERE message_type = 'CART_ABANDONED' AND status = 'SENT' ORDER BY sent_at",
):
    reminders.setdefault(customer, []).append(instant(sent_at))

payments = {}
if reminders:
    for customer, paid_at in rows(
        "orders",
        "SELECT o.customer_id, to_char(h.at AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.US+00:00')"
        " FROM order_status_history h JOIN customer_order o ON o.id = h.order_id"
        " WHERE h.status = 'PAID' ORDER BY h.at",
    ):
        if customer in reminders:
            payments.setdefault(customer, []).append(instant(paid_at))

window = datetime.timedelta(days=DAYS)
sent = sum(len(times) for times in reminders.values())
followed = 0
customers_paid = set()
for customer, times in reminders.items():
    paid = payments.get(customer, [])
    for i, at in enumerate(times):
        until = min(at + window, times[i + 1]) if i + 1 < len(times) else at + window
        first = bisect.bisect_right(paid, at)
        if first < len(paid) and paid[first] <= until:
            followed += 1
            customers_paid.add(customer)

print(f"Cart reminders recorded: {recorded}; delivered: {sent}, to {len(reminders)} Customers")
if sent:
    print(f"Delivered reminders followed by a paid Order within {DAYS} days: {followed} ({followed / sent:.0%})")
    print(f"Customers reminded who paid an Order so: {len(customers_paid)} ({len(customers_paid) / len(reminders):.0%})")
