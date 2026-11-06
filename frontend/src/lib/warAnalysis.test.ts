import { describe, it, expect } from "vitest";
import {
  warLabel,
  totals,
  percent,
  range,
  recurrence,
  type WarDetail,
} from "./warAnalysis";
const wars: WarDetail[] = [0, 1, 2, 3].map((i) => ({
  war: {
    id: i,
    seasonId: 1,
    sectionIndex: 4 - i,
    publishedAt: `2026-10-${String(26 - i * 7).padStart(2, "0")}T12:00:00Z`,
    capturedAt: "2026-10-27T12:00:00Z",
    startDate: null,
    endDate: null,
    clanScore: 9000,
    fame: 1234,
    source: "DIRECT_API",
  },
  participants: [
    { playerTag: "#A", name: "Mesmo nome", contribution: 0, decksUsed: 0 },
    { playerTag: "#B", name: "Mesmo nome", contribution: 2000, decksUsed: 8 },
  ],
}));
const events = [
  { playerTag: "#A", type: "BASELINE", observedAt: "2026-01-01T00:00:00Z" },
];
describe("análise de guerras", () => {
  it("separa desconhecidos de zero e não inventa denominadores", () => {
    const t = totals([
      { playerTag: "#A", name: "A", contribution: 0, decksUsed: null },
      { playerTag: "#B", name: "B", contribution: null, decksUsed: 0 },
    ]);
    expect(t.sum).toBe(0);
    expect(t.zero).toBe(1);
    expect(t.sample).toBe(1);
    expect(t.complete).toBe(false);
    expect(percent(100, 0)).toBeNull();
    expect(totals([]).sum).toBeNull();
  });
  it("combina limites inclusivos sem converter desconhecidos", () => {
    expect(range(null, "0", "50")).toBe(false);
    expect(range(50, "0", "50")).toBe(true);
    expect(range(0, "1", "4")).toBe(false);
  });
  it("identifica por tag e protege reingressos, imports e lacunas", () => {
    expect(recurrence("#A", wars, events, "2026-11-01").twoZero).toBe(true);
    expect(recurrence("#B", wars, events, "2026-11-01").twoZero).toBe(false);
    expect(
      recurrence(
        "#A",
        wars,
        [
          ...events,
          { playerTag: "#A", type: "LEAVE", observedAt: "2026-10-20" },
          { playerTag: "#A", type: "JOIN", observedAt: "2026-10-24" },
        ],
        "2026-11-01",
      ).threeFour,
    ).toBe(false);
    expect(
      recurrence(
        "#A",
        wars.map((w) => ({
          ...w,
          war: { ...w.war, source: "ROYALEAPI_IMPORT" },
        })),
        events,
        "2026-11-01",
      ).twoBelow,
    ).toBe(false);
    expect(
      recurrence(
        "#A",
        [wars[0], { ...wars[1], participants: [] }],
        events,
        "2026-11-01",
      ).twoZero,
    ).toBe(false);
  });
  it("não confirma reincidência com participantes importados em guerra oficial",()=>{
    const mixed=wars.map(w=>({...w,participants:w.participants.map(p=>({...p,source:'ROYALEAPI_IMPORT'}))}));
    expect(recurrence('#A',mixed,events,'2026-11-01').twoZero).toBe(false);
  });

});

it("identifica guerras por datas sem inventar período da batalha", () => {
  expect(warLabel({publishedAt:"2026-11-02T01:00:00Z"}, "America/Sao_Paulo"))
    .toBe("Guerra publicada em 01/11/2026");
  expect(warLabel({publishedAt:"2026-11-02T01:00:00Z"}, "UTC"))
    .toBe("Guerra publicada em 02/11/2026");
  expect(warLabel({publishedAt:"2026-11-02T12:00:00Z",startDate:"2026-10-29T12:00:00Z",endDate:"2026-11-01T12:00:00Z"}))
    .toBe("Guerra · 29/10/2026 a 01/11/2026");
});
