package com.ecomm.eventschemas;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every topic's schema accepts an example event, refuses fields it doesn't define, and closes every
 * object, which is what lets the registry's BACKWARD check refuse a removed field.
 */
class EventSchemasTest {

  private static final JsonMapper JSON = new JsonMapper();

  /** Every schema in {@code schemas/}, as register.sh and Compose's kafka-topics find them. */
  static Stream<String> topics() throws IOException {
    try (var files = Files.list(Path.of("schemas"))) {
      return files
          .map(file -> file.getFileName().toString())
          .filter(name -> name.endsWith(".json"))
          .map(name -> name.substring(0, name.length() - ".json".length()))
          .sorted()
          .toList()
          .stream();
    }
  }

  @ParameterizedTest
  @MethodSource("topics")
  void theExampleEventMatchesItsSchema(String topic) {
    assertThat(schemaOf(topic).validate(exampleOf(topic))).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("topics")
  void aFieldTheSchemaDoesntDefineIsRefused(String topic) {
    var event = exampleOf(topic);
    ((ObjectNode) event).put("unexpected", true);

    assertThat(schemaOf(topic).validate(event)).isNotEmpty();
  }

  @ParameterizedTest
  @MethodSource("topics")
  void everyObjectWithPropertiesIsClosed(String topic) {
    var open = new ArrayList<String>();
    collectOpenObjects(read("event-schemas/" + topic + ".json"), "", open);

    assertThat(open).as("objects without additionalProperties: false").isEmpty();
  }

  private static void collectOpenObjects(JsonNode node, String path, List<String> open) {
    var closed =
        node.path("additionalProperties").isBoolean()
            && !node.path("additionalProperties").asBoolean();
    if (node.has("properties") && !closed) {
      open.add(path.isEmpty() ? "/" : path);
    }
    node.properties().forEach(e -> collectOpenObjects(e.getValue(), path + "/" + e.getKey(), open));
  }

  private static JsonSchema schemaOf(String topic) {
    return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)
        .getSchema(read("event-schemas/" + topic + ".json"));
  }

  private static JsonNode exampleOf(String topic) {
    return read("examples/" + topic + ".json");
  }

  private static JsonNode read(String resource) {
    try (InputStream in = EventSchemasTest.class.getClassLoader().getResourceAsStream(resource)) {
      assertThat(in).as(resource).isNotNull();
      return JSON.readTree(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
