package br.com.scoreboard.cache;

import br.com.scoreboard.dto.ScoreEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisException;

import java.util.Map;
import java.util.Optional;

/**
 * ============================================================================
 * CLASSE: RedisCacheService
 * PACOTE: br.com.scoreboard.cache
 * ============================================================================
 * 
 * RESPONSABILIDADE:
 * Fornece operações de leitura, escrita e exclusão do placar em tempo real no Redis.
 * 
 * POR QUE USAR REDIS AQUI?
 * O PostgreSQL é a fonte da verdade relacional (persistência definitiva).
 * O Redis funciona como um cache em memória ultrarrápido (sub-milissegundo).
 * A interface web consulta primeiro o Redis para exibir placares ao vivo instantaneamente,
 * sem sobrecarregar o banco de dados com polling repetitivo de milhares de usuários.
 */
@ApplicationScoped
public class RedisCacheService {

    // Prefixo padrão para as chaves no Redis (ex: "placar:1", "placar:2")
    private static final String PREFIXO = "placar:";

    // Tempo de expiração da chave no Redis (24 horas em segundos)
    private static final int TTL_SEGUNDOS = 86400;

    @Inject
    @ConfigProperty(name = "REDIS_HOST", defaultValue = "localhost")
    private String host;

    @Inject
    @ConfigProperty(name = "REDIS_PORT", defaultValue = "6379")
    private int porta;

    @Inject
    private ObjectMapper mapper;

    // Pool de conexões Jedis (thread-safe, otimizado para alto volume de requisições)
    private JedisPool pool;

    /**
     * Inicializa o Pool de Conexões com o Redis logo após o CDI criar a instância.
     */
    @PostConstruct
    void init() {
        this.pool = new JedisPool(new JedisPoolConfig(), host, porta);
    }

    /**
     * Atualiza o placar ao vivo de uma partida no Redis.
     * Salva em formato JSON contendo placarA, placarB e timestamp do evento.
     * 
     * @param evento Evento de placar vindo do consumidor RabbitMQ
     */
    public void atualizarPlacar(ScoreEvent evento) {
        try {
            String jsonValor = mapper.writeValueAsString(Map.of(
                    "placarA", evento.getPlacarA(),
                    "placarB", evento.getPlacarB(),
                    "status", evento.getStatus() != null ? evento.getStatus() : "EM_ANDAMENTO",
                    "timestamp", evento.getTimestamp() != null ? evento.getTimestamp() : ""
            ));

            try (Jedis jedis = pool.getResource()) {
                // setex: define o valor e o tempo de expiração de forma atômica
                jedis.setex(PREFIXO + evento.getId(), TTL_SEGUNDOS, jsonValor);
            }
        } catch (JedisException jedisEx) {
            // Repassa a exceção de rede/conexão para o consumidor aplicar o backoff de retry
            throw jedisEx;
        } catch (Exception ex) {
            throw new RuntimeException("Erro ao serializar dados do placar para o Redis", ex);
        }
    }

    /**
     * Busca o placar mais recente no cache do Redis pelo ID da partida.
     * 
     * @param partidaId ID da partida
     * @return Optional com o JSON do placar, ou vazio se não estiver em cache
     */
    public Optional<String> buscarPlacar(Long partidaId) {
        try (Jedis jedis = pool.getResource()) {
            return Optional.ofNullable(jedis.get(PREFIXO + partidaId));
        } catch (Exception e) {
            // Em caso de falha temporária no Redis, retorna vazio para que o sistema
            // faça o fallback seguro lendo direto do banco PostgreSQL
            return Optional.empty();
        }
    }

    public void removerPlacar(Long partidaId) {
        try (Jedis jedis = pool.getResource()) {
            jedis.del(PREFIXO + partidaId);
        } catch (Exception e) {
            // Silencia caso o Redis esteja indisponível
        }
    }

    /**
     * Limpa todo o cache de placares do Redis.
     * Útil quando alterações manuais são feitas diretamente no banco de dados relacional
     * e deseja-se forçar a sincronização imediata com a tela.
     */
    public void limparTodos() {
        try (Jedis jedis = pool.getResource()) {
            var keys = jedis.keys(PREFIXO + "*");
            if (keys != null && !keys.isEmpty()) {
                jedis.del(keys.toArray(new String[0]));
            }
        } catch (Exception e) {
            // Silencia caso o Redis esteja temporariamente inacessível
        }
    }

    /**
     * Executa um comando PING no Redis para validar saúde da conexão (usado pelo /api/health).
     * 
     * @return true se responder "PONG", false caso contrário
     */
    public boolean pingOk() {
        try (Jedis jedis = pool.getResource()) {
            return "PONG".equals(jedis.ping());
        } catch (JedisException e) {
            return false;
        }
    }

    /**
     * Fecha o pool de conexões ao desligar o servidor de aplicação.
     */
    @PreDestroy
    void fechar() {
        if (pool != null) {
            pool.close();
        }
    }
}
