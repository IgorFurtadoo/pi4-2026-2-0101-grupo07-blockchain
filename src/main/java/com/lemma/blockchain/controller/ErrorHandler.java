package com.lemma.blockchain.controller;

import com.lemma.blockchain.dto.ErrorResponse;
import com.lemma.blockchain.exception.ApiException;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler central de erros, registrado no Main.
 * ApiException vira o status e o código dela; qualquer outra exceção vira
 * 500 INTERNAL_ERROR sem expor detalhe interno ao chamador.
 */
public final class ErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);

    private ErrorHandler() {
    }

    public static void handleApiException(ApiException e, Context ctx) {
        ctx.status(e.getStatus()).json(new ErrorResponse(e.getCode(), e.getMessage()));
    }

    public static void handleUnexpected(Exception e, Context ctx) {
        // Loga só método e caminho; nunca o corpo da requisição.
        log.error("Erro inesperado em {} {}", ctx.method(), ctx.path(), e);
        ctx.status(500).json(new ErrorResponse("INTERNAL_ERROR", "Erro interno."));
    }
}
