import { expect, test } from "vitest";
import { donationWeeks, observedWindow } from "./donationHistory";
import { metric, players } from "../../e2e/fixtures";
test("rolling window expires week seven and keeps six weeks across the year", () => {
  expect(donationWeeks("2027-01-04")).toEqual([
    "2026-11-30",
    "2026-12-07",
    "2026-12-14",
    "2026-12-21",
    "2026-12-28",
    "2027-01-04",
  ]);
  const player = {
    ...players[0],
    donations: [
      {
        week: "2026-11-30",
        observedDonations: metric(300),
        snapshots: 2,
        detectedResets: 0,
      },
      {
        week: "2027-01-04",
        observedDonations: metric(0),
        snapshots: 2,
        detectedResets: 0,
      },
    ],
  };
  expect(observedWindow(player, donationWeeks("2027-01-04"))).toEqual({
    total: 300,
    knownWeeks: 2,
  });
  expect(observedWindow(player, donationWeeks("2027-01-11"))).toEqual({
    total: 0,
    knownWeeks: 1,
  });
  expect(observedWindow(player, donationWeeks("2027-03-01"))).toEqual({
    total: null,
    knownWeeks: 0,
  });
});
