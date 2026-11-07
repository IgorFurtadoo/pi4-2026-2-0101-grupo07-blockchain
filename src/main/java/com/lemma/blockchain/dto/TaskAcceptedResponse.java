package com.lemma.blockchain.dto;

import com.lemma.blockchain.model.TaskStatus;

/** Resposta 202 do CT10: a task foi gravada e ainda está PENDING. */
public record TaskAcceptedResponse(String taskId, TaskStatus status) {
}
