# Central de análise de guerras

Rotas `/guerras` (captura parcial atual) e `/guerras/{id}` (histórico preferencial).
A página reutiliza `/wars`, `/wars/current`, `/wars/{id}`, `/clan/members`,
`/clan/events` e `/analytics`. Apenas GETs; não modifica resultados, decisões,
premiações ou regras de expulsão. `AnalyticsDtos` permanece compatível.

## Filtros e navegação

Filtros combináveis, seleção da segunda guerra, período, ordenação e colunas
ocultas ficam na query string. A guerra principal fica no caminho da URL.
“Limpar filtros” limpa a query, mantendo a guerra principal.

- Vínculo: atuais participantes, ex-membros participantes, participantes
  registrados na guerra e elenco atual incluindo quem não tem registro.
  No último caso, registros inexistentes mantêm pontos/decks desconhecidos.
  “Todos” preserva todos os participantes, incluindo ex-membros.
- Pontos: zero explícito, abaixo de 1.600, pelo menos 1.600 ou intervalo inclusivo.
- Decks: 0, 1–4, 5–8, 9–15 e 16. Não presume disponibilidade de 16 ataques.
- Reincidência: duas guerras zeradas (pontos), duas sem ataques (decks), duas
  consecutivas abaixo da meta ou três das quatro últimas abaixo da meta.
- Doações: zero confirmado, abaixo de 50 ou intervalo inclusivo. Exige cobertura;
  valores desconhecidos ou parciais não comprovam uma faixa ou zero.
- Período: 2, 4 (padrão), 6 ou 10 guerras. Os gráficos individuais e a média
  continuam identificados como últimas quatro; a janela de reincidência e o
  histórico visual respeitam o período selecionado.
- Pesquisa por nome ou tag. A identidade, junções e desempates sempre usam tag.

Arquivo paginado carregado para o seletor, deduplicado por temporada/seção com
preferência oficial. Detalhes consultados somente para a janela e comparação.
Ex-membro significa ausente do último elenco coletado, não necessariamente
fora do clã no instante da batalha. Participantes registrados são evidência de
registro na guerra, não prova de disponibilidade integral dos ataques.

## Indicadores e comparação

Cards: soma observada das contribuições, meta atingida, zero decks explícito,
decks observados e variação percentual frente à comparação (anterior por padrão).
A amostra informa quantos registros têm pontos/decks conhecidos. Sem nenhum
valor conhecido, indicadores são desconhecidos, não zero.

A comparação permite duas guerras históricas ou uma captura atual parcial e
uma guerra histórica. Mostra `fame` do clã, `clanScore`, soma individual, média
por jogador com pontos, zero ataques, meta e decks. Esses números têm origens
distintas; a soma individual não substitui score nem medalhas oficiais do clã.
Variação percentual só existe com ambas as somas completas e base diferente de
zero. Populações distintas podem influenciar a variação. Melhora/piora compara
apenas tags presentes em ambos os registros com pontuação conhecida.

A tabela tem colunas ocultáveis e ordenáveis, valores desconhecidos ao fim e
tag como desempate. A média informa a amostra conhecida dentre quatro guerras;
registros ausentes não entram como zero. O diálogo de evidências existente foi
extraído para componente compartilhado e conserva dados técnicos e acessibilidade.

Ao expandir, gráficos separados mostram pontos e decks, com meta de 1.600 no
gráfico de pontos, tendência, valores e fonte em tabela acessível. O histórico
do clã mostra somas observadas por guerra; barras não inventam dados faltantes.

## Integridade e limitações

A captura atual nunca entra como guerra oficial na reincidência. Importações
continuam identificadas e não servem para confirmar reincidência. Os históricos
usam a precedência oficial já definida pelo backend. Publicação, captura e datas
efetivas da batalha são identificadas separadamente; datas ausentes não são
inventadas.

Para reincidência, o último JOIN/BASELINE/LEAVE deve comprovar vínculo anterior
ao início da guerra, considerando o episódio no instante analisado. Sem início
oficial, usa-se margem conservadora de sete dias antes da publicação. BASELINE
não comprova data real de ingresso. Reingresso recente, eventos ausentes e
participante ausente não viram faltas. As duas guerras consecutivas não podem
estar separadas por mais de oito dias; lacunas interrompem confirmação.

Doações são contexto da semana retornada pelo analytics atual, explicitamente
indicada na tela; não são atribuídas à batalha histórica pela data de publicação.
Resets, diferenças observadas, fonte e cobertura vêm de `DonationPeriod` e
`Metric` existentes. Atualmente o cálculo de diferenças costuma informar
`INCOMPLETE`; nesse caso a coluna mostra o limite inferior, mas os filtros de
zero/faixa confirmada não incluem o jogador. Um filtro vazio pode significar
falta de cobertura, não ausência de problemas. As evidências explicam a limitação.

## Validação

Vitest: ausência/zero, limites, nomes iguais com tags distintas, reingresso,
importação e lacunas. Playwright: filtros combinados e URL após reload, colunas,
comparação, diálogo de evidências, gráficos, parcial/oficial e responsividade.
Testes existentes de navegação histórica e empates foram adaptados à nova tabela.
Backend: AnalyticsEngineTest, DonationCalculatorTest, HistoryStoreIT e
HistoricalBackfillIT, sem alterações nos contratos ou regras de backend nesta entrega.
