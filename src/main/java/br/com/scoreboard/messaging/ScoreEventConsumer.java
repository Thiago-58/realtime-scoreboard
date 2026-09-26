package br.com.scoreboard.messaging;

import br.com.scoreboard.cache.RedisCacheService;
import br.com.scoreboard.dto.ScoreEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import redis.clients.jedis.exceptions.JedisException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ============================================================================
 * CLASSE: ScoreEventConsumer
 * PACOTE: br.com.scoreboard.messaging
 * ============================================================================
 * 
 * RESPONSABILIDADE:
 * Escuta continuamente a fila do RabbitMQ e consome as mensagens de atualização
 * de placar, sincronizando o estado com o cache Redis em tempo real.
 * 
 * BOAS PRÁTICAS APLICADAS:
 * 1. ACK Manual (basicAck): A mensagem só é retirada da fila após o Redis ser
 *    atualizado com sucesso. Se o servidor cair no meio, a mensagem não é perdida.
 * 2. Backoff Exponencial: Se o Redis estiver temporariamente fora do ar, o consumidor
 *    rejeita a mensagem com requeue=true (basicNack) e aplica uma pausa progressiva
 *    (500ms -> 1s -> 2s...) antes de tentar novamente, evitando sobrecarregar o sistema.
 * 3. Dead-Letter / Descarte Seguro: Mensagens com JSON corrompido ou payload inválido
 *    são descartadas imediatamente com requeue=false para não travar a fila em loop infinito.
 */
@ApplicationScoped
public class ScoreEventConsumer {

    private static final Logger log = Logger.getLogger(ScoreEventConsumer.class.getName());

    @Inject
    private RabbitMQConnection conn;

    @Inject
    private RedisCacheService redis;

    @Inject
    private ObjectMapper mapper;

    // Tempo inicial de espera em milissegundos para o retry quando o Redis falhar
    @Inject
    @ConfigProperty(name = "REDIS_RETRY_BACKOFF_INITIAL_MS", defaultValue = "500")
    long backoffInicial;

    // Tempo máximo de espera para o retry do backoff exponencial
    @Inject
    @ConfigProperty(name = "REDIS_RETRY_BACKOFF_MAX_MS", defaultValue = "10000")
    long backoffMax;

    private long backoffAtual;

    /**
     * Inicia o consumidor assim que a aplicação Jakarta EE for inicializada (@Observes @Initialized).
     * Abre o canal, configura o prefetch (basicQos=1) para não sobrecarregar e registra o listener.
     */
    public void iniciar(@Observes @Initialized(ApplicationScoped.class) Object init) {
        try {
            backoffAtual = backoffInicial;

            Channel canal = conn.get().createChannel();

            // basicQos(1): Processa uma mensagem por vez para garantir concorrência controlada
            canal.basicQos(1);

            // autoAck = false -> Exige confirmação manual (basicAck)
            canal.basicConsume(conn.getFila(), false, new DefaultConsumer(canal) {
                @Override
                public void handleDelivery(String tag, Envelope envelope, AMQP.BasicProperties props, byte[] corpo)
                        throws IOException {
                    String mensagemJson = new String(corpo, StandardCharsets.UTF_8);
                    processar(canal, envelope.getDeliveryTag(), mensagemJson);
                }
            });

            log.info("🎯 Consumidor de placares RabbitMQ iniciado com sucesso na fila: " + conn.getFila());
        } catch (Exception e) {
            log.log(Level.SEVERE, "❌ Erro ao iniciar o consumidor do RabbitMQ", e);
        }
    }

    /**
     * Processa a mensagem individual recebida da fila.
     * Visibilidade de pacote (package-private) para permitir testes unitários sem precisar subir o broker.
     * 
     * @param canal       Canal AMQP ativo
     * @param deliveryTag Identificador único da mensagem no canal
     * @param corpoJson   Conteúdo da mensagem em formato JSON
     */
    void processar(Channel canal, long deliveryTag, String corpoJson) throws IOException {
        ScoreEvent evento;

        // PASSO 1: Fazer o parse do JSON
        try {
            evento = mapper.readValue(corpoJson, ScoreEvent.class);
        } catch (Exception erroParse) {
            log.log(Level.SEVERE, "⚠️ Payload JSON inválido; descartando mensagem da fila: " + corpoJson, erroParse);
            // requeue = false -> Descarta para evitar loop infinito
            canal.basicNack(deliveryTag, false, false);
            return;
        }

        // PASSO 2: Atualizar o Redis e confirmar com ACK
        try {
            redis.atualizarPlacar(evento);

            log.info("⚡ Placar atualizado no Redis via RabbitMQ para a partida " + evento.getId()
                    + ": " + evento.getPlacarA() + " x " + evento.getPlacarB());

            // Reseta o backoff para o valor inicial após o sucesso
            backoffAtual = backoffInicial;

            // basicAck: Confirma processamento com sucesso e remove da fila
            canal.basicAck(deliveryTag, false);

        } catch (JedisException redisIndisponivel) {
            // Se o Redis falhou (ex: queda temporária de conexão)
            log.log(Level.WARNING, "⏳ Redis indisponível no momento. Aplicando pausa de " + backoffAtual + "ms antes de reenfileirar...", redisIndisponivel);

            dormir(backoffAtual);

            // Dobra o tempo de espera até o limite máximo configurado (Backoff Exponencial)
            backoffAtual = Math.min(backoffAtual * 2, backoffMax);

            // requeue = true -> Devolve a mensagem para a fila para ser tentada novamente
            canal.basicNack(deliveryTag, false, true);
        }
    }

    /**
     * Pausa a thread durante o tempo de backoff.
     */
    private void dormir(long milissegundos) {
        try {
            Thread.sleep(milissegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
