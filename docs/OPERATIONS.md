# Fase 6 — operação local e preparação para publicação

Implementados: login, permissões, CSRF, limite de tentativas, monitoramento de
coletas, health endpoint, backups diários e teste de restauração em banco
descartável. O sistema continua em http://localhost:5173. Backend: porta local
configurada em `BACKEND_PORT` (neste ambiente, 8081).

## Acesso

A conta de liderança é configurada com `AUTH_ADMIN_USERNAME` e
`AUTH_ADMIN_PASSWORD` no arquivo local `.env`. A senha deve ter de 16 a 72
caracteres (até 72 bytes em UTF-8). Não existe senha padrão ou cadastro público.
O placeholder `replace-...` do exemplo é rejeitado na inicialização.
A configuração inicial deste ambiente gerou uma senha aleatória no `.env`,
com permissão de leitura restrita ao proprietário. Para acessá-la, abra esse
arquivo local; não o publique nem o adicione ao Git.

Uma conta opcional de consulta usa `AUTH_VIEWER_USERNAME` e
`AUTH_VIEWER_PASSWORD`. As credenciais precisam ser distintas das da liderança.
Depois de mudar credenciais, execute `docker compose up -d backend`.

| Ação | Liderança | Consulta |
| --- | --- | --- |
| Consultar analytics, histórico, títulos, operações e regras | Sim | Sim |
| Coleta manual | Sim | Não |
| Alterar regras | Sim | Não |
| Registrar avaliações | Sim | Não |
| Formalizar títulos locais | Sim | Não |

As permissões são verificadas no backend; esconder ou desabilitar controles na
interface é apenas apresentação. Cargos e participação na Supercell não são
alterados pelo sistema.

Login e logout usam sessão com cookie HttpOnly e SameSite Strict. As alterações,
incluindo login e logout, exigem CSRF. O login troca o identificador da sessão.
O limite é de dez falhas por endereço visto pela aplicação em cinco minutos;
há resposta 429 com Retry-After. Atrás do Nginx, o endereço observado pode ser
compartilhado pelos usuários. As sessões e o contador de tentativas são locais a
uma instância e se perdem ao reiniciar o backend. O vencimento por inatividade
é de trinta minutos. Para múltiplas instâncias, será necessário armazenamento
compartilhado de sessões e limites.

## Monitoramento

A página **Operações** mostra, por fonte:

- última tentativa e último sucesso;
- idade dos dados e intervalo configurado;
- alerta quando faltam dados ou passaram dois intervalos;
- falhas nas últimas 24 horas;
- histórico paginado de execuções.

O estado RIVER pode ficar desatualizado fora dos dias de guerra porque a coleta
tem cadência condicional. Aviso não significa falha de jogador.

`GET /actuator/health` é público e retorna somente estado, sem detalhes internos.
Os demais endpoints do Actuator não são expostos. A API e a documentação Swagger
exigem login; Swagger é restrito à liderança. No host:

```bash
curl --fail http://localhost:8081/actuator/health
docker compose ps
docker compose logs --tail=100 backend
docker compose logs --tail=50 backup
```

O Nginx entrega a SPA e o proxy da API, com política de conteúdo, proteção contra
embutir a página em frames e tipos de conteúdo explícitos. O token Supercell
continua apenas no backend.

## Backup e restauração

O serviço `backup` cria um arquivo PostgreSQL custom diariamente, mantém
quatorze dias por padrão e gera SHA-256. `BACKUP_UID` e `BACKUP_GID` definem o proprietário dos arquivos; neste ambiente correspondem ao usuário local. Os arquivos ficam em `backups/`,
ignorados pelo Git, com acesso restrito. Configuração:
`BACKUP_INTERVAL_SECONDS` e `BACKUP_RETENTION_DAYS`.

Para backup manual:

```bash
scripts/backup.sh
```

Para testar um arquivo existente:

```bash
scripts/restore-check.sh backups/arquivo.dump
```

O teste cria um PostgreSQL descartável sem portas nem acesso de rede, verifica
o checksum e restaura o dump. Mostra contagens de tabelas e remove o container
ao terminar. Nunca limpa ou substitui o banco local.

Para recuperação real, mantenha os serviços de aplicação parados, restaure o
dump em **um banco novo**, valide as contagens e migrações, e só então aponte a
aplicação para esse banco. Não use o script de verificação para substituir o
volume de produção. Credenciais do ambiente e token Supercell não fazem parte
do dump e precisam de armazenamento separado.

Uma cópia no mesmo host não protege contra perda do host. Antes da publicação,
escolha armazenamento externo para os backups, retenção e acesso. O serviço
local não envia arquivos a terceiros automaticamente.

## Validação real

`scripts/validate-live.py` autentica usando o `.env`, verifica médias, ranking,
exclusividade de títulos, tratamento da disponibilidade individual de decks e
limitações das doações. Compara fame/decks de guerras coincidentes com a API
oficial e escreve somente contagens/resultados em
[LIVE-VALIDATION.json](LIVE-VALIDATION.json), sem credenciais.

A execução desta fase comparou dez guerras e 654 participantes com os campos
oficiais, sem diferenças de pontuação ou decks. Os seis grupos de verificações
internas passaram para 128 jogadores históricos. O retrato de membros atuais
coincidiu com 42 membros na API. Estes são resultados daquela captura, não
garantias permanentes.

Doações continuam limites inferiores. Detectar resets futuros e evolução entre
semanas exige acumular novas capturas; os cenários são cobertos pelos testes,
mas não se deve anunciar um período ainda não observado como validado.

## Publicação pendente

A hospedagem e o domínio ainda precisam ser escolhidos. O que já está preparado:

1. Compose com reinício dos serviços e portas vinculadas ao loopback.
2. Exemplo de reverse proxy HTTPS em [deploy/Caddyfile](../deploy/Caddyfile).
3. Credenciais externas ao código, health endpoint, backup e restauração.
4. Testes de segurança e navegação desktop/celular.

Antes de publicar, defina um host com Docker e IP de saída compatível com a
chave Supercell, configure DNS e HTTPS e coloque `SESSION_COOKIE_SECURE=true`.
Se usar o Caddyfile, execute Caddy no mesmo host, configure `FROTA_DOMAIN` e
mantenha o proxy para a porta local correta. Não exponha as portas do banco
ou do backend. Preserve a origem única frontend/API; os cookies são por sessão
e não são tokens em localStorage.

A publicação, escolha de provedor, DNS e cópia externa de backups não foram
executadas. A configuração atual é para operação local de uma instância.

## Testes

```bash
mvn -f backend/pom.xml verify -Pintegration
npm run build --prefix frontend
npm run test --prefix frontend
npm run test:e2e --prefix frontend
```

Os testes do backend usam PostgreSQL descartável e os de navegador usam fixtures
para cenários reproduzíveis. A validação ao vivo é complementar e de leitura.
