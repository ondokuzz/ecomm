plugins { id("ecomm.spring-service-conventions") }

dependencies {
  // Order Management's reads, with the Customer's own token (ADR 0002).
  implementation("org.springframework.boot:spring-boot-starter-restclient")
  // Conversations.
  implementation("org.springframework.boot:spring-boot-starter-data-redis")

  testImplementation(libs.wiremock.standalone)
  testImplementation("org.springframework.boot:spring-boot-testcontainers")
  testImplementation("org.testcontainers:testcontainers-junit-jupiter")
  testImplementation("org.awaitility:awaitility")
}
