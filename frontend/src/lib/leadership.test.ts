import { describe, expect, it } from "vitest";
import { leaderSignals, type LeaderNote } from "./leadership";
import type { Player, Metric } from "./api";
const metric = (value: unknown, extra = {}) =>
  ({
    value,
    reason: "Motivo com evidências",
    warsConsidered: 4,
    insufficientSample: false,
    incompletePeriod: false,
    ...extra,
  }) as Metric;
const player = () =>
  ({
    tag: "#P01",
    metrics: { recentAverage: metric(2350), recentTrendPoints: metric(100) },
    operationalStatus: metric("MANTER"),
    elderCandidate: metric(false),
  }) as unknown as Player;
describe("prioridades do líder", () => {
  it("reconhece evolução e proximidade sem excluir abaixo do objetivo", () => {
    expect(leaderSignals(player(), [], 2400).map((s) => s.label)).toEqual([
      "Reconhecer evolução",
      "Perto do objetivo",
    ]);
  });
  it("não conclui evolução ou proximidade com amostra limitada", () => {
    const p = player();
    p.metrics.recentAverage = metric(2350, { warsConsidered: 2 });
    p.metrics.recentTrendPoints = metric(100, { insufficientSample: true });
    expect(leaderSignals(p, [], 2400)).toEqual([]);
  });
  it("prioriza retomada vigente, preserva alertas e ignora notas encerradas e de outros membros", () => {
    const p = player();
    p.operationalStatus = metric("REVISAR");
    const notes = [
      { playerTag: p.tag, status: "ACTIVE", followUpDue: true },
    ] as LeaderNote[];
    expect(
      leaderSignals(p, notes, 2400)
        .slice(0, 2)
        .map((s) => s.label),
    ).toEqual(["Retomar acompanhamento", "Revisar contexto"]);
    notes[0].status = "RESOLVED";
    expect(leaderSignals(p, notes, 2400)[0].label).toBe("Revisar contexto");
    notes[0].status = "ACTIVE";
    notes[0].playerTag = "#OTHER";
    expect(leaderSignals(p, notes, 2400)[0].label).toBe("Revisar contexto");
  });
});
