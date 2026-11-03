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
  if (!Number.isFinite(at) || !Number.isFinite(boundary)) return false;
  const e = events
    .filter(
      (e) =>
        e.playerTag === tag &&
        Date.parse(e.observedAt) <= at &&
        ["JOIN", "LEAVE", "BASELINE"].includes(e.type),
    )
    .sort((a, b) => Date.parse(a.observedAt) - Date.parse(b.observedAt))
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
    w.war.source !== "ROYALEAPI_IMPORT" && w.participants.find(p=>p.playerTag===tag)?.source !== "ROYALEAPI_IMPORT" && eligible(tag, w.war, events, at);
  const row = (w: WarDetail) => w.participants.find((p) => p.playerTag === tag);
  const consecutive =
    wars.length >= 2 &&
    new Date(wars[0].war.publishedAt).getTime() -
      new Date(wars[1].war.publishedAt).getTime() <=
      8 * 86400000;
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
