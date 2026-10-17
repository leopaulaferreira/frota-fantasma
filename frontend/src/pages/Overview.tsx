import { useAdmin } from "../lib/auth";
import { LeaderPriorities } from "../components/LeaderPriorities";
import { FleetBanner, AwardCard } from "../components/royale";
import { Link } from "react-router-dom";
import { ArrowUpRight } from "lucide-react";
import { useApi, date, type Report } from "../lib/api";
import {
  Heading,
  QueryState,
  Metrics,
  Empty,
} from "../components/ui";
export function Overview() {
  const admin = useAdmin();
  const q = useApi<Report>("/analytics");
  const r = q.data;
  return (
    <>
      <Heading eyebrow="VISÃO DO CLÃ" title="O rumo da Frota">
        <Link className="button" to="/guerras">
          Explorar guerras <ArrowUpRight size={16} />
        </Link>
      </Heading>
      <FleetBanner week={r?.week} />
      <QueryState query={q}>
        {r && (
          <>
            <div className="section-head">
              <h2>Pulso da semana</h2>
              <small>
                Análise gerada em{" "}
                {new Date(r.generatedAt).toLocaleString("pt-BR")} ·{" "}
                {r.ruleVersion}
              </small>
            </div>
            <Metrics
              metrics={r.clan}
              keys={[
                "currentMembers",
                "averageRecentPoints",
                "participationPercent",
                "totalRecentDecks",
                "observedWeeklyDonations",
                "membersToObserve",
                "membersToReview",
                "membersOffline",
              ]}
            />
            {admin && <LeaderPriorities report={r} />}
            <div className="section-head">
              <h2>Destaques provisórios</h2>
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
            <h2>Evolução entre semanas</h2>
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
            <details className="panel">
              <summary>Todos os indicadores do clã</summary>
              <Metrics metrics={r.clan} />
            </details>
          </>
        )}
      </QueryState>
    </>
  );
}
