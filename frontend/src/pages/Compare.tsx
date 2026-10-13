import { useSearchParams, Link } from "react-router-dom";
import { useApi, date, type Report } from "../lib/api";
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
    <>
      <Heading eyebrow="LEITURA EM CONTEXTO" title="Comparar membros" />
      <p className="lede">
        Até quatro membros, nas mesmas guerras. Abra as métricas para verificar
        diferenças de amostra e período.
      </p>
      <QueryState query={q}>
        <div className="panel">
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
            <h2>Indicadores comparáveis</h2>
            <Table headers={["Indicador", ...players.map((p) => p.name)]}>
              {[...new Set(players.flatMap((p) => Object.keys(p.metrics)))].map(
                (k) => (
                  <tr key={k}>
                    <th>{label(k)}</th>
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
                    <th>
                      <Link to={"/guerras/" + id}>
                        T{w.seasonId} · seção {w.sectionIndex}
                      </Link>
                      <small>{date(w.publishedAt)}</small>
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
    </>
  );
}
