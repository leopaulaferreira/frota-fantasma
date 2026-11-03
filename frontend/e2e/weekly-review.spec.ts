import { test, expect } from "@playwright/test";
import type {
  WeeklyReviewReport,
  ParticipationContext,
} from "../src/lib/weeklyReview";
const report: WeeklyReviewReport = {
  week: "2026-10-12",
  generatedAt: "2026-10-19T00:59:00Z",
  cutoff: "2026-10-19T00:59:00Z",
  deadline: "2026-10-19T01:00:00Z",
  timezone: "America/Sao_Paulo",
  phase: "LIVE",
  pointsTarget: 1600,
  offlineDays: 5,
  collectionEnabled: true,
  membersCapturedAt: "2026-10-19T00:59:00Z",
  riverCapturedAt: "2026-10-19T00:58:00Z",
  riverSnapshotId: 1,
  membersFresh: true,
  riverFresh: true,
  rosterComplete: true,
  members: [
    {
      tag: "#BOTH",
      name: "Duas regras",
      role: "member",
      points: 900,
      decksUsed: 6,
      lastSeen: "2026-10-12T00:59:00Z",
      offlineSeconds: 604800,
      observedSince: "2026-08-01T00:00:00Z",
      belowPoints: true,
      offlineExceeded: true,
      status: "REVIEW",
      missingEvidence: [],
    },
    {
      tag: "#LOW",
      name: "Viagem avisada",
      role: "elder",
      points: 1599,
      decksUsed: 10,
      lastSeen: "2026-10-19T00:00:00Z",
      offlineSeconds: 3540,
      observedSince: "2026-10-16T00:00:00Z",
      belowPoints: true,
      offlineExceeded: false,
      status: "REVIEW",
      missingEvidence: [],
    },
    {
      tag: "#UNKNOWN",
      name: "Sem registro",
      role: "member",
      points: null,
      decksUsed: null,
      lastSeen: null,
      offlineSeconds: null,
      observedSince: null,
      belowPoints: false,
      offlineExceeded: false,
      status: "UNKNOWN",
      missingEvidence: [
        "Pontuação não informada para este membro; ausência de registro não equivale a zero.",
      ],
    },
    {
      tag: "#CLEAR",
      name: "No limite",
      role: "leader",
      points: 1600,
      decksUsed: 16,
      lastSeen: "2026-10-14T00:59:00Z",
      offlineSeconds: 432000,
      observedSince: null,
      belowPoints: false,
      offlineExceeded: false,
      status: "CLEAR",
      missingEvidence: [],
    },
  ],
};
const context: ParticipationContext = {
  wars: [
    {
      id: 1,
      publishedAt: "2026-10-12T10:00:00Z",
      capturedAt: "2026-10-12T12:00:00Z",
      points: 0,
      decks: 0,
      eligible: true,
      source: "DIRECT_API",
      reason: "Vínculo anterior à guerra",
    },
  ],
  averageFour: 0,
  warsConsidered: 2,
  belowTarget: 2,
  consecutiveZeroAttacks: 2,
  twoBelow: true,
  twoZero: true,
  trend: "Participação estável",
  donationWeeks: [
    {
      week: "2026-10-12",
      observed: 0,
      lastCounter: 0,
      samples: 2,
      resets: 0,
      complete: false,
      zeroConfirmed: false,
      reason: "Parcial",
    },
    {
      week: "2026-10-05",
      observed: 0,
      lastCounter: 0,
      samples: 168,
      resets: 0,
      complete: true,
      zeroConfirmed: true,
      reason: "Cobertura observada",
    },
  ],
  totalFourWeeks: 0,
  donationCoverageComplete: true,
  zeroDonationWeeks: 2,
  twoZeroDonations: true,
  events: [{ type: "BASELINE", at: "2026-08-01T00:00:00Z" }],
  tenureDays: 70,
  newcomer: false,
  rejoined: false,
  tenureBasis: "Observado desde",
  group: "PRIORITY",
  reasons: ["Duas guerras abaixo da meta"],
};
report.members[0].participation = context;
report.members[1].participation = {
  ...context,
  group: "FOLLOW",
  newcomer: true,
  twoBelow: false,
  twoZero: false,
  twoZeroDonations: false,
};
report.members[2].participation = {
  ...context,
  group: "INSUFFICIENT",
  wars: [],
  averageFour: null,
  twoBelow: false,
  twoZero: false,
  twoZeroDonations: false,
};
report.members[3].participation = {
  ...context,
  group: "FOLLOW",
  twoBelow: false,
  twoZero: false,
  twoZeroDonations: false,
};
const rows = (page: import("@playwright/test").Page) =>
  page
    .locator(".review-table > table > tbody > tr")
    .filter({ has: page.locator("button[aria-expanded]") });
test.beforeEach(async ({ page }) => {
  await page.route("**/api/**", (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/auth/me")
      return route.fulfill({ json: { username: "lider", roles: ["ADMIN"] } });
    if (path === "/api/auth/csrf")
      return route.fulfill({
        json: { headerName: "X-CSRF-TOKEN", token: "test-token" },
      });
    if (path === "/api/leadership/weekly-review/decisions")
      return route.fulfill({ json: [] });
    if (path === "/api/leadership/weekly-review")
      return route.fulfill({ json: report });
    if (path === "/api/leadership/notes/active")
      return route.fulfill({
        json: {
          generatedAt: report.generatedAt,
          timezone: report.timezone,
          notes: [],
        },
      });
    return route.fulfill({ status: 404, json: { detail: "Sem registro" } });
  });
});

test("tabela ordenável, filtros de histórico e detalhes", async ({
  page,
}, info) => {
  await page.goto("/expulsoes");
  await expect(rows(page)).toHaveCount(4);
  await page.getByLabel("Critério de revisão").selectOption("twoBelow");
  await expect(rows(page)).toHaveCount(1);
  await expect(rows(page)).toContainText("Duas regras");
  await page.getByLabel("Critério de revisão").selectOption("two");
  await expect(rows(page)).toHaveCount(1);
  await page.getByLabel("Critério de revisão").selectOption("zeroDonations");
  await expect(rows(page)).toHaveCount(1);
  await page.getByLabel("Critério de revisão").selectOption("new");
  await expect(rows(page)).toContainText("Viagem avisada");
  await page.getByRole("button", { name: "Mostrar todos" }).click();
  await page.getByLabel("Filtrar revisão").selectOption("INSUFFICIENT");
  await expect(rows(page)).toContainText("Sem registro");
  await page.getByRole("button", { name: "Mostrar todos" }).click();
  await page
    .getByRole("button", { name: "Guerra atual ↕", exact: true })
    .click();
  await expect(rows(page).last()).toContainText("Sem registro");
  await page
    .getByRole("button", { name: "Guerra atual ↑", exact: true })
    .click();
  await expect(rows(page).first()).toContainText("No limite");
  await page.getByRole("button", { name: /^\d+\. Duas regras$/ }).click();
  await expect(
    page.getByRole("heading", {
      name: "Histórico oficial disponível até o recorte",
    }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Entrada, saída e reingressos" }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Histórico de doações" }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({
    path: `test-results/weekly-review-${info.project.name}.png`,
    fullPage: true,
  });
});
test("notas e decisões continuam auditáveis e filtros refletem a decisão", async ({
  page,
}) => {
  const history: Record<string, unknown>[] = [];
  await page.route("**/api/leadership/weekly-review/decisions?*", (route) =>
    route.fulfill({ json: history }),
  );
  await page.route("**/api/leadership/weekly-review/decisions/*", (route) => {
    const body = route.request().postDataJSON();
    expect(body.expectedRevision).toBe(history[0]?.revision || 0);
    const decision = {
      ...body,
      revision: history.length + 1,
      playerTag: "#BOTH",
      createdAt: report.generatedAt,
      createdBy: "lider",
    };
    history.unshift(decision);
    return route.fulfill({ json: decision });
  });
  await page.goto("/expulsoes");
  await page.getByRole("button", { name: /^\d+\. Duas regras$/ }).click();
  const form = page.getByRole("region", { name: "Decisão da revisão semanal" });
  await form.getByLabel("Decisão", { exact: true }).selectOption("WAIT");
  await form.getByLabel("Motivo da decisão").fill("Aguardando justificativa.");
  await form.getByRole("button", { name: "Salvar decisão" }).click();
  await expect(
    page.getByRole("status").filter({ hasText: "Decisão registrada" }),
  ).toBeVisible();
  await page.getByLabel("Critério de revisão").selectOption("wait");
  await expect(rows(page)).toHaveCount(1);
  await form.getByLabel("Decisão", { exact: true }).selectOption("DISMISS");
  await form
    .getByLabel("Motivo da decisão")
    .fill("Histórico revisado individualmente.");
  await form.getByRole("button", { name: "Salvar decisão" }).click();
  await expect(rows(page)).toHaveCount(0);
  await page.getByLabel("Critério de revisão").selectOption("dismissed");
  await expect(rows(page)).toHaveCount(1);
  await page.getByLabel("Critério de revisão").selectOption("reviewed");
  await expect(rows(page)).toHaveCount(1);
  await form
    .getByText("Histórico de decisões desta semana (2)", { exact: true })
    .click();
  await expect(
    form.getByText("Aguardando justificativa.", { exact: true }),
  ).toBeVisible();
  await page
    .getByText("Registrar justificativa para esta revisão", { exact: true })
    .click();
  await expect(page.getByLabel("Motivo informado pelo membro")).toBeVisible();
});
test("recortes antigos e falhas não viram zero", async ({ page }) => {
  await page.route("**/api/leadership/weekly-review?week=*", (route) =>
    route.fulfill({
      json: {
        ...report,
        phase: "CLOSED",
        membersFresh: false,
        riverFresh: false,
        collectionEnabled: false,
        members: [],
      },
    }),
  );
  await page.goto("/expulsoes");
  await page
    .getByLabel("Semana da revisão", { exact: true })
    .fill("2026-10-05");
  await expect(
    page
      .getByRole("alert")
      .filter({ hasText: "coleta automática está desativada" }),
  ).toBeVisible();
  await expect(rows(page)).toHaveCount(0);
  await expect(
    page.getByText(
      "Sem elenco coletado para este recorte. Consulte as coletas.",
    ),
  ).toBeVisible();
});
test("falha das decisões bloqueia escrita", async ({ page }) => {
  await page.route("**/api/leadership/weekly-review/decisions?*", (route) =>
    route.fulfill({ status: 403, json: { detail: "Indisponível" } }),
  );
  await page.goto("/expulsoes");
  await expect(page.getByLabel("Filtrar andamento")).toBeDisabled();
  await page.getByRole("button", { name: /^\d+\. Duas regras$/ }).click();
  await expect(
    page.getByRole("button", { name: "Salvar decisão" }),
  ).toHaveCount(0);
});
test("consulta não solicita dados privados", async ({ page }) => {
  await page.route("**/api/auth/me", (route) =>
    route.fulfill({ json: { username: "consulta", roles: ["VIEWER"] } }),
  );
  let requests = 0;
  page.on("request", (r) => {
    if (r.url().includes("/api/leadership/")) requests++;
  });
  await page.goto("/expulsoes");
  await expect(
    page.getByText(
      "Esta revisão e as justificativas são reservadas à liderança.",
    ),
  ).toBeVisible();
  expect(requests).toBe(0);
});
