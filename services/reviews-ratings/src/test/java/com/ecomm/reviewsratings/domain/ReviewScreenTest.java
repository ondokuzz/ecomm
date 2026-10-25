package com.ecomm.reviewsratings.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Which reviews the screen holds: a screened word or pattern, whatever its case and accents. */
class ReviewScreenTest {

  private final ReviewScreen screen =
      ReviewScreen.of(List.of("scam", "click here", "fr[e3]{2} money", "café"));

  @ParameterizedTest
  @ValueSource(
      strings = {
        "This is a scam.",
        "SCAM",
        "Total Scám, avoid",
        "scam!",
        "#scam",
        "Just CLICK HERE for more",
        "free money inside",
        "Fr33 Money",
        "Better than the cafe downstairs",
        "CAFÉ quality"
      })
  void aScreenedWordOrPatternHoldsTheReview(String body) {
    assertThat(screen.holds(ReviewContent.of(1, null, body))).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Great with scampi",
        "Not a scammer, just honest",
        "clickhere",
        "freemoney",
        "Cafés are another matter",
        "Works as described"
      })
  void onlyAWholeWordOrPhraseMatches(String body) {
    assertThat(screen.holds(ReviewContent.of(4, null, body))).isFalse();
  }

  @Test
  void theTitleIsScreenedToo() {
    assertThat(screen.holds(ReviewContent.of(1, "A scam", "Avoid it."))).isTrue();
  }

  @Test
  void anEmptyScreenHoldsNothing() {
    assertThat(ReviewScreen.of(List.of()).holds(ReviewContent.of(1, "Scam", "scam"))).isFalse();
  }

  @Test
  void aPatternThatIsNotARegularExpressionIsRefusedAtOnce() {
    assertThatThrownBy(() -> ReviewScreen.of(List.of("scam", "fr[ee")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fr[ee");
  }
}
