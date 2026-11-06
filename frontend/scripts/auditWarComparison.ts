import { readFileSync } from "node:fs";
import assert from "node:assert/strict";
import {
  closedArchive,
  selectWars,
  comparisonRows,
  filterRows,
} from "../src/lib/warComparison";
if (!process.argv[2])
  throw new Error(
    "Informe o JSON exportado pela consulta de auditoria somente leitura.",
  );
const d = JSON.parse(readFileSync(process.argv[2], "utf8"));
const archive = closedArchive(
  d.details.map((x) => x.war),
  d.at,
);
const selected = selectWars(archive, null, 0);
const details = selected.map((w) => d.details.find((x) => x.war.id === w.id));
const rows = comparisonRows(details, d.roster, d.events, d.at, archive);
const current = rows.filter((p) => p.current);
let windows = 0,
  checked = 0,
  filterChecks = 0;
const selections = [
  ...archive.map((w) => [w]),
  ...archive.slice(0, -1).map((w, i) => archive.slice(i, i + 4)),
];
for (const selected of selections) {
  const details = selected.map((w) => d.details.find((x) => x.war.id === w.id));
  const result = comparisonRows(details, d.roster, d.events, d.at, archive);
  windows++;
  for (const p of result) {
    const raw = details
      .filter((w) => w.war.source === "DIRECT_API")
      .flatMap((w) =>
        w.participants
          .filter(
            (r) =>
              r.playerTag === p.tag &&
              r.source === "DIRECT_API" &&
              typeof r.contribution === "number" &&
              r.contribution >= 0,
          )
          .map((r) => r.contribution),
      );
    assert.equal(p.sample, raw.length);
    assert.equal(p.total, raw.length ? raw.reduce((a, b) => a + b, 0) : null);
    assert.equal(
      p.average,
      raw.length ? raw.reduce((a, b) => a + b, 0) / raw.length : null,
    );
    assert.equal(
      p.fullFour,
      p.cells.length === 4 &&
        p.cells.every((c) => c.official && c.decks === 16),
    );
    if (p.twoBelow)
      assert(
        p.cells
          .slice(0, 2)
          .every(
            (c) =>
              c.required && c.official && c.points !== null && c.points < 1600,
          ),
      );
    if (!p.current)
      assert(
        details.some((w) => w.participants.some((r) => r.playerTag === p.tag)),
      );
    checked++;
  }
  for (const rule of [
    "",
    "target",
    "below",
    "repeat",
    "zero",
    "decks",
    "full",
    "missing",
  ])
    for (const performance of ["", "asc", "desc", "high", "low"])
      for (const former of ["", "1"]) {
        const params = new URLSearchParams({ rule, performance, former });
        const filtered = filterRows(result, params);
        assert.equal(new Set(filtered.map((p) => p.tag)).size, filtered.length);
        if (!former) assert(filtered.every((p) => p.current));
        if (rule === "zero")
          assert(
            filtered.every(
              (p) => p.cells[0]?.official && p.cells[0]?.decks === 0,
            ),
          );
        if (rule === "repeat") assert(filtered.every((p) => p.twoBelow));
        if (rule === "target")
          assert(filtered.every((p) => p.latest !== null && p.latest >= 1600));
        if (rule === "below")
          assert(filtered.every((p) => p.latest !== null && p.latest < 1600));
        if (rule === "full") assert(filtered.every((p) => p.fullFour));
        if (rule === "missing") assert(filtered.every((p) => p.insufficient));
        if (rule === "decks")
          assert(
            filtered.every(
              (p) =>
                p.cells[0]?.official &&
                p.cells[0]?.decks !== null &&
                p.cells[0]?.decks < 16,
            ),
          );
        if (performance === "high")
          assert(
            filtered.every((p) => p.average !== null && p.average >= 2400),
          );
        if (performance === "low")
          assert(filtered.every((p) => p.average !== null && p.average < 1600));
        filterChecks++;
      }
}
const affected = current.filter((p) =>
  p.cells.some((c) => c.valid && !c.required),
);
const oldNull = current.filter(
  (p) => p.sample > 0 && !p.cells.some((c) => c.valid && c.required),
);
console.log(
  JSON.stringify(
    {
      at: d.at,
      rosterCapturedAt: d.rosterCapturedAt,
      officialWars: selected.length,
      currentMembers: current.length,
      affectedMembers: affected.length,
      discardedOfficialResults: affected.reduce(
        (n, p) => n + p.cells.filter((c) => c.valid && !c.required).length,
        0,
      ),
      membersPreviouslyWithoutAverage: oldNull.length,

      windows,
      checkedPlayerWindows: checked,
      filterCombinations: filterChecks,
      result: "passed",
    },
    null,
    2,
  ),
);
