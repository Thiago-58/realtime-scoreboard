# Requirements Document

## Introduction

Este documento descreve os requisitos funcionais e não funcionais do **Placar em Tempo Real** (*Realtime Scoreboard*), um sistema de gerenciamento e atualização de placares de jogos de futebol. O sistema expõe uma API REST documentada com OpenAPI/Swagger, persiste dados no PostgreSQL via JPA, publica eventos de atualização no RabbitMQ, armazena o estado atual dos placares no Redis e apresenta uma interface web reativa construída com Apache Wicket, executando no servidor de aplicação Payara/GlassFish (Jakarta EE).

---

## Glossary

- **Sistema**: o backend Jakarta EE do Placar em Tempo Real.
- **API**: a interface REST exposta pelo Sistema.
- **Operador**: usuário humano com permissão para criar partidas, alterar placares e gerenciar status.
- **Integrador**: desenvolvedor externo que consome a API para integrar o sistema com outras plataformas.
- **Partida**: entidade que representa um jogo de futebol, contendo os campos `id`, `timeA`, `timeB`, `placarA`, `placarB`, `status` e `dataHoraPartida`.
- **Placar**: par de valores inteiros não negativos (`placarA`, `placarB`) que representam o número de gols de cada time em uma Partida.
- **Status**: enumeração com os valores `EM_ANDAMENTO` e `ENCERRADO` que representa o estado corrente de uma Partida. `EM_ANDAMENTO` indica jogo em curso; `ENCERRADO` indica jogo finalizado. Nenhum estado é irreversível — o Operador pode alternar o status de qualquer Partida entre `EM_ANDAMENTO` e `ENCERRADO` conforme as transições definidas no Requisito 4.
- **Transição_de_Status**: alteração do campo `status` de uma Partida de um valor para outro. Apenas transições entre valores distintos do enumerado `Status` são permitidas.
- **Reabertura**: caso particular de Transição_de_Status em que uma Partida com `status = ENCERRADO` é transicionada de volta para `status = EM_ANDAMENTO` via `PUT /jogos/{id}/status`, com o objetivo de corrigir um Placar informado por engano. A Reabertura reutiliza a transição já definida no Requisito 4.2, não introduz endpoint novo e, após concluída, torna o Placar novamente editável via `PUT /jogos/{id}/placar` conforme o Requisito 3.1.
- **Evento_de_Placar**: mensagem publicada no RabbitMQ sempre que o Placar de uma Partida é atualizado, contendo os campos `id`, `timeA`, `timeB`, `placarA`, `placarB`, `status` e `timestamp` (formato ISO-8601).
- **Exchange**: ponto de entrada de mensagens no RabbitMQ para o qual o Sistema publica Eventos_de_Placar.
- **Fila**: fila RabbitMQ vinculada ao Exchange, da qual o Consumidor recebe Eventos_de_Placar.
- **Routing_Key**: chave de roteamento utilizada na publicação de Eventos_de_Placar no Exchange.
- **Consumidor**: componente do Sistema responsável por receber Eventos_de_Placar da Fila e atualizar o Cache.
- **Cache**: instância Redis que armazena o estado atual dos Placares de todas as Partidas com a chave `placar:{id}`.
- **Interface_Web**: aplicação Apache Wicket que permite ao Operador e ao usuário interagir com o Sistema via navegador.
- **Repositório**: camada de acesso a dados JPA do Sistema.
- **Registro_de_Auditoria**: entidade persistida no PostgreSQL (tabela `auditoria`) que documenta uma única operação de escrita sobre uma Partida, contendo `id`, `usuario` (a Origem da operação: `"api-rest"` ou `"painel-web"`), `tipoAcao`, `entidadeAfetada`, `idEntidade`, `valorAntes`, `valorDepois` e `timestamp` (ISO-8601).
- **Trilha_de_Auditoria**: conjunto ordenado de todos os Registros_de_Auditoria persistidos pelo Sistema, consultável via `GET /auditoria`.
- **Origem**: identificador textual de onde partiu a operação de escrita — `"api-rest"` para a API REST e `"painel-web"` para a Interface_Web. Substitui o conceito de usuário autenticado, que não existe neste sistema.
- **Documentação_API**: especificação OpenAPI gerada automaticamente pelo Sistema e acessível via Swagger UI.
- **Suite_de_Testes**: conjunto de testes automatizados JUnit que cobre as camadas de domínio, serviço e integração do Sistema.
- **Endpoint_de_Health_Check**: endpoint de verificação de saúde do Sistema cujo caminho efetivo canônico é `/api/health`, resultante do recurso JAX-RS de health check publicado sob o prefixo de aplicação `/api`. O caminho `/health` é aceito apenas como alias opcional que redireciona ou responde de forma idêntica a `/api/health`. Toda menção a health check neste documento refere-se ao caminho canônico `/api/health`.
- **Backoff**: estratégia de atraso crescente entre tentativas de reprocessamento de um Evento_de_Placar pelo Consumidor quando o Redis está indisponível. O atraso inicial e o atraso máximo são configurados exclusivamente pelas variáveis de ambiente `REDIS_RETRY_BACKOFF_INITIAL_MS` e `REDIS_RETRY_BACKOFF_MAX_MS`, respectivamente.
- **Falha_Recuperavel**: falha no processamento de um Evento_de_Placar causada exclusivamente por indisponibilidade do Redis (falha de conexão), passível de sucesso em uma nova tentativa após o Redis voltar a responder.
- **Falha_Irrecuperavel**: falha no processamento de um Evento_de_Placar que não será resolvida por nova tentativa, tais como payload malformado ou erro de desserialização.
- **Modo_Degradado**: estado da Interface_Web em que o último polling ao Redis falhou (erro de conexão) ou a chave `placar:{id}` não existe, e a Interface_Web passa a exibir o Placar obtido do PostgreSQL via PartidaService acompanhado de um indicador visual de "não ao vivo".
- **Fallback_de_Leitura**: leitura do Placar atual diretamente do PostgreSQL via PartidaService, realizada pela Interface_Web quando o Cache no Redis está indisponível ou a chave `placar:{id}` não existe.
- **PartidaService**: componente de serviço do Sistema que lê o Placar atual de uma Partida diretamente do PostgreSQL, utilizado pela Interface_Web como fonte de dados no Fallback_de_Leitura.
- **Pagina**: subconjunto contíguo dos resultados de uma listagem, identificado por um índice de página (`page`, base 0) e por um Tamanho_de_Pagina (`size`). A Pagina é obtida no banco via JPQL `setFirstResult`/`setMaxResults` (equivalente a `OFFSET`/`LIMIT`), de modo que a consulta nunca recupere a tabela inteira.
- **Tamanho_de_Pagina**: quantidade máxima de elementos que uma Pagina pode conter, informada pelo parâmetro de query `size`. Quando não informado, assume o Tamanho_de_Pagina_Padrao; quando excede o Teto_de_Pagina, é limitado silenciosamente ao Teto_de_Pagina.
- **Tamanho_de_Pagina_Padrao**: valor de `size` aplicado quando o parâmetro `size` não é informado na requisição. É obtido exclusivamente da variável de ambiente `JOGOS_PAGE_SIZE_DEFAULT`.
- **Teto_de_Pagina** (**Limite_Maximo_de_Listagem**): limite superior absoluto para o Tamanho_de_Pagina de qualquer listagem de Partidas, cujo propósito é impedir respostas ilimitadas que varram a tabela inteira e proteger o PostgreSQL de sobrecarga. É obtido exclusivamente da variável de ambiente `JOGOS_PAGE_SIZE_MAX`.

> **Variáveis de ambiente de paginação:** `JOGOS_PAGE_SIZE_DEFAULT` define o Tamanho_de_Pagina_Padrao e `JOGOS_PAGE_SIZE_MAX` define o Teto_de_Pagina. Ambas são lidas exclusivamente do ambiente, sem valores hard-coded no artefato deployável, em conformidade com o critério 11.1.

> **Variáveis de ambiente de resiliência:** `REDIS_RETRY_BACKOFF_INITIAL_MS` e `REDIS_RETRY_BACKOFF_MAX_MS` configuram o Backoff do Consumidor (Requisito 16.2). Ambas são lidas exclusivamente do ambiente, sem valores hard-coded no artefato deployável, em conformidade com o critério 11.1.

---

## Requirements

### Requisito 1: Criação de Partida

**User Story:** Como Operador, quero criar uma nova partida informando os times, data/hora e status inicial, para que o jogo passe a ser gerenciado pelo sistema.

#### Critérios de Aceitação

1. WHEN uma requisição `POST /jogos` é recebida com os campos `timeA`, `timeB` e `dataHoraPartida` válidos, THE Sistema SHALL persistir a Partida no PostgreSQL com `placarA = 0`, `placarB = 0`, `status = EM_ANDAMENTO` e um `id` gerado automaticamente, e retornar o código HTTP 201 e a representação JSON completa da Partida criada, incluindo o `id` gerado.
2. WHEN uma requisição `POST /jogos` é recebida com `timeA` ou `timeB` em branco ou ausente, THE Sistema SHALL retornar o código HTTP 400 e uma mensagem de erro descritiva indicando qual campo está inválido.
3. WHEN uma requisição `POST /jogos` é recebida com `dataHoraPartida` em formato inválido ou ausente, THE Sistema SHALL retornar o código HTTP 400 e uma mensagem de erro descritiva.
4. WHEN uma requisição `POST /jogos` é recebida com `timeA` igual a `timeB`, THE Sistema SHALL retornar o código HTTP 400 e a mensagem "Os times da partida não podem ser iguais".

#### Propriedades de Corretude

- **Invariante de criação**: para toda Partida criada com sucesso, `placarA == 0`, `placarB == 0` e `status == EM_ANDAMENTO`.
- **Round-trip de serialização**: para toda Partida criada com sucesso, serializar a resposta JSON e desserializar de volta SHALL produzir um objeto equivalente ao original (todos os campos iguais).

---

### Requisito 2: Consulta de Partidas

**User Story:** Como usuário, quero listar todas as partidas e filtrá-las por status ou por nome de time, para que eu possa acompanhar os jogos de meu interesse.

#### Critérios de Aceitação

1. WHEN uma requisição `GET /jogos` é recebida sem parâmetros, THE Sistema SHALL retornar o código HTTP 200 e a Pagina JSON de Partidas cadastradas correspondente ao índice de página 0 e ao Tamanho_de_Pagina_Padrao, aplicando paginação no banco conforme o Requisito 18, sem recuperar a tabela inteira.
2. WHEN uma requisição `GET /jogos?status=EM_ANDAMENTO` é recebida, THE Sistema SHALL retornar o código HTTP 200 e a Pagina JSON contendo somente Partidas cujo `status` seja `EM_ANDAMENTO`, calculada sobre o conjunto filtrado por `status` e limitada pelo Teto_de_Pagina conforme o Requisito 18.
3. WHEN uma requisição `GET /jogos?status=ENCERRADO` é recebida, THE Sistema SHALL retornar o código HTTP 200 e a Pagina JSON contendo somente Partidas cujo `status` seja `ENCERRADO`, calculada sobre o conjunto filtrado por `status` e limitada pelo Teto_de_Pagina conforme o Requisito 18.
4. WHEN uma requisição `GET /jogos?status=VALOR_INVALIDO` é recebida com um valor que não pertence ao enumerado `Status`, THE Sistema SHALL retornar o código HTTP 400 e uma mensagem de erro descritiva.
5. WHEN uma requisição `GET /jogos?time=NOME` é recebida, THE Sistema SHALL retornar o código HTTP 200 e a Pagina JSON contendo somente Partidas em que `timeA` ou `timeB` contenha o valor `NOME` como substring, sem distinção de maiúsculas e minúsculas, calculada sobre o conjunto filtrado por `time` e limitada pelo Teto_de_Pagina conforme o Requisito 18.
6. WHEN uma requisição `GET /jogos/{id}` é recebida com um `id` existente, THE Sistema SHALL retornar o código HTTP 200 e a representação JSON da Partida correspondente.
7. WHEN uma requisição `GET /jogos/{id}` é recebida com um `id` inexistente, THE Sistema SHALL retornar o código HTTP 404 e uma mensagem de erro descritiva.
8. WHEN uma requisição de listagem (`GET /jogos`, com ou sem filtro por `status` ou por `time`) é recebida com os parâmetros `page` e `size`, THE Sistema SHALL aplicar a paginação no banco via JPQL `setFirstResult`/`setMaxResults`, usando o Tamanho_de_Pagina_Padrao (`JOGOS_PAGE_SIZE_DEFAULT`) quando `size` não for informado, o índice de página 0 quando `page` não for informado, e limitando o Tamanho_de_Pagina efetivo ao Teto_de_Pagina (`JOGOS_PAGE_SIZE_MAX`) conforme o Requisito 18.
9. WHEN uma requisição de listagem é recebida com `size` maior que o Teto_de_Pagina (`JOGOS_PAGE_SIZE_MAX`), THE Sistema SHALL limitar silenciosamente o Tamanho_de_Pagina ao Teto_de_Pagina e retornar o código HTTP 200 e a Pagina correspondente, sem retornar erro.
10. IF uma requisição de listagem é recebida com `page` ou `size` inválido (valor negativo ou não numérico), THEN THE Sistema SHALL retornar o código HTTP 400 e uma mensagem de erro descritiva indicando o parâmetro inválido, sem executar a consulta, mantendo o padrão de validação de entrada do Requisito 13.4.
11. WHEN uma requisição de listagem com filtro por `status` ou por `time` é paginada, THE Sistema SHALL calcular a Pagina sobre o conjunto de Partidas que satisfazem o filtro, de modo que toda Partida retornada satisfaça o predicado do filtro e a paginação seja aplicada após a filtragem no banco.

#### Propriedades de Corretude

- **Propriedade metamórfica de filtro por status**: para qualquer `status` válido `S`, o tamanho do conjunto lógico completo (agregando todas as Paginas) retornado por `GET /jogos?status=S` SHALL ser menor ou igual ao tamanho do conjunto lógico completo retornado por `GET /jogos`.
- **Partição por status**: a união dos conjuntos lógicos completos (agregando todas as Paginas) retornados por `GET /jogos?status=EM_ANDAMENTO` e `GET /jogos?status=ENCERRADO` SHALL conter exatamente os mesmos elementos que o conjunto lógico completo de `GET /jogos`, sem duplicatas. Esta propriedade refere-se ao conjunto lógico completo, não a uma única Pagina.
- **Consistência de busca por time**: para qualquer nome `N`, toda Partida retornada por `GET /jogos?time=N` SHALL conter `N` como substring (case-insensitive) em `timeA` ou `timeB`, em qualquer Pagina retornada.
- **Limite superior de cardinalidade da Pagina**: para qualquer requisição de listagem com Tamanho_de_Pagina solicitado `size`, o número de elementos retornados na resposta SHALL ser menor ou igual a `min(size, Teto_de_Pagina)`.
- **Teto respeitado independentemente do total**: para qualquer `size` solicitado, o número de elementos retornados em uma única resposta de listagem SHALL nunca exceder o Teto_de_Pagina (`JOGOS_PAGE_SIZE_MAX`), independentemente do total de Partidas persistidas no PostgreSQL.
- **Preservação do predicado do filtro sob paginação**: para qualquer Pagina retornada por `GET /jogos?status=S`, toda Partida da Pagina SHALL ter `status = S`; para qualquer Pagina retornada por `GET /jogos?time=N`, toda Partida da Pagina SHALL conter `N` como substring (case-insensitive) em `timeA` ou `timeB`.

---

### Requisito 3: Atualização de Placar

**User Story:** Como Operador, quero atualizar o placar de uma partida em andamento, para que o resultado corrente seja refletido no sistema em tempo real.

> **Cobertura dos cenários BDD:**
> - *Cenário 1*: Atualizar placar de jogo em andamento → após persistência no PostgreSQL, o Sistema publica um Evento_de_Placar (Requisito 5), o Consumidor atualiza o Redis (Requisito 6) e a Interface_Web reflete o novo valor automaticamente (Requisito 8).
> - *Cenário 2*: Impedir alteração após encerramento → critério 3 abaixo.

#### Critérios de Aceitação

1. WHEN uma requisição `PUT /jogos/{id}/placar` é recebida com valores inteiros não negativos para `placarA` e `placarB` e a Partida possui `status = EM_ANDAMENTO`, THE Sistema SHALL persistir os novos valores de Placar no PostgreSQL e retornar o código HTTP 200 e a representação JSON atualizada da Partida.
2. WHERE o Redis está disponível, WHEN o Placar é persistido com sucesso, THE Sistema SHALL publicar um Evento_de_Placar no RabbitMQ conforme descrito no Requisito 5, de modo que o Cache no Redis reflita o novo Placar em até 5 segundos após a persistência. (O comportamento quando o Redis está indisponível é definido no critério 3.7 e no Requisito 16.)
3. WHEN uma requisição `PUT /jogos/{id}/placar` é recebida e a Partida possui `status = ENCERRADO`, THE Sistema SHALL retornar o código HTTP 422 e a mensagem "Não é permitido alterar o placar de uma partida encerrada" sem alterar nenhum dado.
4. WHEN uma requisição `PUT /jogos/{id}/placar` é recebida com valor negativo em `placarA` ou `placarB`, THE Sistema SHALL retornar o código HTTP 400 e uma mensagem de erro descritiva indicando qual campo está inválido.
5. WHEN uma requisição `PUT /jogos/{id}/placar` é recebida com `id` inexistente, THE Sistema SHALL retornar o código HTTP 404 e uma mensagem de erro descritiva.
6. WHEN uma requisição `PUT /jogos/{id}/placar` é recebida com um valor não numérico (não conversível para inteiro, ex.: `"placarA": "abc"`) em `placarA` ou `placarB`, THE Sistema SHALL retornar o código HTTP 400 e uma mensagem de erro descritiva indicando qual campo é inválido, sem alterar nenhum dado, sem expor stack trace nem mensagem de exceção interna (coerente com o critério 13.5).
7. IF o Redis está indisponível no momento em que o Placar é persistido com sucesso, THEN THE Sistema SHALL concluir a persistência no PostgreSQL e publicar o Evento_de_Placar no RabbitMQ normalmente, retornando o código HTTP 200, e a atualização do Cache SHALL ocorrer conforme o Requisito 16 quando o Redis voltar a responder, sem descarte do Evento_de_Placar e sem o limite de 5 segundos do critério 3.2.
8. IF o corpo JSON de uma requisição da API (`PUT /jogos/{id}/placar`, `POST /jogos` ou `PUT /jogos/{id}/status`) não pode ser desserializado para o tipo alvo (por exemplo, valor não numérico em `placarA` ou `placarB`, JSON sintaticamente malformado ou campo de tipo incompatível), THEN THE Sistema SHALL tratar explicitamente a falha de desserialização e retornar o código HTTP 400 e uma mensagem de erro descritiva indicando o campo ou o problema de formato, sem alterar nenhum dado, sem expor stack trace e sem expor o nome da classe de exceção interna (coerente com os critérios 3.6 e 13.5).

#### Propriedades de Corretude

- **Invariante de placar não negativo**: para toda Partida, após qualquer atualização bem-sucedida, `placarA >= 0` e `placarB >= 0`.
- **Invariante de imutabilidade de encerramento**: para toda Partida com `status = ENCERRADO`, o valor de `placarA` e `placarB` não SHALL ser alterado por uma requisição `PUT /jogos/{id}/placar`.
- **Rejeição de placar não numérico**: para qualquer valor não numérico (não conversível para inteiro) informado em `placarA` ou `placarB`, a API SHALL retornar HTTP 400 antes de qualquer persistência, sem alterar o estado da Partida.
- **Consistência PostgreSQL → Redis (Redis disponível)**: WHERE o Redis está disponível, para qualquer Placar atualizado com sucesso via API, o valor persistido no PostgreSQL e o valor armazenado no Cache (`placar:{id}`) SHALL ser idênticos após a conclusão do fluxo assíncrono. Enquanto o Redis está indisponível, esta propriedade não se aplica e a convergência do Cache é regida pela propriedade "Convergência do Cache após recuperação" do Requisito 16.
- **Mapeamento de falha de desserialização para HTTP 400**: para qualquer corpo JSON não desserializável enviado a um endpoint da API, a resposta SHALL ser HTTP 400 (nunca HTTP 500) com mensagem descritiva livre de stack trace e de nome de classe de exceção.

---

### Requisito 4: Alteração de Status da Partida

**User Story:** Como Operador, quero alterar o status de uma partida entre os estados possíveis, para que o sistema reflita corretamente o momento do jogo e impeça operações indevidas.

#### Critérios de Aceitação

1. WHEN uma requisição `PUT /jogos/{id}/status` é recebida com `status = ENCERRADO` e a Partida possui `status = EM_ANDAMENTO`, THE Sistema SHALL atualizar o `status` da Partida para `ENCERRADO` no PostgreSQL e retornar o código HTTP 200 e a representação JSON atualizada.
2. WHEN uma requisição `PUT /jogos/{id}/status` é recebida com `status = EM_ANDAMENTO` e a Partida possui `status = ENCERRADO`, THE Sistema SHALL atualizar o `status` da Partida para `EM_ANDAMENTO` no PostgreSQL e retornar o código HTTP 200 e a representação JSON atualizada.
3. WHEN uma requisição `PUT /jogos/{id}/status` é recebida com um `status` idêntico ao `status` atual da Partida, THE Sistema SHALL retornar o código HTTP 422 e a mensagem "A partida já se encontra no status informado".
4. WHEN uma requisição `PUT /jogos/{id}/status` é recebida com um valor que não pertence ao enumerado `Status`, THE Sistema SHALL retornar o código HTTP 400 e uma mensagem de erro descritiva.
5. WHEN uma requisição `PUT /jogos/{id}/status` é recebida com `id` inexistente, THE Sistema SHALL retornar o código HTTP 404 e uma mensagem de erro descritiva.

#### Propriedades de Corretude

- **Invariante de Transição_de_Status**: para toda Transição_de_Status bem-sucedida, o `status` persistido no PostgreSQL SHALL ser igual ao `status` informado na requisição.
- **Rejeição de auto-transição**: para qualquer `status` atual `S`, uma requisição `PUT /jogos/{id}/status` com `status = S` SHALL sempre retornar HTTP 422.

---

### Requisito 5: Publicação de Evento no RabbitMQ

**User Story:** Como arquiteto do sistema, quero que cada atualização de placar seja publicada como evento no RabbitMQ, para que outros componentes possam reagir de forma assíncrona.

#### Critérios de Aceitação

1. WHEN o Placar de uma Partida é persistido com sucesso no PostgreSQL, THE Sistema SHALL publicar um Evento_de_Placar no Exchange configurado pela variável de ambiente `RABBITMQ_EXCHANGE`, utilizando a Routing_Key configurada pela variável de ambiente `RABBITMQ_ROUTING_KEY`.
2. THE Sistema SHALL publicar Eventos_de_Placar em formato JSON contendo obrigatoriamente os campos `id`, `timeA`, `timeB`, `placarA`, `placarB`, `status` e `timestamp` no formato ISO-8601.
3. THE Consumidor SHALL receber Eventos_de_Placar da Fila configurada pela variável de ambiente `RABBITMQ_QUEUE`.
4. THE Sistema SHALL ler o nome do Exchange, o nome da Fila e a Routing_Key exclusivamente das variáveis de ambiente `RABBITMQ_EXCHANGE`, `RABBITMQ_QUEUE` e `RABBITMQ_ROUTING_KEY`, respectivamente.
5. IF a publicação no RabbitMQ falhar, THEN THE Sistema SHALL registrar o erro no log da aplicação com nível `ERROR`, incluindo o `id` da Partida e a causa do erro, e retornar o código HTTP 200 ao cliente sem interromper a persistência já realizada no PostgreSQL.

#### Propriedades de Corretude

- **Round-trip de Evento_de_Placar**: para todo Evento_de_Placar publicado, serializar o objeto para JSON e desserializar de volta SHALL produzir um objeto com todos os campos iguais ao original.
- **Completude de campos**: para todo Evento_de_Placar publicado, os sete campos obrigatórios (`id`, `timeA`, `timeB`, `placarA`, `placarB`, `status`, `timestamp`) SHALL estar presentes e não nulos.

---

### Requisito 6: Consumo de Evento e Atualização do Redis

**User Story:** Como arquiteto do sistema, quero que o Consumidor processe eventos do RabbitMQ e atualize o Redis, para que o estado atual dos placares esteja sempre disponível para leitura de baixa latência.

#### Critérios de Aceitação

1. WHEN um Evento_de_Placar é recebido pelo Consumidor, THE Consumidor SHALL atualizar o Cache no Redis com a chave `placar:{id}` e o valor JSON contendo `placarA`, `placarB` e o `timestamp` da última atualização em formato ISO-8601.
2. WHEN a atualização no Cache é concluída com sucesso, THE Consumidor SHALL confirmar (ACK) a mensagem no RabbitMQ.
3. IF o processamento do Evento_de_Placar falhar por Falha_Irrecuperavel, THEN THE Consumidor SHALL rejeitar (NACK) a mensagem sem requeue e registrar o erro no log da aplicação com nível `ERROR`, incluindo o `id` da Partida e a causa do erro. (O caso de indisponibilidade do Redis, Falha_Recuperavel, é tratado pelo critério 6.6 e pelo Requisito 16.)
4. THE Consumidor SHALL processar Eventos_de_Placar de forma idempotente: o processamento repetido do mesmo Evento_de_Placar SHALL produzir o mesmo estado no Cache sem efeitos colaterais adicionais, inclusive nos reprocessamentos decorrentes do requeue com Backoff descrito no critério 6.6.
5. WHILE o Consumidor está em execução, THE Consumidor SHALL processar cada Evento_de_Placar recebido em até 2 segundos após o recebimento da mensagem da Fila.
6. IF o processamento do Evento_de_Placar falhar por Falha_Recuperavel, THEN THE Consumidor SHALL rejeitar (NACK) a mensagem com requeue (`requeue=true`), mantendo o Evento_de_Placar na Fila, e SHALL aplicar Backoff crescente antes do reprocessamento, com tentativas ilimitadas até que o processamento seja concluído com sucesso e a mensagem seja confirmada (ACK).
7. THE Consumidor SHALL ler o atraso inicial e o atraso máximo do Backoff exclusivamente das variáveis de ambiente `REDIS_RETRY_BACKOFF_INITIAL_MS` e `REDIS_RETRY_BACKOFF_MAX_MS`, respectivamente, e SHALL limitar o atraso de cada tentativa ao valor de `REDIS_RETRY_BACKOFF_MAX_MS`.
8. WHILE a falha de processamento for exclusivamente uma Falha_Recuperavel, THE Consumidor SHALL registrar cada tentativa falha no log da aplicação com nível `WARN`, incluindo o `id` da Partida e o atraso de Backoff aplicado, sem descartar o Evento_de_Placar.

#### Propriedades de Corretude

- **Idempotência do Cache**: para qualquer Evento_de_Placar `E`, processar `E` uma vez ou `N` vezes (N > 1) SHALL resultar no mesmo estado da chave `placar:{id}` no Redis.
- **Consistência de leitura**: para qualquer chave `placar:{id}` no Cache após processamento bem-sucedido, o valor de `placarA` e `placarB` SHALL ser igual ao do Evento_de_Placar mais recente processado para aquele `id`.
- **Não-descarte por Falha_Recuperavel**: para qualquer Evento_de_Placar cuja falha de processamento seja exclusivamente uma Falha_Recuperavel, o Evento_de_Placar SHALL permanecer na Fila até um ACK bem-sucedido, sem ser descartado (ver Requisito 16).

---

### Requisito 7: Interface Web — Gerenciamento de Partidas

**User Story:** Como Operador, quero utilizar a interface web para criar, gerenciar e visualizar partidas, para que eu possa gerenciar os jogos sem precisar usar a API diretamente.

#### Critérios de Aceitação

1. THE Interface_Web SHALL exibir um formulário que permita ao Operador informar `timeA`, `timeB` e `dataHoraPartida` para criar uma nova Partida.
2. WHEN o Operador submete o formulário de criação com dados válidos, THE Interface_Web SHALL chamar a API e exibir a nova Partida na listagem sem recarregar a página inteira.
3. WHEN o Operador submete o formulário de criação com dados inválidos, THE Interface_Web SHALL exibir mensagens de validação próximas aos campos inválidos, sem submeter a requisição à API.
4. THE Interface_Web SHALL exibir uma Pagina de Partidas com seus respectivos times, placar atual e status, aplicando o Tamanho_de_Pagina_Padrao (`JOGOS_PAGE_SIZE_DEFAULT`) quando nenhum Tamanho_de_Pagina for escolhido pelo usuário e limitando o Tamanho_de_Pagina efetivo ao Teto_de_Pagina (`JOGOS_PAGE_SIZE_MAX`), conforme o Requisito 18.
5. THE Interface_Web SHALL permitir ao usuário filtrar a listagem de Partidas por `status`.
6. THE Interface_Web SHALL exibir, para cada Partida com `status = EM_ANDAMENTO`, um controle acionável para atualização dos campos `placarA` e `placarB`.
7. THE Interface_Web SHALL exibir, para cada Partida com `status = EM_ANDAMENTO`, um botão acionável para encerrar a Partida.
8. THE Interface_Web SHALL exibir, para cada Partida, um badge visual indicando o seu `status` corrente.
9. THE Interface_Web SHALL ocultar os controles de atualização de Placar para Partidas com `status = ENCERRADO`. Esta ocultação restringe-se aos controles de edição de Placar e não impede a exibição do botão "Reabrir" definido no critério 11.
10. WHEN o Operador aciona o encerramento de uma Partida, THE Interface_Web SHALL exibir uma caixa de confirmação antes de chamar a API.
11. THE Interface_Web SHALL exibir, para cada Partida com `status = ENCERRADO`, um botão "Reabrir" que, ao ser acionado e após confirmação, solicita à API a Transição_de_Status de `ENCERRADO` para `EM_ANDAMENTO` conforme o Requisito 4.2. (Detalhamento em Requisito 17.)
12. WHEN o Operador aciona o botão "Reabrir" de uma Partida, THE Interface_Web SHALL exibir uma caixa de confirmação antes de chamar a API, mantendo o mesmo padrão de confirmação do critério 10.
13. THE controle de edição de Placar da Interface_Web SHALL aceitar somente valores numéricos inteiros não negativos e, WHEN o Operador informar um valor não numérico (ex.: letra) nos campos `placarA` ou `placarB`, THE Interface_Web SHALL bloquear a submissão à API e exibir uma mensagem de validação clara próxima ao campo inválido (ex.: "Informe um número inteiro maior ou igual a zero"), sem recarregar nem quebrar a página, mantendo o mesmo padrão de validação de formulário do critério 3.
14. THE Interface_Web SHALL oferecer controles de navegação entre Paginas da listagem de Partidas (botões ou links de página anterior e próxima página, ou equivalente) que permitam ao usuário percorrer todas as Paginas da listagem corrente, preservando o filtro por `status` aplicado, sem recarregar a página inteira.

---

### Requisito 8: Atualização em Tempo Real na Interface Web

**User Story:** Como usuário, quero que a interface web atualize os placares automaticamente sem que eu precise recarregar a página, para que eu acompanhe os jogos em tempo real.

> **Cobertura do cenário BDD:**
> - *Cenário 3*: Exibição em tempo real → a Interface_Web consulta o Cache periodicamente e reflete alterações de Placar sem ação do usuário, conforme critérios 1 e 2 abaixo.

#### Critérios de Aceitação

1. WHILE uma Partida com `status = EM_ANDAMENTO` está sendo exibida, THE Interface_Web SHALL consultar o Cache no Redis em intervalos máximos de 5 segundos e refletir qualquer alteração de `placarA` ou `placarB` na tela sem recarregar a página inteira.
2. WHEN um Placar exibido na Interface_Web é atualizado automaticamente, THE Interface_Web SHALL destacar visualmente o campo alterado por um intervalo de 2 a 5 segundos antes de retornar ao estilo normal.
3. WHEN uma Partida exibida muda para `status = ENCERRADO`, THE Interface_Web SHALL remover os controles de edição de Placar e o botão de encerrar, mantendo disponível o botão "Reabrir" conforme o Requisito 7.11, sem recarregar a página inteira.
4. IF o polling ao Cache no Redis falhar por erro de conexão OU a chave `placar:{id}` não existir, THEN THE Interface_Web SHALL executar o Fallback_de_Leitura, obtendo o Placar atual do PostgreSQL via PartidaService e exibindo esse valor na tela. (O comportamento consolidado de resiliência é definido no Requisito 16.)
5. WHILE a Interface_Web está em Modo_Degradado, THE Interface_Web SHALL exibir um indicador visual (badge/rótulo com texto como "valor do banco — não ao vivo") deixando claro ao usuário que o Placar exibido provém do PostgreSQL e não do Cache em tempo real.
6. WHEN o Cache no Redis volta a responder e a chave `placar:{id}` está disponível, THE Interface_Web SHALL remover o indicador de Modo_Degradado e retomar o modo "ao vivo" automaticamente, mantendo o polling em intervalos máximos de 5 segundos, sem recarregar a página inteira e sem exigir ação do usuário.
7. WHEN uma Partida exibida muda de `status = ENCERRADO` para `status = EM_ANDAMENTO` (Reabertura bem-sucedida), THE Interface_Web SHALL restaurar os controles de edição de Placar e os botões de alteração de status conforme os Requisitos 7.6 e 7.7, sem recarregar a página inteira.

---

### Requisito 9: Documentação da API

**User Story:** Como Integrador, quero acessar a documentação interativa da API, para que eu possa entender e testar os endpoints sem ler o código-fonte.

#### Critérios de Aceitação

1. THE Documentação_API SHALL ser gerada automaticamente a partir das anotações OpenAPI presentes no código-fonte do Sistema sem geração manual de arquivos YAML ou JSON separados.
2. THE Documentação_API SHALL estar acessível via navegador na URL `/openapi-ui` do servidor de aplicação sem necessidade de autenticação.
3. THE Documentação_API SHALL descrever todos os endpoints da API, todos abertos e acessíveis sem autenticação, incluindo os parâmetros de entrada, os formatos de resposta e todos os códigos HTTP possíveis, abrangendo os endpoints definidos nos Requisitos 1 a 4 (`POST /jogos`, `GET /jogos`, `GET /jogos/{id}`, `PUT /jogos/{id}/placar`, `PUT /jogos/{id}/status`, incluindo os parâmetros `page` e `size` do Requisito 18), o endpoint aberto `GET /auditoria` (Requisito 15.5, com os filtros `usuario`, `tipoAcao`, `dataInicio` e `dataFim`) e o Endpoint_de_Health_Check `/api/health` (Requisito 13.9).
4. THE Documentação_API SHALL permitir ao usuário executar chamadas de teste diretamente pela interface do Swagger UI.

---

### Requisito 10: Persistência e Integridade dos Dados

**User Story:** Como arquiteto do sistema, quero garantir que os dados das partidas sejam persistidos de forma confiável no PostgreSQL, para que o histórico de jogos seja preservado mesmo após reinicializações do servidor.

#### Critérios de Aceitação

1. THE Repositório SHALL utilizar JPA com mapeamento objeto-relacional para persistir e recuperar entidades do tipo Partida no PostgreSQL.
2. THE Sistema SHALL aplicar constraints de banco de dados (`CHECK placarA >= 0` e `CHECK placarB >= 0`) para garantir que `placarA` e `placarB` sejam sempre maiores ou iguais a zero.
3. WHEN o servidor de aplicação é reiniciado, THE Sistema SHALL recuperar todas as Partidas previamente persistidas sem perda de dados.
4. THE Repositório SHALL executar operações de escrita dentro de transações JPA, garantindo atomicidade: em caso de falha durante a transação, nenhuma alteração parcial SHALL ser persistida no banco de dados.

#### Propriedades de Corretude

- **Persistência após reinício**: para toda Partida persistida antes de um reinício do servidor, `GET /jogos/{id}` após o reinício SHALL retornar os mesmos dados que retornava antes.

---

### Requisito 11: Infraestrutura, Configuração e Containerização

**User Story:** Como engenheiro de infraestrutura, quero que o ambiente seja configurável via variáveis de ambiente, descrito em um README e executável via Docker, para que qualquer desenvolvedor possa executar o sistema localmente com um único comando.

> **Consolidação:** Os aspectos de containerização anteriormente descritos em separado foram incorporados a este requisito para eliminar sobreposição. Este requisito é a fonte canônica de todos os critérios relacionados a Docker, variáveis de ambiente e documentação de execução.

#### Critérios de Aceitação

1. THE Sistema SHALL ler as configurações de conexão com PostgreSQL, RabbitMQ e Redis exclusivamente de variáveis de ambiente, sem valores de conexão hard-coded no artefato deployável.
2. THE Sistema SHALL incluir um arquivo `README.md` na raiz do repositório com: instruções de execução local, lista completa de variáveis de ambiente com seus valores padrão e exemplos de requisições cURL para todos os endpoints da API, abrangendo os endpoints dos Requisitos 1 a 4 e o Endpoint_de_Health_Check `/api/health`.
3. THE Sistema SHALL incluir um arquivo `docker-compose.yml` na raiz do repositório que declare os serviços PostgreSQL, Redis, RabbitMQ e Payara/GlassFish como containers, com as dependências entre serviços (`depends_on` com healthcheck) corretamente configuradas.
4. THE arquivo `docker-compose.yml` SHALL declarar valores padrão para todas as variáveis de ambiente necessárias à execução da aplicação, incluindo credenciais de banco de dados, host e porta do Redis, host, porta e credenciais do RabbitMQ, nome do Exchange, nome da Fila, Routing_Key, `REDIS_RETRY_BACKOFF_INITIAL_MS` e `REDIS_RETRY_BACKOFF_MAX_MS` (Requisito 16.2) e `JOGOS_PAGE_SIZE_DEFAULT` e `JOGOS_PAGE_SIZE_MAX` (Requisito 18.5).
5. THE arquivo `docker-compose.yml` SHALL expor as portas da API REST, da Interface_Web, do Swagger UI, do painel de administração do RabbitMQ e do PostgreSQL em comentários inline ou no `README.md`.
6. WHEN o comando `docker-compose up` é executado na raiz do repositório em uma máquina com Docker instalado, THE Sistema SHALL iniciar todos os serviços dependentes e disponibilizar a aplicação sem erros de inicialização.
7. WHEN as variáveis de ambiente definidas no `docker-compose.yml` são substituídas por valores customizados via arquivo `.env` ou via linha de comando, THE Sistema SHALL utilizar os valores customizados sem necessidade de recompilação do artefato.
8. THE Sistema SHALL seguir o modelo de branching GitFlow, mantendo as branches `main`, `develop`, `feature/*` e `hotfix/*` conforme convenção.
9. THE arquivo `README.md` SHALL documentar `REDIS_RETRY_BACKOFF_INITIAL_MS`, `REDIS_RETRY_BACKOFF_MAX_MS`, `JOGOS_PAGE_SIZE_DEFAULT` e `JOGOS_PAGE_SIZE_MAX` com seus valores padrão.

---

### Requisito 12: Testes Automatizados

**User Story:** Como desenvolvedor, quero que o sistema possua uma suite de testes automatizados cobrindo as camadas de domínio, serviço e integração, para que regressões sejam detectadas automaticamente e a qualidade do código seja garantida.

#### Critérios de Aceitação

1. THE Suite_de_Testes SHALL conter testes unitários JUnit para toda a lógica de negócio das camadas de domínio e serviço, sem dependência de recursos externos como JPA, RabbitMQ ou Redis.
2. THE Suite_de_Testes SHALL conter testes de integração que cubram todos os endpoints REST do Sistema — os endpoints definidos nos Requisitos 1 a 4 — verificando os códigos HTTP e os payloads de resposta para os cenários de sucesso e de erro.
3. WHEN o Placar de uma Partida é atualizado via `PUT /jogos/{id}/placar`, THE Suite_de_Testes SHALL verificar o fluxo assíncrono completo: persistência no PostgreSQL, publicação do Evento_de_Placar no RabbitMQ e atualização do Cache no Redis.
4. THE Suite_de_Testes SHALL organizar os testes por camada, de modo que os testes unitários de domínio possam ser executados de forma independente dos testes de integração com infraestrutura.
5. WHEN um novo endpoint REST é adicionado ao Sistema, THE Suite_de_Testes SHALL incluir ao menos um teste de integração cobrindo o cenário de sucesso do novo endpoint.
6. IF um teste da camada de domínio depender de qualquer recurso externo (banco de dados, mensageria ou cache), THEN THE Suite_de_Testes SHALL ser considerada não-conforme e o desenvolvedor SHALL remover a dependência externa do teste.

---

### Requisito 13: Requisitos Não Funcionais

**User Story:** Como arquiteto do sistema, quero definir requisitos de desempenho, segurança e observabilidade, para que o sistema seja confiável, monitorável e seguro em produção.

#### Critérios de Aceitação — Desempenho

1. WHILE o sistema está sob carga normal (até 50 requisições simultâneas), THE API SHALL responder às requisições de leitura (`GET /jogos`, `GET /jogos/{id}`) em até 300 ms (percentil 95).
2. WHILE o sistema está sob carga normal, THE API SHALL responder às requisições de escrita (`POST /jogos`, `PUT /jogos/{id}/placar`, `PUT /jogos/{id}/status`) em até 500 ms (percentil 95).
3. WHEN um Evento_de_Placar é publicado no RabbitMQ, THE Consumidor SHALL atualizar o Cache no Redis em até 2 segundos após a publicação, de modo que a Interface_Web reflita a mudança em até 5 segundos após a atualização do Placar via API.

> **Nota sobre paginação e desempenho:** A paginação obrigatória com Teto_de_Pagina definida no Requisito 18 é o mecanismo que impede respostas ilimitadas nas listagens (`GET /jogos` e filtros), limitando o número de registros varridos e retornados por consulta e, assim, contribuindo para manter os tempos de resposta de leitura dentro dos limites do critério 13.1. Esta nota não altera os valores de percentil 95 acima.

#### Critérios de Aceitação — Segurança

4. THE API SHALL validar e sanitizar todos os campos de entrada antes de qualquer operação de persistência, rejeitando entradas que excedam 100 caracteres nos campos `timeA` e `timeB` e retornando HTTP 400 para campos de tipo inválido, incluindo valores não numéricos em `placarA` ou `placarB` conforme o critério 3.7.
5. THE Sistema SHALL retornar mensagens de erro que descrevam o problema de forma útil ao cliente sem expor detalhes de implementação interna, stack traces ou mensagens de exceção do servidor.
6. THE arquivo `docker-compose.yml` SHALL definir credenciais para PostgreSQL, Redis e RabbitMQ distintas das credenciais padrão do fornecedor (não SHALL utilizar senhas em branco ou iguais ao nome do serviço).

#### Critérios de Aceitação — Observabilidade

7. THE Sistema SHALL registrar no log da aplicação, com nível `INFO`, o início e o fim bem-sucedido de cada operação de criação, atualização de Placar e alteração de Status, incluindo o `id` da Partida e a duração da operação em milissegundos.
8. THE Sistema SHALL registrar no log da aplicação, com nível `ERROR`, todas as falhas de comunicação com PostgreSQL, RabbitMQ e Redis, incluindo a causa do erro e o contexto da operação que falhou.
9. THE Sistema SHALL expor o Endpoint_de_Health_Check no caminho efetivo canônico `/api/health` (recurso JAX-RS de health check publicado sob o prefixo de aplicação `/api`) que retorne o código HTTP 200 quando todos os serviços dependentes (PostgreSQL, RabbitMQ, Redis) estiverem acessíveis, e o código HTTP 503 quando qualquer serviço dependente estiver indisponível.
10. WHERE o alias `/health` é configurado, THE Sistema SHALL responder em `/health` de forma idêntica a `/api/health`, mantendo `/api/health` como o caminho canônico documentado na Documentação_API e no `README.md`.

#### Propriedades de Corretude

- **Sanitização de entrada**: para qualquer valor de `timeA` ou `timeB` com comprimento maior que 100 caracteres, THE Sistema SHALL retornar HTTP 400.
- **Opacidade de erros**: para qualquer requisição que resulte em erro HTTP 5xx, a resposta SHALL não conter stack trace Java nem nome de classe de exceção interna.

---

### Requisito 15: Trilha de Auditoria

**User Story:** Como Operador/Integrador, quero que toda operação de escrita sobre uma partida seja registrada em uma trilha de auditoria com sua origem, para que as alterações sejam rastreáveis e auditáveis.

> **Nota sobre origem, não usuário:** a auditoria **não identifica um usuário autenticado** — o Sistema não possui login, autenticação, papéis nem usuários. O campo `usuario` do Registro_de_Auditoria registra a **Origem** da operação de escrita: `"api-rest"` para chamadas da API REST e `"painel-web"` para ações realizadas pela Interface_Web (Apache Wicket).

#### Critérios de Aceitação

1. WHEN uma Partida é criada com sucesso, THE Sistema SHALL gravar um Registro_de_Auditoria do tipo `CRIACAO_PARTIDA` com a Origem, o `id` da Partida, `valorAntes` nulo e `valorDepois` com o estado da Partida criada, na mesma transação da criação.
2. WHEN o Placar de uma Partida é atualizado com sucesso, THE Sistema SHALL gravar um Registro_de_Auditoria do tipo `ATUALIZACAO_PLACAR` com a Origem, o `id`, o placar anterior (`valorAntes`, no formato `"AxB"`) e o novo placar (`valorDepois`, no formato `"AxB"`), na mesma transação da atualização.
3. WHEN o status de uma Partida é alterado com sucesso, THE Sistema SHALL gravar um Registro_de_Auditoria do tipo `ALTERACAO_STATUS` com a Origem, o `id`, o status anterior (`valorAntes`) e o novo status (`valorDepois`), na mesma transação da alteração.
4. WHEN uma Partida é excluída com sucesso, THE Sistema SHALL gravar um Registro_de_Auditoria do tipo `EXCLUSAO_PARTIDA` com a Origem, o `id`, uma descrição do estado anterior (`valorAntes`) e `valorDepois` igual a `"EXCLUIDO"`, na mesma transação da exclusão.
5. WHEN uma requisição `GET /auditoria` é recebida, THE Sistema SHALL retornar o código HTTP 200 e a lista de Registros_de_Auditoria ordenada do mais recente para o mais antigo (por `timestamp` decrescente), aceitando os filtros opcionais de query `usuario`, `tipoAcao`, `dataInicio` e `dataFim`.
6. WHEN uma requisição `GET /auditoria` é recebida com um ou mais dos filtros (`usuario`, `tipoAcao`, `dataInicio`, `dataFim`), THE Sistema SHALL aplicar apenas os filtros informados sobre a Trilha_de_Auditoria, ignorando os parâmetros não informados.
7. THE endpoint `GET /auditoria` SHALL ser aberto, acessível sem autenticação, coerente com o restante da API.
8. IF a gravação do Registro_de_Auditoria falhar durante uma operação de escrita, THEN THE Sistema SHALL sofrer rollback da operação de negócio junto com a auditoria (mesma transação), preservando a atomicidade — nem a operação de negócio nem o registro de auditoria SHALL ser persistidos parcialmente.

#### Propriedades de Corretude

- **Um registro por operação de escrita**: toda operação de escrita bem-sucedida (criar, atualizar placar, alterar status, excluir) SHALL produzir exatamente um Registro_de_Auditoria correspondente com o `TipoAcao` correto.
- **Ordenação decrescente da consulta**: a consulta `GET /auditoria` SHALL retornar os Registros_de_Auditoria em ordem decrescente de `timestamp` (mais recente primeiro).
- **Atomicidade da auditoria**: a operação de negócio e seu Registro_de_Auditoria SHALL compartilhar a mesma transação, de modo que a falha de qualquer um provoque o rollback de ambos.

---

### Requisito 16: Resiliência à Indisponibilidade do Redis

**User Story:** Como usuário e como arquiteto do sistema, quero que o sistema continue operando de forma correta e observável quando o Redis estiver indisponível, para que nenhuma atualização de placar seja perdida e para que a interface web continue exibindo o placar da fonte da verdade (PostgreSQL) enquanto o cache em tempo real não estiver disponível.

> **Reconciliação com o Requisito 6:** Este requisito consolida o tratamento de Falha_Recuperavel (indisponibilidade do Redis). O critério 6.3 trata apenas de Falha_Irrecuperavel (NACK sem requeue + log `ERROR`); o requeue com Backoff e as tentativas ilimitadas estão definidos nos critérios 6.6, 6.7 e 6.8 e são detalhados aqui. O Redis permanece cache de baixa latência e o PostgreSQL permanece a fonte da verdade.

#### Critérios de Aceitação — Consumidor

1. IF o Consumidor falhar ao atualizar o Cache no Redis por indisponibilidade do Redis (Falha_Recuperavel), THEN THE Consumidor SHALL rejeitar (NACK) a mensagem com requeue (`requeue=true`), mantendo o Evento_de_Placar na Fila, e SHALL aplicar Backoff crescente antes do reprocessamento, com tentativas ilimitadas, sem descartar o Evento_de_Placar.
2. THE Consumidor SHALL derivar o atraso de Backoff exclusivamente das variáveis de ambiente `REDIS_RETRY_BACKOFF_INITIAL_MS` (atraso inicial) e `REDIS_RETRY_BACKOFF_MAX_MS` (atraso máximo), sem valores hard-coded no artefato deployável.
3. WHEN o Redis volta a responder após um período de indisponibilidade, THE Consumidor SHALL processar os Eventos_de_Placar represados na Fila e atualizar o Cache com a chave `placar:{id}`, confirmando (ACK) cada mensagem processada com sucesso.
4. THE Consumidor SHALL processar os Eventos_de_Placar represados de forma idempotente, de modo que o reprocessamento decorrente do requeue com Backoff produza o mesmo estado final no Cache que uma única execução bem-sucedida.
5. IF a falha de processamento for uma Falha_Irrecuperavel (por exemplo, payload malformado ou erro de desserialização), THEN THE Consumidor SHALL rejeitar (NACK) a mensagem sem requeue e registrar o erro no log da aplicação com nível `ERROR`, conforme o critério 6.3, evitando loop de reprocessamento.

#### Critérios de Aceitação — Interface Web

6. IF o polling ao Cache no Redis falhar por erro de conexão OU a chave `placar:{id}` não existir, THEN THE Interface_Web SHALL obter o Placar atual do PostgreSQL via PartidaService (Fallback_de_Leitura) e exibir esse valor na tela.
7. WHILE a Interface_Web está em Modo_Degradado, THE Interface_Web SHALL exibir um indicador visual (badge/rótulo com texto como "valor do banco — não ao vivo" ou "atualização em tempo real indisponível") indicando que o Placar exibido provém do PostgreSQL e não do Cache.
8. WHILE a Interface_Web está em Modo_Degradado, THE Interface_Web SHALL continuar consultando o Cache no Redis em intervalos máximos de 5 segundos.
9. WHEN o Cache no Redis volta a responder e a chave `placar:{id}` está disponível, THE Interface_Web SHALL remover o indicador de Modo_Degradado e retomar o modo "ao vivo" automaticamente, sem recarregar a página inteira e sem exigir ação do usuário.

#### Propriedades de Corretude

- **Não-descarte por indisponibilidade do Redis**: enquanto a falha de processamento for exclusivamente uma Falha_Recuperavel, nenhum Evento_de_Placar SHALL ser descartado; o Evento_de_Placar SHALL permanecer na Fila até um ACK bem-sucedido.
- **Convergência do Cache após recuperação**: após o Redis se recuperar e os Eventos_de_Placar represados serem processados, o valor final da chave `placar:{id}` SHALL convergir para o do Evento_de_Placar mais recente represado para aquele `id`, apoiado pela idempotência do processamento.
- **Fidelidade do Fallback_de_Leitura**: enquanto o Redis está indisponível, a leitura exibida pela Interface_Web para uma Partida SHALL refletir exatamente o valor de Placar persistido no PostgreSQL para aquela Partida.
- **Presença do indicador de Modo_Degradado (se e somente se)**: o indicador visual de "não ao vivo" SHALL estar presente se, e somente se, o último polling ao Redis falhou ou a chave `placar:{id}` não existe.

---

### Requisito 17: Reabertura de Partida Encerrada

**User Story:** Como Operador, quero reabrir uma partida encerrada para corrigir um placar informado por engano, para que o resultado fique correto.

> **Reconciliação com os Requisitos 3, 4, 7 e 8:** A Reabertura não introduz endpoint novo. Ela reutiliza a Transição_de_Status de `ENCERRADO` para `EM_ANDAMENTO` já definida no Requisito 4.2. A proibição de editar o Placar enquanto a Partida está `ENCERRADO` (Requisito 3.3) permanece inalterada; a edição do Placar só volta a ser aceita após a Partida retornar a `EM_ANDAMENTO`.

#### Critérios de Aceitação

1. WHEN uma requisição `PUT /jogos/{id}/status` com `status = EM_ANDAMENTO` é recebida e a Partida possui `status = ENCERRADO`, THE Sistema SHALL executar a Reabertura, atualizando o `status` da Partida para `EM_ANDAMENTO` no PostgreSQL e retornando o código HTTP 200 e a representação JSON atualizada, conforme o Requisito 4.2.
2. WHEN uma Reabertura é concluída com sucesso, THE Sistema SHALL tornar o Placar da Partida novamente editável, de modo que uma requisição subsequente `PUT /jogos/{id}/placar` com valores inteiros não negativos seja aceita conforme o Requisito 3.1.
3. IF uma requisição `PUT /jogos/{id}/placar` é recebida enquanto a Partida possui `status = ENCERRADO`, THEN THE Sistema SHALL retornar o código HTTP 422 e a mensagem "Não é permitido alterar o placar de uma partida encerrada" sem alterar nenhum dado, conforme o Requisito 3.3.

#### Propriedades de Corretude

- **Placar editável após Reabertura**: para toda Partida que sofreu uma Reabertura bem-sucedida (`ENCERRADO` → `EM_ANDAMENTO`), uma requisição subsequente `PUT /jogos/{id}/placar` com valores válidos SHALL ser aceita (HTTP 200).
- **Imutabilidade do Placar enquanto ENCERRADO**: para toda Partida com `status = ENCERRADO`, uma requisição `PUT /jogos/{id}/placar` SHALL continuar retornando HTTP 422 sem alterar nenhum dado.

---

### Requisito 18: Paginação e Proteção contra Sobrecarga de Listagens

**User Story:** Como arquiteto do sistema, quero que as listagens de Partidas sejam paginadas com um Teto_de_Pagina máximo de registros por resposta, para proteger o PostgreSQL de sobrecarga e evitar respostas ilimitadas que varram a tabela inteira.

> **Reconciliação com os Requisitos 2 e 13:** Este requisito é a fonte canônica da política de paginação das listagens de Partidas. Os critérios do Requisito 2 (2.1 a 2.3, 2.5 e 2.8 a 2.11) apontam para este requisito quanto aos detalhes de paginação; o Requisito 13 referencia a paginação como mecanismo de proteção dos tempos de resposta. Esta política aplica-se às listagens `GET /jogos` e a suas variantes com filtro por `status` e por `time`; não se aplica a `GET /jogos/{id}` (busca única).

#### Critérios de Aceitação

1. THE Sistema SHALL aplicar paginação obrigatória a toda listagem de Partidas (`GET /jogos`, `GET /jogos?status=...` e `GET /jogos?time=...`) por meio de JPQL `setFirstResult`/`setMaxResults` (equivalente a `OFFSET`/`LIMIT`), de modo que nenhuma consulta de listagem recupere a tabela inteira de Partidas.
2. THE Sistema SHALL aceitar os parâmetros de query `page` (índice de página, base 0) e `size` (Tamanho_de_Pagina) nas listagens de Partidas.
3. WHEN uma requisição de listagem é recebida sem o parâmetro `size`, THE Sistema SHALL utilizar o Tamanho_de_Pagina_Padrao obtido da variável de ambiente `JOGOS_PAGE_SIZE_DEFAULT`.
4. WHEN uma requisição de listagem é recebida sem o parâmetro `page`, THE Sistema SHALL utilizar o índice de página 0.
5. THE Sistema SHALL obter o Tamanho_de_Pagina_Padrao e o Teto_de_Pagina exclusivamente das variáveis de ambiente `JOGOS_PAGE_SIZE_DEFAULT` e `JOGOS_PAGE_SIZE_MAX`, respectivamente, sem valores hard-coded no artefato deployável, em conformidade com o critério 11.1.
6. WHEN uma requisição de listagem é recebida com `size` maior que o Teto_de_Pagina (`JOGOS_PAGE_SIZE_MAX`), THE Sistema SHALL limitar silenciosamente o Tamanho_de_Pagina efetivo ao Teto_de_Pagina e retornar o código HTTP 200 e a Pagina correspondente, sem retornar erro.
7. IF uma requisição de listagem é recebida com `page` ou `size` inválido (valor negativo ou não numérico), THEN THE Sistema SHALL retornar o código HTTP 400 e uma mensagem de erro descritiva indicando o parâmetro inválido, sem executar a consulta, mantendo o padrão de validação de entrada do Requisito 13.4.
8. WHEN uma requisição de listagem com filtro por `status` ou por `time` é recebida, THE Sistema SHALL aplicar a paginação sobre o conjunto de Partidas que satisfazem o filtro, calculando a Pagina após a filtragem no banco.
9. THE Sistema SHALL garantir que o número de Partidas retornadas em qualquer resposta de listagem seja menor ou igual ao Teto_de_Pagina (`JOGOS_PAGE_SIZE_MAX`), independentemente do total de Partidas persistidas.
10. IF o valor informado nos parâmetros de query `page` ou `size` não é convertível para o tipo numérico alvo (por exemplo, `page=abc`), THEN THE Sistema SHALL tratar explicitamente a falha de conversão — recebendo o parâmetro como texto na camada de recurso e convertendo-o no próprio recurso, ou mapeando a exceção de parâmetro do JAX-RS — de modo que a resposta observável seja o código HTTP 400 com mensagem de erro descritiva indicando o parâmetro inválido, e não o código HTTP 404 que a implementação JAX-RS retorna por padrão para falha de conversão de `@QueryParam`.
11. THE Sistema SHALL responder às requisições de listagem com `page` ou `size` não numérico com o código HTTP 400, sem expor stack trace nem nome de classe de exceção interna, em conformidade com os critérios 13.5 e 18.7.

#### Propriedades de Corretude

- **Limite superior de cardinalidade da resposta**: para qualquer requisição de listagem com Tamanho_de_Pagina solicitado `size`, o número de elementos retornados SHALL ser menor ou igual a `min(size, Teto_de_Pagina)`.
- **Nunca varre a tabela inteira**: para qualquer requisição de listagem, a consulta ao PostgreSQL SHALL aplicar `LIMIT`/`OFFSET` (via `setMaxResults`/`setFirstResult`) com `LIMIT` menor ou igual ao Teto_de_Pagina, de modo que o número de registros recuperados seja limitado e nunca corresponda à totalidade da tabela quando esta exceder o Teto_de_Pagina.
- **Teto respeitado**: para qualquer `size` solicitado (inclusive valores acima do Teto_de_Pagina), o número de elementos retornados em uma única resposta SHALL nunca exceder o Teto_de_Pagina (`JOGOS_PAGE_SIZE_MAX`).
- **Preservação do filtro sob paginação**: para qualquer Pagina retornada por uma listagem filtrada por `status = S` ou por `time = N`, toda Partida da Pagina SHALL satisfazer o respectivo predicado do filtro.
- **Padrão seguro na ausência de parâmetros**: para qualquer requisição de listagem sem `page` e sem `size`, o Sistema SHALL retornar a Pagina de índice 0 com no máximo o Tamanho_de_Pagina_Padrao elementos.
- **HTTP 400 para parâmetro de paginação não numérico**: para qualquer valor não numérico informado em `page` ou em `size`, a resposta observável da API SHALL ser HTTP 400 com mensagem descritiva, nunca HTTP 404 e nunca HTTP 5xx.
