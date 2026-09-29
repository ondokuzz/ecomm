plugins { id("ecomm.spring-service-conventions") }

dependencies {
  implementation(platform(libs.spring.cloud.bom))
  implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webmvc")

  testImplementation(libs.wiremock.standalone)
}
