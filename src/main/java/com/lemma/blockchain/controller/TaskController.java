package com.lemma.blockchain.controller;

import com.lemma.blockchain.service.TaskService;
import io.javalin.http.Context;

/** Rota de consulta de task. Sem regra de negócio: chama o service e devolve o DTO. */
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /** CT14: GET /tasks/{taskId} → 200 { taskId, status, transactionHash?, failureReason?, updatedAt }. */
    public void get(Context ctx) {
        ctx.json(taskService.getTask(ctx.pathParam("taskId")));
    }
}
