import { useQuery } from "@tanstack/react-query";
export interface Metric {
  value: unknown;
  origin: string;
  quality: string;
  period: { from: string | null; to: string | null; basis: string };
  warsConsidered: number;
  insufficientSample: boolean;
  incompletePeriod: boolean;
  dataUsed: string[];
  reason: string;
}
export interface WarStats {
  warId: number;
  seasonId: number;
  sectionIndex: number;
  publishedAt: string;
  points: Metric;
  decksUsed: Metric;
  participation: Metric;
  rank?: Metric;
}
export interface Player {
  tag: string;
  name: string;
  role: string;
  currentMember: boolean;
  metrics: Record<string, Metric>;
  wars: WarStats[];
  donations: {
    week: string;
    observedDonations: Metric;
    snapshots: number;
    detectedResets: number;
  }[];
  participation: Metric;
  historicalContext: Metric;
  operationalStatus: Metric;
  elderCandidate: Metric;
}
export interface Report {
  generatedAt: string;
  week: string;
  ruleVersion: string;
  players: Player[];
  clan: Record<string, Metric>;
  highlights: Highlight[];
  evolution: { week: string; metrics: Record<string, Metric> }[];
  recommendationRules: RuleVersion;
}
export interface Highlight {
  title: string;
  playerTag: string | null;
  result: Metric;
}
export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
}
export interface Award {
  week: string;
  evaluationId: string;
  finalizedAt: string;
  title: string;
  playerTag: string;
  evidence: Highlight;
}
export interface Evaluation {
  id: string;
  week: string;
  createdAt: string;
  analysisCutoff: string;
  formulaVersion: string;
  report: Report;
}
export interface RuleVersion {
  id: string | null;
  revision: number;
  createdAt: string;
  reason: string;
  settings: {
    analytics: Record<string, number | string>;
    score: {
      enabled: boolean;
      warWeight: number;
      decksWeight: number;
      donationsWeight: number;
      warTarget: number;
      donationTarget: number;
    };
    recentAlertDays: number;
    immortalAllowPartial: boolean;
  };
}
export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}
let csrf: Promise<{ headerName: string; token: string }> | undefined;
export function clearCsrf() {
  csrf = undefined;
}
export async function api<T>(path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {};
  if (body !== undefined) {
    csrf ??= fetch("/api/auth/csrf", { credentials: "same-origin" })
      .then(async (r) => {
        if (!r.ok)
          throw new ApiError(
            r.status,
            "Não foi possível iniciar a sessão de segurança.",
          );
        return r.json();
      })
      .catch((e) => {
        csrf = undefined;
        throw e;
      });
    const token = await csrf;
    headers[token.headerName] = token.token;
    headers["Content-Type"] =
      body instanceof URLSearchParams
        ? "application/x-www-form-urlencoded"
        : "application/json";
  }
  const r = await fetch("/api" + path, {
    credentials: "same-origin",
    headers,
    method: body === undefined ? "GET" : "POST",
    body:
      body === undefined
        ? undefined
        : body instanceof URLSearchParams
          ? body.toString()
          : JSON.stringify(body),
  });
  if (path === "/auth/login" || path === "/auth/logout" || r.status === 403)
    clearCsrf();
  if (!r.ok) {
    const p = await r.json().catch(() => ({}));
    if (r.status === 401 && !path.startsWith("/auth/"))
      window.dispatchEvent(new Event("frota:session-expired"));
    throw new ApiError(
      r.status,
      p.detail ||
        p.message ||
        "Não foi possível consultar os dados (" + r.status + ").",
    );
  }
  return r.json();
}
export function useApi<T>(
  path: string,
  refetchInterval: number | false = false,
) {
  return useQuery({
    queryKey: [path],
    queryFn: () => api<T>(path),
    staleTime: 30000,
    refetchInterval,
    retry: (count, error) =>
      count < 1 && (!(error instanceof ApiError) || error.status >= 500),
  });
}
export const tagPath = (tag: string) => encodeURIComponent(tag);
export const num = (m?: Metric) =>
  typeof m?.value === "number" ? m.value : null;
export function format(v: unknown): string {
  if (v === null || v === undefined) return "Dados insuficientes";
  if (typeof v === "boolean") return v ? "Sim" : "Não";
  if (typeof v === "number")
    return new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 1 }).format(
      v,
    );
  if (typeof v === "object") return JSON.stringify(v);
  return String(v).replaceAll("_", " ");
}
export function date(v?: string | null) {
  if (!v) return "Não informado";
  const d = new Date(v.length === 10 ? v + "T12:00:00" : v);
  return isNaN(d.getTime()) ? v : d.toLocaleDateString("pt-BR");
}
export function dateTime(v?: string | null) {
  if (!v) return "Não informado";
  const d = new Date(v);
  return isNaN(d.getTime()) ? v : d.toLocaleString("pt-BR");
}
export function rankRows<
  T extends { contribution: number | null; playerTag: string },
>(rows: T[]) {
  const score = (r: T) =>
    typeof r.contribution === "number" && r.contribution >= 0
      ? r.contribution
      : -Infinity;
  return [...rows]
    .sort(
      (a, b) => score(b) - score(a) || a.playerTag.localeCompare(b.playerTag),
    )
    .map((r, _i, all) => ({
      ...r,
      rank:
        score(r) === -Infinity
          ? null
          : 1 + all.filter((x) => score(x) > score(r)).length,
    }));
}
