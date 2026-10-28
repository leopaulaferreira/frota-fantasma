import { num, type Player } from "./api";
import { shiftWeek } from "./weeks";
export function donationWeeks(currentWeek: string) {
  return Array.from({ length: 6 }, (_, index) =>
    shiftWeek(currentWeek, index - 5),
  );
}
export function observedWindow(player: Player, weeks: string[]) {
  const values = weeks
    .map((week) => {
      const period = player.donations.find((p) => p.week === week);
      return period ? num(period.observedDonations) : null;
    })
    .filter((value): value is number => value !== null);
  return {
    total: values.length ? values.reduce((sum, n) => sum + n, 0) : null,
    knownWeeks: values.length,
  };
}
