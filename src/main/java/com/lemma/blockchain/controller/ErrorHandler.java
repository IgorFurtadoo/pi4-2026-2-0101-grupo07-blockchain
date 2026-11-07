package com.lemma.blockchain.controller;

import com.fasterxml.jackson.core.JacksonException;
import com.lemma.blockchain.dto.ErrorResponse;
import com.lemma.blockchain.exception.ApiException;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler central de erros, registrado no Main.
 * ApiException vira o status e o código dela; erro do Jackson ao ler o corpo vira
 * 400 REQUEST_INVALID; qualquer outra exceção vira 500 INTERNAL_ERROR sem expor
 * detalhe interno ao chamador. Rota inexistente (404) e método errado (405) ficam
 * com a resposta padrão do Javalin, que tem handler próprio para HttpResponseException.
 */
public final class ErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);

    private ErrorHandler() {
    }

    public static void handleApiException(ApiException e, Context ctx) {
        ctx.status(e.getStatus()).json(new ErrorResponse(e.getCode(), e.getMessage()));
    }

    /** Corpo ausente, JSON malformado ou campo com tipo errado (o Javalin repassa o erro do Jackson). */
    public static void handleInvalidBody(JacksonException e, Context ctx) {
        // Não loga a mensagem do Jackson: ela pode trazer trechos do corpo.
        ctx.status(400).json(new ErrorResponse("REQUEST_INVALID", "Corpo da requisição inválido."));
    }

    public static void handleUnexpected(Exception e, Context ctx) {
        // Loga só método e caminho; nunca o corpo da requisição.
        log.error("Erro inesperado em {} {}", ctx.method(), ctx.path(), e);
        ctx.status(500).json(new ErrorResponse("INTERNAL_ERROR", "Erro interno."));
    }
}
