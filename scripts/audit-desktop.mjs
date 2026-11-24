/** Read-only desktop audit of the local application; credentials never enter artifacts. */
import { readFile, writeFile, mkdir } from "node:fs/promises";
import { createRequire } from "node:module";
const require = createRequire(
  new URL("../frontend/package.json", import.meta.url),
);
const { chromium, expect } = require("@playwright/test");
const root = new URL("../", import.meta.url);
const values = Object.fromEntries(
  (await readFile(new URL(".env", root), "utf8"))
    .split(/\r?\n/)
    .filter((line) => line.includes("=") && !line.trimStart().startsWith("#"))
    .map((line) => {
      const i = line.indexOf("=");
      return [
        line.slice(0, i).trim(),
        line
          .slice(i + 1)
          .trim()
          .replace(/^(['"])(.*)\1$/, "$2"),
      ];
    }),
);
const baseURL =
  process.env.FROTA_URL || `http://127.0.0.1:${values.FRONTEND_PORT || 5173}`;
if (!["127.0.0.1", "localhost", "[::1]"].includes(new URL(baseURL).hostname))
  throw new Error("This audit targets a local application only.");
const browser = await chromium.launch({
  executablePath: process.env.CHROME_PATH || "/usr/bin/google-chrome",
  args: ["--no-sandbox"],
});
const context = await browser.newContext({
  baseURL,
  viewport: { width: 1440, height: 1000 },
});
const page = await context.newPage();
const runtimeErrors = [];
page.on("pageerror", (error) => runtimeErrors.push(error.message));
const results = [];
try {
  await page.goto("/");
  await page
    .getByLabel("Usuário", { exact: true })
    .fill(values.AUTH_ADMIN_USERNAME || "lider");
  await page
    .getByLabel("Senha", { exact: true })
    .fill(values.AUTH_ADMIN_PASSWORD);
  await page.getByRole("button", { name: "Entrar na Frota" }).click();
  await expect(
    page.getByRole("button", { name: "Sair", exact: true }),
  ).toBeVisible({ timeout: 30000 });
  const response = await context.request.get("/api/analytics");
  if (!response.ok()) throw new Error(`Analytics HTTP ${response.status()}`);
  const report = await response.json();
  const mismatches = [];
  const equal = (a, b) =>
    a === b ||
    (typeof a === "number" && typeof b === "number" && Math.abs(a - b) < 1e-8);
  for (const p of report.players) {
    for (const [key, limit] of [
      ["recentAverage", 4],
      ["historicalAverage", undefined],
    ]) {
      const points = p.wars
        .slice(0, limit)
        .map((w) => w.points.value)
        .filter((v) => typeof v === "number" && v >= 0);
      const expected = points.length
        ? points.reduce((a, b) => a + b, 0) / points.length
        : null;
      if (!equal(p.metrics[key].value, expected)) mismatches.push(key);
    }
    const decks = p.wars
      .slice(0, 4)
      .map((w) => w.decksUsed.value)
      .filter((v) => typeof v === "number" && v >= 0);
    const expected =
      decks.length && !decks.some((v) => v > 16)
        ? (decks.reduce((a, b) => a + b, 0) * 100) / (16 * decks.length)
        : null;
    if (!equal(p.metrics.standardDeckTargetPercent.value, expected))
      mismatches.push("standardDeckTargetPercent");
    if (p.metrics.deckUtilizationPercent.value !== null)
      mismatches.push("deckUtilizationPercent");
  }
  const routes = [
    ["overview", "/"],
    ["members", "/membros"],
    ["donations", "/doacoes"],
    ["wars", "/guerras"],
    ["comparison", "/comparativo"],
    ["recommendations", "/recomendacoes"],
    ["followup", "/recomendacoes?tipo=acompanhamento"],
    ["evaluations", "/recomendacoes?tipo=avaliacoes"],
    ["awards", "/premiacoes"],
    ["review", "/expulsoes"],
    ["rules", "/regras"],
    ["operations", "/operacoes"],
  ];
  const players = report.players.filter((p) => p.currentMember).slice(0, 2);
  if (players.length) {
    routes.push(["profile", `/membros/${encodeURIComponent(players[0].tag)}`]);
    routes.push([
      "compare",
      `/comparar?${new URLSearchParams({ tags: players.map((p) => p.tag).join(",") })}`,
    ]);
  }
  const evaluations = await context.request.get(
    "/api/recommendations/evaluations",
  );
  if (evaluations.ok()) {
    const first = (await evaluations.json()).items?.[0];
    if (first) routes.push(["evaluation", `/recomendacoes/${first.id}`]);
  }
  await mkdir(new URL("docs/screenshots/", root), { recursive: true });
  for (const [name, route] of routes) {
    await page.goto(route);
    await expect(page.locator("main h1")).toBeVisible({ timeout: 30000 });
    await expect(page.locator("main")).not.toContainText(
      "Carregando registros…",
      { timeout: 30000 },
    );
    await expect(page.locator("main").getByRole("alert")).toHaveCount(0, {
      timeout: 30000,
    });
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth > innerWidth,
    );
    await page.screenshot({
      path: new URL(`docs/screenshots/${name}-live.png`, root).pathname,
      fullPage: true,
    });
    results.push({ page: name, horizontalOverflow: overflow });
  }
  const output = {
    validatedAt: new Date().toISOString(),
    viewport: { width: 1440, height: 1000 },
    pages: results,
    playersChecked: report.players.length,
    metricMismatches: mismatches,
    runtimeErrors,
    limitations: [
      "Read-only navigation; no rules, decisions, awards or imports were submitted.",
      "Current local captures; no new collection or comparison with the remote Supercell API was requested.",
    ],
  };
  await writeFile(
    new URL("docs/DESKTOP-VALIDATION.json", root),
    JSON.stringify(output, null, 2) + "\n",
  );
  console.log(
    JSON.stringify({
      pages: results.length,
      players: report.players.length,
      metricMismatches: mismatches.length,
      runtimeErrors: runtimeErrors.length,
      overflows: results.filter((r) => r.horizontalOverflow).length,
    }),
  );
  if (
    mismatches.length ||
    runtimeErrors.length ||
    results.some((r) => r.horizontalOverflow)
  )
    process.exitCode = 1;
} finally {
  await browser.close();
}
