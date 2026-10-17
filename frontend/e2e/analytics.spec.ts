import { test, expect } from "@playwright/test";
import { report, players, rules, evaluation, award } from "./fixtures";
test.beforeEach(async ({ page }) => {
  await page.route("**/api/**", async (route) => {
    const url = new URL(route.request().url());
    const path = decodeURIComponent(url.pathname);
    let result: unknown;
    const paged = (items: unknown[], total = items.length) => ({
      items,
      page: Number(url.searchParams.get("page") || 0),
      size: 20,
      totalElements: total,
    });
    if (path === "/api/auth/me")
      result = { username: "lider", roles: ["ADMIN"] };
    else if (path === "/api/auth/csrf")
      result = { headerName: "X-CSRF-TOKEN", token: "test-csrf" };
    else if (path === "/api/auth/login" || path === "/api/auth/logout")
      result = { authenticated: true };
    else if (path === "/api/operations")
      result = {
        generatedAt: report.generatedAt,
        collectionEnabled: true,
        sources: [
          {
            name: "MEMBERS",
            lastSuccess: report.generatedAt,
            ageSeconds: 60,
            expectedIntervalSeconds: 1800,
            stale: false,
            failuresLast24Hours: 0,
            latest: { status: "SUCCESS" },
          },
          {
            name: "RIVER",
            lastSuccess: null,
            ageSeconds: null,
            expectedIntervalSeconds: 900,
            stale: true,
            failuresLast24Hours: 2,
            latest: { status: "FAILED", errorCode: "UPSTREAM_UNAVAILABLE" },
          },
        ],
      };
    else if (path === "/api/collections")
      result = paged([
        {
          id: "run-1",
          kind: "MEMBERS",
          status: "SUCCESS",
          startedAt: report.generatedAt,
          completedAt: report.generatedAt,
          recordCount: 42,
          errorCode: null,
        },
      ]);
    else if (path === "/api/leadership/notes/active") result = { generatedAt: report.generatedAt, timezone: "America/Sao_Paulo", notes: [] };
    else if (path.startsWith("/api/leadership/players/") && path.endsWith("/notes")) result = paged([]);
    else if (path === "/api/analytics") result = report;
    else if (path.startsWith("/api/analytics/players/"))
      result = players.find((p) => path.endsWith(p.tag));
    else if (path === "/api/recommendations/automation") result = { enabled: true, schedule: "Segunda-feira às 06h", timezone: "America/Sao_Paulo", closingWeek: report.week, pending: false };
    else if (path === "/api/recommendations/rules") result = rules;
    else if (path === "/api/recommendations/rules/history") result = paged([]);
    else if (path === "/api/recommendations/evaluations")
      result =
        route.request().method() === "POST" ? evaluation : paged([evaluation]);
    else if (path === "/api/recommendations/evaluations/eval-1")
      result = evaluation;
    else if (path.endsWith("/awards")) result = paged([award]);
    else if (
      path.startsWith("/api/recommendations/players/") &&
      path.endsWith("/history")
    )
      result = paged([
        {
          evaluationId: "eval-1",
          createdAt: report.generatedAt,
          week: report.week,
          status: "MANTER",
          evidence: players[0],
        },
      ]);
    else if (path.endsWith("/events"))
      result = paged([
        {
          id: 1,
          type: "JOIN",
          observedAt: report.generatedAt,
          previousRole: null,
          currentRole: "member",
        },
      ]);
    else if (path.endsWith("/history"))
      result = paged([
        {
          capturedAt: report.generatedAt,
          trophies: 10000,
          donations: 240,
          apiLastSeen: report.generatedAt,
          role: "member",
        },
      ]);
    else if (path === "/api/wars")
      result = paged(
        (url.searchParams.get("page") === "1"
          ? [players[0].wars[5]]
          : players[0].wars.slice(0, 5)
        ).map((w) => ({ ...w, id: w.warId })),
        21,
      );
    else if (/^\/api\/wars\/\d+$/.test(path)) {
      const w = players[0].wars.find((w) => path.endsWith("/" + w.warId))!;
      result = {
        war: { ...w, id: w.warId, capturedAt: report.generatedAt },
        participants: players.map((p, i) => ({
          playerTag: p.tag,
          name: p.name,
          contribution: 3200 - i * 200,
          decksUsed: 16 - i,
        })),
      };
    } else if (path === "/api/wars/current")
      result = {
        snapshot: {
          capturedAt: report.generatedAt,
          state: "warDay",
          periodType: "warDay",
        },
        race: {
          clan: {
            name: "Frota Fantasma",
            participants: players.map((p) => ({
              tag: p.tag,
              name: p.name,
              fame: 1600,
              decksUsed: 8,
              decksUsedToday: 4,
            })),
          },
        },
      };
    else if (path === "/api/collections/sync")
      result = [
        { kind: "MEMBERS", status: "SUCCESS", errorCode: null },
        {
          kind: "WAR_LOG",
          status: "FAILED",
          errorCode: "UPSTREAM_UNAVAILABLE",
        },
      ];
    else
      return route.fulfill({
        status: 404,
        json: { detail: "Ainda não há dados para esta consulta." },
      });
    await route.fulfill({ json: result });
  });
});
test("dashboard e investigação completa de membro", async ({ page }, info) => {
  const errors: string[] = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "O rumo da Frota" }),
  ).toBeVisible();
  await expect
    .poll(() =>
      page
        .locator(".banner-ghost, .award-character")
        .evaluateAll(
          (images) =>
            images.length === 4 &&
            images.every(
              (image) =>
                (image as HTMLImageElement).complete &&
                (image as HTMLImageElement).naturalWidth > 0,
            ),
        ),
    )
    .toBe(true);
  await page.screenshot({
    path: "test-results/dashboard-" + info.project.name + ".png",
    fullPage: true,
  });
  await page.getByRole("link", { name: "Tripulação", exact: true }).click();
  await page.getByLabel("Buscar membro").fill("Maré");
  await page.getByRole("link", { name: /Maré Negra/ }).click();
  await expect(
    page.getByRole("heading", { name: "Maré Negra", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Guerras", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: /Histórico completo.*6/ }),
  ).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Temporada 137 · seção -2" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Todas as métricas" }).click();
  const missing = page
    .locator(".metric")
    .filter({ hasText: "Utilização real de decks (%)" });
  await missing.getByRole("button").click();
  await expect(page.getByRole("dialog")).toContainText(
    "A API não fornece a capacidade individual",
  );
  await page.getByRole("button", { name: "Fechar explicação" }).click();
  await page.getByRole("button", { name: "Doações", exact: true }).click();
  await expect(
    page.getByText("Resets detectados", { exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Linha do tempo" }).click();
  await expect(
    page.getByRole("heading", { name: "Avaliações e alterações de status" }),
  ).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Ver evidências congeladas" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Premiações", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Capitão da Guerra" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Visão geral", exact: true }).click();
  await page.screenshot({
    path: "test-results/profile-" + info.project.name + ".png",
    fullPage: true,
  });
  expect(errors).toEqual([]);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
});
test("guerras anteriores, paginação e comparação alinhada", async ({
  page,
}) => {
  await page.goto("/guerras/1");
  await expect(
    page.getByRole("heading", { name: "Ranking da guerra" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Próxima página" }).click();
  await page.getByRole("link", { name: /Temporada 137 · seção -2/ }).click();
  await expect(page).toHaveURL(/guerras\/6$/);
  await page.goto("/comparar?tags=%23P01");
  await page.getByLabel("Adicionar membro").selectOption("#P03");
  await expect(
    page.getByRole("columnheader", { name: "Maré Negra" }).first(),
  ).toBeVisible();
  await expect(
    page.getByRole("columnheader", { name: "Brisa" }).first(),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Guerra a guerra" }),
  ).toBeVisible();
  await expect(page.getByText("Dados insuficientes").first()).toBeVisible();
});
test("avaliações explícitas, evidências congeladas e coleta parcial", async ({
  page,
}) => {
  await page.goto("/recomendacoes");
  await page.getByRole("button", { name: "Registrar avaliação" }).click();
  await page.getByRole("link", { name: "Consultar evidências →" }).click();
  await expect(
    page.getByRole("heading", { name: "Evidências preservadas" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Atualizar dados" }).click();
  await expect(page.getByRole("status")).toContainText("WAR_LOG: FAILED");
});
test("regras validam pesos e preservam expectedVersion", async ({ page }) => {
  let body: Record<string, unknown> | undefined;
  await page.route("**/api/recommendations/rules", async (route) => {
    if (route.request().method() === "POST") {
      body = route.request().postDataJSON();
      await route.fulfill({ json: rules });
    } else await route.fulfill({ json: rules });
  });
  await page.goto("/regras");
  await page.getByLabel("Peso • guerras (%)").fill("44");
  await page.getByLabel("Motivo da alteração").fill("Ajuste de teste");
  await expect(
    page.getByRole("button", { name: "Salvar nova versão" }),
  ).toBeDisabled();
  await page.getByLabel("Peso • guerras (%)").fill("45");
  await page.getByRole("button", { name: "Salvar nova versão" }).click();
  await expect.poll(() => body?.reason).toBe("Ajuste de teste");
  expect(body?.expectedVersion).toBeNull();
});
test("erro recuperável e estado vazio sem números fabricados", async ({
  page,
}) => {
  let fail = true;
  await page.route("**/api/analytics", (route) =>
    fail
      ? route.fulfill({
          status: 503,
          json: { detail: "Fonte temporariamente indisponível." },
        })
      : route.fulfill({ json: { ...report, players: [] } }),
  );
  await page.goto("/membros");
  await expect(page.getByRole("alert")).toContainText(
    "Fonte temporariamente indisponível",
  );
  fail = false;
  await page.getByRole("button", { name: "Tentar novamente" }).click();
  await expect(
    page.getByText("Nenhum membro corresponde aos filtros."),
  ).toBeVisible();
});

test("identidade visual permanece disponível quando a API falha", async ({
  page,
}) => {
  await page.route("**/api/analytics", (route) =>
    route.fulfill({ status: 503, json: { detail: "Fonte indisponível" } }),
  );
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "Uma tripulação. Um legado." }),
  ).toBeVisible();
  await expect
    .poll(() =>
      page
        .locator(".banner-ghost")
        .evaluate((image) => (image as HTMLImageElement).naturalWidth),
    )
    .toBeGreaterThan(0);
  await expect(page.getByRole("alert")).toContainText("Fonte indisponível");
});

test("login, logout e sessão expirada", async ({ page }) => {
  let authenticated = false;
  await page.route("**/api/auth/me", (route) =>
    authenticated
      ? route.fulfill({ json: { username: "lider", roles: ["ADMIN"] } })
      : route.fulfill({ status: 401, json: { detail: "Entre na sua conta" } }),
  );
  await page.route("**/api/auth/login", (route) => {
    authenticated = true;
    expect(route.request().headers()["x-csrf-token"]).toBe("test-csrf");
    return route.fulfill({ json: { authenticated: true } });
  });
  await page.route("**/api/auth/logout", (route) => {
    authenticated = false;
    return route.fulfill({ json: { loggedOut: true } });
  });
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "Bem-vindo a bordo." }),
  ).toBeVisible();
  await page.getByLabel("Usuário", { exact: true }).fill("lider");
  await page.getByLabel("Senha", { exact: true }).fill("senha-de-teste");
  await page.getByRole("button", { name: "Entrar na Frota" }).click();
  await expect(
    page.getByRole("heading", { name: "O rumo da Frota" }),
  ).toBeVisible();
  authenticated = false;
  await page.route("**/api/operations", (route) =>
    route.fulfill({ status: 401, json: { detail: "Sessão expirada" } }),
  );
  await page.getByRole("link", { name: "Operações", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Bem-vindo a bordo." }),
  ).toBeVisible();
  await page.unroute("**/api/operations");
  await page.getByLabel("Usuário", { exact: true }).fill("lider");
  await page.getByLabel("Senha", { exact: true }).fill("senha-de-teste");
  await page.getByRole("button", { name: "Entrar na Frota" }).click();
  await expect(
    page.getByRole("heading", { name: "Operações da Frota" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Sair", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Bem-vindo a bordo." }),
  ).toBeVisible();
});

test("conta de consulta acessa histórico e não altera regras", async ({
  page,
}) => {
  await page.route("**/api/auth/me", (route) =>
    route.fulfill({ json: { username: "consulta", roles: ["VIEWER"] } }),
  );
  await page.goto("/regras");
  await expect(
    page.getByRole("heading", { name: "Regras da Frota" }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Salvar nova versão" }),
  ).toBeDisabled();
  await expect(
    page.getByRole("button", { name: "Atualizar dados" }),
  ).toBeDisabled();
  await page.goto("/recomendacoes");
  await expect(
    page.getByRole("button", { name: "Registrar avaliação" }),
  ).toBeDisabled();
});

test("operações mostram falhas e falta de primeira captura sem falso sucesso", async ({
  page,
}) => {
  await page.goto("/operacoes");
  await expect(
    page.getByRole("heading", { name: "Aguardando primeira coleta" }),
  ).toBeVisible();
  await expect(page.getByText("2 falhas nas últimas 24 horas.")).toBeVisible();
  await expect(
    page.getByText("UPSTREAM_UNAVAILABLE", { exact: false }),
  ).toBeVisible();
  await expect(
    page.getByRole("columnheader", { name: "Conclusão" }),
  ).toBeVisible();
});

test("explicação abre em janela sem esticar a tabela e preserva os dados técnicos", async ({
  page,
}, info) => {
  await page.goto("/membros");
  const table = page.locator(".table-scroll");
  const trigger = page
    .locator(".metric")
    .filter({ hasText: "Média das últimas 4 guerras" })
    .first()
    .getByRole("button");
  await expect(trigger).toBeVisible();
  const before = await table.boundingBox();
  await trigger.click();
  const dialog = page.getByRole("dialog", {
    name: "Média das últimas 4 guerras",
    exact: true,
  });
  await expect(dialog).toBeVisible();
  await expect(
    dialog.getByText("Calculado com dados da API", { exact: true }),
  ).toBeVisible();
  await expect(dialog.getByText("DERIVED", { exact: true })).not.toBeVisible();
  const after = await table.boundingBox();
  expect(after?.width).toBeCloseTo(before!.width, 0);
  expect(after?.height).toBeCloseTo(before!.height, 0);
  const box = await dialog.boundingBox();
  const viewport = page.viewportSize()!;
  expect(box!.x).toBeGreaterThanOrEqual(0);
  expect(box!.y).toBeGreaterThanOrEqual(0);
  expect(box!.x + box!.width).toBeLessThanOrEqual(viewport.width);
  expect(box!.y + box!.height).toBeLessThanOrEqual(viewport.height);
  await page.screenshot({
    path: "test-results/evidence-" + info.project.name + ".png",
  });
  await dialog.locator("summary").click();
  await expect(dialog.getByText("DERIVED", { exact: true })).toBeVisible();
  await expect(
    dialog.getByText("war:1:fame=3200", { exact: true }),
  ).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(dialog).not.toBeVisible();
  await expect(trigger).toBeFocused();
});

test("evidências da guerra abrem sem esticar o ranking", async ({ page }) => {
  await page.goto("/guerras/1");
  const trigger = page.getByRole("button", { name: "Evidências", exact: true }).first();
  const row = page.locator("tbody tr").first();
  const before = await row.boundingBox();
  await trigger.click();
  const dialog = page.getByRole("dialog", { name: /Evidências de/ });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByText("Pontuação na guerra", { exact: true })).toBeVisible();
  await expect(dialog.getByText("Não informado neste registro").first()).toBeVisible();
  await expect(dialog.getByText("DIRECT_API", { exact: true })).not.toBeVisible();
  expect((await row.boundingBox())?.height).toBe(before?.height);
  const bounds = await dialog.boundingBox();
  expect(bounds!.x).toBeGreaterThanOrEqual(0);
  expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(page.viewportSize()!.width);
  await dialog.getByText("Consultar registro técnico original").click();
  await expect(dialog.getByText("DIRECT_API", { exact: true })).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(dialog).not.toBeVisible();
  await expect(trigger).toBeFocused();
});

test("premiações permitem comparar candidatos sem corte no objetivo", async ({ page }) => {
  await page.goto("/premiacoes");
  const section = page.getByRole("region", { name: "Possíveis candidatos a Ancião" });
  await expect(section).toBeVisible();
  const rows = section.locator("tbody tr");
  const expected = players.filter(p => p.currentMember && p.role === "member");
  await expect(rows).toHaveCount(expected.length);
  await page.getByLabel("Objetivo de referência (pontos)").fill("99999");
  await expect(rows).toHaveCount(expected.length);
  await expect(section.getByText(/pontos abaixo/).first()).toBeVisible();
  await page.getByLabel("Objetivo de referência (pontos)").fill("");
  await expect(section.getByRole("alert")).toBeVisible();
  await expect(rows).toHaveCount(expected.length);
});

test("corrida atual ordena pontos do maior ao menor", async ({ page }) => {
  await page.route("**/api/wars/current", route => route.fulfill({ json: {
    snapshot: { capturedAt: report.generatedAt, state: "warDay", periodType: "warDay" },
    race: { clan: { name: "Frota", participants: [
      { tag: "#A", name: "Zero", fame: 0, decksUsed: 0, decksUsedToday: 0 },
      { tag: "#B", name: "Líder", fame: 2400, decksUsed: 12, decksUsedToday: 4 },
      { tag: "#C", name: "Desconhecido", fame: null, decksUsed: null, decksUsedToday: null },
      { tag: "#D", name: "Empatado", fame: 2400, decksUsed: 12, decksUsedToday: 4 },
      { tag: "#E", name: "Intermediário", fame: 1600, decksUsed: 8, decksUsedToday: 4 },
    ] } },
  } }));
  await page.goto("/guerras");
  const rows = page.locator("tbody tr");
  await expect(rows).toHaveCount(5);
  await expect(rows.locator("td:first-child")).toHaveText(["1º", "1º", "3º", "4º", "—"]);
  await expect(rows.locator("td:nth-child(2) a")).toHaveText(["Líder", "Empatado", "Intermediário", "Zero", "Desconhecido"]);
});


test("mural mostra fechamento automático e títulos publicados", async ({ page }) => {
  await page.goto("/premiacoes");
  await expect(page.getByText(/Fechamento automático: Segunda-feira às 06h/)).toBeVisible();
  await expect(page.getByText(/Premiações publicadas para o fechamento/)).toBeVisible();
  const final = page.getByRole("heading", { name: "Títulos formalizados" });
  const projection = page.getByRole("heading", { name: "Projeção da semana" });
  await expect(final).toBeVisible();
  expect((await final.boundingBox())!.y).toBeLessThan((await projection.boundingBox())!.y);
  await expect(page.getByRole("link", { name: "Avaliação original →" })).toBeVisible();
});


test("líder registra contexto e encerra acompanhamento preservando histórico", async ({ page }) => {
  let saved: Record<string, unknown> | null = null;
  await page.route("**/api/leadership/**", async route => {
    const path = decodeURIComponent(new URL(route.request().url()).pathname);
    if (path.endsWith("/resolve")) {
      saved = { ...saved, status: "RESOLVED", followUpDue: false, resolvedAt: report.generatedAt, resolvedBy: "lider", resolution: route.request().postDataJSON().reason };
      return route.fulfill({ json: saved });
    }
    if (path.endsWith("/notes") && route.request().method() === "POST") {
      const body = route.request().postDataJSON();
      expect(body.requestKey).toBeTruthy();
      saved = { ...body, id:"note-1",playerTag:players[0].tag,createdAt:report.generatedAt,createdBy:"lider",status:"ACTIVE",followUpDue:true,resolvedAt:null };
      return route.fulfill({ json: saved });
    }
    if (path.endsWith("/active")) return route.fulfill({json:{generatedAt:report.generatedAt,timezone:"America/Sao_Paulo",notes:saved?.status==="ACTIVE"?[saved]:[]}});
    return route.fulfill({json:{items:saved?[saved]:[],totalElements:saved?1:0,page:0,size:20}});
  });
  await page.goto("/membros/" + encodeURIComponent(players[0].tag) + "?tab=notas");
  await page.getByLabel("Tipo de anotação").selectOption("JUSTIFICATION");
  await page.getByLabel("Descrição da anotação").fill("Avisou viagem; conversar no retorno.");
  await page.getByRole("button",{name:"Salvar anotação"}).click();
  await expect(page.getByText("Anotação registrada.")).toBeVisible();
  await expect(page.getByText("Avisou viagem; conversar no retorno.")).toBeVisible();
  await page.goto("/");
  const section=page.getByRole("region",{name:"Prioridades do líder"});
  await section.getByLabel("Filtrar prioridade").selectOption("Retomar acompanhamento");
  await expect(section.getByText("1 membros nesta seleção",{exact:false})).toBeVisible();
  await section.getByRole("link",{name:"Abrir histórico e notas →"}).click();
  await page.getByText("Encerrar acompanhamento",{exact:true}).click();
  await page.getByLabel("Resultado do acompanhamento").fill("Retornou e participou da guerra.");
  await page.getByRole("button",{name:"Salvar encerramento"}).click();
  await expect(page.getByText(/Encerrada por lider/)).toBeVisible();
  await expect(page.getByText("Avisou viagem; conversar no retorno.")).toBeVisible();
});

test("consulta não exibe notas nem prioridades privadas", async ({ page }) => {
  await page.route("**/api/auth/me",route=>route.fulfill({json:{username:"consulta",roles:["VIEWER"]}}));
  let privateRequests=0;
  page.on("request",r=>{if(r.url().includes("/api/leadership/")) privateRequests++;});
  await page.goto("/");
  await expect(page.getByRole("heading",{name:"O rumo da Frota"})).toBeVisible();
  await expect(page.getByRole("heading",{name:"Prioridades do líder"})).toHaveCount(0);
  await page.goto("/membros/"+encodeURIComponent(players[0].tag)+"?tab=notas");
  await expect(page.getByRole("heading",{name:players[0].name,exact:true})).toBeVisible();
  await expect(page.getByRole("button",{name:"Notas da liderança"})).toHaveCount(0);
  expect(privateRequests).toBe(0);
});

test("prioridades permitem percorrer toda a lista e reiniciam página ao buscar", async ({ page }) => {
  const roster = Array.from({length: 12}, (_,i) => ({...players[0], tag:`#TEST${i}`, name:`Tripulante ${i}`, currentMember:true,
    operationalStatus:{...players[0].operationalStatus,value:"OBSERVAR"}}));
  await page.route("**/api/analytics", route=>route.fulfill({json:{...report,players:roster}}));
  await page.goto("/");
  const region=page.getByRole("region",{name:"Prioridades do líder"});
  await expect(region.locator(".priority-list > li")).toHaveCount(10);
  await region.getByRole("button",{name:"Próximas prioridades"}).click();
  await expect(region.locator(".priority-list > li")).toHaveCount(2);
  await region.getByLabel("Buscar nas prioridades").fill("Tripulante 11");
  await expect(region.locator(".priority-list > li")).toHaveCount(1);
  await expect(region.getByText("Tripulante 11",{exact:true})).toBeVisible();
});
