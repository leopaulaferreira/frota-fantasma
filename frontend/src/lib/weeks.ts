// Date-only arithmetic in UTC avoids browser timezone and DST shifts.
export function shiftWeek(week: string, weeks: number): string {
  const day = new Date(week + "T12:00:00Z");
  day.setUTCDate(day.getUTCDate() + weeks * 7);
  return day.toISOString().slice(0, 10);
}
export function mondayOf(value: string): string {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return "";
  const day = new Date(value + "T12:00:00Z");
  if (
    !Number.isFinite(day.getTime()) ||
    day.toISOString().slice(0, 10) !== value
  )
    return "";
  day.setUTCDate(day.getUTCDate() - ((day.getUTCDay() + 6) % 7));
  return day.toISOString().slice(0, 10);
}
