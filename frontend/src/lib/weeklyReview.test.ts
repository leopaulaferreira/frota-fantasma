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

import {
  twoClosedBelow,
  closedAndLiveBelow,
  type WeeklyReviewReport,
} from "./weeklyReview";
it("mostra resultados abaixo mesmo com vínculo não elegível, sem fabricar zero", () => {
  const p = member("#A", 0, 0);
  p.participation = {
    wars: [
      {
        id: 1,
        publishedAt: "2026-11-02T10:00:00Z",
        source: "DIRECT_API",
        points: 200,
        eligible: false,
      },
      {
        id: 2,
        publishedAt: "2026-10-26T10:00:00Z",
        source: "DIRECT_API",
        points: 1000,
        eligible: false,
      },
    ],
  } as WeeklyReviewMember["participation"];
  expect(twoClosedBelow(p, 1600)).toBe(true);
  p.participation!.wars[1].points = null;
  expect(twoClosedBelow(p, 1600)).toBe(false);
  p.participation!.wars[1].points = 1600;
  expect(twoClosedBelow(p, 1600)).toBe(false);
  p.participation!.wars[1].points = 0;
  p.participation!.wars[1].source = "ROYALEAPI_IMPORT";
  expect(twoClosedBelow(p, 1600)).toBe(false);
});
it("prévia usa captura fresca e publicação da semana no fuso, sem chamar parcial de encerrada", () => {
  const p = member("#A", 100, 1);
  p.participation = {
    wars: [
      {
        id: 1,
        publishedAt: "2026-11-02T10:00:00Z",
        source: "DIRECT_API",
        points: 0,
      },
    ],
  } as WeeklyReviewMember["participation"];
  const r = {
    phase: "LIVE",
    riverFresh: true,
    membersFresh: true,
    pointsTarget: 1600,
    week: "2026-11-02",
    timezone: "America/Sao_Paulo",
  } as WeeklyReviewReport;
  expect(closedAndLiveBelow(p, r)).toBe(true);
  expect(closedAndLiveBelow(p, { ...r, riverFresh: false })).toBe(false);
  expect(closedAndLiveBelow(p, { ...r, phase: "CLOSED" })).toBe(false);
  p.participation!.wars[0].publishedAt = "2026-11-02T01:00:00Z";
  expect(closedAndLiveBelow(p, r)).toBe(false);
});
