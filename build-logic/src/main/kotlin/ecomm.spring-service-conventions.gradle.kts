// A deployable Spring Boot service built from platform/service-template. Its image comes from the
// service's own Dockerfile, which packages the bootJar produced here.
plugins {
  id("ecomm.java-conventions")
  id("org.springframework.boot")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
  implementation(project(":platform:service-commons"))
  implementation("org.springframework.boot:spring-boot-starter-webmvc")
  implementation("org.springframework.boot:spring-boot-starter-actuator")

  testImplementation(testFixtures(project(":platform:service-commons")))
  testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
  testImplementation("org.springframework.boot:spring-boot-resttestclient")
  testImplementation(libs.findLibrary("archunit-junit5").get())
}

// A stable name so every service's Dockerfile copies the same file.
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
  archiveFileName = "app.jar"
}

tasks.named<Jar>("jar") { enabled = false }
