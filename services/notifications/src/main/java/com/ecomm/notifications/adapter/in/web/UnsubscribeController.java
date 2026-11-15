package com.ecomm.notifications.adapter.in.web;

import com.ecomm.notifications.application.port.in.UnsubscribeUseCase;
import com.ecomm.notifications.application.port.in.UnsubscribeUseCase.InvalidTokenException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A Cart reminder's unsubscribe link, opened without signing in, and answered as small HTML pages
 * since a browser or a mail client opens it. {@code POST} turns cart reminders off: the reminder's
 * {@code List-Unsubscribe} header for a mail client's one-click unsubscribe (RFC 8058), and the
 * button on the page {@code GET} answers. {@code GET} itself changes nothing, so a link scanner
 * that opens it unsubscribes nobody. A bad or expired token is a 400 page saying to sign in.
 */
@RestController
@RequestMapping(value = "/unsubscribe/cart-reminders", produces = MediaType.TEXT_HTML_VALUE)
class UnsubscribeController {

  private final UnsubscribeUseCase unsubscribe;
  private final String preferencesUrl;

  UnsubscribeController(
      UnsubscribeUseCase unsubscribe,
      @Value("${ecomm.notifications.storefront-url}") String storefrontUrl) {
    this.unsubscribe = unsubscribe;
    this.preferencesUrl = storefrontUrl + "/notification-preferences";
  }

  /** Asks to confirm. The form has no action, so it posts back to this URL, token and all. */
  @GetMapping
  ResponseEntity<String> confirm(@RequestParam(required = false) String token) {
    if (!unsubscribe.isValid(token)) {
      throw new InvalidTokenException();
    }
    return page(
        HttpStatus.OK,
        "Unsubscribe from cart reminders",
        "<p>Stop the emails about Carts you leave? You'll still get the emails about your Orders,"
            + " Payments and returns.</p>"
            + "<form method=\"post\">"
            + "<input type=\"hidden\" name=\"List-Unsubscribe\" value=\"One-Click\">"
            + "<button type=\"submit\">Turn off cart reminders</button>"
            + "</form>");
  }

  @PostMapping
  ResponseEntity<String> unsubscribe(@RequestParam(required = false) String token) {
    unsubscribe.unsubscribeFromCartReminders(token);
    return page(
        HttpStatus.OK,
        "Cart reminders are off",
        "<p>You won't be emailed about Carts you leave. You can turn cart reminders on again in"
            + " your <a href=\""
            + preferencesUrl
            + "\">Notification preferences</a>.</p>");
  }

  @ExceptionHandler(InvalidTokenException.class)
  ResponseEntity<String> invalid() {
    return page(
        HttpStatus.BAD_REQUEST,
        "This link has expired",
        "<p>This unsubscribe link has expired or isn't valid. To turn cart reminders off, sign in"
            + " and change your <a href=\""
            + preferencesUrl
            + "\">Notification preferences</a>.</p>");
  }

  /** A page of its own, with the Storefront's colours; nothing in it comes from the request. */
  private static ResponseEntity<String> page(HttpStatus status, String title, String body) {
    var html =
        "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
            + "<meta name=\"robots\" content=\"noindex\">"
            + "<title>"
            + title
            + " · Ecomm</title><style>"
            + "body{margin:0;font-family:system-ui,sans-serif;background:#f4faf9;color:#0b2523;"
            + "line-height:1.5}"
            + "main{max-width:32rem;margin:4rem auto;padding:2rem;background:#fff;"
            + "border:1px solid #d3e5e2;border-radius:12px}"
            + "h1{font-size:1.5rem;margin-top:0}a{color:#08625e}"
            + "button{font:inherit;padding:.6rem 1.2rem;border:0;border-radius:8px;"
            + "background:#0b7a75;color:#fff;cursor:pointer}"
            + "button:hover{background:#08625e}"
            + "button:focus-visible,a:focus-visible{outline:3px solid #0b2523;outline-offset:2px}"
            + "</style></head><body><main><h1>"
            + title
            + "</h1>"
            + body
            + "<p>Ecomm</p></main></body></html>";
    return ResponseEntity.status(status)
        .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
        .body(html);
  }
}
