package com.lemma.blockchain.exception;

/**
 * Erro previsto nos contratos: status HTTP + código em UPPER_SNAKE_CASE.
 * O handler central (controller/ErrorHandler) converte em { "code", "message" }.
 * A mensagem pode ser exibida ao usuário: nunca coloque segredo nela.
 */
public class ApiException extends RuntimeException {

    private final int status;
    private final String code;

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
