import { useAdmin } from "../lib/auth";
import { LeaderNotes } from "../components/LeaderNotes";
import { useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import {
  useApi,
  date,
  format,
  tagPath,
  num,
  type Player,
  type Report,
  type Award,
} from "../lib/api";
import { label, title } from "../lib/labels";
import {
  Heading,
  QueryState,
  Metrics,
  MetricView,
  Badge,
  Person,
  Table,
  Empty,
  Paged,
} from "../components/ui";
export function Members() {
  const q = useApi<Report>("/analytics");
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("");
  const [role, setRole] = useState("");
  const [sort, setSort] = useState("recentAverage");
  const [former, setFormer] = useState(false);
  const players = (q.data?.players || [])
    .filter(
      (p) =>
        (former || p.currentMember) &&
        (p.name + p.tag).toLowerCase().includes(search.toLowerCase()) &&
        (!status || p.operationalStatus.value === status) &&
        (!role || p.role === role),
    )
    .sort(
      (a, b) =>
        (num(b.metrics[sort]) ?? -Infinity) -
        (num(a.metrics[sort]) ?? -Infinity),
    );
  return (
    <>
      <Heading eyebrow="TRIPULAÇÃO" title="Cada membro, uma história" />
      <p className="lede">
        Consulte evidências, acompanhe a consistência e conheça o contexto antes
        de decidir.
      </p>
      <div className="toolbar">
        <input
          aria-label="Buscar membro"
          placeholder="Buscar nome ou tag…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <select
          aria-label="Filtrar status"
          value={status}
          onChange={(e) => setStatus(e.target.value)}
        >
          <option value="">Todos os status</option>
          {["MANTER", "OBSERVAR", "REVISAR"].map((s) => (
            <option key={s}>{s}</option>
          ))}
        </select>
        <select
          aria-label="Filtrar cargo"
          value={role}
          onChange={(e) => setRole(e.target.value)}
        >
          <option value="">Todos os cargos</option>
          <option value="member">Membro</option>
          <option value="elder">Ancião</option>
          <option value="coLeader">Colíder</option>
          <option value="leader">Líder</option>
        </select>
        <select
          aria-label="Ordenar membros"
          value={sort}
          onChange={(e) => setSort(e.target.value)}
        >
          {[
            "recentAverage",
            "historicalAverage",
            "frotaScore",
            "weeklyDonations",
            "offlineDays",
            "trophies",
            "recentDecks",
            "recentPoints",
          ].map((k) => (
            <option key={k} value={k}>
              {label(k)}
            </option>
          ))}
        </select>
        <label>
          <input
            type="checkbox"
            checked={former}
            onChange={(e) => setFormer(e.target.checked)}
          />{" "}
          Incluir ex-membros
        </label>
      </div>
      <QueryState query={q}>
        <p className="muted">{players.length} membros encontrados</p>
        <Table
          headers={[
            "Membro",
            "Status",
            "Média • 4 guerras",
            "Participação",
            "Frota Score",
            "Doações",
          ]}
        >
          {players.map((p) => (
            <tr key={p.tag}>
              <td>
                <Person player={p} />
                {!p.currentMember && <small>Ex-membro</small>}
              </td>
              <td>
                <Badge value={p.operationalStatus.value} />
              </td>
              {[
                "recentAverage",
                "recentParticipation",
                "frotaScore",
                "weeklyDonations",
              ].map((k) => (
                <td key={k}>
                  <MetricView compact name={label(k)} metric={p.metrics[k]} />
                </td>
              ))}
            </tr>
          ))}
        </Table>
        {!players.length && (
          <Empty>Nenhum membro corresponde aos filtros.</Empty>
        )}
      </QueryState>
    </>
  );
}
export function PlayerProfile() {
  const admin = useAdmin();
  const [params] = useSearchParams();
  const { tag = "" } = useParams();
  const q = useApi<Player>("/analytics/players/" + tagPath(tag));
  const [tab, setTab] = useState(
    params.get("tab") === "notas" && admin
      ? "Notas da liderança"
      : "Visão geral",
  );
  const p = q.data;
  return (
    <>
      <Link className="back" to="/membros">
        ← Tripulação
      </Link>
      <QueryState query={q}>
        {p && (
          <>
            <Heading
              eyebrow={
                p.tag + " · " + (p.currentMember ? "MEMBRO ATUAL" : "EX-MEMBRO")
              }
              title={p.name}
            >
              <Badge value={p.operationalStatus.value} />
              <Link
                className="button"
                to={"/comparar?tags=" + encodeURIComponent(p.tag)}
              >
                Comparar membro
              </Link>
            </Heading>
            <div className="tabs">
              {[
                "Visão geral",
                "Guerras",
                "Linha do tempo",
                "Doações",
                "Premiações",
                "Todas as métricas",
                ...(admin ? ["Notas da liderança"] : []),
              ].map((t) => (
                <button
                  key={t}
                  aria-pressed={tab === t}
                  className={tab === t ? "active" : ""}
                  onClick={() => setTab(t)}
                >
                  {t}
                </button>
              ))}
            </div>
            {admin && tab === "Notas da liderança" && (
              <LeaderNotes key={p.tag} tag={p.tag} />
            )}
            {tab === "Visão geral" && (
              <>
                <Metrics
                  metrics={p.metrics}
                  keys={[
                    "recentAverage",
                    "historicalAverage",
                    "recentParticipation",
                    "frotaScore",
                    "recentDecks",
                    "completeStandardWars",
                    "weeklyDonations",
                    "offlineDays",
                    "lastKnownActivity",
                    "trophies",
                    "observedTenureDays",
                    "recentTrendPoints",
                  ]}
                />
                <h2>Contexto para decidir</h2>
                <div className="metrics">
                  {(
                    [
                      "operationalStatus",
                      "historicalContext",
                      "participation",
                      "elderCandidate",
                    ] as const
                  ).map((k) => (
                    <MetricView key={k} name={label(k)} metric={p[k]} />
                  ))}
                </div>
                <div className="callout">
                  <b>Motivos do status</b>
                  <p>{p.operationalStatus.reason}</p>
                  <b>Candidatura a Ancião</b>
                  <p>{p.elderCandidate.reason}</p>
                </div>
                <h2>Composição do Frota Score</h2>
                <Metrics
                  metrics={p.metrics}
                  keys={[
                    "frotaScore",
                    "frotaScoreWar",
                    "frotaScoreDecks",
                    "frotaScoreDonations",
                    "recentAlerts",
                  ]}
                />
                <WarHistory player={p} />
              </>
            )}
            {tab === "Guerras" && <WarHistory player={p} />}
            {tab === "Todas as métricas" && (
              <>
                <p className="lede">
                  Abra qualquer indicador para consultar período, origem, dados
                  usados e motivo.
                </p>
                <Metrics metrics={p.metrics} />
              </>
            )}
            {tab === "Doações" && <Donations player={p} />}
            {tab === "Premiações" && (
              <Paged<Award>
                path={"/recommendations/players/" + tagPath(tag) + "/awards"}
                empty="Nenhuma premiação formalizada para este membro."
                render={(items) => (
                  <div className="timeline">
                    {items.map((a) => (
                      <article key={a.week + a.title}>
                        <small>Semana de {date(a.week)}</small>
                        <h3>{title(a.title)}</h3>
                        <MetricView
                          name="Evidências da premiação"
                          metric={a.evidence.result}
                        />
                        <Link to={"/recomendacoes/" + a.evaluationId}>
                          Abrir avaliação original
                        </Link>
                      </article>
                    ))}
                  </div>
                )}
              />
            )}
            {tab === "Linha do tempo" && (
              <>
                <h2>Guerras e doações observadas</h2>
                <div className="timeline">
                  {[
                    ...p.wars.map((w) => ({
                      at: w.publishedAt,
                      key: "w" + w.warId,
                      body: (
                        <>
                          <Link to={"/guerras/" + w.warId}>
                            <h3>
                              Guerra · temporada {w.seasonId} / seção{" "}
                              {w.sectionIndex}
                            </h3>
                          </Link>
                          <div className="metrics">
                            <MetricView name="Pontos" metric={w.points} />
                            <MetricView
                              name="Decks usados"
                              metric={w.decksUsed}
                            />
                            <MetricView name="Posição" metric={w.rank} />
                          </div>
                        </>
                      ),
                    })),
                    ...p.donations.map((d) => ({
                      at: d.week,
                      key: "d" + d.week,
                      body: (
                        <>
                          <h3>Doações da semana</h3>
                          <MetricView
                            name="Doações observadas"
                            metric={d.observedDonations}
                          />
                          <small>
                            {d.snapshots} snapshots · {d.detectedResets} resets
                          </small>
                        </>
                      ),
                    })),
                  ]
                    .sort((a, b) => b.at.localeCompare(a.at))
                    .map((e) => (
                      <article key={e.key}>
                        <small>{date(e.at)}</small>
                        {e.body}
                      </article>
                    ))}
                </div>
                <h2>Avaliações e alterações de status</h2>
                <Paged<{
                  evaluationId: string;
                  createdAt: string;
                  week: string;
                  status: string;
                  evidence: Player;
                }>
                  path={"/recommendations/players/" + tagPath(tag) + "/history"}
                  render={(items) => (
                    <div className="timeline">
                      {items.map((s) => (
                        <article key={s.evaluationId}>
                          <small>
                            Avaliação registrada em {date(s.createdAt)} · semana{" "}
                            {date(s.week)}
                          </small>
                          <p>
                            <Badge value={s.status} />
                          </p>
                          <p>{s.evidence.operationalStatus.reason}</p>
                          <MetricView
                            name="Candidatura nesta avaliação"
                            metric={s.evidence.elderCandidate}
                          />
                          <Link to={"/recomendacoes/" + s.evaluationId}>
                            Ver evidências congeladas
                          </Link>
                        </article>
                      ))}
                    </div>
                  )}
                />
                <h2>Entradas, saídas e cargos</h2>
                <Paged<{
                  id: number;
                  type: string;
                  observedAt: string;
                  previousRole: string;
                  currentRole: string;
                }>
                  path={"/players/" + tagPath(tag) + "/events"}
                  render={(items) => (
                    <div className="timeline">
                      {items.map((e) => (
                        <article key={e.id}>
                          <small>{date(e.observedAt)}</small>
                          <h3>{format(e.type)}</h3>
                          <p>
                            {e.previousRole || "—"} → {e.currentRole || "—"}
                          </p>
                        </article>
                      ))}
                    </div>
                  )}
                />
                <h2>Snapshots de membro</h2>
                <Paged<Record<string, unknown>>
                  path={"/players/" + tagPath(tag) + "/history"}
                  render={(items) => (
                    <Table
                      headers={[
                        "Captura",
                        "Troféus",
                        "Contador de doações",
                        "Última atividade oficial",
                        "Cargo",
                      ]}
                    >
                      {items.map((s, i) => (
                        <tr key={i}>
                          <td>{date(String(s.capturedAt))}</td>
                          <td>{format(s.trophies)}</td>
                          <td>{format(s.donations)}</td>
                          <td>{date(s.apiLastSeen as string)}</td>
                          <td>{format(s.role)}</td>
                        </tr>
                      ))}
                    </Table>
                  )}
                />
              </>
            )}
          </>
        )}
      </QueryState>
    </>
  );
}
export function WarHistory({ player: p }: { player: Player }) {
  return (
    <>
      <h2>
        Histórico completo de guerras{" "}
        <span className="muted">/ {p.wars.length}</span>
      </h2>
      <p className="muted">
        Registro ausente significa desconhecido. A meta de 16 decks é uma
        referência, não a capacidade individual confirmada.
      </p>
      {!p.wars.length ? (
        <Empty />
      ) : (
        <>
          <div
            className="spark-bars"
            aria-label="Evolução da pontuação por guerra"
          >
            {[...p.wars].reverse().map((w) => (
              <Link
                key={w.warId}
                title={
                  date(w.publishedAt) +
                  ": " +
                  format(w.points.value) +
                  " pontos"
                }
                to={"/guerras/" + w.warId}
              >
                <span
                  className={num(w.points) === null ? "unknown-bar" : ""}
                  style={{
                    height:
                      (num(w.points) === null
                        ? 4
                        : Math.max(
                            4,
                            (100 * (num(w.points) || 0)) /
                              Math.max(
                                1,
                                ...p.wars.map((x) => num(x.points) || 0),
                              ),
                          )) + "%",
                  }}
                />
                <small>{w.sectionIndex}</small>
              </Link>
            ))}
          </div>
          <p className="muted chart-note">
            Barras: pontuação observada. Contorno tracejado: registro
            desconhecido. Valores e evidências na tabela abaixo.
          </p>
          <Table
            headers={[
              "Guerra / publicação",
              "Pontos",
              "Decks",
              "Participação",
              "Posição",
            ]}
          >
            {p.wars.map((w) => (
              <tr key={w.warId}>
                <td>
                  <Link to={"/guerras/" + w.warId}>
                    Temporada {w.seasonId} · seção {w.sectionIndex}
                  </Link>
                  <small>{date(w.publishedAt)}</small>
                </td>
                {(
                  ["points", "decksUsed", "participation", "rank"] as const
                ).map((k) => (
                  <td key={k}>
                    <MetricView compact name={label(k)} metric={w[k]} />
                  </td>
                ))}
              </tr>
            ))}
          </Table>
        </>
      )}
    </>
  );
}
export function Donations({ player: p }: { player: Player }) {
  return (
    <>
      <h2>Doações por período</h2>
      <p className="lede">
        Diferenças entre snapshots, com resets detectados. Os valores são
        limites inferiores observados; períodos incompletos permanecem
        sinalizados.
      </p>
      <Table
        headers={[
          "Semana",
          "Doações observadas",
          "Snapshots",
          "Resets detectados",
        ]}
      >
        {p.donations.map((d) => (
          <tr key={d.week}>
            <td>{date(d.week)}</td>
            <td>
              <MetricView compact name="Doações" metric={d.observedDonations} />
            </td>
            <td>{d.snapshots}</td>
            <td>{d.detectedResets}</td>
          </tr>
        ))}
      </Table>
      {!p.donations.length && (
        <Empty>Dados insuficientes: aguardando snapshots comparáveis.</Empty>
      )}
    </>
  );
}
