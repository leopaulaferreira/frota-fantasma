import { describe, it, expect } from "vitest";
import { classifyProspect } from "./prospects";
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
    expect(classifyProspect(p, report, 2400, 5).band).toBe("near");
    p.elderCandidate = metric(true);
    p.metrics.recentAlerts = metric(1);
    expect(classifyProspect(p, report, 2400, 5).band).toBe("near");
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
