package com.lemma.blockchain.controller;

import com.lemma.blockchain.dto.CreateWalletResponse;
import com.lemma.blockchain.service.WalletService;
import io.javalin.http.Context;

/**
 * Rotas de carteira. Sem regra de negócio: chama o service e devolve o DTO.
 */
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    /** CT4: POST /wallets → 201 { "ethereumAddress": "0x..." }. */
    public void create(Context ctx) {
        String ethereumAddress = walletService.createWallet();
        ctx.status(201).json(new CreateWalletResponse(ethereumAddress));
    }
}
