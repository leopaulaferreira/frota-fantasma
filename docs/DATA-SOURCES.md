# Origem e limites dos dados

A fonte de produção é a [API oficial do Clash Royale](https://developer.clashroyale.com/#/documentation). A validação de contratos usa respostas simuladas nos testes e consultas reais controladas quando há credencial disponível. Não se assume que todos os endpoints legados estejam disponíveis para todos os clãs.

## Contratos implementados

| Origem | Campos atualmente expostos |
| --- | --- |
| Clã | `tag`, `name`, `description`, `type`, `clanScore`, `clanWarTrophies`, `requiredTrophies`, `donationsPerWeek`, `members` |
| Membro | `tag`, `name`, `role`, `expLevel`, `trophies`, `clanRank`, `previousClanRank`, `donations`, `donationsReceived` |
| Sistema local | `capturedAt`, `snapshotDate` e `observedAt`: instantes UTC de observação, não atividade no jogo |

Campos numéricos opcionais usam tipos anuláveis. Valores ausentes não são convertidos em zero. Campos externos extras são ignorados sem quebrar a desserialização. Os DTOs internos são independentes das entidades JPA.

## Corrida atual e resultados oficiais

A coleta implementa `currentriverrace` e `riverracelog`, com contratos simulados nos testes. A corrida atual preserva `sectionIndex`, `periodIndex`, tipo/estado e participantes, além do payload interpretado. Resultados oficiais preservam `seasonId`, `sectionIndex`, `createdDate`, classificação, alteração de troféus e participantes. `fame` é armazenado como contribuição; decks, ataques a barcos e reparos permanecem anuláveis quando ausentes.

O identificador local da guerra é distinto da chave oficial `(clã, seasonId, sectionIndex)`. `createdDate` representa publicação, não início/fim exatos. Datas de início/fim, batalhas, vitórias e derrotas permanecem nulas quando a fonte não as fornece. Snapshots da corrida não são vinculados por suposição a uma temporada do histórico.

O fechamento semanal consulta o histórico oficial; somente entradas publicadas são importadas. A coleta periódica busca publicações atrasadas. Perfis externos e `currentwar` legado ainda não são dependências implementadas: `/api/players/{tag}` expõe apenas o jogador observado nas coletas locais.

## Dados derivados dos snapshots

| Informação desejada | Tratamento atual ou previsto para analytics |
| --- | --- |
| Entrada/saída | Comparar duas listas completas coletadas com sucesso; primeira coleta é linha de base. Falha ou resposta parcial não significa saída |
| `firstSeenAt` / `lastSeenAt` | Primeiro/último registro observado no clã; não equivalem a data exata de ingresso ou último login |
| Última atividade / 5 dias inativo | Usar timestamp oficial se disponível; caso contrário, apenas evidências de alterações em snapshots. Sem evidência suficiente: atividade desconhecida, com indicação da limitação |
| Doações semanais | Respeitar o período/reset do contador; não somar valores cumulativos repetidos. Registrar lacunas e mudança de período |
| Histórico de troféus/cargos | Série dos valores observados; mudanças entre coletas têm precisão limitada ao intervalo |
| Média e participação em 4 guerras | Identificar quatro guerras/períodos únicos, evitando somar snapshots da mesma guerra. Exibir tamanho da amostra e períodos faltantes |
| Decks disponíveis | Derivar das regras confirmadas do período e elegibilidade/tempo no clã; não presumir 64 decks para novos membros ou dados incompletos |
| Pontuação por guerra | Guardar atualizações da mesma guerra sem contá-las como guerras novas; consolidar somente após fechamento confirmado |
| Alertas/recomendações | Usar regras versionadas, período da análise, estatísticas e motivo. Não aplicar decisão automática |

Snapshots e eventos sustentam o [módulo analítico implementado](ANALYTICS.md). Consulte o [registro de validação real](API-VALIDATION.json): `lastSeen` existe na resposta de membros observada e é armazenado a partir da V3. Valores antigos ausentes continuam desconhecidos. A coleta futura não reconstrói acontecimentos anteriores à primeira observação quando a API não disponibiliza esse histórico.
