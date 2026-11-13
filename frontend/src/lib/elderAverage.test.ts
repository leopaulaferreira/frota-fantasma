import { describe, expect, it } from "vitest";
import type { Player } from "./api";
import { elderAverage } from "./elderAverage";
const player = (...values: (number | null)[]) =>
  ({
    wars: values.map((value, warId) => ({ warId, points: { value } })),
  }) as unknown as Player;
describe("média para Ancião", () => {
  it("inclui o limite de 2400 e usa apenas quatro guerras", () => {
    expect(elderAverage(player(2000, 2800, 2400, 2400, 0))).toMatchObject({
      average: 2400,
      status: "meets",
    });
    expect(elderAverage(player(2399, 2400, 2400, 2400)).status).toBe("below");
  });
  it("não substitui lacunas por guerras antigas nem aprova amostras parciais", () => {
    for (const values of [
      [3200, 3200, 3200],
      [3200, null, 3200, 3200, 3200],
      [3200, NaN, 3200, 3200],
      [3200, -1, 3200, 3200],
    ])
      expect(elderAverage(player(...values))).toMatchObject({
        average: null,
        status: "unknown",
      });
  });
  it("inclui zero explícito e não exige 2400 em cada guerra", () => {
    expect(elderAverage(player(0, 3200, 3200, 3200))).toMatchObject({
      average: 2400,
      status: "meets",
    });
  });
});
