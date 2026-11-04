package com.ecomm.supportassistant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;

/** A turn whose provider fails is answered 500, and still leaves its log line, as failed. */
@ExtendWith(OutputCaptureExtension.class)
@Import(GroundingApiTest.Provider.class)
class FailedTurnLogApiTest extends SupportAssistantApiTest {

  @Autowired ScriptedSupportModel model;

  @BeforeEach
  void resetModel() {
    model.reset();
  }

  @Test
  void aTurnThatFailsIsLoggedAsFailed(CapturedOutput output) {
    // Nothing is scripted, so the provider throws.
    var id = started();

    send(id, "Where is my order?").expectStatus().is5xxServerError();

    assertThat(output.getOut())
        .contains("Turn in Conversation " + id + " failed: tools [], 1 provider calls")
        .doesNotContain("Where is my order?");
  }
}
