package com.lemma.blockchain.dto;

/** Resposta 201 do CT4 createWallet. Endereço no formato com checksum EIP-55. */
public record CreateWalletResponse(String ethereumAddress) {
}
