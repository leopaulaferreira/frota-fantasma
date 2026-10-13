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
