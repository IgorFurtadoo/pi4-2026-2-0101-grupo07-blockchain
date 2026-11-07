package com.lemma.blockchain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Corpo do CT10 requestTransactionSubmission. amountEth chega como número JSON e é
 * lido em BigDecimal exato (sem passar por double). Campo ausente fica nulo e o
 * TaskService responde com o código do próprio campo. Campo extra é ignorado.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateTaskRequest(String sourceAddress, String destinationAddress, BigDecimal amountEth) {
}
