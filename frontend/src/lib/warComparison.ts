import { api, type Page } from "./api";
import {
  eligible,
  known,
  recurrence,
  type WarSummary,
  type WarDetail,
  type WarEvent,
} from "./warAnalysis";

export async function allPages<T>(path: string): Promise<T[]> {
  const rows: T[] = [];
  for (let page = 0; ; page++) {
    const data = await api<Page<T>>(`${path}?page=${page}&size=100`);
    rows.push(...data.items);
    if (!data.items.length || rows.length >= data.totalElements) return rows;
  }
}
export function closedArchive(items: WarSummary[], at: string) {
  const unique = new Map<string, WarSummary>();
  for (const w of items) {
    if (
      !(Date.parse(w.publishedAt) <= Date.parse(at)) ||
      (w.endDate && !(Date.parse(w.endDate) <= Date.parse(at)))
    )
      continue;
    const key = w.seasonId + ":" + w.sectionIndex;
    const previous = unique.get(key);
    if (
      !previous ||
      (w.source === "DIRECT_API" && previous.source !== "DIRECT_API")
    )
      unique.set(key, w);
  }
  return [...unique.values()].sort(
    (a, b) =>
      Date.parse(b.publishedAt) - Date.parse(a.publishedAt) || b.id - a.id,
  );
}
export function selectWars(
  archive: WarSummary[],
  ids: string | null,
  offset: number,
) {
  if (ids !== null) {
    const selected = new Set(ids.split(",").map(Number));
    return archive.filter((w) => selected.has(w.id)).slice(0, 4);
  }
  return archive
    .filter((w) => w.source === "DIRECT_API")
    .slice(offset, offset + 4);
}
export function displayDate(value: string | null, zone: string) {
  if (!value) return "Não informado";
  return new Intl.DateTimeFormat("pt-BR", { timeZone: zone }).format(
    new Date(value),
  );
}
export function comparisonRows(
  wars: WarDetail[],
  roster: { tag: string; name: string }[],
  events: WarEvent[],
  at: string,
  archive: WarSummary[],
) {
  const members = new Map(roster.map((p) => [p.tag, p.name]));
  const people = new Map(members);
  wars.forEach((w) =>
    w.participants.forEach((p) => {
      if (!people.has(p.playerTag)) people.set(p.playerTag, p.name);
    }),
  );
  return [...people].map(([tag, name]) => {
    const current = members.has(tag);
    // Former members retain the episode immediately before their confirmed departure.
    const membership = events
      .filter(
        (e) =>
          e.playerTag === tag &&
          Date.parse(e.observedAt) <= Date.parse(at) &&
          ["JOIN", "BASELINE", "LEAVE"].includes(e.type),
      )
      .sort((a, b) => Date.parse(a.observedAt) - Date.parse(b.observedAt));
    const last = membership.at(-1);
    const anchor =
      !current && last?.type === "LEAVE"
        ? new Date(Date.parse(last.observedAt) - 1).toISOString()
        : at;
    const cells = wars.map((w) => {
      const record = w.participants.find((p) => p.playerTag === tag);
      const official =
        w.war.source === "DIRECT_API" && record?.source === "DIRECT_API";
      const required = eligible(tag, w.war, events, anchor);
      const points = known(record?.contribution),
        decks = known(record?.decksUsed);
      const valid = official && required && points !== null;
      const beforeEpisode =
        last?.type === "JOIN" &&
        Date.parse(last.observedAt) >
          Date.parse(w.war.endDate || w.war.publishedAt);
      const reason = beforeEpisode
        ? "Anterior ao ingresso/reingresso observado"
        : !required
          ? "Vínculo integral não comprovado · novato ou outro episódio"
          : !record
            ? "Pontuação não coletada"
            : !official
              ? "Registro importado / parcial"
              : points === null
                ? "Pontuação não coletada"
                : points >= 1600
                  ? "Meta cumprida"
                  : "Abaixo da meta";
      return {
        war: w.war,
        record,
        points,
        decks,
        valid,
        required,
        official,
        reason,
      };
    });
    const scores = cells.filter((c) => c.valid).map((c) => c.points!);
    const fullFour =
      cells.length === 4 && cells.every((c) => c.valid && c.decks === 16);
    const insufficient =
      cells.length < 4 || cells.some((c) => !c.valid || c.decks === null);
    const adjacent =
      wars.length >= 2 &&
      Date.parse(wars[0].war.publishedAt) -
        Date.parse(wars[1].war.publishedAt) <=
        8 * 86400000 &&
      archive.findIndex((w) => w.id === wars[1].war.id) ===
        archive.findIndex((w) => w.id === wars[0].war.id) + 1;
    const twoBelow =
      adjacent &&
      cells.slice(0, 2).every((c) => c.valid) &&
      recurrence(tag, wars, events, anchor).twoBelow;
    const delta =
      adjacent && cells[0]?.valid && cells[1]?.valid
        ? cells[0].points! - cells[1].points!
        : null;
    const total = scores.length ? scores.reduce((a, b) => a + b, 0) : null;
    return {
      tag,
      name,
      current,
      cells,
      fullFour,
      insufficient,
      twoBelow,
      delta,
      sample: scores.length,
      total,
      average: total === null ? null : total / scores.length,
      latest: cells[0]?.valid ? cells[0].points : null,
      situation: twoBelow
        ? "Revisão · duas abaixo da meta"
        : insufficient
          ? "Dados insuficientes"
          : cells[0]?.valid && cells[0].points! < 1600
            ? "Acompanhar"
            : "Bom desempenho",
    };
  });
}
export type ComparisonRow = ReturnType<typeof comparisonRows>[number];
export function filterRows(rows: ComparisonRow[], params: URLSearchParams) {
  const rule = params.get("rule"),
    performance = params.get("performance");
  return rows
    .filter((p) => {
      if (!p.current && params.get("former") !== "1") return false;
      if (
        !`${p.name} ${p.tag}`
          .toLocaleLowerCase()
          .includes((params.get("q") || "").toLocaleLowerCase())
      )
        return false;
      const last = p.cells[0];
      if (rule === "target" && !(p.latest !== null && p.latest >= 1600))
        return false;
      if (rule === "below" && !(p.latest !== null && p.latest < 1600))
        return false;
      if (rule === "repeat" && !p.twoBelow) return false;
      if (
        rule === "zero" &&
        !(last?.official && last.required && last.decks === 0)
      )
        return false;
      if (
        rule === "decks" &&
        !(
          last?.official &&
          last.required &&
          last.decks !== null &&
          last.decks < 16
        )
      )
        return false;
      if (rule === "full" && !p.fullFour) return false;
      if (rule === "missing" && !p.insufficient) return false;
      if (performance === "high" && !(p.average !== null && p.average >= 2400))
        return false;
      if (performance === "low" && !(p.average !== null && p.average < 1600))
        return false;
      return true;
    })
    .sort((a, b) => {
      const sort =
        performance === "asc"
          ? "latestAsc"
          : performance === "desc"
            ? "latestDesc"
            : params.get("sort") || "averageDesc";
      if (sort === "name")
        return (
          a.name.localeCompare(b.name, "pt-BR") || a.tag.localeCompare(b.tag)
        );
      const key = sort.startsWith("latest")
        ? "latest"
        : sort.startsWith("delta")
          ? "delta"
          : "average";
      const x = a[key],
        y = b[key];
      if (x === null || y === null)
        return x === y ? a.tag.localeCompare(b.tag) : x === null ? 1 : -1;
      return (
        (x - y) * (sort.endsWith("Asc") ? 1 : -1) || a.tag.localeCompare(b.tag)
      );
    });
}
