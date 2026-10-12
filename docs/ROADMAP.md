# Desenvolvimento por fases

O objetivo é apoiar a gestão humana do Frota Fantasma. Promoção e expulsão nunca serão executadas automaticamente. Cada fase deve terminar com testes adequados, correções, documentação e commits pequenos (`feat`, `fix`, `refactor`, `test`, `docs`, `chore`).

## Fase 1 — base do backend

- Java 21, Spring Boot, Web, JPA, PostgreSQL, Flyway, Jackson e Bean Validation.
- Integração oficial com token somente via ambiente no backend.
- GET `/api/clan` e `/api/clan/members`, DTOs e entidades separados.
- Timeouts configuráveis, retry limitado, rate limit e erros sanitizados.
- OpenAPI/Swagger, Compose e setup local documentados.
- Testes unitários, HTTP simulado e PostgreSQL com Testcontainers.

O retrato atual já persistido é uma base operacional. O histórico e os agendamentos pertencem à Fase 2.

## Fase 2 — histórico e coleta automática

Introduzir `Clan`, `Player`, `ClanMembershipSnapshot`, `War`, `WarParticipant` e eventos de associação em migrations novas, preservando o banco existente. Distinguir os identificadores locais dos identificadores realmente fornecidos pela API. Guardar captura UTC, origem, período e estado de completude dos dados.

Validar os contratos de guerra atual, river race, warlog e perfil. Requisições de atualização devem ser idempotentes; deduplicar guerra/período e impedir execuções sobrepostas dos jobs.

Intervalos iniciais configuráveis: clã/membros a cada 30 minutos; guerra a cada 15 minutos durante dias de guerra; snapshot diário; fechamento semanal após confirmação de término. Definir timezone e dias de guerra configuráveis. O fechamento não pode depender apenas do relógio se a API ainda não confirmou o resultado.

Critérios de validação: entradas/saídas apenas com coletas completas, cargos ao longo do tempo, repetição de jobs sem duplicações, indisponibilidade sem falsas saídas, preservação das migrations anteriores.

## Fase 3 — analytics

Implementar `PlayerWeeklyStats`, média de quatro guerras, totais por período, decks utilizados/disponíveis, doações semanais e evidências de atividade. Documentar fórmulas, amostra e tratamento de períodos incompletos conforme [DATA-SOURCES.md](DATA-SOURCES.md).

Expor dados para cards: membros, troféus de guerra, resultado atual, doações, participação, decks, abaixo da meta, inativos, novos membros e saídas. Validar reset de contadores, novos membros, ausência de histórico e fechamento semanal.

## Fase 4 — recomendações explicáveis

Persistir `MemberStatus` e regras versionadas/configuráveis. Valores iniciais solicitados: meta de 1600 pontos, limite de inatividade de 5 dias e janela de 4 guerras. Critérios de doações, decks e tempo no clã também serão configuração, com validação de faixas e unidades.

- **Promoções:** sugerir somente candidatos a Ancião; considerar cargo atual, mínimo de guerras, média, percentual de decks, doações, tempo de associação e ausência de alertas recentes. Exibir estatísticas, score e motivo; nunca aplicar promoção.
- **Observação:** verde/amarelo/vermelho com razões para pontuação baixa recorrente, decks não usados, inatividade inferida, zero doações e ausência recorrente. Distinguir informação desconhecida de falha comprovada.
- **Capitão da Guerra:** maior pontuação no período atual/finalizado identificado.
- **Guardião da Frota:** maior número de doações no período semanal identificado.
- **Alma Imortal:** participação nas quatro guerras, priorização de decks completos/quase completos, média e doações como critério adicional/desempate. Participação incompleta excluída por padrão, com opção explícita de configuração. Definir desempates determinísticos e mostrar os dados usados.
- **Frota Score:** opcional, normalizado de 0 a 100; pesos iniciais 45% guerra, 35% decks/participação e 20% doações. Validar soma dos pesos e metas de normalização. Explicar cada parcela e o tratamento de dados ausentes.

Antes de concluir: testes próprios para cada recomendação, empates, amostra insuficiente, membros novos, ausência recorrente e limites do score.

## Fase 5 — frontend

React + TypeScript + Vite + React Router + TanStack Query, consumindo apenas a API interna. Layout escuro, responsivo, naval e fantasmagórico, com componentes reutilizáveis e gráficos simples.

Telas: dashboard; membros com filtros por cargo/status e ordenação por guerra, doações, troféus, atividade, média e decks; guerra com ranking, metas, decks restantes e evolução; perfil/histórico individual; promoções; observação; destaques; configuração de regras e pesos.

Tratar carregamento, erro, rate limit, vazio, dados desatualizados e amostra insuficiente. Exibir explicações das recomendações e limites das estimativas junto aos indicadores.

Antes de implementar o visual, inspecionar `../cr-api-assets` ou clonar separadamente [RoyaleAPI/cr-api-assets](https://github.com/RoyaleAPI/cr-api-assets). Verificar licenças/avisos; selecionar apenas emblemas/ícones que ajudem membros, guerras e destaques; copiar somente os arquivos utilizados para o frontend e manter um registro de origem/licença. O repositório não será arquitetura-base nem dependência de runtime. Não reproduzir o site RoyaleAPI.

## Fase 6 — refinamento e deploy

Consolidar testes ponta a ponta, responsividade, acessibilidade, métricas, autenticação, autorização para editar regras, backups e restauração. Acrescentar screenshots reais e instruções do ambiente escolhido. Antes de publicação externa, definir destino de deploy e IP de saída permitido na Supercell; não presumir provedor ou conta.
