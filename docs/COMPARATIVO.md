# Comparativo de guerras

## Diagnóstico

A rota será `/comparativo`, separada de `/comparar` e `/guerras`.
As consultas existentes são suficientes; não é necessária migração ou nova API.

- `GET /api/wars` e `/api/wars/{id}`: histórico efetivo de guerras encerradas,
  com precedência oficial em `HistoricalReadService`. Identidade por temporada/seção,
  participantes associados pelo ID do jogador e expostos por tag.
- `GET /api/clan/members`: último elenco completo persistido, não ausência em coleta parcial.
- `GET /api/clan/events`: eventos JOIN, BASELINE e LEAVE com instante observado.
- `GET /api/analytics`: métricas e doações com origem, período e cobertura.
- A corrida em andamento está em `/wars/current`, separado do histórico, e não
  será consultada para o comparativo.

Persistência: `war`, `war_participant`, `membership_event`,
`clan_membership_history` e views `effective_*`. Não serão modificadas.
Pontuação (`contribution`) e decks (`decksUsed`) são campos distintos.
Zero pontos não confirma zero ataques. Datas de publicação não são datas de batalha.

A elegibilidade existente em `warAnalysis.ts` exige vínculo observado anterior
ao início oficial ou, sem início, sete dias antes da publicação. Reingresso
reinicia esse vínculo; BASELINE não prova a data real de entrada.
Importações são contexto e não confirmam reincidência. Lacunas não são zeros.
O comparativo não altera regras de expulsão, promoções, premiações ou decisões.
