plugins { id("ecomm.spring-service-conventions") }

dependencies {
  // RMAs and the projections of delivered Shipments and Products, in its own returns_warranty
  // database.
  implementation("org.springframework.boot:spring-boot-starter-jdbc")
  implementation("org.springframework.boot:spring-boot-starter-flyway")
  implementation("org.flywaydb:flyway-database-postgresql")
  runtimeOnly("org.postgresql:postgresql")

  // Publishing RMA events through service-commons' port, with Postgres as the outbox, and consuming
  // Fulfillment & Shipping's Shipment events and Catalog's Product events.
  implementation(platform(libs.spring.modulith.bom))
  implementation("org.springframework.boot:spring-boot-starter-kafka")
  implementation("org.springframework.modulith:spring-modulith-starter-jdbc")
  implementation("org.springframework.modulith:spring-modulith-events-kafka")
  implementation(libs.apicurio.jsonschema.serde.kafka)

  testImplementation("org.springframework.boot:spring-boot-testcontainers")
  testImplementation("org.testcontainers:testcontainers-postgresql")
  testImplementation("org.testcontainers:testcontainers-junit-jupiter")
  testImplementation("org.apache.kafka:kafka-clients")
  testImplementation("org.awaitility:awaitility")
  testImplementation(project(":platform:event-schemas"))
  testImplementation(libs.json.schema.validator)
}
