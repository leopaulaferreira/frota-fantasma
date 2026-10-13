import { FleetBanner, AwardCard } from "../components/royale";
import { Link } from "react-router-dom";
import { ArrowUpRight } from "lucide-react";
import { useApi, date, type Report } from "../lib/api";
import {
  Heading,
  QueryState,
  Metrics,
  Person,
  Badge,
  Table,
  Empty,
} from "../components/ui";
export function Overview() {
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
            <div className="section-head">
              <h2>Sinais que merecem atenção</h2>
              <Link to="/recomendacoes">Ver recomendações →</Link>
            </div>
            <Table headers={["Membro", "Status", "Motivo"]}>
              {r.players
                .filter(
                  (p) =>
                    p.currentMember &&
                    ["OBSERVAR", "REVISAR"].includes(
                      String(p.operationalStatus.value),
                    ),
                )
                .slice(0, 5)
                .map((p) => (
                  <tr key={p.tag}>
                    <td>
                      <Person player={p} />
                    </td>
                    <td>
                      <Badge value={p.operationalStatus.value} />
                    </td>
                    <td className="reason">{p.operationalStatus.reason}</td>
                  </tr>
                ))}
            </Table>
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
