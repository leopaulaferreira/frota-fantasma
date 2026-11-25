import "../overview.css";
import { CollectionFreshness } from "../components/CollectionFreshness";
import { WeeklyEvolution } from "../components/WeeklyEvolution";
import { LeadershipSummary } from "../components/LeadershipSummary";
import { useAdmin } from "../lib/auth";
import { LeaderPriorities } from "../components/LeaderPriorities";
import { FleetBanner, AwardCard } from "../components/royale";
import { Link } from "react-router-dom";
import { useApi, date, type Report } from "../lib/api";
import { QueryState, Metrics, Empty } from "../components/ui";
export function Overview() {
  const admin = useAdmin();
  const q = useApi<Report>("/analytics");
  const r = q.data;
  return (
    <div className="overview">
      <FleetBanner week={r?.week} />
      <QueryState query={q}>
        {r && (
          <>
            <div className="section-head">
              <h2>Resumo da semana</h2>

            </div>
            <CollectionFreshness generatedAt={r.generatedAt} />
            <Metrics
              metrics={r.clan}
              keys={[
                "currentMembers",
                "averageRecentPoints",
                "participationPercent",
                "observedWeeklyDonations",
              ]}
            />
            <section className="overview-attention" aria-labelledby="attention-title">
              <div className="section-head">
                <h2 id="attention-title">Pendências da semana</h2>
                <Link to="/membros">Ver membros →</Link>
              </div>
              <LeadershipSummary report={r} />
              <Metrics metrics={r.clan} keys={["membersToObserve", "membersToReview", "membersOffline"]} />
            </section>
            {admin && <details className="panel overview-priorities">
              <summary>Investigar prioridades e acompanhamentos</summary>
              <LeaderPriorities report={r} />
            </details>}
            <WeeklyEvolution report={r} />
            <div className="section-head">
              <h2>Premiações previstas</h2>
              <Link to="/premiacoes">Histórico de títulos →</Link>
            </div>
            <div className="awards">
              {r.highlights.map((h) => (
                <AwardCard
                  key={h.title}
                  highlight={h}
                  players={r.players}
                  week={r.week}
                />
              ))}
            </div>
            <details className="panel">
            <summary>Consultar indicadores e evidências por semana</summary>
            {r.evolution.length ? (
              r.evolution.map((e) => (
                <details className="panel" key={e.week}>
                  <summary>Semana de {date(e.week)}</summary>
                  <Metrics metrics={e.metrics} />
                </details>
              ))
            ) : (
              <Empty />
            )}
            </details>
            <details className="panel">
              <summary>Todos os indicadores do clã</summary>
              <Metrics metrics={r.clan} />
            </details>
          </>
        )}
      </QueryState>
    </div>
  );
}
