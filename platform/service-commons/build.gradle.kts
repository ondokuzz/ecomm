// Shared library for every service. Only the web and security packages depend on Spring; money is
// pure Java so domain code can use it.
plugins {
  id("ecomm.java-conventions")
  `java-library`
  `java-test-fixtures`
}

dependencies {
  compileOnly("org.springframework.boot:spring-boot-autoconfigure")
  compileOnly("org.springframework:spring-webmvc")
  compileOnly("org.springframework.boot:spring-boot-restclient")
  compileOnly("jakarta.servlet:jakarta.servlet-api")
  compileOnly("org.slf4j:slf4j-api")
  compileOnly("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")

  testFixturesApi(libs.archunit.junit5)
  testFixturesImplementation("org.springframework.security:spring-security-oauth2-jose")
  testFixturesImplementation("org.springframework:spring-test")
}
