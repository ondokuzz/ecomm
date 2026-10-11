plugins { id("ecomm.spring-service-conventions") }

dependencies {
  // The projection, in Postgres: one transaction per event, and co-occurrence as a join (README).
  implementation("org.springframework.boot:spring-boot-starter-jdbc")
  implementation("org.springframework.boot:spring-boot-starter-flyway")
  implementation("org.flywaydb:flyway-database-postgresql")
  runtimeOnly("org.postgresql:postgresql")
  // Consuming Order Management's and Catalog's integration events; Recommendations publishes none.
  implementation("org.springframework.boot:spring-boot-starter-kafka")

  testImplementation(project(":platform:event-schemas"))
  testImplementation(libs.json.schema.validator)
  testImplementation("org.springframework.boot:spring-boot-testcontainers")
  testImplementation("org.testcontainers:testcontainers-postgresql")
  testImplementation("org.testcontainers:testcontainers-junit-jupiter")
  testImplementation("org.apache.kafka:kafka-clients")
  testImplementation("org.awaitility:awaitility")
}
