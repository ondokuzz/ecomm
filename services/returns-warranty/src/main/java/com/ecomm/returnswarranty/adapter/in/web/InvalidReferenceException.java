package com.ecomm.returnswarranty.adapter.in.web;

/** RMAs were looked for by something that isn't an RMA reference. */
class InvalidReferenceException extends InvalidRequestException {

  InvalidReferenceException() {
    super("reference", "must be an RMA reference: 8 hex digits, such as RMA-3F2A9C1B");
  }
}
