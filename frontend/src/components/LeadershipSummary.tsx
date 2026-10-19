import { Link } from "react-router-dom";
import { useAdmin } from "../lib/auth";
import type { WeeklyReviewReport } from "../lib/weeklyReview";
import { type Report, useApi } from "../lib/api";
import { classifyProspect, promotionSetting } from "../lib/prospects";
export function LeadershipSummary({ report }: { report: Report }) {
  const admin = useAdmin();
  const members = report.players.filter((p) => p.currentMember);
  const target = promotionSetting(report, "pointsTarget") ?? NaN;
  const ready = members.filter(
    (p) =>
      p.role === "member" &&
      classifyProspect(p, report, target, 5).band === "strong",
  ).length;
  const review = members.filter(
    (p) => p.operationalStatus.value === "REVISAR",
  ).length;
  const observe = members.filter(
    (p) => p.operationalStatus.value === "OBSERVAR",
  ).length;
  const stale = members.filter(
    (p) => p.metrics.trophies?.incompletePeriod,
  ).length;
  return (
    <section className="leadership-summary" aria-label="Atalhos de gestão">
      <Link to="/anciao">
        <small>DESENVOLVER</small>
        <strong>{ready}</strong>
        <b>Prontos para avaliar</b>
        <span>Critérios para Ancião e próximos passos →</span>
      </Link>
      {admin && <WeeklyDecision />}
      <Link to="/recomendacoes">
        <small>ACOMPANHAR</small>
        <strong>{review + observe}</strong>
        <b>
          {review} para revisar · {observe} para observar
        </b>
        <span>Entender alertas e conferir evidências →</span>
      </Link>
      <Link to="/operacoes">
        <small>CONFIABILIDADE</small>
        <strong>{stale}</strong>
        <b>Membros com coleta desatualizada</b>
        <span>Verificar fontes e horários das coletas →</span>
      </Link>
    </section>
  );
}

function WeeklyDecision() {
  const query = useApi<WeeklyReviewReport>("/leadership/weekly-review", 60000);
  const report = query.data;
  const count = report?.members.filter((m) => m.status === "REVIEW").length;
  return (
    <Link to="/expulsoes" className="weekly-decision">
      <small>REVISÃO SEMANAL</small>
      <strong>{count ?? "—"}</strong>
      <b>Membros para avaliar</b>
      <span>
        {query.error
          ? "Consulta indisponível · conferir revisão →"
          : !report
            ? "Consultando a revisão…"
            : !report.membersFresh ||
                !report.riverFresh ||
                !report.rosterComplete
              ? "Há dados pendentes · conferir fontes →"
              : "Conferir regras e justificativas →"}
      </span>
    </Link>
  );
}
