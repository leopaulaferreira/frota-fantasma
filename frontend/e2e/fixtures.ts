import type { Metric, Player, Report, RuleVersion } from "../src/lib/api";
export const metric = (
  value: unknown,
  extra: Partial<Metric> = {},
): Metric => ({
  value,
  origin: "DERIVED",
  quality: "AVAILABLE",
  period: {
    from: "2026-09-21T00:00:00Z",
    to: "2026-10-12T12:00:00Z",
    basis: "FOUR_CLAN_WARS",
  },
  warsConsidered: 4,
  insufficientSample: false,
  incompletePeriod: false,
  dataUsed: ["war:1:fame=3200", "war:1:decksUsed=16"],
  reason:
    "Calculado a partir das quatro guerras registradas; meta padrão de 16 decks.",
  ...extra,
});
export const rules: RuleVersion = {
  id: null,
  revision: 0,
  createdAt: "2026-10-12T12:00:00Z",
  reason: "Regras iniciais",
  settings: {
    analytics: {
      pointsTarget: 1600,
      offlineDays: 5,
      minimumWars: 4,
      immortalMinimumWars: 4,
      reviewLowWars: 3,
      adequateStandardDeckPercent: 80,
      promotionTenureDays: 28,
      promotionDonations: 100,
      freshness: "PT2H",
      timezone: "UTC",
    },
    score: {
      enabled: true,
      warWeight: 45,
      decksWeight: 35,
      donationsWeight: 20,
      warTarget: 3200,
      donationTarget: 200,
    },
    recentAlertDays: 28,
    immortalAllowPartial: false,
  },
};
export const players: Player[] = [
  "Maré Negra",
  "Corvo do Norte",
  "Brisa",
  "Almirante",
  "Sombra",
].map((name, i) => ({
  tag: "#P0" + (i + 1),
  name,
  role: "member",
  currentMember: true,
  metrics: {
    recentPoints: metric(12000 - i * 1500),
    recentAverage: metric(3000 - i * 350),
    historicalAverage: metric(2850 - i * 250),
    recentParticipation: metric("4/4"),
    recentDecks: metric(64 - i * 8),
    completeStandardWars: metric(4 - i),
    weeklyDonations: metric(240 - i * 30, {
      origin: "SNAPSHOT_DERIVED",
      quality: "INCOMPLETE",
      incompletePeriod: true,
      reason:
        "Diferenças entre snapshots. Reset detectado; período incompleto.",
    }),
    offlineDays: metric(i),
    lastKnownActivity: metric("2026-10-12T10:00:00Z"),
    trophies: metric(10000 - i * 300),
    observedTenureDays: metric(80),
    recentTrendPoints: metric(200 - i * 100),
    frotaScore: metric(94 - i * 10),
    frotaScoreWar: metric(40),
    frotaScoreDecks: metric(35),
    frotaScoreDonations: metric(19, { incompletePeriod: true }),
    deckUtilizationPercent: metric(null, {
      origin: "UNAVAILABLE",
      quality: "UNAVAILABLE",
      reason: "A API não fornece a capacidade individual de decks.",
    }),
  },
  wars: Array.from({ length: 6 }, (_, w) => ({
    warId: w + 1,
    seasonId: 137,
    sectionIndex: 3 - w,
    publishedAt: new Date(Date.UTC(2026, 9, 12 - w * 7)).toISOString(),
    points: metric(i === 2 && w === 4 ? null : 3200 - i * 200 - w * 100, {
      insufficientSample: i === 2 && w === 4,
    }),
    decksUsed: metric(16 - i),
    participation: metric(i ? "PARCIAL_PADRAO" : "COMPLETA_PADRAO"),
    rank: metric(i + 1),
  })),
  donations: [
    {
      week: "2026-10-12",
      observedDonations: metric(240 - i * 30, {
        incompletePeriod: true,
        quality: "INCOMPLETE",
        origin: "SNAPSHOT_DERIVED",
        reason: "Reset detectado; contagem nunca é negativa.",
      }),
      snapshots: 8,
      detectedResets: 1,
    },
  ],
  participation: metric("ADEQUADA"),
  historicalContext: metric(i < 2 ? "ANTIGO_CONSISTENTE" : "IRREGULAR"),
  operationalStatus: metric(i < 2 ? "MANTER" : i < 4 ? "OBSERVAR" : "REVISAR", {
    reason:
      i < 2
        ? "Participação consistente no período."
        : "Decks abaixo da meta em múltiplas guerras. Consulte o histórico antes de decidir.",
  }),
  elderCandidate: metric(i === 0, {
    reason:
      i === 0
        ? "Frequência, média, doações e tempo observados atendem aos critérios."
        : "Amostra ou consistência não atende aos critérios de candidatura.",
  }),
}));
export const report: Report = {
  generatedAt: "2026-10-12T12:00:00Z",
  week: "2026-10-12",
  ruleVersion: "analytics-v2",
  recommendationRules: rules,
  players,
  clan: {
    currentMembers: metric(50),
    averageRecentPoints: metric(2480),
    participationPercent: metric(92),
    totalRecentDecks: metric(2736),
    observedWeeklyDonations: metric(8420, { incompletePeriod: true }),
    membersToObserve: metric(7),
    membersToReview: metric(2),
    membersOffline: metric(3),
  },
  highlights: ["CAPITAO_DA_GUERRA", "GUARDIAO_DA_FROTA", "ALMA_IMORTAL"].map(
    (title, i) => ({
      title,
      playerTag: players[i].tag,
      result: metric(3200 - i * 200),
    }),
  ),
  evolution: [
    {
      week: "2026-10-05",
      metrics: { averagePoints: metric(2400), totalDecks: metric(2650) },
    },
  ],
};
export const evaluation = {
  id: "eval-1",
  week: report.week,
  createdAt: report.generatedAt,
  analysisCutoff: report.generatedAt,
  formulaVersion: "analytics-v2",
  report,
};
export const award = {
  week: report.week,
  evaluationId: "eval-1",
  finalizedAt: report.generatedAt,
  title: "CAPITAO_DA_GUERRA",
  playerTag: "#P01",
  evidence: report.highlights[0],
};
