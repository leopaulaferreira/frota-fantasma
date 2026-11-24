import type { Report } from "./api";

export function evolutionSeries(report: Pick<Report, "week" | "evolution">, key: string) {
  const end = new Date(`${report.week}T00:00:00Z`);
  return Array.from({ length: 8 }, (_, index) => {
    const day = new Date(end);
    day.setUTCDate(day.getUTCDate() - (7 - index) * 7);
    const week = day.toISOString().slice(0, 10);
    const metric = report.evolution.find(item => item.week === week)?.metrics[key];
    const value = metric?.quality !== "UNAVAILABLE" && typeof metric?.value === "number" && Number.isFinite(metric.value) && metric.value >= 0 ? metric.value : null;
    return { week, value, partial: value !== null && !!(metric?.incompletePeriod || metric?.insufficientSample || metric?.quality === "INCOMPLETE" || metric?.quality === "INSUFFICIENT_DATA") };
  });
}
