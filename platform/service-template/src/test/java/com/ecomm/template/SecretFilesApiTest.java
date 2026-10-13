package com.ecomm.template;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * A secret given only as a file, as Compose mounts one
 * (docs/adr/0011-secrets-as-generated-files.md): the file's name is the property, its content the
 * value. Here the database password is in no property at all, only in a file in the secrets
 * directory, and the service still opens its database.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "ecomm.secrets-dir=" + SecretFilesApiTest.SECRETS_DIR + "/")
@AutoConfigureRestTestClient
class SecretFilesApiTest {

  static final String SECRETS_DIR = "build/secret-files-test";

  static {
    try {
      var dir = Files.createDirectories(Path.of(SECRETS_DIR));
      Files.writeString(
          dir.resolve("spring.datasource.password"), TestInfrastructure.databasePassword());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    TestInfrastructure.registerAllButTheDatabasePasswordWith(registry);
  }

  @Autowired RestTestClient http;

  @Test
  void aDatabasePasswordGivenOnlyAsAFileOpensTheDatabase() {
    http.get()
        .uri("/actuator/health")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("UP");
  }
}
