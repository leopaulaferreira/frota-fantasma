import type { Player, Metric } from "./api";
export interface LeaderNote {
  id: string;
  playerTag: string;
  kind: "OBSERVATION" | "JUSTIFICATION" | "AGREEMENT" | "CANDIDACY";
  body: string;
  createdAt: string;
  createdBy: string;
  expiresOn: string | null;
  followUpOn: string | null;
  resolvedAt: string | null;
  resolvedBy: string | null;
  resolution: string | null;
  status: "ACTIVE" | "EXPIRED" | "RESOLVED";
  followUpDue: boolean;
}
export interface ActiveNotes {
  generatedAt: string;
  timezone: string;
  notes: LeaderNote[];
}
export const noteKinds = {
  OBSERVATION: "Observação",
  JUSTIFICATION: "Justificativa",
  AGREEMENT: "Combinado",
  CANDIDACY: "Acompanhar candidatura a Ancião",
};
export const noteStatuses = {
  ACTIVE: "Vigente",
  EXPIRED: "Validade encerrada",
  RESOLVED: "Encerrada pelo líder",
};
export interface Signal {
  label: string;
  reason: string;
  order: number;
  metric?: Metric;
}
export function leaderSignals(
  player: Player,
  notes: LeaderNote[],
  objective: number,
): Signal[] {
  const signals: Signal[] = [];
  const valid = notes.filter(
    (n) => n.playerTag === player.tag && n.status === "ACTIVE",
  );
  if (valid.some((n) => n.followUpDue))
    signals.push({
      label: "Retomar acompanhamento",
      reason:
        "Há uma anotação vigente com data de acompanhamento atingida. Confira o combinado e registre o resultado.",
      order: 0,
    });
  const status = player.operationalStatus;
  if (["REVISAR", "OBSERVAR"].includes(String(status.value)))
    signals.push({
      label:
        status.value === "REVISAR"
          ? "Revisar contexto"
          : status.insufficientSample
            ? "Conhecer melhor o membro"
            : "Conversar e acompanhar",
      reason: status.reason,
      order: status.value === "REVISAR" ? 1 : status.insufficientSample ? 6 : 2,
      metric: status,
    });
  if (player.elderCandidate.value === true)
    signals.push({
      label: "Considerar para Ancião",
      reason: player.elderCandidate.reason,
      order: 3,
      metric: player.elderCandidate,
    });
  const trend = player.metrics.recentTrendPoints;
  if (
    typeof trend?.value === "number" &&
    trend.value !== 0 &&
    !trend.insufficientSample &&
    !trend.incompletePeriod
  )
    signals.push({
      label: trend.value > 0 ? "Reconhecer evolução" : "Entender queda recente",
      reason: `A média das duas últimas guerras ${trend.value > 0 ? "subiu" : "caiu"} ${Math.abs(trend.value).toLocaleString("pt-BR")} pontos em relação às duas anteriores. ${trend.reason}`,
      order: trend.value > 0 ? 4 : 2,
      metric: trend,
    });
  const average = player.metrics.recentAverage;
  if (
    Number.isFinite(objective) &&
    objective > 0 &&
    typeof average?.value === "number" &&
    average.warsConsidered >= 4 &&
    !average.insufficientSample &&
    !average.incompletePeriod &&
    average.value >= objective * 0.95 &&
    average.value < objective
  )
    signals.push({
      label: "Perto do objetivo",
      reason: `Média de ${average.value.toLocaleString("pt-BR")} pontos: até 5% abaixo da referência de ${objective.toLocaleString("pt-BR")}. Serve para acompanhamento, sem excluir o membro de candidaturas.`,
      order: 5,
      metric: average,
    });
  if (!signals.length && valid.length)
    signals.push({
      label: "Acompanhamento registrado",
      reason:
        "Há contexto vigente informado pela liderança. Consulte as notas antes de decidir.",
      order: 7,
    });
  return signals.sort((a, b) => a.order - b.order);
}
