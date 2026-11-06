plugins { id("ecomm.spring-service-conventions") }

dependencies {
  // Order Management's reads, with the Customer's own token (ADR 0002).
  implementation("org.springframework.boot:spring-boot-starter-restclient")
  // Conversations.
  implementation("org.springframework.boot:spring-boot-starter-data-redis")
  // Claude, when the API key is set (ADR 0003).
  implementation(libs.anthropic.java)
  // A failing provider is answered fast as unavailable.
  implementation(libs.resilience4j.circuitbreaker)

  testImplementation(libs.wiremock.standalone)
  testImplementation("org.springframework.boot:spring-boot-testcontainers")
  testImplementation("org.testcontainers:testcontainers-junit-jupiter")
  testImplementation("org.awaitility:awaitility")
}
