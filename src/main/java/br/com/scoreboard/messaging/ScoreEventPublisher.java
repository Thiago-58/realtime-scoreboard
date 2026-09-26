package br.com.scoreboard.messaging;

import br.com.scoreboard.dto.ScoreEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.MessageProperties;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ============================================================================
 * CLASSE: ScoreEventPublisher
 * PACOTE: br.com.scoreboard.messaging
 * ============================================================================
 * 
 * RESPONSABILIDADE:
 * Publica eventos de atualização de placar (ScoreEvent) na fila do RabbitMQ.
 * 
 * PADRÃO DE ARQUITETURA:
 * - Event-Driven Architecture (EDA): Desacopla a camada de persistência
 *   (Postgres) da camada de cache rápido (Redis) e da notificação em tempo real.
 * - Mensagens Persistentes: Usa MessageProperties.PERSISTENT_TEXT_PLAIN para
 *   garantir que nenhuma mensagem seja perdida caso o broker reinicie.
 */
@ApplicationScoped
public class ScoreEventPublisher {

    private static final Logger log = Logger.getLogger(ScoreEventPublisher.class.getName());

    private RabbitMQConnection conn;
    private ObjectMapper mapper;

    /**
     * Construtor padrão protegido exigido pelo CDI.
     */
    protected ScoreEventPublisher() { }

    /**
     * Injeção de dependências via construtor (facilita testes unitários).
     * 
     * @param conn   Conexão ativa com o broker RabbitMQ
     * @param mapper Serializador Jackson para converter o ScoreEvent em JSON
     */
    @Inject
    public ScoreEventPublisher(RabbitMQConnection conn, ObjectMapper mapper) {
        this.conn = conn;
        this.mapper = mapper;
    }

    /**
     * Publica um evento de placar no RabbitMQ.
     * 
     * PASSO A PASSO DO MÉTODO:
     * 1. Abre um canal leve (Channel) temporário a partir da conexão persistente.
     * 2. Converte o objeto ScoreEvent em bytes JSON.
     * 3. Publica na Exchange configurada com a Routing Key correspondente.
     * 4. Fecha o canal automaticamente pelo bloco try-with-resources.
     * 
     * @param evento Objeto contendo os dados atualizados da partida (ID, times, placares e status)
     */
    public void publicar(ScoreEvent evento) {
        try (Channel canal = conn.get().createChannel()) {
            byte[] jsonBytes = mapper.writeValueAsBytes(evento);

            canal.basicPublish(
                    conn.getExchange(),
                    conn.getRoutingKey(),
                    MessageProperties.PERSISTENT_TEXT_PLAIN,
                    jsonBytes
            );

            log.info("📢 Evento de placar publicado no RabbitMQ para o jogo ID: " + evento.getId()
                    + " (" + evento.getTimeA() + " " + evento.getPlacarA() + " x " + evento.getPlacarB() + " " + evento.getTimeB() + ")");
        } catch (Exception e) {
            log.log(Level.SEVERE, "❌ Falha ao publicar evento de placar para a partida ID=" + evento.getId(), e);
        }
    }
}
