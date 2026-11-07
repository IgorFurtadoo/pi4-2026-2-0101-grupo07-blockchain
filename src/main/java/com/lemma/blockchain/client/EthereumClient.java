package com.lemma.blockchain.client;

import java.math.BigInteger;

/**
 * Chamadas ao Provedor RPC da Sepolia (P8). É a única interface do projeto
 * (seção 4.3 do desenho): os testes usam uma implementação falsa, sem rede.
 * Os passos 6 e 7 acrescentam nonce, gás, envio e recibo.
 */
public interface EthereumClient {

    /** CT8 rpcGetBalance: saldo do endereço no bloco "latest", em wei. */
    BigInteger getBalance(String address) throws RpcException;
}
