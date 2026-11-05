import { describe, it, expect } from "vitest";
import {
  closedArchive,
  selectWars,
  comparisonRows,
  filterRows,
  displayDate,
} from "./warComparison";
import type { WarDetail, WarEvent } from "./warAnalysis";

const at = "2026-11-05T12:00:00Z";
const wars: WarDetail[] = Array.from({ length: 10 }, (_, i) => ({
  war: {
    id: i + 1,
    seasonId: 137 - Math.floor(i / 4),
    sectionIndex: 3 - (i % 4),
    publishedAt: new Date(
      Date.parse("2026-11-02T10:00:00Z") - i * 7 * 86400000,
    ).toISOString(),
    capturedAt: at,
    startDate: null,
    endDate: null,
    source: "DIRECT_API",
    fame: 30000,
    clanScore: 2000,
  },
  participants: [
    {
      playerTag: "#A",
      name: "Nome antigo",
      contribution: 0,
      decksUsed: 0,
      source: "DIRECT_API",
    },
    {
      playerTag: "#B",
      name: "Mesmo nome",
      contribution: 2400,
      decksUsed: 16,
      source: "DIRECT_API",
    },
    {
      playerTag: "#EX",
      name: "Ex-membro",
      contribution: 1000,
      decksUsed: 4,
      source: "DIRECT_API",
    },
  ],
}));
const roster = [
  { tag: "#A", name: "Mesmo nome" },
  { tag: "#B", name: "Mesmo nome" },
  { tag: "#NEW", name: "Novato" },
];
const events: WarEvent[] = ["#A", "#B", "#EX"].map((playerTag) => ({
  playerTag,
  type: "BASELINE",
  observedAt: "2026-01-01T00:00:00Z",
}));
events.push({
  playerTag: "#EX",
  type: "LEAVE",
  observedAt: "2026-11-03T00:00:00Z",
});
const archive = wars.map((w) => w.war);
const rows = (w = wars.slice(0, 4), e = events) =>
  comparisonRows(w, roster, e, at, archive);
describe("comparativo", () => {
  it("seleciona quatro oficiais encerradas, deduplica e exclui futuro", () => {
    const all = closedArchive(
      [
        ...archive,
        { ...archive[0], id: 99, source: "ROYALEAPI_IMPORT" },
        {
          ...archive[0],
          id: 100,
          seasonId: 999,
          endDate: "2026-11-10T00:00:00Z",
        },
        {
          ...archive[0],
          id: 101,
          seasonId: 998,
          publishedAt: "2026-11-10T00:00:00Z",
        },
      ],
      at,
    );
    expect(selectWars(all, null, 0).map((w) => w.id)).toEqual([1, 2, 3, 4]);
    expect(selectWars(all, null, 4).map((w) => w.id)).toEqual([5, 6, 7, 8]);
    expect(selectWars(all, null, 8).map((w) => w.id)).toEqual([9, 10]);
    expect(selectWars(all, "4,2,2,999", 0).map((w) => w.id)).toEqual([2, 4]);
    expect(selectWars(all, "", 0)).toEqual([]);
  });
  it("usa tag e elenco atual, inclui somente ex participantes selecionados", () => {
    expect(filterRows(rows(), new URLSearchParams()).map((p) => p.tag)).toEqual(
      ["#B", "#A", "#NEW"],
    );
    const all = filterRows(rows(), new URLSearchParams("former=1"));
    expect(all.map((p) => p.tag)).toContain("#EX");
    expect(all.find((p) => p.tag === "#A")?.name).toBe("Mesmo nome");
    expect(
      rows(
        wars
          .slice(0, 4)
          .map((w) => ({
            ...w,
            participants: w.participants.filter((p) => p.playerTag !== "#EX"),
          })),
      ).map((p) => p.tag),
    ).not.toContain("#EX");
  });
  it("combina busca, reincidência, vínculo e baixo desempenho", () => {
    const selected = filterRows(
      rows(),
      new URLSearchParams("q=%23A&rule=repeat&performance=low"),
    );
    expect(selected.map((p) => p.tag)).toEqual(["#A"]);
    expect(selected[0].average).toBe(0);
    expect(
      filterRows(rows(), new URLSearchParams("rule=below")).map((p) => p.tag),
    ).toEqual(["#A"]);
    expect(
      filterRows(rows(), new URLSearchParams("rule=full&performance=high")).map(
        (p) => p.tag,
      ),
    ).toEqual(["#B"]);
  });
  it("não transforma ausência em zero nem calcula média com guerras desconhecidas", () => {
    const changed = structuredClone(wars.slice(0, 4));
    changed[1].participants = [];
    const a = rows(changed).find((p) => p.tag === "#A")!;
    expect(a.sample).toBe(3);
    expect(a.average).toBe(0);
    expect(a.twoBelow).toBe(false);
    expect(a.cells[1].points).toBeNull();
    expect(a.insufficient).toBe(true);
    expect(rows().find((p) => p.tag === "#NEW")?.average).toBeNull();
  });
  it("protege novatos, reingressos e lacunas na seleção", () => {
    const rejoin = [
      ...events,
      { playerTag: "#A", type: "LEAVE", observedAt: "2026-10-29" },
      { playerTag: "#A", type: "JOIN", observedAt: "2026-11-01" },
    ];
    expect(rows(undefined, rejoin).find((p) => p.tag === "#A")?.twoBelow).toBe(
      false,
    );
    expect(rows(undefined, rejoin).find((p) => p.tag === "#A")?.sample).toBe(0);
    expect(
      rows([wars[0], wars[2], wars[3], wars[4]]).find((p) => p.tag === "#A")
        ?.twoBelow,
    ).toBe(false);
  });
  it("não confirma reincidência nem média com importações parciais", () => {
    const changed = structuredClone(wars.slice(0, 4));
    changed[0].participants[0].source = "ROYALEAPI_IMPORT";
    const a = rows(changed).find((p) => p.tag === "#A")!;
    expect(a.twoBelow).toBe(false);
    expect(a.sample).toBe(3);
    expect(a.latest).toBeNull();
  });
  it("zero pontos não comprova zero decks; quatro completas exigem quatro registros", () => {
    const changed = structuredClone(wars.slice(0, 4));
    changed[0].participants[0].decksUsed = 4;
    expect(filterRows(rows(changed), new URLSearchParams("rule=zero"))).toEqual(
      [],
    );
    expect(rows(wars.slice(0, 3)).find((p) => p.tag === "#B")?.fullFour).toBe(
      false,
    );
  });
  it("ordena desconhecidos no fim e desempata por tag", () => {
    const changed = structuredClone(wars.slice(0, 4));
    changed.forEach((w) => (w.participants[0].contribution = 2400));
    expect(
      filterRows(rows(changed), new URLSearchParams("sort=averageAsc")).map(
        (p) => p.tag,
      ),
    ).toEqual(["#A", "#B", "#NEW"]);
    expect(
      filterRows(rows(), new URLSearchParams("sort=name&q=Mesmo")).map(
        (p) => p.tag,
      ),
    ).toEqual(["#A", "#B"]);
  });
  it("exibe datas no fuso configurado e respeita limites exatos de entrada", () => {
    expect(displayDate("2026-11-02T01:00:00Z", "America/Sao_Paulo")).toBe(
      "01/11/2026",
    );
    expect(displayDate("2026-11-02T01:00:00Z", "UTC")).toBe("02/11/2026");
    const changed = structuredClone(wars.slice(0, 4));
    changed[0].war.startDate = "2026-10-29T10:00:00Z";
    const e = [
      { playerTag: "#A", type: "JOIN", observedAt: "2026-10-29T10:00:01Z" },
    ];
    expect(
      rows(changed, e).find((p) => p.tag === "#A")?.cells[0].required,
    ).toBe(false);
    e[0].observedAt = "2026-10-29T10:00:00Z";
    expect(
      rows(changed, e).find((p) => p.tag === "#A")?.cells[0].required,
    ).toBe(true);
  });
});
