plugins { id("ecomm.spring-service-conventions") }

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-jdbc")
  implementation("org.springframework.boot:spring-boot-starter-flyway")
  implementation("org.flywaydb:flyway-database-postgresql")
  runtimeOnly("org.postgresql:postgresql")
  // The mock gateway posts its webhooks to Payment's own endpoint, as a real gateway would.
  implementation("org.springframework.boot:spring-boot-starter-restclient")

  // Publishing Payment events through service-commons' port, with Postgres as the outbox.
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
