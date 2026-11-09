package com.lemma.blockchain.client;

import java.math.BigInteger;

/**
 * Chamadas ao Provedor RPC da Sepolia (P8). É a única interface do projeto
 * (seção 4.3 do desenho): os testes usam uma implementação falsa, sem rede.
 * O passo 7 acrescenta o recibo.
 *
 * Todos os métodos lançam {@link RpcUnavailableException} quando não há resposta
 * utilizável e {@link RpcRejectedException} quando o nó devolve erro JSON-RPC.
 */
public interface EthereumClient {

    /** CT8 rpcGetBalance: saldo do endereço no bloco "latest", em wei. */
    BigInteger getBalance(String address) throws RpcException;

    /** CT21 rpcGetTransactionCount: nonce do endereço no bloco "pending". */
    BigInteger getTransactionCount(String address) throws RpcException;

    /** CT22 rpcGetGasPrice: preço do gás, em wei. */
    BigInteger getGasPrice() throws RpcException;

    /** CT23 rpcSendRawTransaction: envia a transação assinada (hex "0x...") e devolve o hash informado pelo nó. */
    String sendRawTransaction(String signedTransactionHex) throws RpcException;
}
