import { describe, it, expect } from "vitest";
import { classifyProspect, compareProspects } from "./prospects";
import { players, report, metric } from "../../e2e/fixtures";
function candidate(average = 2500, donations = 200) {
  const p = structuredClone(players[0]);
  p.metrics.recentAverage = metric(average);
  p.metrics.weeklyDonations = metric(donations, { incompletePeriod: true });
  p.metrics.standardDeckTargetPercent = metric(100);
  p.metrics.recentAlerts = metric(0);
  return p;
}
describe("sinais de candidatura", () => {
  it("verde exige pontuação e todos os critérios, inclusive alertas e regras atuais", () => {
    expect(classifyProspect(candidate(), report, 2400, 5).band).toBe("strong");
    const p = candidate();
    p.elderCandidate = metric(false);
    expect(classifyProspect(p, report, 2400, 5).band).toBe("blocked");
    p.elderCandidate = metric(true);
    p.metrics.recentAlerts = metric(1);
    expect(classifyProspect(p, report, 2400, 5).band).toBe("blocked");
  });
  it("amarelo mantém 2350 e 2320, ou pontuação alta com doações abaixo", () => {
    for (const average of [2350, 2320, 2280])
      expect(classifyProspect(candidate(average), report, 2400, 5).band).toBe(
        "near",
      );
    expect(classifyProspect(candidate(2600, 40), report, 2400, 5).band).toBe(
      "near",
    );
    expect(classifyProspect(candidate(2279), report, 2400, 5).band).toBe(
      "developing",
    );
    expect(classifyProspect(candidate(2320), report, 2400, 2).band).toBe(
      "developing",
    );
  });
  it("cinza diferencia informação ausente de zero real e amostra pequena", () => {
    const p = candidate();
    p.metrics.weeklyDonations = metric(null);
    expect(classifyProspect(p, report, 2400, 5).band).toBe("unknown");
    p.metrics.weeklyDonations = metric(0);
    expect(classifyProspect(p, report, 2400, 5).band).toBe("near");
    p.metrics.recentAverage = metric(3000, { warsConsidered: 1 });
    expect(classifyProspect(p, report, 2400, 5).band).toBe("unknown");
    expect(classifyProspect(candidate(), report, NaN, 5).band).toBe("unknown");
  });
});

describe("triagem multicritério para liderança", () => {
  it("não aproxima membro com três pendências mesmo com média alta", () => {
    const p = candidate(3200, 10);
    p.metrics.observedTenureDays = metric(2);
    p.metrics.standardDeckTargetPercent = metric(50);
    const result = classifyProspect(p, report, 2400, 5);
    expect(result.band).toBe("developing");
    expect(result.pending).toBe(3);
    expect(result.criteria.find((c) => c.key === "tenure")?.state).toBe(
      "pending",
    );
  });
  it("não deixa alerta conhecido desaparecer atrás de dado ausente", () => {
    const p = candidate();
    p.metrics.recentAlerts = metric(2);
    p.metrics.weeklyDonations = metric(null);
    const result = classifyProspect(p, report, 2400, 5);
    expect(result.band).toBe("blocked");
    expect(result.unknown).toBe(1);
    expect(result.nextAction).toContain("avaliações com alerta");
  });
  it("respeita o mínimo salvo de duas guerras em uma janela ainda com duas guerras", () => {
    const p = candidate();
    p.wars = p.wars.slice(0, 2);
    p.metrics.recentAverage = metric(2500, { warsConsidered: 2 });
    const r = structuredClone(report);
    r.recommendationRules.settings.analytics.minimumWars = 2;
    expect(classifyProspect(p, r, 2400, 5).band).toBe("strong");
    p.wars.push({ ...p.wars[0], warId: 3, points: metric(null) });
    expect(classifyProspect(p, r, 2400, 5).band).toBe("unknown");
  });
  it("simulação não reduz a meta oficial e dados antigos não autorizam promoção", () => {
    const p = candidate(1500);
    expect(classifyProspect(p, report, 1000, 5).band).toBe("developing");
    p.metrics.recentAverage = metric(3000);
    p.metrics.trophies = metric(9000, { incompletePeriod: true });
    expect(classifyProspect(p, report, 2400, 5).band).toBe("unknown");
    p.metrics.trophies = metric(9000);
    p.metrics.lastKnownActivity = metric(null);
    expect(classifyProspect(p, report, 2400, 5).band).toBe("unknown");
  });
  it("distingue zero, falta de participação e dados desconhecidos", () => {
    const p = candidate();
    p.wars[0].points = metric(0);
    p.wars[0].decksUsed = metric(0);
    expect(
      classifyProspect(p, report, 2400, 5).criteria.find(
        (c) => c.key === "participation",
      )?.state,
    ).toBe("pending");
    p.wars[0].points = metric(null);
    expect(
      classifyProspect(p, report, 2400, 5).criteria.find(
        (c) => c.key === "participation",
      )?.state,
    ).toBe("unknown");
  });
  it("ordena pronto antes de média maior com alertas; permite ordenar por métrica", () => {
    const ready = candidate(2500);
    ready.tag = "#READY";
    const blocked = candidate(3200);
    blocked.tag = "#BLOCKED";
    blocked.metrics.recentAlerts = metric(1);
    const rows = [blocked, ready].map((p) => ({
      p,
      assessment: classifyProspect(p, report, 2400, 5),
    }));
    expect([...rows].sort((a, b) => compareProspects(a, b))[0].p.tag).toBe(
      "#READY",
    );
    expect(
      [...rows].sort((a, b) => compareProspects(a, b, "recentAverage"))[0].p
        .tag,
    ).toBe("#BLOCKED");
    const missing = candidate();
    missing.metrics.recentAverage = metric(null);
    const row = {
      p: missing,
      assessment: classifyProspect(missing, report, 2400, 5),
    };
    expect(compareProspects(row, row, "recentAverage")).toBe(0);
  });
});

it("deixa evidências insuficientes depois dos membros que podem ser revisados com dados conhecidos", () => {
  const known = candidate(2800); known.metrics.recentAlerts = metric(1);
  const unknown = candidate(3000); unknown.metrics.recentAverage = metric(null);
  const rows = [unknown,known].map(p => ({p,assessment:classifyProspect(p,report,2400,5)}));
  expect(rows.sort((a,b) => compareProspects(a,b))[0].p).toBe(known);
});
