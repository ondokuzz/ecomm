plugins { id("ecomm.spring-service-conventions") }

dependencies {
  // Checkout's own client-credentials token for its internal calls (Identity & Access ADR 0002).
  implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
  implementation("org.springframework.boot:spring-boot-starter-restclient")

  testImplementation(libs.wiremock.standalone)
}
