package com.lemma.blockchain.controller;

import com.lemma.blockchain.exception.ApiException;
import io.javalin.http.Context;
import io.javalin.http.Handler;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Exige o cabeçalho X-Service-Key em todas as rotas (P2).
 * Registrado no Main como "before", antes de qualquer controller.
 */
public class ServiceKeyFilter implements Handler {

    public static final String HEADER = "X-Service-Key";

    private final byte[] expectedKey;

    public ServiceKeyFilter(String expectedKey) {
        this.expectedKey = expectedKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void handle(Context ctx) {
        if (!isValid(ctx.header(HEADER))) {
            throw new ApiException(401, "SERVICE_KEY_INVALID", "Chave de serviço ausente ou incorreta.");
        }
    }

    /**
     * Comparação em tempo constante: MessageDigest.isEqual percorre sempre a chave
     * esperada inteira (1º argumento), sem parar no primeiro byte diferente.
     */
    boolean isValid(String providedKey) {
        if (providedKey == null) {
            return false;
        }
        return MessageDigest.isEqual(expectedKey, providedKey.getBytes(StandardCharsets.UTF_8));
    }
}
