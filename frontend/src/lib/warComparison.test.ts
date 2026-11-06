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
        wars.slice(0, 4).map((w) => ({
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
    expect(rows(undefined, rejoin).find((p) => p.tag === "#A")?.sample).toBe(4);
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

it("preserva o zero oficial de Wakasa sem presumir participação exigível", () => {
  const war: WarDetail = {
    war: { ...wars[0].war, publishedAt: "2026-11-02T09:41:00Z" },
    participants: [
      {
        playerTag: "#W",
        name: "Wakasa",
        contribution: 0,
        decksUsed: 0,
        source: "DIRECT_API",
      },
    ],
  };
  const result = comparisonRows(
    [war, { ...wars[1], participants: [] }, { ...wars[2], participants: [] }],
    [{ tag: "#W", name: "Wakasa" }],
    [{ playerTag: "#W", type: "JOIN", observedAt: "2026-10-28T14:03:26Z" }],
    at,
    [war.war, wars[1].war, wars[2].war],
  )[0];
  expect(result.average).toBe(0);
  expect(result.total).toBe(0);
  expect(result.sample).toBe(1);
  expect(result.cells[0].valid).toBe(true);
  expect(result.cells[0].required).toBe(false);
  expect(result.cells[0].reason).toContain(
    "participação integral não comprovada",
  );
  expect(result.cells[1].beforeEpisode).toBe(true);
  expect(result.twoBelow).toBe(false);
  expect(result.situation).toBe("Histórico em formação · 1/4 guerras");
});

it("mantém desempenho de novatos e separa filtros de decks de faltas", () => {
  const e = [
    { playerTag: "#A", type: "JOIN", observedAt: "2026-10-30T12:00:00Z" },
  ];
  const result = rows(undefined, e);
  const a = result.find((p) => p.tag === "#A")!;
  expect(a.average).toBe(0);
  expect(a.twoBelow).toBe(false);
  expect(
    filterRows(result, new URLSearchParams("q=%23A&rule=zero")).map(
      (p) => p.tag,
    ),
  ).toEqual(["#A"]);
  expect(
    filterRows(result, new URLSearchParams("q=%23A&rule=decks")).map(
      (p) => p.tag,
    ),
  ).toEqual(["#A"]);
  expect(filterRows(result, new URLSearchParams("q=%23A&rule=repeat"))).toEqual(
    [],
  );
});

it("pontuação conhecida não depende de decks conhecidos, nem o inverso", () => {
  const changed = structuredClone(wars.slice(0, 4));
  changed[0].participants[0].decksUsed = null;
  const a = rows(changed).find((p) => p.tag === "#A")!;
  expect(a.average).toBe(0);
  expect(a.sample).toBe(4);
  expect(a.situation).toBe("Revisão · duas abaixo da meta");
  expect(
    filterRows(rows(changed), new URLSearchParams("q=%23A&rule=zero")),
  ).toEqual([]);
  changed[0].participants[0].decksUsed = 0;
  changed[0].participants[0].contribution = null;
  const b = rows(changed).find((p) => p.tag === "#A")!;
  expect(b.sample).toBe(3);
  expect(b.twoBelow).toBe(false);
  expect(b.situation).toBe("Coleta incompleta");
  expect(
    filterRows(rows(changed), new URLSearchParams("q=%23A&rule=zero")),
  ).toHaveLength(1);
});

it("não pune ex-membro que saiu durante a batalha", () => {
  const e = events.filter((e) => e.playerTag !== "#EX");
  e.push(
    { playerTag: "#EX", type: "JOIN", observedAt: "2026-01-01T00:00:00Z" },
    { playerTag: "#EX", type: "LEAVE", observedAt: "2026-10-30T12:00:00Z" },
  );
  const ex = rows(undefined, e).find((p) => p.tag === "#EX")!;
  expect(ex.cells[0].required).toBe(false);
  expect(ex.average).toBe(1000);
  expect(ex.twoBelow).toBe(false);
});

it("arquivo desconhecido, ordem invertida e guerra duplicada não comprovam sequência", () => {
  for (const selection of [
    [wars[0], wars[0]],
    [wars[1], wars[0]],
  ]) {
    expect(rows(selection).find((p) => p.tag === "#A")?.twoBelow).toBe(false);
  }
  const result = comparisonRows(wars.slice(0, 2), roster, events, at, [
    wars[1].war,
  ]);
  expect(result.find((p) => p.tag === "#A")?.twoBelow).toBe(false);
});

it("meta atingida por recém-chegado permanece um resultado conhecido", () => {
  const e = [
    { playerTag: "#B", type: "JOIN", observedAt: "2026-10-30T12:00:00Z" },
  ];
  const result = rows(wars.slice(0, 1), e);
  const b = result.find((p) => p.tag === "#B")!;
  expect(b.average).toBe(2400);
  expect(b.latest).toBe(2400);
  expect(b.cells[0].reason).toBe("Meta cumprida");
  expect(b.situation).toBe("Histórico em formação · 1/4 guerras");
  expect(
    filterRows(result, new URLSearchParams("q=%23B&rule=target")),
  ).toHaveLength(1);
});

it("16 decks oficiais nas quatro não dependem de pontuação coletada", () => {
  const changed = structuredClone(wars.slice(0, 4));
  changed.forEach((w) => (w.participants[1].contribution = null));
  const b = rows(changed).find((p) => p.tag === "#B")!;
  expect(b.average).toBeNull();
  expect(b.fullFour).toBe(true);
  expect(
    filterRows(rows(changed), new URLSearchParams("q=%23B&rule=full")),
  ).toHaveLength(1);
});
