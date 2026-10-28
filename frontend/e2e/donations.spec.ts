import { test, expect } from "@playwright/test";
import { report, players, metric } from "./fixtures";
test("six saved counters survive reset, expire from the window and open evidence", async ({
  page,
}) => {
  let week = "2026-10-26";
  const member = {
    ...players[0],
    donations: [
      {
        week: "2026-09-21",
        lastCounter: metric(999),
        counterCapturedAt: "2026-09-27T23:00:00Z",
        observedDonations: metric(900),
        snapshots: 2,
        detectedResets: 0,
      },
      {
        week: "2026-10-19",
        lastCounter: metric(800),
        counterCapturedAt: "2026-10-25T23:00:00Z",
        observedDonations: metric(600),
        snapshots: 2,
        detectedResets: 0,
      },
      {
        week: "2026-10-26",
        lastCounter: metric(0),
        counterCapturedAt: "2026-10-26T10:00:00Z",
        observedDonations: metric(null),
        snapshots: 1,
        detectedResets: 1,
      },
    ],
  };
  await page.route("**/api/**", (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/auth/me")
      return route.fulfill({
        json: { username: "consulta", roles: ["VIEWER"] },
      });
    if (path === "/api/analytics")
      return route.fulfill({ json: { ...report, week, players: [member] } });
    return route.fulfill({ status: 404, json: { detail: "Sem registro" } });
  });
  await page.goto("/membros");
  await expect(
    page.locator(".page-heading").getByRole("link", { name: /doações/ }),
  ).toHaveCount(0);
  await expect(
    page
      .getByLabel("Ordenar membros")
      .locator('option[value="weeklyDonations"]'),
  ).toHaveText("Doações");
  await page.getByLabel("Ordenar membros").selectOption("weeklyDonations");
  await expect(page).toHaveURL(/\/doacoes/);
  await expect(
    page.getByRole("heading", { name: "Doações", exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("columnheader")).toHaveCount(3);
  await page.getByLabel("Filtrar semana de doações").selectOption("2026-10-19");
  await expect(page).toHaveURL(/week=2026-10-19/);
  await expect(page.locator("tbody")).toContainText("800");
  await expect(page.locator("tbody")).not.toContainText("999");
  await page.reload();
  await expect(page.getByLabel("Filtrar semana de doações")).toHaveValue(
    "2026-10-19",
  );
  await page.getByLabel("Filtrar semana de doações").selectOption("all");
  await expect(page.getByRole("columnheader")).toHaveCount(8);
  await expect(
    page.getByRole("columnheader", { name: "21/09/2026" }),
  ).toBeVisible();
  const row = page.locator("tbody tr").first();
  await expect(row).toContainText("800");
  await expect(row.locator("td").last()).toContainText("1.500");
  await row
    .getByRole("button", { name: /Contador salvo · 19\/10\/2026/ })
    .click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.getByRole("button", { name: "Fechar explicação" }).click();
  await page
    .getByLabel("Valores do histórico de doações")
    .selectOption("observed");
  await expect(row).toContainText("600");
  week = "2026-11-02";
  await page.reload();
  await expect(
    page.getByRole("columnheader", { name: "21/09/2026" }),
  ).toHaveCount(0);
  await expect(row.locator("td").last()).toContainText("600");
  await expect(row).not.toContainText("999");
  await expect(row).toContainText("Sem coleta");
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
});
