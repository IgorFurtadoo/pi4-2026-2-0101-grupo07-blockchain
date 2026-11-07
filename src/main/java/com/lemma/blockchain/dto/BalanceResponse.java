package com.lemma.blockchain.dto;

import java.math.BigDecimal;

/**
 * Resposta 200 do CT7 getWalletBalance. Endereço com checksum EIP-55, saldo em ETH
 * exato (sem double) e a rede, sempre SEPOLIA.
 */
public record BalanceResponse(String ethereumAddress, BigDecimal balanceEth, String network) {
}
