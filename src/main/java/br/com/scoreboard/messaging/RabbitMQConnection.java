package br.com.scoreboard.messaging;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ============================================================================
 * CLASSE: RabbitMQConnection
 * PACOTE: br.com.scoreboard.messaging
 * ============================================================================
 * 
 * RESPONSABILIDADE:
 * Gerencia a conexão com o broker RabbitMQ, declarando e vinculando a Exchange,
 * a Fila e a Routing Key necessárias para a mensageria de placares.
 * 
 * PADRÃO DE PROJETO:
 * - Singleton / ApplicationScoped: mantemos uma única conexão aberta durante
 *   todo o ciclo de vida da aplicação para melhor desempenho.
 * - Resiliência: ativa Automatic Recovery para reconectar se o broker cair.
 */
@ApplicationScoped
public class RabbitMQConnection {

    private static final Logger log = Logger.getLogger(RabbitMQConnection.class.getName());

    // Host e porta do RabbitMQ (com valores padrão e sobrescritos via Docker/ambiente)
    @Inject
    @ConfigProperty(name = "RABBITMQ_HOST", defaultValue = "localhost")
    private String host;

    @Inject
    @ConfigProperty(name = "RABBITMQ_PORT", defaultValue = "5672")
    private int porta;

    @Inject
    @ConfigProperty(name = "RABBITMQ_USER", defaultValue = "sb_mq_user")
    private String usuario;

    @Inject
    @ConfigProperty(name = "RABBITMQ_PASS", defaultValue = "sb_mq_password")
    private String senha;

    @Inject
    @ConfigProperty(name = "RABBITMQ_EXCHANGE", defaultValue = "scoreboard.exchange")
    private String exchange;

    @Inject
    @ConfigProperty(name = "RABBITMQ_QUEUE", defaultValue = "scoreboard.queue")
    private String fila;

    @Inject
    @ConfigProperty(name = "RABBITMQ_ROUTING_KEY", defaultValue = "score.update")
    private String routingKey;

    // Conexão nativa AMQP com o RabbitMQ
    private Connection connection;

    /**
     * Inicializa a conexão com o RabbitMQ logo após o CDI criar este Bean.
     * Declara a exchange 'direct', a fila durável e cria o bind entre elas.
     */
    @PostConstruct
    void init() {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(host);
            factory.setPort(porta);
            factory.setUsername(usuario);
            factory.setPassword(senha);
            factory.setAutomaticRecoveryEnabled(true); // Reconexão automática em caso de oscilação na rede

            this.connection = factory.newConnection();

            // Declara e vincula os componentes de mensageria
            try (Channel canal = connection.createChannel()) {
                // Exchange do tipo "direct" e durável (sobrevive a reinicializações do broker)
                canal.exchangeDeclare(exchange, "direct", true);

                // Fila durável (durable=true), não exclusiva, sem auto-delete
                canal.queueDeclare(fila, true, false, false, null);

                // Vincula a fila à exchange usando a chave de roteamento
                canal.queueBind(fila, exchange, routingKey);
            }
            log.info(" Conexão com RabbitMQ estabelecida com sucesso no host: " + host);
        } catch (Exception e) {
            log.log(Level.SEVERE, "❌ Erro ao inicializar conexão com RabbitMQ", e);
            throw new RuntimeException("Erro ao inicializar conexão com RabbitMQ", e);
        }
    }

    /**
     * Retorna a conexão ativa. Se estiver fechada, tenta reconectar.
     */
    public Connection get() {
        if (connection == null || !connection.isOpen()) {
            init();
        }
        return connection;
    }

    /**
     * Verifica se a conexão com o RabbitMQ está aberta e saudável (usado no Health Check).
     */
    public boolean isOpen() {
        return connection != null && connection.isOpen();
    }

    public String getExchange() {
        return exchange;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public String getFila() {
        return fila;
    }

    /**
     * Fecha a conexão graciosamente quando o servidor Payara for desligado.
     */
    @PreDestroy
    void fechar() {
        try {
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
        } catch (Exception e) {
            // Silencia no shutdown
        }
    }
}
