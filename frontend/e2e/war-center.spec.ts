import { test, expect } from "@playwright/test";
const entries = [
  { playerTag: "#A", name: "Mesmo nome", contribution: 0, decksUsed: 0 },
  { playerTag: "#B", name: "Mesmo nome", contribution: 2000, decksUsed: 8 },
  { playerTag: "#C", name: "Sem dados", contribution: null, decksUsed: null },
];
const wars = [0, 1, 2, 3].map((i) => ({
  id: i + 1,
  seasonId: 1,
  sectionIndex: 4 - i,
  publishedAt: `2026-10-${String(26 - i * 7).padStart(2, "0")}T12:00:00Z`,
  capturedAt: "2026-10-27T12:00:00Z",
  startDate: null,
  endDate: null,
  source: "DIRECT_API",
  fame: 5000,
  clanScore: 12345,
}));
test.beforeEach(async ({ page }) => {
  await page.route("**/api/**", (r) => {
    const url = new URL(r.request().url()),
      p = url.pathname;
    let data: unknown;
    if (p === "/api/auth/me") data = { username: "test", roles: ["ADMIN"] };
    else if (p === "/api/wars") data = { items: wars, totalElements: 4 };
    else if (p === "/api/clan/members")
      data = [{ tag: "#A", name: "Mesmo nome" }];
    else if (p === "/api/clan/events")
      data = {
        items: [
          {
            playerTag: "#A",
            type: "BASELINE",
            observedAt: "2026-01-01T00:00:00Z",
          },
        ],
        totalElements: 1,
      };
    else if (p === "/api/analytics")
      data = {
        week: "2026-10-26",
        generatedAt: "2026-10-28T12:00:00Z",
        players: [
          {
            tag: "#A",
            donations: [
              {
                week: "2026-10-26",
                observedDonations: {
                  value: 0,
                  quality: "INCOMPLETE",
                  incompletePeriod: true,
                },
              },
            ],
          },
        ],
      };
    else if (p === "/api/wars/current")
      data = {
        snapshot: {
          capturedAt: "2026-10-28T12:00:00Z",
          periodType: "colosseum",
        },
        race: {
          clan: {
            participants: entries.map((x) => ({
              tag: x.playerTag,
              name: x.name,
              fame: x.contribution,
              decksUsed: x.decksUsed,
            })),
          },
        },
      };
    else if (/^\/api\/wars\/\d+$/.test(p))
      data = {
        war: wars[Number(p.split("/").at(-1)) - 1],
        participants: entries,
      };
    else return r.fulfill({ status: 404, json: { detail: "Ausente" } });
    return r.fulfill({ json: data });
  });
});
test("filtros combinados, URL, nomes iguais e cobertura", async ({ page }) => {
  await page.goto(
    "/guerras/1?membership=current&points=zero&decks=0%3A0&recurrence=twoZero",
  );
  const rows = page.locator(".war-player-table > table > tbody > tr");
  await expect(rows).toHaveCount(1);
  await expect(rows).toContainText("#A");
  await page.reload();
  await expect(rows).toHaveCount(1);
  await page
    .getByRole("combobox", { name: "Doações", exact: true })
    .selectOption("zero");
  await expect(rows).toHaveCount(0);
  await page
    .getByRole("button", { name: "Limpar filtros", exact: true })
    .click();
  await expect(rows).toHaveCount(3);
  await page.getByLabel("Vínculo", { exact: true }).selectOption("former");
  await expect(rows).toHaveCount(2);
  await expect(rows.first()).toContainText("#B");
});
test("comparação separa score de soma e mantém evidências", async ({
  page,
}, info) => {
  await page.goto("/guerras/1?compare=2");
  await expect(page.getByText("12.345", { exact: true }).first()).toBeVisible();
  await expect(page.getByText("2.000", { exact: true }).first()).toBeVisible();
  await page
    .getByRole("button", { name: "Evidências", exact: true })
    .first()
    .click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.keyboard.press("Escape");
  await page
    .getByRole("button", { name: "Evolução e contexto", exact: true })
    .first()
    .click();
  await expect(
    page.getByRole("img", { name: "Decks usados nas últimas quatro guerras" }),
  ).toBeVisible();
  await page.getByText("Colunas visíveis", { exact: true }).click();
  await page.getByLabel("Média de 4", { exact: true }).click();
  await expect(page).toHaveURL(/hide=average/);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({
    path: `test-results/war-center-${info.project.name}.png`,
    fullPage: true,
  });
});
test("captura atual é parcial e falta de vínculo não acusa novato", async ({
  page,
}) => {
  await page.goto("/guerras?recurrence=twoZero");
  await expect(page.getByText(/Captura parcial da corrida/)).toBeVisible();
  await page.route("**/api/clan/events*", (r) =>
    r.fulfill({
      json: {
        items: [
          { playerTag: "#A", type: "JOIN", observedAt: "2026-10-27T12:00:00Z" },
        ],
        totalElements: 1,
      },
    }),
  );
  await page.reload();
  await expect(
    page.locator(".war-player-table > table > tbody > tr"),
  ).toHaveCount(0);
});
