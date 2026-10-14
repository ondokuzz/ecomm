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
  // Order and Shipment events start and wake the fulfillment Saga; Orchestration publishes none.
  implementation("org.springframework.boot:spring-boot-starter-kafka")

  testImplementation(libs.temporal.testing)
  testImplementation(libs.wiremock.standalone)
  testImplementation(project(":platform:event-schemas"))
  testImplementation(libs.json.schema.validator)
  testImplementation("org.apache.kafka:kafka-clients")
  testImplementation("org.awaitility:awaitility")
}
