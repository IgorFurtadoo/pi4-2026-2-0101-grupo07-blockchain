package com.lemma.blockchain.service;

import com.lemma.blockchain.client.EthereumClient;
import com.lemma.blockchain.client.RpcException;
import com.lemma.blockchain.client.RpcRejectedException;
import com.lemma.blockchain.client.RpcUnavailableException;
import com.lemma.blockchain.crypto.KeyCipher;
import com.lemma.blockchain.dto.BalanceResponse;
import com.lemma.blockchain.exception.ApiException;
import com.lemma.blockchain.model.Wallet;
import com.lemma.blockchain.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testa o WalletService sem banco e sem rede: um repository de teste guarda as
 * carteiras em memória e um EthereumClient falso devolve o saldo combinado.
 * Nenhuma asserção compara a chave privada com assertEquals, para que uma falha
 * não imprima a chave na saída.
 */
class WalletServiceTest {

    /** Endereço gravado no banco, em minúsculo (forma canônica). */
    private static final String STORED_ADDRESS = "0x71c7656ec7ab88b098defb751b7401b5f6d8976f";

    /** Repository de teste: captura o que seria gravado e lido, sem Hibernate. */
    private static class InMemoryWalletRepository extends WalletRepository {
        final List<Wallet> saved = new ArrayList<>();
        final List<String> searched = new ArrayList<>();

        InMemoryWalletRepository() {
            super(null);
        }

        @Override
        public void save(Wallet wallet) {
            saved.add(wallet);
        }

        /** Compara exatamente, como a chave primária: só acha se vier em minúsculo. */
        @Override
        public Optional<Wallet> findByAddress(String ethereumAddress) {
            searched.add(ethereumAddress);
            return saved.stream()
                    .filter(wallet -> wallet.getEthereumAddress().equals(ethereumAddress))
                    .findFirst();
        }
    }

    /** RPC falso: devolve o saldo configurado ou lança a falha configurada, e conta as chamadas. */
    private static class FakeEthereumClient implements EthereumClient {
        BigInteger balanceWei = BigInteger.ZERO;
        RpcException failure = null;
        final List<String> calls = new ArrayList<>();

        @Override
        public BigInteger getBalance(String address) throws RpcException {
            calls.add(address);
            if (failure != null) {
                throw failure;
            }
            return balanceWei;
        }

        // O WalletService não usa as chamadas do worker.
        @Override
        public BigInteger getTransactionCount(String address) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BigInteger getGasPrice() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String sendRawTransaction(String signedTransactionHex) {
            throw new UnsupportedOperationException();
        }
    }

    private final InMemoryWalletRepository repository = new InMemoryWalletRepository();
    private final KeyCipher keyCipher = new KeyCipher(randomKey());
    private final FakeEthereumClient ethereumClient = new FakeEthereumClient();
    private final WalletService service = new WalletService(repository, keyCipher, ethereumClient);

    private static byte[] randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }

    /** Grava a carteira de teste (o valor cifrado não importa para o saldo). */
    private void storeWallet() {
        repository.save(new Wallet(STORED_ADDRESS, "valor-cifrado-de-teste", Instant.now()));
    }

    private String balanceOf(String wei) {
        storeWallet();
        ethereumClient.balanceWei = new BigInteger(wei);
        return service.getBalance(STORED_ADDRESS).balanceEth().toPlainString();
    }

    // ---------- createWallet (CT4) ----------

    @Test
    void savesWalletWithCanonicalAddressAndReturnsChecksumAddress() {
        String returned = service.createWallet();

        assertEquals(1, repository.saved.size());
        Wallet wallet = repository.saved.get(0);
        String stored = wallet.getEthereumAddress();

        assertTrue(stored.matches("^0x[0-9a-f]{40}$"), "endereço gravado deve ser 0x + minúsculo");
        assertEquals(Keys.toChecksumAddress(stored), returned);
        assertEquals(stored, returned.toLowerCase(Locale.ROOT));
        assertNotNull(wallet.getCreatedAt());
    }

    @Test
    void decryptedKeyDerivesTheStoredAddress() {
        service.createWallet();
        Wallet wallet = repository.saved.get(0);

        // O AAD é exatamente o endereço lido da Wallet gravada.
        byte[] privateKey = keyCipher.decrypt(wallet.getEncryptedPrivateKey(), wallet.getEthereumAddress());
        String derived = "0x" + Keys.getAddress(ECKeyPair.create(privateKey));
        Arrays.fill(privateKey, (byte) 0);

        assertEquals(wallet.getEthereumAddress(), derived);
    }

    @Test
    void storedValueIsNotThePrivateKeyInPlainText() {
        service.createWallet();
        Wallet wallet = repository.saved.get(0);

        byte[] privateKey = keyCipher.decrypt(wallet.getEncryptedPrivateKey(), wallet.getEthereumAddress());
        String privateKeyHex = Numeric.toHexStringNoPrefix(privateKey);
        Arrays.fill(privateKey, (byte) 0);

        String stored = wallet.getEncryptedPrivateKey().toLowerCase(Locale.ROOT);
        assertFalse(stored.contains(privateKeyHex), "valor gravado não pode conter a chave em hex");
        assertFalse(stored.matches("^(0x)?[0-9a-f]{64}$"), "valor gravado não pode ser hex de 32 bytes");
    }

    @Test
    void eachWalletHasItsOwnAddress() {
        service.createWallet();
        service.createWallet();

        assertFalse(repository.saved.get(0).getEthereumAddress()
                .equals(repository.saved.get(1).getEthereumAddress()));
    }

    // ---------- getBalance (CT7): conversão wei → ETH ----------

    @Test
    void zeroWeiIsZeroEth() {
        assertEquals("0", balanceOf("0"));
    }

    @Test
    void oneWeiIsTheSmallestEthFraction() {
        assertEquals("0.000000000000000001", balanceOf("1"));
    }

    @Test
    void quarterEth() {
        assertEquals("0.25", balanceOf("250000000000000000"));
    }

    @Test
    void oneEth() {
        assertEquals("1", balanceOf("1000000000000000000"));
    }

    @Test
    void largeBalanceKeepsEveryDigit() {
        assertEquals("123456789012.34567890123456789", balanceOf("123456789012345678901234567890"));
    }

    // ---------- getBalance (CT7): erros e normalização ----------

    @Test
    void invalidAddressIsRejectedBeforeRepositoryAndClient() {
        storeWallet();
        repository.searched.clear();
        List<String> invalid = Arrays.asList(
                null,
                "",
                "0x123",                                         // curto
                "71c7656ec7ab88b098defb751b7401b5f6d8976f",      // sem 0x
                "0X71c7656ec7ab88b098defb751b7401b5f6d8976f",    // prefixo 0X
                "0x71c7656ec7ab88b098defb751b7401b5f6d8976g",    // caractere não-hex
                "0x71c7656ec7ab88b098defb751b7401b5f6d8976f00"); // longo

        for (String address : invalid) {
            ApiException e = assertThrows(ApiException.class, () -> service.getBalance(address));
            assertEquals(400, e.getStatus(), "endereço: " + address);
            assertEquals("ADDRESS_INVALID", e.getCode(), "endereço: " + address);
        }
        assertTrue(repository.searched.isEmpty(), "não deve consultar o repository");
        assertTrue(ethereumClient.calls.isEmpty(), "não deve chamar o RPC");
    }

    @Test
    void mixedCaseWithWrongChecksumIsRejectedBeforeRepositoryAndClient() {
        storeWallet();
        repository.searched.clear();
        // Exemplo dos contratos com a caixa de duas letras trocada.
        String wrongChecksum = "0x71c7656EC7ab88b098defB751B7401B5f6d8976F";

        ApiException e = assertThrows(ApiException.class, () -> service.getBalance(wrongChecksum));

        assertEquals(400, e.getStatus());
        assertEquals("ADDRESS_INVALID", e.getCode());
        assertTrue(repository.searched.isEmpty(), "não deve consultar o repository");
        assertTrue(ethereumClient.calls.isEmpty(), "não deve chamar o RPC");
    }

    @Test
    void unknownWalletIsNotFoundWithoutCallingClient() {
        ApiException e = assertThrows(ApiException.class, () -> service.getBalance(STORED_ADDRESS));

        assertEquals(404, e.getStatus());
        assertEquals("WALLET_NOT_FOUND", e.getCode());
        assertTrue(ethereumClient.calls.isEmpty(), "não deve chamar o RPC");
    }

    @Test
    void clientFailureBecomesRpcUnavailable() {
        storeWallet();
        List<RpcException> failures = List.of(
                new RpcUnavailableException("falha simulada"),
                new RpcRejectedException(-32602, "invalid argument"));

        for (RpcException failure : failures) {
            ethereumClient.failure = failure;
            ApiException e = assertThrows(ApiException.class, () -> service.getBalance(STORED_ADDRESS));

            String kind = failure.getClass().getSimpleName();
            assertEquals(502, e.getStatus(), kind);
            assertEquals("RPC_UNAVAILABLE", e.getCode(), kind);
        }
    }

    @Test
    void upperCaseInputFindsLowerCaseWalletAndAnswersWithChecksum() {
        storeWallet();
        ethereumClient.balanceWei = new BigInteger("250000000000000000");
        String upperCase = "0x" + STORED_ADDRESS.substring(2).toUpperCase(Locale.ROOT);

        BalanceResponse response = service.getBalance(upperCase);

        assertEquals(List.of(STORED_ADDRESS), repository.searched);
        assertEquals(List.of(STORED_ADDRESS), ethereumClient.calls);
        assertEquals("0x71C7656EC7ab88b098defB751B7401B5f6d8976F", response.ethereumAddress());
        assertEquals("0.25", response.balanceEth().toPlainString());
        assertEquals("SEPOLIA", response.network());
    }
}
