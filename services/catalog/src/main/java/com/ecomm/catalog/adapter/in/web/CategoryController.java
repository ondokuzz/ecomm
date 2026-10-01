package com.ecomm.catalog.adapter.in.web;

import com.ecomm.catalog.application.port.in.BrowseCatalogUseCase;
import com.ecomm.catalog.application.port.in.ManageCategoriesUseCase;
import com.ecomm.catalog.domain.CategoryAlreadyExistsException;
import com.ecomm.catalog.domain.CategoryInUseException;
import com.ecomm.catalog.domain.CategoryNotFoundException;
import com.ecomm.catalog.domain.InvalidCategoryException;
import java.util.List;
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

/** Anyone may read Categories (see {@code public-read-paths}); only Staff may change them. */
@RestController
@RequestMapping("/categories")
class CategoryController {

  private final BrowseCatalogUseCase browse;
  private final ManageCategoriesUseCase manage;

  CategoryController(BrowseCatalogUseCase browse, ManageCategoriesUseCase manage) {
    this.browse = browse;
    this.manage = manage;
  }

  @GetMapping
  List<CategoryResponse> categories() {
    return browse.categories().stream().map(CategoryResponse::of).toList();
  }

  @GetMapping("/{slug}")
  CategoryResponse category(@PathVariable String slug) {
    return browse
        .category(slug)
        .map(CategoryResponse::of)
        .orElseThrow(() -> new CategoryNotFoundException(slug));
  }

  @PostMapping
  @PreAuthorize("hasRole('STAFF')")
  ResponseEntity<CategoryResponse> create(@RequestBody CategoryRequest request) {
    var category = manage.create(request.toCategory());
    var location =
        UriComponentsBuilder.fromPath("/categories/{slug}")
            .buildAndExpand(category.slug())
            .encode()
            .toUri();
    return ResponseEntity.created(location).body(category(category.slug()));
  }

  @PutMapping("/{slug}")
  @PreAuthorize("hasRole('STAFF')")
  CategoryResponse update(@PathVariable String slug, @RequestBody CategoryRequest request) {
    manage.update(request.toCategoryWithSlug(slug));
    return category(slug);
  }

  @DeleteMapping("/{slug}")
  @PreAuthorize("hasRole('STAFF')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable String slug) {
    manage.delete(slug);
  }

  @ExceptionHandler(CategoryNotFoundException.class)
  ProblemDetail notFound(CategoryNotFoundException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler({CategoryAlreadyExistsException.class, CategoryInUseException.class})
  ProblemDetail conflict(RuntimeException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
  }

  /** Names the offending field in {@code errors}, the shape a Product's violations take. */
  @ExceptionHandler(InvalidCategoryException.class)
  ProblemDetail invalid(InvalidCategoryException e) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    problem.setProperty("errors", List.of(e.violation()));
    return problem;
  }
}
