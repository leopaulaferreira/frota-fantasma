export interface WeeklyReviewMember {
  participation?: ParticipationContext;
  tag: string;
  name: string;
  role: string;
  points: number | null;
  decksUsed: number | null;
  donations?: number | null;
  lastSeen: string | null;
  offlineSeconds: number | null;
  observedSince: string | null;
  belowPoints: boolean;
  offlineExceeded: boolean;
  status: "REVIEW" | "UNKNOWN" | "CLEAR";
  missingEvidence: string[];
}
export interface WeeklyReviewReport {
  week: string;
  generatedAt: string;
  cutoff: string;
  deadline: string;
  timezone: string;
  phase: "LIVE" | "CLOSED";
  pointsTarget: number;
  offlineDays: number;
  collectionEnabled: boolean;
  membersCapturedAt: string | null;
  riverCapturedAt: string | null;
  riverSnapshotId: number | null;
  membersFresh: boolean;
  riverFresh: boolean;
  rosterComplete: boolean;
  members: WeeklyReviewMember[];
}
export const reviewStatuses = {
  REVIEW: "Avaliar individualmente",
  UNKNOWN: "Conferir dados",
  CLEAR: "Sem sinal pelas regras",
};
export const saoPauloTime = (value: string | null) =>
  value
    ? new Date(value).toLocaleString("pt-BR", { timeZone: "America/Sao_Paulo" })
    : "Sem coleta";
export function offlineDuration(seconds: number | null) {
  if (seconds === null) return "Atividade desconhecida";
  const days = Math.floor(seconds / 86400),
    hours = Math.floor((seconds % 86400) / 3600),
    minutes = Math.floor((seconds % 3600) / 60);
  return `${days}d ${hours}h ${minutes}min`;
}

export const decisionLabels = {
  KEEP: "Manter nesta semana",
  TALK: "Conversar com o membro",
  DISMISS: "Dispensar do clã",
  WAIT: "Aguardar justificativa",
  PENDING: "Reabrir avaliação",
};
export interface ReviewDecision {
  revision: number;
  playerTag: string;
  week: string;
  action: keyof typeof decisionLabels;
  reason: string;
  createdAt: string;
  createdBy: string;
}
export function decisionPending(decision?: ReviewDecision) {
  return (
    !decision || decision.action === "WAIT" || decision.action === "PENDING"
  );
}

export function reviewOrder(a: WeeklyReviewMember, b: WeeklyReviewMember) {
  const group = (p: WeeklyReviewMember) =>
    p.points === 0 ? 0 : p.belowPoints ? 1 : 2;
  return (
    group(a) - group(b) ||
    (a.decksUsed ?? Infinity) - (b.decksUsed ?? Infinity) ||
    (a.points ?? Infinity) - (b.points ?? Infinity) ||
    a.tag.localeCompare(b.tag)
  );
}

export interface ParticipationContext {
  wars: {
    id: number;
    publishedAt: string;
    capturedAt: string;
    points: number | null;
    decks: number | null;
    eligible: boolean;
    source: string;
    reason: string;
  }[];
  averageFour: number | null;
  warsConsidered: number;
  belowTarget: number;
  consecutiveZeroAttacks: number;
  twoBelow: boolean;
  twoZero: boolean;
  trend: string;
  donationWeeks: {
    week: string;
    observed: number | null;
    lastCounter: number | null;
    samples: number;
    resets: number;
    complete: boolean;
    zeroConfirmed: boolean;
    reason: string;
  }[];
  totalFourWeeks: number | null;
  donationCoverageComplete: boolean;
  zeroDonationWeeks: number;
  twoZeroDonations: boolean;
  events: { type: string; at: string }[];
  tenureDays: number | null;
  newcomer: boolean;
  rejoined: boolean;
  tenureBasis: string;
  group: "PRIORITY" | "FOLLOW" | "INSUFFICIENT";
  reasons: string[];
}
export const participationGroups = {
  PRIORITY: "Revisão prioritária",
  FOLLOW: "Acompanhar",
  INSUFFICIENT: "Dados insuficientes",
};
