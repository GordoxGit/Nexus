package com.heneria.nexus.service.api;

/**
 * Result of a transfer operation.
 */
public record EconomyTransferResult(long fromBalance, long toBalance) {
}
