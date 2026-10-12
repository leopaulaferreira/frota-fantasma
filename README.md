# Frota Fantasma Analytics

Gestão e análise do clã **Frota Fantasma — #RQJGC9GV**. O objetivo é acompanhar guerras, decks, doações, atividade e trajetória dos membros, com recomendações explicáveis para apoiar decisões humanas. O sistema não promove nem expulsa jogadores automaticamente.

**Etapa atual: Fase 1 — backend de consulta do clã e membros.** O PostgreSQL guarda o último retrato consultado. Coleta agendada, histórico, analytics e frontend estão previstos no [plano por fases](docs/ROADMAP.md). A existência das tabelas atuais não significa que já há histórico de quatro guerras.

## Stack e arquitetura

Java 21, Spring Boot 3.5, Spring Web, Spring Data JPA, PostgreSQL 17, Flyway, Jackson, Bean Validation e [springdoc/OpenAPI](https://springdoc.org/v2/). O frontend planejado usa React, TypeScript, Vite, React Router e TanStack Query.

```text
backend/src/main/java/com/frotafantasma/analytics/
├── controller/   # HTTP e respostas de erro
├── service/      # consulta e coordenação da persistência
├── client/       # comunicação exclusiva do backend com a Supercell
├── config/       # configuração validada e OpenAPI
├── dto/          # contratos internos; dto/clash contém contratos externos
├── entity/       # modelos JPA, sem dependência dos DTOs
├── mapper/       # conversão de DTOs e entidades
└── repository/   # acesso ao PostgreSQL
backend/src/main/resources/db/migration/  # migrations Flyway
```

Fluxo atual: controller → service → client → API oficial; após sucesso, service → mapper → repository → PostgreSQL. Chamadas externas ocorrem fora das transações de escrita. Controllers não calculam regras de negócio. `scheduler` e `analytics` serão adicionados nas fases correspondentes.

## Executar com Docker Compose

Requisitos: Docker com Compose e acesso à internet para a API oficial.

1. Se `.env` ainda não existir, copie: `cp .env.example .env`.
2. Edite `CLASH_ROYALE_API_TOKEN` e `POSTGRES_PASSWORD` no arquivo local. Os valores do exemplo são placeholders.
3. Execute `docker compose up --build -d`.
4. Acompanhe a inicialização: `docker compose logs -f backend`.

Na porta padrão:

- Clã: <http://localhost:8080/api/clan>
- Membros: <http://localhost:8080/api/clan/members>
- Swagger: <http://localhost:8080/swagger-ui/index.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>

Use o valor de `BACKEND_PORT` do seu `.env` nas URLs se tiver alterado a porta. `POSTGRES_PORT` também é configurável. Os serviços são publicados apenas em `127.0.0.1`.

`docker compose down` encerra os serviços e preserva o banco. O volume contém os dados coletados; `down -v` apaga esses dados. Alterar a senha em `.env` não altera a senha de um banco já inicializado: atualize também o usuário PostgreSQL quando necessário.

## API key

Crie uma conta no [portal oficial da Supercell](https://developer.clashroyale.com/), crie uma chave para Clash Royale e informe o IP público de saída da máquina que executa o backend. Salve o token apenas no `.env` local. Se o IP mudar, ajuste a chave no portal.

O browser e o futuro frontend chamarão somente os endpoints internos. O Swagger também usa a API interna e não solicita o token da Supercell.

## Variáveis de ambiente

As propriedades da aplicação ficam em `backend/src/main/resources/application.yml`.

| Variável | Padrão / finalidade |
| --- | --- |
| `CLASH_ROYALE_API_TOKEN` | Obrigatória; credencial exclusiva do backend |
| `CLAN_TAG` | `#RQJGC9GV`; tag com `#`, letras maiúsculas e números, até 32 caracteres |
| `POSTGRES_DB` | `frota_fantasma`; nome do banco no Compose |
| `POSTGRES_USER` | `frota_fantasma`; usuário no Compose |
| `POSTGRES_PASSWORD` | Obrigatória no Compose; senha local do banco |
| `POSTGRES_PORT` | `5432`; porta do banco no host |
| `BACKEND_PORT` | `8080`; porta HTTP no host |
| `DB_URL` | Fora do Compose: `jdbc:postgresql://localhost:5432/frota_fantasma` |
| `DB_USERNAME` | Fora do Compose: `frota_fantasma` |
| `DB_PASSWORD` | Senha do banco ao executar pelo Maven/IDE |
| `SERVER_PORT` | Porta HTTP ao executar pelo Maven/IDE; padrão Spring `8080` |
| `CLASH_CONNECT_TIMEOUT` | `5s`; entre `1ms` e `30s` |
| `CLASH_READ_TIMEOUT` | `10s`; entre `1ms` e `30s` |
| `CLASH_MAX_ATTEMPTS` | `2` tentativas totais, incluindo a primeira; mínimo 1, máximo 3 |
| `CLASH_RETRY_DELAY` | `500ms`; entre `1ms` e `5s`; multiplicado pelo número da tentativa anterior |
| `CLASH_RATE_LIMIT_COOLDOWN` | `30s`; espera padrão após 429, entre `1s` e `10m` |
| `CLASH_ROYALE_API_BASE_URL` | `https://api.clashroyale.com/v1`; substituição para testes locais, não repassada pelo Compose |

## Executar pelo Maven ou IDE

Requisitos: Java 21, Maven 3.9+ e PostgreSQL. O Spring não carrega `.env` automaticamente. Em Bash, após configurar o arquivo:

```bash
docker compose up -d db
set -a
. ./.env
set +a
export DB_URL="jdbc:postgresql://localhost:${POSTGRES_PORT:-5432}/${POSTGRES_DB:-frota_fantasma}"
export DB_USERNAME="${POSTGRES_USER:-frota_fantasma}"
export DB_PASSWORD="$POSTGRES_PASSWORD"
export SERVER_PORT="${BACKEND_PORT:-8080}"
cd backend
mvn spring-boot:run
```

Pare o backend do Compose antes de usar a mesma porta no Maven: `docker compose stop backend`. Na IDE, execute `FrotaFantasmaApplication` com as mesmas variáveis; não compartilhe configurações da IDE contendo credenciais.

## Endpoints e persistência da Fase 1

| Endpoint interno | Fonte externa | Resposta |
| --- | --- | --- |
| `GET /api/clan` | `/v1/clans/{clanTag}` | Objeto com tag, nome, descrição, tipo, pontuação, troféus de guerra, requisitos, doações e quantidade de membros |
| `GET /api/clan/members` | `/v1/clans/{clanTag}/members` | Array de membros: tag, nome, cargo, nível, troféus, posição e doações |

O backend valida a tag configurada e codifica `#` como `%23`. Os endpoints não recebem uma URL externa fornecida pelo usuário.

As tabelas `clan_snapshot` e `clan_member_snapshot` contêm o **último estado**, com horário de observação. Cada GET bem-sucedido atualiza esse estado. A lista de membros é substituída em uma transação; falhas externas não removem os dados anteriores. Não há fallback automático para retornar dados antigos como se fossem atuais.

Flyway aplica as migrations; Hibernate apenas valida o schema (`ddl-auto: validate`). A migração V1 existente será preservada ao introduzir o modelo histórico na Fase 2.

## Erros e limites

Os erros de integração retornam `application/problem+json`, incluindo `status`, `detail`, `code`, `instance` e `timestamp`. Corpos externos, stack traces e credenciais não são incluídos nessas respostas.

| Situação | HTTP interno / código |
| --- | --- |
| Clã não encontrado | 404 / `CLAN_NOT_FOUND` |
| Credencial rejeitada, conexão ou outras falhas externas | 502 / `UPSTREAM_ERROR` ou `UPSTREAM_CONNECTION_ERROR` |
| JSON vazio, malformado ou dados obrigatórios ausentes | 502 / `INVALID_UPSTREAM_RESPONSE` |
| Manutenção/503 | 503 / `UPSTREAM_UNAVAILABLE` |
| Limite de chamadas/429 | 503 / `UPSTREAM_RATE_LIMITED`, com header `Retry-After` |
| Timeout após as tentativas permitidas | 504 / `UPSTREAM_TIMEOUT` |
| Interrupção durante espera de retry | 503 / `UPSTREAM_INTERRUPTED` |

Retry ocorre apenas para erros de conexão/timeout e HTTP 500, 502, 503 e 504. Erros 400/401/403/404, dados inválidos e 429 não são repetidos na mesma consulta. Após 429, uma pausa em memória é compartilhada entre os dois endpoints desta instância. `Retry-After` aceita segundos ou data HTTP futura de até 24 horas; valores ausentes/inválidos usam o intervalo configurado. A pausa se encerra no prazo informado e não é persistida entre reinicializações.

## Testes

```bash
cd backend
mvn test                       # unitários e HTTP com API simulada; sem Docker/token real
mvn verify -Pintegration        # inclui Spring + Flyway + PostgreSQL via Testcontainers
```

A suíte de integração requer Docker em execução e cria um banco descartável separado do Compose. Ela testa os dois endpoints, persistência, substituição dos membros, preservação em falhas e Swagger. Credenciais de teste são fictícias; nenhuma suíte lê o `.env` local ou consulta a Supercell.

Os testes de analytics, Alma Imortal, Frota Score, promoção e observação serão implementados junto às regras nas fases 3 e 4.

## Segurança, dados e próximos passos

- `.env` e variantes estão no `.gitignore`; `.env.example` contém apenas placeholders. O contexto Docker também exclui `.env`.
- O token entra somente no processo do backend; não é gravado no banco, incluído nos DTOs ou no OpenAPI.
- Logs da aplicação não registram o token ou corpos de erro da Supercell. Não habilite logs de headers HTTP com credenciais reais.
- Esta fase tem acesso local sem autenticação de usuários. Autenticação e controles para publicação externa fazem parte da etapa de deploy.
- Consulte [origem e limites dos dados](docs/DATA-SOURCES.md) antes de interpretar atividade, doações ou participação.
- Consulte o [roteiro de implementação](docs/ROADMAP.md) para os critérios das próximas fases.

A interface e os screenshots chegam na Fase 5. Nenhum asset externo foi incorporado nesta fase. Antes do frontend, será inspecionado o [RoyaleAPI/cr-api-assets](https://github.com/RoyaleAPI/cr-api-assets), inclusive seus avisos/licenças. Apenas arquivos efetivamente utilizados serão copiados com origem documentada; não haverá dependência desse repositório em runtime.
