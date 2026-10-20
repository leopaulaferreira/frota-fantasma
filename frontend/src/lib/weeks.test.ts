import { describe, expect, it } from "vitest";
import { mondayOf, shiftWeek } from "./weeks";
describe("semanas de consulta", () => {
  it("normaliza domingo e segunda sem trocar a semana por fuso", () => {
    expect(mondayOf("2026-10-18")).toBe("2026-10-12");
    expect(mondayOf("2026-10-19")).toBe("2026-10-19");
    expect(mondayOf("2026-01-01")).toBe("2025-12-29");
    expect(shiftWeek("2026-01-05", -1)).toBe("2025-12-29");
  });
  it("rejeita datas inválidas e entrada vazia", () => {
    for (const value of ["", "2026-02-30", "abc", "2026-13-01"])
      expect(mondayOf(value)).toBe("");
  });
});
