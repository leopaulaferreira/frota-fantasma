import type { Metric, Player, Report } from "./api";
export type ProspectBand =
  "strong" | "near" | "developing" | "unknown" | "blocked";
export const prospectBands: Record<ProspectBand, string> = {
  strong: "Pronto para avaliação",
  near: "Poucas pendências",
  developing: "Em desenvolvimento",
  unknown: "Completar evidências",
  blocked: "Revisar alertas",
};
export type CriterionState = "pass" | "pending" | "unknown" | "blocked";
export interface ProspectCriterion {
  key: string;
  label: string;
  state: CriterionState;
  detail: string;
  action: string;
  metric?: Metric;
}
export const criterionLabels: Record<CriterionState, string> = {
  pass: "Atendido",
  pending: "Pendente",
  unknown: "Sem evidência",
  blocked: "Revisar",
};
export function numeric(metric?: Metric): number | null {
  return typeof metric?.value === "number" && Number.isFinite(metric.value)
    ? metric.value
    : null;
}
export function fourKnown(player: Player): boolean {
  return (
    player.metrics.recentAverage?.warsConsidered === 4 &&
    player.wars.slice(0, 4).length === 4 &&
    player.wars.slice(0, 4).every((w) => numeric(w.points) !== null)
  );
}
export function promotionSetting(report: Report, key: string): number | null {
  const value = report.recommendationRules?.settings.analytics[key];
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}
const display = (n: number) =>
  n.toLocaleString("pt-BR", { maximumFractionDigits: 1 });

/** Read-only triage. The backend eligibility remains a mandatory gate. */
export function classifyProspect(
  player: Player,
  report: Report,
  objective: number,
  tolerance: number,
) {
  const criteria: ProspectCriterion[] = [];
  const add = (
    key: string,
    label: string,
    state: CriterionState,
    detail: string,
    action: string,
    metric?: Metric,
  ) => criteria.push({ key, label, state, detail, action, metric });
  const minimum = promotionSetting(report, "minimumWars");
  const average = numeric(player.metrics.recentAverage);
  const recent = player.wars.slice(0, 4);
  const known = recent.filter(
    (w) => numeric(w.points) !== null && numeric(w.decksUsed) !== null,
  ).length;
  const complete =
    minimum !== null &&
    recent.length >= minimum &&
    known === recent.length &&
    (player.metrics.recentAverage?.warsConsidered ?? 0) >= minimum &&
    !player.metrics.recentAverage?.insufficientSample &&
    !player.metrics.recentAverage?.incompletePeriod;
  add(
    "sample",
    "Histórico de guerras",
    complete ? "pass" : "unknown",
    `${known}/${recent.length} guerras com pontos e decks conhecidos; mínimo configurado: ${minimum ?? "indisponível"}.`,
    "Coletar resultados oficiais e completar a amostra de guerras.",
    player.metrics.recentAverage,
  );
  const officialTarget = promotionSetting(report, "pointsTarget");
  // A simulation can raise the reference, but can never relax the saved policy.
  const validInput =
    Number.isFinite(objective) &&
    objective > 0 &&
    Number.isFinite(tolerance) &&
    tolerance >= 0 &&
    tolerance <= 20;
  const target =
    officialTarget === null || !validInput
      ? null
      : Math.max(objective, officialTarget);
  const compare = (
    key: string,
    label: string,
    metric: Metric | undefined,
    reference: number | null,
    unit: string,
    action: string,
  ) => {
    const value = numeric(metric);
    add(
      key,
      label,
      value === null || reference === null
        ? "unknown"
        : value >= reference
          ? "pass"
          : "pending",
      `${value === null ? "Desconhecido" : display(value)} / ${reference === null ? "referência indisponível" : display(reference)} ${unit}`,
      action,
      metric,
    );
  };
  compare(
    "points",
    "Média recente",
    player.metrics.recentAverage,
    target,
    "pontos",
    "Acompanhar desempenho nas próximas guerras até atingir a média de referência.",
  );
  const participationKnown =
    recent.length > 0 &&
    recent.every(
      (w) => numeric(w.points) !== null && numeric(w.decksUsed) !== null,
    );
  const positive = recent.filter(
    (w) => (numeric(w.points) ?? 0) > 0 || (numeric(w.decksUsed) ?? 0) > 0,
  ).length;
  add(
    "participation",
    "Participação",
    !participationKnown
      ? "unknown"
      : positive === recent.length
        ? "pass"
        : "pending",
    `${positive}/${recent.length} guerras com participação positiva. Registro ausente não equivale a zero.`,
    "Conversar sobre guerras sem participação e acompanhar regularidade.",
    player.metrics.recentParticipation,
  );
  compare(
    "decks",
    "Uso do padrão de decks",
    player.metrics.standardDeckTargetPercent,
    promotionSetting(report, "adequateStandardDeckPercent"),
    "%",
    "Acompanhar o uso dos decks, considerando a disponibilidade real do membro.",
  );
  compare(
    "tenure",
    "Tempo observado",
    player.metrics.observedTenureDays,
    promotionSetting(report, "promotionTenureDays"),
    "dias",
    "Aguardar mais tempo observado no episódio atual de associação ao clã.",
  );
  compare(
    "donations",
    "Doações na semana",
    player.metrics.weeklyDonations,
    promotionSetting(report, "promotionDonations"),
    "doações",
    "Verificar novas coletas e a contribuição na semana; o total observado pode estar subestimado.",
  );
  const alerts = numeric(player.metrics.recentAlerts);
  add(
    "alerts",
    "Alertas registrados",
    alerts === null ? "unknown" : alerts > 0 ? "blocked" : "pass",
    alerts === null
      ? "Histórico indisponível."
      : `${alerts} alerta(s) em ${report.recommendationRules?.settings.recentAlertDays ?? "?"} dias. Zero não comprova monitoramento contínuo.`,
    "Revisar as avaliações com alerta e acompanhar a recuperação durante a janela configurada.",
    player.metrics.recentAlerts,
  );
  const status = player.operationalStatus;
  const stale = player.metrics.trophies?.incompletePeriod === true;
  const missingActivity = player.metrics.lastKnownActivity?.value == null;
  const statusUnknown =
    !status.value || status.insufficientSample || stale || missingActivity;
  add(
    "status",
    "Situação atual",
    statusUnknown ? "unknown" : status.value === "MANTER" ? "pass" : "blocked",
    stale
      ? "Coleta de membros desatualizada. Atualize os dados antes de decidir."
      : status.reason,
    statusUnknown
      ? "Atualizar a coleta e verificar a atividade oficial do membro."
      : "Revisar o contexto operacional e as notas da liderança antes de promover.",
    status,
  );
  if (!player.currentMember || player.role !== "member")
    add(
      "role",
      "Cargo e elenco",
      "blocked",
      "Apenas membros atuais sem cargo são elegíveis.",
      "Conferir cargo e vínculo atual com o clã.",
    );
  if (
    player.elderCandidate.insufficientSample &&
    criteria.every((c) => c.state === "pass")
  )
    add(
      "policy",
      "Amostra da avaliação",
      "unknown",
      player.elderCandidate.reason,
      "Completar as evidências exigidas pela avaliação oficial.",
      player.elderCandidate,
    );
  if (
    player.elderCandidate.value !== true &&
    criteria.every((c) => c.state === "pass")
  )
    add(
      "policy",
      "Regras salvas",
      "blocked",
      player.elderCandidate.reason,
      "Conferir as evidências da avaliação oficial antes de decidir.",
      player.elderCandidate,
    );
  const pending = criteria.filter((c) => c.state === "pending");
  const unknown = criteria.filter((c) => c.state === "unknown");
  const blockers = criteria.filter((c) => c.state === "blocked");
  const passed = criteria.filter((c) => c.state === "pass").length;
  const band: ProspectBand = blockers.length
    ? "blocked"
    : unknown.length || !validInput
      ? "unknown"
      : !pending.length && player.elderCandidate.value === true
        ? "strong"
        : pending.length <= 2 &&
            average !== null &&
            target !== null &&
            average >= target * (1 - tolerance / 100)
          ? "near"
          : "developing";
  const nextAction = !validInput
    ? "Informe uma referência positiva e tolerância de 0% a 20%."
    : (blockers[0]?.action ??
      unknown[0]?.action ??
      pending[0]?.action ??
      "Revisar histórico e notas, conversar com o membro e decidir a promoção no jogo.");
  return {
    band,
    criteria,
    passed,
    pending: pending.length,
    unknown: unknown.length,
    blockers: blockers.length,
    nextAction,
    reasons: criteria.map((c) => `${c.label}: ${c.detail}`),
  };
}
export type ProspectAssessment = ReturnType<typeof classifyProspect>;
const bandOrder: ProspectBand[] = [
  "strong",
  "near",
  "developing",
  "blocked",
  "unknown",
];
export function compareProspects(
  a: { p: Player; assessment: ProspectAssessment },
  b: { p: Player; assessment: ProspectAssessment },
  sort = "readiness",
) {
  const value = (p: Player, key: string) =>
    numeric(p.metrics[key]) ?? -Infinity;
  const compareMetric = (key: string) => {
    const av = value(a.p, key),
      bv = value(b.p, key);
    return av === bv ? 0 : av > bv ? -1 : 1;
  };
  return (
    (sort === "readiness"
      ? bandOrder.indexOf(a.assessment.band) -
          bandOrder.indexOf(b.assessment.band) ||
        b.assessment.passed - a.assessment.passed
      : compareMetric(sort)) ||
    compareMetric("recentAverage") ||
    compareMetric("standardDeckTargetPercent") ||
    a.p.tag.localeCompare(b.p.tag)
  );
}
