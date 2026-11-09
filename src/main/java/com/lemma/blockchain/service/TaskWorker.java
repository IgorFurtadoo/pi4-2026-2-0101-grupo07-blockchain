package com.lemma.blockchain.service;

import com.lemma.blockchain.client.CoreApiClient;
import com.lemma.blockchain.client.EthereumClient;
import com.lemma.blockchain.client.ReceiptInfo;
import com.lemma.blockchain.client.RpcException;
import com.lemma.blockchain.client.RpcRejectedException;
import com.lemma.blockchain.crypto.KeyCipher;
import com.lemma.blockchain.model.FailureReason;
import com.lemma.blockchain.model.Task;
import com.lemma.blockchain.model.Wallet;
import com.lemma.blockchain.repository.TaskRepository;
import com.lemma.blockchain.repository.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Hash;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Worker que processa as tasks em segundo plano (seção 7 do desenho). Cada ciclo
 * tem duas fases:
 * <ul>
 *   <li>Fase A, acompanhar: tasks PENDING com hash. Pede o recibo (CT16); sem
 *       recibo, pergunta se a rede conhece a transação (CT24) e, se não conhece,
 *       reenvia a mesma transação assinada (CT23).</li>
 *   <li>Fase B, enviar: tasks PENDING sem hash. Assina e envia (CT21, CT22, CT23).</li>
 * </ul>
 * A Fase A roda antes: uma task confirmada libera a carteira para a Fase B do
 * mesmo ciclo. Toda transição para estado final é gravada e depois avisada à
 * Core API pelo webhook (CT15).
 *
 * O Main chama {@link #runCycle()} a cada WORKER_INTERVAL_SECONDS, sempre na mesma
 * thread. Os testes chamam o ciclo direto, sem agendador.
 *
 * Nenhum log inclui a chave privada, o texto cifrado nem o objeto Wallet:
 * só taskId, estado, hash (que é público) e número do bloco.
 */
public class TaskWorker {

    private static final Logger log = LoggerFactory.getLogger(TaskWorker.class);

    /**
     * Limite de gás fixo da transferência (decisão 7 da seção 12 do desenho).
     * Desde o Glamsterdam (EIP-8037, ativo na Sepolia em 06/10/2026), enviar valor a
     * uma conta que ainda não existe custa cerca de 207 mil de gás; para conta que já
     * existe, continua 21.000. 500.000 cobre os dois casos, e o gás não usado é devolvido.
     */
    static final BigInteger GAS_LIMIT = BigInteger.valueOf(500_000);

    /**
     * Trechos (em minúsculo) da mensagem de erro do nó que indicam, no reenvio, que a
     * transação já está ou já esteve na rede. São as frases de clientes tipo geth.
     * Nesses casos a task continua PENDING: o recibo aparece num ciclo seguinte.
     */
    private static final List<String> ALREADY_ON_NETWORK = List.of("already known", "known transaction", "nonce too low");

    private final TaskRepository taskRepository;
    private final WalletRepository walletRepository;
    private final KeyCipher keyCipher;
    private final EthereumClient ethereumClient;
    private final CoreApiClient coreApiClient;
    private final long chainId;

    public TaskWorker(TaskRepository taskRepository, WalletRepository walletRepository, KeyCipher keyCipher,
                      EthereumClient ethereumClient, CoreApiClient coreApiClient, long chainId) {
        this.taskRepository = taskRepository;
        this.walletRepository = walletRepository;
        this.keyCipher = keyCipher;
        this.ethereumClient = ethereumClient;
        this.coreApiClient = coreApiClient;
        this.chainId = chainId;
    }

    /**
     * Um ciclo: Fase A e depois Fase B. Cada fase tem o seu try/catch, para a falha
     * de uma (por exemplo, o banco fora na consulta) não pular a outra.
     */
    public void runCycle() {
        try {
            trackPendingTransactions();
        } catch (RuntimeException e) {
            log.error("Fase A falhou ({}: {}); segue para a Fase B", e.getClass().getSimpleName(), e.getMessage());
        }
        try {
            submitPendingTasks();
        } catch (RuntimeException e) {
            log.error("Fase B falhou ({}: {}); tenta de novo no próximo ciclo",
                    e.getClass().getSimpleName(), e.getMessage());
        }
    }

    // ---------- Fase A: acompanhar ----------

    /**
     * Percorre as tasks PENDING com hash, das mais antigas para as mais novas.
     * Cada task tem o seu try/catch: uma falha inesperada só vai ao log, o estado
     * gravado fica como estava e as outras seguem.
     */
    private void trackPendingTransactions() {
        for (Task task : taskRepository.findPendingWithHash()) {
            try {
                track(task);
            } catch (RuntimeException e) {
                log.error("Task {}: falha inesperada ao acompanhar ({}: {}); estado mantido",
                        task.getTaskId(), e.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    /**
     * Fase A de uma task: recibo (CT16) → a rede conhece? (CT24) → reenvio (CT23).
     * Falha de RPC na consulta (Unavailable ou Rejected) não muda nada: a task
     * continua PENDING e é conferida de novo no próximo ciclo.
     */
    private void track(Task task) {
        String taskId = task.getTaskId();
        String hash = task.getTransactionHash();

        Optional<ReceiptInfo> receipt;
        try {
            receipt = ethereumClient.getTransactionReceipt(hash);
        } catch (RpcException e) {
            log.warn("Task {}: falha no RPC ao pedir o recibo de {} ({}); continua PENDING",
                    taskId, hash, e.getMessage());
            return;
        }
        if (receipt.isPresent()) {
            finish(task, receipt.get());
            return;
        }

        boolean known;
        try {
            known = ethereumClient.isTransactionKnown(hash);
        } catch (RpcException e) {
            log.warn("Task {}: falha no RPC ao consultar o hash {} ({}); continua PENDING",
                    taskId, hash, e.getMessage());
            return;
        }
        if (known) {
            log.debug("Task {}: hash {} na rede, ainda sem recibo", taskId, hash);
            return;
        }
        if (task.getSignedTransaction() == null) {
            log.warn("Task {}: a rede não conhece o hash {} e não há transação assinada para reenviar; continua PENDING",
                    taskId, hash);
            return;
        }
        resend(task);
    }

    /** Recibo presente: 0x1 → CONFIRMED; 0x0 → FAILED/TRANSACTION_REVERTED (hash mantido). Grava e avisa. */
    private void finish(Task task, ReceiptInfo receipt) {
        if (receipt.success()) {
            task.markConfirmed(Instant.now());
        } else {
            task.markReverted(Instant.now());
        }
        taskRepository.update(task);
        if (receipt.success()) {
            log.info("Task {}: CONFIRMED, hash {} no bloco {}",
                    task.getTaskId(), task.getTransactionHash(), receipt.blockNumber());
        } else {
            log.warn("Task {}: FAILED/TRANSACTION_REVERTED, hash {} no bloco {}",
                    task.getTaskId(), task.getTransactionHash(), receipt.blockNumber());
        }
        coreApiClient.notifyTaskCompleted(task);
    }

    /**
     * A rede não conhece o hash: reenvia a mesma transação assinada. É seguro, porque
     * hash e nonce são os mesmos; se a original aparecer, as duas são uma só.
     */
    private void resend(Task task) {
        String taskId = task.getTaskId();
        try {
            ethereumClient.sendRawTransaction(task.getSignedTransaction());
            log.info("Task {}: a rede não conhecia o hash {}; transação reenviada, continua PENDING",
                    taskId, task.getTransactionHash());
        } catch (RpcRejectedException e) {
            if (isAlreadyOnNetwork(e)) {
                // Já está ou já esteve na rede: nunca vira REJECTED_BY_NETWORK.
                log.warn("Task {}: reenvio respondeu \"{}\"; a transação já está ou esteve na rede, continua PENDING",
                        taskId, e.getNodeMessage());
                return;
            }
            task.markRejected(Instant.now());
            taskRepository.update(task);
            log.warn("Task {}: rede recusou o reenvio (erro {}: {}); FAILED/REJECTED_BY_NETWORK",
                    taskId, e.getRpcCode(), e.getNodeMessage());
            coreApiClient.notifyTaskCompleted(task);
        } catch (RpcException e) {
            log.warn("Task {}: sem resposta do RPC no reenvio ({}); continua PENDING", taskId, e.getMessage());
        }
    }

    /** A mensagem do nó diz que a transação já está ou já esteve na rede (heurística de clientes tipo geth). */
    private static boolean isAlreadyOnNetwork(RpcRejectedException e) {
        if (e.getNodeMessage() == null) {
            return false;
        }
        String message = e.getNodeMessage().toLowerCase(Locale.ROOT);
        return ALREADY_ON_NETWORK.stream().anyMatch(message::contains);
    }

    // ---------- Fase B: enviar ----------

    /**
     * Percorre as tasks PENDING sem hash, das mais antigas para
     * as mais novas, e trata no máximo uma task por carteira: pula a carteira que já
     * tem task PENDING com hash no banco ou que já teve uma task tratada neste ciclo
     * (seção 7.3: com duas em voo, o RPC pode devolver o mesmo nonce às duas).
     *
     * Cada task tem o seu try/catch: uma falha inesperada numa task não impede as outras.
     */
    private void submitPendingTasks() {
        Set<String> walletsHandledInThisCycle = new HashSet<>();

        for (Task task : taskRepository.findPendingWithoutHash()) {
            String source = task.getSourceAddress();
            try {
                if (walletsHandledInThisCycle.contains(source)
                        || taskRepository.existsPendingWithHashFrom(source)) {
                    continue;
                }
                walletsHandledInThisCycle.add(source);
                submit(task);
            } catch (RuntimeException e) {
                // Sem mudar o estado: antes de gravar o hash, a task é tentada de novo no
                // próximo ciclo (ex.: banco fora); depois, fica com hash para a Fase A.
                log.error("Task {}: falha inesperada ({}: {}); estado mantido",
                        task.getTaskId(), e.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    /**
     * Fase B de uma task, na ordem: carteira → nonce → gás → decifrar → assinar →
     * zerar a chave → gravar hash e transação assinada → enviar.
     * Nenhuma transação de banco fica aberta durante as chamadas ao RPC: cada método
     * do repository abre e fecha a sua.
     */
    private void submit(Task task) {
        String taskId = task.getTaskId();

        // CT5: lê a carteira de origem.
        Optional<Wallet> wallet = walletRepository.findByAddress(task.getSourceAddress());
        if (wallet.isEmpty()) {
            fail(task, "carteira de origem não encontrada");
            return;
        }

        // CT21 e CT22. Falha aqui vira FAILED/INTERNAL_ERROR, como nos contratos: nada foi enviado.
        BigInteger nonce;
        BigInteger gasPrice;
        try {
            nonce = ethereumClient.getTransactionCount(task.getSourceAddress());
            gasPrice = ethereumClient.getGasPrice();
        } catch (RpcException e) {
            fail(task, "falha no RPC ao obter nonce ou gás: " + e.getMessage());
            return;
        }

        // Decifra só agora, para a chave ficar na memória o menor tempo possível.
        // O AAD é o endereço da carteira (ADR 0003).
        String address = wallet.get().getEthereumAddress();
        byte[] privateKey;
        try {
            privateKey = keyCipher.decrypt(wallet.get().getEncryptedPrivateKey(), address);
        } catch (IllegalStateException e) {
            fail(task, e.getMessage()); // a mensagem do KeyCipher não traz a chave nem o valor cifrado
            return;
        }

        SignedTransaction signed;
        try {
            signed = sign(task, privateKey, nonce, gasPrice);
        } finally {
            Arrays.fill(privateKey, (byte) 0);
        }

        // Grava hash e transação assinada ANTES de enviar (seção 7.3, emenda do CT11).
        // Se o processo cair entre esta linha e o envio, a Fase A encontra o hash.
        task.markSigned(signed.hash(), signed.hex(), Instant.now());
        taskRepository.update(task);
        log.info("Task {}: assinada e gravada, hash {}", taskId, signed.hash());

        // CT23.
        String nodeHash;
        try {
            nodeHash = ethereumClient.sendRawTransaction(signed.hex());
        } catch (RpcRejectedException e) {
            // A rede recusou: a transação nunca esteve na rede. Apaga hash e transação assinada.
            task.markRejected(Instant.now());
            taskRepository.update(task);
            log.warn("Task {}: rede recusou o envio (erro {}: {}); FAILED/REJECTED_BY_NETWORK",
                    taskId, e.getRpcCode(), e.getNodeMessage());
            coreApiClient.notifyTaskCompleted(task);
            return;
        } catch (RpcException e) {
            // RpcUnavailableException: não se sabe se chegou. Não muda nada (emenda do CT23);
            // a task fica PENDING com hash e a Fase A confere e reenvia.
            log.warn("Task {}: sem resposta do RPC no envio ({}); continua PENDING com hash {}",
                    taskId, e.getMessage(), signed.hash());
            return;
        }

        if (!signed.hash().equalsIgnoreCase(nodeHash)) {
            log.warn("Task {}: hash informado pelo nó ({}) difere do calculado; mantido o calculado {}",
                    taskId, nodeHash, signed.hash());
        }
        log.info("Task {}: enviada, PENDING com hash {}", taskId, signed.hash());
    }

    /**
     * Monta a transação legada (P8) e assina com a chave da carteira.
     *
     * Limitação aceita (seção 12 do desenho): quem chama zera o byte[] da chave, mas o
     * Web3j cria um BigInteger (imutável) ao montar o ECKeyPair, e esse valor fica na
     * memória até o coletor de lixo. Mitigação: ECKeyPair e Credentials existem só
     * como variáveis locais deste método, a chave nunca vira String e nunca vai ao log.
     */
    private SignedTransaction sign(Task task, byte[] privateKey, BigInteger nonce, BigInteger gasPrice) {
        // toBigIntegerExact falha se houver mais de 18 casas; o CT10 já recusa esse valor.
        BigInteger valueWei = Convert.toWei(task.getAmountEth(), Convert.Unit.ETHER).toBigIntegerExact();
        RawTransaction transaction = RawTransaction.createEtherTransaction(
                nonce, gasPrice, GAS_LIMIT, task.getDestinationAddress(), valueWei);

        ECKeyPair keyPair = ECKeyPair.create(privateKey);
        Credentials credentials = Credentials.create(keyPair);
        byte[] signedBytes = TransactionEncoder.signMessage(transaction, chainId, credentials);

        // O hash é calculado aqui, sem depender da resposta do nó.
        return new SignedTransaction(Numeric.toHexString(signedBytes), Numeric.toHexString(Hash.sha3(signedBytes)));
    }

    /** Falha antes do envio: FAILED/INTERNAL_ERROR. Grava e avisa. */
    private void fail(Task task, String detail) {
        task.markFailed(FailureReason.INTERNAL_ERROR, Instant.now());
        taskRepository.update(task);
        log.warn("Task {}: FAILED/INTERNAL_ERROR ({})", task.getTaskId(), detail);
        coreApiClient.notifyTaskCompleted(task);
    }

    /** Transação assinada em hexadecimal ("0x...") e o seu hash. */
    private record SignedTransaction(String hex, String hash) {
    }
}
