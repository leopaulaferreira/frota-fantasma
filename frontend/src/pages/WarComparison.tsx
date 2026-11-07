import { warLabel } from "../lib/warAnalysis";
import { useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useQueries, useQuery } from "@tanstack/react-query";
import { api, useApi, format, type Report } from "../lib/api";
import { Heading, QueryState, Empty, MetricView } from "../components/ui";
import { WarEvidence } from "../components/WarEvidence";
import {
  type WarSummary,
  type WarDetail,
  type WarEvent,
} from "../lib/warAnalysis";
import {
  allPages,
  closedArchive,
  selectWars,
  comparisonRows,
  filterRows,
  displayDate,
  type ComparisonRow,
} from "../lib/warComparison";

export function WarComparison() {
  const [url, setUrl] = useSearchParams();
  const [opened, setOpened] = useState<string | null>(null);
  const dialog = useRef<HTMLDialogElement>(null);
  const roster = useApi<{ tag: string; name: string }[]>("/clan/members");
  const analytics = useApi<Report>("/analytics");
  const archive = useQuery({
    queryKey: ["comparison-archive"],
    queryFn: () => allPages<WarSummary>("/wars"),
  });
  const events = useQuery({
    queryKey: ["war-analysis-events"],
    queryFn: () => allPages<WarEvent>("/clan/events"),
  });
  const at = analytics.data?.generatedAt || new Date().toISOString();
  const zone = String(
    analytics.data?.recommendationRules?.settings.analytics.timezone ||
      "America/Sao_Paulo",
  );
  const wars = closedArchive(archive.data || [], at);
  const officialCount = wars.filter((w) => w.source === "DIRECT_API").length;
  const rawOffset = Number(url.get("offset") || 0);
  const offset =
    Number.isSafeInteger(rawOffset) && rawOffset >= 0
      ? Math.min(rawOffset, Math.max(0, Math.ceil(officialCount / 4) - 1) * 4)
      : 0;
  const selected = selectWars(wars, url.get("wars"), offset);
  const history = useQueries({
    queries: selected.map((w) => ({
      queryKey: ["war-detail", String(w.id)],
      queryFn: () => api<WarDetail>("/wars/" + w.id),
    })),
  });
  const queries = [archive, roster, events, analytics, ...history];
  const query = {
    isPending: queries.some((q) => q.isPending),
    error: queries.find((q) => q.error)?.error || null,
    refetch: () => queries.forEach((q) => void q.refetch()),
  };
  const details = history.flatMap((q) => (q.data ? [q.data] : []));
  const allRows = comparisonRows(
    details,
    roster.data || [],
    events.data || [],
    at,
    wars,
  );
  const rows = filterRows(allRows, url);
  const player = allRows.find((p) => p.tag === opened);
  const donor = analytics.data?.players.find((p) => p.tag === opened);
  const set = (key: string, value: string) =>
    setUrl((p) => {
      const next = new URLSearchParams(p);
      if (value) next.set(key, value);
      else next.delete(key);
      return next;
    });
  const page = (next: number) =>
    setUrl((p) => {
      const q = new URLSearchParams(p);
      q.delete("wars");
      q.set("offset", String(next));
      return q;
    });
  const control = (key: string, label: string, options: string[][]) => (
    <label>
      {label}
      <select
        value={url.get(key) || ""}
        onChange={(e) => set(key, e.target.value)}
        disabled={
          key === "sort" &&
          ["asc", "desc"].includes(url.get("performance") || "")
        }
      >
        {options.map(([value, name]) => (
          <option key={value} value={value}>
            {name}
          </option>
        ))}
      </select>
    </label>
  );
  const date = (value: string | null) => displayDate(value, zone);
  const label = (w: WarSummary) => warLabel(w, zone);
  const open = (tag: string) => {
    setOpened(tag);
    dialog.current?.showModal();
  };
  return (
    <>
      <Heading title="Comparativo de Guerras" eyebrow="ANÁLISE DA TRIPULAÇÃO" />
      <p>Análise de desempenho nas últimas quatro guerras</p>
      <QueryState query={query}>
        <section className="panel comparison-controls">
          <div className="toolbar">
            <button
              disabled={!offset && !url.has("wars")}
              onClick={() => page(Math.max(0, offset - 4))}
            >
              Período mais recente
            </button>
            <button
              disabled={offset + 4 >= officialCount}
              onClick={() => page(offset + 4)}
            >
              Quatro guerras anteriores
            </button>
            <button
              onClick={() =>
                setUrl((p) => {
                  const q = new URLSearchParams(p);
                  q.delete("wars");
                  q.delete("offset");
                  return q;
                })
              }
            >
              Últimas quatro
            </button>
            <details>
              <summary>
                Selecionar guerras históricas ({selected.length}/4)
              </summary>
              <div className="comparison-picker">
                {wars.map((w) => (
                  <label key={w.id}>
                    <input
                      type="checkbox"
                      checked={selected.some((s) => s.id === w.id)}
                      disabled={
                        selected.length >= 4 &&
                        !selected.some((s) => s.id === w.id)
                      }
                      onChange={(e) => {
                        const ids = selected
                          .map((s) => s.id)
                          .filter((id) => id !== w.id);
                        if (e.target.checked) ids.push(w.id);
                        // Explicit empty selection must not restore the default silently.
                        setUrl((p) => {
                          const q = new URLSearchParams(p);
                          q.set("wars", ids.join(","));
                          q.delete("offset");
                          return q;
                        });
                      }}
                    />
                    {label(w)} ·{" "}
                    {w.source === "DIRECT_API" ? "Oficial" : "Importação"}
                  </label>
                ))}
              </div>
            </details>
          </div>
          {selected.length < 4 && (
            <p role="status">
              Amostra limitada: {selected.length} de quatro guerras
              selecionadas. Não confirma desempenho completo em quatro guerras.
            </p>
          )}
          <p className="muted">
            A seleção padrão usa resultados oficiais encerrados. Datas no fuso{" "}
            {zone}. Publicação não representa o início da batalha.
          </p>
        </section>
        <div className="facts comparison-facts">
          {[
            ["Membros atuais analisados", rows.filter((p) => p.current).length],
            [
              "Meta na última guerra",
              rows.filter((p) => p.latest !== null && p.latest >= 1600).length,
            ],
            ["Duas consecutivas abaixo", rows.filter((p) => p.twoBelow).length],
            ["16/16 decks nas quatro", rows.filter((p) => p.fullFour).length],
            [
              "Histórico insuficiente",
              rows.filter((p) => p.insufficient).length,
            ],
          ].map(([name, value]) => (
            <div key={name}>
              <small>{name}</small>
              <strong>{value}</strong>
            </div>
          ))}
        </div>
        <p className="muted">
          Indicadores sobre os {rows.length} jogadores filtrados. Elenco de
          referência: última coleta completa; dados ausentes não contam como
          zero.
        </p>
        <div className="toolbar">
          <label>
            Nome ou tag
            <input
              type="search"
              value={url.get("q") || ""}
              onChange={(e) => set("q", e.target.value)}
            />
          </label>
          <label>
            <input
              type="checkbox"
              checked={url.get("former") === "1"}
              onChange={(e) => set("former", e.target.checked ? "1" : "")}
            />{" "}
            Incluir ex-membros
          </label>
          {control("rule", "Cumprimento das regras", [
            ["", "Todos"],
            ["target", "Meta cumprida na última guerra"],
            ["below", "Abaixo de 1.600 na última guerra"],
            ["repeat", "Duas consecutivas elegíveis abaixo de 1.600"],
            ["zero", "Zero ataques confirmado na última"],
            ["decks", "Menos de 16 decks na última"],
            ["full", "16/16 decks nas quatro"],
            ["missing", "Histórico insuficiente"],
            ["unrecorded", "Vínculo comprovado · resultado ausente"],
          ])}
          {control("sort", "Ordenação", [
            ["", "Maior média"],
            ["averageAsc", "Menor média"],
            ["latestDesc", "Maior pontuação na última"],
            ["latestAsc", "Menor pontuação na última"],
            ["deltaDesc", "Maior evolução"],
            ["deltaAsc", "Maior queda"],
            ["name", "Nome em ordem alfabética"],
          ])}
          <button
            onClick={() =>
              setUrl((p) => {
                const q = new URLSearchParams();
                for (const k of ["wars", "offset"])
                  if (p.has(k)) q.set(k, p.get(k)!);
                return q;
              })
            }
          >
            Limpar filtros
          </button>
        </div>
        <details open={!!url.get("performance")}>
          <summary>Desempenho e critérios</summary>
          <div className="toolbar">
            {control("performance", "Desempenho", [
              ["", "Todos"],
              ["asc", "Pontuação crescente na última"],
              ["desc", "Pontuação decrescente na última"],
              ["high", "Média igual ou superior a 2.400"],
              ["low", "Média abaixo de 1.600"],
            ])}
          </div>
          <p className="muted">
            Média dos resultados oficiais registrados, mesmo sem vínculo
            integral comprovado; a amostra acompanha o valor. 2.400 pontos é um
            sinal para avaliação, não garante promoção: doações, participação e
            contexto também importam. Referência de 16 decks (quatro por dia,
            quinta a domingo), sem presumir disponibilidade integral. Os filtros
            de pontos e decks mostram registros; somente a revisão por
            reincidência exige vínculo elegível. Ordenação por pontuação em
            Desempenho prevalece sobre Ordenação.
          </p>
        </details>
        <p role="status">
          {rows.length} resultados · {allRows.length} jogadores no universo
          selecionado
        </p>
        {!selected.length ? (
          <Empty>
            Nenhuma guerra encerrada selecionada. Use o seletor ou restaure as
            últimas quatro.
          </Empty>
        ) : !rows.length ? (
          <Empty>
            Nenhum jogador atende aos filtros. Tente limpar a seleção de
            filtros.
          </Empty>
        ) : (
          <div className="table-scroll comparison-table">
            <table>
              <caption>
                Resultados da guerra mais recente para a mais antiga · datas de
                publicação quando o encerramento não é informado
              </caption>
              <thead>
                <tr>
                  <th>Jogador</th>
                  {selected.map((w, i) => (
                    <th
                      key={w.id}
                      className={i === 0 ? "comparison-latest" : ""}
                    >
                      <Link
                        className="comparison-war-date"
                        to={"/guerras/" + w.id}
                      >
                        {date(w.endDate || w.publishedAt).slice(0, 5)}
                      </Link>
                      <small>
                        {
                          [
                            "Última guerra",
                            "Anterior",
                            "Há 2 guerras",
                            "Há 3 guerras",
                          ][i]
                        }
                      </small>
                      <details className="comparison-metadata">
                        <summary
                          aria-label={
                            "Informações da guerra " + date(w.publishedAt)
                          }
                        >
                          Sobre a data
                        </summary>
                        <div>
                          <b>
                            {w.endDate
                              ? "Data de encerramento"
                              : "Data de publicação"}
                          </b>
                          <p>
                            Publicação: {date(w.publishedAt)} · ID {w.id}
                          </p>
                          <p>
                            Temporada {w.seasonId} · seção {w.sectionIndex}
                          </p>
                          <p>
                            Período: {date(w.startDate)} → {date(w.endDate)}
                          </p>
                          <p>
                            {w.source === "DIRECT_API"
                              ? "Resultado oficial"
                              : "Importação · cobertura parcial"}
                          </p>
                        </div>
                      </details>
                    </th>
                  ))}
                  <th>Média</th>
                  <th>Situação</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((p) => (
                  <tr key={p.tag} onClick={() => open(p.tag)}>
                    <th scope="row" className="comparison-identity">
                      <Link
                        className="comparison-name"
                        to={"/membros/" + encodeURIComponent(p.tag)}
                        onClick={(e) => e.stopPropagation()}
                      >
                        {p.name}
                      </Link>
                      <small className="comparison-tag">{p.tag}</small>
                      <span className="comparison-membership">
                        {p.current ? "Membro atual" : "Ex-membro"}
                      </span>
                    </th>
                    {p.cells.map((c, i) => (
                      <td
                        key={c.war.id}
                        className={i === 0 ? "comparison-latest" : ""}
                      >
                        <div className="comparison-score">
                          <strong>
                            {c.points === null ? "—" : format(c.points)}
                          </strong>
                          {c.points !== null && <span>pts</span>}
                        </div>
                        <button
                          className={"comparison-result " + c.tone}
                          onClick={(e) => {
                            e.stopPropagation();
                            open(p.tag);
                          }}
                          aria-label={
                            c.reason +
                            " · " +
                            date(c.war.publishedAt) +
                            " · Ver detalhes de " +
                            p.name
                          }
                        >
                          {
                            {
                              TARGET: "✓ Meta",
                              BELOW_TARGET: "Abaixo da meta",
                              MISSING_RESULT: "Conferir registro",
                              BEFORE_ENTRY: "Antes da entrada",
                              UNKNOWN_MEMBERSHIP: "Vínculo incerto",
                              PARTIAL: "Parcial",
                              UNCONFIRMED_OBLIGATION: "Ver contexto",
                            }[c.state]
                          }
                        </button>
                        <small className="comparison-decks">
                          {c.decks === null
                            ? "Decks não informados"
                            : c.official
                              ? c.decks === 16
                                ? "16 decks registrados"
                                : " " + format(c.decks) + " decks registrados"
                              : format(c.decks) + " decks · parcial"}
                        </small>
                      </td>
                    ))}
                    <td className="comparison-average">
                      <strong>
                        {p.average === null ? "—" : format(p.average)}
                        <span>{p.average !== null ? " pts" : ""}</span>
                      </strong>
                      <small>
                        {p.sample
                          ? `${p.cells.filter((c) => c.valid && c.points! >= 1600).length}/${p.sample} metas cumpridas`
                          : "Metas: sem amostra"}
                      </small>
                      <small>
                        {p.sample}/{selected.length} com pontuação oficial
                      </small>
                      {p.delta !== null && (
                        <span
                          className="comparison-delta"
                          tabIndex={0}
                          aria-label={`Variação de ${format(p.delta)} pontos entre a última guerra e a imediatamente anterior`}
                          title="Última guerra menos a imediatamente anterior; apenas resultados oficiais comparáveis"
                        >
                          {p.delta > 0 ? "↑ +" : p.delta < 0 ? "↓ " : "→ "}
                          {format(p.delta)} pts
                        </span>
                      )}
                    </td>
                    <td>
                      <button
                        className={
                          "comparison-situation " + (p.twoBelow ? "warn" : "")
                        }
                        aria-haspopup="dialog"
                        onClick={(e) => {
                          e.stopPropagation();
                          open(p.tag);
                        }}
                        aria-label={
                          "Detalhes de " + p.name + ": " + p.situation
                        }
                      >
                        {p.twoBelow
                          ? "Reincidência"
                          : p.situation.startsWith("Conferir")
                            ? "Conferir registro"
                            : p.situation.startsWith("Histórico")
                              ? "Histórico parcial"
                              : p.situation.startsWith("Dados")
                                ? "Dados insuficientes"
                                : p.situation.startsWith("Guerras anteriores")
                                  ? "Antes da entrada"
                                  : p.situation === "Bom desempenho"
                                    ? "Regular"
                                    : p.situation ===
                                        "Participação integral não comprovada"
                                      ? "Ver participação"
                                      : p.situation}
                        <span aria-hidden="true"> ↗</span>
                      </button>
                      {p.fullFour && <small>16 decks nas quatro</small>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <p className="callout">
          Revisão é um indicador para análise humana. Ausência de registro,
          entrada recente, reingresso ou vínculo não comprovado não são faltas.
          Sem início oficial, exige-se vínculo observado sete dias antes da
          publicação; seleções com lacunas não confirmam reincidência.
        </p>
      </QueryState>
      <dialog
        ref={dialog}
        className="metric-dialog comparison-dialog"
        aria-labelledby="comparison-player-title"
        onClose={() => setOpened(null)}
      >
        <header className="metric-dialog-header">
          <h2 id="comparison-player-title">
            {player?.name || "Detalhes do jogador"}
          </h2>
          <button
            autoFocus
            className="dialog-close"
            aria-label="Fechar detalhes"
            onClick={() => dialog.current?.close()}
          >
            ×
          </button>
        </header>
        {player && (
          <div className="metric-dialog-body">
            <p>
              {player.tag} · {player.current ? "Membro atual" : "Ex-membro"} ·{" "}
              {player.situation}
            </p>
            <Link to={"/membros/" + encodeURIComponent(player.tag)}>
              Abrir perfil completo
            </Link>
            <p>
              Média: {format(player.average)} · Total: {format(player.total)}{" "}
              pts · {player.sample}/{selected.length} guerras com pontuação
              oficial.
            </p>
            <p>
              {player.delta === null
                ? "Evolução: dados insuficientes entre guerras consecutivas."
                : player.delta > 0
                  ? "Pontuação em evolução."
                  : player.delta < 0
                    ? "Queda na pontuação."
                    : "Pontuação estável."}
            </p>
            <ComparisonChart player={player} zone={zone} />
            {player.cells.map((c, i) => (
              <section className="panel" key={c.war.id}>
                <h3>{label(c.war)}</h3>
                <p>
                  {format(c.points)} pts ·{" "}
                  {c.decks === null
                    ? "Decks desconhecidos"
                    : c.decks + "/16 decks usados"}{" "}
                  · {c.reason}
                </p>
                <p>
                  {c.decks === 16
                    ? "Referência de participação completa atingida."
                    : c.decks === null
                      ? "Participação: dados insuficientes."
                      : "Referência de 16 decks não atingida; disponibilidade individual não comprovada."}
                </p>
                {i + 1 < player.cells.length && (
                  <p>
                    Variação frente à coluna anterior no tempo:{" "}
                    {c.valid && player.cells[i + 1].valid
                      ? format(c.points! - player.cells[i + 1].points!) +
                        " pts (entre as guerras selecionadas)"
                      : "Dados insuficientes"}
                  </p>
                )}
                {c.record && (
                  <WarEvidence
                    war={c.war}
                    participant={{
                      decksUsedToday: null,
                      boatAttacks: null,
                      repairPoints: null,
                      battlesPlayed: null,
                      wins: null,
                      losses: null,
                      ...c.record,
                    }}
                  />
                )}
              </section>
            ))}
            <h3>Doações semanais disponíveis</h3>
            <p className="muted">
              Contexto complementar, sem recalcular premiações. Períodos das
              doações são independentes das datas de publicação das guerras.
            </p>
            {donor?.donations.length ? (
              donor.donations
                .slice(-6)
                .reverse()
                .map((d) => (
                  <section key={d.week}>
                    <h4>Semana de {d.week}</h4>
                    <MetricView
                      name="Doações observadas"
                      metric={d.observedDonations}
                    />
                    {d.lastCounter && (
                      <MetricView
                        name="Último contador coletado"
                        metric={d.lastCounter}
                      />
                    )}
                  </section>
                ))
            ) : (
              <p>Não há coletas de doações disponíveis.</p>
            )}
          </div>
        )}
      </dialog>
    </>
  );
}
function ComparisonChart({
  player,
  zone,
}: {
  player: ComparisonRow;
  zone: string;
}) {
  if (player.sample < 2)
    return (
      <p>
        Gráfico indisponível: são necessárias duas guerras com pontuação
        oficial.
      </p>
    );
  const cells = [...player.cells].reverse();
  const max = Math.max(
    3200,
    ...cells.filter((c) => c.valid).map((c) => c.points!),
  );
  const y = (n: number) => 155 - (n / max) * 120;
  return (
    <svg
      viewBox="0 0 520 190"
      role="img"
      aria-label="Evolução dos pontos nas guerras selecionadas, da mais antiga para a recente; datas de encerramento ou publicação; meta de 1.600"
      className="comparison-chart"
    >
      <line
        x1="0"
        x2="520"
        y1={y(1600)}
        y2={y(1600)}
        stroke="currentColor"
        strokeDasharray="4 4"
      />
      <text x="0" y={y(1600) - 5} fill="currentColor">
        Meta 1.600
      </text>
      {cells.map((c, i) => (
        <g key={c.war.id}>
          {c.valid ? (
            <>
              <rect
                x={50 + i * 120}
                y={y(c.points!)}
                width="35"
                height={Math.max(2, 155 - y(c.points!))}
                fill={c.points! >= 1600 ? "#8cebd5" : "#dec08a"}
              />
              <text x={45 + i * 120} y={y(c.points!) - 8} fill="currentColor">
                {c.points}
              </text>
            </>
          ) : (
            <text x={45 + i * 120} y="145" fill="currentColor">
              Sem dados
            </text>
          )}
          <text x={40 + i * 120} y="180" fill="currentColor">
            {displayDate(c.war.endDate || c.war.publishedAt, zone).slice(0, 5)}
          </text>
        </g>
      ))}
    </svg>
  );
}
