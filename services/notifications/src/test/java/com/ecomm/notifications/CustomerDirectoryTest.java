package com.ecomm.notifications;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecomm.notifications.application.port.out.CustomerDirectory;
import com.ecomm.notifications.application.port.out.CustomerDirectory.DirectoryUnavailableException;
import com.ecomm.notifications.domain.Recipient;
import java.util.Base64;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The {@code CustomerDirectory} against Keycloak's token endpoint and admin API in WireMock: a
 * Customer read with Notifications' own token, kept for ten minutes, and the ways it can't be.
 */
class CustomerDirectoryTest extends NotificationsApiTest {

  @Autowired CustomerDirectory directory;

  @Test
  void aCustomerIsReadFromKeycloaksAdminApiWithNotificationsOwnToken() {
    var customer = newCustomerId();
    Keycloak.customer(customer, "ada@example.com", "Ada", "Lovelace");

    var recipient = directory.find(customer);

    assertThat(recipient)
        .contains(new Recipient(customer, "ada@example.com", "Ada", "Lovelace", Locale.ENGLISH));
    Keycloak.SERVER.verify(
        getRequestedFor(urlEqualTo(Keycloak.USERS_PATH + customer))
            .withHeader("Authorization", equalTo("Bearer notifications-token-1")));
    var clientIdAndSecret =
        Base64.getEncoder().encodeToString("notifications:test".getBytes(UTF_8));
    Keycloak.SERVER.verify(
        postRequestedFor(urlEqualTo(Keycloak.TOKEN_PATH))
            .withHeader("Authorization", equalTo("Basic " + clientIdAndSecret))
            .withRequestBody(equalTo("grant_type=client_credentials")));
  }

  @Test
  void aCustomerReadOnceIsKeptRatherThanReadAgain() {
    var customer = newCustomerId();
    Keycloak.customer(customer, "ada@example.com", "Ada", "Lovelace");

    directory.find(customer);
    var again = directory.find(customer);

    assertThat(again).map(Recipient::email).contains("ada@example.com");
    Keycloak.SERVER.verify(1, getRequestedFor(urlEqualTo(Keycloak.USERS_PATH + customer)));
  }

  @Test
  void aCustomerKeycloakDoesNotKnowIsNobody() {
    assertThat(directory.find(newCustomerId())).isEmpty();
  }

  @Test
  void aCustomerWithNoEmailAddressIsNobody() {
    var customer = newCustomerId();
    Keycloak.customer(customer, null, "No", "Address");

    assertThat(directory.find(customer)).isEmpty();
  }

  @Test
  void keycloakDownIsTheDirectoryUnavailable() {
    Keycloak.down();

    assertThatThrownBy(() -> directory.find(newCustomerId()))
        .isInstanceOf(DirectoryUnavailableException.class);
  }

  @Test
  void noTokenIsTheDirectoryUnavailable() {
    Keycloak.SERVER.resetAll();

    assertThatThrownBy(() -> directory.find(newCustomerId()))
        .isInstanceOf(DirectoryUnavailableException.class);
  }
}
