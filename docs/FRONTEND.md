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

A rota `/anciao`, disponível na navegação principal, reúne os membros atuais de
cargo `member`. Recomendações usa a mesma lista e Premiações oferece acesso a
ela. A visão geral mostra atalhos para desenvolvimento, acompanhamento e
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
