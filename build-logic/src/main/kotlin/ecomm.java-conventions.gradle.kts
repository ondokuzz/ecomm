// Every JVM module: Java 21 toolchain, JUnit 5, google-java-format via Spotless.
plugins {
  java
  id("com.diffplug.spotless")
}

group = "com.ecomm"

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

val springBootBom = dependencies.platform(the<VersionCatalogsExtension>().named("libs").findLibrary("spring-boot-bom").get())

dependencies {
  // Pin every module to Spring Boot's managed versions, services and libraries alike.
  implementation(springBootBom)
  testImplementation(springBootBom)
  testImplementation("org.junit.jupiter:junit-jupiter")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach { options.compilerArgs.add("-parameters") }

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
  // A failed test says why on the console, its assertion and causes in full, rather than only in
  // the HTML report and the JUnit XML, with `-q` too, which keeps everything else quiet.
  testLogging {
    for (level in listOf(lifecycle, quiet)) {
      level.events("failed")
      level.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
      level.showCauses = true
      level.showStackTraces = true
    }
  }
}

spotless {
  java {
    googleJavaFormat()
    removeUnusedImports()
  }
}

pluginManager.withPlugin("java-test-fixtures") {
  dependencies { "testFixturesImplementation"(springBootBom) }
}
