package com.lemma.blockchain.repository;

import com.lemma.blockchain.model.Task;
import com.lemma.blockchain.model.TaskStatus;
import org.hibernate.SessionFactory;

import java.util.List;
import java.util.Optional;

/**
 * Acesso à tabela task (CT11). Cada método abre e fecha a própria transação
 * (seção 4.3 do desenho): o worker nunca fica com transação de banco aberta
 * durante uma chamada ao RPC.
 */
public class TaskRepository {

    private final SessionFactory sessionFactory;

    public TaskRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public void save(Task task) {
        sessionFactory.inTransaction(session -> session.persist(task));
    }

    public Optional<Task> findById(String taskId) {
        return sessionFactory.fromTransaction(
                session -> Optional.ofNullable(session.find(Task.class, taskId)));
    }

    /** Grava o estado atual de uma task lida antes (entidade desanexada). */
    public void update(Task task) {
        sessionFactory.inTransaction(session -> session.merge(task));
    }

    /** Fase B do worker: tasks PENDING ainda não assinadas, das mais antigas para as mais novas. */
    public List<Task> findPendingWithoutHash() {
        return sessionFactory.fromTransaction(session -> session.createSelectionQuery(
                        "from Task t where t.status = :status and t.transactionHash is null"
                                + " order by t.createdAt, t.taskId", Task.class)
                .setParameter("status", TaskStatus.PENDING)
                .getResultList());
    }

    /**
     * Diz se a carteira já tem uma task PENDING com hash (enviada ou em envio).
     * O worker usa isto para manter uma task por carteira de cada vez (seção 7.3).
     * O endereço vem da task, já em minúsculo.
     */
    public boolean existsPendingWithHashFrom(String sourceAddress) {
        Long count = sessionFactory.fromTransaction(session -> session.createSelectionQuery(
                        "select count(t) from Task t where t.status = :status"
                                + " and t.transactionHash is not null and t.sourceAddress = :source", Long.class)
                .setParameter("status", TaskStatus.PENDING)
                .setParameter("source", sourceAddress)
                .getSingleResult());
        return count > 0;
    }
}
