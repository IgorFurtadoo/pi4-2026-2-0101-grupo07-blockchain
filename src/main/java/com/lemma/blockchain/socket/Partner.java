package com.lemma.blockchain.socket;

import com.lemma.socket.Message;

import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.Semaphore;

/**
 * Uma conexão com um assinante (o Core API) do canal de socket (CT27).
 * Corresponde ao Parceiro do exemplo do professor: guarda o socket e os dois
 * streams de objetos, envia e recebe mensagens e fecha a conexão.
 */
public class Partner {

    /**
     * Filtro de desserialização (ADR 0008): só as mensagens do protocolo e String,
     * até 4096 bytes; qualquer outra classe é recusada antes de ser instanciada.
     */
    public static final ObjectInputFilter INPUT_FILTER =
            ObjectInputFilter.Config.createFilter("maxbytes=4096;com.lemma.socket.*;java.lang.String;!*");

    private final Socket connection;
    private final ObjectInputStream receiver;
    private final ObjectOutputStream transmitter;

    /** Um envio por vez: o worker (broadcast) e o desligamento podem enviar ao mesmo tempo. */
    private final Semaphore mutEx = new Semaphore(1, true);

    public Partner(Socket connection, ObjectInputStream receiver, ObjectOutputStream transmitter) {
        this.connection = connection;
        this.receiver = receiver;
        this.transmitter = transmitter;
    }

    /** Envia uma mensagem ao assinante. Lança IOException se a conexão caiu. */
    public void send(Message message) throws IOException {
        mutEx.acquireUninterruptibly();
        try {
            transmitter.writeObject(message);
            transmitter.flush();
            // Esquece os objetos já enviados: sem isso o stream guarda todos durante a conexão.
            transmitter.reset();
        } finally {
            mutEx.release();
        }
    }

    /** Lê a próxima mensagem do assinante. Bloqueia até chegar algo ou a conexão cair. */
    public Message read() throws IOException, ClassNotFoundException {
        return (Message) receiver.readObject();
    }

    /** Fecha a conexão; erros ao fechar são ignorados. */
    public void close() {
        try {
            connection.close(); // fecha também os dois streams
        } catch (IOException e) {
            // nada a fazer: a conexão já está indo embora
        }
    }
}
