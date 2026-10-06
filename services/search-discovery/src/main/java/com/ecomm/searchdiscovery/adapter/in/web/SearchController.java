package com.ecomm.searchdiscovery.adapter.in.web;

import com.ecomm.searchdiscovery.application.port.in.SearchUseCase;
import com.ecomm.searchdiscovery.domain.InvalidSearchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Anyone may search the Catalog; see {@link SearchParameters} for what a search can ask. */
@RestController
class SearchController {

  private final SearchUseCase search;

  SearchController(SearchUseCase search) {
    this.search = search;
  }

  @GetMapping("/search")
  SearchResponse search(@RequestParam MultiValueMap<String, String> params) {
    var request = SearchParameters.read(params);
    return SearchResponse.of(request, search.search(request));
  }

  @ExceptionHandler(InvalidSearchException.class)
  ProblemDetail invalid(InvalidSearchException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }
}
