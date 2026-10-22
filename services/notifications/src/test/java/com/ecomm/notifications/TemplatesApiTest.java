package com.ecomm.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.commons.security.FakeKeycloak;
import com.ecomm.notifications.domain.MessageType;
import com.ecomm.notifications.domain.NotificationChannel;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.ParameterizedTypeReference;

/**
 * Staff preview every Message template, rendered with its Message type's sample content, on each
 * channel the type goes on; every SMS fits 160 characters.
 */
class TemplatesApiTest extends NotificationsApiTest {

  record Preview(
      String messageType,
      String channel,
      String locale,
      String subject,
      String html,
      String text) {}

  @Test
  void staffListEveryMessageTypeAndChannelWithItsPreview() {
    var previews =
        http.get()
            .uri("/templates")
            .header("Authorization", "Bearer " + STAFF)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(new ParameterizedTypeReference<List<Preview>>() {})
            .returnResult()
            .getResponseBody();

    assertThat(previews)
        .extracting(Preview::messageType, Preview::channel)
        .containsExactlyElementsOf(
            Stream.of(MessageType.values())
                .flatMap(
                    t ->
                        t.channels().stream()
                            .map(c -> org.assertj.core.groups.Tuple.tuple(t.name(), c.name())))
                .toList());
    var confirmed = previews.getFirst();
    assertThat(confirmed.locale()).isEqualTo("en");
    assertThat(confirmed.subject()).isEqualTo("Your Order #3F2A9C1B is confirmed");
    assertThat(confirmed.text())
        .contains("Hello Ada,")
        .contains("1 × Google Pixel 9 (128 GB, Obsidian): €799.00")
        .contains("2 × Sony WH-1000XM5 (Black): €758.00")
        .contains("Discount: −€155.70")
        .contains("Total: €1,406.29");
    assertThat(confirmed.html()).startsWith("<!DOCTYPE html>").contains("€1,406.29");
  }

  static Stream<Arguments> everyMessageTypeAndChannel() {
    return Stream.of(MessageType.values())
        .flatMap(t -> t.channels().stream().map(c -> Arguments.of(t, c)));
  }

  @ParameterizedTest
  @MethodSource("everyMessageTypeAndChannel")
  void everyTemplateRendersItsSample(MessageType type, NotificationChannel channel) {
    var preview =
        preview(type.name(), channel.name(), STAFF)
            .expectStatus()
            .isOk()
            .expectBody(Preview.class)
            .returnResult()
            .getResponseBody();

    assertThat(preview.messageType()).isEqualTo(type.name());
    assertThat(preview.channel()).isEqualTo(channel.name());
    assertThat(preview.text()).isNotBlank();
    switch (channel) {
      case EMAIL -> {
        assertThat(preview.subject()).isNotBlank().doesNotContain("\n");
        assertThat(preview.html()).isNotBlank();
      }
      case SMS -> {
        assertThat(preview.subject()).isNull();
        assertThat(preview.text()).hasSizeLessThanOrEqualTo(160).doesNotContain("\n");
      }
      case PUSH -> assertThat(preview.subject()).isNotBlank().doesNotContain("\n");
    }
  }

  @Test
  void theShippedSmsAndPushReadAsTheCustomerGetsThem() {
    var sms = preview("ORDER_SHIPPED", "SMS", STAFF).expectBody(Preview.class).returnResult();
    var push = preview("ORDER_SHIPPED", "PUSH", STAFF).expectBody(Preview.class).returnResult();

    assertThat(sms.getResponseBody().text())
        .isEqualTo("Ecomm: your Order #3F2A9C1B has shipped. Mock Carrier tracking: MC0012345678");
    assertThat(push.getResponseBody().subject()).isEqualTo("Your Order #3F2A9C1B is on its way");
    assertThat(push.getResponseBody().text())
        .isEqualTo(
            "1 × Google Pixel 9 (128 GB, Obsidian) and 1 more, with Mock Carrier: MC0012345678");
  }

  @Test
  void anUnknownMessageTypeOrChannelIsNotFound() {
    preview("NO_SUCH_TYPE", "EMAIL", STAFF).expectStatus().isNotFound();
    preview("ORDER_CONFIRMED", "PIGEON", STAFF).expectStatus().isNotFound();
    preview("ORDER_CONFIRMED", "SMS", STAFF).expectStatus().isNotFound();
  }

  @Test
  void onlyStaffPreview() {
    var customer = FakeKeycloak.token("c-1", "CUSTOMER");
    preview("ORDER_CONFIRMED", "EMAIL", customer).expectStatus().isForbidden();
    http.get()
        .uri("/templates")
        .header("Authorization", "Bearer " + customer)
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  private org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec preview(
      String type, String channel, String token) {
    return http.post()
        .uri("/templates/{type}/{channel}/preview", type, channel)
        .header("Authorization", "Bearer " + token)
        .exchange();
  }
}
