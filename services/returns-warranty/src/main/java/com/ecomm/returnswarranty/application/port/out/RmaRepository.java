package com.ecomm.returnswarranty.application.port.out;

import com.ecomm.returnswarranty.application.port.in.Page;
import com.ecomm.returnswarranty.application.port.in.RmaFilter;
import com.ecomm.returnswarranty.domain.Rma;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** RMAs and their RMA histories. */
public interface RmaRepository {

  void insert(Rma rma);

  /**
   * Keeps what changed: its status, version, Refund amount, Send-back deadline, Warehouse,
   * Inspection and resolution; and appends the history entries it has that weren't kept yet.
   */
  void update(Rma rma);

  Optional<Rma> find(UUID id);

  /** The RMA, locked until the transaction ends. */
  Optional<Rma> lock(UUID id);

  /** Every RMA for the Order, oldest first. */
  List<Rma> ofOrder(String orderId);

  /** A page of the RMAs that match: approved ones by Send-back deadline, others oldest first. */
  Page<Rma> find(RmaFilter filter, int page, int size);
}
