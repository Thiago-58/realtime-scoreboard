# Implementation Plan: Placar em Tempo Real (Realtime Scoreboard)

## Overview

Implementação incremental do sistema de placar em tempo real como um único artefato WAR Jakarta EE, seguindo a ordem: scaffold → domínio → repositório → serviço → exceções → REST → mensageria → cache → interface web → testes → infraestrutura. Cada tarefa constrói sobre as anteriores, garantindo que nenhum código fique isolado sem integração.

Os testes são **JUnit 5 por exemplo**: para cada regra do design escrevemos casos concretos de sucesso e de erro, com os limites conhecidos escritos à mão (placar 0, placar negativo, string vazia, string com 101 caracteres, `page=abc`, JSON malformado). Unidade e UI com JUnit 5 + Mockito + AssertJ + WicketTester; integração com Testcontainers + REST-assured.

As seções 34 a 43 concentram o **realinhamento ao design simplificado** (exceção única, repositório com uma só consulta paginada, MicroProfile Config, paginação na API, JSON inválido → 400, health check canônico, Interface Web por status e navegação entre páginas, README/compose e testes de integração de validação de parâmetros).

## Tasks

---

- [x] 1. Scaffold do projeto Maven e configuração de dependências
  - Criar a estrutura de diretórios `realtime-scoreboard/` conforme o design (`domain`, `dto`, `repository`, `service`, `messaging`, `cache`, `rest`, `web`, `exception`)
  - Criar `pom.xml` com empacotamento WAR, Jakarta EE 10 API (`jakarta.jakartaee-api:10.0.0` provided), dependências: Hibernate ORM, Flyway Core, RabbitMQ Java Client, Jedis, Jackson Databind, Apache Wicket 10, JUnit 5, Mockito (core + junit-jupiter), AssertJ, REST-assured, Testcontainers (PostgreSQL, RabbitMQ, Redis modules), Wicket Tester
  - Configurar Maven Surefire Plugin para fase `test` e Failsafe Plugin com `@Tag("integration")` para fase `verify`
  - Criar `src/main/webapp/WEB-INF/web.xml` registrando `WicketFilter` no path `/wicket/*`
  - Criar `src/main/resources/META-INF/persistence.xml` com unidade `scoreboardPU` (JTA, `schema-generation=none`, Hibernate PostgreSQL dialect)
  - _Requisitos: 10.1, 11.3, 12.4_

  - [x] 1.1 Criar estrutura de diretórios e `pom.xml`
    - Criar todos os diretórios de pacote sob `src/main/java/br/com/scoreboard/`
    - Declarar todas as dependências com versões fixas (pinned)
    - Dependências de teste: `junit-jupiter`, `mockito-core`, `mockito-junit-jupiter`, `assertj-core`, `rest-assured`, `testcontainers` (postgresql/rabbitmq/junit-jupiter) e `wicket-tester`. Nenhuma biblioteca de geração aleatória de entradas entra no `pom.xml` — a suíte é inteira de testes por exemplo
    - Se o JDK em uso exigir, manter o override de `net.bytebuddy:byte-buddy` para a versão compatível com o **Mockito**
    - NOTA de ajuste (design): o `JsonProducer` (tarefa 10.0) exige `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` (mesma versão do `jackson-databind`) para o `JavaTimeModule`/ISO-8601; se essa dependência ainda não estiver no `pom.xml`, ela é acrescentada como parte da tarefa 10.0 (sem reabrir este item já concluído)
    - _Requisitos: 10.1, 12.4_

  - [x] 1.2 Criar `web.xml`, `persistence.xml` e `beans.xml`
    - `web.xml`: registrar `WicketFilter` (wicket.configuration=deployment) no path `/wicket/*` e `ScoreboardApplication` JAX-RS
    - `persistence.xml`: unidade `scoreboardPU`, JTA datasource `java:app/jdbc/ScoreboardDS`
    - `beans.xml`: ativar CDI com `bean-discovery-mode="all"`
    - _Requisitos: 10.1, 11.1_

---

- [x] 2. Modelo de domínio e enumerado de status
  - Criar `StatusPartida.java` com os valores `EM_ANDAMENTO`, `ENCERRADO`
  - Criar `Partida.java` com anotações JPA (`@Entity`, `@Table(name="jogos")`, índices em `status`, `time_a`, `time_b`), campos: `id` (BIGSERIAL), `timeA`, `timeB`, `placarA=0`, `placarB=0`, `status=EM_ANDAMENTO`, `dataHoraPartida`; implementar `equals`/`hashCode` por `id`
  - _Requisitos: 1.1, 10.1, 10.2_

  - [x] 2.1 Criar `StatusPartida.java` e `Partida.java`
    - Incluir todas as anotações JPA e Bean Validation (`@NotBlank`, `@Size(max=100)`, `@Min(0)`)
    - _Requisitos: 1.1, 1.2, 3.5, 10.1, 10.2_

---

- [x] 3. Migração Flyway e DDL do banco de dados
  - Criar `src/main/resources/db/migration/V1__create_jogos_table.sql`
  - DDL deve incluir: `BIGSERIAL PRIMARY KEY`, `VARCHAR(100) NOT NULL` para times, `INT NOT NULL DEFAULT 0` com `CHECK (placar_a >= 0)` e `CHECK (placar_b >= 0)`, `VARCHAR(20) NOT NULL DEFAULT 'EM_ANDAMENTO'` para status, `TIMESTAMP NOT NULL` para data/hora
  - Criar índices: `idx_jogos_status`, `idx_jogos_time_a`, `idx_jogos_time_b`
  - Criar `payara-resources.xml` em `WEB-INF/` para configurar o DataSource via variáveis de ambiente
  - _Requisitos: 10.1, 10.2, 10.3, 11.1_

  - [x] 3.1 Criar script de migração Flyway `V1__create_jogos_table.sql`
    - Incluir constraints CHECK para `placar_a >= 0` e `placar_b >= 0` conforme design
    - _Requisitos: 10.1, 10.2_

---

- [x] 4. Camada de DTOs (Request/Response/Event)
  - Criar `CriarPartidaRequest.java` com `@NotBlank @Size(max=100)` em `timeA`/`timeB` e `@NotNull` em `dataHoraPartida`
  - Criar `AtualizarPlacarRequest.java` com `@Min(0)` em `placarA` e `placarB`
  - Criar `AtualizarStatusRequest.java` com `@NotNull StatusPartida status`
  - Criar `PartidaResponse.java` com todos os campos serializáveis (id, timeA, timeB, placarA, placarB, status, dataHoraPartida ISO-8601)
  - Criar `ScoreEvent.java` com os sete campos obrigatórios: `id`, `timeA`, `timeB`, `placarA`, `placarB`, `status`, `timestamp` (ISO-8601)
  - _Requisitos: 1.1, 3.1, 4.1, 5.2, 13.4_

  - [x] 4.1 Criar todas as classes de DTO com anotações de validação
    - Garantir serialização/desserialização Jackson correta (LocalDateTime → ISO-8601)
    - _Requisitos: 1.1, 3.1, 4.1, 5.2_

  - [x] 4.2 Escrever testes JUnit de round-trip JSON do `ScoreEvent` (R18)
    - Testar que serializar um `ScoreEvent` preenchido e desserializar de volta devolve todos os campos iguais ao original
    - Testar que o JSON gerado contém os sete campos obrigatórios (`id`, `timeA`, `timeB`, `placarA`, `placarB`, `status`, `timestamp`) e nenhum deles nulo
    - Testar que o `timestamp` é escrito em ISO-8601
    - _Requisitos: 5.2_

---

- [x] 5. Hierarquia de exceções de domínio e `GlobalExceptionMapper`
  - Criar `PartidaNaoEncontradaException.java` (extends `RuntimeException`) → HTTP 404
  - Criar `StatusInvalidoException.java` (extends `RuntimeException`) → HTTP 422
  - Criar `PlacarInvalidoException.java` (extends `RuntimeException`) → HTTP 400
  - Criar `GlobalExceptionMapper.java` implementando `ExceptionMapper<Throwable>`; mapear as três exceções de domínio para os códigos corretos; mapear `ConstraintViolationException` para HTTP 400; retornar HTTP 500 genérico sem stack trace para qualquer outra exceção
  - NOTA: o design simplificado substitui `StatusInvalidoException` + `PlacarInvalidoException` por uma única `RegraNegocioException` que carrega o status HTTP (400 ou 422). Esta seção registra o que já foi implementado; a consolidação é feita como ajuste sobre o código existente na tarefa 34.1
  - _Requisitos: 1.2, 1.4, 3.3, 3.4, 3.5, 4.7, 13.5_

  - [x] 5.1 Criar as três classes de exceção e `GlobalExceptionMapper`
    - Garantir que respostas 5xx não exponham nome de classe, `Exception`, `StackTrace` ou `at br.com.scoreboard` (R38)
    - _Requisitos: 1.2, 3.3, 3.4, 4.7, 13.5_

---

- [x] 6. Camada de repositório JPA
  - Criar `PartidaRepository.java` anotado com `@ApplicationScoped`; injetar `EntityManager` via `@PersistenceContext(unitName="scoreboardPU")`
  - Implementar: `salvar(Partida)`, `buscarPorId(Long) → Optional<Partida>`, `listarTodas()`, `listarPorStatus(StatusPartida)` (JPQL com parâmetro), `buscarPorNomeTime(String)` (JPQL com `LIKE` case-insensitive)
  - NOTA: o design simplificado troca os métodos separados por filtro por **uma única** consulta `listar(status, time, page, size)` com filtro dinâmico e paginação (`setFirstResult`/`setMaxResults`). Esta seção registra o que já foi implementado; a consolidação é feita como ajuste sobre o código existente na tarefa 34.3
  - _Requisitos: 2.1, 2.2, 2.3, 2.4, 2.6, 10.1, 10.4_

  - [x] 6.1 Implementar `PartidaRepository` com todas as queries JPQL
    - Queries devem usar parâmetros nomeados (não concatenação de string) para prevenir SQL injection
    - _Requisitos: 2.1, 2.2, 2.6, 10.4_

---

- [x] 7. Camada de serviço (lógica de negócio)
  - Criar `PartidaService.java` como `@Stateless` EJB; injetar `PartidaRepository` e o publisher de eventos
  - Implementar `criarPartida(CriarPartidaRequest)`: validar `timeA ≠ timeB` (400 com a mensagem "Os times da partida não podem ser iguais"); criar `Partida` com `placarA=0`, `placarB=0`, `status=EM_ANDAMENTO`; persistir; retornar `PartidaResponse`
  - Implementar `atualizarPlacar(Long id, int placarA, int placarB)`: buscar partida (lançar `PartidaNaoEncontradaException` se ausente); verificar `status == EM_ANDAMENTO` (422 para ENCERRADO); validar `placarA >= 0` e `placarB >= 0`; persistir; publicar `ScoreEvent` (absorver exceção de publicação com log ERROR); retornar DTO
  - Implementar `atualizarStatus(Long id, StatusPartida novoStatus)`: buscar partida; verificar `novoStatus ≠ statusAtual` (422); persistir; retornar DTO
  - Implementar `listarPartidas()`, `listarPorStatus(StatusPartida)`, `buscarPorNomeTime(String)`, `buscarPorId(Long)`
  - Adicionar logging INFO no início e fim de `criarPartida`, `atualizarPlacar` e `atualizarStatus` (incluindo `id` e duração em ms)
  - NOTA: o alinhamento ao design (exceção única `RegraNegocioException`, listagem paginada e `@ConfigProperty` para os tamanhos de página) é feito nas tarefas 34.1, 34.3, 35.1 e 36.2
  - _Requisitos: 1.1, 1.4, 2.1, 2.2, 2.6, 3.1, 3.2, 3.3, 3.4, 3.5, 4.1–4.7, 13.7_

  - [x] 7.1 Implementar `criarPartida` e `buscarPorId`
    - Incluir validação `timeA ≠ timeB` e inicialização com placar zero e status `EM_ANDAMENTO`
    - _Requisitos: 1.1, 1.4, 2.7_

  - [x] 7.2 Escrever testes JUnit de criação de partida (R1, R2, R3)
    - Testar que criar partida com dados válidos inicializa `placarA=0`, `placarB=0` e `status=EM_ANDAMENTO`
    - Testar que `timeA` igual a `timeB` é rejeitado com a mensagem "Os times da partida não podem ser iguais"
    - Testar que `timeA`/`timeB` nulo, vazio ou só com espaços é rejeitado
    - Testar que `dataHoraPartida` ausente é rejeitada
    - _Requisitos: 1.1, 1.2, 1.3, 1.4_

  - [x] 7.3 Implementar `atualizarPlacar` com validações de domínio
    - Incluir degradação graciosa em caso de falha de publicação RabbitMQ
    - _Requisitos: 3.1, 3.3, 3.4, 3.5, 3.6, 5.5_

  - [x] 7.4 Escrever testes JUnit de atualização de placar (R8, R9, R10, R11)
    - Testar que atualizar o placar de uma partida `EM_ANDAMENTO` persiste exatamente os valores informados (incluindo o caso-limite `0x0`)
    - Testar que atualizar o placar de uma partida `ENCERRADO` é rejeitado com 422 e que `placarA`/`placarB` permanecem inalterados
    - Testar que placar negativo (`-1`) é rejeitado com 400 antes de qualquer persistência
    - Testar que `id` inexistente resulta em `PartidaNaoEncontradaException` (404)
    - _Requisitos: 3.1, 3.3, 3.4, 3.5, 3.6_

  - [x] 7.5 Implementar `atualizarStatus` e operações de listagem
    - Incluir validação de auto-transição (`novoStatus == statusAtual`)
    - _Requisitos: 2.1, 2.2, 2.6, 4.1–4.7_

  - [x] 7.6 Escrever testes JUnit de transição de status (R14, R15)
    - Testar, um caso por transição, as duas transições válidas: `EM_ANDAMENTO→ENCERRADO` e `ENCERRADO→EM_ANDAMENTO`, verificando que o status persistido é exatamente o informado
    - Testar que informar o mesmo status atual é rejeitado com 422 e a mensagem "A partida já se encontra no status informado"
    - _Requisitos: 4.1, 4.2, 4.3_

  - [x] 7.7 Escrever testes JUnit dos filtros de listagem (R4, R6, R7)
    - Testar que a listagem filtrada por `status` devolve somente partidas daquele status (um caso por status)
    - Testar que a busca por `time` encontra o termo em `timeA` e em `timeB`, ignorando maiúsculas/minúsculas, e que um termo inexistente devolve lista vazia
    - Testar que `buscarPorId` devolve exatamente os valores persistidos
    - _Requisitos: 2.2, 2.3, 2.4, 2.6, 2.7_

- [x] 8. Checkpoint — Testes unitários da camada de serviço passando
  - Garantir que todos os testes unitários (JUnit 5 + Mockito) para `PartidaService` passem com `mvn test`
  - Verificar cobertura de todos os fluxos de sucesso e erro definidos nos Requisitos 1–4
  - Nenhum teste desta camada deve ter dependência externa (banco, RabbitMQ, Redis)
  - _Requisitos: 12.1, 12.4, 12.6_

---

- [x] 9. Recursos REST JAX-RS e documentação OpenAPI
  - Criar `ScoreboardApplication.java` anotado com `@ApplicationPath("/api")` e `@ApplicationScoped`
  - Criar `JogosResource.java` com os cinco endpoints: `POST /jogos`, `GET /jogos`, `GET /jogos/{id}`, `PUT /jogos/{id}/placar`, `PUT /jogos/{id}/status`; delegar toda lógica ao `PartidaService`
  - Anotar cada método com `@Operation`, `@APIResponse` (todos os códigos HTTP possíveis) e `@RequestBody` do MicroProfile OpenAPI
  - Criar `HealthResource.java` com `@Path("/health")`, que somado ao `@ApplicationPath("/api")` resulta no caminho canônico `/api/health`: verificar PostgreSQL (JDBC ping), Redis (`PING`) e RabbitMQ (`isOpen()`); retornar 200 ou 503
  - _Requisitos: 1.1–1.4, 2.1–2.8, 3.1–3.6, 4.1–4.9, 9.1–9.4, 13.9_

  - [x] 9.1 Implementar `ScoreboardApplication` e `JogosResource` com todos os cinco endpoints
    - Aplicar Bean Validation (`@Valid`) nos parâmetros de request body
    - _Requisitos: 1.1, 2.1, 3.1, 4.1, 9.3_

  - [x] 9.2 Adicionar anotações MicroProfile OpenAPI em todos os endpoints
    - Documentar todos os códigos HTTP de sucesso e erro para cada endpoint
    - _Requisitos: 9.1, 9.2, 9.3, 9.4_

  - [x] 9.3 Implementar `HealthResource` com verificação de dependências
    - Retornar HTTP 200 quando todas as dependências estiverem saudáveis; HTTP 503 caso contrário
    - Caminho canônico `/api/health` (`@Path("/health")` sob o `@ApplicationPath("/api")`); o alias opcional `/health` entra na tarefa 38.2
    - _Requisitos: 13.9_

  - [x] 9.4 Escrever testes JUnit de sanitização e opacidade de erro (R38, R39)
    - Testar que `timeA` ou `timeB` com 101 caracteres é rejeitado com HTTP 400 antes de qualquer persistência, e que 100 caracteres é aceito (caso-limite)
    - Testar que a resposta de erro HTTP 5xx não contém `Exception`, `StackTrace`, `at br.com.scoreboard` nem nome de classe Java
    - _Requisitos: 13.4, 13.5_

---

- [x] 10. Integração RabbitMQ (conexão + publisher + consumidor)
  - Criar o gerenciador de conexão como `@Startup @Singleton`; ler `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASS` da configuração externa; habilitar `setAutomaticRecoveryEnabled(true)`; declarar exchange e fila idempotentemente no `@PostConstruct`; fechar connection no `@PreDestroy`
  - Criar `RabbitPublisher.java` como `@ApplicationScoped`; criar channel por operação e fechar após uso; serializar `ScoreEvent` para JSON com Jackson; absorver exceção de publicação com log ERROR
  - Criar `ScoreConsumer.java` como `@Startup @Singleton`; registrar `DefaultConsumer` no `@PostConstruct`; chamar `redisCache.atualizarPlacar(event)` e `basicAck` em caso de sucesso; `basicNack` sem requeue em caso de payload inválido
  - Adicionar logging WARN para reconexões automáticas e ERROR para falhas de processamento
  - NOTA: o design usa os nomes simplificados `RabbitConnection`, `RabbitPublisher` e `ScoreConsumer`. O arquivo `RabbitMQConnectionManager` já criado atende exatamente ao papel descrito para `RabbitConnection` — não é preciso renomeá-lo para as tarefas seguintes funcionarem
  - _Requisitos: 5.1–5.5, 6.1–6.5, 11.1, 13.8_

  - [x] 10.0 Criar `JsonProducer` (producer CDI do `ObjectMapper`) no pacote `config/`
    - Criar a pasta `config/` sob `src/main/java/br/com/scoreboard/` e a classe `config/JsonProducer.java` com um método `@Produces @ApplicationScoped ObjectMapper objectMapper()` que registra o `JavaTimeModule` e desliga `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS` (datas em ISO-8601)
    - Por que existe: o `ObjectMapper` do Jackson não é um bean CDI (classe de biblioteca, sem escopo); sem este producer, os pontos `@Inject ObjectMapper` de `RabbitPublisher`, `ScoreConsumer` e `RedisCache` ficam sem candidato e o deploy falha na subida (*unsatisfied dependency*). O producer centraliza a configuração do mapper em um único lugar
    - Adicionar a dependência `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` (mesma versão do `jackson-databind`) ao `pom.xml` se ainda não estiver presente — necessária para o `JavaTimeModule`
    - _Requisitos: 5.2, 11.1_

  - [x] 10.1 Criar `RabbitMQConnectionManager` com gestão de ciclo de vida
    - Ler todas as configurações exclusivamente do ambiente (sem valores hard-coded); a migração para `@ConfigProperty` é feita na tarefa 35.1
    - Expor `getExchange()`, `getRoutingKey()`, `getFila()`, `get()` e `isOpen()` para o publisher, o consumidor e o health check
    - _Requisitos: 5.4, 11.1_

  - [x] 10.2 Criar `RabbitPublisher` com serialização Jackson e tratamento de falhas
    - Garantir degradação graciosa: falha de publicação não interrompe a resposta HTTP 200
    - Obter exchange e routing key do gerenciador de conexão (tarefa 10.1)
    - Injetar o `ObjectMapper` produzido pelo `JsonProducer` (tarefa 10.0) via construtor `@Inject RabbitPublisher(RabbitConnection, ObjectMapper)`, e declarar também um construtor protegido sem argumentos exigido pelo proxy CDI de `@ApplicationScoped` (o construtor `@Inject` torna a serialização testável com um `new ObjectMapper()`)
    - _Requisitos: 5.1, 5.2, 5.5_

  - [x] 10.3 Criar `ScoreConsumer` com ACK/NACK e atualização do Redis
    - Configurar `basicQos(1)` para processamento ordenado
    - Em sucesso da atualização do Cache, `basicAck`; classificar falhas em Falha_Recuperavel (indisponibilidade do Redis — `JedisException`/`JedisConnectionException` propagada pelo `RedisCache`) e Falha_Irrecuperavel (payload malformado/erro de desserialização)
    - Nesta tarefa base, tratar apenas o caminho de Falha_Irrecuperavel: `basicNack` sem requeue + log `ERROR` (o requeue com Backoff da Falha_Recuperavel é adicionado incrementalmente na tarefa 26.2)
    - Extrair o corpo do processamento para o método testável `void processar(Channel ch, long deliveryTag, String body)` (o `body` já como `String`); o `handleDelivery` apenas converte `byte[]`→`String` em UTF-8 e delega ao `processar(...)`, de modo que os testes de unidade exercitem `processar` com mocks de `Channel`
    - Injetar o `ObjectMapper` produzido pelo `JsonProducer` (tarefa 10.0) para desserializar o `ScoreEvent`
    - _Requisitos: 5.3, 6.1, 6.2, 6.3_

  - [x] 10.4 Escrever testes JUnit do publisher e do consumidor (R17, R19, R20, R23)
    - Instanciar o `RabbitPublisher` pelo construtor `@Inject(RabbitConnection, ObjectMapper)` passando um `new ObjectMapper()` e testar que, após salvar o placar, um `ScoreEvent` é publicado com os sete campos preenchidos (Mockito no publisher)
    - Testar que uma falha de publicação não propaga exceção (a resposta segue 200) e é apenas logada
    - Exercitar o consumidor pelo método `processar(Channel ch, long deliveryTag, String body)` (body como `String`): ao processar um evento válido, grava `placar:{id}` e chama `basicAck`
    - Testar que um payload malformado (ex.: `"{json-quebrado"`) gera `basicNack` sem requeue, sem entrar em loop
    - _Requisitos: 5.1, 5.2, 5.5, 6.1, 6.2, 6.3_

---

- [x] 11. Integração Redis (`RedisCache` com JedisPool próprio)
  - Criar `RedisCache.java` como `@ApplicationScoped`; ler `REDIS_HOST` e `REDIS_PORT` da configuração externa; criar o `JedisPool` no `@PostConstruct` e fechá-lo no `@PreDestroy` — **sem** producer CDI separado
  - Implementar `atualizarPlacar(ScoreEvent)` com chave `placar:{id}`, TTL 86400 s (24 h) e valor JSON `{placarA, placarB, timestamp}`; implementar `buscarPlacar(Long) → Optional<String>` e `pingOk()` (usado pelo health check)
  - _Requisitos: 6.1, 6.4, 8.1, 11.1_

  - [x] 11.1 Criar `RedisCache` com JedisPool próprio (sem `JedisPoolProducer`)
    - Usar try-with-resources para `Jedis jedis = pool.getResource()` em todas as operações
    - `atualizarPlacar` idempotente (`SETEX` total da chave `placar:{id}`) e **propaga** `JedisException` ao chamador em caso de Redis indisponível — de propósito, sem capturar (para o consumidor classificar como Falha_Recuperavel na tarefa 26.2)
    - `buscarPlacar(Long)` retorna `Optional.empty()` quando a chave não existe e **propaga** `JedisException` quando o Redis está inacessível — de propósito, sem capturar (consumido pelo Fallback_de_Leitura da Interface_Web na tarefa 27.2)
    - `pingOk()` retorna `true` somente quando o Redis responde `PONG` e, ao contrário de `atualizarPlacar`/`buscarPlacar`, **captura `JedisException` e retorna `false`** na falha (nunca lança), pois é usado pelo `/api/health`, que não pode virar 500
    - _Requisitos: 6.1, 6.4, 11.1, 13.9_

  - [x] 11.2 Escrever testes JUnit do `RedisCache` com Jedis mockado (R20, R21)
    - Testar que gravar o mesmo evento três vezes executa três `SETEX` idênticos na chave `placar:{id}` (estado final igual ao de uma única gravação — idempotência)
    - Testar que o valor gravado contém `placarA`, `placarB` e `timestamp` exatamente como no evento
    - Testar que `buscarPlacar` devolve `Optional.empty()` quando a chave não existe e propaga `JedisException` quando o Redis está fora
    - _Requisitos: 6.1, 6.4_

- [x] 12. Checkpoint — Testes unitários passando
  - Executar `mvn test` e garantir que todos os testes unitários e de UI (Camadas 1 e 3) passem
  - Verificar que os testes de `PartidaService`, `RedisCache`, `ScoreEvent`, publisher e consumidor cobrem os fluxos de sucesso e de erro
  - _Requisitos: 12.1, 12.4_

---

- [x] 13. Interface Web Apache Wicket
  - Criar `WicketApplication.java` estendendo `WebApplication`; configurar `getHomePage()` para `HomePage.class`; registrar encoding UTF-8 no `@Override init()`
  - Criar `HomePage.java` estendendo `WebPage`; compor `PartidaListPanel` e formulário de criação de partida (`Form<CriarPartidaRequest>` com `TextField` para times e `DateTimeField` para data/hora)
  - Criar `PartidaListPanel.java` estendendo `Panel`; implementar `ListView<PartidaViewModel>` com `setOutputMarkupId(true)`; adicionar `AjaxSelfUpdatingTimerBehavior(Duration.ofSeconds(5))` que consulta `RedisCache` para atualizar placares; fazer fallback para `PartidaService` quando a chave Redis não existir
  - Criar `PartidaRowPanel.java` estendendo `Panel`; exibir times, placar atual e status badge; exibir controles condicionalmente (placar input + botões de status) com base em `status`; armazenar valores anteriores em `IModel` da sessão para destacar campos alterados com classe CSS `score-updated` por 3 s via JavaScript
  - Criar templates HTML Wicket correspondentes em `src/main/webapp/WEB-INF/wicket/`
  - _Requisitos: 7.1–7.10, 8.1–8.5_

  - [x] 13.1 Criar `WicketApplication` e `HomePage` com formulário de criação
    - Formulário deve exibir mensagens de validação próximas aos campos inválidos sem submeter à API
    - _Requisitos: 7.1, 7.2, 7.3_

  - [x] 13.2 Criar `PartidaListPanel` com `AjaxSelfUpdatingTimerBehavior`
    - Polling a cada 5 s via `RedisCache`; fallback para `PartidaService` quando cache miss
    - Usar o tamanho de página padrão na carga da listagem; a navegação entre páginas entra na tarefa 39.2
    - _Requisitos: 7.4, 7.5, 8.1_

  - [x] 13.3 Criar `PartidaRowPanel` com controles condicionais e destaque visual
    - Controles de placar visíveis apenas para `EM_ANDAMENTO`; sem controles de edição para `ENCERRADO`
    - Caixa de confirmação antes de encerrar partida
    - Destaque CSS `score-updated` por 2–5 s quando placar muda
    - _Requisitos: 7.6, 7.7, 7.8, 7.9, 7.10, 8.2, 8.3, 8.4, 8.5_

  - [x] 13.4 Criar templates HTML Wicket e CSS/JS estáticos
    - Templates em `src/main/webapp/WEB-INF/wicket/` com atributos `wicket:id` correspondentes aos componentes Java
    - CSS em `src/main/webapp/static/` incluindo estilos para `.score-updated` e badges de status
    - _Requisitos: 7.4, 8.2_

---

- [x] 14. Testes de integração com Testcontainers e REST-assured
  - Criar `JogosResourceIT.java` com `@Testcontainers` e containers: `PostgreSQLContainer`, `GenericContainer` (Redis), `RabbitMQContainer`; inicializar Payara Micro com as envvars dos containers
  - Implementar testes de integração para todos os endpoints REST (sucesso e erro) dos Requisitos 1–4
  - Implementar teste do fluxo assíncrono completo: `PUT /jogos/{id}/placar` → PostgreSQL → RabbitMQ → Redis (aguardar até 5 s com Awaitility)
  - Marcar classe com `@Tag("integration")` para execução exclusiva na fase `verify` do Maven Failsafe
  - _Requisitos: 12.2, 12.3, 12.4, 12.5_

  - [x] 14.1 Implementar testes de integração para criação e consulta de partidas (`POST /jogos`, `GET /jogos`, `GET /jogos/{id}`)
    - Cobrir cenários de sucesso (HTTP 201/200) e erro (HTTP 400, 404) para Requisitos 1 e 2
    - _Requisitos: 1.1–1.4, 2.1–2.8, 12.2_

  - [x] 14.2 Implementar testes de integração para atualização de placar e fluxo assíncrono
    - Verificar fluxo completo: API → PostgreSQL → RabbitMQ → Redis em até 5 s (Awaitility)
    - Cobrir HTTP 200, 400, 404, 422 para `PUT /jogos/{id}/placar`
    - _Requisitos: 3.1–3.6, 12.3_

  - [x] 14.3 Implementar testes de integração para alteração de status
    - Cobrir todas as seis transições válidas e auto-transição inválida
    - _Requisitos: 4.1–4.9, 12.2_

  - [x] 14.4 Implementar teste de health check e validação do documento OpenAPI
    - Verificar que `GET /api/health` retorna 200 com todos os serviços ativos (caminho canônico)
    - Verificar que `GET /openapi` retorna documento com todos os endpoints documentados
    - _Requisitos: 9.1–9.4, 13.9_

- [x] 15. Checkpoint — Todos os testes passando
  - Executar `mvn test` (unitários + UI) e `mvn verify` (integração)
  - Garantir que todos os cenários dos Requisitos 1–6 estejam cobertos por ao menos um teste
  - Corrigir eventuais falhas antes de prosseguir para infraestrutura
  - _Requisitos: 12.1–12.6_

---

- [x] 16. Configuração Docker Compose
  - Criar `docker-compose.yml` na raiz do repositório declarando os serviços: `postgres:15`, `redis:7`, `rabbitmq:3.13-management` e `payara:6`
  - Configurar `depends_on` com healthchecks para PostgreSQL (`pg_isready`), Redis (`redis-cli ping`) e RabbitMQ (`rabbitmq-diagnostics -q ping`)
  - Declarar valores padrão para todas as variáveis de ambiente (credenciais, hosts, portas, exchange, fila, routing key) com credenciais **distintas** das padrões do fornecedor (não usar senhas em branco ou iguais ao nome do serviço)
  - Expor portas: `8080` (REST/Wicket), `4848` (Admin Payara), `15672` (RabbitMQ Management), `5432` (PostgreSQL)
  - _Requisitos: 11.3, 11.4, 11.5, 11.6, 11.7, 13.6_

  - [x] 16.1 Criar `docker-compose.yml` com todos os serviços e healthchecks
    - Credenciais devem ser diferentes dos padrões do fornecedor (Requisito 13.6)
    - As variáveis de backoff e paginação são acrescentadas na tarefa 41.1
    - _Requisitos: 11.3, 11.4, 11.5, 11.6, 13.6_

---

- [x] 17. README e documentação de execução
  - Criar `README.md` na raiz do repositório com:
    - Instruções de execução local (`docker-compose up`)
    - Lista completa de variáveis de ambiente com valores padrão
    - Exemplos de requisições cURL para todos os endpoints dos Requisitos 1–4 (criar partida, listar, buscar por ID, filtrar por status, filtrar por time, atualizar placar, alterar status)
    - Descrição da estrutura de pacotes e das camadas de teste
  - _Requisitos: 11.2, 11.5_

  - [x] 17.1 Criar `README.md` com instruções, variáveis de ambiente e exemplos cURL
    - Cobrir todos os endpoints dos Requisitos 1–4 com exemplos completos
    - As variáveis novas (backoff e paginação) entram na tarefa 41.2
    - _Requisitos: 11.2_

---

- [ ] 18. Checkpoint final — Validação completa do sistema
  - Executar `mvn test` e `mvn verify` com todos os testes passando
  - Verificar que `docker-compose up` inicializa todos os serviços sem erros
  - Confirmar que `/openapi-ui` exibe todos os endpoints documentados
  - Confirmar que `/api/health` retorna HTTP 200 com todos os serviços ativos
  - Garantir que todos os Requisitos 1–13 estão cobertos por tarefas implementadas
  - _Requisitos: 9.1–9.4, 11.6, 12.1–12.6, 13.9_

---

- [ ] 26. Resiliência do Consumidor ao Redis offline (requeue + Backoff)
  - Estender o `ScoreConsumer` (criado na tarefa 10.3) para distinguir Falha_Recuperavel (indisponibilidade do Redis — `JedisException`/`JedisConnectionException` propagada pelo `RedisCache`) de Falha_Irrecuperavel (payload malformado/erro de desserialização), aplicando requeue com Backoff crescente no primeiro caso e NACK sem requeue no segundo
  - Ler o atraso inicial e o teto do Backoff de `REDIS_RETRY_BACKOFF_INITIAL_MS` e `REDIS_RETRY_BACKOFF_MAX_MS` (sem valores hard-coded), declarando-as com valores padrão no `docker-compose.yml` e documentando-as no `README.md`
  - _Requisitos: 6.6, 6.7, 6.8, 16.1, 16.2, 16.3, 16.4, 16.5_

  - [ ] 26.1 Adicionar as variáveis de Backoff ao `docker-compose.yml` e ao `README.md`
    - Declarar `REDIS_RETRY_BACKOFF_INITIAL_MS` (padrão `500`) e `REDIS_RETRY_BACKOFF_MAX_MS` (padrão `10000`) no bloco `environment` do serviço `payara`, garantindo que `REDIS_RETRY_BACKOFF_MAX_MS` fique abaixo do `consumer_timeout` do RabbitMQ
    - Documentar ambas no `README.md` junto às demais variáveis de ambiente (lidas exclusivamente do ambiente, sem hard-code)
    - NOTA: alteração incremental sobre o `docker-compose.yml` (tarefa 16.1) e o `README.md` (tarefa 17.1)
    - _Requisitos: 16.2, 11.1, 11.2, 11.4_

  - [ ] 26.2 Estender o `ScoreConsumer` com requeue + Backoff para Falha_Recuperavel
    - Ler `backoffInicial`/`backoffMax` de `REDIS_RETRY_BACKOFF_INITIAL_MS`/`REDIS_RETRY_BACKOFF_MAX_MS` na inicialização
    - Em Falha_Recuperavel (`JedisException` do `RedisCache`): `basicNack(deliveryTag, multiple=false, requeue=true)` mantendo o Evento_de_Placar na Fila; aplicar atraso crescente (dobrando) com teto em `REDIS_RETRY_BACKOFF_MAX_MS` antes do reprocessamento, com tentativas ilimitadas até um `basicAck` bem-sucedido; log `WARN` por tentativa incluindo o `id` da partida e o atraso de Backoff aplicado
    - Em sucesso, resetar o atraso para o valor inicial
    - Em Falha_Irrecuperavel: `basicNack` sem requeue + log `ERROR` (mantém o comportamento da tarefa 10.3, evitando loop de reprocessamento)
    - Reforçar idempotência sob reprocessamento: reaproveitar a escrita idempotente do `RedisCache.atualizarPlacar` (`SETEX` total da chave `placar:{id}`) de modo que reprocessar o mesmo evento N vezes produza o mesmo estado
    - Manter a lógica de resiliência dentro do método `processar(Channel ch, long deliveryTag, String body)` extraído na tarefa 10.3 (mesma assinatura, `body` como `String`); o `handleDelivery` continua apenas convertendo `byte[]`→`String` UTF-8 e delegando
    - _Requisitos: 6.6, 6.7, 6.8, 16.1, 16.2, 16.3, 16.4, 16.5_

  - [ ]* 26.3 Escrever testes JUnit de não-descarte por Falha_Recuperavel (R22, R23)
    - Com mocks de `Channel` e `RedisCache`, exercitando `processar(Channel ch, long deliveryTag, String body)` (body como `String`): testar que uma `JedisConnectionException` resulta em `basicNack(deliveryTag, false, true)` (evento permanece na Fila) e log `WARN`
    - Testar que um payload malformado resulta em `basicNack(deliveryTag, false, false)` e log `ERROR`
    - Testar que o atraso aplicado cresce entre tentativas e nunca passa do teto configurado
    - _Requisitos: 6.3, 6.6, 6.7, 6.8, 16.1, 16.5_

  - [ ]* 26.4 Escrever testes JUnit de convergência do cache após reprocessamento (R21, R24)
    - Testar que, dada uma sequência de eventos represados para o mesmo `id`, reprocessar cada um mais de uma vez deixa a chave `placar:{id}` com os valores do evento mais recente
    - Testar que, após um sucesso, o atraso de Backoff volta ao valor inicial
    - _Requisitos: 6.4, 16.3, 16.4_

  - [ ]* 26.5 Escrever teste de integração de derrubar/religar o Redis (Testcontainers)
    - Derrubar o container Redis (`stop`), publicar uma atualização de placar via `PUT /jogos/{id}/placar` e verificar que a mensagem permanece na Fila enquanto o Redis está fora; ao religar (`start`), verificar que a chave `placar:{id}` converge para o placar mais recente
    - Marcar `@Tag("integration")`; usar Awaitility para aguardar a convergência após a religação
    - _Requisitos: 16.1, 16.2, 16.3, 12.3_

---

- [ ] 27. Resiliência da Interface Web (Fallback_de_Leitura + indicador de "não ao vivo")
  - Criar `PartidaViewModel` com a flag `aoVivo`, incluindo as fábricas `aoVivo(PartidaResponse, String jsonCache)` (placar lido do Redis) e `doBanco(PartidaResponse)` (placar do PostgreSQL, `aoVivo=false`)
  - Estender `PartidaListPanel`/`PartidaRowPanel` (tarefas 13.2/13.3) para reconstruir o `PartidaViewModel` a cada ciclo de polling: tentar o Redis via `RedisCache.buscarPlacar(id)` e, quando o polling falhar por `JedisException` OU `buscarPlacar(id)` retornar vazio, executar o Fallback_de_Leitura via `PartidaService` (PostgreSQL); exibir o indicador visual (`.nao-ao-vivo`) apenas em Modo_Degradado; ao voltar o Redis, remover o indicador e retomar o modo ao vivo sem reload
  - _Requisitos: 8.6, 8.7, 8.8, 16.6, 16.7, 16.8, 16.9_

  - [ ] 27.1 Criar `PartidaViewModel` com a flag `aoVivo`
    - Fábricas `aoVivo(...)` (placar do Redis, `aoVivo=true`) e `doBanco(...)` (placar do PostgreSQL, `aoVivo=false`); expor `isAoVivo()`
    - _Requisitos: 8.7, 16.7_

  - [ ] 27.2 Estender `PartidaListPanel`/`PartidaRowPanel` com Fallback_de_Leitura e Modo_Degradado
    - Em `montar(...)`, tentar `RedisCache.buscarPlacar(id)`: se a chave existir → `PartidaViewModel.aoVivo(...)`; se retornar vazio ou lançar `JedisException` → capturar a exceção, executar o Fallback_de_Leitura via `PartidaService` e retornar `PartidaViewModel.doBanco(...)` (log `WARN` na entrada em Modo_Degradado)
    - No `PartidaRowPanel`, exibir o indicador `.nao-ao-vivo` (rótulo "valor do banco — não ao vivo") com `setVisible(!vm.isAoVivo())`; o `AjaxSelfUpdatingTimerBehavior` mantém o polling em intervalos de 5 s mesmo em Modo_Degradado
    - Retomada automática: como o `PartidaViewModel` é reconstruído a cada ciclo, quando o Redis volta e a chave existe o próximo ciclo produz `aoVivo=true`, o indicador some e a linha retoma o modo ao vivo via AJAX sem recarregar a página e sem ação do usuário
    - _Requisitos: 8.6, 8.7, 8.8, 16.6, 16.7, 16.8, 16.9_

  - [ ] 27.3 Adicionar a classe CSS `.nao-ao-vivo` ao arquivo estático
    - Acrescentar `.nao-ao-vivo` ao CSS em `src/main/webapp/static/`, ao lado da já existente `.score-updated`
    - NOTA: alteração incremental sobre o CSS estático da tarefa 13.4
    - _Requisitos: 8.7, 16.7_

  - [ ]* 27.4 Escrever teste WicketTester do Fallback_de_Leitura (R41)
    - Com `RedisCache` mockado lançando `JedisConnectionException`: testar que o `PartidaViewModel` fica com `aoVivo=false` e exibe exatamente o `placarA`/`placarB` vindos do PostgreSQL
    - Repetir o caso com `buscarPlacar` devolvendo `Optional.empty()` (chave ausente)
    - _Requisitos: 8.6, 16.6_

  - [ ]* 27.5 Escrever teste WicketTester da presença do indicador "não ao vivo" (R42)
    - Testar os três cenários com `RedisCache` mockado: chave presente (indicador invisível), chave ausente (indicador visível) e `JedisException` (indicador visível)
    - Testar que, ao voltar a existir a chave num novo ciclo, o indicador desaparece
    - _Requisitos: 8.7, 8.8, 16.7, 16.9_

---

- [ ] 28. Checkpoint de resiliência ao Redis — Validação completa
  - Executar `mvn test` (unitários + UI) e `mvn verify` (integração de derrubar/religar o Redis) com todos os testes passando
  - Confirmar que nenhum Evento_de_Placar é descartado enquanto o Redis está offline (requeue + Backoff, tentativas ilimitadas até ACK) e que o cache converge ao religar
  - Confirmar que a Interface_Web degrada corretamente (Fallback_de_Leitura no PostgreSQL + indicador `.nao-ao-vivo`) e retoma o modo ao vivo automaticamente quando o Redis volta, sem recarregar a página
  - _Requisitos: 16.1–16.9, 12.1–12.6_

---

- [ ] 29. Reabertura de Partida Encerrada (Interface Web)
  - Estender a `PartidaRowPanel` (criada na tarefa 13.3) e seus templates HTML (tarefa 13.4) para expor o botão "Reabrir" no estado `ENCERRADO`; a Reabertura reutiliza integralmente `PartidaService.atualizarStatus` (transição `ENCERRADO` → `EM_ANDAMENTO` do Req 4.5), sem endpoint novo e sem duplicar a lógica de `atualizarStatus` ou `JogosResource` já cobertas
  - NOTA: alterações incrementais sobre `PartidaRowPanel` (13.3) e templates/CSS Wicket (13.4); nenhuma nova rota ou método de serviço é criado
  - _Requisitos: 7.11, 7.12, 8.3, 8.7, 17.1, 17.3_

  - [ ] 29.1 Adicionar o botão "Reabrir" à `PartidaRowPanel` com restauração sem reload
    - Exibir o botão "Reabrir" apenas para Partidas com `status = ENCERRADO` (Req 7.11)
    - Ao acionar, exibir caixa de confirmação (mesmo padrão do encerramento, Req 7.12) e, confirmado, disparar a transição `ENCERRADO` → `EM_ANDAMENTO` via `PartidaService.atualizarStatus`
    - Após o sucesso (`PartidaResponse` com `status = EM_ANDAMENTO`), restaurar via AJAX os controles de edição de `placarA`/`placarB` e o botão "Encerrar" e remover o botão "Reabrir", sem recarregar a página (Req 8.7)
    - Manter a proibição de edição de Placar enquanto `status = ENCERRADO`: o botão "Reabrir" é o único controle de escrita exposto no estado `ENCERRADO` e não afrouxa a imutabilidade (Req 3.3, 8.3)
    - _Requisitos: 7.11, 7.12, 8.3, 8.7, 17.1, 17.3_

  - [ ] 29.2 Ajustar templates HTML Wicket e CSS para o botão "Reabrir"
    - Adicionar o `wicket:id` do botão "Reabrir" ao template da `PartidaRowPanel` e o estilo correspondente no CSS estático (`src/main/webapp/static/`), ao lado dos demais controles de status
    - NOTA: alteração incremental sobre os templates/CSS da tarefa 13.4
    - _Requisitos: 7.11_

  - [ ]* 29.3 Escrever testes JUnit de editabilidade de placar após Reabertura (R43, R44)
    - Com `PartidaRepository` mockado: para uma partida `ENCERRADO`, testar que `atualizarPlacar` é rejeitado com 422 e os dados ficam inalterados
    - Em seguida, testar que `atualizarStatus(ENCERRADO → EM_ANDAMENTO)` é aceito e que um `atualizarPlacar` posterior com valores válidos persiste exatamente os valores informados
    - _Requisitos: 17.1, 17.3, 17.4, 3.1, 3.3, 4.5_

  - [ ]* 29.4 Escrever teste de integração de ponta a ponta da Reabertura (R45, Testcontainers)
    - Fluxo completo: `ENCERRADO` → reabrir (`PUT /jogos/{id}/status` com `EM_ANDAMENTO`) → editar placar (`PUT /jogos/{id}/placar`) → HTTP 200
    - Marcar `@Tag("integration")`
    - _Requisitos: 17.1, 17.3, 4.2_

---

- [ ] 30. Checkpoint de reabertura — Validação completa
  - Executar `mvn test` e `mvn verify` (integração da Reabertura) com todos os testes passando
  - Confirmar que o Placar volta a ser editável após a Reabertura (`ENCERRADO` → `EM_ANDAMENTO` → `PUT /jogos/{id}/placar` aceito)
  - Confirmar que `ENCERRADO` continua bloqueando a edição de Placar (HTTP 422) enquanto o status não muda
  - _Requisitos: 17.1–17.4, 12.1–12.6_

---

- [ ] 31. Validação de placar numérico na API (HTTP 400 sem 5xx)
  - Reforçar o tratamento de erro da API para que um valor não numérico em `placarA`/`placarB` no corpo de `PUT /jogos/{id}/placar` resulte em HTTP 400 (não 5xx), com mensagem descritiva indicando o campo inválido e sem expor stack trace nem mensagem de exceção interna, conforme os critérios 3.7, 13.4 e 13.5
  - Como o `AtualizarPlacarRequest` já tipa `placarA`/`placarB` como `int`, um valor textual falha na desserialização do corpo JSON; o caminho a tratar é a falha de parse/desserialização (não a validação de domínio), mapeando-a para 400 no `GlobalExceptionMapper`
  - NOTA: alteração incremental sobre o `GlobalExceptionMapper` existente (tarefa 5.1, já concluída). O tratamento genérico de JSON malformado e de campo de tipo incompatível é completado na tarefa 37.1
  - _Requisitos: 3.7, 13.4, 13.5_

  - [ ] 31.1 Reforçar o `GlobalExceptionMapper` para placar não numérico → HTTP 400
    - Mapear a falha de desserialização do corpo JSON quando `placarA`/`placarB` não são numéricos (`com.fasterxml.jackson.databind.exc.InvalidFormatException`) para HTTP 400 com mensagem que nomeia o campo inválido (obtido de `e.getPath()`), sem retornar 500 e sem expor nome de classe, `Exception`, `StackTrace` ou `at br.com.scoreboard`
    - Garantir que a rejeição ocorra antes de qualquer persistência, deixando o estado da Partida inalterado
    - _Requisitos: 3.7, 13.4, 13.5_

  - [ ]* 31.2 Escrever teste de integração de rejeição de placar não numérico (R12)
    - Enviar `PUT /jogos/{id}/placar` com `"placarA": "abc"` e verificar HTTP 400, mensagem que nomeia o campo, ausência de `Exception`/stack trace na resposta e placar inalterado no banco
    - Marcar `@Tag("integration")` (REST-assured/Testcontainers)
    - _Requisitos: 3.7, 13.5, 12.2_

---

- [ ] 32. Validação de placar numérico na Interface Web (Wicket)
  - Estender o `PartidaRowPanel` (criado na tarefa 13.3) para que o controle de edição de Placar aceite somente valores numéricos inteiros não negativos, bloqueando a submissão à API e exibindo mensagem de validação próxima ao campo quando o valor informado não for numérico, conforme os critérios 7.14 e 7.3
  - NOTA: alterações incrementais sobre a `PartidaRowPanel` (tarefa 13.3) e seus templates/CSS (tarefa 13.4); nenhuma nova rota ou método de serviço é criado
  - _Requisitos: 7.14, 7.3_

  - [ ] 32.1 Usar campo numérico com validação no `PartidaRowPanel`
    - No `PartidaRowPanel`, usar `NumberTextField<Integer>` com `RangeValidator.minimum(0)`; um valor não numérico gera erro de conversão exibido próximo ao campo via `FeedbackPanel` ("Informe um número inteiro maior ou igual a zero") e bloqueia a submissão à API, sem recarregar nem quebrar a página (mesmo padrão do Req 7.3)
    - Ajustar o template HTML e o CSS estático conforme necessário (`src/main/webapp/WEB-INF/wicket/` e `src/main/webapp/static/`)
    - _Requisitos: 7.14, 7.3_

  - [ ]* 32.2 Escrever teste WicketTester da validação do campo de placar (R48)
    - Usar `WicketTester` + `FormTester` para testar que digitar `"abc"` no campo de placar exibe a mensagem de validação e NÃO chama o serviço
    - Testar que `-1` também é rejeitado e que `0` é aceito (casos-limite)
    - _Requisitos: 7.14, 7.3_

---

- [ ] 33. Checkpoint — Validação de entrada de placar (API + UI)
  - Executar `mvn test`/`mvn verify` confirmando que a API retorna HTTP 400 para placar não numérico (sem stack trace) e que a Interface_Web bloqueia a submissão exibindo a mensagem de validação próxima ao campo
  - Confirmar que o dado permanece inalterado quando a API rejeita um placar não numérico (R12)
  - _Requisitos: 3.7, 7.14, 13.4, 13.5, 12.1–12.6_

---

- [ ] 34. Alinhamento ao design simplificado — exceção única e repositório paginado
  - Ajustes sobre código JÁ EXISTENTE: consolidar as duas exceções de regra de negócio em uma só e trocar os métodos de listagem do repositório por uma única consulta com filtro dinâmico e paginação
  - Nenhuma regra de negócio muda de comportamento observável: os mesmos códigos HTTP (400/422/404) e as mesmas mensagens continuam válidos
  - _Requisitos: 1.4, 3.3, 3.4, 4.7, 13.5, 2.1, 2.2, 2.6, 2.9, 2.12, 18.1, 18.8_

  - [ ] 34.1 Consolidar `StatusInvalidoException` e `PlacarInvalidoException` em `RegraNegocioException`
    - Criar `RegraNegocioException(int status, String mensagem)` no pacote `exception`, carregando o próprio código HTTP (400 ou 422)
    - Substituir no `PartidaService` os lançamentos das duas exceções antigas: 400 para "Os times da partida não podem ser iguais"; 422 para "Não é permitido alterar o placar de uma partida encerrada" e "A partida já se encontra no status informado"
    - Ajustar o `GlobalExceptionMapper` para mapear `RegraNegocioException` usando `e.getStatus()`, mantendo `PartidaNaoEncontradaException` → 404
    - Remover `StatusInvalidoException.java` e `PlacarInvalidoException.java` e atualizar todos os `import` afetados
    - _Requisitos: 1.4, 3.3, 3.4, 4.7, 13.5_

  - [ ]* 34.2 Ajustar/escrever testes JUnit da exceção consolidada
    - Atualizar os testes existentes do `PartidaService` (tarefas 7.2, 7.4, 7.6) para esperar `RegraNegocioException`
    - Testar que o status carregado é 400 para times iguais e 422 para placar em partida encerrada e para auto-transição de status
    - _Requisitos: 1.4, 3.3, 4.7_

  - [ ] 34.3 Consolidar a listagem do `PartidaRepository` em uma consulta única com paginação
    - Substituir `listarTodas()`, `listarPorStatus(...)` e `buscarPorNomeTime(...)` por um único método `listar(StatusPartida status, String time, int page, int size)`
    - Montar o JPQL com filtro dinâmico (`status` opcional; `time` opcional com `LOWER(timeA) LIKE :time OR LOWER(timeB) LIKE :time`), sempre com parâmetros nomeados, e `ORDER BY p.id`
    - Aplicar `setFirstResult(page * size)` e `setMaxResults(size)` para que nenhuma listagem varra a tabela inteira
    - Adicionar `pingOk()` (usado pelo `/api/health`): usar QUERY NATIVA `em.createNativeQuery("SELECT 1").getSingleResult()` (NÃO JPQL — `SELECT 1` não é JPQL válido), dentro de try/catch que retorna `false` em qualquer falha e nunca lança, para o health check nunca virar 500
    - Ajustar o `PartidaService` para chamar o novo método único
    - _Requisitos: 2.1, 2.2, 2.6, 2.9, 2.12, 18.1, 18.8_

  - [ ]* 34.4 Escrever testes JUnit da listagem consolidada (R4, R6, R46)
    - Testar que sem filtro a consulta monta o JPQL sem cláusulas extras e com `LIMIT`/`OFFSET` aplicados
    - Testar que com filtro por `status` toda partida retornada tem aquele status e que a paginação é aplicada depois do filtro
    - Testar que com filtro por `time` a busca é case-insensitive e encontra o termo em `timeA` e em `timeB`
    - Testar que `page=1` com `size=10` pede `setFirstResult(10)` e `setMaxResults(10)`
    - _Requisitos: 2.2, 2.6, 2.12, 18.1, 18.8_

---

- [ ] 35. Configuração externa via MicroProfile Config (`@ConfigProperty`)
  - Substituir todo uso de `System.getenv` (e de `Integer.parseInt`/`Long.parseLong` sobre variáveis de ambiente) por injeção `@Inject @ConfigProperty`, que entrega o valor já convertido para o tipo do campo e apenas uma vez, na criação do bean
  - MicroProfile Config já vem no Payara 6 — nenhuma dependência nova no `pom.xml`
  - Convenção de `defaultValue`: tamanhos, timeouts e backoff têm padrão; credenciais de infraestrutura **não têm**, para o deploy falhar na subida quando a variável faltar
  - _Requisitos: 11.1, 16.2, 18.5_

  - [ ] 35.1 Trocar `System.getenv` por `@ConfigProperty` em todos os componentes de configuração
    - `PartidaService`: `JOGOS_PAGE_SIZE_DEFAULT` (`"20"`) e `JOGOS_PAGE_SIZE_MAX` (`"100"`), ambos `int`
    - Gerenciador de conexão do RabbitMQ (tarefa 10.1): `RABBITMQ_HOST` (`"localhost"`), `RABBITMQ_PORT` (`"5672"`, `int`), `RABBITMQ_USER`/`RABBITMQ_PASS` (sem padrão), `RABBITMQ_EXCHANGE`, `RABBITMQ_QUEUE`, `RABBITMQ_ROUTING_KEY` (sem padrão)
    - `RabbitPublisher`: obter exchange e routing key do gerenciador de conexão, sem reler o ambiente
    - `ScoreConsumer`: `RABBITMQ_QUEUE`, `REDIS_RETRY_BACKOFF_INITIAL_MS` (`"500"`, `long`) e `REDIS_RETRY_BACKOFF_MAX_MS` (`"10000"`, `long`)
    - `RedisCache`: `REDIS_HOST` (`"localhost"`) e `REDIS_PORT` (`"6379"`, `int`)
    - Remover qualquer `parseInt`/`parseLong` de configuração e qualquer valor de conexão hard-coded que tenha sobrado
    - _Requisitos: 11.1, 16.2, 18.5_

  - [ ]* 35.2 Escrever testes JUnit de configuração (R53)
    - Testar que, com os campos de configuração preenchidos com o `defaultValue` documentado, o cálculo do tamanho de página e do backoff usa exatamente esses valores
    - Testar que uma credencial de infraestrutura sem valor informado (ex.: `RABBITMQ_PASS`) faz o bean falhar na inicialização (em vez de subir com um valor embutido)
    - Nos testes de unidade, atribuir os campos diretamente — sem mexer no ambiente do processo
    - _Requisitos: 11.1, 18.5_

---

- [ ] 36. Paginação na API (`page`/`size`) fim a fim
  - Receber `page` e `size` como `String` no `JogosResource` e convertê-los no próprio recurso, para que `page=abc` responda **400** e não o 404 que o JAX-RS devolveria com parâmetro `int`
  - Aplicar no `PartidaService` o padrão (`JOGOS_PAGE_SIZE_DEFAULT`) e o teto silencioso (`min(size, JOGOS_PAGE_SIZE_MAX)`), delegando a consulta paginada ao repositório
  - _Requisitos: 18.1–18.11, 2.9, 2.10, 2.11, 2.12_

  - [ ] 36.1 Receber `page`/`size` como `String` no `JogosResource` e converter com erro 400
    - Declarar `@QueryParam("page") String page` e `@QueryParam("size") String size`
    - Criar um método auxiliar de conversão: ausente ou em branco → valor padrão (`0` para `page`, `null` para `size`); texto não numérico ou número negativo → `RegraNegocioException(400, "Parâmetro 'page'/'size' inválido: informe um número inteiro maior ou igual a zero")`
    - Rejeitar antes de executar a consulta e sem expor stack trace nem nome de classe de exceção
    - Documentar `page` e `size` nas anotações OpenAPI do endpoint de listagem, incluindo o `@APIResponse` de 400
    - _Requisitos: 18.2, 18.4, 18.7, 18.10, 18.11, 2.11, 9.3_

  - [ ] 36.2 Aplicar padrão e teto de página no `PartidaService` e usar a consulta paginada
    - `size` nulo → usar `JOGOS_PAGE_SIZE_DEFAULT`; aplicar `Math.min(size, JOGOS_PAGE_SIZE_MAX)` como teto silencioso (200, sem erro)
    - Repassar `page` e o tamanho efetivo ao `PartidaRepository.listar(...)` (tarefa 34.3), de modo que o filtro seja aplicado antes da paginação no banco
    - Converter o `status` textual para o enum, retornando 400 quando o valor não pertencer a `StatusPartida`
    - _Requisitos: 18.1, 18.3, 18.5, 18.6, 18.8, 18.9, 2.1, 2.5, 2.9, 2.10, 2.12_

  - [ ]* 36.3 Escrever testes JUnit da conversão de parâmetros e do teto de página (R46, R47, R49)
    - Testar a conversão: `null` → padrão; `"0"` → 0; `"abc"` → 400 nomeando o parâmetro; `"-1"` → 400
    - Testar que `size` acima do teto é reduzido silenciosamente ao teto e que `size` ausente usa o padrão
    - Testar que o número de itens devolvidos nunca passa de `min(size, JOGOS_PAGE_SIZE_MAX)`
    - _Requisitos: 18.3, 18.4, 18.6, 18.7, 18.9, 18.10, 18.11_

---

- [ ] 37. `GlobalExceptionMapper` — falha de desserialização JSON → HTTP 400
  - Completar o mapeamento de corpo JSON não desserializável para 400 descritivo, cobrindo campo de tipo incompatível e JSON sintaticamente malformado em `POST /jogos`, `PUT /jogos/{id}/placar` e `PUT /jogos/{id}/status`
  - Nenhuma dessas situações pode resultar em 5xx nem expor stack trace ou nome de classe de exceção
  - NOTA: alteração incremental sobre o `GlobalExceptionMapper` (tarefas 5.1 e 31.1); como ele é `ExceptionMapper<Throwable>`, não é preciso criar provider novo
  - _Requisitos: 3.7, 3.9, 13.5_

  - [ ] 37.1 Mapear `InvalidFormatException`, `JsonProcessingException` e `ProcessingException` para 400
    - `InvalidFormatException` → 400 nomeando o campo (via `e.getPath()`): "Campo 'x' inválido: informe um valor do tipo esperado"
    - `JsonProcessingException`/`jakarta.ws.rs.ProcessingException` → 400 com "Corpo da requisição inválido: JSON mal formado ou campo de tipo incompatível"
    - Garantir que nenhuma dessas respostas contenha `Exception`, `StackTrace`, `at br.com.scoreboard` ou nome de classe Java, e que nada seja persistido
    - _Requisitos: 3.7, 3.9, 13.5_

  - [ ]* 37.2 Escrever testes de integração de corpo JSON inválido (R50)
    - Testar `POST /jogos` com JSON truncado (ex.: `{ "timeA": "Flamengo", `) → 400 descritivo, sem `Exception` na resposta
    - Testar `PUT /jogos/{id}/status` com `"status": 123` (tipo incompatível) → 400 nomeando o campo
    - Marcar `@Tag("integration")`
    - _Requisitos: 3.9, 13.5, 12.2_

---

- [ ] 38. Health check no caminho canônico `/api/health` (+ alias opcional `/health`)
  - Fixar `/api/health` como caminho canônico do health check: é o que aparece na documentação OpenAPI, no `README.md` e no healthcheck do `docker-compose.yml`
  - Oferecer `/health` apenas como alias opcional, redirecionando ou respondendo de forma idêntica a `/api/health`
  - _Requisitos: 13.9, 13.10_

  - [ ] 38.1 Consolidar `/api/health` como caminho canônico
    - Confirmar que `HealthResource` usa `@Path("/health")` sob `@ApplicationPath("/api")`, resultando em `/api/health`
    - Verificar PostgreSQL (`PartidaRepository.pingOk()`), RabbitMQ (`isOpen()`) e Redis (`RedisCache.pingOk()`); retornar 200 com `{"status":"UP"}` ou 503 com `{"status":"DOWN"}`
    - Garantir que o endpoint responda **sempre 200 ou 503 e NUNCA 500**: como os três checks (`repo.pingOk()`, `rabbit.isOpen()`, `redis.pingOk()`) retornam booleano e capturam internamente a falha da própria dependência (nenhum lança), a expressão `&&` sempre avalia e o `GlobalExceptionMapper` nunca é acionado neste caminho
    - Atualizar toda referência a `/health` no `docker-compose.yml` e nos testes para `/api/health`
    - _Requisitos: 13.9_

  - [ ] 38.2 Configurar o alias opcional `/health`
    - Declarar no `web.xml` o redirecionamento de `/health` para `/api/health`, mantendo o caminho canônico como o documentado
    - _Requisitos: 13.10_

  - [ ]* 38.3 Escrever teste de integração do health check (R40)
    - Testar que `GET /api/health` responde 200 com `status = UP`
    - Testar que, com uma dependência derrubada (container parado), a resposta é 503
    - Testar que o alias `/health` responde de forma equivalente
    - Marcar `@Tag("integration")`
    - _Requisitos: 13.9, 13.10, 12.2_

---

- [ ] 39. Interface Web — controles por status e navegação entre páginas
  - Confirmar que os controles de escrita são exibidos incondicionalmente na Partida do status apropriado (edição de placar e "Encerrar" para `EM_ANDAMENTO`; "Reabrir" para `ENCERRADO`); o formulário de criação fica sempre visível
  - Acrescentar navegação entre páginas na listagem, preservando o filtro por `status`, sem recarregar a página
  - NOTA: alterações incrementais sobre `HomePage` (13.1), `PartidaListPanel` (13.2), `PartidaRowPanel` (13.3) e templates/CSS (13.4)
  - _Requisitos: 7.4, 7.5, 7.6, 7.7, 7.9, 7.11, 7.14, 8.3, 8.7_

  - [ ] 39.1 Adicionar navegação entre páginas na listagem, preservando o filtro
    - No `PartidaListPanel`, manter os campos `pagina` (base 0) e `filtroStatus` e usar o tamanho de página injetado (`JOGOS_PAGE_SIZE_DEFAULT`)
    - Adicionar `AjaxLink` "anterior" (habilitado quando `pagina > 0`) e "próxima" (habilitado quando a página veio cheia), mais um rótulo indicando a página atual; ao navegar, chamar `target.add(this)` para re-renderizar só o painel
    - Ao mudar o filtro por `status`, voltar para a página 0; como a carga sempre usa `filtroStatus` e `pagina`, o filtro é preservado na navegação
    - _Requisitos: 7.4, 7.5, 7.14_

  - [ ]* 39.2 Escrever testes WicketTester de controles por status e navegação (R48, R51)
    - Testar que os controles aparecem conforme o status da partida: `EM_ANDAMENTO` mostra edição de placar e "Encerrar"; `ENCERRADO` mostra "Reabrir"
    - Testar que "anterior" começa desabilitado, que clicar em "próxima" (AJAX) avança a página e que o filtro por `status` continua aplicado após a navegação
    - _Requisitos: 7.6, 7.7, 7.9, 7.11, 7.14_

---

- [ ] 41. README e `docker-compose.yml` — variáveis novas e exemplos cURL
  - Completar a infraestrutura declarada nas tarefas 16.1 e 17.1 com as variáveis de backoff e paginação, e com exemplos cURL de listagem paginada e health check
  - _Requisitos: 11.2, 11.4, 11.9, 11.10_

  - [ ] 41.1 Acrescentar as variáveis novas ao `docker-compose.yml`
    - No bloco `environment` do serviço `payara`: `REDIS_RETRY_BACKOFF_INITIAL_MS` (`500`), `REDIS_RETRY_BACKOFF_MAX_MS` (`10000`), `JOGOS_PAGE_SIZE_DEFAULT` (`20`) e `JOGOS_PAGE_SIZE_MAX` (`100`)
    - Manter as credenciais de infraestrutura (PostgreSQL/Redis/RabbitMQ) substituíveis via `.env` ou linha de comando
    - Apontar o healthcheck da aplicação para `/api/health`
    - _Requisitos: 11.4, 11.7, 11.9, 13.6_

  - [ ] 41.2 Documentar as variáveis novas e os exemplos cURL no `README.md`
    - Documentar `REDIS_RETRY_BACKOFF_INITIAL_MS`, `REDIS_RETRY_BACKOFF_MAX_MS`, `JOGOS_PAGE_SIZE_DEFAULT` e `JOGOS_PAGE_SIZE_MAX` com seus valores padrão
    - Acrescentar exemplo cURL de `GET /api/health`
    - Documentar os parâmetros `page` e `size` nos exemplos de listagem
    - _Requisitos: 11.2, 11.10_

---

- [ ] 42. Testes de integração de validação de parâmetros
  - Fechar a cobertura de integração exigida pelo Requisito 12.2 para os endpoints e códigos que ainda não têm teste próprio
  - _Requisitos: 12.2, 12.5_

  - [ ]* 42.1 Escrever testes de integração de `page`/`size` inválidos (R49)
    - Testar `GET /jogos?page=abc` e `GET /jogos?size=abc` → 400 nomeando o parâmetro, nunca 404 nem 5xx, sem stack trace
    - Testar `page=-1` e `size=-5` → 400
    - Testar `size` acima do teto → 200 com no máximo `JOGOS_PAGE_SIZE_MAX` itens
    - Marcar `@Tag("integration")`
    - _Requisitos: 12.2_

---

- [ ] 43. Checkpoint final de alinhamento ao design
  - Executar `mvn test` (unitários + UI) e `mvn verify` (integração) com todos os testes passando
  - Confirmar que nenhuma classe lê configuração com `System.getenv` e que subir sem uma credencial de infraestrutura obrigatória falha de forma visível
  - Confirmar que toda listagem de partidas é paginada no banco e que `page`/`size` inválidos respondem 400
  - Confirmar que JSON malformado ou com campo de tipo incompatível responde 400 sem stack trace
  - Confirmar que `/api/health` é o caminho canônico documentado e usado pelo compose
  - Confirmar que a Interface_Web exibe os controles de escrita conforme o status e oferece navegação entre páginas
  - _Requisitos: 1–18 (escopo completo, exceto os requisitos de autenticação/autorização removidos), 12.1–12.6_

---

- [x] 44. Execução das migrações Flyway no startup da aplicação
  - Criar `FlywayMigrator.java` no pacote `config` como `@Startup @Singleton`, executando as migrações ANTES de qualquer acesso JPA: injetar o DataSource `java:app/jdbc/ScoreboardDS` via `@Resource`, montar o Flyway com `locations=classpath:db/migration` e `baselineOnMigrate=true`, e chamar `migrate()` no `@PostConstruct`
  - Falhar visivelmente na subida caso a migração falhe (não engolir exceção), para que um schema inconsistente não passe silenciosamente
  - Remover do `docker-compose.yml` a montagem de `./src/main/resources/db/migration` em `/docker-entrypoint-initdb.d` (contorno temporário introduzido na tarefa 16.1), passando a confiar nas migrações da aplicação; ajustar os comentários do compose de acordo
  - NOTA: garante que a migração `V1__create_jogos_table.sql` chegue ao banco em deploy real (não apenas em ambiente de teste)
  - _Requisitos: 10.1, 10.3, 11.1_

  - [x] 44.1 Criar `FlywayMigrator` e ajustar o `docker-compose.yml`
    - Verificar após o deploy que a tabela `flyway_schema_history` é criada e que `V1` fica registrada
    - Confirmar que uma segunda subida não reaplica as migrações (idempotência)
    - _Requisitos: 10.1, 10.3_

---

- [x] 45. Trilha de Auditoria (por origem)
  - Implementar a trilha de auditoria que registra a **Origem** de cada operação de escrita sobre uma Partida (`"api-rest"` para a API REST, `"painel-web"` para a Interface Web) — sem autenticação, login ou usuários; o campo `usuario` guarda a origem, não uma pessoa logada
  - Cada operação de escrita (`criar`, `atualizarPlacar`, `atualizarStatus`, `excluir`) grava exatamente um `RegistroAuditoria` na **mesma transação** da operação de negócio (JTA `REQUIRED` herdada), garantindo atomicidade
  - Expor a consulta aberta `GET /auditoria` com filtros opcionais `usuario`, `tipoAcao`, `dataInicio` e `dataFim`, ordenada por `timestamp` decrescente
  - NOTA: código já implementado no projeto; tarefas marcadas como concluídas
  - _Requisitos: 15.1–15.8_

  - [x] 45.1 Criar enum `TipoAcao` e entidade `RegistroAuditoria` (tabela `auditoria`), registrar em `persistence.xml`, e migração `V3__create_auditoria.sql`
    - `TipoAcao`: `CRIACAO_PARTIDA`, `ATUALIZACAO_PLACAR`, `ALTERACAO_STATUS`, `EXCLUSAO_PARTIDA`
    - `RegistroAuditoria` (`@Entity @Table(name="auditoria")`): `id` (BIGSERIAL), `usuario` VARCHAR(100) NOT NULL (a Origem), `tipoAcao` VARCHAR(30) NOT NULL, `entidadeAfetada` VARCHAR(50), `idEntidade` BIGINT, `valorAntes` TEXT, `valorDepois` TEXT, `timestamp` TIMESTAMP NOT NULL; índices em `usuario`, `tipo_acao` e `timestamp`
    - Adicionar `<class>br.com.scoreboard.domain.RegistroAuditoria</class>` ao `persistence.xml` e criar `db/migration/V3__create_auditoria.sql` (sem migração que remova a tabela)
    - _Requisitos: 15.1–15.4_

  - [x] 45.2 Criar `AuditoriaRepository` (`@ApplicationScoped`)
    - `salvar(RegistroAuditoria)` via `em.merge`
    - `consultar(usuario, tipoAcao, dataInicio, dataFim)` com filtro dinâmico (parâmetros nomeados) e `ORDER BY r.timestamp DESC`, aplicando apenas os filtros informados
    - _Requisitos: 15.5, 15.6_

  - [x] 45.3 Criar `AuditoriaService` (`@Stateless`) e integrar no `PartidaService`
    - `registrar(TipoAcao tipo, String origem, Long idEntidade, String antes, String depois)`: monta o `RegistroAuditoria` (grava a origem em `usuario`) e persiste via `AuditoriaRepository`, na transação herdada da operação de negócio (mesma TX, atomicidade)
    - `consultar(...)`: delega ao repositório e mapeia para `AuditoriaResponse`
    - Integrar as chamadas no `PartidaService`: `criar` → `CRIACAO_PARTIDA` (`valorAntes=null`, `valorDepois`=JSON da partida via `ObjectMapper`); `atualizarPlacar` → `ATUALIZACAO_PLACAR` (`"AxB"` antes/depois); `atualizarStatus` → `ALTERACAO_STATUS` (status anterior/novo); `excluir` → `EXCLUSAO_PARTIDA` (descrição anterior / `"EXCLUIDO"`)
    - _Requisitos: 15.1–15.4, 15.8_

  - [x] 45.4 Criar `AuditoriaResource` (`GET /auditoria`) e o DTO `AuditoriaResponse`
    - Endpoint **aberto** (sem autenticação) com filtros opcionais de query `usuario`, `tipoAcao`, `dataInicio`, `dataFim`; retorna a lista de `AuditoriaResponse` ordenada por `timestamp` decrescente
    - `AuditoriaResponse`: `id`, `usuario` (a Origem), `tipoAcao`, `entidadeAfetada`, `idEntidade`, `valorAntes`, `valorDepois`, `timestamp` (ISO-8601)
    - _Requisitos: 15.5, 15.6, 15.7_

  - [ ]* 45.5 (opcional) Testes de auditoria
    - Unitário: verificar que cada operação de escrita (`criar`/`atualizarPlacar`/`atualizarStatus`/`excluir`) chama `auditoria.registrar` com o `TipoAcao` correto e a Origem informada (R54–R57)
    - Integração: após operações de escrita, `GET /auditoria` retorna os registros em ordem decrescente de `timestamp` e respeita os filtros; confirmar o rollback conjunto em falha de gravação (R58, R59)
    - _Requisitos: 15.1–15.6, 12.2_

---

## Notes

- Tarefas marcadas com `*` são opcionais e podem ser puladas para um MVP mais rápido; elas cobrem o checklist de regras do design
- Todos os testes são **JUnit 5 por exemplo**: casos concretos de sucesso e de erro, com os limites conhecidos escritos à mão (placar 0, placar negativo, string vazia, string com 101 caracteres, `page=abc`, JSON malformado). Nenhum teste gera entradas aleatórias
- Cada tarefa de teste cita as regras do design que cobre (ex.: R9, R22) e mantém a linha `_Requisitos: ..._` para rastreabilidade ao requirements
- Ferramentas por camada: unidade → JUnit 5 + Mockito + AssertJ; UI → WicketTester; integração → Testcontainers + REST-assured
- As tarefas de integração são executadas exclusivamente via `mvn verify` (Maven Failsafe Plugin com `@Tag("integration")`); unidade e UI rodam em `mvn test`
- Nenhum teste de unidade ou de UI deve depender de recursos externos (banco, RabbitMQ, Redis) — use mocks
- Toda configuração de infraestrutura entra por `@ConfigProperty` (MicroProfile Config), lida do ambiente; credenciais de infraestrutura ficam **sem** `defaultValue` para o deploy falhar cedo se faltarem
- O ciclo de vida da conexão com o RabbitMQ fica no bean `@Startup @Singleton` de conexão (`RabbitConnection` no design; o arquivo existente `RabbitMQConnectionManager` cumpre esse papel); nunca criar `Connection` fora dele
- A API e a Interface Web são abertas: não há autenticação, autorização por papéis nem usuários. A trilha de auditoria faz parte do escopo e registra a **origem** de cada operação de escrita (`"api-rest"`/`"painel-web"`), não um usuário logado (seção 45)
- O caminho canônico do health check é `/api/health`; `/health` é apenas alias opcional

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["2.1"] },
    { "id": 2, "tasks": ["3.1", "4.1"] },
    { "id": 3, "tasks": ["4.2", "5.1"] },
    { "id": 4, "tasks": ["6.1"] },
    { "id": 5, "tasks": ["7.1"] },
    { "id": 6, "tasks": ["7.2", "7.3"] },
    { "id": 7, "tasks": ["7.4", "7.5"] },
    { "id": 8, "tasks": ["7.6", "7.7", "9.1"] },
    { "id": 9, "tasks": ["9.2", "9.3", "10.0", "10.1"] },
    { "id": 10, "tasks": ["9.4", "10.2"] },
    { "id": 11, "tasks": ["10.3", "11.1"] },
    { "id": 12, "tasks": ["10.4", "11.2"] },
    { "id": 13, "tasks": ["13.1"] },
    { "id": 14, "tasks": ["13.2"] },
    { "id": 15, "tasks": ["13.3", "13.4"] },
    { "id": 16, "tasks": ["14.1"] },
    { "id": 17, "tasks": ["14.2", "14.3"] },
    { "id": 18, "tasks": ["14.4", "16.1"] },
    { "id": 19, "tasks": ["17.1"] },
    { "id": 20, "tasks": ["26.1", "27.1", "27.3"] },
    { "id": 21, "tasks": ["26.2", "27.2"] },
    { "id": 22, "tasks": ["26.3", "26.4", "26.5", "27.4", "27.5"] },
    { "id": 23, "tasks": ["29.1", "29.3"] },
    { "id": 24, "tasks": ["29.2", "29.4"] },
    { "id": 25, "tasks": ["31.1", "32.1"] },
    { "id": 26, "tasks": ["31.2", "32.2"] },
    { "id": 27, "tasks": ["34.1", "34.3"] },
    { "id": 28, "tasks": ["34.2", "34.4", "35.1"] },
    { "id": 29, "tasks": ["35.2", "36.1"] },
    { "id": 30, "tasks": ["36.2", "37.1"] },
    { "id": 31, "tasks": ["36.3", "37.2", "38.1"] },
    { "id": 32, "tasks": ["38.2", "39.1"] },
    { "id": 33, "tasks": ["38.3", "39.2"] },
    { "id": 34, "tasks": ["41.1"] },
    { "id": 35, "tasks": ["41.2", "42.1"] },
    { "id": 36, "tasks": ["44.1"] },
    { "id": 37, "tasks": ["45.1"] },
    { "id": 38, "tasks": ["45.2"] },
    { "id": 39, "tasks": ["45.3", "45.4"] },
    { "id": 40, "tasks": ["45.5"] }
  ]
}
```
