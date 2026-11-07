package com.lemma.blockchain.config;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppConfigTest {

    // Valores só de teste. A chave é 32 bytes zerados, nunca usada fora daqui.
    private static final String TEST_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private static Map<String, String> validEnv() {
        Map<String, String> env = new HashMap<>();
        env.put("SERVER_PORT", "8081");
        env.put("DB_URL", "jdbc:mysql://blockchain-db:3306/blockchain");
        env.put("DB_USER", "app");
        env.put("DB_PASSWORD", "test-db-password");
        env.put("RPC_URL", "https://ethereum-sepolia-rpc.publicnode.com");
        env.put("CHAIN_ID", "11155111");
        env.put("SERVICE_KEY", "test-service-key");
        env.put("WALLET_ENCRYPTION_KEY", TEST_KEY);
        env.put("CORE_API_WEBHOOK_URL", "http://core-api:8080/webhooks/tasks");
        env.put("WORKER_INTERVAL_SECONDS", "5");
        env.put("RPC_TIMEOUT_SECONDS", "10");
        return env;
    }

    private static String failureMessage(Map<String, String> env) {
        return assertThrows(IllegalStateException.class, () -> AppConfig.fromEnv(env)).getMessage();
    }

    @Test
    void loadsValidConfiguration() {
        AppConfig config = AppConfig.fromEnv(validEnv());

        assertEquals(8081, config.serverPort());
        assertEquals(11155111L, config.chainId());
        assertEquals(32, config.walletEncryptionKey().length);
        assertEquals(5, config.workerIntervalSeconds());
    }

    @Test
    void failsWhenVariableIsMissing() {
        Map<String, String> env = validEnv();
        env.remove("SERVICE_KEY");

        assertTrue(failureMessage(env).contains("variável obrigatória ausente: SERVICE_KEY"));
    }

    @Test
    void failsWhenVariableIsBlank() {
        Map<String, String> env = validEnv();
        env.put("RPC_URL", "   ");

        assertTrue(failureMessage(env).contains("RPC_URL"));
    }

    @Test
    void reportsAllMissingVariablesAtOnce() {
        Map<String, String> env = validEnv();
        env.remove("DB_URL");
        env.remove("CORE_API_WEBHOOK_URL");

        String message = failureMessage(env);
        assertTrue(message.contains("DB_URL"));
        assertTrue(message.contains("CORE_API_WEBHOOK_URL"));
    }

    @Test
    void rejectsChainIdOtherThanSepolia() {
        Map<String, String> env = validEnv();
        env.put("CHAIN_ID", "1");

        assertTrue(failureMessage(env).contains("CHAIN_ID"));
    }

    @Test
    void rejectsNonPositiveNumbers() {
        Map<String, String> env = validEnv();
        env.put("WORKER_INTERVAL_SECONDS", "0");
        env.put("SERVER_PORT", "abc");

        String message = failureMessage(env);
        assertTrue(message.contains("WORKER_INTERVAL_SECONDS"));
        assertTrue(message.contains("SERVER_PORT"));
    }

    @Test
    void rejectsEncryptionKeyWithWrongSize() {
        Map<String, String> env = validEnv();
        env.put("WALLET_ENCRYPTION_KEY", Base64.getEncoder().encodeToString(new byte[16]));

        assertTrue(failureMessage(env).contains("WALLET_ENCRYPTION_KEY"));
    }

    @Test
    void rejectsPlaceholderKeyFromEnvExample() {
        String placeholder = "GERAR_COM_openssl_rand_-base64_32";
        Map<String, String> env = validEnv();
        env.put("WALLET_ENCRYPTION_KEY", placeholder);

        String message = failureMessage(env);
        assertTrue(message.contains("WALLET_ENCRYPTION_KEY"));
        assertTrue(message.contains("openssl rand -base64 32"));
        assertFalse(message.contains(placeholder));
    }

    @Test
    void errorMessageAndToStringDoNotExposeSecrets() {
        Map<String, String> env = validEnv();
        env.remove("DB_URL");

        String message = failureMessage(env);
        assertFalse(message.contains("test-db-password"));
        assertFalse(message.contains("test-service-key"));

        String text = AppConfig.fromEnv(validEnv()).toString();
        assertFalse(text.contains("test-db-password"));
        assertFalse(text.contains("test-service-key"));
        assertFalse(text.contains(TEST_KEY));
    }

    @Test
    void toStringDoesNotExposeUrls() {
        Map<String, String> env = validEnv();
        env.put("RPC_URL", "https://sepolia.provedor-exemplo.io/v3/test-api-key");
        env.put("DB_URL", "jdbc:mysql://blockchain-db:3306/blockchain?password=test-jdbc-password");

        String text = AppConfig.fromEnv(env).toString();
        assertFalse(text.contains("provedor-exemplo"));
        assertFalse(text.contains("test-api-key"));
        assertFalse(text.contains("jdbc:mysql"));
        assertFalse(text.contains("test-jdbc-password"));
    }
}
