import { describe, it, expect } from "vitest";
import { rankRows, format, date, tagPath } from "./api";
describe("dados analíticos", () => {
  it("preserva zero e distingue dado desconhecido", () => {
    expect(format(0)).toBe("0");
    expect(format(null)).toBe("Dados insuficientes");
    expect(format(false)).toBe("Não");
  });
  it("empates compartilham posição e ausentes não entram no ranking", () => {
    const rows = rankRows([
      { playerTag: "#A", contribution: 0 },
      { playerTag: "#B", contribution: 3200 },
      { playerTag: "#C", contribution: null },
      { playerTag: "#D", contribution: 3200 },
    ]);
    expect(rows.map((r) => r.rank)).toEqual([1, 1, 3, null]);
    expect(rows.map((r) => r.playerTag)).toEqual(["#B", "#D", "#A", "#C"]);
  });
  it("preserva datas de semana e codifica tags em rotas", () => {
    expect(date("2026-10-12")).toBe("12/10/2026");
    expect(tagPath("#P01")).toBe("%23P01");
  });
});
