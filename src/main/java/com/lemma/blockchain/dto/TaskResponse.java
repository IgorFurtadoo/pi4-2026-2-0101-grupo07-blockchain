package com.lemma.blockchain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lemma.blockchain.model.FailureReason;
import com.lemma.blockchain.model.Task;
import com.lemma.blockchain.model.TaskStatus;

import java.time.temporal.ChronoUnit;

/**
 * Resposta 200 do CT14 getTask e corpo do webhook CT15 notifyTaskCompleted (os dois
 * contratos têm os mesmos campos). transactionHash e failureReason são opcionais:
 * quando nulos, saem omitidos do JSON. updatedAt em ISO 8601 UTC, em segundos
 * (ex.: 2026-10-20T14:36:10Z).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskResponse(String taskId, TaskStatus status, String transactionHash,
                           FailureReason failureReason, String updatedAt) {

    /** Monta a resposta a partir da task; a transação assinada nunca sai. */
    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getTaskId(),
                task.getStatus(),
                task.getTransactionHash(),
                task.getFailureReason(),
                task.getUpdatedAt().truncatedTo(ChronoUnit.SECONDS).toString());
    }
}
