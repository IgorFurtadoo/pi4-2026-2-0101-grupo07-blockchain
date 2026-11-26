package com.lemma.blockchain.socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

/**
 * Thread que aceita conexões na porta TASK_SOCKET_PORT (CT27) e entrega cada uma
 * a uma ConnectionSupervisor. Corresponde à AceitadoraDeConexao do exemplo do professor.
 */
public class ConnectionAcceptor extends Thread {

    private static final Logger log = LoggerFactory.getLogger(ConnectionAcceptor.class);

    private final ServerSocket serverSocket;
    private final String serviceKey;
    private final List<Partner> subscribers;

    /** Abre a porta já aqui: porta ocupada ou inválida falha na subida do serviço. */
    public ConnectionAcceptor(int port, String serviceKey, List<Partner> subscribers) throws IOException {
        super("socket-acceptor");
        setDaemon(true);
        this.serverSocket = new ServerSocket(port);
        this.serviceKey = serviceKey;
        this.subscribers = subscribers;
    }

    /** Porta realmente aberta (nos testes, a porta 0 vira uma porta livre qualquer). */
    public int getPort() {
        return serverSocket.getLocalPort();
    }

    @Override
    public void run() {
        while (!serverSocket.isClosed()) {
            Socket connection;
            try {
                connection = serverSocket.accept();
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    log.warn("socket: falha ao aceitar conexão ({})", e.getClass().getSimpleName());
                }
                continue;
            }
            new ConnectionSupervisor(connection, serviceKey, subscribers).start();
        }
    }

    /** Para de aceitar conexões novas. As já assinadas são fechadas pelo TaskNoticeBroadcaster. */
    public void close() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            // nada a fazer
        }
    }
}
