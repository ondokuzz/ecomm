// Shared library for every service. Only the web, security, idempotency, metrics and
// events.{outbox,kafka} packages depend on frameworks; money and events are pure Java so domain and
// application code can use them.
plugins {
  id("ecomm.java-conventions")
  `java-library`
  `java-test-fixtures`
}

dependencies {
  compileOnly(platform(libs.spring.modulith.bom))
  compileOnly("org.springframework.boot:spring-boot-autoconfigure")
  compileOnly("org.springframework:spring-webmvc")
  compileOnly("org.springframework:spring-jdbc")
  compileOnly("org.springframework.boot:spring-boot-restclient")
  compileOnly("jakarta.servlet:jakarta.servlet-api")
  compileOnly("org.slf4j:slf4j-api")
  compileOnly("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
  compileOnly("org.springframework.boot:spring-boot-kafka")
  compileOnly("org.springframework.modulith:spring-modulith-events-api")
  compileOnly("tools.jackson.core:jackson-databind")
  compileOnly("org.springframework.modulith:spring-modulith-events-core")
  compileOnly("io.micrometer:micrometer-core")

  // Every service serves /actuator/prometheus (see metrics.MetricsDefaults).
  runtimeOnly("io.micrometer:micrometer-registry-prometheus")

  testFixturesApi(libs.archunit.junit5)
  testFixturesImplementation("org.springframework.security:spring-security-oauth2-jose")
  testFixturesImplementation("org.springframework:spring-test")
  testFixturesImplementation("org.testcontainers:testcontainers-kafka")
  testFixturesImplementation("org.apache.kafka:kafka-clients")
  testFixturesImplementation("tools.jackson.core:jackson-databind")
}
