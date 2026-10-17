import type { Metric, Player, Report } from "./api";
export type ProspectBand = "strong" | "near" | "developing" | "unknown";
export const prospectBands: Record<ProspectBand, string> = {
  strong: "Forte para Ancião",
  near: "Próximo / pontos a desenvolver",
  developing: "Em desenvolvimento",
  unknown: "Dados insuficientes",
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
export function classifyProspect(
  player: Player,
  report: Report,
  objective: number,
  tolerance: number,
) {
  const average = numeric(player.metrics.recentAverage);
  const donations = numeric(player.metrics.weeklyDonations);
  const tenure = numeric(player.metrics.observedTenureDays);
  const decks = numeric(player.metrics.standardDeckTargetPercent);
  const alerts = numeric(player.metrics.recentAlerts);
  const settings = report.recommendationRules?.settings.analytics;
  const setting = (key: string) =>
    typeof settings?.[key] === "number" ? (settings[key] as number) : null;
  const donationTarget = setting("promotionDonations"),
    tenureTarget = setting("promotionTenureDays"),
    deckTarget = setting("adequateStandardDeckPercent");
  const reasons: string[] = [];
  if (
    !Number.isFinite(objective) ||
    objective <= 0 ||
    !Number.isFinite(tolerance) ||
    tolerance < 0 ||
    tolerance > 20
  )
    return {
      band: "unknown" as ProspectBand,
      reasons: ["Informe objetivo positivo e tolerância de 0% a 20%."],
    };
  if (average !== null)
    reasons.push(
      `Média ${average.toLocaleString("pt-BR")} / objetivo ${objective.toLocaleString("pt-BR")} pontos.`,
    );
  else reasons.push("Média recente desconhecida.");
  if (
    player.metrics.recentAverage?.insufficientSample ||
    player.metrics.recentAverage?.incompletePeriod
  )
    reasons.push(
      "A média recente está sinalizada como amostra insuficiente ou período incompleto.",
    );
  if (!fourKnown(player))
    reasons.push("Ainda não há quatro pontuações conhecidas na janela do clã.");
  if (donations !== null && donationTarget !== null)
    reasons.push(
      `Doações observadas: ${donations.toLocaleString("pt-BR")} / referência ${donationTarget.toLocaleString("pt-BR")}. São um limite inferior; coleta incompleta pode subestimar o total.`,
    );
  else reasons.push("Doações ou referência de doações indisponíveis.");
  if (tenure !== null && tenureTarget !== null)
    reasons.push(
      `Tempo observado: ${tenure} / ${tenureTarget} dias. Não é necessariamente o tempo real no clã.`,
    );
  else reasons.push("Tempo no clã ou referência indisponível.");
  if (decks !== null && deckTarget !== null)
    reasons.push(
      `Uso do padrão de decks: ${decks.toLocaleString("pt-BR", { maximumFractionDigits: 1 })}% / ${deckTarget}%. Disponibilidade individual não confirmada.`,
    );
  else reasons.push("Referência de uso de decks indisponível.");
  if (alerts !== null)
    reasons.push(
      `${alerts} alerta(s) registrado(s) na janela das regras; zero não comprova monitoramento contínuo.`,
    );
  else reasons.push("Histórico de alertas indisponível.");
  const unknown =
    !fourKnown(player) ||
    player.metrics.recentAverage?.insufficientSample ||
    player.metrics.recentAverage?.incompletePeriod ||
    [
      average,
      donations,
      tenure,
      decks,
      alerts,
      donationTarget,
      tenureTarget,
      deckTarget,
    ].some((v) => v === null);
  if (unknown) return { band: "unknown" as ProspectBand, reasons };
  const strong =
    average! >= objective &&
    donations! >= donationTarget! &&
    tenure! >= tenureTarget! &&
    decks! >= deckTarget! &&
    alerts === 0 &&
    player.operationalStatus.value === "MANTER" &&
    player.elderCandidate.value === true &&
    !player.elderCandidate.insufficientSample;
  if (strong)
    reasons.unshift(
      "Atende à referência de pontuação e aos critérios atuais de candidatura. Requer decisão humana.",
    );
  else reasons.unshift(player.elderCandidate.reason);
  const band: ProspectBand = strong
    ? "strong"
    : average! >= objective * (1 - tolerance / 100)
      ? "near"
      : "developing";
  return { band, reasons };
}
