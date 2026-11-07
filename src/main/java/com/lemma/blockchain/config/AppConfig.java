package com.lemma.blockchain.config;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Configuração do serviço, lida das variáveis de ambiente (seção 3.2 do desenho).
 *
 * Todas as variáveis são obrigatórias. Se faltar alguma ou o valor for inválido,
 * {@link #fromEnv(Map)} lança {@link IllegalStateException} listando todos os
 * problemas de uma vez. A mensagem cita só o nome da variável, nunca o valor,
 * para não vazar segredo em log.
 */
public record AppConfig(
        int serverPort,
        String dbUrl,
        String dbUser,
        String dbPassword,
        String rpcUrl,
        long chainId,
        String serviceKey,
        byte[] walletEncryptionKey,
        String coreApiWebhookUrl,
        int workerIntervalSeconds,
        int rpcTimeoutSeconds) {

    /** chainId da Sepolia (P8). Qualquer outro valor é recusado, para nunca apontar para a mainnet. */
    public static final long SEPOLIA_CHAIN_ID = 11155111L;

    /** AES-256 precisa de chave de 32 bytes (CT5, seção 9 do desenho). */
    private static final int ENCRYPTION_KEY_BYTES = 32;

    public static AppConfig fromEnv(Map<String, String> env) {
        Reader reader = new Reader(env);

        int serverPort = reader.positiveInt("SERVER_PORT");
        String dbUrl = reader.text("DB_URL");
        String dbUser = reader.text("DB_USER");
        String dbPassword = reader.text("DB_PASSWORD");
        String rpcUrl = reader.text("RPC_URL");
        long chainId = reader.chainId("CHAIN_ID");
        String serviceKey = reader.text("SERVICE_KEY");
        byte[] walletEncryptionKey = reader.encryptionKey("WALLET_ENCRYPTION_KEY");
        String coreApiWebhookUrl = reader.text("CORE_API_WEBHOOK_URL");
        int workerIntervalSeconds = reader.positiveInt("WORKER_INTERVAL_SECONDS");
        int rpcTimeoutSeconds = reader.positiveInt("RPC_TIMEOUT_SECONDS");

        if (!reader.problems.isEmpty()) {
            throw new IllegalStateException(String.join("; ", reader.problems));
        }

        return new AppConfig(serverPort, dbUrl, dbUser, dbPassword, rpcUrl, chainId,
                serviceKey, walletEncryptionKey, coreApiWebhookUrl,
                workerIntervalSeconds, rpcTimeoutSeconds);
    }

    /**
     * Não imprime segredos (senha do banco, chave de serviço, chave de cifragem).
     * Também omite as URLs do RPC e do banco: provedores pagos costumam levar a chave
     * de API na URL, e uma URL JDBC pode carregar credencial na query string.
     */
    @Override
    public String toString() {
        return "AppConfig[serverPort=" + serverPort
                + ", dbUser=" + dbUser
                + ", chainId=" + chainId
                + ", coreApiWebhookUrl=" + coreApiWebhookUrl
                + ", workerIntervalSeconds=" + workerIntervalSeconds
                + ", rpcTimeoutSeconds=" + rpcTimeoutSeconds + "]";
    }

    /** Lê cada variável e acumula os problemas encontrados, sem parar no primeiro. */
    private static final class Reader {
        private final Map<String, String> env;
        private final List<String> problems = new ArrayList<>();

        Reader(Map<String, String> env) {
            this.env = env;
        }

        String text(String name) {
            String value = env.get(name);
            if (value == null || value.isBlank()) {
                problems.add("variável obrigatória ausente: " + name);
                return null;
            }
            return value.trim();
        }

        int positiveInt(String name) {
            String value = text(name);
            if (value == null) {
                return 0;
            }
            try {
                int number = Integer.parseInt(value);
                if (number > 0) {
                    return number;
                }
            } catch (NumberFormatException e) {
                // cai na mensagem abaixo
            }
            problems.add(name + " inválida: precisa ser um número inteiro maior que zero");
            return 0;
        }

        long chainId(String name) {
            String value = text(name);
            if (value == null) {
                return 0;
            }
            if (!value.equals(String.valueOf(SEPOLIA_CHAIN_ID))) {
                problems.add(name + " inválida: precisa ser " + SEPOLIA_CHAIN_ID + " (Sepolia)");
                return 0;
            }
            return SEPOLIA_CHAIN_ID;
        }

        byte[] encryptionKey(String name) {
            String value = text(name);
            if (value == null) {
                return null;
            }
            try {
                byte[] key = Base64.getDecoder().decode(value);
                if (key.length == ENCRYPTION_KEY_BYTES) {
                    return key;
                }
            } catch (IllegalArgumentException e) {
                // cai na mensagem abaixo
            }
            problems.add(name + " inválida: precisa ser Base64 de 32 bytes. Gere com: openssl rand -base64 32");
            return null;
        }
    }
}
