plugins { id("ecomm.spring-service-conventions") }

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-data-redis")
  // Publishing cart.cart straight to Kafka with the idempotent producer: Cart has no Postgres, so
  // no outbox (ADR 0002's 2026-10-25 note).
  implementation("org.springframework.boot:spring-boot-starter-kafka")
  implementation(libs.apicurio.jsonschema.serde.kafka)

  testImplementation("org.springframework.boot:spring-boot-testcontainers")
  testImplementation("org.testcontainers:testcontainers-junit-jupiter")
  testImplementation("org.apache.kafka:kafka-clients")
  testImplementation(project(":platform:event-schemas"))
}
