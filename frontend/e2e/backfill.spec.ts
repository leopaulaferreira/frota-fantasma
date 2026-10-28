import { test, expect } from "@playwright/test";
import { report } from "./fixtures";

const rows = [
  { line: 2, playerTag: "#P01", outcome: "NEW", reason: "Histórico ausente; backfill confirmado." },
  { line: 3, playerTag: "#P02", outcome: "DUPLICATE", reason: "Registro oficial idêntico." },
  { line: 4, playerTag: "#P03", outcome: "CONFLICT", reason: "Oficial=3200, CSV=3100. Oficial preservado." },
];
const plan = { batchId: "batch-1", resolutionId: 7, rowsAnalyzed: 3, rowsNew: 1, rowsDuplicated: 1, rowsConflicted: 1, rowsIgnored: 0, playersAffected: 1, periodFrom: "2026-10-05T10:00:00Z", periodTo: "2026-10-05T10:00:00Z", evidenceVersion: "version-1", rows, warnings: ["Valores conflitantes não entram nas métricas."], previous: null };
const result = { id: "application-1", status: "PARTIAL", rowsNew: 1, rowsDuplicated: 1, rowsConflicted: 1, rowsIgnored: 0, appliedAt: "2026-10-28T10:00:00Z", appliedBy: "leader", rows };
async function setup(page: import("@playwright/test").Page, identified = true) {
  let applied = false, writes = 0;
  await page.route("**/api/**", route => {
    const path = decodeURIComponent(new URL(route.request().url()).pathname);
    let json: unknown;
    if (path === "/api/auth/me") json = { username: "leader", roles: ["ADMIN"] };
    else if (path === "/api/auth/csrf") json = { headerName: "X-CSRF-TOKEN", token: "csrf" };
    else if (path === "/api/operations") json = { sources: [], collectionEnabled: true, generatedAt: "2026-10-28T10:00:00Z" };
    else if (path.endsWith("/backfill/preview")) json = { ...plan, previous: applied ? result : null };
    else if (path.endsWith("/backfill")) {
      expect(route.request().headers()["x-csrf-token"]).toBe("csrf");
      expect(route.request().postDataJSON()).toEqual({ resolutionId: 7, evidenceVersion: "version-1", confirmed: true });
      writes++; applied = true; json = result;
    } else if (path === "/api/imports/royaleapi") json = { items: [{ id: "batch-1", filename: "race.csv", source: "ROYALEAPI_IMPORT", importedAt: "2026-10-28T09:00:00Z", rowsImported: 0, rowsSkipped: 0, rowsConflicted: 0, rowsInvalid: 0, rowsUnmatched: 3, status: identified ? "IDENTIFIED" : "PENDING_IDENTIFICATION", analysis: { filename: "race.csv", rowsReceived: 3, playersIdentified: 3, rowsValid: 3, rowsNew: 0, rowsExisting: 0, rowsConflicted: 0, rowsInvalid: 0, rowsUnmatched: 3, warnings: [], errors: [], rows: [] }, incorporation: applied ? { ...result, influencesAnalytics: true, activeRecords: 1 } : null }], page: 0, size: 20, totalElements: 1 };
    else json = { items: [], page: 0, size: 20, totalElements: 0 };
    return route.fulfill({ json });
  });
  await page.goto("/operacoes");
  return () => writes;
}
test("prévia, cancelamento, incorporação parcial e resultado persistido", async ({ page }) => {
  const writes = await setup(page);
  await page.getByRole("button", { name: "Incorporar ao histórico", exact: true }).click();
  const dialog = page.getByRole("dialog", { name: "Incorporar ao histórico" });
  await expect(dialog.getByText(/3 registros analisados/)).toBeVisible();
  await expect(dialog.getByRole("button", { name: "Incorporar ao histórico", exact: true })).toBeDisabled();
  await dialog.getByRole("button", { name: "Cancelar / fechar" }).click(); expect(writes()).toBe(0);
  await page.getByRole("button", { name: "Incorporar ao histórico", exact: true }).click();
  await dialog.getByRole("checkbox").check();
  await dialog.getByRole("button", { name: "Incorporar ao histórico", exact: true }).click();
  await expect(dialog.getByRole("heading", { name: "Incorporação registrada com pendências" })).toBeVisible();
  await expect(dialog.getByText("1 conflitos", { exact: true })).toBeVisible();
  await dialog.getByText("Ver resultado por registro").click();
  await expect(dialog.getByText("Oficial=3200, CSV=3100. Oficial preservado.")).toBeVisible();
  await dialog.getByRole("button", { name: "Cancelar / fechar" }).click();
  await expect(page.getByText(/Analytics: Sim/)).toBeVisible();
  await page.getByRole("button", { name: "Consultar incorporação" }).click();
  await expect(dialog.getByRole("heading", { name: "Incorporação registrada com pendências" })).toBeVisible();
  await expect(dialog.getByRole("button", { name: "Incorporar ao histórico", exact: true })).toHaveCount(0);
  expect(writes()).toBe(1);
  expect(await dialog.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
});
test("pendente não tem ação e prévia desatualizada exige nova análise", async ({ page }) => {
  await setup(page, false);
  await expect(page.getByRole("button", { name: "Incorporar ao histórico", exact: true })).toHaveCount(0);
  await setup(page, true);
  await page.getByRole("button", { name: "Incorporar ao histórico", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await page.route("**/api/imports/royaleapi/batch-1/backfill", route => route.fulfill({ status: 409, json: { detail: "O histórico mudou desde a prévia. Analise novamente." } }));
  await dialog.getByRole("checkbox").check();
  await dialog.getByRole("button", { name: "Incorporar ao histórico", exact: true }).click();
  await expect(dialog.getByRole("alert")).toContainText("O histórico mudou");
});
test("histórico importado aparece no perfil e em Comparar com fontes explícitas", async ({ page }) => {
  const data = structuredClone(report); const player = data.players[0];
  player.wars[0].points = { ...player.wars[0].points, origin: "ROYALEAPI_IMPORT", sources: ["ROYALEAPI_IMPORT"], incompletePeriod: true };
  player.metrics.recentAverage = { ...player.metrics.recentAverage, sources: ["DIRECT_API", "ROYALEAPI_IMPORT"], dataUsed: ["source=DIRECT_API", "source=ROYALEAPI_IMPORT;batch=batch-1;resolution=7"], incompletePeriod: true };
  await page.route("**/api/**", route => {
    const path = decodeURIComponent(new URL(route.request().url()).pathname);
    return route.fulfill({ json: path === "/api/auth/me" ? { username: "viewer", roles: ["VIEWER"] } : path === "/api/analytics" ? data : path.startsWith("/api/analytics/players/") ? player : { items: [], totalElements: 0, page: 0, size: 20 } });
  });
  await page.goto(`/membros/${encodeURIComponent(player.tag)}`);
  await page.getByRole("button", { name: "Guerras", exact: true }).click();
  await expect(page.getByRole("heading", { name: /Histórico completo/ })).toBeVisible();
  await page.goto(`/comparar?tags=${encodeURIComponent(player.tag)}`);
  await expect(page.getByRole("heading", { name: "Guerra a guerra" })).toBeVisible();
  const row = page.getByRole("row").filter({ has: page.getByRole("rowheader", { name: "Média das últimas 4 guerras", exact: true }) });
  await row.getByRole("button").click();
  await expect(page.getByRole("dialog").getByText(/Fontes utilizadas: Informado pela API oficial \+ Histórico importado do RoyaleAPI/)).toBeVisible();
});
