package com.ecomm.supportassistant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * Each turn leaves one log line, with its Conversation ID, Correlation ID, the tools it called and
 * how long it took, and never the Customer's words or the reply.
 */
@ExtendWith(OutputCaptureExtension.class)
class TurnLogApiTest extends SupportAssistantApiTest {

  @Test
  void aTurnIsLoggedWithoutTheCustomersWordsOrTheReply(CapturedOutput output) {
    stubLatestOrderPaid();
    var id = started();

    send(id, "Where is my order, the one for Erika?").expectStatus().isOk();

    var line =
        output
            .getOut()
            .lines()
            .filter(l -> l.contains("Turn in Conversation " + id + " replied"))
            .toList();
    assertThat(line)
        .singleElement()
        .satisfies(
            l ->
                assertThat(l)
                    .contains("[test-correlation-1]")
                    .contains("tools [find_orders found, get_order found, get_shipment not_found]")
                    .contains("4 provider calls")
                    .containsPattern("in \\d+ ms"));
    assertThat(output.getOut()).doesNotContain("Erika").doesNotContain("is paid");
  }
}
