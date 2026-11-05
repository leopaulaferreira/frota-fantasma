# Fase 5 — central de comando

Frontend React + TypeScript + Vite, React Router e TanStack Query em `frontend/`.
Consome somente `/api` do backend. Não contém token da Supercell nem dados de
demonstração no aplicativo; fixtures existem exclusivamente em `frontend/e2e/`.

## Executar

Com o backend na porta 8080:

```bash
npm ci --prefix frontend
npm run dev --prefix frontend
```

Abra http://127.0.0.1:5173. Para outro backend, configure `API_PROXY_TARGET` no
processo do Vite (por exemplo, `http://127.0.0.1:8081`); não use variáveis VITE
para segredos. Node 24 é a versão usada no build e no Dockerfile.

Para executar os três serviços com as variáveis locais já configuradas:

```bash
docker compose up --build -d
```

O serviço frontend usa Nginx, proxy para `backend:8080`, fallback de rotas da SPA
e porta `127.0.0.1:${FRONTEND_PORT:-5173}`. A publicação externa continua pendente. O login e as permissões estão descritos em [OPERATIONS.md](OPERATIONS.md).

## Consultas e investigação

- **Visão geral:** indicadores do clã, motivos de observação, destaques provisórios,
  evolução semanal e acesso a todos os indicadores disponíveis.
- **Tripulação:** busca por nome/tag, filtros por status/cargo, ex-membros e
  ordenação por médias, score, doações, atividade, troféus, decks e pontos.
- **Perfil:** todas as métricas, contexto, candidatura, componentes do Frota Score,
  histórico integral de guerras disponível no relatório, pontos/decks/ranking,
  evolução visual, doações por período com resets, premiações formalizadas,
  avaliações preservadas, entradas/saídas/cargos e snapshots do membro.
- **Guerras:** arquivo paginado, navegação entre guerras, corrida atual da última
  captura, ranking completo e campos originais de cada participante. Empates usam
  posições 1, 1, 3; pontuação desconhecida não recebe posição.
- **Comparação:** até quatro membros nas mesmas guerras, incluindo histórico e
  ex-membros. Nenhum registro ausente é convertido em zero.
- **Recomendações:** motivos de observação/revisão e candidatura; registro explícito
  de avaliações; consulta de evidências e regras congeladas.
- **Premiações:** projeções separadas dos títulos formalizados, histórico paginado
  geral e por jogador. A formalização ocorre apenas na tela da avaliação revisada,
  com validação de semana encerrada e exclusividade feita pelo backend.
- **Regras:** critérios e pesos editáveis, motivo obrigatório, validação de soma
  dos pesos e controle de concorrência por `expectedVersion`; versões anteriores
  permanecem consultáveis.

As consultas paginadas oferecem navegação até o fim do histórico, sem limitar a
investigação aos primeiros vinte registros. Guerras e períodos de doação do
relatório analítico são exibidos integralmente. A linha do tempo combina guerras
e doações e apresenta, em seções próprias, avaliações e eventos de associação.

Cada indicador abre uma janela de explicação, sem expandir cards ou linhas da tabela. Ela permite consultar origem, qualidade, período,
quantidade de guerras e motivo/fórmula descrita pelo backend. Os códigos originais e registros utilizados permanecem acessíveis na seção de dados técnicos.
Período incompleto, amostra insuficiente e indisponibilidade permanecem visíveis.
Valores observados com amostra insuficiente são exibidos com a limitação, sem
substituí-los por resultados fictícios.

A meta padrão de 16 decks não é a disponibilidade individual. Doações calculadas
por snapshots são limites inferiores; resets não são reduções reais. A atividade
oficial é distinta da observação local e os dias offline são os calculados pelo
backend na captura. Classificação é apoio à decisão, sem promoção ou expulsão.

`Atualizar dados` solicita coleta explícita e apresenta o resultado individual de
cada fonte; HTTP 200 com uma fonte FAILED não é anunciado como sucesso completo.

## Complementos do backend

- `WarStats.rank`: DERIVED a partir de `fame/contribution` de todos os participantes
  registrados da guerra, incluindo ex-membros. Utiliza os campos oficiais já
  validados em [API-VALIDATION.json](API-VALIDATION.json). Não consulta ou inventa
  um novo campo de ranking individual da Supercell.
- `GET /api/recommendations/awards?page=0&size=20`
- `GET /api/recommendations/players/{tag}/awards?page=0&size=20`

As duas consultas de premiações retornam `items,page,size,totalElements`.
Cada item contém semana, avaliação original, data de formalização, título,
jogador e evidências. São registros locais imutáveis, não prêmios da API oficial.

## Assets

Oito PNGs são servidos localmente a partir de RoyaleAPI/cr-api-assets. Origem,
commit e condições estão em
[NOTICE.md](../frontend/public/assets/royale/NOTICE.md), com aviso visível no rodapé.
São ilustrações decorativas; não identificam oficialmente o clã.
O tema usa Fantasma Real e Pescador no banner, Rei Esqueleto nas guerras,
personagens nas premiações, Arena Real na navegação e emblemas nos indicadores.
Não representam cartas utilizadas, avatar de membro ou arena atual. A identidade
visual continua visível durante carregamento e falhas da API.

Screenshots de revisão com **dados ilustrativos dos testes**, não dados reais:
[desktop](screenshots/frontend-desktop.png) e
[celular](screenshots/frontend-mobile.png).

## Verificação

```bash
npm run build --prefix frontend
npm run test --prefix frontend
npm run test:e2e --prefix frontend
mvn -f backend/pom.xml verify -Pintegration
```

Playwright usa Chrome local em `/usr/bin/google-chrome`; configure `CHROME_PATH`
quando necessário. Os testes de navegador interceptam a API com fixtures de teste,
exercitam desktop e celular e geram screenshots em `frontend/test-results/`.
Não provam conectividade ao vivo com a Supercell. Os testes de integração do
backend validam os contratos internos com PostgreSQL descartável.

O frontend pode executar sem histórico: apresenta estados vazios/insuficientes.
Para investigação real, a coleta do backend precisa ter acumulado observações.

### Área de candidatos a Ancião

Recomendações (`/recomendacoes`) centraliza o seletor **Tipo de análise**:
**Candidatos a Ancião** (padrão), **Membros para acompanhar** e
**Avaliações registradas**. A seleção fica na URL (`tipo=candidatos`,
`tipo=acompanhamento` ou `tipo=avaliacoes`) e persiste ao recarregar.
Candidatos reúne os membros atuais de cargo `member`, mantendo ranking e
dois níveis de detalhes. A antiga rota `/anciao` redireciona para esse filtro;
não existe mais um item separado no menu. Premiações oferece acesso direto. A visão geral mostra atalhos para desenvolvimento, acompanhamento e
confiabilidade das coletas. Comparar membros também apresenta prontidão e
próximo passo antes dos indicadores e do histórico de guerras.

A regra de triagem fica em `frontend/src/lib/prospects.ts`, compartilhada entre
lista, resumo e comparação. Componentes de apresentação não implementam
critérios diferentes. Estilos de gestão ficam em `leadership.css`.

### Prioridades e notas da liderança

A visão geral do perfil ADMIN inclui uma lista filtrável de prioridades, sem
limite silencioso de cinco membros, com navegação em páginas de dez. Ordem: retomadas com data atingida, revisão,
atenção/queda, candidaturas, evolução, proximidade do objetivo e amostra pequena.
As categorias podem coexistir para um membro e sempre expõem os motivos e as
métricas originais. Evolução/queda usam a diferença entre duas guerras recentes
e duas anteriores, exigindo amostra e período suficientes. Proximidade usa até
5% abaixo da referência ajustável (inicialmente a meta das regras salvas), com quatro pontuações
conhecidas. Essas condições não alteram elegibilidade nem geram punições.

No perfil, a aba **Notas da liderança** registra observações, justificativas e
combinados. São dados manuais, separados dos dados da API. Cada nota tem autor
obtido da sessão, data de criação, validade inclusiva opcional e data opcional
para retomar o acompanhamento, no fuso America/Sao_Paulo. Encerrar exige um
resultado, preservando o texto original. Correções são feitas encerrando a nota
e registrando outra. O histórico é paginado e inclui notas expiradas/encerradas;
o resumo usa apenas notas vigentes. Notas não mudam status ou alertas analíticos.

Rotas `/api/leadership/**` são exclusivas de ADMIN, inclusive leitura; VIEWER não
recebe o conteúdo. Gravações usam CSRF, validação, chave de idempotência e trava
por clã. A migração V5 adiciona registros imutáveis de notas e encerramentos,
incluídos nos backups do PostgreSQL. Dados ficam restritos ao clã configurado.


### Sinais e filtros de candidatura

A lista explica oito critérios: amostra, média, participação positiva em toda a
janela, uso do padrão de decks, tempo observado, doações semanais, alertas
persistidos e situação operacional atual. Cada critério distingue atendido,
pendente, sem evidência e revisão necessária. A avaliação oficial do backend
continua obrigatória para sinalizar prontidão; esta triagem não altera cargos,
regras ou `elderCandidate`.

- **Pronto para avaliação:** todos os critérios atendidos e aprovação oficial.
- **Poucas pendências:** até dois critérios pendentes, média dentro da tolerância
  (5% inicialmente), sem alerta e sem dado ausente.
- **Em desenvolvimento:** dados disponíveis, mas mais pendências ou média fora
  da faixa próxima.
- **Completar evidências:** referências, atividade, coleta atualizada ou amostra
  insuficientes. O mínimo de guerras respeita as regras (2 a 4), e toda guerra
  da janela disponível precisa ter pontos e decks conhecidos.
- **Revisar alertas:** alerta persistido, situação operacional com ressalva ou
  veto oficial não explicado pelos demais critérios. Um alerta conhecido tem
  prioridade mesmo quando outros dados estão ausentes.

A ordenação inicial prioriza prontos, poucas pendências, desenvolvimento e
revisão de alertas, deixando evidências insuficientes por último. Dentro de
cada grupo, prioriza o número de critérios atendidos,
com desempate por média, uso de decks e tag. A ordenação por métrica escolhida
pelo líder vale para toda a seleção, sem agrupamentos ocultos. Filtros por
situação, pendência específica, nome/tag, amostra e acompanhamento manual têm
lista única de todos os membros e botão de limpeza. Nenhum membro abaixo da meta é
excluído automaticamente.

A referência inicial usa `pointsTarget` das regras salvas. Simulações locais
podem aumentar a exigência, mas nunca reduzem a meta oficial. Limpar o campo
retoma a referência salva. Tolerância aceita 0% a 20%. A triagem não cria Score
próprio nem soma pontos para compensar critérios não atendidos.

Cada candidato aparece inicialmente em duas linhas, com posição numerada, nome,
situação e resumo de critérios, média, decks e doações. A numeração segue a
ordenação selecionada antes dos filtros, preservando a posição ao buscar um membro.
A ordenação padrão prioriza prontidão; todos os resultados ficam na mesma lista,
sem paginação. O primeiro clique abre o próximo passo, o checklist e as ações de
acompanhamento. Um segundo expansor abre guerras, evolução e métricas completas. Doações observadas continuam sendo limite
inferior: estar abaixo da meta não comprova falta de contribuição. Tempo
observado desde JOIN/BASELINE não comprova ingresso real. Zero alertas não
comprova monitoramento contínuo.

ADMIN pode registrar acompanhamento de candidatura (tipo `CANDIDACY`, migração
V6), com motivo e data obrigatória para reavaliar. O registro usa as mesmas
proteções e histórico das notas da liderança; pode ser encerrado no perfil.
VIEWER não consulta os acompanhamentos privados. Nenhum cargo é alterado.

### Expulsões semanais

A rota `/expulsoes` aparece abaixo de Premiações para ADMIN. Lista compacta,
numerada e expansível com revisão por pontuação da semana e inatividade,
consulta a recortes passados, filtros e registro de justificativas. A prévia
acompanha as capturas até domingo às 22h de São Paulo. Os motivos e horários de
coleta ficam explícitos, com grupos separados para dados insuficientes e
membros sem sinal. Consulte [WEEKLY-REVIEW.md](WEEKLY-REVIEW.md).

### Identidade visual da Frota

A camada visual `src/fleet.css` reúne a identidade espectral (azul profundo,
verde e latão), as superfícies compactas e a adaptação para celular. Reutiliza
os assets locais com créditos em `public/assets/royale/NOTICE.md`, sem CDN.
O menu se organiza em A bordo, Decisões do líder e Administração; Expulsões
semanais permanece imediatamente após Premiações e restrita à liderança.

A visão geral traz um atalho privado para a revisão semanal, com contagem dos
sinais confirmados e indicação de fontes pendentes/erro. As posições dos
candidatos preservam a ordenação e os dois níveis de expansão; as três primeiras
recebem molduras distintas, sem representar aprovação ou cargo conquistado.
Na revisão semanal, as instruções de funcionamento ficam em uma expansão,
mas prazo, horários das fontes e alertas de qualidade permanecem visíveis.

### Consultar e comparar doações anteriores

Em **Tripulação**, use **Semana passada** ou **Consultar semana de**. A data
escolhida é normalizada para a segunda-feira e fica na URL (`?week=YYYY-MM-DD`).
O relatório usa o calendário configurado no backend e as coletas persistidas até
o recorte da semana. O elenco/cargo/status também correspondem a esse recorte;
“Incluir membros que saíram até o recorte” permite consultar outros registros.
A média de guerras continua usando até quatro resultados publicados até o recorte,
não uma soma de pontos da guerra daquela semana.

**Comparar doações entre semanas** apresenta, por jogador da seleção, as doações
observadas em duas semanas e a diferença. Valores ausentes não viram zero;
as diferenças não comprovam evolução real se a cobertura das coletas divergir.
O perfil aberto nessa lista mantém a semana consultada e oferece retorno ao
perfil atual. Notas privadas continuam sendo notas atuais.

Os snapshots já eram persistidos: o reset do contador no jogo não remove o
histórico local. O filtro apenas expõe a consulta histórica existente. Coletas
anteriores à implantação, intervalos sem captura e resets não observados não
são reconstruídos; os totais de doações permanecem limites inferiores.

### Histórico móvel de seis semanas de doações

Em Tripulação, **Doações** no seletor de indicadores abre `/doacoes`. O filtro
**Semana** abre por padrão a semana atual e permite escolher qualquer uma das
últimas seis ou mostrar todas juntas. A seleção permanece na URL e ao recarregar.
O perfil
individual também oferece esse atalho, preenchendo a busca com a tag.
A janela inclui a semana atual e as cinco anteriores, no calendário configurado
nas regras. Ao virar a semana, a coluna mais antiga sai e o acumulado é recalculado.
Essa renovação é da visualização; o histórico persistido e as evidências de
avaliações continuam preservados nas consultas anteriores por data.

Por padrão, cada célula mostra o **último contador salvo** naquela semana e o
horário da captura. Esse valor é o contador informado pelo jogo na coleta, não
uma garantia do total final da semana. Mesmo uma única captura pode informar o
contador. Um zero explícito é exibido como zero; ausência de coleta é desconhecida.
O seletor permite consultar as **doações observadas no período**, calculadas por
diferenças entre coletas. O acumulado usa apenas essas diferenças nas seis semanas,
com cobertura e limitação visíveis; nunca soma contadores como se fossem doações
adicionais. Filtros por membro e inclusão de ex-membros ficam disponíveis.

Os contadores já são gravados em `clan_membership_history` pelas coletas regulares.
A resposta de cada período passa a incluir `lastCounter` e `counterCapturedAt`,
calculados dessas capturas, sem alterar a fórmula usada nas avaliações ou prêmios.
O painel atualiza a consulta a cada minuto. A coleta depende do sistema ligado;
o período anterior à primeira coleta não pode ser reconstruído.

### Cabeçalhos consistentes

O componente `Heading` aplica o mesmo cabeçalho ilustrado a todas as páginas,
incluindo Recomendações, Comparar, Doações, Expulsões, Regras e Operações.
Cada seção reutiliza uma arte local do Clash. Título e imagem ocupam colunas
próprias; ações do cabeçalho ficam em uma faixa inferior integrada ao painel,
sem sobrepor a arte. O layout e sua adaptação para celular estão centralizados
em `fleet.css`; as regras antigas de posicionamento absoluto foram removidas.

### Dados externos em Operações

Sem novo item na sidebar: ADMIN encontra **Dados externos → Importar CSV** na página
Operações. O modal permite escolher/arrastar arquivo, analisar, consultar contagens
e motivos por registro, cancelar e confirmar somente após prévia. O transporte usa
`FormData` no cliente `api`, preservando CSRF e tratamento de sessão/erros; o browser
define o boundary multipart.

Os formatos verificados têm identidade temporal incompleta. O botão deixa explícito
**Registrar pendências (sem alterar métricas)**, e o resultado informa zero registros
incorporados. Arquivos inválidos não podem ser confirmados. Trocar arquivo invalida
prévia; loading bloqueia confirmação concorrente; erros permanecem visíveis.

O histórico paginado mostra data, arquivo, origem, novos, ignorados, conflitos,
inválidos, pendentes e status, com detalhes da análise. VIEWER não carrega a auditoria.
Formatos e limitação do backfill: [ROYALEAPI-IMPORT.md](ROYALEAPI-IMPORT.md).


### Identificar dados externos

Cada lote no histórico de Operações oferece **Identificar dados** ou **Revisar
identificação**. River Race apresenta correspondência exata/possíveis candidatos,
contagens por campo, tags coincidentes, diferenças e contexto oficial. O ADMIN deve
selecionar explicitamente um alvo; valores incompatíveis indicam que a confirmação
registrará conflito. Sem correspondência, só é possível rejeitar ou manter pendente.

Para membros, o modal apresenta clã, sugestão de data até o minuto e fuso editável.
**Alterar** registra a data como informada pelo usuário. Motivo e checkbox explícito
são exigidos antes de confirmar. Nenhuma sugestão é salva automaticamente. O histórico
preserva resoluções/correções anteriores e evidências. Conflitos de concorrência ou
evidência desatualizada exigem atualização. O aviso de que identificação não sobrescreve
Supercell nem incorpora CSV por si só permanece visível.

Listas extensas de correspondências mostram inicialmente 20 candidatos ordenados.
**Mostrar mais correspondências** revela mais 20 na mesma tela, até disponibilizar
todos; a contagem total permanece visível e nenhum alvo é pré-selecionado.


### Incorporar dados identificados

Lotes IDENTIFIED oferecem **Incorporar ao histórico**: modal com analisados, novos,
duplicados, conflitos, ignorados, jogadores afetados, período e motivos por linha.
Cancelar não grava; confirmação exige checkbox e prévia válida. Resultado parcial
mostra pendências explicitamente. Reabrir uma execução concluída consulta o resultado
preservado, sem executar novamente. Erros e conflitos de prévia permanecem visíveis.

O histórico diferencia identificação atual e incorporação, com contagens da execução
e **Analytics: Sim/Não**, calculado pelo backend para o estado atual. Uma chegada
oficial posterior pode tornar importados inativos sem apagar a auditoria. Métricas
mostram fontes combinadas; a página Guerras identifica backfill no arquivo, ranking
e evidências, sem rotular valores importados como exclusivamente oficiais. Perfil e
Comparar recebem o mesmo histórico do analytics, sem novos cálculos no frontend.

A central de guerras oferece filtros compartilháveis na URL, comparação lado a
lado e evolução individual. Contratos, critérios e limites em [WARS.md](WARS.md).

## Comparativo de guerras

O menu **Comparativo** abre `/comparativo`, com matriz das quatro últimas guerras oficiais encerradas, filtros combináveis na URL e diálogo individual com gráfico, doações e evidências. A rota `/comparar` permanece disponível para comparação de perfis. Consulte [critérios, utilização e validação](COMPARATIVO.md).
