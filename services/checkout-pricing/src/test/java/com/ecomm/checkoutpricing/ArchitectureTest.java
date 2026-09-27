package com.ecomm.checkoutpricing;

import com.ecomm.commons.architecture.HexagonalRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

@AnalyzeClasses(
    packages = "com.ecomm.checkoutpricing",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest static final ArchTests hexagonal = ArchTests.in(HexagonalRules.class);
}
