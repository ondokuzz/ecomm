package com.ecomm.promotions.domain;

import com.ecomm.commons.money.Money;
import com.ecomm.promotions.domain.Discount.CampaignDiscount;
import com.ecomm.promotions.domain.Discount.CouponDiscount;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * How every Discount a Checkout Session is due stacks. Running Campaigns apply first, lowest
 * priority first, each worked out on what its lines still come to after the Discounts before it:
 * all the lines, or only those in its Categories. Its minimum is measured on what those lines came
 * to before any Discount, so an earlier Discount never takes one away. A Campaign that can't apply
 * (its amount or minimum in another Currency, its minimum not reached, none of its lines in the
 * session) or would take nothing off is left out. The Coupon applies last, on what all the lines
 * still come to, its minimum measured on the subtotal, and is rejected as it would be on its own.
 * Every Discount is at most what its lines still come to, so together they never exceed the
 * subtotal.
 */
public final class DiscountStacking {

  private DiscountStacking() {}

  /**
   * The Discounts {@code lines} get at {@code now}, in the order they apply.
   *
   * @throws InvalidPromotionException unless there is at least one line, all in one Currency
   * @throws CouponNotApplicableException when the Coupon doesn't apply
   */
  public static List<Discount> discounts(
      List<Line> lines, List<Campaign> campaigns, Optional<Coupon> coupon, Instant now) {
    if (lines.isEmpty()) {
      throw new InvalidPromotionException("lines", "need at least one line");
    }
    var currency = lines.getFirst().unitPrice().currency();
    if (lines.stream().anyMatch(l -> !l.unitPrice().currency().equals(currency))) {
      throw new InvalidPromotionException("lines", "must all be in one currency");
    }
    // What each line came to before any Discount, and what it still comes to after those so far.
    var totals = lines.stream().mapToLong(Line::totalMinor).toArray();
    var remaining = totals.clone();
    var discounts = new ArrayList<Discount>();

    var running =
        campaigns.stream()
            .filter(c -> c.stateAt(now) == CampaignState.RUNNING)
            .sorted(Comparator.comparingInt(Campaign::priority))
            .toList();
    for (var campaign : running) {
      var scopedLines =
          IntStream.range(0, lines.size())
              .filter(
                  i ->
                      campaign.categories().isEmpty()
                          || campaign.categories().contains(lines.get(i).category()))
              .toArray();
      var scopedRemaining = new Money(sum(remaining, scopedLines), currency);
      var scopedTotal = new Money(sum(totals, scopedLines), currency);
      if (!applies(campaign, scopedRemaining, scopedTotal)) {
        continue;
      }
      var amount = campaign.discount().on(scopedRemaining);
      if (amount.amountMinor() == 0) {
        continue;
      }
      spread(amount.amountMinor(), remaining, scopedLines);
      discounts.add(new CampaignDiscount(campaign.id(), campaign.name(), amount));
    }

    if (coupon.isPresent()) {
      var allLines = IntStream.range(0, lines.size()).toArray();
      var amount =
          coupon
              .get()
              .discountOn(
                  new Money(sum(remaining, allLines), currency),
                  new Money(sum(totals, allLines), currency),
                  now);
      discounts.add(new CouponDiscount(coupon.get().code(), amount));
    }
    return List.copyOf(discounts);
  }

  /**
   * Whether {@code campaign} can take something off its lines, which still come to {@code
   * remaining} and came to {@code total} before any Discount.
   */
  private static boolean applies(Campaign campaign, Money remaining, Money total) {
    if (remaining.amountMinor() == 0 || !campaign.discount().appliesTo(remaining)) {
      return false;
    }
    var minimum = campaign.minimumSubtotal();
    return minimum == null
        || (minimum.currency().equals(total.currency())
            && total.amountMinor() >= minimum.amountMinor());
  }

  private static long sum(long[] amounts, int[] lineIndexes) {
    long sum = 0;
    for (var i : lineIndexes) {
      sum = Math.addExact(sum, amounts[i]);
    }
    return sum;
  }

  /**
   * Takes {@code amount} off the {@code scopedLines} in proportion to what each still comes to,
   * rounding each share down and giving the minor units left over to the lines whose shares lost
   * the most, so the shares add up to {@code amount} and none is more than its line.
   */
  private static void spread(long amount, long[] remaining, int[] scopedLines) {
    var scopedRemaining = BigInteger.valueOf(sum(remaining, scopedLines));
    var taken = BigInteger.valueOf(amount);
    // shares[n] and leftovers[n] belong to the line scopedLines[n].
    var shares = new long[scopedLines.length];
    var leftovers = new BigInteger[scopedLines.length];
    long given = 0;
    for (var n = 0; n < scopedLines.length; n++) {
      var division =
          taken
              .multiply(BigInteger.valueOf(remaining[scopedLines[n]]))
              .divideAndRemainder(scopedRemaining);
      shares[n] = division[0].longValueExact();
      leftovers[n] = division[1];
      given += shares[n];
    }
    var mostLostFirst =
        IntStream.range(0, scopedLines.length)
            .boxed()
            .sorted(
                Comparator.comparing((Integer n) -> leftovers[n]).reversed().thenComparing(n -> n))
            .toList();
    for (var n : mostLostFirst.subList(0, (int) (amount - given))) {
      shares[n]++;
    }
    for (var n = 0; n < scopedLines.length; n++) {
      remaining[scopedLines[n]] -= shares[n];
    }
  }
}
