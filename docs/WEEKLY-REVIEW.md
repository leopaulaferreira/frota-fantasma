# Revisão semanal de participação e expulsões

A página `/expulsoes` é exclusiva da liderança (ADMIN). Reúne evidências para
revisão individual. Nenhuma classificação ou decisão executa expulsão no jogo.

## Problema encontrado e correção

O relatório aceitava apenas `periodType=warDay`. As capturas locais da semana de
26/10/2026 traziam `colosseum`, inclusive a captura de 01/11 às 21h55 em São Paulo.
Apesar de recente e com participantes, ela era descartada por `riverFresh`.
Isso removia os sinais de pontos e deixava principalmente os alertas offline.

A seleção agora busca a última captura de `warDay` **ou** `colosseum`, desde
quinta-feira da semana selecionada até o recorte. Uma captura posterior de
`training` não encobre a captura de guerra válida. A validade de uma hora,
a integridade do elenco e o limite de domingo continuam aplicados. Sem fonte
válida, participante ausente ou número explícito válido, o valor é desconhecido.
Não se reutiliza corrida anterior como pontuação da semana nem se inventa zero.

## Recorte histórico

O recorte é domingo às **22h em America/Sao_Paulo**. Antes dele, a prévia usa o
instante atual; depois, usa somente registros coletados até esse limite.
De segunda a quarta, a seleção padrão abre a semana anterior. De quinta a
domingo, abre a semana em andamento. Uma data explícita deve ser segunda-feira.

O elenco vem da última captura completa de `clan_history` e seu `run_id`,
com membros oficiais correspondentes. `membersFresh` e `riverFresh` exigem
fontes de até uma hora. A falta de corrida não impede um alerta offline
comprovado, e vice-versa. Atividade futura ou ausente não comprova inatividade.

Histórico oficial, participantes, eventos e doações também respeitam o recorte.
Uma guerra publicada ou capturada posteriormente não altera a evidência daquele
recorte. Se um registro oficial foi atualizado depois do recorte e sua versão
anterior não está disponível, ele não é reconstruído artificialmente.
Consultas são somente leitura, com transação REPEATABLE_READ. Não há migração,
reescrita de guerras, alterações em importações ou sobrescrita de decisões.

## Guerras, permanência e classificação

A tabela mostra a última guerra oficial conhecida e a captura parcial da corrida
selecionada. Ao expandir, exibe todas as guerras oficiais disponíveis no recorte,
com pontos, decks, publicação, captura, fonte e elegibilidade. Importações não
alimentam os sinais de dispensa desta revisão; continuam disponíveis nas áreas
de histórico. Dados oficiais não são sobrescritos por importações.

O vínculo atual é determinado pelo último evento JOIN, BASELINE ou LEAVE até o
recorte. BASELINE significa “observado desde”, não data real de entrada. Um JOIN
após LEAVE identifica reingresso. Menos de 14 dias observados identifica novato;
isso não prova que um membro de baseline entrou recentemente no jogo.

Uma guerra só conta como falta se o vínculo atual for observado antes do seu
início oficial. Quando a API não fornece datas de início/fim, a revisão exige,
conservadoramente, vínculo observado pelo menos sete dias antes da publicação.
Essa janela é uma regra de elegibilidade, não uma data oficial inventada.
Guerras anteriores ao vínculo atual ou disputadas durante ingresso não geram
faltas. Não se presume disponibilidade integral de ataques para recém-chegados.

- **Média:** até quatro guerras oficiais elegíveis com pontos disponíveis;
  exibe o tamanho da amostra. Não inclui a corrida parcial.
- **Abaixo da meta:** número dessas guerras com pontos estritamente menores
  que 1.600. A igualdade não aciona o sinal.
- **Duas guerras abaixo:** as duas últimas guerras oficiais conhecidas devem
  ser elegíveis e ter pontuação explícita abaixo de 1.600. Não pula lacunas.
- **Zero ataques consecutivos:** decks explicitamente iguais a zero nas guerras
  oficiais consecutivas mais recentes. Falta de participante, decks ausentes
  vínculo inelegível ou intervalo superior a oito dias entre publicações
  interrompem a sequência (não se pula uma semana ausente). Zero pontos não prova zero ataques.
- **Tendência:** comparação de decks das duas últimas guerras oficiais elegíveis.
  Sem ambos os valores, a tendência é insuficiente.

Grupos principais:

1. **Revisão prioritária:** duas últimas guerras abaixo da meta, duas ou mais
   guerras seguidas com zero decks, ou duas semanas completas com zero doações
   confirmado pelas capturas.
2. **Acompanhar:** sinal isolado de baixa pontuação, queda de decks ou mais de
   cinco dias offline. Também inclui membros com evidência suficiente sem
   reincidência, explicitamente sem recomendação de dispensa.
3. **Dados insuficientes:** sem evidência confiável para recomendar decisão,
   ou elenco sem validade no recorte. Falta de dados não indica falta do membro.

Os campos legados REVIEW/UNKNOWN/CLEAR continuam representando apenas os sinais
de pontos/offline do recorte, para compatibilidade e auditoria. O campo
`participation.group` é a classificação histórica exibida na tabela.

## Doações e cobertura

O detalhe traz a semana selecionada (parcial) e as quatro semanas de calendário
anteriores, com valores observados, último contador, número de capturas, resets
e qualidade. A tabela resume a semana completa anterior. O acumulado das quatro
semanas completas é um **limite inferior observado**, nunca um total exato.
Se qualquer semana não tiver comparações válidas, o acumulado é desconhecido.

As diferenças são calculadas apenas entre capturas oficiais dentro da mesma
semana, sem cruzar eventos de entrada/saída. O primeiro contador não é somado.
Queda de contador inicia novo segmento e soma o valor após reset. Não se soma
uma diferença entre semanas. Contadores ausentes ou negativos são desconhecidos.

“Zero confirmado pelas capturas” exige semana encerrada, vínculo anterior,
contadores todos explicitamente zero, nenhuma entrada/saída ou reset, ao menos
duas capturas e cobertura com lacunas de no máximo uma hora, inclusive nas
bordas da semana. É confirmação observacional: não prova o que ocorreu entre
capturas. Uma diferença zero com cobertura incompleta não aciona o filtro.
O filtro de duas semanas considera as duas semanas completas anteriores à
selecionada, e não a semana parcial em andamento.

## Tabela e filtros

Colunas: Jogador, Guerra anterior, Guerra atual, Decks, Doações, Tempo no clã,
Dias offline e Situação. Os cabeçalhos alternam ordenação crescente/decrescente;
valores desconhecidos ficam no fim. A numeração acompanha a seleção visível.
A ordem inicial põe zerados primeiro, depois membros abaixo da meta por decks
crescentes (1, 2, 3, 4…), pontos e tag. Um botão restaura essa ordem.

Filtros: duas guerras abaixo da meta; duas guerras sem ataques; zero doações nas
duas semanas completas; offline acima do limite (padrão cinco dias); novatos;
aguardando justificativa; revisados; dispensados. Também há filtros da captura
atual, meta ajustável de doações, grupo, nome/tag, andamento e notas vigentes.
Selecionar um critério limpa o filtro de grupo para não ocultar candidatos de
outras classificações. Os demais filtros são combinados. “Mostrar todos” limpa
a seleção. Nenhum filtro altera regras persistidas ou executa ação no jogo.

Ao expandir um jogador, ficam disponíveis os motivos, as fontes, as lacunas,
o histórico oficial, as doações, os eventos e as decisões/notas existentes.

## Decisões, notas e auditoria

Decisões: Manter, Conversar, Dispensar, Aguardar justificativa e Reabrir avaliação.
Manter/Conversar/Dispensar são revisados; Aguardar/Reabrir/sem decisão são pendentes.
O filtro “Aguardando justificativa” usa a decisão WAIT. Notas justificativas são
outro filtro: presença de nota não significa que a avaliação foi encerrada.

Motivo, autor autenticado, horário e evidências são preservados por clã, jogador
e semana. A versão esperada evita sobrescrita concorrente; `requestKey` assegura
idempotência. Os detalhes históricos agora também acompanham o membro no JSON
de evidência de novas decisões. Registros existentes permanecem intactos.
Falha ao carregar decisões bloqueia escrita e não inventa estado pendente.
Notas atuais são identificadas como contexto vigente, mesmo em semanas antigas.

- `GET /api/leadership/weekly-review?week=YYYY-MM-DD`: recorte e contexto histórico.
- `GET /api/leadership/weekly-review/decisions?week=YYYY-MM-DD`: versões decrescentes.
- `POST /api/leadership/weekly-review/decisions/{tag}`: decisão com ADMIN e CSRF.

A consulta da tela se renova a cada minuto. “Recarregar lista” relê o banco;
“Atualizar dados” coleta da API. Coletas após o recorte não retroagem ao domingo.
Os jobs de domingo tentam coletas a cada cinco minutos entre 18h e 21h55 e às
21h59. A aplicação precisa estar ligada; não existe recuperação fictícia de
capturas enquanto desligada.

## Validação

Testes do engine cobrem limites, ausência/zero, fontes antigas, dados inválidos,
fuso e virada semanal. Testes de participação cobrem reincidência, participante
ausente, ingresso, reingresso, corrida parcial, resets e cobertura de doações.
Integração com PostgreSQL cobre Coliseu, treino posterior, recorte, histórico
oficial conhecido na data, permissões, decisões, imutabilidade e concorrência.
Playwright cobre tabela, ordenação, filtros, detalhes, decisões, falhas,
privacidade e ausência de transbordamento no desktop e celular.

## Resultados abaixo da meta e elegibilidade — refinamento de 07/11/2026

O filtro “Duas guerras abaixo da meta” agora consulta as pontuações oficiais das
duas últimas guerras encerradas disponíveis, sem excluir resultados conhecidos
por vínculo não comprovado. Ausência não é zero. Essa seleção não significa
reincidência com obrigação integral confirmada.

“Duas encerradas · vínculo elegível” conserva o critério conservador do backend.
Os grupos prioritária/acompanhar e as decisões manuais não foram recalculados.
A tabela mostra também a penúltima encerrada quando usa esses filtros, com datas
e indicação de vínculo, mantendo a corrida atual identificada como parcial.

“Encerrada + atual abaixo · prévia” compara a última publicação da mesma semana
(no fuso do relatório) com a captura atual, somente na fase LIVE e com fontes
frescas. O valor atual pode aumentar; isso não representa duas guerras encerradas.
Nenhuma seleção executa expulsões ou altera decisões registradas.

Na conferência somente leitura do elenco local, cinco membros tinham dois
resultados oficiais abaixo de 1.600; apenas dois atendiam ao indicador de vínculo
do backend. A diferença era ocultada pelo antigo filtro e pela apresentação de
colunas de períodos diferentes. Os testes agora usam duas guerras reais nas
fixtures em vez de apenas um booleano de reincidência.
