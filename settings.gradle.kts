pluginManagement { includeBuild("build-logic") }

dependencyResolutionManagement {
  repositories { mavenCentral() }
}

rootProject.name = "ecomm"

include(":platform:service-commons", ":platform:service-template")
