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
  // Kafka alone: Orchestration only consumes, so its tests need no schema registry.
  testImplementation("org.testcontainers:testcontainers-kafka")
  testImplementation("org.awaitility:awaitility")
}

// One JVM for every test class, and one Spring context alive at a time: when a class needs another
// context, the last one is closed, so no earlier context's Temporal worker or Kafka consumer takes
// a later class's tasks or events. FulfillmentWorkflowTest runs on a test server of its own,
// without time skipping: a Saga it terminates while a workflow task is outstanding leaves that
// server's time skipping locked for good, which on the shared server made CheckoutWorkflowTest's
// hour of retries wait in real time. Each class first checks that the shared server still skips
// time (OrchestrationTest.timeSkips).
tasks.test { systemProperty("spring.test.context.cache.maxSize", "1") }
