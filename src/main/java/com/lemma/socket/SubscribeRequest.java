package com.lemma.socket;

/**
 * Primeira mensagem do cliente (Core API): pede para receber os avisos de task
 * concluída, provando que conhece a SERVICE_KEY (CT27). Arquivo idêntico no Core API.
 */
public class SubscribeRequest extends Message {

    private static final long serialVersionUID = 1L;

    private final String serviceKey;

    public SubscribeRequest(String serviceKey) {
        this.serviceKey = serviceKey;
    }

    public String getServiceKey() {
        return serviceKey;
    }

    /** Nunca imprime a chave. */
    @Override
    public String toString() {
        return "SubscribeRequest[serviceKey=***]";
    }
}
