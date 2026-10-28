import { test, expect } from "@playwright/test";

const analysis = {
  filename: "clan.csv", rowsReceived: 2, playersIdentified: 1, rowsValid: 2,
  rowsNew: 0, rowsExisting: 0, rowsConflicted: 0, rowsInvalid: 0, rowsUnmatched: 2,
  warnings: ["Faltam temporada e seção. Nenhuma linha será incorporada às métricas."], errors: [],
  rows: [{ line: 2, playerTag: "#P01", status: "UNMATCHED", reason: "Guerra não identificada." }], existingBatchId: null,
};
const batch = { id: "batch-1", filename: "clan.csv", source: "ROYALEAPI_IMPORT", importedAt: "2026-10-26T22:00:00Z", rowsImported: 0, rowsSkipped: 0, rowsConflicted: 0, rowsInvalid: 0, rowsUnmatched: 2, status: "PENDING_IDENTIFICATION", analysis };
const file = { name: "clan.csv", mimeType: "text/csv", buffer: Buffer.from("sample") };
test.beforeEach(async ({ page }) => {
  await page.route("**/api/**", route => {
    const path = new URL(route.request().url()).pathname;
    const json = path === "/api/auth/me" ? { username: "leader", roles: ["ADMIN"] }
      : path === "/api/auth/csrf" ? { headerName: "X-CSRF-TOKEN", token: "csrf" }
      : path === "/api/operations" ? { sources: [], collectionEnabled: true, generatedAt: "2026-10-26T22:00:00Z" }
      : path === "/api/imports/royaleapi/preview" ? { analysis, previewToken: "ticket" }
      : { items: [], page: 0, size: 20, totalElements: 0 };
    return route.fulfill({ json });
  });
});

test("abre CSV, analisa, cancela sem gravar, confirma pendências e mostra auditoria", async ({ page }) => {
  let imports = 0;
  await page.route("**/api/imports/royaleapi*", async route => {
    if (route.request().method() === "POST") {
      imports++;
      expect(route.request().headers()["x-csrf-token"]).toBe("csrf");
      expect(route.request().headers()["content-type"]).toContain("multipart/form-data; boundary=");
      expect(route.request().postData()).toContain("ticket");
      return route.fulfill({ json: { batch, alreadyProcessed: imports > 1 } });
    }
    return route.fulfill({ json: { items: imports ? [batch] : [], page: 0, size: 20, totalElements: imports ? 1 : 0 } });
  });
  await page.goto("/operacoes");
  await page.getByRole("button", { name: "Importar CSV", exact: true }).click();
  const dialog = page.getByRole("dialog", { name: "Importar CSV do RoyaleAPI" });
  await expect(dialog.getByRole("button", { name: "Analisar arquivo" })).toBeDisabled();
  await dialog.getByLabel("Selecionar CSV").setInputFiles(file);
  await dialog.getByRole("button", { name: "Analisar arquivo" }).click();
  await expect(dialog.getByText("2 registros analisados", { exact: false })).toBeVisible();
  await dialog.getByRole("button", { name: "Cancelar" }).click();
  await expect(dialog).not.toBeVisible();
  expect(imports).toBe(0);
  await page.getByRole("button", { name: "Importar CSV", exact: true }).click();
  await dialog.getByLabel("Selecionar CSV").setInputFiles(file);
  await dialog.getByRole("button", { name: "Analisar arquivo" }).click();
  await dialog.getByRole("button", { name: "Registrar pendências (sem alterar métricas)" }).click();
  await expect(dialog.getByRole("heading", { name: "Lote registrado com pendências" })).toBeVisible();
  await expect(dialog.getByText("0 registros incorporados às métricas; 2 pendentes de identificação.")).toBeVisible();
  await dialog.getByRole("button", { name: "Fechar", exact: true }).click();
  await expect(page.getByRole("cell", { name: "ROYALEAPI_IMPORT", exact: true })).toBeVisible();
  await expect(page.getByRole("cell", { name: "Pendente de identificação", exact: true })).toBeVisible();
  expect(imports).toBe(1);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test("loading, erros e troca de arquivo invalidam a confirmação", async ({ page }) => {
  let release!: () => void;
  const hold = new Promise<void>(resolve => { release = resolve; });
  await page.route("**/api/imports/royaleapi/preview", async route => {
    await hold;
    return route.fulfill({ status: 400, json: { detail: "Cabeçalho desconhecido." } });
  });
  await page.goto("/operacoes");
  await page.getByRole("button", { name: "Importar CSV", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Selecionar CSV").setInputFiles(file);
  await dialog.getByRole("button", { name: "Analisar arquivo" }).click();
  await expect(dialog.getByRole("button", { name: "Analisando…" })).toBeDisabled();
  await expect(dialog.getByRole("button", { name: "Cancelar" })).toBeDisabled();
  release();
  await expect(dialog.getByRole("alert")).toHaveText("Cabeçalho desconhecido.");
  await page.route("**/api/imports/royaleapi/preview", route => route.fulfill({ json: { analysis, previewToken: "ticket" } }));
  await dialog.getByRole("button", { name: "Analisar arquivo" }).click();
  await expect(dialog.getByRole("button", { name: "Registrar pendências (sem alterar métricas)" })).toBeEnabled();
  await page.route("**/api/imports/royaleapi", route => route.fulfill({ status: 409, json: { detail: "A prévia expirou. Analise novamente." } }));
  await dialog.getByRole("button", { name: "Registrar pendências (sem alterar métricas)" }).click();
  await expect(dialog.getByRole("alert")).toContainText("A prévia expirou");
  await dialog.getByLabel("Selecionar CSV").setInputFiles({ ...file, name: "outro.csv" });
  await expect(dialog.getByRole("button", { name: "Registrar pendências (sem alterar métricas)" })).toHaveCount(0);
});

test("expõe conflitos e inválidos sem oferecer confirmação indevida", async ({ page }) => {
  await page.route("**/api/imports/royaleapi/preview", route => route.fulfill({ json: {
    previewToken: "ticket", analysis: { ...analysis, rowsConflicted: 1, rowsInvalid: 1, rowsUnmatched: 0,
      errors: ["CSV contém registros inválidos."], rows: [{ line: 2, playerTag: "#P01", status: "CONFLICT", reason: "Valor oficial preservado." }] },
  } }));
  await page.goto("/operacoes");
  await page.getByRole("button", { name: "Importar CSV", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Selecionar CSV").setInputFiles(file);
  await dialog.getByRole("button", { name: "Analisar arquivo" }).click();
  await expect(dialog.getByRole("alert")).toHaveText("CSV contém registros inválidos.");
  await expect(dialog.getByRole("button", { name: "Registrar pendências (sem alterar métricas)" })).toBeDisabled();
  await dialog.getByText("Ver resultados por registro").click();
  await expect(dialog.getByText("Valor oficial preservado.")).toBeVisible();
});

test("VIEWER não acessa importações nem carrega auditoria", async ({ page }) => {
  let accesses = 0;
  await page.route("**/api/auth/me", route => route.fulfill({ json: { username: "viewer", roles: ["VIEWER"] } }));
  await page.route("**/api/imports/**", route => { accesses++; return route.fulfill({ status: 403 }); });
  await page.goto("/operacoes");
  await expect(page.getByRole("heading", { name: "Operações da Frota" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Importar CSV", exact: true })).toHaveCount(0);
  expect(accesses).toBe(0);
});
