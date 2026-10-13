# Analytics e apoio à gestão

Implementação `analytics-v2`, com [histórico e regras da Fase 4](RECOMMENDATIONS.md). Nenhum endpoint promove, expulsa ou altera o cargo de membros. Os resultados são calculados dos dados persistidos, em uma leitura consistente do PostgreSQL. Não há chamadas à Supercell ao abrir um relatório. Resultados e títulos deste relatório são recalculáveis/provisórios. A Fase 4 acrescenta avaliações congeladas e um registro separado de premiações definitivas.

## Validação real das fontes

Antes dos cálculos, foram consultados com sucesso (HTTP 200), em 12/10/2026, os endpoints oficiais do clã configurado: `/clans/{tag}`, `/members`, `/currentriverrace` e `/riverracelog`. O [registro de validação](API-VALIDATION.json) contém horários, formatos e contagens, sem token nem nomes dos jogadores. Na amostra havia 38 membros, todos com `lastSeen`, e dez entradas de guerras. Isso valida os campos observados, não garante que futuras respostas nunca os omitam.

Campos usados: `fame`, `decksUsed`, `seasonId`, `sectionIndex`, `createdDate`, `donations`, `trophies`, `role`, `tag` e `lastSeen`. A V3 acrescenta `api_last_seen` nos snapshots. Linhas antigas mantêm NULL: não se reconstrói atividade passada a partir do horário da coleta. `player.lastSeenAt` continua sendo a última observação local de associação, não login.

A Supercell documenta [quatro dias de batalha e limite diário de participantes](https://support.clashroyale.com/hc/en-us/articles/49484904148891-About-Clan-Wars) e [quatro decks com reset diário](https://support.clashroyale.com/hc/en-us/articles/49484904440987-War-Decks). O padrão resultante é 16 usos por guerra. Os resultados reais inspecionados tinham `decksUsed` entre 0 e 16. **Isso não prova que cada jogador teve 16 decks disponíveis**: ingresso, saída, elegibilidade e bloqueios de participação não são fornecidos integralmente pelos endpoints.

## Contrato e endpoints

- `GET /api/analytics`: relatório completo, incluindo regras efetivas e versão.
- `GET /api/analytics/players`: jogadores observados, incluindo ex-membros.
- `GET /api/analytics/players/{tag}`: análise individual; codificar `#` como `%23`.
- `GET /api/analytics/clan`: indicadores do elenco observado.
- `GET /api/analytics/highlights`: três títulos alocados juntos, sem repetir jogador.
- `GET /api/analytics/promotions`: avaliação dos membros de cargo `member`, com candidatos e motivos de inelegibilidade.
- `GET /api/analytics/evolution`: comparação semanal.

Todos aceitam `?week=YYYY-MM-DD`, uma segunda-feira não futura. Padrão: semana corrente no fuso analítico. A semana é `[segunda 00:00, segunda seguinte 00:00)`. Guerras entram pela **data de publicação**, pois a API não informa início/fim confiáveis nos contratos validados. Não confundir a semana de publicação com os dias em que as batalhas aconteceram. Um resultado histórico usa o conhecimento persistido disponível hoje, incluindo correções/importações posteriores; não é uma reprodução auditável do que o sistema sabia naquela época.

Toda métrica contém `value`, `origin`, `quality`, `period`, `warsConsidered`, `insufficientSample`, `incompletePeriod`, `dataUsed` e `reason`. `dataUsed` identifica linhas de snapshots/guerras/eventos e valores utilizados. O período informa sua base: semana civil, timestamps de publicação ou snapshot. Datas de guerra desconhecidas permanecem nulas.

`quality`: `AVAILABLE`, `INSUFFICIENT_DATA`, `INCOMPLETE` ou `UNAVAILABLE`. O consumidor deve exibir **dados insuficientes** quando `insufficientSample=true` e mostrar valores parciais apenas com essa ressalva. `value=null` não é zero. Uma média disponível com amostra pequena não autoriza conclusão de consistência. Período incompleto não impede apresentar um limite inferior observado, mas deve aparecer junto ao valor.

## Catálogo de métricas e fórmulas

| Campo / indicador | Origem | Fórmula ou limite |
| --- | --- | --- |
| `wars[].points` | DIRECT_API | `fame` da guerra oficial; ausente continua nulo |
| `wars[].decksUsed` | DIRECT_API | `decksUsed` oficial |
| `trophies` | DIRECT_API | Valor do último snapshot de membros |
| `lastKnownActivity` | DIRECT_API | `lastSeen` oficial do snapshot; timestamp futuro é inválido para análise |
| `offlineDays` | DERIVED | Dias completos entre `lastSeen` e captura; nunca acumular dias offline só porque a coleta parou |
| `recentPoints`, `recentAverage` | DERIVED | Soma de `fame` conhecido e divisão pelo número de pontuações conhecidas nas últimas quatro guerras **do clã** |
| `historicalAverage` | DERIVED | Média das pontuações conhecidas em todas as guerras disponíveis |
| `warsParticipated` | DERIVED | Número de guerras do histórico com `fame>0` ou `decksUsed>0` |
| `recentDecks` | DERIVED | Soma dos decks conhecidos nas últimas quatro guerras |
| `standardDeckTargetPercent` | DERIVED | `100 × decks / (16 × guerras com decks conhecidos)`; parâmetro de comparação, não elegibilidade individual |
| `deckUtilizationPercent`, `fullDeckUtilizationWars` | UNAVAILABLE | Disponibilidade efetiva por jogador não fornecida com segurança |
| `completeStandardWars` | DERIVED | Guerras com exatamente 16 decks observados |
| `partialStandardWars` | DERIVED | Guerras com 1–15 decks; não afirma que o jogador poderia ter usado os restantes |
| `zeroParticipationWars` | DERIVED | Exige `fame=0` **e** `decksUsed=0` explícitos |
| `recentParticipation`, `analyzedWars` | DERIVED | Participações positivas / guerras disponíveis, até quatro; pode ser `3/4`, `2/3` etc. |
| `bestPoints`, `worstPoints` | DERIVED | Máximo/mínimo de pontuações conhecidas, incluindo zero explícito |
| `recentTrendPoints` | DERIVED | Média das duas mais recentes menos média das duas anteriores; exige quatro pontuações |
| `warsBelowTarget` | DERIVED | Quantas pontuações conhecidas ficaram abaixo da meta configurada |
| `wars[].participation`, `participation` | DERIVED | Presença positiva, zero explícito, padrão completo/parcial e tamanho de amostra |
| `observedTenureDays` | SNAPSHOT_DERIVED | Dias observados no episódio atual desde último JOIN/BASELINE; reingresso reinicia, BASELINE não é ingresso real |
| `weeklyDonations`, `donations[].observedDonations` | SNAPSHOT_DERIVED | Limite inferior por diferenças comparáveis; veja resets abaixo |
| `historicalContext`, `operationalStatus`, `elderCandidate` | SNAPSHOT_DERIVED | Combinação de guerras, episódio de associação, atividade e doações; regras abaixo |
| `currentMembers` | DIRECT_API | `members` no último snapshot do clã |
| `newMembers`, `departures` | SNAPSHOT_DERIVED | Eventos JOIN/LEAVE; BASELINE não conta como novo membro |
| `averageRecentPoints`, `participationPercent` do clã | DERIVED | Média por par jogador/guerra conhecido; taxa de participações positivas entre pares conhecidos |
| `totalRecentDecks` do clã | DERIVED | Soma de decks conhecidos do elenco observado atual |
| `observedWeeklyDonations` do clã | SNAPSHOT_DERIVED | Soma de limites inferiores dos membros atuais, com cobertura explícita |
| `membersBelowTarget` | DERIVED | Membros atuais com pontuação explícita abaixo da meta na última guerra publicada |
| `membersOffline` | DERIVED | Contagem com lastSeen válido/recente e dias offline acima do limite; cobertura incompleta identificada |
| `membersToObserve`, `membersToReview` | SNAPSHOT_DERIVED | Contagens das classificações do elenco observado |
| Evolução: `averagePoints`, `totalDecks`, `participationPercent`, `standardDeckTargetPercent`, `averagePointsChange` | DERIVED | Participantes de guerras publicadas em cada semana, incluindo ex-membros; variação só entre semanas adjacentes |
| Evolução: `observedDonations`, `newMembers`, `departures` | SNAPSHOT_DERIVED | Snapshots/eventos da semana, incluindo ex-membros |
| Capitão | DERIVED | Ordenação de pontuações da guerra |
| Alma Imortal | SNAPSHOT_DERIVED | Ordenação de métricas de guerra e doações derivadas de snapshots como desempate |
| Guardião da Frota | SNAPSHOT_DERIVED | Ordenação do limite inferior de doações observadas na semana |

Não interpolar guerras ausentes, não buscar quatro participações antigas para esconder ausências recentes, não deduzir zero de um jogador ausente do payload. Se `decksUsed>16`, preservar o valor oficial e deixar a comparação com o padrão indisponível; nunca truncar silenciosamente para 100%.

## Doações e resets

Os contadores ficam preservados em cada `clan_membership_history`. Para pares consecutivos na mesma semana e no mesmo episódio de associação:

- Contador crescente/igual: somar `atual - anterior`.
- Contador menor: registrar reset observado, começar novo segmento e somar `atual`, nunca subtrair.
- Primeiro snapshot da semana: estabelecer base; não creditar seu contador como diferença observada.
- Mudança de semana, reingresso, valor ausente ou timestamps iguais: não conectar os contadores.

Isso evita interpretar reset como perda. Não resolve resets ocultos entre capturas, doações antes do primeiro snapshot ou depois do último: o total fica marcado como **limite inferior / período incompleto**, mesmo em semana encerrada. Dois snapshots comparáveis são o mínimo. Valores nulos e uma única observação produzem dados insuficientes. Uma queda pode também refletir correção da fonte: o sistema conserva os valores e evidencia o segmento, sem afirmar horário exato do reset. O calendário configurado organiza a análise; não é uma garantia do instante de reset da Supercell.

## Participação, contexto e status

Amostra padrão: quatro guerras. Uma única guerra ruim não gera conclusão de baixa participação nem alerta recorrente. Ausência no payload é desconhecida. Zero explícito comprova contribuição zero registrada, não prova que o jogador estava elegível durante todos os dias.

Contextos: `NOVO` requer JOIN observado recente; BASELINE recente gera pouca amostra/tempo observado, sem inventar data de ingresso. `ANTIGO_CONSISTENTE`, `ANTIGO_EM_QUEDA`, `IRREGULAR` e `ATUALMENTE_INATIVO` usam tempo observado, tendência, recorrência e `lastSeen` válido. `AMOSTRA_INSUFICIENTE` indica que não é possível distinguir os casos com segurança.

- **MANTER:** amostra suficiente e ausência dos alertas abaixo.
- **OBSERVAR:** baixa atividade a partir de `max(1, offlineDays-2)` dias (três no padrão); amostra insuficiente; coleta antiga/atividade desconhecida; duas ou mais guerras abaixo da meta, parciais no padrão ou com zeros explícitos. Uso parcial não é prova de negligência.
- **REVISAR:** `lastSeen` indica ao menos cinco dias offline em captura recente; ou, com amostra suficiente e associação observada anterior à janela, ao menos dois zeros explícitos ou três guerras abaixo da meta. A associação anterior usa uma margem conservadora de sete dias antes da publicação mais antiga, identificada como aproximação, não data oficial de início.

Não se infere ausência de justificativa pessoal. O líder recebe a evidência para revisar o contexto. Ex-membros não recebem status operacional. Captura antiga não continua aumentando artificialmente dias offline. A janela de alertas é a mesma de até quatro guerras; não há sanções automáticas. A Fase 4 registra avaliações e usa alertas persistidos como critério adicional para candidatura.

## Destaques e candidatos

A alocação semanal usa **Capitão → Guardião → Alma**, removendo cada escolhido antes da categoria seguinte. Empates finais usam tag crescente para garantir determinismo. Apenas membros do elenco observado no corte são elegíveis. Sem candidato/dados, o título fica vazio com motivo. A mesma resposta nunca concede dois títulos ao mesmo jogador. As listas são recalculadas conforme chegam dados; não representam títulos concedidos definitivamente.

- **Capitão:** maior pontuação positiva da última guerra publicada na semana selecionada; mostrar pontos e decks. Não reutiliza uma guerra antiga como título de semana nova.
- **Guardião:** maior limite inferior positivo de doações na semana. Provisório: lacunas diferentes podem alterar o ranking real.
- **Alma:** exige amostra mínima configurável de guerras completas no padrão de 16; dados de pontos/decks presentes na janela e nenhum zero explícito. Ordena por quantidade de completas, total de decks, média, doações e tag. Doações desconhecidas ficam atrás das conhecidas; não viram zero.
- **Ancião:** somente `member`; participação positiva em todas as guerras da janela, amostra mínima, média >= meta, uso do padrão >= percentual configurado, tempo observado mínimo, doações observadas >= mínimo e status MANTER. Retorna avaliação e razões para candidatos e não elegíveis. Cumprir um limite inferior de doações já prova atingir o mínimo, mas não torna o período completo.

## Configuração

As regras efetivas aparecem em `rules` e `recommendationRules` no relatório. Havendo uma versão persistida, ela prevalece sobre os padrões do ambiente. `ruleVersion=analytics-v2` identifica as fórmulas; os valores efetivos devem acompanhar qualquer relatório exportado.

| Variável | Padrão | Validação |
| --- | --- | --- |
| `ANALYTICS_POINTS_TARGET` | 1600 | >=1 |
| `ANALYTICS_OFFLINE_DAYS` | 5 | >=1 |
| `ANALYTICS_MINIMUM_WARS` | 4 | 2–4 |
| `ANALYTICS_IMMORTAL_MINIMUM_WARS` | 4 | 2–4 |
| `ANALYTICS_REVIEW_LOW_WARS` | 3 | 2–4 |
| `ANALYTICS_ADEQUATE_STANDARD_DECK_PERCENT` | 80 | 1–100; critério de candidatura |
| `ANALYTICS_PROMOTION_TENURE_DAYS` | 28 | >=1 |
| `ANALYTICS_PROMOTION_DONATIONS` | 100 | >=0 |
| `ANALYTICS_FRESHNESS` | PT2H | >=1 minuto |
| `ANALYTICS_TIMEZONE` | UTC | ZoneId válido; calendário analítico |

As configurações são repassadas pelo Compose e validadas na inicialização. Alterar o fuso muda agrupamentos de doações/publicações: comparar relatórios exige manter as regras e o fuso usados.
