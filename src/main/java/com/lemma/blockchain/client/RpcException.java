package com.lemma.blockchain.client;

/**
 * Falha ao falar com o Provedor RPC: conexão, tempo esgotado, HTTP 429/5xx ou
 * erro JSON-RPC. Não carrega status HTTP: quem decide a resposta é o service.
 *
 * É verificada (extends Exception) para o compilador obrigar quem chama a tratar.
 * A mensagem nunca contém a URL do RPC, que pode carregar o token do provedor.
 */
public class RpcException extends Exception {

    public RpcException(String message) {
        super(message);
    }
}
