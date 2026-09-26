package com.ecomm.commons.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The hexagonal layering every service follows. Include it in a service's tests with {@code
 * ArchTests.in(HexagonalRules.class)}.
 *
 * <ul>
 *   <li>{@code domain}: pure Java, depends on nothing else in the service or on any framework
 *   <li>{@code application}: use cases and ports, depends only on {@code domain}, no framework
 *   <li>{@code adapter.in.web}, {@code adapter.out.<tech>}: reach use cases only through ports,
 *       never depend on each other
 * </ul>
 *
 * <p>A service need not have every layer (a stateless service may have no outbound adapter), so
 * each rule passes when the layer it constrains is absent.
 */
public final class HexagonalRules {

  private static final String[] FRAMEWORKS = {
    "org.springframework..",
    "jakarta..",
    "org.keycloak..",
    "com.ecomm.commons.web..",
    "com.ecomm.commons.security.."
  };

  private HexagonalRules() {}

  @ArchTest
  public static final ArchRule domainIsFrameworkFree =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(FRAMEWORKS)
          .orShould()
          .dependOnClassesThat()
          .resideInAnyPackage("..application..", "..adapter..")
          .because("the domain is pure Java and knows nothing about use cases or adapters")
          .allowEmptyShould(true);

  @ArchTest
  public static final ArchRule applicationIsFrameworkFree =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(FRAMEWORKS)
          .orShould()
          .dependOnClassesThat()
          .resideInAPackage("..adapter..")
          .because("use cases are plain Java and reach the outside world only through ports")
          .allowEmptyShould(true);

  @ArchTest
  public static final ArchRule adaptersUseCasesOnlyThroughPorts =
      noClasses()
          .that()
          .resideInAPackage("..adapter..")
          .should()
          .dependOnClassesThat(
              resideInAPackage("..application..")
                  .and(resideInAPackage("..application.port..").negate()))
          .because("adapters depend on ports, not on use-case implementations")
          .allowEmptyShould(true);

  @ArchTest
  public static final ArchRule inboundAdaptersDoNotUseOutboundAdapters =
      noClasses()
          .that()
          .resideInAPackage("..adapter.in..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..adapter.out..")
          .because("inbound adapters go through use cases, not around them")
          .allowEmptyShould(true);

  @ArchTest
  public static final ArchRule outboundAdaptersDoNotUseInboundAdapters =
      noClasses()
          .that()
          .resideInAPackage("..adapter.out..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..adapter.in..")
          .because("outbound adapters only implement ports")
          .allowEmptyShould(true);
}
