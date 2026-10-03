package com.ecomm.promotions.adapter.in.web;

import com.ecomm.promotions.application.port.in.ManageCampaignsUseCase;
import com.ecomm.promotions.domain.CampaignNotFoundException;
import com.ecomm.promotions.domain.CatalogUnavailableException;
import com.ecomm.promotions.domain.InvalidPromotionException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/** Only Staff see or change Campaigns. An ID that isn't a UUID names no Campaign. */
@RestController
@RequestMapping("/campaigns")
@PreAuthorize("hasRole('STAFF')")
class CampaignController {

  private static final Logger log = LoggerFactory.getLogger(CampaignController.class);

  private final ManageCampaignsUseCase campaigns;

  CampaignController(ManageCampaignsUseCase campaigns) {
    this.campaigns = campaigns;
  }

  /** Every Campaign by priority, each with its state now. */
  @GetMapping
  List<CampaignResponse> campaigns() {
    return campaigns.campaigns().stream().map(CampaignResponse::of).toList();
  }

  @GetMapping("/{id}")
  CampaignResponse campaign(@PathVariable String id) {
    return campaigns
        .campaign(uuid(id))
        .map(CampaignResponse::of)
        .orElseThrow(() -> new CampaignNotFoundException(id));
  }

  /** 201 with the Campaign, under a new ID, and its URL in {@code Location}. */
  @PostMapping
  ResponseEntity<CampaignResponse> create(@RequestBody CampaignRequest request) {
    var view = campaigns.create(request.toCampaign(UUID.randomUUID()));
    var id = view.campaign().id();
    log.info("Created Campaign {} ({})", id, view.campaign().name());
    var location =
        UriComponentsBuilder.fromPath("/campaigns/{id}").buildAndExpand(id).encode().toUri();
    return ResponseEntity.created(location).body(CampaignResponse.of(view));
  }

  /** Replaces every field of the Campaign but its ID. */
  @PutMapping("/{id}")
  CampaignResponse update(@PathVariable String id, @RequestBody CampaignRequest request) {
    var view = campaigns.update(request.toCampaign(uuid(id)));
    log.info("Updated Campaign {}", id);
    return CampaignResponse.of(view);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable String id) {
    campaigns.delete(uuid(id));
    log.info("Deleted Campaign {}", id);
  }

  private static UUID uuid(String id) {
    try {
      return UUID.fromString(id);
    } catch (IllegalArgumentException e) {
      throw new CampaignNotFoundException(id);
    }
  }

  @ExceptionHandler(CampaignNotFoundException.class)
  ProblemDetail notFound(CampaignNotFoundException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(InvalidPromotionException.class)
  ProblemDetail invalid(InvalidPromotionException e) {
    return Problems.invalid(e);
  }

  /** 503: whether the Campaign's Categories and currencies are Catalog's can't be known now. */
  @ExceptionHandler(CatalogUnavailableException.class)
  ProblemDetail catalogUnavailable(CatalogUnavailableException e) {
    log.warn("Campaign not saved: {}", e.getMessage());
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.SERVICE_UNAVAILABLE,
        "Catalog can't be reached to check the Campaign's Categories and currencies. Try again"
            + " shortly.");
  }
}
