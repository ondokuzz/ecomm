pluginManagement { includeBuild("build-logic") }

dependencyResolutionManagement {
  repositories { mavenCentral() }
}

rootProject.name = "ecomm"

include(":platform:service-commons", ":platform:event-schemas", ":platform:service-template", ":platform:api-gateway", ":services:catalog", ":services:inventory", ":services:cart", ":services:payment", ":services:order-management", ":services:checkout-pricing", ":services:promotions", ":services:search-discovery", ":services:reviews-ratings")
