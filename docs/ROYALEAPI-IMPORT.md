# Importação manual RoyaleAPI: quarentena, identificação e incorporação

## Estado da implementação

Operações → **Dados externos → Importar CSV** permite analisar os dois formatos
observados, consultar problemas por registro e confirmar o armazenamento de um
lote **pendente de identificação**. A Supercell permanece a fonte principal e
autoritativa. Não há sincronização, scraping ou download automático do RoyaleAPI.

A Fase 3 permite incorporar registros de lotes cuja **última resolução é IDENTIFIED**.
A incorporação exige prévia e confirmação separadas. Novos registros entram no histórico
preferencial; duplicados não criam observações adicionais e conflitos preservam o existente.
Os estados originais do upload e as resoluções continuam imutáveis.

## Formatos efetivamente observados

Amostras inspecionadas em 28/10/2026, sem copiar dados pessoais para o repositório:

| Arquivo | Formato | Registros |
| --- | --- | --- |
| clan-RQJGC9GV-2026-10-26-02-10-50.csv | Corrida | 80 |
| clan-RQJGC9GV-2026-10-26-21-10-94.csv | Corrida | 81 |
| clan-RQJGC9GV-2026-10-26-02-10-30.csv | Membros | 44 |
| clan-RQJGC9GV-2026-10-26-21-10-75.csv | Membros | 42 |

Cabeçalho de membros:

```csv
tag,name,role,lastSeen,expLevel,trophies,arena_id,arena_name,arena_rawName,clanRank,previousClanRank,donations,donationsReceived,clanChestPoints
```

Cabeçalho de corrida:

```csv
state,clan_tag,clan_name,clan_badge_id,clan_fame,clan_repair_points,clan_finish_time,player_name,player_tag,player_fame,player_repair_points,player_contribution,player_boat_attacks,player_decks_used,player_decks_used_today
```

O adapter aceita estes cabeçalhos e sua ordem exata, separados por vírgula, UTF-8
com BOM opcional, CRLF/LF, campos entre aspas e aspas duplicadas. Não há um formato
alternativo inventado com colunas que não foram observadas. Novos exports exigem
inspeção de amostras e extensão explícita do adapter.

O formato de corrida não informa `season_id`/`section_index`, nem prova encerramento
oficial; `state=full` não é tratado como prova de resultado final. O formato de membros
não informa clã nem instante da captura. `lastSeen` é atividade do jogador, não
instante de exportação. O nome do arquivo e seu horário de modificação não constituem
identificação confiável. `clan_finish_time` também não identifica temporada/seção.

## Identidades e preservação

- Guerra existente: `war(clan_id, season_id, section_index)`.
- Participação existente: `war_participant(war_id, player_id)`.
- Jogador: `player.player_tag`, normalizando somente o prefixo `#` ausente.
- Captura de membros: `clan_membership_history`, vinculada a uma coleta oficial
  ou, no backfill, ao lote e à resolução que confirmam clã e data de captura.

Na importação inicial, jogadores desconhecidos ficam pendentes; não são criados
nem associados ao elenco. A incorporação confirmada pode criar um jogador histórico
inativo com a tag real do CSV, sem inventar associação ao elenco atual.
Uma pontuação isolada não autoriza atribuir uma linha a uma guerra. A Fase 2
compara o conjunto de participantes e exige confirmação explícita do alvo.
Na análise inicial, novos/duplicados/conflitos ficam em zero **não classificados**, acompanhados
da contagem de pendências e de aviso. Zero nesses contadores não comprova ausência
de conflito. Campos vazios continuam vazios na quarentena; não viram valores zero.

Upload e identificação não alteram o histórico funcional. A incorporação explícita
pode acrescentar guerras, participações e capturas; nunca cria eventos de associação,
altera elenco atual ou executa avaliações/premiações. `HistoryStore.saveMembers` e `saveWarLog` não são reutilizados
como importadores: seus efeitos incluem atualização de elenco e substituição de
participantes oficiais. Agendamentos e comunicação com a Supercell são preservados.

## API e confirmação

Todos os endpoints `/api/imports/**`, inclusive consulta de auditoria, exigem ADMIN.
Os POSTs mantêm sessão e CSRF existentes; não há exceção de segurança para multipart.

- `POST /api/imports/royaleapi/preview`: multipart `file`. Retorna `analysis` e
  `previewToken`. Não grava no banco. Identifica jogadores conhecidos, valida campos,
  lista motivos e informa se o SHA-256 já tem um lote confirmado.
- `POST /api/imports/royaleapi`: multipart `file` e `previewToken`. Exige os mesmos
  bytes e nome analisados, na mesma sessão, em até 15 minutos. Uma nova prévia invalida
  a anterior. A confirmação revalida o conteúdo e registra pendências atomicamente.
- `GET /api/imports/royaleapi?page=0&size=10`: histórico paginado; tamanho até 50.

Limites: 512 KiB por arquivo, 600 KiB por requisição multipart, 2.000 registros,
4.096 caracteres por campo, nome até 180 caracteres sem caminhos/controles. Extensão
`.csv` é obrigatória, mas MIME/extensão não substituem validação do conteúdo. Arquivos
vazios, binários, UTF-8 inválido, cabeçalhos desconhecidos, aspas e colunas malformadas
são rejeitados integralmente. Campos numéricos inválidos aparecem na prévia e impedem
confirmação de todo o arquivo. Falha de escrita causa rollback do lote e das linhas.

Conteúdo é tratado como texto/dados, nunca executado; não há download de CSV nem
interpretação de fórmulas de planilha. Logs não incluem conteúdo dos arquivos.

## Auditoria e reenvio

V8 adiciona `import_batch` e `import_row`, com triggers imutáveis reutilizando a
proteção de auditoria existente. O lote registra origem `ROYALEAPI_IMPORT`, nome,
SHA-256, data/hora real da importação, usuário autenticado, formato, status e contagens.
Linhas preservam campos e motivos; a data da importação não é data do dado histórico.
A auditoria participa dos backups PostgreSQL existentes.

A restrição única `(clan_tag, checksum)` e lock transacional por checksum protegem
reenvios concorrentes. Repetir o mesmo arquivo retorna o lote anterior com
`alreadyProcessed=true`, sem duplicar dados. Nomes diferentes não contornam o checksum.
Lotes com conteúdo rejeitado não são gravados. O histórico de tentativas HTTP não é
um novo mecanismo de logging de dados pessoais.

## Fase 2 — identificação auditável

Na ação **Identificar dados**, o ADMIN consulta os candidatos e confirma um alvo.
O serviço isolado `RiverIdentityMatcher` usa somente dados já persistidos em `war`,
`war_participant`, `player` e `river_race_snapshot`. Não consulta serviços externos.
Não usa nomes, elenco ativo ou limite de 50 participantes; ex-membros permanecem.

### River Race

O fingerprint SHA-256 inclui clã e participantes ordenados por tag normalizada,
com `fame`, `decksUsed`, `boatAttacks` e `repairPoints`. Ausência permanece distinta
de zero. Tags duplicadas invalidam a identificação do conjunto. Nome, contador diário,
`player_contribution`, filename e horário de upload não são chaves de corrida.

- `EXACT_MATCH`: um único alvo oficial tem conjunto completo idêntico, todos os quatro
  valores conhecidos e alguma contribuição/deck positivo. A unicidade refere-se ao
  histórico disponível, não à totalidade da Supercell. Mesmo assim exige confirmação.
- `POSSIBLE_MATCH`: candidatos com pelo menos 80% das tags do CSV em comum, no mínimo
  três coincidências (ou todas quando há menos de três linhas), e algum valor
  comparável. É um limiar explícito de triagem, não prova de identidade. Dois alvos
  com fingerprint igual permanecem ambíguos; nenhum é escolhido automaticamente.
- `UNRESOLVED`: não há candidato que atenda à triagem, o lote tem tags duplicadas ou
  pertence a outro clã. A confirmação de alvo fica indisponível.

Ordenação: fingerprint completo igual, menos incompatibilidades, mais tags coincidentes,
mais registros integralmente idênticos, identificador como desempate determinístico.
A ordenação não elimina candidatos nem confirma o primeiro. Não há recorte de datas
por filename e nenhuma guerra é criada para acomodar um CSV.

Cada candidato apresenta tipo/ID, datas oficiais, temporada/seção disponíveis,
período/tipo de snapshot, tags comuns/exclusivas, fame e decks idênticos, registros
integralmente iguais, campos desconhecidos e diferenças com seus valores. A contagem
de valores maiores/incompatíveis é por **campo**, não por jogador.

Valor oficial maior é compatível com uma captura anterior, mas não comprova ordem
temporal: o CSV não tem capturedAt. Valor importado maior é incompatível com progressão
crescente; confirmar esse alvo registra `CONFLICT`, preservando os valores oficiais.
Os demais alvos confirmados ficam `IDENTIFIED`, inclusive associações possíveis
confirmadas pelo ADMIN, cuja incerteza continua explícita no método e nas evidências.

Snapshots têm seção/período, mas não seasonId; uma associação a snapshot mantém
seasonId nulo e não inventa uma guerra publicada. Nem a captura oficial do snapshot
nem a publicação da guerra são copiadas para capturedAt do CSV de corrida.
O registro da resolução conserva o alvo e seu contexto oficial.

### Membros e datas

O clã configurado só é associado após confirmação. O parser de filename aceita
`clan-TAG-AAAA-MM-DD-HH-mm-N.csv` do mesmo clã e sugere uma data local até o minuto.
O componente final N é ignorado mesmo quando maior que 59. Datas inválidas e filenames
fora do padrão não geram sugestão. A sugestão não tem fuso implícito.

O ADMIN confirma data, clã e fuso IANA apresentado/editável (o padrão vem das regras
analíticas atuais). O servidor converte para UTC; datas futuras, anteriores à época,
horários inexistentes/ambíguos por horário de verão e fusos inválidos são rejeitados.
`lastSeen` e upload nunca são usados como capturedAt. O método é:

- `USER_CONFIRMED_FILENAME`: a data local coincide exatamente com a sugestão confirmada.
- `USER_ENTERED`: data digitada ou alterada explicitamente.

`MATCHED_OFFICIAL_SNAPSHOT` não é emitido para membros: não há comparação automática
de capturas de membros nesta fase. Sugerir não cria resolução nem grava no banco.

### Auditoria, estados e API

A V9 adiciona `import_resolution`. Cada resolução guarda batchId, revisão, autor da
sessão, horário, método, alvo, temporada/seção quando existentes, capturedAt/fuso quando
aplicáveis, motivo, confirmação manual, sugestões, versão da política, fingerprints,
lista dos candidatos considerados e evidências do alvo selecionado.

Métodos River: `USER_CONFIRMED_EXACT_MATCH` e `USER_CONFIRMED_POSSIBLE_MATCH`.
Rejeitar/reabrir usa `USER_REVIEW`. Estados: `PENDING_IDENTIFICATION`, `IDENTIFIED`,
`CONFLICT`, `REJECTED`. Corrigir, rejeitar ou reabrir acrescenta revisão; não atualiza
nem apaga o lote, linhas ou resoluções anteriores. O histórico de importações apresenta
o estado da última revisão, enquanto as contagens de entrada continuam preservadas.

- `GET /api/imports/royaleapi/{batchId}/identification`: sugestões, candidatos, avisos,
  `evidenceVersion` e histórico, somente ADMIN e sem gravações.
- `POST` no mesmo endereço: JSON com `requestKey`, `expectedRevision`, `evidenceVersion`,
  `action` (`IDENTIFY`, `REJECTED`, `PENDING_IDENTIFICATION`), `confirmed=true` e motivo.
  River exige `candidateKey` apresentado; membros exigem `clanTag`, `capturedAtLocal`,
  `timezone` e `timestampOrigin` aceito. Sessão ADMIN e CSRF continuam obrigatórios.

A confirmação recalcula as evidências. Mudança da proposta oficial ou da revisão
retorna 409 e exige atualizar; não aceita IDs arbitrários enviados pelo browser.
Lock transacional por lote serializa decisões concorrentes. Reenvio idêntico com
mesma requestKey/autor retorna a mesma resolução; conteúdo diferente retorna conflito.
Triggers imutáveis reutilizam a proteção de auditoria anterior. Uma resolução usa
as evidências preservadas naquele momento; alterações oficiais posteriores não
reescrevem sua decisão. A Fase 3 revalida o histórico e a resolução antes de incorporar dados.

## Fase 3 — incorporação confirmada

Em Operações → Dados externos, lotes `IDENTIFIED` oferecem **Incorporar ao histórico**.
A prévia informa analisados, novos, duplicados, conflitos, ignorados, jogadores com novos
registros e o período comprovado. Depois da confirmação, o resultado real permanece
consultável. “Incorporação com pendências” nunca é anunciada como sucesso total.

### Modelo e prioridade

V10 estende **as mesmas tabelas** `war`, `war_participant` e `clan_membership_history`
com `source`, `batch_id`, `resolution_id` e `imported_at`. Não cria um modelo paralelo
de guerras. Registros anteriores continuam `DIRECT_API`. Capturas importadas não
inventam `collection_run`: seu `run_id` é nulo, com proveniência obrigatória. Capturas
oficiais continuam exigindo runId. O horário da incorporação não é capturedAt histórico.

`import_application` e `import_application_row` preservam execução, autor, resolução,
contagens, valores normalizados e motivos por linha. Os triggers imutáveis mantêm essa
auditoria mesmo quando a Supercell passa a ocupar o registro funcional correspondente.
Os valores originais do arquivo continuam em `import_row`.

A leitura única `HistoricalReadService` usa as views `effective_war`,
`effective_war_participant` e `effective_membership_history`. Estas exigem aplicação
confirmada vinculada à última resolução IDENTIFIED para utilizar registros importados.
Rejeitar/reabrir/corrigir uma resolução remove seus dados anteriores da leitura; a
correção precisa de nova incorporação. O contexto importado da guerra também precisa
continuar válido. Nenhum registro funcional excluído vira zero no analytics.

Prioridade por identidade: **Supercell → RoyaleAPI confirmado**. Valores derivados
continuam calculados no módulo analytics; não concorrem com os registros de origem.
Membros são identificados por clã, playerId e instante confirmado, com uma observação
preferencial nesse instante. Um oficial prevalece integralmente, inclusive campos nulos.
Participações mantêm a chave única `war_id + player_id`. O contexto da guerra mantém
`clan_id + season_id + section_index`.

Campos relevantes iguais: duplicado, sem novo registro funcional. Diferença, inclusive
CSV preenchido contra campo oficial nulo: conflito, sem preenchimento complementar.
Nomes são descritivos, não chave nem critério de conflito de contadores. Divergência
contra outro backfill ativo também fica em conflito; não há substituição silenciosa.
Tags reais validadas podem originar um cadastro histórico inativo, sem ingresso,
saída, cargo atual ou tempo de associação inventados.

### Guerras e limites da identidade

A Fase 3 usa **somente** o alvo e as evidências confirmados na Fase 2; não faz matching.
Para WAR, exige seasonId, sectionIndex e publicação oficial preservados na resolução,
revalida a publicação se a guerra existir e reconcilia cada participação. Uma lacuna
local pode ser reconstruída usando essa identidade oficial anteriormente comprovada,
sem criar temporada/seção/data por estimativa. Não há caminho para criar uma guerra
nunca identificada pela Fase 2 a partir de um CSV sem esses metadados.

O teste de guerra ausente preserva uma resolução válida, simula a ausência do histórico
funcional, incorpora o CSV e depois executa uma coleta oficial da mesma identidade.
A coleta passa a fornecer a linha preferencial sem duplicar a guerra; o CSV anterior
fica na auditoria. Novas linhas oficiais substituem participações importadas homônimas;
participações importadas ainda válidas e não presentes no payload oficial permanecem
com sua proveniência, sem transformar ausência oficial em zero.

Um alvo **SNAPSHOT** não fornece seasonId/publicação nem prova resultado final. Suas
linhas são ignoradas na incorporação analítica desta fase, com motivo explícito. Não
convertemos snapshots de corrida em guerras encerradas, mesmo com state=full. Um CSV
ligado a WAR também pode conter contadores parciais: valores diferentes do oficial
são conflitos; backfill novo tem cobertura incompleta explicitamente sinalizada.

`captured_at` de guerras/participações incorporadas registra o recebimento local,
nunca a data da batalha; `imported_at` e a interface deixam isso explícito. O agrupamento
analítico continua usando a publicação oficial confirmada. Início/fim permanecem nulos.

### Membros, doações e analytics

Membros exigem data de captura e clã confirmados. Preservam troféus, cargo histórico,
doações, doações recebidas, ranks e lastSeen; vazio continua nulo. Não há evento automático
de associação nem atualização da tabela de elenco ativo por causa da incorporação.

Analytics, perfil, comparação, guerras e evolução consomem a mesma leitura preferencial.
Médias, quatro guerras recentes, tendência, participação, candidatos, destaques e Frota
Score usam as fórmulas existentes. `DonationCalculator` continua calculando diferenças,
resets, episódios e semanas; o contador bruto nunca é somado como total de doações.
A revisão semanal operacional continua usando capturas oficiais, e uma guerra de backfill
não aciona o fechamento automático como se fosse publicação oficial recém-coletada.
A incorporação não dispara ou reescreve premiações, promoções ou expulsões.

Métricas diretas indicam `DIRECT_API` ou `ROYALEAPI_IMPORT`; derivadas mantêm `DERIVED`
ou `SNAPSHOT_DERIVED`, com `sources` e `dataUsed` identificando as origens, lotes e
resoluções. Backfill não comprova completude; as métricas afetadas sinalizam a limitação.
Ausência não vira zero e a disponibilidade individual de decks continua desconhecida.

### API, transação e idempotência

- `GET /api/imports/royaleapi/{batchId}/backfill/preview`: ADMIN, leitura consistente,
  sem gravação no banco; guarda um comprovante da prévia na sessão por 15 minutos.
- `POST /api/imports/royaleapi/{batchId}/backfill`: ADMIN + CSRF, JSON
  `resolutionId`, `evidenceVersion`, `confirmed=true`. Exige a prévia na mesma sessão.

O servidor trava o lote e usa a **mesma chave PostgreSQL de exclusão por clã da coleta**.
Uma coleta/incorporação concorrente retorna 409 para nova tentativa. Resolução ou
histórico alterados invalidam a confirmação e exigem nova prévia. Falha causa rollback
de aplicação, linhas de auditoria e novos dados funcionais. Cada par lote/resolução
é incorporado uma vez; reenvio retorna a execução preservada, sem duplicação.

A coluna **Histórico / Analytics** separa importação, identificação e incorporação.
`Analytics: Sim/Não` é calculado das linhas atualmente elegíveis, não das contagens
originais: pode virar Não após chegada oficial, rejeição ou correção. Contagens de
incorporação permanecem como auditoria da execução. A confirmação não é uma garantia
de que todos os registros da aplicação continuarão usados para sempre.

Não existe scraping, download ou sincronização automática com RoyaleAPI.
