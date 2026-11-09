package com.lemma.blockchain.client;

/**
 * Falha ao falar com o Provedor RPC. Não carrega status HTTP: quem decide a
 * resposta é o service. Há duas subclasses, porque o worker trata cada uma de
 * um jeito (seção 7.3 do desenho):
 * <ul>
 *   <li>{@link RpcUnavailableException}: não houve resposta utilizável (conexão,
 *       tempo esgotado, HTTP 429/5xx, resposta ilegível);</li>
 *   <li>{@link RpcRejectedException}: o nó respondeu com o objeto "error" do JSON-RPC.</li>
 * </ul>
 *
 * É verificada (extends Exception) para o compilador obrigar quem chama a tratar.
 * A mensagem nunca contém a URL do RPC, que pode carregar o token do provedor.
 */
public class RpcException extends Exception {

    public RpcException(String message) {
        super(message);
    }
}
