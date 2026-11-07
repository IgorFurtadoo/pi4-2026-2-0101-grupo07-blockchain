package com.lemma.blockchain.repository;

import com.lemma.blockchain.model.Wallet;
import org.hibernate.SessionFactory;

import java.util.Locale;
import java.util.Optional;

/**
 * Acesso à tabela wallet (CT5). Cada método abre e fecha a própria transação
 * (seção 4.3 do desenho).
 */
public class WalletRepository {

    private final SessionFactory sessionFactory;

    public WalletRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public void save(Wallet wallet) {
        sessionFactory.inTransaction(session -> session.persist(wallet));
    }

    /** Aceita o endereço em qualquer caixa; no banco ele está em minúsculo. */
    public Optional<Wallet> findByAddress(String ethereumAddress) {
        String canonical = ethereumAddress.toLowerCase(Locale.ROOT);
        return sessionFactory.fromTransaction(
                session -> Optional.ofNullable(session.find(Wallet.class, canonical)));
    }
}
