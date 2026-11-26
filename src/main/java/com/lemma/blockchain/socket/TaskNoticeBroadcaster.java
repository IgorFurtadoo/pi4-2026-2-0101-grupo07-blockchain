package com.lemma.blockchain.socket;

import com.lemma.socket.Message;
import com.lemma.socket.ShutdownNotice;
import com.lemma.socket.TaskCompletedNotice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;

/**
 * Envia uma mensagem a todos os assinantes do canal de socket (CT27). Corresponde ao
 * laço do comando "desativar" do Servidor do exemplo do professor, que percorre a
 * lista de usuários sob synchronized.
 *
 * Sincroniza no mesmo objeto que a ConnectionSupervisor: a própria lista subscribers.
 * Nunca lança exceção para quem chama.
 */
public class TaskNoticeBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(TaskNoticeBroadcaster.class);

    private final List<Partner> subscribers;

    public TaskNoticeBroadcaster(List<Partner> subscribers) {
        this.subscribers = subscribers;
    }

    /** Envia a todos; quem falhar é removido e fechado. Sem confirmação e sem retentativa. */
    public void broadcast(Message message) {
        try {
            int delivered = 0;
            synchronized (subscribers) {
                Iterator<Partner> iterator = subscribers.iterator();
                while (iterator.hasNext()) {
                    Partner partner = iterator.next();
                    try {
                        partner.send(message);
                        delivered++;
                    } catch (Exception e) {
                        iterator.remove();
                        partner.close();
                    }
                }
            }
            String taskId = message instanceof TaskCompletedNotice notice ? notice.getTaskId() : "-";
            log.info("socket: {} assinante(s) avisado(s) (taskId={})", delivered, taskId);
        } catch (RuntimeException e) {
            log.warn("socket: falha no aviso ({})", e.getClass().getSimpleName());
        }
    }

    /** Desligamento limpo: envia ShutdownNotice a todos e fecha todas as conexões. */
    public void shutdown() {
        synchronized (subscribers) {
            ShutdownNotice notice = new ShutdownNotice();
            for (Partner partner : subscribers) {
                try {
                    partner.send(notice);
                } catch (Exception e) {
                    // vai fechar de qualquer jeito
                }
                partner.close();
            }
            subscribers.clear();
        }
    }
}
