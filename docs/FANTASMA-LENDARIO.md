# Fantasma Lendário

Premiação semanal independente, disponível em `/premiacoes`, inicialmente experimental.
Reconhece a maior **superação do recorde individual do Caminho de Troféus**, condicionada à participação na guerra. Não altera a alocação dos três títulos tradicionais.

## Regras

- Superação = recorde atual − recorde anterior. Evolução líquida = troféus atuais − troféus da coleta anterior; esta última é apenas informativa.
- Mínimo de 1.600 medalhas e exatamente 16 decks na guerra oficial correspondente.
- Não ser líder; continuidade observada durante o ciclo completo; coleta anterior válida na semana imediatamente anterior, com mesma origem e versão do recorde.
- Superação estritamente positiva. Desempate: superação decrescente, medalhas decrescentes, tag crescente.
- Vitórias consecutivas são permitidas. A primeira coleta não produz vencedor.
- Exemplo: recorde 11.000, coleta anterior 10.500, coleta atual 11.200 → evolução líquida +700, superação +200.
- Se faltar um perfil, não se confirma um vencedor parcial: o membro sem dados poderia ter um resultado superior. Valores ausentes permanecem nulos.

## Arquitetura e persistência

O repositório utiliza **PostgreSQL 17 e Flyway**, não MySQL. A migration `V11__legendary_ghost.sql` preserva essa arquitetura.

- `legendary_cycle`: chave `(clan_tag, week)`, encerramento, prazo, origem/versão do recorde, início da versão, limite validado de troféus, modo experimental/oficial, guerra, vencedor e resultado JSONB imutável.
- `legendary_snapshot`: chave `(clan_tag, week, player_tag)`, nome na coleta, cargo, continuidade, troféus, `bestTrophies` bruto, recorde adotado, instante, clã do perfil, tentativas e código da última falha. Origem e versão são herdadas do ciclo.
- A tag identifica o jogador; mudanças de nome não dividem seu histórico.
- Constraints evitam duplicatas. Triggers protegem capturas bem-sucedidas e ciclos finalizados contra alteração/exclusão.
- Bloqueio consultivo PostgreSQL por clã coordena réplicas. Chamadas HTTP não mantêm uma transação SQL aberta. Cada perfil bem-sucedido é persistido imediatamente e não é consultado novamente naquele ciclo.
- Reutiliza `ClashRoyaleClient`, seus timeouts/retries/controle de 429, `CollectionCoordinator`, o histórico oficial de membros e os participantes das guerras. Não adiciona regras ao `AnalyticsEngine` nem altera `AnalyticsDtos`.

## Coleta automática

`LegendaryScheduler` usa segunda-feira às **06:00 em America/Sao_Paulo**, o mesmo fechamento de `AutomaticAwardsService`. `week` identifica a segunda de encerramento; o intervalo de participação começa sete dias antes.

1. Congela o último elenco oficial observado antes do encerramento, com no máximo duas horas de idade.
2. Consulta `GET /v1/players/{playerTag}` para cada membro desse elenco. A tag é codificada no caminho.
3. Aceita capturas apenas entre o fechamento e o prazo configurado (padrão: 08:00 de segunda). Uma resposta que chegue depois do prazo é rejeitada.
4. Reutiliza o fechamento da guerra para atualizar o log oficial. Os perfis são capturados **antes** de esperar a publicação desse log.
5. A cada 15 minutos, retoma apenas perfis pendentes dentro da janela e tenta associar a guerra oficial. Se a guerra atrasar, conserva os troféus já capturados; não os substitui pelos valores posteriores.
6. Finaliza quando houver dados suficientes ou quando os prazos expirarem. A guerra pode chegar até o próximo fechamento; sem uma associação única nesse intervalo, não há vencedor.

O serviço não reconstrói semanas antigas com perfis atuais. Se estiver desligado durante a janela, registra a ausência, sem consultar troféus retroativos. `COLLECTION_ENABLED=false` desativa os agendamentos; `LEGENDARY_ENABLED=false` desativa também a coleta manual desta categoria.

Continuidade: exige evidência de presença próxima ao início do ciclo e ausência de eventos `JOIN`/`LEAVE` até a captura. O clã no perfil deve continuar sendo o clã configurado. Registros importados não são usados nessa comprovação. Entradas/saídas entre duas consultas podem não ser observadas; trata-se de continuidade observada, não de uma garantia fornecida pela Supercell.

## Campos da API e validação

Consulta real realizada em **25/11/2026** com credencial local, sem gravar tokens no repositório. Uma amostra distribuída por seis posições do elenco apresentou:

| Troféus no elenco | `trophies` do perfil | `bestTrophies` do perfil |
| ---: | ---: | ---: |
| 14.000 | 14.000 | 14.000 |
| 12.596 | 12.596 | 12.979 |
| 11.192 | 11.192 | 11.251 |
| 10.076 | 10.076 | 10.076 |
| 8.543 | 8.543 | 8.543 |
| 8.086 | 8.086 | 8.115 |

`trophies` coincidiu com os troféus individuais retornados no elenco. Resultados de `currentPathOfLegendSeasonResult` são separados e não entram no DTO desta funcionalidade. Troféus sazonais, posição no clã, troféus de guerra e outros modos também não entram no cálculo. As medalhas e os decks vêm exclusivamente de `war_participant` da guerra oficial selecionada.

A documentação oficial distingue o Caminho de Troféus regular do sazonal: o progresso regular permanece, enquanto troféus sazonais são reiniciados ([Supercell Support](https://support.supercell.com/clash-royale/en/articles/seasonal-trophy-road-2-2.html)). As mudanças históricas reforçam a necessidade de versionamento ([atualização de dezembro de 2025](https://supercell.com/en/games/clashroyale/blog/release-notes/december-update-2025/)). O [portal oficial da API](https://developer.clashroyale.com/api-docs/index.html) não forneceu uma descrição textual verificável da semântica histórica de `bestTrophies` durante esta análise.

**Limite da validação:** não foi possível comparar diretamente com a tela do jogo, nem comprovar que `bestTrophies` nunca conserva valores de versões anteriores da Ladder. Portanto, o padrão é `OBSERVED`: maior valor observado pelo sistema, explicitamente identificado assim na interface. O recorde usa o máximo de `trophies` do perfil, das capturas oficiais de membros desde `era-start` e dos recordes anteriores da mesma origem/versão. Picos entre fechamentos são preservados quando observados pela coleta de membros. `bestTrophies` é armazenado para auditoria, mas não participa desse cálculo.

`OFFICIAL_API` é uma opção para uso após validação no jogo: exige `bestTrophies` presente, maior ou igual a `trophies`, dentro do limite validado e sem regressão em relação ao histórico da mesma versão. Falha não provoca troca automática de origem. Mudanças de origem, versão ou início da versão exigem uma nova base. Valores além do limite ou regressões oficiais bloqueiam a captura e sinalizam revisão; não há corte artificial de troféus.

## Configuração e ativação oficial

| Variável | Padrão | Uso |
| --- | --- | --- |
| `LEGENDARY_ENABLED` | `true` | Habilita a funcionalidade de coleta |
| `LEGENDARY_OFFICIAL` | `false` | Novos ciclos são experimentais |
| `LEGENDARY_RECORD_SOURCE` | `OBSERVED` | Alternativa: `OFFICIAL_API`, após validação |
| `LEGENDARY_RECORD_ERA` | `trophy-road-v1` | Identificador da versão semântica da Ladder |
| `LEGENDARY_ERA_START` | `2026-11-25T00:00:00Z` | Início do histórico comparável |
| `LEGENDARY_TROPHY_LIMIT` | `14000` | Limite regular validado; não inclui troféus sazonais |
| `LEGENDARY_CAPTURE_WINDOW` | `PT2H` | Janela após o fechamento; máximo de seis horas |

Para ativar oficialmente, após avaliar os resultados experimentais:

1. Defina `LEGENDARY_OFFICIAL=true` no `.env`.
2. Recrie o backend local: `docker compose up -d --no-deps backend`.
3. Confira o próximo ciclo na página `/premiacoes`. A mudança vale para ciclos criados depois da ativação, sem reclassificar semanas anteriores.

Essa opção torna o resultado **oficial no acompanhamento da categoria**. O projeto não possui um serviço de envio automático de mensagens ao clã; esta implementação não cria nem envia mensagens. Qualquer integração futura deve consumir apenas ciclos finalizados, `official=true`, `status=COMPLETE` e `winnerTag` não nulo. Os três títulos existentes continuam com sua alocação original.

Se a Supercell alterar a semântica ou reiniciar o Caminho de Troféus, pause a coleta, valide os campos, ajuste o limite quando necessário e crie uma nova `record-era` e `era-start`. Uma simples expansão do limite, sem mudar o significado dos troféus, não exige descartar o histórico. Nunca modifique os ciclos antigos.

## Endpoints e interface

- `GET /api/legendary-awards?week=YYYY-MM-DD&page=0`: consulta autenticada, sem chamadas externas; histórico paginado em blocos de 52 semanas.
- `POST /api/legendary-awards/collect`: administrador com CSRF; retoma a coleta dentro da janela permitida. Não força uma coleta retroativa nem finaliza as outras premiações.
- `/premiacoes`: card com identidade visual existente, origem do recorde, resultado, critérios, tabela de doze colunas, elegibilidade/motivos e seleção de semanas. “—” representa dado ausente.

Estados: `COLLECTING`, `AWAITING_WAR`, `COMPLETE`, `INCOMPLETE`, `MISSED`. Nenhum estado incompleto confirma vencedor. Os dados sintéticos usados em testes ficam exclusivamente nos testes; não há seed de produção.

## Verificação

Comandos reproduzíveis:

```sh
mvn -B -f backend/pom.xml clean verify -Pintegration
npm run test --prefix frontend
npm run build --prefix frontend
npm run test:e2e --prefix frontend -- --project=desktop
```

Testes cobrem cálculo, exemplos do requisito, recuperação de perdas, um troféu de superação, empate, liderança, decks, histórico insuficiente, picos intermediários, mudança de nome, saída/retorno, falhas e retomada, imutabilidade, concorrência, janela temporal, publicação tardia da guerra, isolamento de origem/versão, ativação oficial e autorização/CSRF. Integração usa PostgreSQL 17 em Testcontainers; testes HTTP do cliente verificam o endpoint e excluem troféus ranqueados.

Limitações operacionais: observações não capturam picos ocorridos inteiramente entre consultas; falhas de coleta podem deixar uma semana sem resultado; existe uma tolerância explícita de até duas horas após o encerramento, e a API não oferece o perfil retroativo exato daquele instante. A primeira comparação exige duas coletas semanais válidas.

Resultado da validação em 25/11/2026: **120 testes unitários + 93 de integração no backend**, **54 testes unitários no frontend** e **71 testes de navegador desktop** aprovados; um teste de geração de capturas permanece ignorado por padrão. Build TypeScript/Vite aprovado.

## Arquivos da implementação

Criados:

- `backend/src/main/java/com/frotafantasma/analytics/dto/clash/PlayerProfileDto.java`
- `backend/src/main/java/com/frotafantasma/analytics/legendary/LegendaryProperties.java`
- `backend/src/main/java/com/frotafantasma/analytics/legendary/LegendaryDtos.java`
- `backend/src/main/java/com/frotafantasma/analytics/legendary/LegendaryCalculator.java`
- `backend/src/main/java/com/frotafantasma/analytics/legendary/LegendaryRepository.java`
- `backend/src/main/java/com/frotafantasma/analytics/legendary/LegendaryService.java`
- `backend/src/main/java/com/frotafantasma/analytics/legendary/LegendaryScheduler.java`
- `backend/src/main/java/com/frotafantasma/analytics/legendary/LegendaryController.java`
- `backend/src/main/resources/db/migration/V11__legendary_ghost.sql`
- `backend/src/test/java/com/frotafantasma/analytics/LegendaryAwardIT.java`
- `backend/src/test/java/com/frotafantasma/analytics/legendary/LegendaryCalculatorTest.java`
- `backend/src/test/java/com/frotafantasma/analytics/legendary/LegendarySchedulerTest.java`
- `frontend/src/components/LegendaryAward.tsx`
- `frontend/e2e/legendary-award.spec.ts`
- `docs/FANTASMA-LENDARIO.md`

Modificados: `ClashRoyaleClient.java`, `ClashRoyaleClientTest.java`, `application.yml`, `compose.yaml`, `.env.example`, `frontend/src/pages/Recommendations.tsx`, `frontend/src/fleet.css`, `frontend/e2e/analytics.spec.ts` e `README.md`.

Verificação local após atualização: migration V11 aplicada, health `UP`, endpoint autenticado HTTP 200, modo experimental e origem `OBSERVED` confirmados. A página apresentou corretamente ausência de histórico, sem vencedor, sem erros JavaScript e sem transbordamento horizontal da página em 1440 px. Próxima janela: **30/11/2026 às 06:00 de São Paulo**; primeira comparação possível em **07/12/2026**, condicionada às duas coletas e à elegibilidade. Backup pré-migration: `backups/frota-20261126T020401Z.dump` (arquivo local, fora do Git).
