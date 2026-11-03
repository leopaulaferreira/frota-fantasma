import { describe, it, expect } from "vitest";
import { reviewOrder, type WeeklyReviewMember } from "./weeklyReview";
const member = (tag: string, points: number | null, decksUsed: number | null) =>
  ({
    tag,
    points,
    decksUsed,
    belowPoints: points !== null && points < 1600,
    observedSince: "2026-09-01T00:00:00Z",
  }) as WeeklyReviewMember;
describe("revisão semanal", () => {
  it("prioriza zerados e depois decks crescentes abaixo da meta", () => {
    const rows = [
      member("#3", 300, 3),
      member("#unknown", null, null),
      member("#1", 500, 1),
      member("#0", 0, 0),
      member("#2", 200, 2),
    ];
    expect(rows.sort(reviewOrder).map((p) => p.tag)).toEqual([
      "#0",
      "#1",
      "#2",
      "#3",
      "#unknown",
    ]);
  });
});
