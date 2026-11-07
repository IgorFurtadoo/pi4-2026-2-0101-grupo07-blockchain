package com.lemma.blockchain.service;

import com.lemma.blockchain.client.EthereumClient;
import com.lemma.blockchain.client.RpcException;
import com.lemma.blockchain.crypto.KeyCipher;
import com.lemma.blockchain.dto.BalanceResponse;
import com.lemma.blockchain.exception.ApiException;
import com.lemma.blockchain.model.Wallet;
import com.lemma.blockchain.repository.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Regras das carteiras custodiais (CT4, CT5, CT7).
 */
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    private static final int PRIVATE_KEY_BYTES = 32;

    /** "0x" + 40 hex em qualquer caixa. O checksum de caixa mista não é conferido (seção 6). */
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("^0x[0-9a-fA-F]{40}$");

    /** O AppConfig só aceita o chainId da Sepolia, então a rede é sempre esta. */
    private static final String NETWORK = "SEPOLIA";

    private final WalletRepository walletRepository;
    private final KeyCipher keyCipher;
    private final EthereumClient ethereumClient;

    public WalletService(WalletRepository walletRepository, KeyCipher keyCipher, EthereumClient ethereumClient) {
        this.walletRepository = walletRepository;
        this.keyCipher = keyCipher;
        this.ethereumClient = ethereumClient;
    }

    /**
     * CT4: gera um par de chaves, grava a chave privada cifrada (ADR 0003) e
     * devolve só o endereço, no formato com checksum EIP-55.
     * A chave privada nunca sai deste método.
     */
    public String createWallet() {
        ECKeyPair keyPair;
        try {
            keyPair = Keys.createEcKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("falha ao gerar o par de chaves");
        }

        // Forma canônica no banco: "0x" + 40 hex em minúsculo. Também é o AAD da cifra.
        String address = toCanonicalAddress(Keys.getAddress(keyPair));

        byte[] privateKey = Numeric.toBytesPadded(keyPair.getPrivateKey(), PRIVATE_KEY_BYTES);
        String encryptedPrivateKey;
        try {
            encryptedPrivateKey = keyCipher.encrypt(privateKey, address);
        } finally {
            Arrays.fill(privateKey, (byte) 0); // não deixa a chave em claro na memória
        }

        walletRepository.save(new Wallet(address, encryptedPrivateKey, Instant.now()));
        return Keys.toChecksumAddress(address);
    }

    /**
     * CT7: saldo de uma carteira custodial, consultado no RPC (CT8) e convertido
     * de wei para ETH exato. Ordem: valida o formato, confere a carteira no banco
     * e só então chama a rede.
     */
    public BalanceResponse getBalance(String ethereumAddress) {
        if (ethereumAddress == null || !ADDRESS_PATTERN.matcher(ethereumAddress).matches()) {
            throw new ApiException(400, "ADDRESS_INVALID", "Endereço Ethereum com formato inválido.");
        }

        String address = ethereumAddress.toLowerCase(Locale.ROOT);
        if (walletRepository.findByAddress(address).isEmpty()) {
            throw new ApiException(404, "WALLET_NOT_FOUND", "Carteira não encontrada.");
        }

        BigInteger balanceWei;
        try {
            balanceWei = ethereumClient.getBalance(address);
        } catch (RpcException e) {
            // A mensagem do RpcException nunca contém a URL do RPC.
            log.warn("Falha no RPC ao consultar saldo: {}", e.getMessage());
            throw new ApiException(502, "RPC_UNAVAILABLE", "Provedor RPC indisponível.");
        }

        BigDecimal balanceEth = Convert.fromWei(new BigDecimal(balanceWei), Convert.Unit.ETHER);
        return new BalanceResponse(Keys.toChecksumAddress(address), balanceEth, NETWORK);
    }

    /** Keys.getAddress devolve 40 hex sem "0x". */
    static String toCanonicalAddress(String addressWithoutPrefix) {
        return "0x" + addressWithoutPrefix.toLowerCase(Locale.ROOT);
    }
}
