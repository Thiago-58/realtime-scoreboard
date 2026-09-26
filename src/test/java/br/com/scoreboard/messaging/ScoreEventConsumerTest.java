package br.com.scoreboard.messaging;

import br.com.scoreboard.cache.RedisCacheService;
import br.com.scoreboard.dto.ScoreEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.exceptions.JedisException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ============================================================================
 * CLASSE: ScoreEventConsumerTest
 * PACOTE: br.com.scoreboard.messaging
 * ============================================================================
 * 
 * RESPONSABILIDADE:
 * Testes unitários do fluxo assíncrono de mensageria com RabbitMQ e Redis.
 * Valida consumo de eventos, atualização no Redis, confirmação manual (ACK),
 * descarte de JSON inválido (NACK requeue=false) e retry com backoff (NACK requeue=true).
 */
@ExtendWith(MockitoExtension.class)
class ScoreEventConsumerTest {

    @Mock
    private RabbitMQConnection conn;

    @Mock
    private RedisCacheService redis;

    @Spy
    private ObjectMapper mapper = new ObjectMapper();

    @Mock
    private Channel channel;

    @InjectMocks
    private ScoreEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer.backoffInicial = 10;
        consumer.backoffMax = 50;
    }

    @Test
    @DisplayName("Assíncrono: Deve processar evento de placar com sucesso, atualizar Redis e enviar ACK")
    void deveProcessarEventoComSucessoEAtualizarRedis() throws IOException {
        String json = """
                {
                    "id": 1,
                    "timeA": "Flamengo",
                    "timeB": "Palmeiras",
                    "placarA": 2,
                    "placarB": 1,
                    "status": "EM_ANDAMENTO",
                    "timestamp": "2026-09-26T15:30:00Z"
                }
                """;

        consumer.processar(channel, 100L, json);

        ArgumentCaptor<ScoreEvent> captor = ArgumentCaptor.forClass(ScoreEvent.class);
        verify(redis).atualizarPlacar(captor.capture());
        ScoreEvent capturado = captor.getValue();
        assertThat(capturado.getId()).isEqualTo(1L);
        assertThat(capturado.getPlacarA()).isEqualTo(2);
        assertThat(capturado.getPlacarB()).isEqualTo(1);

        verify(channel).basicAck(eq(100L), eq(false));
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("Assíncrono: Deve descartar mensagem corrompida com NACK sem reenfileirar")
    void deveDescartarMensagemCorrompidaComNackSemReenfileirar() throws IOException {
        String jsonInvalido = "{ corpo-invalido-nao-json ";

        consumer.processar(channel, 101L, jsonInvalido);

        verifyNoInteractions(redis);
        verify(channel).basicNack(eq(101L), eq(false), eq(false));
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("Assíncrono: Deve reenfileirar com NACK quando Redis estiver indisponível")
    void deveReenfileirarComNackQuandoRedisIndisponivel() throws IOException {
        String json = """
                {
                    "id": 2,
                    "timeA": "Corinthians",
                    "timeB": "São Paulo",
                    "placarA": 1,
                    "placarB": 0,
                    "status": "EM_ANDAMENTO",
                    "timestamp": "2026-09-26T15:30:00Z"
                }
                """;

        doThrow(new JedisException("Redis offline")).when(redis).atualizarPlacar(any());

        consumer.processar(channel, 102L, json);

        verify(channel).basicNack(eq(102L), eq(false), eq(true));
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }
}
