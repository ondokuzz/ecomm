plugins { id("ecomm.spring-service-conventions") }

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-couchbase")

  // Publishing Product and Category events through service-commons' port, with a Couchbase outbox
  // and a relay to Kafka.
  implementation("org.springframework.boot:spring-boot-starter-kafka")
  implementation(libs.apicurio.jsonschema.serde.kafka)

  testImplementation("org.springframework.boot:spring-boot-testcontainers")
  testImplementation("org.testcontainers:testcontainers-couchbase")
  testImplementation("org.testcontainers:testcontainers-junit-jupiter")
  testImplementation("org.apache.kafka:kafka-clients")
  testImplementation("org.awaitility:awaitility")
  testImplementation(project(":platform:event-schemas"))
  testImplementation(libs.json.schema.validator)
}
