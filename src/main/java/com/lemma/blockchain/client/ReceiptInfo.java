package com.lemma.blockchain.client;

import java.math.BigInteger;

/**
 * O que o worker usa do recibo (CT16): se a transação foi executada com sucesso
 * (status 0x1) ou revertida (status 0x0), e o número do bloco, só para o log.
 */
public record ReceiptInfo(boolean success, BigInteger blockNumber) {
}
