package com.lemma.blockchain.client;

import okhttp3.OkHttpClient;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * EthereumClient com Web3j sobre HTTP (seção 8 do desenho).
 *
 * A URL do RPC fica só dentro do HttpService. As mensagens de erro citam apenas
 * o tipo da falha, e a exceção original não é encadeada: a mensagem dela pode
 * trazer host ou caminho da URL, onde provedores costumam pôr o token.
 */
public class Web3jEthereumClient implements EthereumClient {

    private static final Pattern TRANSACTION_HASH = Pattern.compile("^0x[0-9a-fA-F]{64}$");

    private final Web3j web3j;

    public Web3jEthereumClient(String rpcUrl, int timeoutSeconds) {
        // Timeout de conexão e de leitura (RPC_TIMEOUT_SECONDS, seção 3.2 do desenho).
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build();
        this.web3j = Web3j.build(new HttpService(rpcUrl, httpClient));
    }

    @Override
    public BigInteger getBalance(String address) throws RpcException {
        EthGetBalance response = send(web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST));
        try {
            return response.getBalance();
        } catch (RuntimeException e) {
            // "result" ausente ou fora do formato hexadecimal.
            throw new RpcUnavailableException("RPC respondeu com saldo ilegível");
        }
    }

    @Override
    public BigInteger getTransactionCount(String address) throws RpcException {
        // "pending" conta também as transações da conta que ainda estão no mempool (CT21).
        EthGetTransactionCount response = send(web3j.ethGetTransactionCount(address, DefaultBlockParameterName.PENDING));
        try {
            return response.getTransactionCount();
        } catch (RuntimeException e) {
            throw new RpcUnavailableException("RPC respondeu com nonce ilegível");
        }
    }

    @Override
    public BigInteger getGasPrice() throws RpcException {
        EthGasPrice response = send(web3j.ethGasPrice());
        try {
            return response.getGasPrice();
        } catch (RuntimeException e) {
            throw new RpcUnavailableException("RPC respondeu com preço do gás ilegível");
        }
    }

    @Override
    public String sendRawTransaction(String signedTransactionHex) throws RpcException {
        String hash = send(web3j.ethSendRawTransaction(signedTransactionHex)).getTransactionHash();
        if (hash == null || !TRANSACTION_HASH.matcher(hash).matches()) {
            throw new RpcUnavailableException("RPC respondeu com hash ilegível");
        }
        return hash;
    }

    /**
     * Lê o status do recibo e valida à mão. Não usa TransactionReceipt.isStatusOK():
     * no Web3j 6.1.0 ele devolve true quando o status é nulo, e um recibo sem status
     * não pode virar CONFIRMED.
     */
    @Override
    public Optional<ReceiptInfo> getTransactionReceipt(String txHash) throws RpcException {
        EthGetTransactionReceipt response = send(web3j.ethGetTransactionReceipt(txHash));
        Optional<TransactionReceipt> receipt = response.getTransactionReceipt();
        if (receipt.isEmpty()) {
            return Optional.empty(); // ainda não incluída em bloco
        }

        String status = receipt.get().getStatus();
        boolean success;
        if ("0x1".equals(status)) {
            success = true;
        } else if ("0x0".equals(status)) {
            success = false;
        } else {
            throw new RpcUnavailableException("RPC respondeu com recibo ilegível (status)");
        }

        BigInteger blockNumber;
        try {
            blockNumber = receipt.get().getBlockNumber();
        } catch (RuntimeException e) {
            throw new RpcUnavailableException("RPC respondeu com recibo ilegível (bloco)");
        }
        if (blockNumber == null) {
            throw new RpcUnavailableException("RPC respondeu com recibo ilegível (bloco)");
        }
        return Optional.of(new ReceiptInfo(success, blockNumber));
    }

    @Override
    public boolean isTransactionKnown(String txHash) throws RpcException {
        // result nulo: a rede não conhece o hash (CT24).
        EthTransaction response = send(web3j.ethGetTransactionByHash(txHash));
        return response.getTransaction().isPresent();
    }

    /**
     * Faz a chamada e separa os dois tipos de falha:
     * sem resposta utilizável vira {@link RpcUnavailableException};
     * objeto "error" do JSON-RPC vira {@link RpcRejectedException}.
     */
    private static <T extends Response<?>> T send(Request<?, T> request) throws RpcException {
        T response;
        try {
            response = request.send();
        } catch (IOException | RuntimeException e) {
            // IOException: conexão recusada, tempo esgotado.
            // RuntimeException: HTTP 429/5xx (ClientConnectionException) ou corpo ilegível.
            throw new RpcUnavailableException("falha na chamada ao RPC: " + e.getClass().getSimpleName());
        }

        if (response.hasError()) {
            Response.Error error = response.getError();
            throw new RpcRejectedException(error.getCode(), error.getMessage());
        }
        return response;
    }
}
