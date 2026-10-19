export interface WeeklyReviewMember {
  tag: string;
  name: string;
  role: string;
  points: number | null;
  decksUsed: number | null;
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
