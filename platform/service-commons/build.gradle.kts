// Shared library for every service. Only the web package depends on Spring; money is pure Java so
// domain code can use it.
plugins {
  id("ecomm.java-conventions")
  `java-library`
  `java-test-fixtures`
}

dependencies {
  compileOnly("org.springframework.boot:spring-boot-autoconfigure")
  compileOnly("org.springframework:spring-webmvc")
  compileOnly("jakarta.servlet:jakarta.servlet-api")
  compileOnly("org.slf4j:slf4j-api")

  testFixturesApi(libs.archunit.junit5)
}
