package com.lemma.blockchain.client;

/**
 * O nó respondeu com o objeto "error" do JSON-RPC (por exemplo, saldo
 * insuficiente ou nonce inválido no envio, CT23). Guarda o código e a mensagem
 * do nó, que podem ir ao log: não contêm a URL do RPC.
 */
public class RpcRejectedException extends RpcException {

    private final int rpcCode;
    private final String nodeMessage;

    public RpcRejectedException(int rpcCode, String nodeMessage) {
        super("RPC respondeu com erro JSON-RPC " + rpcCode + ": " + nodeMessage);
        this.rpcCode = rpcCode;
        this.nodeMessage = nodeMessage;
    }

    public int getRpcCode() {
        return rpcCode;
    }

    public String getNodeMessage() {
        return nodeMessage;
    }
}
