plugins { id("ecomm.spring-service-conventions") }

dependencies {
  // The Temporal worker for every Saga (ADR 0009). The starter brings Temporal's test server along;
  // only the tests need it.
  implementation(libs.temporal.spring.boot.starter) {
    exclude(group = "io.temporal", module = "temporal-testing")
  }
  // Orchestration's own client-credentials token for the Saga steps' calls (Identity & Access ADR
  // 0002).
  implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
  implementation("org.springframework.boot:spring-boot-starter-restclient")

  testImplementation(libs.temporal.testing)
  testImplementation(libs.wiremock.standalone)
}
