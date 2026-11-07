package com.lemma.blockchain.controller;

import com.lemma.blockchain.dto.CreateTaskRequest;
import com.lemma.blockchain.exception.ApiException;
import com.lemma.blockchain.service.TaskService;
import io.javalin.http.Context;

/**
 * Rota do pedido de envio. Sem regra de negócio: lê o corpo, chama o service e
 * devolve o DTO. Corpo ausente ou malformado vira 400 REQUEST_INVALID no
 * ErrorHandler; o corpo literal null é tratado aqui.
 */
public class TransactionController {

    private final TaskService taskService;

    public TransactionController(TaskService taskService) {
        this.taskService = taskService;
    }

    /** CT10: POST /transactions → 202 { taskId, status: PENDING }. */
    public void create(Context ctx) {
        // O JSON literal null faria o Javalin lançar NullPointerException (500); tratamos antes.
        if ("null".equals(ctx.body().strip())) {
            throw new ApiException(400, "REQUEST_INVALID", "Corpo da requisição inválido.");
        }
        CreateTaskRequest request = ctx.bodyAsClass(CreateTaskRequest.class);
        ctx.status(202).json(taskService.requestSubmission(
                request.sourceAddress(), request.destinationAddress(), request.amountEth()));
    }
}
