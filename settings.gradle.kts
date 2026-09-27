pluginManagement { includeBuild("build-logic") }

dependencyResolutionManagement {
  repositories { mavenCentral() }
}

rootProject.name = "ecomm"

include(":platform:service-commons", ":platform:service-template", ":services:catalog", ":services:inventory", ":services:cart", ":services:payment", ":services:order-management", ":services:checkout-pricing")
