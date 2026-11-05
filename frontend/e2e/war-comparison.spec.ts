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
  await page.getByLabel("Cumprimento das regras").selectOption("repeat");
  await expect(page.locator(".comparison-table tbody tr")).toHaveCount(1);
  await expect(page.locator(".comparison-table")).toContainText("Revisão");
  await page.reload();
  await expect(page.getByLabel("Nome ou tag")).toHaveValue("#P01");
  await page
    .locator(".comparison-table")
    .getByRole("button", { name: "Mesmo nome" })
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
  await expect(page.locator(".comparison-table thead")).toContainText("T136");
  await page.getByRole("button", { name: "Período mais recente" }).click();
  await expect(page.locator(".comparison-table thead")).toContainText("T137");
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
  await expect(page.locator(".comparison-table")).toContainText("Não coletado");
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
