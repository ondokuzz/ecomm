package com.ecomm.returnswarranty.application.port.out;

import com.ecomm.returnswarranty.application.port.in.Page;
import com.ecomm.returnswarranty.application.port.in.RmaFilter;
import com.ecomm.returnswarranty.domain.Rma;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** RMAs and their decision histories. */
public interface RmaRepository {

  void insert(Rma rma);

  /** Keeps its new status and version, and appends the decisions it has that weren't kept yet. */
  void update(Rma rma);

  Optional<Rma> find(UUID id);

  /** The RMA, locked until the transaction ends. */
  Optional<Rma> lock(UUID id);

  /** Every RMA for the Order. */
  List<Rma> ofOrder(String orderId);

  /** A page of the RMAs that match, oldest first. */
  Page<Rma> find(RmaFilter filter, int page, int size);
}
