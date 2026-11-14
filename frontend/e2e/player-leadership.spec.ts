import { test, expect } from "@playwright/test";
import { players, report } from "./fixtures";

for (const admin of [true, false]) {
  test(`perfil conecta acompanhamento respeitando acesso ${admin ? "lider" : "consulta"}`, async ({ page }) => {
    const privateRequests: string[] = [];
    await page.route("**/api/**", async route => {
      const url = new URL(route.request().url());
      const path = decodeURIComponent(url.pathname);
      if (path.includes("/leadership/")) privateRequests.push(path);
      let result: unknown = { items: [], totalElements: 0, page: 0, size: 20 };
      if (path === "/api/auth/me") result = { username: "teste", roles: [admin ? "ADMIN" : "VIEWER"] };
      else if (path === "/api/auth/csrf") result = { headerName: "X-CSRF-TOKEN", token: "test" };
      else if (path.startsWith("/api/analytics/players/")) result = players[0];
      else if (path === "/api/analytics") result = report;
      else if (path === "/api/leadership/weekly-review") result = { week: report.week };
      else if (path === "/api/leadership/weekly-review/decisions") result = [
        { playerTag: players[0].tag, week: report.week, revision: 1, action: "TALK", reason: "Conversar sobre participação", createdBy: "lider", createdAt: report.generatedAt },
        { playerTag: "#OUTRO", week: report.week, revision: 2, action: "DISMISS", reason: "Motivo de outro membro", createdBy: "lider", createdAt: report.generatedAt },
      ];
      await route.fulfill({ json: result });
    });
    await page.goto(`/membros/${encodeURIComponent(players[0].tag)}`);
    await expect(page.getByRole("heading", { name: players[0].name, exact: true })).toBeVisible();
    if (admin) {
      await expect(page.getByRole("link", { name: "Abrir revisão semanal deste membro →" })).toHaveAttribute("href", `/expulsoes?${new URLSearchParams({ membro: players[0].tag })}`);
      await page.getByRole("button", { name: "Notas da liderança", exact: true }).click();
      await expect(page.getByText("Conversar sobre participação", { exact: true })).toBeVisible();
      await expect(page.getByText("Motivo de outro membro")).toHaveCount(0);
      await expect(page.getByRole("link", { name: "Abrir revisão deste membro →" })).toHaveAttribute("href", `/expulsoes?${new URLSearchParams({ membro: players[0].tag, week: report.week })}`);
    } else {
      await expect(page.getByRole("button", { name: "Notas da liderança" })).toHaveCount(0);
      await expect(page.getByRole("link", { name: "Abrir revisão semanal deste membro →" })).toHaveCount(0);
      expect(privateRequests).toEqual([]);
    }
  });
}
