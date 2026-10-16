import { describe, expect, it } from "vitest";
import { orderedProspects } from "./ElderProspects";
import type { Player } from "../lib/api";
const player = (
  tag: string,
  average: number | null,
  role = "member",
  currentMember = true,
) =>
  ({
    tag,
    role,
    currentMember,
    metrics: { recentAverage: { value: average } },
  }) as unknown as Player;
describe("lista de possíveis candidatos", () => {
  it("mantém membros abaixo do objetivo, zeros e dados ausentes; exclui cargos e saídas", () => {
    const players = [
      player("#B", 2350),
      player("#C", null),
      player("#A", 2500),
      player("#D", 0),
      player("#E", 3000, "elder"),
      player("#F", 3200, "member", false),
    ];
    expect(orderedProspects(players).map((p) => p.tag)).toEqual([
      "#A",
      "#B",
      "#D",
      "#C",
    ]);
    expect(players[0].tag).toBe("#B");
  });
  it("desempata de forma estável sem esconder médias com pouca amostra", () => {
    expect(
      orderedProspects([player("#B", 2400), player("#A", 2400)]).map(
        (p) => p.tag,
      ),
    ).toEqual(["#A", "#B"]);
  });
});
