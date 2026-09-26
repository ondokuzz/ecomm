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

tasks.withType<Test>().configureEach { useJUnitPlatform() }

spotless {
  java {
    googleJavaFormat()
    removeUnusedImports()
  }
}

pluginManager.withPlugin("java-test-fixtures") {
  dependencies { "testFixturesImplementation"(springBootBom) }
}
