package com.lemma.blockchain.client;

/**
 * O Provedor RPC não deu resposta utilizável: conexão recusada, tempo esgotado,
 * HTTP 429/5xx ou corpo ilegível. Não se sabe se o pedido chegou ao nó.
 */
public class RpcUnavailableException extends RpcException {

    public RpcUnavailableException(String message) {
        super(message);
    }
}
