package com.heneria.nexus.api;

/**
 * Result of a transfer operation.
 *
 * @param fromBalance resulting balance of the source account, in minor units
 * @param toBalance resulting balance of the destination account, in minor units
 */
public record EconomyTransferResult(long fromBalance, long toBalance) {
}
