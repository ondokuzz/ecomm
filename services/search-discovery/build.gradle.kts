plugins { id("ecomm.spring-service-conventions") }

dependencies {
  // The projection's store, with the text index search runs on (ADR 0001).
  implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
  // Consuming Catalog's and Inventory's integration events; Search publishes none.
  implementation("org.springframework.boot:spring-boot-starter-kafka")

  testImplementation(project(":platform:event-schemas"))
  testImplementation(libs.json.schema.validator)
  testImplementation("org.springframework.boot:spring-boot-testcontainers")
  testImplementation("org.testcontainers:testcontainers-mongodb")
  testImplementation("org.testcontainers:testcontainers-junit-jupiter")
  testImplementation("org.apache.kafka:kafka-clients")
  testImplementation("org.awaitility:awaitility")
}
