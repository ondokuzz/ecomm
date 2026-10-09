package com.ecomm.orchestration;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.ecomm.commons.architecture.HexagonalRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "com.ecomm.orchestration",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest static final ArchTests hexagonal = ArchTests.in(HexagonalRules.class);

  /**
   * Workflow code must replay the same way every time, so it does no I/O of its own: it reaches
   * other contexts only through the activity interfaces in {@code application.port}, never through
   * an HTTP client, a socket or a file.
   */
  @ArchTest
  static final ArchRule workflowsReachOtherContextsOnlyThroughActivities =
      classes()
          .that()
          .resideInAPackage("com.ecomm.orchestration.application")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage(
              "com.ecomm.orchestration.application..",
              "io.temporal.activity..",
              "io.temporal.common..",
              "io.temporal.failure..",
              "io.temporal.workflow..",
              "com.uber.m3.tally..",
              "org.slf4j..",
              "java.lang..",
              "java.time..",
              "java.util..");
}
