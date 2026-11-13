import type { Player } from "./api";

export const ELDER_AVERAGE_TARGET = 2400;

// The API supplies the clan's wars newest first, including missing player rows.
// Never skip a missing result to substitute an older war.
export function elderAverage(player: Player) {
  const wars = player.wars.slice(0, 4);
  const values = wars.map((war) => war.points.value);
  const complete =
    wars.length === 4 &&
    values.every(
      (value) =>
        typeof value === "number" && Number.isFinite(value) && value >= 0,
    );
  const average = complete
    ? (values as number[]).reduce((sum, value) => sum + value, 0) / 4
    : null;
  const status: "unknown" | "meets" | "below" =
    average === null
      ? "unknown"
      : average >= ELDER_AVERAGE_TARGET
        ? "meets"
        : "below";
  return { wars, average, status };
}
