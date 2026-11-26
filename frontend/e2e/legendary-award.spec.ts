import { test, expect } from "@playwright/test";
import { report } from "./fixtures";
import type { LegendaryOverview, LegendaryCandidate } from "../src/components/LegendaryAward";

const candidate: LegendaryCandidate = {
  position: 1, tag: "#AAA", name: "Membro de teste", previousTrophies: 10500, trophies: 11200,
  netChange: 700, previousRecord: 11000, record: 11200, improvement: 200,
  warMedals: 1600, decksUsed: 16, eligible: true, reasons: [], capturedAt: "2026-12-07T09:01:00Z", apiBestTrophies: 13000,
};
const baseline: LegendaryOverview = {
  enabled: true, official: false, recordSource: "OBSERVED", recordEra: "test-era",
  nextCollectionAt: "2026-12-14T09:00:00Z", weeks: [], page: 0, totalWeeks: 0, result: null,
};
function result(week = "2026-12-07"): LegendaryOverview {
  return { ...baseline, weeks: ["2026-12-07", "2026-11-30"], totalWeeks: 2, result: {
    cycle: { week, closesAt: week + "T09:00:00Z", deadline: week + "T11:00:00Z", finalizedAt: week + "T09:02:00Z",
      status: "COMPLETE", recordSource: "OBSERVED", recordEra: "test-era", official: false, winnerTag: "#AAA" },
    candidates: [candidate, { ...candidate, tag: "#BBB", name: "Sem histórico", previousTrophies: null, previousRecord: null,
      improvement: null, netChange: null, eligible: false, position: null, reasons: ["Sem base válida na semana anterior"] }],
    notice: "Resultado experimental. Não integra os títulos nem as mensagens oficiais do clã.",
  }};
}
test.beforeEach(async ({ page }) => {
  await page.route("**/api/**", async route => {
    const path = new URL(route.request().url()).pathname;
    const data = path === "/api/auth/me" ? { username: "viewer", roles: ["VIEWER"] }
      : path === "/api/analytics" ? report
      : path === "/api/recommendations/automation" ? { enabled: true, pending: false, closingWeek: "2026-11-30", schedule: "Segunda-feira às 06h", timezone: "America/Sao_Paulo" }
      : path === "/api/legendary-awards" ? baseline
      : { items: [], page: 0, size: 20, totalElements: 0 };
    await route.fulfill({ json: data });
  });
});
test("primeira coleta não inventa vencedor nem trata ausência como zero", async ({ page }) => {
  await page.goto("/premiacoes");
  const section = page.getByRole("region", { name: "Fantasma Lendário", exact: true });
  await expect(section.getByText("Aguardando dados para comparação")).toBeVisible();
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await section.getByRole("button", { name: "Informações sobre Fantasma Lendário" }).click();
  const dialog = page.getByRole("dialog", { name: "Fantasma Lendário" });
  await expect(dialog.getByText("Recorde observado pelo sistema", { exact: true })).toBeVisible();
  await expect(dialog.getByText(/A primeira coleta estabelece/)).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(dialog).not.toBeVisible();
  await expect(section.getByRole("button", { name: "Informações sobre Fantasma Lendário" })).toBeFocused();
  await expect(section.locator(".legendary-card .award-winner")).toHaveCount(0);
  const cards = page.locator(".awards-four > .royale-award");
  await expect(cards).toHaveCount(4);
  const boxes = await cards.evaluateAll(nodes => nodes.map(n => (n as HTMLElement).offsetTop));
  expect(new Set(boxes).size).toBe(1);
});
test("mostra superação separada da evolução e consulta semanas anteriores", async ({ page }) => {
  await page.route("**/api/legendary-awards?*", async route => {
    const week = new URL(route.request().url()).searchParams.get("week") || "2026-12-07";
    const data = result(week);
    if (week === "2026-11-30") {
      data.result!.cycle.winnerTag = null;
      data.result!.candidates = [{ ...candidate, position: null, previousRecord: null, improvement: null, eligible: false, reasons: ["Sem base válida na semana anterior"] }];
    }
    await route.fulfill({ json: data });
  });
  await page.goto("/premiacoes");
  const table = page.getByRole("region", { name: "Acompanhamento do Fantasma Lendário" });
  const row = table.getByRole("row").filter({ hasText: "Membro de teste" });
  await expect(row).toContainText("+200"); await expect(row).toContainText("+700");
  await expect(table.getByRole("row").filter({ hasText: "Sem histórico" })).toContainText("—");
  await expect(page.locator(".legendary-card .award-winner")).toHaveText("Membro de teste");
  await expect(page.locator(".legendary-card")).toContainText("11.000");
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.locator(".legendary-section").screenshot({ path: "test-results/legendary-award.png" });
  await page.getByLabel("Encerramento do ciclo").selectOption("2026-11-30");
  await expect(page.locator(".legendary-card")).toContainText("Sem vencedor neste ciclo");
});
test("coleta parcial mostra candidatos sem anunciar vencedor", async ({ page }) => {
  const data = result(); data.result!.cycle.status = "INCOMPLETE"; data.result!.cycle.winnerTag = null;
  data.result!.notice = "Ciclo sem resultado: coleta incompleta ou guerra oficial indisponível.";
  await page.route("**/api/legendary-awards?*", route => route.fulfill({ json: data }));
  await page.goto("/premiacoes");
  await expect(page.getByText("Dados incompletos", { exact: true })).toBeVisible();
  await expect(page.locator(".legendary-card .award-winner")).toHaveCount(0);
  await expect(page.locator(".legendary-card")).toContainText("Sem vencedor neste ciclo");
});
test("erro na consulta permite tentar novamente", async ({ page }) => {
  let fail = true;
  await page.route("**/api/legendary-awards?*", route => fail
    ? route.fulfill({ status: 503, json: { detail: "Consulta temporariamente indisponível" } })
    : route.fulfill({ json: baseline }));
  await page.goto("/premiacoes");
  await expect(page.locator(".awards-four").getByRole("alert")).toContainText("Consulta temporariamente indisponível");
  fail = false;
  await page.locator(".awards-four").getByRole("button", { name: "Tentar novamente" }).click();
  await expect(page.locator(".legendary-card")).toContainText("Aguardando dados para comparação");
});
