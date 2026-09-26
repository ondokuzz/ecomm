plugins { id("ecomm.spring-service-conventions") }

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-couchbase")

  testImplementation("org.springframework.boot:spring-boot-testcontainers")
  testImplementation("org.testcontainers:testcontainers-couchbase")
  testImplementation("org.testcontainers:testcontainers-junit-jupiter")
}
