# Frota Fantasma Analytics

Base do backend para consultar o clã **#RQJGC9GV** pela [API oficial do Clash Royale](https://developer.clashroyale.com/#/documentation). O projeto usa Java 21, Spring Boot, PostgreSQL e Docker Compose. A interface React/TypeScript fica para uma fase futura.

## Executar com Docker Compose

1. Copie o arquivo de exemplo: `cp .env.example .env`.
2. Em `.env`, substitua `CLASH_ROYALE_API_TOKEN` por uma chave criada no [portal de desenvolvedores do Clash Royale](https://developer.clashroyale.com/) e defina uma senha em `POSTGRES_PASSWORD`. Configure no portal o IP público que fará as chamadas à API.
3. Inicie os serviços: `docker compose up --build -d`.
4. Consulte `http://localhost:8080/api/clan` e `http://localhost:8080/api/clan/members`.

Se alguma porta estiver ocupada, ajuste `BACKEND_PORT` ou `POSTGRES_PORT` em `.env` e use a porta escolhida nas URLs.

Use `docker compose logs -f backend` para acompanhar a inicialização e `docker compose down` para parar os serviços. O volume PostgreSQL persiste entre execuções.

## Executar o backend com Maven

Instale Java 21 e Maven. Inicie apenas o banco com `docker compose up -d db` após criar `.env`. Depois exporte `CLASH_ROYALE_API_TOKEN`, `CLAN_TAG` (padrão `#RQJGC9GV`) e `DB_PASSWORD` no seu terminal e execute:

```bash
cd backend
mvn spring-boot:run
```

`DB_URL` e `DB_USERNAME` podem ser definidos se os valores padrão de `application.yml` não servirem. Para rodar os testes: `cd backend && mvn test`.

## API interna

| Rota | Resposta |
| --- | --- |
| `GET /api/clan` | Dados atuais do clã, incluindo nome, pontuação e quantidade de membros |
| `GET /api/clan/members` | Lista atual de membros com tag, função, troféus e doações |

Cada consulta busca dados atuais na API oficial e atualiza o último retrato no PostgreSQL. As tabelas são criadas pelo Flyway. Falhas externas retornam um corpo `application/problem+json` com `code` e `detail`: clã ausente (`404`), limite de chamadas ou manutenção (`503`), tempo esgotado (`504`) e demais falhas de integração (`502`). O corpo e o token da resposta externa não são expostos.

As configurações ficam em `backend/src/main/resources/application.yml`. `CLAN_TAG` e `CLASH_ROYALE_API_TOKEN` são variáveis de ambiente. O arquivo `.env` e outros arquivos `.env.*`, exceto `.env.example`, são ignorados pelo Git; nunca grave tokens reais no repositório.
