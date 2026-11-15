import { expect, it } from "vitest";
import { collectionAge, warSample } from "./dataContext";
it("identifica médias parciais e completas", () => {
  expect(warSample(2)).toBe("2/4 guerras conhecidas · média parcial");
  expect(warSample(4)).toBe("4/4 guerras conhecidas");
});
it("mede idade em relação ao recorte, inclusive histórico, sem inventar coleta", () => {
  expect(collectionAge("2026-11-01T20:00:00Z", "2026-11-01T20:12:00Z")).toBe("12 min antes do recorte");
  expect(collectionAge(null, "2026-11-01T20:12:00Z")).toBe("horário não confirmado");
  expect(collectionAge("2026-11-01T21:00:00Z", "2026-11-01T20:12:00Z")).toBe("horário não confirmado");
});
