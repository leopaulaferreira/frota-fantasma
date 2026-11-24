import { describe, it, expect } from "vitest";
import { evolutionSeries } from "./evolution";
import type { Metric } from "./api";

describe("weekly evolution", () => {
  it("preserves calendar gaps and real zeroes, excluding unavailable values", () => {
    const series = evolutionSeries({ week: "2026-01-12", evolution: [
      { week: "2025-12-29", metrics: { points: { value: 0, quality: "AVAILABLE" } as Metric } },
      { week: "2026-01-05", metrics: { points: { value: 42, quality: "UNAVAILABLE" } as Metric } },
      { week: "2026-01-12", metrics: { points: { value: 120, quality: "INCOMPLETE" } as Metric } },
    ] }, "points");
    expect(series).toHaveLength(8);
    expect(series.slice(-4)).toEqual([
      { week: "2025-12-22", value: null, partial: false },
      { week: "2025-12-29", value: 0, partial: false },
      { week: "2026-01-05", value: null, partial: false },
      { week: "2026-01-12", value: 120, partial: true },
    ]);
  });
});
