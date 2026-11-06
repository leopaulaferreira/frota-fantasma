export interface WarSummary {
  id: number;
  seasonId: number;
  sectionIndex: number;
  publishedAt: string;
  startDate: string | null;
  endDate: string | null;
  clanScore: number | null;
  fame: number | null;
  source?: string;
  capturedAt: string;
}
export interface WarPlayer {
  playerTag: string;
  name: string;
  contribution: number | null;
  decksUsed: number | null;
  source?: string;
}
export interface WarDetail {
  war: WarSummary;
  participants: WarPlayer[];
}
export interface WarEvent {
  id?: number;
  playerTag: string;
  type: string;
  observedAt: string;
}
export const known = (n: unknown): number | null =>
  typeof n === "number" && Number.isFinite(n) && n >= 0 ? n : null;
export function totals(rows: WarPlayer[]) {
  const points = rows.map((r) => known(r.contribution)),
    decks = rows.map((r) => known(r.decksUsed));
  const values = points.filter((x): x is number => x !== null);
  return {
    sum: values.length ? values.reduce((a, b) => a + b, 0) : null,
    average: values.length
      ? values.reduce((a, b) => a + b, 0) / values.length
      : null,
    zero: decks.some((x) => x !== null)
      ? decks.filter((x) => x === 0).length
      : null,
    deckSample: decks.filter((x) => x !== null).length,
    target: values.length ? values.filter((x) => x >= 1600).length : null,
    decks: decks.some((x) => x !== null)
      ? decks.reduce<number>((a, b) => a + (b ?? 0), 0)
      : null,
    complete: rows.length > 0 && points.every((x) => x !== null),
    sample: values.length,
    total: rows.length,
  };
}
export const percent = (a: number | null, b: number | null) =>
  a === null || b === null || b === 0 ? null : ((a - b) / b) * 100;
export function eligible(
  tag: string,
  war: WarSummary,
  events: WarEvent[],
  episodeAt: string,
) {
  const at = Date.parse(episodeAt);
  const boundary = war.startDate
    ? Date.parse(war.startDate)
    : Date.parse(war.publishedAt) - 7 * 86400000;
  const end = Date.parse(war.endDate || war.publishedAt);
  if (
    !Number.isFinite(at) ||
    !Number.isFinite(boundary) ||
    !Number.isFinite(end) ||
    end < boundary
  )
    return false;
  // A departure during the battle prevents assuming full availability,
  // including when reviewing a former member's episode before departure.
  if (
    events.some(
      (e) =>
        e.playerTag === tag &&
        ["JOIN", "LEAVE"].includes(e.type) &&
        Date.parse(e.observedAt) > boundary &&
        Date.parse(e.observedAt) <= end,
    )
  )
    return false;
  const e = events
    .filter(
      (e) =>
        e.playerTag === tag &&
        Date.parse(e.observedAt) <= at &&
        ["JOIN", "LEAVE", "BASELINE"].includes(e.type),
    )
    .sort(
      (a, b) =>
        Date.parse(a.observedAt) - Date.parse(b.observedAt) ||
        (a.id ?? 0) - (b.id ?? 0),
    )
    .at(-1);
  return !!e && e.type !== "LEAVE" && Date.parse(e.observedAt) <= boundary;
}

export function recurrence(
  tag: string,
  wars: WarDetail[],
  events: WarEvent[],
  at: string,
) {
  const valid = (w: WarDetail) =>
    w.war.source === "DIRECT_API" &&
    w.participants.find((p) => p.playerTag === tag)?.source === "DIRECT_API" &&
    eligible(tag, w.war, events, at);
  const row = (w: WarDetail) => w.participants.find((p) => p.playerTag === tag);
  const gap =
    wars.length >= 2
      ? Date.parse(wars[0].war.publishedAt) -
        Date.parse(wars[1].war.publishedAt)
      : NaN;
  const consecutive =
    wars.length >= 2 &&
    wars[0].war.id !== wars[1].war.id &&
    gap > 0 &&
    gap <= 8 * 86400000;
  return {
    twoZeroPoints:
      consecutive &&
      wars
        .slice(0, 2)
        .every((w) => valid(w) && known(row(w)?.contribution) === 0),
    twoZero:
      consecutive &&
      wars.slice(0, 2).every((w) => valid(w) && known(row(w)?.decksUsed) === 0),
    twoBelow:
      consecutive &&
      wars
        .slice(0, 2)
        .every(
          (w) =>
            valid(w) &&
            known(row(w)?.contribution) !== null &&
            row(w)!.contribution! < 1600,
        ),
    threeFour:
      wars.length >= 4 &&
      wars
        .slice(0, 4)
        .filter(
          (w) =>
            valid(w) &&
            known(row(w)?.contribution) !== null &&
            row(w)!.contribution! < 1600,
        ).length >= 3,
  };
}
export function range(value: number | null, min: string, max: string) {
  return (
    value !== null &&
    (!min || (Number.isFinite(Number(min)) && value >= Number(min))) &&
    (!max || (Number.isFinite(Number(max)) && value <= Number(max)))
  );
}

// Publication is a fallback label, never an invented battle date.
export function warLabel(
  war: {
    publishedAt: string;
    startDate?: string | null;
    endDate?: string | null;
  },
  zone = "America/Sao_Paulo",
) {
  const date = (at: string) =>
    new Intl.DateTimeFormat("pt-BR", { timeZone: zone }).format(new Date(at));
  if (war.startDate && war.endDate)
    return `Guerra · ${date(war.startDate)} a ${date(war.endDate)}`;
  if (war.endDate) return `Guerra encerrada em ${date(war.endDate)}`;
  return `Guerra publicada em ${date(war.publishedAt)}`;
}
