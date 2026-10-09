plugins { id("ecomm.spring-service-conventions") }

dependencies {
  // Checkout's own client-credentials token for its internal calls (Identity & Access ADR 0002).
  implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
  implementation("org.springframework.boot:spring-boot-starter-restclient")
  // Checkout Sessions (ADR 0001).
  implementation("org.springframework.boot:spring-boot-starter-data-redis")
  // Paying starts the checkout Saga through Temporal's client (ADR 0009). The starter brings
  // Temporal's test server along; only the tests need it.
  implementation(libs.temporal.spring.boot.starter) {
    exclude(group = "io.temporal", module = "temporal-testing")
  }

  testImplementation(libs.wiremock.standalone)
  testImplementation(libs.temporal.testing)
  testImplementation("org.awaitility:awaitility")
  testImplementation("org.springframework.boot:spring-boot-testcontainers")
  testImplementation("org.testcontainers:testcontainers-junit-jupiter")
}
