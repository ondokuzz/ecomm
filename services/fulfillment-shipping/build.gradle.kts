plugins { id("ecomm.spring-service-conventions") }

dependencies {
  // Its own fulfillment_shipping database. Quotes need no tables; Shipments bring the first ones.
  implementation("org.springframework.boot:spring-boot-starter-jdbc")
  runtimeOnly("org.postgresql:postgresql")

  testImplementation("org.springframework.boot:spring-boot-testcontainers")
  testImplementation("org.testcontainers:testcontainers-postgresql")
  testImplementation("org.testcontainers:testcontainers-junit-jupiter")
}
