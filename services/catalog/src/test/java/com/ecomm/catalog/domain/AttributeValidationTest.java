package com.ecomm.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A Product's attributes against its Category's non-axis attribute definitions: every required one
 * present, types matching, {@code ENUM} values allowed, and no unknown attributes.
 */
class AttributeValidationTest {

  private static final Category HEADPHONES =
      new Category(
          "headphones",
          "Headphones",
          List.of(
              definition("brand", AttributeType.TEXT, true),
              new AttributeDefinition(
                  "type", AttributeType.ENUM, List.of("In-ear", "Over-ear"), true, false),
              definition("wireless", AttributeType.BOOLEAN, true),
              definition("batteryHours", AttributeType.NUMBER, false),
              new AttributeDefinition("colour", AttributeType.TEXT, List.of(), true, true)));

  private static AttributeDefinition definition(String name, AttributeType type, boolean required) {
    return new AttributeDefinition(name, type, List.of(), required, false);
  }

  private static List<FieldViolation> violations(Map<String, String> attributes) {
    return HEADPHONES.violations(attributes);
  }

  @Test
  void attributesSatisfyingEveryDefinitionAreValid() {
    assertThat(
            violations(
                Map.of(
                    "brand", "Sony", "type", "Over-ear", "wireless", "true", "batteryHours", "30")))
        .isEmpty();
  }

  @Test
  void anOptionalAttributeMayBeLeftOut() {
    assertThat(violations(Map.of("brand", "Sony", "type", "In-ear", "wireless", "false")))
        .isEmpty();
  }

  @Test
  void everyMissingRequiredAttributeIsNamed() {
    assertThat(violations(Map.of("brand", "Sony")))
        .containsExactly(
            new FieldViolation("attributes.type", "is required"),
            new FieldViolation("attributes.wireless", "is required"));
  }

  @Test
  void aBlankRequiredAttributeCountsAsMissing() {
    assertThat(violations(Map.of("brand", " ", "type", "In-ear", "wireless", "true")))
        .containsExactly(new FieldViolation("attributes.brand", "is required"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"yes", "TRUE", "1", ""})
  void aBooleanIsTrueOrFalse(String value) {
    assertThat(violations(Map.of("brand", "Sony", "type", "In-ear", "wireless", value)))
        .extracting(FieldViolation::field)
        .containsExactly("attributes.wireless");
  }

  @ParameterizedTest
  @ValueSource(strings = {"thirty", "30h", "", "1e"})
  void aNumberIsADecimal(String value) {
    assertThat(
            violations(
                Map.of(
                    "brand", "Sony", "type", "In-ear", "wireless", "true", "batteryHours", value)))
        .extracting(FieldViolation::field)
        .containsExactly("attributes.batteryHours");
  }

  @ParameterizedTest
  @ValueSource(strings = {"30", "-2", "7.5"})
  void numbersMayBeWholeNegativeOrFractional(String value) {
    assertThat(
            violations(
                Map.of(
                    "brand", "Sony", "type", "In-ear", "wireless", "true", "batteryHours", value)))
        .isEmpty();
  }

  @Test
  void anEnumTakesOnlyItsValues() {
    assertThat(violations(Map.of("brand", "Sony", "type", "in-ear", "wireless", "true")))
        .containsExactly(new FieldViolation("attributes.type", "must be one of In-ear, Over-ear"));
  }

  @Test
  void anAttributeTheCategoryDoesNotDefineIsUnknown() {
    assertThat(
            violations(
                Map.of("brand", "Sony", "type", "In-ear", "wireless", "true", "strap", "silicone")))
        .containsExactly(
            new FieldViolation("attributes.strap", "is not defined by Category headphones"));
  }

  @Test
  void anAttributeNamingAVariantAxisIsNeitherRequiredNorUnknown() {
    // Axis values belong on Variants; until Variants carry them, a Product may still hold one.
    assertThat(violations(Map.of("brand", "Sony", "type", "In-ear", "wireless", "true"))).isEmpty();
    assertThat(
            violations(
                Map.of("brand", "Sony", "type", "In-ear", "wireless", "true", "colour", "Black")))
        .isEmpty();
  }

  @Test
  void anAxisValueAProductStillCarriesMustFitItsType() {
    var phones =
        new Category(
            "phones",
            "Phones",
            List.of(
                new AttributeDefinition(
                    "storage", AttributeType.ENUM, List.of("128 GB", "256 GB"), true, true)));

    assertThat(phones.violations(Map.of("storage", "9 TB")))
        .containsExactly(new FieldViolation("attributes.storage", "must be one of 128 GB, 256 GB"));
    assertThat(phones.violations(Map.of("storage", "256 GB"))).isEmpty();
  }

  @Test
  void everyViolationIsReportedAtOnce() {
    assertThat(violations(Map.of("type", "Earbuds", "wireless", "maybe", "strap", "x")))
        .extracting(FieldViolation::field)
        .containsExactlyInAnyOrder(
            "attributes.brand", "attributes.type", "attributes.wireless", "attributes.strap");
  }

  @Test
  void anEnumNeedsValues() {
    assertThatThrownBy(
            () -> new AttributeDefinition("type", AttributeType.ENUM, List.of(), true, false))
        .isInstanceOf(InvalidCategoryException.class);
  }

  @Test
  void onlyAnEnumHasValues() {
    assertThatThrownBy(
            () -> new AttributeDefinition("brand", AttributeType.TEXT, List.of("A"), true, false))
        .isInstanceOf(InvalidCategoryException.class);
  }

  @Test
  void aCategoryDefinesEachAttributeOnce() {
    assertThatThrownBy(
            () ->
                new Category(
                    "headphones",
                    "Headphones",
                    List.of(
                        definition("brand", AttributeType.TEXT, true),
                        definition("brand", AttributeType.NUMBER, false))))
        .isInstanceOf(InvalidCategoryException.class)
        .hasMessageContaining("brand");
  }

  @ParameterizedTest
  @ValueSource(strings = {"Smart Watches", "phones_", "-phones", ""})
  void aCategorySlugIsLowercaseLettersDigitsAndHyphens(String slug) {
    assertThatThrownBy(() -> new Category(slug, "Anything", List.of()))
        .isInstanceOf(InvalidCategoryException.class);
  }
}
