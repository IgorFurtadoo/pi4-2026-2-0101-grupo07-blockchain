package com.lemma.blockchain.config;

import com.lemma.blockchain.dto.BalanceResponse;
import com.lemma.blockchain.dto.CreateTaskRequest;
import com.lemma.blockchain.dto.TaskAcceptedResponse;
import com.lemma.blockchain.dto.TaskResponse;
import com.lemma.blockchain.model.FailureReason;
import com.lemma.blockchain.model.TaskStatus;
import org.junit.jupiter.api.Test;
import org.web3j.utils.Convert;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Prova o formato do JSON: saldo sem notação científica, campos opcionais da task
 * omitidos e amountEth lido em BigDecimal exato.
 */
class JsonConfigTest {

    private static final String ADDRESS = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F";

    private static final String TASK_ID = "a3f1c2d4-5b6e-4f70-8a91-0c2d3e4f5a6b";

    private static String toJson(BigDecimal balanceEth) {
        BalanceResponse response = new BalanceResponse(ADDRESS, balanceEth, "SEPOLIA");
        return JsonConfig.jsonMapper().toJsonString(response, BalanceResponse.class);
    }

    @Test
    void oneWeiIsWrittenAsPlainDecimal() {
        // Mesma conversão do WalletService; sem a configuração, sairia 1E-18.
        BigDecimal oneWei = Convert.fromWei(BigDecimal.ONE, Convert.Unit.ETHER);

        assertEquals("{\"ethereumAddress\":\"" + ADDRESS + "\","
                        + "\"balanceEth\":0.000000000000000001,\"network\":\"SEPOLIA\"}",
                toJson(oneWei));
    }

    @Test
    void zeroIsWrittenAsZero() {
        BigDecimal zero = Convert.fromWei(BigDecimal.ZERO, Convert.Unit.ETHER);

        assertEquals("{\"ethereumAddress\":\"" + ADDRESS + "\","
                        + "\"balanceEth\":0,\"network\":\"SEPOLIA\"}",
                toJson(zero));
    }

    // ---------- Task (CT10, CT14) ----------

    private static CreateTaskRequest readRequest(String json) {
        return JsonConfig.jsonMapper().fromJsonString(json, CreateTaskRequest.class);
    }

    @Test
    void acceptedTaskHasTaskIdAndStatus() {
        String json = JsonConfig.jsonMapper().toJsonString(
                new TaskAcceptedResponse(TASK_ID, TaskStatus.PENDING), TaskAcceptedResponse.class);

        assertEquals("{\"taskId\":\"" + TASK_ID + "\",\"status\":\"PENDING\"}", json);
    }

    @Test
    void pendingTaskOmitsHashAndFailureReason() {
        TaskResponse response = new TaskResponse(TASK_ID, TaskStatus.PENDING, null, null, "2026-10-20T14:36:10Z");

        assertEquals("{\"taskId\":\"" + TASK_ID + "\",\"status\":\"PENDING\","
                        + "\"updatedAt\":\"2026-10-20T14:36:10Z\"}",
                JsonConfig.jsonMapper().toJsonString(response, TaskResponse.class));
    }

    @Test
    void failedTaskIncludesHashAndFailureReason() {
        String hash = "0x5c504ed432cb51138bcf09aa5e8a410dd4a1e204ef84bfed1be16dfba1b22060";
        TaskResponse response = new TaskResponse(TASK_ID, TaskStatus.FAILED, hash,
                FailureReason.TRANSACTION_REVERTED, "2026-10-20T14:36:10Z");

        assertEquals("{\"taskId\":\"" + TASK_ID + "\",\"status\":\"FAILED\","
                        + "\"transactionHash\":\"" + hash + "\",\"failureReason\":\"TRANSACTION_REVERTED\","
                        + "\"updatedAt\":\"2026-10-20T14:36:10Z\"}",
                JsonConfig.jsonMapper().toJsonString(response, TaskResponse.class));
    }

    @Test
    void amountEthIsReadAsExactNumber() {
        CreateTaskRequest request = readRequest("{\"sourceAddress\":\"" + ADDRESS + "\","
                + "\"destinationAddress\":\"" + ADDRESS + "\",\"amountEth\":0.01}");

        assertEquals(ADDRESS, request.sourceAddress());
        assertEquals(ADDRESS, request.destinationAddress());
        assertEquals("0.01", request.amountEth().toPlainString());
    }

    @Test
    void oneWeiIsReadWithoutRounding() {
        CreateTaskRequest request = readRequest("{\"amountEth\":0.000000000000000001}");

        assertEquals("0.000000000000000001", request.amountEth().toPlainString());
    }

    @Test
    void missingFieldsAreNullAndExtraFieldsAreIgnored() {
        CreateTaskRequest request = readRequest("{\"amountEth\":1,\"extra\":true}");

        assertNull(request.sourceAddress());
        assertNull(request.destinationAddress());
        assertEquals("1", request.amountEth().toPlainString());
    }
}
