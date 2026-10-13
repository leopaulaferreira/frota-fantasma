# Frota Fantasma Analytics

Gestão e análise do clã **Frota Fantasma — #RQJGC9GV**. O objetivo é acompanhar guerras, decks, doações, atividade e trajetória dos membros, com recomendações explicáveis para apoiar decisões humanas. O sistema não promove nem expulsa jogadores automaticamente.

**Etapa atual: Fase 6 — operação local e preparação para publicação.** O PostgreSQL guarda snapshots de clã, membros e corrida atual, eventos de associação e resultados oficiais de guerras. Os GETs consultam os dados persistidos; jobs e sincronização manual atualizam as fontes. Indicadores, classificações operacionais, destaques e candidatos a Ancião estão disponíveis no painel React, com login e permissões. Operação e limites estão em [OPERATIONS.md](docs/OPERATIONS.md). Consulte o [catálogo de métricas e limitações](docs/ANALYTICS.md). A existência das tabelas não significa que já há histórico de quatro guerras.

## Stack e arquitetura

Java 21, Spring Boot 3.5, Spring Web, Spring Data JPA, PostgreSQL 17, Flyway, Jackson, Bean Validation e [springdoc/OpenAPI](https://springdoc.org/v2/). Spring Security protege a API. O frontend usa React, TypeScript, Vite, React Router e TanStack Query.

```text
backend/src/main/java/com/frotafantasma/analytics/
├── controller/   # HTTP e respostas de erro
├── service/      # consulta, coleta e coordenação da persistência
├── scheduler/    # coletas periódicas, diárias e semanais
├── analytics/    # métricas explicáveis, doações, contexto e recomendações
├── client/       # comunicação exclusiva do backend com a Supercell
├── config/       # configuração validada e OpenAPI
├── dto/          # contratos internos; dto/clash contém contratos externos
├── entity/       # modelos JPA, sem dependência dos DTOs
├── mapper/       # conversão de DTOs e entidades
└── repository/   # acesso ao PostgreSQL
backend/src/main/resources/db/migration/  # migrations Flyway
```

Consultas: controller → service → repository → PostgreSQL. Coletas: scheduler ou POST → coordenador → client → API oficial → persistência transacional. Chamadas externas ocorrem fora das transações de escrita. Controllers não calculam regras de negócio. O módulo `analytics` calcula relatórios a partir do histórico local, com regras versionadas e evidências.

## Executar com Docker Compose

Requisitos: Docker com Compose e acesso à internet para a API oficial.

1. Se `.env` ainda não existir, copie: `cp .env.example .env`.
2. Edite `CLASH_ROYALE_API_TOKEN`, `POSTGRES_PASSWORD` e `AUTH_ADMIN_PASSWORD` no arquivo local. Os valores do exemplo são placeholders. A senha do painel precisa de 16 a 72 caracteres (até 72 bytes UTF-8).
3. Execute `docker compose up --build -d`.
4. Acompanhe a inicialização: `docker compose logs -f backend`.

Na porta padrão:

- Painel e login: <http://localhost:5173>
- Clã (com sessão): <http://localhost:8080/api/clan>
- Membros: <http://localhost:8080/api/clan/members>
- Swagger: <http://localhost:8080/swagger-ui/index.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>

Use o valor de `BACKEND_PORT` do seu `.env` nas URLs se tiver alterado a porta. `POSTGRES_PORT` também é configurável. Os serviços são publicados apenas em `127.0.0.1`.

`docker compose down` encerra os serviços e preserva o banco. O volume contém os dados coletados; `down -v` apaga esses dados. Alterar a senha em `.env` não altera a senha de um banco já inicializado: atualize também o usuário PostgreSQL quando necessário.

## API key

Crie uma conta no [portal oficial da Supercell](https://developer.clashroyale.com/), crie uma chave para Clash Royale e informe o IP público de saída da máquina que executa o backend. Salve o token apenas no `.env` local. Se o IP mudar, ajuste a chave no portal.

O browser e o frontend chamam somente os endpoints internos. O Swagger também usa a API interna e não solicita o token da Supercell.

## Variáveis de ambiente

As propriedades da aplicação ficam em `backend/src/main/resources/application.yml`.

| Variável | Padrão / finalidade |
| --- | --- |
| `AUTH_ADMIN_USERNAME` / `AUTH_ADMIN_PASSWORD` | Conta de liderança; senha obrigatória, sem padrão |
| `AUTH_VIEWER_USERNAME` / `AUTH_VIEWER_PASSWORD` | Conta opcional de consulta |
| `FRONTEND_PORT` | `5173`; porta do painel |
| `SESSION_COOKIE_SECURE` | `false` local; `true` em HTTPS |
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

## Endpoints e persistência da Fase 2

| Endpoint interno | Resposta |
| --- | --- |
| `GET /api/clan` e `/api/clan/members` | Último retrato persistido do clã e sua lista de membros |
| `GET /api/clan/history` e `/api/clan/events` | Snapshots e eventos de associação/cargo |
| `GET /api/players/{tag}` | Jogador observado localmente; não consulta perfil externo |
| `GET /api/players/{tag}/history`, `/events` e `/wars` | Histórico de membros, eventos e participações do jogador |
| `GET /api/wars` e `/api/wars/{id}` | Resultados oficiais persistidos e participantes |
| `GET /api/wars/current` | Último snapshot da corrida atual, com instante da captura |
| `GET /api/wars/current/history` e `/api/wars/current/history/{id}` | Histórico de snapshots da corrida |
| `GET /api/collections` | Execuções, horários, contagens e códigos de falha |
| `POST /api/collections/sync` | Coleta clã/membros, corrida atual e histórico oficial |

Listagens históricas aceitam `page` (a partir de zero) e `size` (1–100, padrão 20), retornando `items`, `page`, `size`, `totalElements` e `totalPages`. Nas URLs de jogadores, codifique `#` como `%23`.

Para a primeira coleta manual: `curl -X POST http://localhost:8080/api/collections/sync` (ajuste a porta). A resposta contém um resultado por fonte: `SUCCESS`, `FAILED` ou `SKIPPED`. HTTP 200 não garante sucesso de todas as fontes. Antes de existirem dados, consultas que precisam de um clã ou snapshot retornam 404. Falhas de coleta preservam os dados anteriores; verifique os horários de captura e `/api/collections` para avaliar a atualização.

As coletas usam `/v1/clans/{clanTag}`, `/members`, `/currentriverrace` e `/riverracelog`. Membros e histórico percorrem todas as páginas até o limite configurado; respostas incompletas não alteram a lista anterior. A contagem de membros precisa coincidir com o clã. A primeira lista estabelece `BASELINE`; as seguintes podem registrar `JOIN`, `LEAVE` e `ROLE_CHANGE`.

Cada coleta mantém um lock de sessão PostgreSQL por clã, compartilhado entre réplicas, sem manter transação SQL aberta durante HTTP. Jobs deduplicam execuções bem-sucedidas por intervalo/dia; falhas permitem nova tentativa. A sincronização manual cria novas observações. Resultados oficiais são deduplicados por clã, temporada e seção; alterações atualizam a mesma guerra. Execuções interrompidas são marcadas como falha ao obter novamente o lock.

Flyway aplica as migrations e Hibernate valida o schema (`ddl-auto: validate`). A V1 permanece intacta; a V2 preserva seus registros no modelo histórico. As tabelas antigas deixam de receber atualizações. Nenhuma migração inventa observações anteriores.

## Agendamentos

Os padrões abaixo também podem ser configurados no `.env` do Compose. Use aspas nos valores de cron para permitir carregar o arquivo em Bash.

| Variável | Padrão / finalidade |
| --- | --- |
| `COLLECTION_ENABLED` | `true`; `false` desliga jobs, mantendo sincronização manual |
| `COLLECTION_MEMBERS_INTERVAL` | `PT30M`; intervalo de clã/membros, mínimo 1 minuto |
| `COLLECTION_WAR_INTERVAL` | `PT15M`; intervalo da corrida nos dias de guerra, mínimo 1 minuto |
| `COLLECTION_LOG_INTERVAL` | `PT6H`; busca periódica de resultados oficiais, mínimo 1 minuto |
| `COLLECTION_INITIAL_DELAY` | `PT10S`; atraso inicial dos jobs de intervalo, mínimo zero |
| `COLLECTION_TIMEZONE` | `UTC`; fuso dos dias de guerra e cron, por exemplo `America/Sao_Paulo` |
| `COLLECTION_WAR_DAYS` | `THURSDAY,FRIDAY,SATURDAY,SUNDAY` |
| `COLLECTION_DAILY_CRON` | `0 5 0 * * *`; snapshots de membros e corrida todos os dias às 00:05 |
| `COLLECTION_WEEKLY_CRON` | `0 0 12 * * MON`; consulta de resultados às segundas às 12:00 |
| `COLLECTION_MAX_PAGES` | `20`; limite de páginas por fonte, entre 1 e 100 |

Intervalos contam a partir do término da execução anterior. Capturas são armazenadas em UTC. O job semanal importa apenas resultados já publicados no histórico oficial; o relógio não encerra uma guerra. Caso a publicação atrase, o job periódico de histórico volta a consultá-la. Uma consulta bem-sucedida com histórico vazio não confirma fechamento.

## Erros e limites

Durante a coleta, falhas da fonte são registradas em `CollectionResult.errorCode` e `/api/collections`; o POST pode retornar HTTP 200 com resultados `FAILED`. A tabela abaixo descreve o mapeamento interno do cliente de integração, não o status HTTP do POST. Exceções de integração que chegam ao controller retornam `application/problem+json`, incluindo `status`, `detail`, `code`, `instance` e `timestamp`. Corpos externos, stack traces e credenciais não são incluídos nessas respostas.

| Situação | HTTP interno / código |
| --- | --- |
| Clã não encontrado | 404 / `CLAN_NOT_FOUND` |
| Credencial rejeitada, conexão ou outras falhas externas | 502 / `UPSTREAM_ERROR` ou `UPSTREAM_CONNECTION_ERROR` |
| JSON vazio, malformado ou dados obrigatórios ausentes | 502 / `INVALID_UPSTREAM_RESPONSE` |
| Manutenção/503 | 503 / `UPSTREAM_UNAVAILABLE` |
| Limite de chamadas/429 | 503 / `UPSTREAM_RATE_LIMITED`, com header `Retry-After` |
| Timeout após as tentativas permitidas | 504 / `UPSTREAM_TIMEOUT` |
| Interrupção durante espera de retry | 503 / `UPSTREAM_INTERRUPTED` |

Retry ocorre apenas para erros de conexão/timeout e HTTP 500, 502, 503 e 504. Erros 400/401/403/404, dados inválidos e 429 não são repetidos na mesma consulta. Após 429, uma pausa em memória é compartilhada entre as chamadas à API externa desta instância. `Retry-After` aceita segundos ou data HTTP futura de até 24 horas; valores ausentes/inválidos usam o intervalo configurado. A pausa se encerra no prazo informado e não é persistida entre reinicializações.

## Testes

```bash
cd backend
mvn test                       # unitários e HTTP com API simulada; sem Docker/token real
mvn verify -Pintegration        # inclui Spring + Flyway + PostgreSQL via Testcontainers
```

A suíte de integração requer Docker em execução e cria bancos descartáveis separados do Compose. Ela testa consultas locais, coleta manual, deduplicação, exclusão mútua, preservação em falhas, eventos de membros, migração de dados da V1 e Swagger. Testes do scheduler verificam dias de guerra, fuso e chaves por período. Credenciais de teste são fictícias; nenhuma suíte lê o `.env` local ou consulta a Supercell.

A suíte de analytics cobre amostras insuficientes, uma guerra ruim isolada, resets de doações, reingresso, atividade oficial ausente/desatualizada, médias, tendência, exclusividade dos títulos e critérios de candidatura. Frota Score é opcional, com pesos/metas validados, parcelas explicáveis e tratamento de dados ausentes.

## Analytics de gestão

O relatório completo está em `GET /api/analytics`. Há consultas específicas em `/api/analytics/players`, `/players/{tag}`, `/clan`, `/highlights`, `/promotions` e `/evolution`. O parâmetro opcional `week=YYYY-MM-DD` aceita a segunda-feira da semana analisada.

Cada métrica informa origem (`DIRECT_API`, `DERIVED`, `SNAPSHOT_DERIVED` ou `UNAVAILABLE`), período, guerras consideradas, evidências e motivo. Campos desconhecidos permanecem nulos, com indicação de dados insuficientes. O `lastSeen` oficial passa a ser armazenado pela V3, separadamente do horário local da coleta; snapshots antigos não recebem atividade inventada.

As respostas reais de quatro endpoints foram validadas antes da implementação; veja o [registro sem credenciais](docs/API-VALIDATION.json). Doações por diferenças são limites inferiores com períodos incompletos. Percentual do padrão de 16 decks não equivale à utilização individual disponível, que a API não permite calcular com segurança. Nenhum status ou candidato executa promoção/expulsão.

Consulte [ANALYTICS.md](docs/ANALYTICS.md) para fórmulas, configurações `ANALYTICS_*`, critérios dos títulos, semanas de publicação e exemplos de limitações. Todas as opções estão no `.env.example` e são repassadas pelo Compose.

## Recomendações e avaliações persistidas

A Fase 4 adiciona versões de regras editáveis em `/api/recommendations/rules`, registro explícito de avaliações em `/api/recommendations/evaluations`, histórico de status por jogador e formalização de premiações semanais. Os GETs de analytics continuam sem escrita; o relatório inclui a versão ativa, histórico de alertas e Frota Score opcional.

Cada avaliação congela o relatório e suas evidências. Alertas registrados podem impedir candidatura a Ancião mesmo após melhora no status atual. Títulos formalizados não mudam quando dados/regras são atualizados e não se repetem para um jogador na mesma semana. Nenhuma dessas operações altera cargos ou membros na Supercell.

Consulte [RECOMMENDATIONS.md](docs/RECOMMENDATIONS.md) para exemplos de payloads, fluxo de formalização, idempotência e limites dos indicadores. A V4 preserva as migrations anteriores e adiciona tabelas de auditoria imutáveis.

## Segurança, dados e próximos passos

- `.env` e variantes estão no `.gitignore`; `.env.example` contém apenas placeholders. O contexto Docker também exclui `.env`.
- O token entra somente no processo do backend; não é gravado no banco, incluído nos DTOs ou no OpenAPI.
- Logs da aplicação não registram o token ou corpos de erro da Supercell. Não habilite logs de headers HTTP com credenciais reais.
- Esta fase tem acesso local sem autenticação de usuários. Autenticação e controles para publicação externa fazem parte da etapa de deploy.
- Consulte [origem e limites dos dados](docs/DATA-SOURCES.md) antes de interpretar atividade, doações ou participação.
- Consulte o [roteiro de implementação](docs/ROADMAP.md) para os critérios das próximas fases.

A interface e os screenshots chegam na Fase 5. Nenhum asset externo foi incorporado nesta fase. Antes do frontend, será inspecionado o [RoyaleAPI/cr-api-assets](https://github.com/RoyaleAPI/cr-api-assets), inclusive seus avisos/licenças. Apenas arquivos efetivamente utilizados serão copiados com origem documentada; não haverá dependência desse repositório em runtime.

## Frontend — Fase 5

Central de comando com dashboard, tripulação, perfil completo, navegação entre
guerras, comparação, recomendações, premiações e regras. Cada métrica preserva
as evidências e limitações do backend.

Com o backend em execução: `npm ci --prefix frontend` e
`npm run dev --prefix frontend`. Acesse http://127.0.0.1:5173.
O Compose também inclui o frontend: `docker compose up --build -d`.

Consulte [FRONTEND.md](docs/FRONTEND.md) para os fluxos, configuração e testes.

## Operação e acesso — Fase 6

O painel exige login. A conta de liderança usa `AUTH_ADMIN_USERNAME` e
`AUTH_ADMIN_PASSWORD` no `.env`; a conta de consulta é opcional. Coletas,
regras, avaliações e formalização de títulos são restritas à liderança.

O Compose inclui backup diário. Monitoramento fica na página Operações.
Setup, recuperação, verificações reais e preparação para HTTPS:
[OPERATIONS.md](docs/OPERATIONS.md).
