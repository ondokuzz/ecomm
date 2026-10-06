package com.ecomm.reviewsratings.application.port.out;

import com.ecomm.reviewsratings.domain.ProductVariants;
import java.util.Optional;

/** Reviews' copy of each Product's Variants, as Catalog last published them. */
public interface ProductStore {

  Optional<ProductVariants> find(String sku);

  void save(ProductVariants product);
}
