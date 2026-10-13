# Fase 4 — avaliações e recomendações auditáveis

A aplicação continua sem promover, expulsar ou mudar cargos na Supercell. Nesta fase, as escritas são locais: versões de regras, avaliações, status e títulos. A migração V4 acrescenta tabelas sem alterar os snapshots nem as migrations anteriores.

## Regras versionadas

`GET /api/recommendations/rules` retorna a versão ativa, com `id`, `revision`, `createdAt`, `reason` e `settings`. Antes da primeira gravação, a revisão é 0, com `id=null`, e usa os valores `ANALYTICS_*` do ambiente. O Score começa desabilitado, a janela de alertas é 28 dias e Alma Imortal exclui participação parcial.

`POST /api/recommendations/rules` cria uma versão e a ativa em uma única transação. Enviar a configuração completa, o motivo e o `expectedVersion` lido anteriormente. Se outra requisição tiver alterado a versão ativa, retorna 409. Uma nova versão não edita versões anteriores nem reescreve avaliações. Após haver versão persistida, ela prevalece sobre alterações no ambiente.

Exemplo de primeira versão (antes de existir `id` ativo):

```json
{
  "expectedVersion": null,
  "reason": "Ativar Score com critérios iniciais de gestão",
  "settings": {
    "analytics": {
      "pointsTarget": 1600,
      "offlineDays": 5,
      "minimumWars": 4,
      "immortalMinimumWars": 4,
      "reviewLowWars": 3,
      "adequateStandardDeckPercent": 80,
      "promotionTenureDays": 28,
      "promotionDonations": 100,
      "freshness": "PT2H",
      "timezone": "UTC"
    },
    "score": {
      "enabled": true,
      "warWeight": 45,
      "decksWeight": 35,
      "donationsWeight": 20,
      "warTarget": 3200,
      "donationTarget": 200
    },
    "recentAlertDays": 28,
    "immortalAllowPartial": false
  }
}
```

Os pesos são inteiros entre 0 e 100 e devem somar 100, mesmo com Score desabilitado. Metas de normalização devem ser positivas. `recentAlertDays` aceita 1–365. As validações dos critérios analíticos seguem [ANALYTICS.md](ANALYTICS.md). O fuso deve permanecer igual ao da configuração inicial para que as semanas de premiação sejam comparáveis; mudar o calendário do histórico não faz parte desta API.

`GET /api/recommendations/rules/history?page=0&size=20` lista versões anteriores, da mais recente para a mais antiga. Para restaurar critérios antigos, copie suas configurações e crie outra versão com o `expectedVersion` atual e um motivo; o histórico permanece intacto.

## Avaliação persistida e histórico de status

`GET /api/analytics` continua sendo uma consulta sem escrita. Usa a versão ativa, inclui `recommendationRules` e acrescenta Score, cores e histórico de alertas. Abrir o dashboard não cria alertas nem avaliações.

Para registrar uma avaliação:

```http
POST /api/recommendations/evaluations
Content-Type: application/json

{"week":"2026-10-12","idempotencyKey":"avaliacao-2026-10-12-1"}
```

`week` é opcional e segue a semana civil/de publicação definida no analytics. `idempotencyKey` é obrigatória, não vazia, até 100 caracteres e exclusiva por clã. Repetir a mesma chave/semana devolve a avaliação original, inclusive se as regras ou os dados tiverem mudado; para avaliar novamente use outra chave. Reutilizar a chave para outra semana retorna 409.

A resposta contém:

- `id`, semana, instante de criação e `analysisCutoff` (instante final dos dados considerados).
- ID da versão das regras e `formulaVersion=analytics-v2`.
- O relatório completo congelado: métricas, períodos, amostras, dados utilizados, limitações, motivos, candidatos e destaques provisórios.

Cada membro atual tem um `member_status` ligado à avaliação, com MANTER/GREEN, OBSERVAR/YELLOW ou REVISAR/RED e sua evidência completa. Ex-membros não recebem status operacional. Avaliações e status são gravados juntos; uma falha reverte a transação. As fontes são lidas em um snapshot consistente do PostgreSQL. Não há acesso à Supercell durante essa operação.

Consultas:

| Endpoint | Resultado |
| --- | --- |
| `GET /api/recommendations/evaluations` | Resumos paginados das avaliações |
| `GET /api/recommendations/evaluations/{id}` | Relatório exato que foi armazenado, sem recalcular |
| `GET /api/recommendations/players/{tag}/history` | Status, cor, período e evidências do jogador ao longo das avaliações |

Listagens usam `page>=0` e `size=1..100`, padrão 20, com `items` e `totalElements`. Tags devem codificar `#` como `%23`.

Não há job novo que crie avaliações automaticamente nesta fase: o POST é o ponto explícito de registro. Coletas continuam seguindo seus próprios agendamentos. Ausência de avaliações não é evidência de ausência de problemas.

## Alertas recentes e candidatos a Ancião

Além dos critérios existentes, a análise busca status OBSERVAR ou REVISAR **persistidos** na janela configurada. A janela considera `analysisCutoff`, não a data em que uma guerra foi importada. Alertas posteriores ao corte solicitado são excluídos; avaliações retroativas preservadas podem acrescentar evidência a esse período em consultas futuras.

`recentAlerts` contém a quantidade de avaliações com alerta, seus IDs, status e cortes. Várias avaliações da mesma guerra não são contadas como várias guerras: essa contagem só bloqueia candidatura, não agrava o status atual. Zero significa nenhum alerta registrado, sem afirmar vigilância contínua. Cada alerta preserva as regras originais; mudar as regras não apaga alertas anteriores.

Um membro pode estar MANTER hoje e ainda ter `elderCandidate=false` devido ao histórico recente. A razão informa explicitamente o bloqueio. O histórico não transforma automaticamente MANTER em REVISAR, e o Score não substitui os critérios de candidatura. Ao sair da janela, o alerta deixa de bloquear novas análises; avaliações anteriores permanecem imutáveis.

## Frota Score opcional

Não há novos campos externos inventados. Usa somente as métricas baseadas nos contratos reais já [validados na Fase 3](API-VALIDATION.json): `fame`, `decksUsed` e diferenças entre snapshots de `donations`. A origem do total é `SNAPSHOT_DERIVED`; as parcelas preservam suas origens.

Para cada parcela, `contribuição = peso × min(1, max(0, valor / meta))`:

| Parcela | Peso inicial | Entrada | Meta inicial |
| --- | --- | --- | --- |
| `frotaScoreWar` | 45 | Média recente de pontos | 3200 pontos |
| `frotaScoreDecks` | 35 | Percentual do **padrão** de 16 decks | 100% do padrão |
| `frotaScoreDonations` | 20 | Doações observadas na semana | 200 |

`frotaScore` soma as parcelas, de 0 a 100. Cada parcela expõe valor de entrada, meta, peso, fórmula, período, amostra e evidências. As janelas de guerra e doações são distintas e aparecem nos componentes.

- Componente necessário sem dado/amostra suficiente: total nulo, `INSUFFICIENT_DATA`.
- Não renormaliza pesos nem substitui dado ausente por zero.
- Peso zero exclui explicitamente uma parcela; sua contribuição zero decorre da regra, não de um dado inventado.
- Doações incompletas tornam o total `INCOMPLETE`, um limite inferior segundo as regras atuais.
- Score desabilitado: total nulo e `UNAVAILABLE`, com motivo de configuração.
- Percentual de decks é referência de desempenho, não disponibilidade individual. `deckUtilizationPercent` continua indisponível quando a fonte não sustenta seu cálculo.

## Alma Imortal com participação parcial

`immortalAllowPartial=false` mantém a exigência de guerras completas no padrão. Quando explicitamente habilitado, admite guerras com uso positivo de decks, respeitando a amostra mínima e excluindo zeros e valores desconhecidos. A ordenação continua priorizando completas, total de decks, média, doações e tag. A razão do título registra a opção usada. A elegibilidade individual para usar decks não é inferida.

## Premiações semanais definitivas

Primeiro gere e consulte uma avaliação para uma semana encerrada. Depois:

```http
POST /api/recommendations/evaluations/{id}/finalize-awards
```

A operação formaliza os vencedores que já constam dessa avaliação, sem recalcular com regras novas. Requer:

- Semana de publicação encerrada no fuso original.
- Avaliação gerada com corte no fim dessa semana; uma avaliação feita no meio da semana não serve, mesmo se hoje a semana já terminou.
- Pelo menos um título elegível, com valor e amostra suficiente. Categorias sem candidato permanecem sem concessão.

O fechamento é local, não encerra uma guerra na Supercell: as guerras usadas pelo analytics vêm de resultados oficiais publicados. Períodos de doações podem continuar incompletos; formalizar o vencedor observado **não transforma suas métricas em dados completos**. As limitações ficam congeladas junto com a premiação.

`GET /api/recommendations/awards/{week}` consulta o lote definitivo, com `evaluationId`, `finalizedAt`, fuso, vencedores e evidências. Os destaques de `/api/analytics` continuam sendo simulações provisórias e podem mudar; para títulos concedidos, use este endpoint.

A ordem Capitão → Guardião → Alma e os desempates da Fase 3 são preservados. Uma semana tem somente um lote definitivo; um jogador tem no máximo um título nessa semana. Repetir a formalização da mesma avaliação retorna o lote existente. Formalizar outra avaliação da mesma semana retorna 409. Não existe operação de sobrescrita ou exclusão de premiações nesta fase.

## Integridade e testes

Locks transacionais PostgreSQL por clã serializam alterações de regras, registros e formalizações entre instâncias. Chaves únicas protegem idempotência e exclusividade dos títulos. FKs vinculam avaliação, versão, clã e semana. Triggers impedem UPDATE/DELETE de versões, avaliações, status e premiações; somente o ponteiro de versão ativa é atualizado.

A suíte cobre pesos inválidos, normalização e limites do Score, dados insuficientes, snapshots incompletos, revisão concorrente, histórico imutável, alertas anteriores, idempotência, formalizações concorrentes, semanas abertas e ausência de candidatos. A API permanece no escopo local do Compose; autenticação/autorização para publicação externa continuam na etapa de deploy.
