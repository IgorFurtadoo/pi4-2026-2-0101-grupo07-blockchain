package com.lemma.blockchain.service;

import com.lemma.blockchain.dto.TaskAcceptedResponse;
import com.lemma.blockchain.dto.TaskResponse;
import com.lemma.blockchain.exception.ApiException;
import com.lemma.blockchain.model.Task;
import com.lemma.blockchain.repository.TaskRepository;
import com.lemma.blockchain.repository.WalletRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Pedido de envio e consulta de task (CT10, CT11, CT14).
 * Só grava e lê o banco: quem assina e envia à rede é o worker (seção 7 do desenho).
 */
public class TaskService {

    /** 1 wei = 10^-18 ETH: mais casas não cabem em wei. */
    private static final int MAX_DECIMAL_PLACES = 18;

    /** Coluna amount_eth é DECIMAL(38,18): sobram 20 dígitos para a parte inteira. */
    private static final int MAX_INTEGER_DIGITS = 20;

    private final TaskRepository taskRepository;
    private final WalletRepository walletRepository;

    public TaskService(TaskRepository taskRepository, WalletRepository walletRepository) {
        this.taskRepository = taskRepository;
        this.walletRepository = walletRepository;
    }

    /**
     * CT10: valida os endereços e o valor, confere que a origem é uma carteira
     * custodial e grava a task PENDING, sem hash. Não consulta o RPC nem o saldo;
     * origem igual ao destino é permitida.
     */
    public TaskAcceptedResponse requestSubmission(String sourceAddress, String destinationAddress,
                                                  BigDecimal amountEth) {
        String source = AddressValidator.toCanonical(sourceAddress);
        String destination = AddressValidator.toCanonical(destinationAddress);
        BigDecimal amount = validAmount(amountEth);

        if (walletRepository.findByAddress(source).isEmpty()) {
            throw new ApiException(404, "WALLET_NOT_FOUND", "Carteira não encontrada.");
        }

        Task task = new Task(UUID.randomUUID().toString(), source, destination, amount, Instant.now());
        taskRepository.save(task);
        return new TaskAcceptedResponse(task.getTaskId(), task.getStatus());
    }

    /** CT14: só lê do banco. Qualquer taskId sem registro, de qualquer formato, é 404. */
    public TaskResponse getTask(String taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ApiException(404, "TASK_NOT_FOUND", "Task não encontrada."));

        return new TaskResponse(
                task.getTaskId(),
                task.getStatus(),
                task.getTransactionHash(),
                task.getFailureReason(),
                task.getUpdatedAt().truncatedTo(ChronoUnit.SECONDS).toString());
    }

    /**
     * Valor maior que zero, com no máximo 18 casas e 20 dígitos inteiros.
     * Zeros à direita não contam (1.000 é o mesmo que 1). Devolve o valor com
     * escala 18, igual à coluna, para gravar exatamente o que foi validado.
     */
    private static BigDecimal validAmount(BigDecimal amountEth) {
        if (amountEth == null || amountEth.signum() <= 0) {
            throw invalidAmount();
        }

        BigDecimal stripped = amountEth.stripTrailingZeros();
        int decimalPlaces = Math.max(stripped.scale(), 0);
        int integerDigits = stripped.precision() - stripped.scale();
        if (decimalPlaces > MAX_DECIMAL_PLACES || integerDigits > MAX_INTEGER_DIGITS) {
            throw invalidAmount();
        }

        return stripped.setScale(MAX_DECIMAL_PLACES);
    }

    private static ApiException invalidAmount() {
        return new ApiException(400, "AMOUNT_INVALID",
                "amountEth deve ser maior que zero, com no máximo 18 casas decimais e 20 dígitos inteiros.");
    }
}
