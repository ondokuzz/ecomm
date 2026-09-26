plugins { id("ecomm.spring-service-conventions") }

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-data-redis")

  testImplementation("org.springframework.boot:spring-boot-testcontainers")
  testImplementation("org.testcontainers:testcontainers-junit-jupiter")
}
