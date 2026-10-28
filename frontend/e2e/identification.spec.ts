import { test, expect } from "@playwright/test";

const candidate = {
  key: "WAR:1", kind: "WAR", targetId: 1, seasonId: 133, sectionIndex: 3,
  periodIndex: null, periodType: null, officialAt: "2026-10-26T10:00:00Z",
  equalFingerprint: true, matchingTags: 81, identicalFame: 81, identicalDecks: 81,
  identicalRows: 81, officialGreaterValues: 0, incompatibleValues: 0, unknownValues: 0,
  csvOnlyTags: 0, officialOnlyTags: 0, evidence: [] as string[],
};
const identity = {
  batchId: "batch-1", format: "RIVER", filename: "race.csv", clanTag: "#RQJGC9GV", clanName: "Frota Fantasma",
  match: "EXACT_MATCH", candidates: [candidate], warnings: ["Identificar não altera dados oficiais nem incorpora dados ao analytics."],
  suggestedCapturedAt: null as string | null, suggestedTimezone: "UTC", evidenceVersion: "version-1", history: [] as Record<string, unknown>[],
};
const initialAnalysis = {
  filename: "race.csv", rowsReceived: 81, playersIdentified: 81, rowsValid: 81, rowsNew: 0,
  rowsExisting: 0, rowsConflicted: 0, rowsInvalid: 0, rowsUnmatched: 81, warnings: [], errors: [], rows: [], existingBatchId: null,
};
async function setup(page: import("@playwright/test").Page, data = structuredClone(identity)) {
  const submissions: Record<string, unknown>[] = [];
  await page.route("**/api/**", async route => {
    const path = new URL(route.request().url()).pathname;
    let json: unknown;
    if (path === "/api/auth/me") json = { username: "leader", roles: ["ADMIN"] };
    else if (path === "/api/auth/csrf") json = { headerName: "X-CSRF-TOKEN", token: "csrf" };
    else if (path === "/api/operations") json = { sources: [], collectionEnabled: true, generatedAt: "2026-10-26T22:00:00Z" };
    else if (path.endsWith("/identification")) {
      if (route.request().method() === "GET") json = data;
      else {
        const body = route.request().postDataJSON(); submissions.push(body);
        expect(route.request().headers()["x-csrf-token"]).toBe("csrf");
        const selected = data.candidates.find(c => c.key === body.candidateKey);
        const result = { revision: data.history.length + 1, status: selected?.incompatibleValues ? "CONFLICT" : "IDENTIFIED", resolvedBy: "leader", resolvedAt: "2026-10-28T10:00:00Z", resolutionMethod: body.timestampOrigin || "USER_CONFIRMED_EXACT_MATCH", reason: body.reason, targetKind: selected?.kind, targetId: selected?.targetId, seasonId: selected?.seasonId, sectionIndex: selected?.sectionIndex, capturedAt: data.format === "MEMBERS" ? "2026-10-26T05:10:00Z" : null, timezone: body.timezone, evidence: body };
        data.history.unshift(result); json = result;
      }
    } else json = { items: path === "/api/imports/royaleapi" ? [{ id: "batch-1", filename: data.filename, source: "ROYALEAPI_IMPORT", importedAt: "2026-10-27T10:00:00Z", rowsImported: 0, rowsSkipped: 0, rowsConflicted: 0, rowsInvalid: 0, rowsUnmatched: 81, status: data.history[0]?.status || "PENDING_IDENTIFICATION", analysis: initialAnalysis }] : [], page: 0, size: 20, totalElements: path === "/api/imports/royaleapi" ? 1 : 0 };
    return route.fulfill({ json });
  });
  await page.goto("/operacoes");
  await page.getByRole("button", { name: "Identificar dados", exact: true }).click();
  return submissions;
}

test("correspondência exata exige seleção e confirmação, e mantém histórico", async ({ page }) => {
  const submissions = await setup(page);
  const dialog = page.getByRole("dialog", { name: "Identificar dados importados" });
  await expect(dialog.getByRole("heading", { name: "Correspondência encontrada" })).toBeVisible();
  await expect(dialog.getByText(/81 jogadores coincidentes/)).toBeVisible();
  const save = dialog.getByRole("button", { name: "Confirmar correspondência", exact: true });
  await expect(save).toBeDisabled();
  await dialog.getByRole("radio").check();
  await dialog.getByLabel("Motivo da resolução").fill("Conferi a corrida.");
  await dialog.getByRole("checkbox").check();
  await save.click();
  await expect(dialog.getByRole("status")).toContainText("Resolução registrada: Identificado");
  expect(submissions).toHaveLength(1);
  expect(submissions[0]).toMatchObject({ candidateKey: "WAR:1", confirmed: true, expectedRevision: 0, evidenceVersion: "version-1" });
  await expect(dialog.getByText(/Identificado ·/)).toBeVisible();
  await dialog.getByText(/Identificado ·/).click();
  await expect(dialog.getByText("Método: USER_CONFIRMED_EXACT_MATCH")).toBeVisible();
  expect(await dialog.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
  await dialog.getByRole("button", { name: "Fechar identificação" }).click();
  await expect(page.getByRole("button", { name: "Revisar identificação" })).toBeVisible();
});

test("candidatos possíveis mostram divergências e confirmação registra conflito", async ({ page }) => {
  const data = structuredClone(identity); data.match = "POSSIBLE_MATCH";
  data.candidates = [{ ...candidate, equalFingerprint: false, officialGreaterValues: 6 }, { ...candidate, key: "WAR:2", targetId: 2, equalFingerprint: false, incompatibleValues: 1, evidence: ["#P01: fame CSV=3300, oficial=3200 (incompatível)."] }];
  const submissions = await setup(page, data);
  const dialog = page.getByRole("dialog");
  await expect(dialog.getByRole("heading", { name: "Possíveis correspondências" })).toBeVisible();
  await dialog.getByRole("radio").nth(1).check();
  await expect(dialog.getByText(/registrará CONFLITO/)).toBeVisible();
  await dialog.getByText("Ver detalhes").nth(1).click();
  await expect(dialog.getByText(/CSV=3300/)).toBeVisible();
  await dialog.getByLabel("Motivo da resolução").fill("Associei para revisão da divergência.");
  await dialog.getByRole("checkbox").check();
  await dialog.getByRole("button", { name: "Confirmar correspondência", exact: true }).click();
  await expect(dialog.getByRole("status")).toContainText("Conflito");
  expect(submissions[0].candidateKey).toBe("WAR:2");
});

test("membros sugerem minuto, exigem fuso e preservam procedência da data alterada", async ({ page }) => {
  const data = structuredClone(identity); data.format = "MEMBERS"; data.candidates = []; data.match = "UNRESOLVED";
  data.filename = "clan-RQJGC9GV-2026-10-26-02-10-94.csv"; data.suggestedCapturedAt = "2026-10-26T02:10:00";
  const submissions = await setup(page, data);
  const dialog = page.getByRole("dialog");
  await expect(dialog.getByLabel("Data e hora da captura")).toHaveValue("2026-10-26T02:10");
  expect(submissions).toHaveLength(0);
  await dialog.getByLabel("Fuso horário da captura").fill("America/Sao_Paulo");
  await dialog.getByLabel("Motivo da resolução").fill("Conferi clã e horário no export.");
  await dialog.getByRole("checkbox").check();
  await dialog.getByRole("button", { name: "Confirmar origem", exact: true }).click();
  await expect(dialog.getByRole("status")).toContainText("Identificado");
  expect(submissions[0]).toMatchObject({ clanTag: "#RQJGC9GV", capturedAtLocal: "2026-10-26T02:10", timezone: "America/Sao_Paulo", timestampOrigin: "USER_CONFIRMED_FILENAME" });
  await dialog.getByRole("button", { name: "Alterar", exact: true }).click();
  await dialog.getByLabel("Data e hora da captura").fill("2026-10-26T03:10");
  await dialog.getByLabel("Motivo da resolução").fill("Correção confirmada pelo líder.");
  await dialog.getByRole("checkbox").check();
  await dialog.getByRole("button", { name: "Confirmar origem", exact: true }).click();
  await expect.poll(() => submissions.length).toBe(2);
  expect(submissions[1]).toMatchObject({ timestampOrigin: "USER_ENTERED", expectedRevision: 1 });
});

test("sem correspondência não confirma alvo e falhas de atualização são visíveis", async ({ page }) => {
  const data = structuredClone(identity); data.match = "UNRESOLVED"; data.candidates = [];
  const submissions = await setup(page, data);
  const dialog = page.getByRole("dialog");
  await expect(dialog.getByRole("heading", { name: "Sem correspondência segura" })).toBeVisible();
  await dialog.getByLabel("Motivo da resolução").fill("Sem prova.");
  await dialog.getByRole("checkbox").check();
  await expect(dialog.getByRole("button", { name: "Confirmar correspondência", exact: true })).toBeDisabled();
  expect(submissions).toHaveLength(0);
  await page.route("**/api/imports/royaleapi/batch-1/identification", route => route.fulfill({ status: 409, json: { detail: "As evidências oficiais mudaram." } }));
  await dialog.getByLabel("Ação", { exact: true }).selectOption("REJECTED");
  await dialog.getByRole("checkbox").check();
  await dialog.getByRole("button", { name: "Registrar revisão", exact: true }).click();
  await expect(dialog.getByRole("alert")).toHaveText("As evidências oficiais mudaram.");
});


test("lista extensa mantém todos os candidatos acessíveis sem selecionar alvo", async ({ page }) => {
  const data = structuredClone(identity); data.match = "POSSIBLE_MATCH";
  data.candidates = Array.from({ length: 25 }, (_, i) => ({ ...candidate, key: `WAR:${i + 1}`, targetId: i + 1 }));
  await setup(page, data);
  const dialog = page.getByRole("dialog");
  await expect(dialog.getByRole("radio")).toHaveCount(20);
  await expect(dialog.getByText(/Exibindo 20 de 25 candidatos/)).toBeVisible();
  await dialog.getByRole("button", { name: "Mostrar mais correspondências" }).click();
  await expect(dialog.getByRole("radio")).toHaveCount(25);
  await expect(dialog.locator('input[type="radio"]:checked')).toHaveCount(0);
});
