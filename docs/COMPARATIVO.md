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

## Como usar

No menu **Comparativo**, a página abre as últimas quatro guerras oficiais encerradas
e somente membros do último elenco completo. **Quatro guerras anteriores** percorre
blocos de quatro; **Período mais recente** retorna; **Últimas quatro** restaura o padrão.
O seletor manual permite até quatro guerras, inclusive importadas identificadas como
contexto parcial. Sem quatro oficiais disponíveis, a amostra menor é explicitada.
A seleção vazia permanece vazia até o usuário escolher uma guerra.

A tabela ordena as guerras da mais recente para a mais antiga, com temporada/seção,
publicação e datas efetivas quando disponíveis. O jogador permanece visível na
rolagem horizontal. Clique na linha ou no botão com o nome para abrir o diálogo;
Escape fecha o diálogo. O perfil e as evidências das coletas permanecem acessíveis.

Filtros e seleção persistem na URL: `wars` (IDs separados por vírgula), `offset`,
`q`, `former`, `rule`, `performance` e `sort`. Limpar filtros conserva o período.
Exemplo: `/comparativo?rule=repeat&sort=averageAsc`.
“Incluir ex-membros” acrescenta apenas participantes registrados nas guerras
selecionadas que não constam do último elenco completo. Nomes iguais e apelidos
alterados são associados pela tag; o nome atual tem preferência.

## Critérios e limites

- Meta visual: 1.600 pontos. Abaixo da meta só recebe destaque de falta com
  resultado oficial, pontuação conhecida e vínculo elegível.
- Média e total usam pontos oficiais registrados, independentemente de comprovação de vínculo integral, dentro da
  seleção. O denominador aparece ao lado da média; não busca guerras extras para
  preencher lacunas. Sem valores válidos, média e total são desconhecidos.
- Reincidência usa a função compartilhada `recurrence` de `warAnalysis.ts`.
  Exige as duas guerras mais recentes da seleção, adjacentes no arquivo, separadas
  por até oito dias, ambas oficiais, elegíveis e abaixo da meta. Não pula registros
  ausentes para fabricar uma sequência. Seleções arbitrárias não criam consecutividade.
- A função compartilhada `eligible` protege reingressos: o episódio atual não
  penaliza guerras anteriores. Para ex-membros com saída registrada, conserva-se
  o episódio imediatamente anterior à saída. Vínculo desconhecido é insuficiente.
- Zero ataques é zero explícito em `decksUsed`, com fonte oficial; zero pontos não comprova zero ataques. Filtros “zero ataques” e
  “menos de 16 decks” referem-se à última guerra selecionada.
- “16/16 nas quatro” exige exatamente quatro guerras com registros oficiais de 16 decks
  registrados em cada uma. É referência de utilização, não disponibilidade presumida.
- Histórico insuficiente: menos de quatro guerras, pontos/fonte inválidos
  ou decks desconhecidos em alguma coluna. Isso pode coexistir com reincidência
  confirmada nas duas guerras recentes; a situação prioriza a evidência de revisão.
- Média >= 2.400 é um filtro de desempenho, não uma regra nova de promoção.
  Doações e avaliação humana continuam necessárias.
- Indicadores resumem os jogadores filtrados. Desconhecidos ficam no fim da
  ordenação; empates usam tag. O filtro de desempenho crescente/decrescente
  determina a ordenação pela pontuação da última guerra.
- Evolução compara pontos válidos em guerras adjacentes. O gráfico necessita
  de duas amostras válidas e mantém lacunas. O diálogo também mostra diferenças
  entre colunas selecionadas, explicitando que podem não ser guerras consecutivas.
- Datas são exibidas no fuso das regras retornadas pelo analytics. Não se inventam
  datas de batalha a partir de publicação. Doações conservam seu próprio período,
  origem e cobertura, independentemente da seleção de guerras.

Não há chamadas de escrita na página. Guardião, decisões, notas, regras de
promoção e histórico formalizado não são recalculados por esta funcionalidade.
O volume de consultas cresce com o arquivo de guerras e eventos: os índices são
paginados, os detalhes limitados a quatro e compartilhados no cache de consultas.
Falhas nas fontes necessárias bloqueiam a tabela, com opção de tentar novamente.

## Validação executada

- `npm --prefix frontend test`: 35 testes aprovados, incluindo nove do comparativo.
- `npm --prefix frontend run build`: TypeScript e build de produção aprovados.
- `npm --prefix frontend run test:e2e -- war-comparison.spec.ts war-center.spec.ts weekly-review.spec.ts analytics.spec.ts`:
  68 testes aprovados em desktop e mobile. Cobrem navegação, seleção histórica,
  filtros combinados, persistência na URL, diálogo, estados de erro e regressão.
- Após ajuste de largura da tabela, os seis testes de navegador do comparativo
  foram executados novamente e aprovados; capturas desktop/mobile foram geradas.
- `mvn -q -f backend/pom.xml -Dtest=AnalyticsEngineTest,DonationCalculatorTest,WeeklyParticipationTest,WeeklyReviewEngineTest,RecommendationIntegrationIT,HistoryStoreIT,HistoricalBackfillIT test`:
  62 testes aprovados, sem skips, incluindo proteção do fechamento do Guardião e
  idempotência de premiações formalizadas. Integrações usam bancos de teste.

### Resultado registrado e exigibilidade

Pontuação oficial e decks registrados continuam visíveis e entram nas estatísticas descritivas mesmo quando o ingresso recente impede confirmar obrigação integral. Essa proteção continua exigida para reincidência e destaque vermelho por falta. Um zero oficial é uma amostra de valor zero, não ausência de dados. O texto “Histórico em formação · N/4 guerras” identifica amostras menores; “Coleta incompleta” indica lacunas em guerras elegíveis. Sem pontuação oficial conhecida, permanece “Dados insuficientes”. A média pode incluir episódios anteriores, mas eles não confirmam reincidência no episódio atual.

## Auditoria de resultados registrados — 6 de novembro de 2026

A verificação encontrou uma mistura entre resultado conhecido e participação
exigível: médias e filtros descartavam números oficiais quando o vínculo integral
não era comprovado. A correção separa essas dimensões:

- Média, total, meta atingida e filtros de pontos usam a pontuação oficial conhecida.
- Filtros de decks e quatro registros de 16 decks usam os decks oficiais conhecidos,
  independentemente da disponibilidade de pontuação ou de ataques exigíveis.
- Reincidência continua exigindo vínculo elegível. Saída durante a batalha,
  fonte desconhecida, guerras duplicadas ou ordem invertida não confirmam sequência.
- Importações e ausência de registro não passam a ser resultados oficiais ou zeros.
- Exibição de histórico parcial não constitui recomendação de expulsão.

No recorte somente leitura de 06/11/2026 às 20:09 UTC, havia 50 membros atuais,
quatro guerras oficiais na seleção padrão e 13 guerras no arquivo. O cruzamento
identificou 42 resultados oficiais de 42 membros que a exigência de vínculo
excluía das estatísticas; 11 membros ficavam sem média. Foram verificados 25
seleções históricas, 2.553 combinações jogador/período e 2.000 combinações de filtros.
Esses números representam esse recorte, não uma garantia de cobertura futura.

### Reproduzir a auditoria somente leitura

Na raiz do projeto, com os serviços locais ativos:

```bash
docker compose exec -T db sh -c 'psql -qAt -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < scripts/audit-comparativo.sql > /tmp/comparison-audit-data.json
frontend/node_modules/.bin/vite-node frontend/scripts/auditWarComparison.ts /tmp/comparison-audit-data.json
```

A consulta usa transação READ ONLY e não modifica dados. O JSON contém histórico
real: mantenha-o fora do Git. O verificador cruza média, total e amostra com
pontuações oficiais brutas, verifica os filtros e a proteção contra reincidência.
Ele verifica consistência com o que foi coletado; não comprova atividade que a
fonte não registrou. Datas de batalha ausentes e cobertura parcial continuam
sendo limitações explícitas.

Na rodada final: 44 testes unitários frontend e build de produção aprovados.
O caso do recém-chegado com zero oficial tem regressão automatizada tanto no
cálculo quanto no navegador. Nenhuma premiação ou decisão persistida foi alterada.
