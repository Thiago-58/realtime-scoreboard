# 📖 Documentação da API REST — Realtime Scoreboard

Esta documentação descreve todos os endpoints, parâmetros, formatos de dados, códigos de status HTTP e exemplos práticos para integração com a API do **Realtime Scoreboard**.

---

## 📌 1. Visão Geral

- **URL Base**: `http://localhost:8080/api`
- **Formato dos Dados**: `application/json` (em requisições e respostas)
- **Autenticação**: **Livre / Aberta** (sem necessidade de token, cabeçalhos de autorização ou login)
- **Documentação Interativa (Swagger UI)**: [http://localhost:8080/openapi-ui](http://localhost:8080/openapi-ui)
- **Especificação OpenAPI (JSON/YAML)**: [http://localhost:8080/openapi](http://localhost:8080/openapi)
- **Interface Gráfica Web (Wicket)**: [http://localhost:8080/](http://localhost:8080/)

---

## 🚦 2. Padrão de Respostas de Erro

Todas as mensagens de erro retornadas pela API seguem um formato JSON único e padronizado:

```json
{
  "erro": "Mensagem detalhada descrevendo a validação ou regra violada"
}
```

### Códigos HTTP Utilizados:
| Código | Nome | Quando Ocorre |
|---|---|---|
| **200** | `OK` | Requisição processada com sucesso (`GET`, `PUT`). |
| **201** | `Created` | Recurso criado com sucesso (`POST`). |
| **204** | `No Content` | Recurso excluído com sucesso (`DELETE`), sem corpo de resposta. |
| **400** | `Bad Request` | Validação de campo falhou (ex: nomes vazios, placares negativos, paginação negativa, times iguais). |
| **404** | `Not Found` | Partida com o ID informado não foi encontrada. |
| **422** | `Unprocessable Entity` | Regra de negócio violada (ex: tentativa de alterar placar de jogo encerrado). |
| **500** | `Internal Server Error` | Erro inesperado do servidor. |
| **503** | `Service Unavailable` | Falha de infraestrutura no Health Check (PostgreSQL, Redis ou RabbitMQ fora do ar). |

---

## ⚽ 3. Endpoints da API

### 3.1. Cadastrar Novo Jogo
Cria uma nova partida com status inicial `EM_ANDAMENTO`.

- **Método**: `POST`
- **Rota**: `/api/jogos`
- **Cabeçalhos**: `Content-Type: application/json`

#### Corpo da Requisição (JSON):
```json
{
  "timeA": "Flamengo",
  "timeB": "Palmeiras",
  "placarA": 0,
  "placarB": 0,
  "dataHoraPartida": "2026-11-20T20:00:00"
}
```

| Campo | Tipo | Obrigatório? | Descrição / Validação |
|---|---|:---:|---|
| `timeA` | String | **Sim** | Nome do time mandante (não pode ser vazio nem igual ao time B). |
| `timeB` | String | **Sim** | Nome do time visitante (não pode ser vazio nem igual ao time A). |
| `placarA` | Inteiro | Não | Placar inicial do mandante (padrão: `0`, mínimo: `0`). |
| `placarB` | Inteiro | Não | Placar inicial do visitante (padrão: `0`, mínimo: `0`). |
| `dataHoraPartida` | String (ISO-8601) | Não | Data e hora da partida (ex: `2026-11-20T20:00:00`). |

#### Resposta de Sucesso (`201 Created`):
```json
{
  "id": 8,
  "timeA": "Flamengo",
  "timeB": "Palmeiras",
  "placarA": 0,
  "placarB": 0,
  "status": "EM_ANDAMENTO",
  "dataHoraPartida": "2026-11-20T20:00:00"
}
```

#### Exemplo cURL:
```bash
curl -X POST http://localhost:8080/api/jogos \
  -H "Content-Type: application/json" \
  -d '{
    "timeA": "Flamengo",
    "timeB": "Palmeiras",
    "placarA": 0,
    "placarB": 0,
    "dataHoraPartida": "2026-11-20T20:00:00"
  }'
```

---

### 3.2. Listar Jogos (com Filtros e Paginação)
Retorna uma lista paginada de partidas cadastradas.

- **Método**: `GET`
- **Rota**: `/api/jogos`

#### Parâmetros de Query (Opcionais):
| Parâmetro | Tipo | Descrição | Exemplo |
|---|---|---|---|
| `status` | String | Filtra por status: `EM_ANDAMENTO`, `INTERVALO` ou `ENCERRADO`. | `?status=EM_ANDAMENTO` |
| `time` | String | Filtro por nome do time (busca parcial insensível a maiúsculas/minúsculas). | `?time=Flamengo` |
| `page` | Inteiro | Número da página iniciando em `0` (padrão: `0`). | `?page=0` |
| `size` | Inteiro | Quantidade de registros por página (padrão: `20`). | `?size=10` |

#### Resposta de Sucesso (`200 OK`):
```json
[
  {
    "id": 1,
    "timeA": "Flamengo",
    "timeB": "Palmeiras",
    "placarA": 2,
    "placarB": 1,
    "status": "EM_ANDAMENTO",
    "dataHoraPartida": "2026-10-15T16:00"
  }
]
```

#### Exemplos cURL:
```bash
# Todos os jogos
curl -X GET "http://localhost:8080/api/jogos"

# Apenas jogos em andamento com paginação
curl -X GET "http://localhost:8080/api/jogos?status=EM_ANDAMENTO&page=0&size=5"

# Buscar por time
curl -X GET "http://localhost:8080/api/jogos?time=Palmeiras"
```

---

### 3.3. Buscar Jogo por ID
Recupera os detalhes completos de uma partida específica.

- **Método**: `GET`
- **Rota**: `/api/jogos/{id}`

#### Parâmetros de Path:
- `id` (Long): Identificador numérico da partida.

#### Resposta de Sucesso (`200 OK`):
```json
{
  "id": 1,
  "timeA": "Flamengo",
  "timeB": "Palmeiras",
  "placarA": 2,
  "placarB": 1,
  "status": "EM_ANDAMENTO",
  "dataHoraPartida": "2026-10-15T16:00"
}
```

#### Resposta de Erro (`404 Not Found`):
```json
{
  "erro": "Partida não encontrada para o ID: 999"
}
```

#### Exemplo cURL:
```bash
curl -X GET http://localhost:8080/api/jogos/1
```

---

### 3.4. Atualizar Placar do Jogo
Atualiza o placar de uma partida em andamento. 

> [!NOTE]
> Esta operação atualiza o banco **PostgreSQL**, grava na **Auditoria** (origem `api-rest`), dispara um evento assíncrono no **RabbitMQ** e atualiza o cache no **Redis**, refletindo na interface web em tempo real.

- **Método**: `PUT`
- **Rota**: `/api/jogos/{id}/placar`
- **Cabeçalhos**: `Content-Type: application/json`

#### Corpo da Requisição (JSON):
```json
{
  "placarA": 3,
  "placarB": 2
}
```

| Campo | Tipo | Obrigatório? | Descrição |
|---|---|:---:|---|
| `placarA` | Inteiro | **Sim** | Novo placar do mandante ($\ge 0$). |
| `placarB` | Inteiro | **Sim** | Novo placar do visitante ($\ge 0$). |

#### Resposta de Sucesso (`200 OK`):
```json
{
  "id": 1,
  "timeA": "Flamengo",
  "timeB": "Palmeiras",
  "placarA": 3,
  "placarB": 2,
  "status": "EM_ANDAMENTO",
  "dataHoraPartida": "2026-10-15T16:00"
}
```

#### Respostas de Erro:
- `400 Bad Request`: Placar com valor negativo (`"Os placares devem ser maiores ou iguais a zero."`).
- `404 Not Found`: Partida inexistente.
- `422 Unprocessable Entity`: Partida já encerrada (`"Não é permitido alterar o placar de uma partida com status ENCERRADO."`).

#### Exemplo cURL:
```bash
curl -X PUT http://localhost:8080/api/jogos/1/placar \
  -H "Content-Type: application/json" \
  -d '{
    "placarA": 3,
    "placarB": 2
  }'
```

---

### 3.5. Alterar Status do Jogo
Permite avançar o estado do jogo (ex: encerrar a partida ou colocar em intervalo).

- **Método**: `PUT`
- **Rota**: `/api/jogos/{id}/status`
- **Cabeçalhos**: `Content-Type: application/json`

#### Corpo da Requisição (JSON):
```json
{
  "status": "ENCERRADO"
}
```

Valores aceitos para `status`:
- `EM_ANDAMENTO`
- `INTERVALO`
- `ENCERRADO`

#### Resposta de Sucesso (`200 OK`):
```json
{
  "id": 1,
  "timeA": "Flamengo",
  "timeB": "Palmeiras",
  "placarA": 3,
  "placarB": 2,
  "status": "ENCERRADO",
  "dataHoraPartida": "2026-10-15T16:00"
}
```

#### Exemplo cURL:
```bash
curl -X PUT http://localhost:8080/api/jogos/1/status \
  -H "Content-Type: application/json" \
  -d '{
    "status": "ENCERRADO"
  }'
```

---

### 3.6. Excluir Partida
Remove a partida do banco de dados relacional e invalida a chave correspondente no cache Redis. Registra a ação na trilha de auditoria.

- **Método**: `DELETE`
- **Rota**: `/api/jogos/{id}`

#### Resposta de Sucesso:
- `204 No Content` (sem corpo de resposta).

#### Resposta de Erro:
- `404 Not Found` (se a partida não existir).

#### Exemplo cURL:
```bash
curl -X DELETE http://localhost:8080/api/jogos/1
```

---

## 📜 4. Endpoint de Auditoria

### 4.1. Consultar Histórico de Auditoria
Permite consultar todas as operações de mutação registradas no sistema (criação, gols, mudanças de status e exclusões).

- **Método**: `GET`
- **Rota**: `/api/auditoria`

#### Parâmetros de Query (Opcionais):
| Parâmetro | Tipo | Descrição | Exemplo |
|---|---|---|---|
| `usuario` | String | Origem da ação: `"painel-web"` ou `"api-rest"`. | `?usuario=api-rest` |
| `tipoAcao` | String | Ação: `CRIACAO_PARTIDA`, `ATUALIZACAO_PLACAR`, `ALTERACAO_STATUS`, `EXCLUSAO_PARTIDA`. | `?tipoAcao=ATUALIZACAO_PLACAR` |
| `dataInicio` | String (ISO) | Filtro por data inicial (ex: `2026-09-01T00:00:00`). | `?dataInicio=2026-09-01T00:00:00` |
| `dataFim` | String (ISO) | Filtro por data final (ex: `2026-09-30T23:59:59`). | `?dataFim=2026-09-30T23:59:59` |

#### Resposta de Sucesso (`200 OK`):
```json
[
  {
    "id": 52,
    "usuario": "api-rest",
    "tipoAcao": "ATUALIZACAO_PLACAR",
    "entidadeAfetada": "Partida",
    "idEntidade": 1,
    "valorAntes": "9x5",
    "valorDepois": "10x5",
    "timestamp": "2026-09-26T16:16:24.700"
  },
  {
    "id": 51,
    "usuario": "api-rest",
    "tipoAcao": "CRIACAO_PARTIDA",
    "entidadeAfetada": "Partida",
    "idEntidade": 8,
    "valorAntes": null,
    "valorDepois": "{\"id\":null,\"timeA\":\"Brasil\",\"timeB\":\"Argentina\",\"placarA\":0,\"placarB\":0,\"status\":\"EM_ANDAMENTO\"}",
    "timestamp": "2026-09-26T16:14:28.760"
  }
]
```

#### Exemplo cURL:
```bash
curl -X GET "http://localhost:8080/api/auditoria?usuario=api-rest"
```

---

## 🩺 5. Endpoint de Monitoramento (Health Check)

### 5.1. Verificar Saúde do Sistema
Verifica a integridade das 3 dependências fundamentais: **PostgreSQL**, **Redis** e **RabbitMQ**.

- **Método**: `GET`
- **Rota**: `/api/health`

#### Resposta quando todos os serviços estão saudáveis (`200 OK`):
```json
{
  "status": "UP",
  "bancoPostgres": "UP",
  "cacheRedis": "UP",
  "mensageriaRabbitMQ": "UP"
}
```

#### Resposta se algum serviço estiver indisponível (`503 Service Unavailable`):
```json
{
  "status": "DOWN",
  "bancoPostgres": "UP",
  "cacheRedis": "DOWN",
  "mensageriaRabbitMQ": "UP"
}
```

#### Exemplo cURL:
```bash
curl -X GET http://localhost:8080/api/health
```

---

## 💡 6. Dicas e Testes Rápidos

### Testar com Postman / Insomnia
1. Defina a Base URL como `http://localhost:8080/api`.
2. Em **Headers**, adicione: `Content-Type: application/json`.
3. Na aba **Authorization**, selecione **No Auth** (sem necessidade de token).
4. Utilize os exemplos de corpo JSON acima para disparar requisições.

### Acessar a Documentação Interativa
Abra no navegador:
👉 **[http://localhost:8080/openapi-ui](http://localhost:8080/openapi-ui)**
Você pode testar todos os endpoints diretamente pela interface gráfica Swagger, preenchendo os campos e clicando em **"Try it out" / "Execute"**.
