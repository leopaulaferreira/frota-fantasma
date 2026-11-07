import { test, expect } from "@playwright/test";
import { report } from "./fixtures";
const wars = Array.from({ length: 8 }, (_, i) => ({
  id: i + 1,
  seasonId: 137 - Math.floor(i / 4),
  sectionIndex: 3 - (i % 4),
  publishedAt: new Date(
    Date.parse("2026-10-12T10:00:00Z") - i * 7 * 86400000,
  ).toISOString(),
  capturedAt: "2026-10-12T11:00:00Z",
  startDate: null,
  endDate: null,
  source: "DIRECT_API",
  fame: 40000,
  clanScore: 2000,
}));
const participants = [
  {
    playerTag: "#P01",
    name: "Mesmo nome",
    contribution: 0,
    decksUsed: 0,
    source: "DIRECT_API",
  },
  {
    playerTag: "#P02",
    name: "Mesmo nome",
    contribution: 2500,
    decksUsed: 16,
    source: "DIRECT_API",
  },
  {
    playerTag: "#EX",
    name: "Ex participante",
    contribution: 1000,
    decksUsed: 4,
    source: "DIRECT_API",
  },
];
test.beforeEach(async ({ page }) => {
  await page.route("**/api/**", (route) => {
    const path = new URL(route.request().url()).pathname;
    let data: unknown;
    if (path === "/api/auth/me") data = { username: "test", roles: ["ADMIN"] };
    else if (path === "/api/wars")
      data = { items: wars, totalElements: wars.length };
    else if (path === "/api/clan/members")
      data = [
        { tag: "#P01", name: "Mesmo nome" },
        { tag: "#P02", name: "Mesmo nome" },
        { tag: "#NEW", name: "Novato" },
      ];
    else if (path === "/api/clan/events")
      data = {
        items: participants.map((p) => ({
          playerTag: p.playerTag,
          type: "BASELINE",
          observedAt: "2026-01-01T00:00:00Z",
        })),
        totalElements: 3,
      };
    else if (path === "/api/analytics") data = report;
    else if (/^\/api\/wars\/\d+$/.test(path))
      data = { war: wars[Number(path.split("/").at(-1)) - 1], participants };
    else return route.fulfill({ status: 404, json: { detail: "Ausente" } });
    return route.fulfill({ json: data });
  });
});
test("matriz única, filtros combinados e detalhe acessível", async ({
  page,
}) => {
  await page.goto("/comparativo");
  await expect(
    page.getByRole("heading", { name: "Comparativo de Guerras" }),
  ).toBeVisible();
  await expect(page.locator(".comparison-table tbody tr")).toHaveCount(3);
  await expect(page.locator(".comparison-table thead th")).toHaveCount(7);
  await page.screenshot({
    path: "/tmp/comparativo-" + test.info().project.name + ".png",
    fullPage: true,
  });
  await expect(page.locator(".comparison-table")).not.toContainText(
    "Ex participante",
  );
  await page.getByLabel("Incluir ex-membros").click();
  await expect(page.getByLabel("Incluir ex-membros")).toBeChecked();
  await expect(page.locator(".comparison-table tbody tr")).toHaveCount(4);
  await page.getByLabel("Nome ou tag").fill("#P01");
  await expect(page).toHaveURL(/q=%23P01/);
  await page.getByLabel("Cumprimento das regras").selectOption("repeat");
  await expect(page.locator(".comparison-table tbody tr")).toHaveCount(1);
  await expect(page.locator(".comparison-table")).toContainText("Reincidência");
  await expect(page).toHaveURL(/rule=repeat/);
  await page.reload();
  await expect(page.getByLabel("Nome ou tag")).toHaveValue("#P01");
  await page
    .locator(".comparison-table")
    .getByRole("button", { name: /Detalhes de Mesmo nome/ })
    .click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await expect(page.getByRole("dialog")).toContainText("#P01");
  await expect(page.getByRole("dialog")).toContainText("0/16 decks usados");
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await page.getByRole("button", { name: "Limpar filtros" }).click();
  await expect(page.locator(".comparison-table tbody tr")).toHaveCount(3);
  await page.getByLabel("Cumprimento das regras").selectOption("full");
  await expect(page.locator(".comparison-table tbody tr")).toHaveCount(1);
  await expect(page.locator(".comparison-table")).toContainText("#P02");
});
test("períodos, seleção manual, amostra incompleta e retorno ao padrão", async ({
  page,
}) => {
  await page.goto("/comparativo");
  await page.getByRole("button", { name: "Quatro guerras anteriores" }).click();
  await expect(page.locator(".comparison-table thead")).toContainText(
    "14/09/2026",
  );
  await page.getByRole("button", { name: "Período mais recente" }).click();
  await expect(page.locator(".comparison-table thead")).toContainText(
    "12/10/2026",
  );
  await page.getByText("Selecionar guerras históricas (4/4)").click();
  const selected = page.locator(".comparison-picker input:checked");
  await selected.first().click();
  await expect(
    page.getByText("Amostra limitada:", { exact: false }),
  ).toBeVisible();
  await expect(page.locator(".comparison-table thead th")).toHaveCount(6);
  await page.reload();
  await expect(page.locator(".comparison-table thead th")).toHaveCount(6);
  await page.getByRole("button", { name: "Últimas quatro" }).click();
  await expect(page.locator(".comparison-table thead th")).toHaveCount(7);
  await page.getByLabel("Cumprimento das regras").selectOption("missing");
  await expect(page.locator(".comparison-table")).toContainText("Novato");
  await expect(page.locator(".comparison-table")).toContainText(
    "Vínculo incerto",
  );
  await page.getByLabel("Nome ou tag").fill("inexistente");
  await expect(
    page.getByText("Nenhum jogador atende aos filtros.", { exact: false }),
  ).toBeVisible();
});
test("erro de consulta não vira resultados vazios ou zeros", async ({
  page,
}) => {
  await page.route("**/api/clan/events?*", (r) =>
    r.fulfill({ status: 500, json: { detail: "Falha de teste" } }),
  );
  await page.goto("/comparativo");
  await expect(page.getByRole("alert")).toContainText("Consulta indisponível", {
    timeout: 15000,
  });
  await expect(page.locator(".comparison-table")).toHaveCount(0);
});

test("zero oficial de recém-chegado permanece na média e nos filtros sem virar reincidência", async ({
  page,
}) => {
  const selected = wars.slice(0, 3).map((w, i) => ({
    ...w,
    publishedAt: new Date(
      Date.parse("2026-11-02T09:41:00Z") - i * 7 * 86400000,
    ).toISOString(),
  }));
  await page.route("**/api/analytics", (r) =>
    r.fulfill({ json: { ...report, generatedAt: "2026-11-06T00:00:00Z" } }),
  );
  await page.route("**/api/clan/members", (r) =>
    r.fulfill({ json: [{ tag: "#W", name: "Wakasa" }] }),
  );
  await page.route("**/api/clan/events?*", (r) =>
    r.fulfill({
      json: {
        items: [
          { playerTag: "#W", type: "JOIN", observedAt: "2026-10-28T14:03:26Z" },
        ],
        totalElements: 1,
      },
    }),
  );
  await page.route(/\/api\/wars(?:\?.*|\/\d+)$/, (r) => {
    const path = new URL(r.request().url()).pathname;
    if (path === "/api/wars")
      return r.fulfill({
        json: { items: selected, totalElements: selected.length },
      });
    const w = selected.find((w) => w.id === Number(path.split("/").at(-1)));
    return r.fulfill({
      json: {
        war: w,
        participants:
          w?.id === 1
            ? [
                {
                  playerTag: "#W",
                  name: "Wakasa",
                  contribution: 0,
                  decksUsed: 0,
                  source: "DIRECT_API",
                },
              ]
            : [],
      },
    });
  });
  await page.goto("/comparativo");
  const table = page.locator(".comparison-table");
  await expect(table).toContainText("1/3 com pontuação oficial");
  await expect(table).toContainText("Histórico parcial");
  await expect(
    table.getByRole("button", { name: /participação integral não comprovada/ }),
  ).toBeVisible();
  await expect(table.locator("tbody td").nth(3).locator("strong")).toHaveText(
    "0 pts",
  );
  await expect(table.locator(".comparison-result.bad")).toHaveCount(0);
  await page.getByLabel("Cumprimento das regras").selectOption("zero");
  await expect(table).toContainText("Wakasa");
  await page.getByLabel("Cumprimento das regras").selectOption("repeat");
  await expect(
    page.getByText("Nenhum jogador atende aos filtros.", { exact: false }),
  ).toBeVisible();
});

test("resultado ausente de membro elegível fica amarelo e tem filtro próprio", async ({
  page,
}) => {
  await page.route(/\/api\/wars\/\d+$/, (r) => {
    const id = Number(new URL(r.request().url()).pathname.split("/").at(-1));
    return r.fulfill({
      json: {
        war: wars[id - 1],
        participants: participants.filter((p) => p.playerTag !== "#P01"),
      },
    });
  });
  await page.goto("/comparativo");
  await page.getByLabel("Cumprimento das regras").selectOption("unrecorded");
  const table = page.locator(".comparison-table");
  await expect(table.locator("tbody tr")).toHaveCount(1);
  await expect(table).toContainText("#P01");
  await expect(table).toContainText("Conferir registro");
  await expect(table.locator(".comparison-result.warn").first()).toContainText(
    "Conferir registro",
  );
  await expect(table.locator(".comparison-result.bad")).toHaveCount(0);
  await page.getByLabel("Cumprimento das regras").selectOption("repeat");
  await expect(
    page.getByText("Nenhum jogador atende aos filtros.", { exact: false }),
  ).toBeVisible();
});

test("hierarquia compacta preserva perfil e metadados da publicação",async({page})=>{
  await page.goto("/comparativo");
  const table=page.locator(".comparison-table");
  await expect(table.locator(".comparison-name").first()).toHaveAttribute("href","/membros/%23P02");
  await expect(table.locator(".comparison-average").first()).toContainText("4/4 metas cumpridas");
  await expect(table).toContainText("Metas: sem amostra");
  await table.getByLabel("Informações da guerra 12/10/2026").click();
  await expect(table.locator(".comparison-metadata[open]").getByText("Data de publicação",{exact:true})).toBeVisible();
  await expect(table.getByText("Temporada 137 · seção 3")).toBeVisible();
  await expect(table.locator(".comparison-metadata[open]").getByText("Resultado oficial",{exact:true})).toBeVisible();
});
