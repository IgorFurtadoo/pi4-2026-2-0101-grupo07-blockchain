package com.lemma.blockchain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lemma.blockchain.model.FailureReason;
import com.lemma.blockchain.model.TaskStatus;

/**
 * Resposta 200 do CT14 getTask. transactionHash e failureReason são opcionais no
 * contrato: quando nulos, saem omitidos do JSON. updatedAt em ISO 8601 UTC, em
 * segundos (ex.: 2026-10-20T14:36:10Z).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskResponse(String taskId, TaskStatus status, String transactionHash,
                           FailureReason failureReason, String updatedAt) {
}
