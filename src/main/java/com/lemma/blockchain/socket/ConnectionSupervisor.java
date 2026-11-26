package com.lemma.blockchain.socket;

import com.lemma.socket.Message;
import com.lemma.socket.SubscribeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Thread que cuida de uma conexão (CT27): confere a assinatura, registra o assinante
 * em subscribers e fica lendo até a conexão cair, quando o remove. Corresponde à
 * SupervisoraDeConexao do exemplo do professor.
 *
 * Nenhum log traz a chave nem o endereço do cliente.
 */
public class ConnectionSupervisor extends Thread {

    private static final Logger log = LoggerFactory.getLogger(ConnectionSupervisor.class);

    /** Prazo para o cliente mandar o SubscribeRequest. */
    static final int SUBSCRIBE_TIMEOUT_MILLIS = 5000;

    private final Socket connection;
    private final String serviceKey;
    private final List<Partner> subscribers;

    public ConnectionSupervisor(Socket connection, String serviceKey, List<Partner> subscribers) {
        super("socket-connection");
        setDaemon(true);
        this.connection = connection;
        this.serviceKey = serviceKey;
        this.subscribers = subscribers;
    }

    @Override
    public void run() {
        Partner partner = null;
        boolean subscribed = false;
        try {
            // Antes de criar o ObjectInputStream: o construtor dele já bloqueia lendo o cabeçalho.
            connection.setSoTimeout(SUBSCRIBE_TIMEOUT_MILLIS);

            // Como no exemplo: o ObjectOutputStream primeiro, para os dois lados não travarem.
            ObjectOutputStream transmitter = new ObjectOutputStream(connection.getOutputStream());
            transmitter.flush();
            ObjectInputStream receiver = new ObjectInputStream(connection.getInputStream());
            receiver.setObjectInputFilter(Partner.INPUT_FILTER);
            partner = new Partner(connection, receiver, transmitter);

            Message first = partner.read();
            if (!(first instanceof SubscribeRequest request) || !keyMatches(request.getServiceKey())) {
                log.warn("socket: assinatura recusada (primeira mensagem inválida ou chave incorreta)");
                return;
            }

            connection.setSoTimeout(0); // assinado: daqui em diante o servidor só envia
            synchronized (subscribers) {
                subscribers.add(partner);
            }
            subscribed = true;
            log.info("socket: assinatura aceita");

            // O cliente não deve mandar mais nada; a leitura só serve para saber quando a conexão cai.
            while (true) {
                partner.read();
            }
        } catch (Exception e) {
            // Antes de assinar: tempo esgotado, EOF ou classe recusada pelo filtro.
            // Depois de assinar: a conexão caiu, o que é o fim normal.
            if (!subscribed) {
                log.warn("socket: assinatura recusada ({})", e.getClass().getSimpleName());
            }
        } finally {
            if (partner != null) {
                synchronized (subscribers) {
                    subscribers.remove(partner);
                }
                partner.close();
            } else {
                closeQuietly();
            }
        }
    }

    /** Comparação em tempo constante, nos bytes UTF-8. */
    private boolean keyMatches(String received) {
        if (received == null) {
            return false;
        }
        return MessageDigest.isEqual(received.getBytes(StandardCharsets.UTF_8),
                serviceKey.getBytes(StandardCharsets.UTF_8));
    }

    private void closeQuietly() {
        try {
            connection.close();
        } catch (Exception e) {
            // nada a fazer
        }
    }
}
