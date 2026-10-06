plugins { id("ecomm.spring-service-conventions") }

dependencies {
  // Reviews and the projection they're checked against (ADR 0001).
  implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
  // Consuming Order Management's and Catalog's integration events; Reviews publishes none.
  implementation("org.springframework.boot:spring-boot-starter-kafka")

  testImplementation(project(":platform:event-schemas"))
  testImplementation(libs.json.schema.validator)
  testImplementation("org.springframework.boot:spring-boot-testcontainers")
  testImplementation("org.testcontainers:testcontainers-mongodb")
  testImplementation("org.testcontainers:testcontainers-junit-jupiter")
  testImplementation("org.apache.kafka:kafka-clients")
  testImplementation("org.awaitility:awaitility")
}
