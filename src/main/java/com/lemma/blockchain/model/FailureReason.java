package com.lemma.blockchain.model;

/** Motivo de uma task FAILED (CT11); nulo nos demais estados. */
public enum FailureReason {
    REJECTED_BY_NETWORK,
    TRANSACTION_REVERTED,
    INTERNAL_ERROR
}
