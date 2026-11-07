package com.lemma.blockchain.repository;

import com.lemma.blockchain.model.Task;
import org.hibernate.SessionFactory;

import java.util.Optional;

/**
 * Acesso à tabela task (CT11). Cada método abre e fecha a própria transação
 * (seção 4.3 do desenho). O worker (passos 6 a 8) acrescenta as consultas de PENDING.
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
}
