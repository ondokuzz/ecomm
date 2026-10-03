// The integration events' JSON Schemas, one per topic, in schemas/<topic>.json: the only place an
// event's shape is defined. register.sh registers them in Apicurio, and they are on the classpath as
// event-schemas/<topic>.json for tests that register them in a test registry.
plugins {
  id("ecomm.java-conventions")
  `java-library`
}

tasks.processResources { from("schemas") { into("event-schemas") } }

dependencies {
  testImplementation(libs.json.schema.validator)
  testImplementation("com.fasterxml.jackson.core:jackson-databind")
  testImplementation("org.assertj:assertj-core")
}
