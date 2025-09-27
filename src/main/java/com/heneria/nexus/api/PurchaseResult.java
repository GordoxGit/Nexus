package com.heneria.nexus.api;

/**
 * Possible outcomes for a shop purchase.
 */
public enum PurchaseResult {
    SUCCESS,
    INSUFFICIENT_FUNDS,
    ALREADY_OWNED,
    ERROR
}
