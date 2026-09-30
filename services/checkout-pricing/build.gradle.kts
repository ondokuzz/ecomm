plugins { id("ecomm.spring-service-conventions") }

dependencies {
  // Checkout's own client-credentials token for its internal calls (Identity & Access ADR 0002).
  implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
  implementation("org.springframework.boot:spring-boot-starter-restclient")
  // Checkout Sessions (ADR 0001).
  implementation("org.springframework.boot:spring-boot-starter-data-redis")

  testImplementation(libs.wiremock.standalone)
  testImplementation("org.springframework.boot:spring-boot-testcontainers")
  testImplementation("org.testcontainers:testcontainers-junit-jupiter")
}
