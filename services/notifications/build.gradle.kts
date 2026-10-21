plugins { id("ecomm.spring-service-conventions") }

dependencies {
  // The Notification ledger and the Product names projection, in Postgres (README).
  implementation("org.springframework.boot:spring-boot-starter-jdbc")
  implementation("org.springframework.boot:spring-boot-starter-flyway")
  implementation("org.flywaydb:flyway-database-postgresql")
  runtimeOnly("org.postgresql:postgresql")
  // Consuming Order Management's and Catalog's integration events; Notifications publishes none.
  implementation("org.springframework.boot:spring-boot-starter-kafka")
  // The email NotificationProvider, over SMTP (ADR 0005).
  implementation("org.springframework.boot:spring-boot-starter-mail")
  // Jakarta Mail's implementation, which the starter brings at runtime only: its exceptions carry
  // the SMTP reply code that tells a permanent refusal from a transient one.
  implementation("org.eclipse.angus:angus-mail")
  // Message templates.
  implementation("com.samskivert:jmustache")
  // The CustomerDirectory reads Keycloak's admin API with Notifications' own client-credentials
  // token (Identity & Access ADR 0002), and caches its answers.
  implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
  implementation("org.springframework.boot:spring-boot-starter-restclient")
  implementation("com.github.ben-manes.caffeine:caffeine")

  testImplementation(project(":platform:event-schemas"))
  testImplementation(libs.json.schema.validator)
  testImplementation(libs.wiremock.standalone)
  testImplementation("org.springframework.boot:spring-boot-testcontainers")
  testImplementation("org.testcontainers:testcontainers-postgresql")
  testImplementation("org.testcontainers:testcontainers-junit-jupiter")
  testImplementation("org.apache.kafka:kafka-clients")
  testImplementation("org.awaitility:awaitility")
}

// One Spring context alive at a time: when a class needs another (the SMTP outage's, say), the last
// one is closed first, so no earlier context's consumers take a later class's events, nor its
// dispatcher its Notifications.
tasks.test { systemProperty("spring.test.context.cache.maxSize", "1") }
