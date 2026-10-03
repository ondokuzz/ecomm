plugins { id("ecomm.spring-service-conventions") }

dependencies {
  // Postgres, whose event publication registry is the outbox for integration events.
  implementation("org.springframework.boot:spring-boot-starter-jdbc")
  implementation("org.springframework.boot:spring-boot-starter-flyway")
  implementation("org.flywaydb:flyway-database-postgresql")
  runtimeOnly("org.postgresql:postgresql")

  // Publishing integration events through service-commons' port, and consuming them.
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
}
