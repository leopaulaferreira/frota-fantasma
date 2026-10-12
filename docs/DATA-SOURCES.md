# Origem e limites dos dados

A fonte de produção é a [API oficial do Clash Royale](https://developer.clashroyale.com/#/documentation). A validação de contratos usa respostas simuladas nos testes e consultas reais controladas quando há credencial disponível. Não se assume que todos os endpoints legados estejam disponíveis para todos os clãs.

## Contratos implementados na Fase 1

| Origem | Campos atualmente expostos |
| --- | --- |
| Clã | `tag`, `name`, `description`, `type`, `clanScore`, `clanWarTrophies`, `requiredTrophies`, `donationsPerWeek`, `members` |
| Membro | `tag`, `name`, `role`, `expLevel`, `trophies`, `clanRank`, `previousClanRank`, `donations`, `donationsReceived` |
| Sistema local | `observedAt` nas entidades: instante UTC da coleta, não atividade no jogo |

Campos numéricos opcionais usam tipos anuláveis. Valores ausentes não são convertidos em zero. Campos externos extras são ignorados sem quebrar a desserialização. Os DTOs internos são independentes das entidades JPA.

## Contratos a validar na Fase 2

Inspecionar, quando disponíveis, current river race, histórico de river races/warlog, guerra atual e perfil de jogador. Confirmar os campos de identificação de temporada/período, participantes, pontuação e decks antes de definir as migrations. Não transformar `currentwar` ou outro endpoint legado em dependência obrigatória sem confirmar suporte.

Vitórias, derrotas, batalhas e mudanças de troféus só serão armazenadas como valores fornecidos se estiverem presentes na fonte. Dados derivados terão origem e fórmula identificáveis. Perfis individuais serão consultados quando necessários, sem fazer uma requisição por jogador a cada abertura de tela.

## Dados derivados dos snapshots

| Informação desejada | Tratamento planejado |
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

A Fase 1 não fornece esses indicadores históricos. A coleta futura não reconstrói acontecimentos anteriores à primeira observação quando a API não disponibiliza esse histórico.
