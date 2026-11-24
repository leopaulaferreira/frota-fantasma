import { warLabel } from "../lib/warAnalysis";
import { useSearchParams, Link } from "react-router-dom";
import { useApi, type Report } from "../lib/api";
import {
  classifyProspect,
  promotionSetting,
  prospectBands,
} from "../lib/prospects";
import { label } from "../lib/labels";
import {
  Heading,
  QueryState,
  Table,
  MetricView,
  Empty,
} from "../components/ui";
export function Compare() {
  const q = useApi<Report>("/analytics");
  const [params, setParams] = useSearchParams();
  const selected = (params.get("tags") || "")
    .split(",")
    .filter(Boolean)
    .slice(0, 4);
  const players = q.data?.players.filter((p) => selected.includes(p.tag)) || [];
  const warIds = [
    ...new Set(players.flatMap((p) => p.wars.map((w) => w.warId))),
  ].sort((a, b) => {
    const wars = players.flatMap((p) => p.wars);
    return (wars.find((w) => w.warId === b)?.publishedAt || "").localeCompare(
      wars.find((w) => w.warId === a)?.publishedAt || "",
    );
  });
  return (
    <div className="member-comparison">
      <Heading eyebrow="LEITURA EM CONTEXTO" title="Comparar jogadores" />
      <p className="lede">
        Até quatro membros, nas mesmas guerras. Abra as métricas para verificar
        diferenças de amostra e período.
      </p>
      <QueryState query={q}>
        <div className="panel comparison-selection">
          <div className="section-head">
            <h2>Membros selecionados</h2>
            <span className="badge" role="status">{selected.length} de 4</span>
          </div>
          <label>
            Adicionar membro
            <select
              aria-label="Adicionar membro"
              value=""
              disabled={selected.length >= 4}
              onChange={(e) =>
                setParams({ tags: [...selected, e.target.value].join(",") })
              }
            >
              <option value="">Selecionar membro…</option>
              {q.data?.players
                .filter((p) => !selected.includes(p.tag))
                .map((p) => (
                  <option key={p.tag} value={p.tag}>
                    {p.name} ({p.tag}){!p.currentMember ? " · ex-membro" : ""}
                  </option>
                ))}
            </select>
          </label>
          <p className="muted comparison-selection-help">
            {selected.length >= 4
              ? "Limite de quatro membros atingido. Remova um para adicionar outro."
              : "Adicione até quatro membros. Toque no nome selecionado para removê-lo."}
          </p>
          <div className="chips">
            {selected.map((t) => (
              <button
                key={t}
                onClick={() =>
                  setParams({ tags: selected.filter((x) => x !== t).join(",") })
                }
              >
                {q.data?.players.find((p) => p.tag === t)?.name || t} ×
              </button>
            ))}
          </div>
        </div>
        {players.length ? (
          <>
            <h2>Contexto para a decisão</h2>
            <div className="comparison-decisions">
              {players.map((p) => {
                const assessment = classifyProspect(
                  p,
                  q.data!,
                  promotionSetting(q.data!, "pointsTarget") ?? NaN,
                  5,
                );
                return (
                  <article className="panel" key={p.tag}>
                    <Link to={"/membros/" + encodeURIComponent(p.tag)}>
                      <h3>{p.name}</h3>
                    </Link>
                    {p.currentMember && p.role === "member" ? (
                      <>
                        <span className={`badge prospect-${assessment.band}`}>
                          {prospectBands[assessment.band]}
                        </span>
                        <p>
                          {assessment.passed}/{assessment.criteria.length}{" "}
                          critérios para Ancião atendidos
                        </p>
                        <p className="muted">{assessment.nextAction}</p>
                      </>
                    ) : (
                      <p className="muted">
                        {!p.currentMember
                          ? "Fora do elenco atual"
                          : "Já possui cargo no clã"}
                      </p>
                    )}
                    <MetricView
                      compact
                      name="Situação atual"
                      metric={p.operationalStatus}
                    />
                  </article>
                );
              })}
            </div>
            <h2>Indicadores comparáveis</h2>
            <p className="muted">Role a tabela para consultar os indicadores e deslize para os lados para ver os demais membros.</p>
            <Table headers={["Indicador", ...players.map((p) => p.name)]}>
              {[...new Set(players.flatMap((p) => Object.keys(p.metrics)))].map(
                (k) => (
                  <tr key={k}>
                    <th scope="row">{label(k)}</th>
                    {players.map((p) => (
                      <td key={p.tag}>
                        <MetricView
                          compact
                          name={label(k)}
                          metric={p.metrics[k]}
                        />
                      </td>
                    ))}
                  </tr>
                ),
              )}
            </Table>
            <h2>Guerra a guerra</h2>
            <Table headers={["Guerra", ...players.map((p) => p.name)]}>
              {warIds.map((id) => {
                const w = players
                  .flatMap((p) => p.wars)
                  .find((w) => w.warId === id)!;
                return (
                  <tr key={id}>
                    <th scope="row">
                      <Link to={"/guerras/" + id}>
                        {warLabel(w, String(q.data?.recommendationRules?.settings.analytics.timezone || "America/Sao_Paulo"))}
                      </Link>
                      <small>Temporada {w.seasonId} · seção {w.sectionIndex}</small>
                    </th>
                    {players.map((p) => {
                      const pw = p.wars.find((x) => x.warId === id);
                      return (
                        <td key={p.tag}>
                          {pw ? (
                            <>
                              <MetricView
                                compact
                                name="Pontos"
                                metric={pw.points}
                              />
                              <MetricView
                                compact
                                name="Decks"
                                metric={pw.decksUsed}
                              />
                              <MetricView
                                compact
                                name="Posição"
                                metric={pw.rank}
                              />
                            </>
                          ) : (
                            <span className="muted">Registro desconhecido</span>
                          )}
                        </td>
                      );
                    })}
                  </tr>
                );
              })}
            </Table>
          </>
        ) : (
          <Empty>Selecione membros para iniciar a comparação.</Empty>
        )}
      </QueryState>
    </div>
  );
}
