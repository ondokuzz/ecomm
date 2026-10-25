package com.ecomm.reviewsratings.domain;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The store's screen of words and patterns a review's title and body may not contain, such as
 * {@code scam} or {@code fr[e3]{2} money}. Each is a regular expression, matched as a whole word or
 * phrase, so {@code scam} holds "a scam!" but not "scampi", and ignoring case and accents on both
 * sides, so it holds "SCÁM" too.
 */
public final class ReviewScreen {

  /** A letter, digit or underscore: what may not touch a match on either side. */
  private static final String WORD_CHARACTER = "[\\p{L}\\p{N}_]";

  private static final Pattern MARKS = Pattern.compile("\\p{M}+");

  private final List<Pattern> patterns;

  private ReviewScreen(List<Pattern> patterns) {
    this.patterns = patterns;
  }

  /**
   * @throws IllegalArgumentException naming the first entry that isn't a regular expression
   */
  public static ReviewScreen of(List<String> entries) {
    return new ReviewScreen(entries.stream().map(ReviewScreen::compile).toList());
  }

  /** Whether the review says something screened, in its title or its body. */
  public boolean holds(ReviewContent content) {
    var text = withoutAccents(content.title().map(t -> t + "\n").orElse("") + content.body());
    return patterns.stream().anyMatch(p -> p.matcher(text).find());
  }

  private static Pattern compile(String entry) {
    try {
      return Pattern.compile(
          "(?<!" + WORD_CHARACTER + ")(?:" + withoutAccents(entry) + ")(?!" + WORD_CHARACTER + ")",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    } catch (PatternSyntaxException e) {
      throw new IllegalArgumentException("The screened pattern " + entry + " is invalid.", e);
    }
  }

  /** "Scám" as "Scam": each letter's accents taken off. */
  private static String withoutAccents(String text) {
    return MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
  }
}
