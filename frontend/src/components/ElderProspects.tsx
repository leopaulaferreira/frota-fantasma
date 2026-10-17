import { Link } from "react-router-dom";
import { useAdmin } from "../lib/auth";
import {
  classifyProspect,
  fourKnown,
  numeric,
  prospectBands,
} from "../lib/prospects";
import type { ActiveNotes } from "../lib/leadership";
import { CandidacyWatch } from "./CandidacyWatch";
import { useState } from "react";
import {
  type Player,
  type Report,
  type Metric,
  date,
  format,
  useApi,
} from "../lib/api";
import { Badge, Empty, MetricView, Person, Table } from "./ui";

export function recentAverage(player: Player): number | null {
  const value = player.metrics.recentAverage?.value;
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

export function orderedProspects(players: Player[]) {
  return players
    .filter((p) => p.currentMember && p.role === "member")
    .sort((a, b) => {
      const av = recentAverage(a),
        bv = recentAverage(b);
      if (av === null && bv !== null) return 1;
      if (bv === null && av !== null) return -1;
      return (bv ?? 0) - (av ?? 0) || a.tag.localeCompare(b.tag);
    });
}

function objectiveDistance(source: Metric, objective: number): Metric {
  const average = source.value;
  return {
    ...source,
    value: typeof average === "number" ? average - objective : null,
    origin: "DERIVED",
    reason: `Média recente menos o objetivo de referência (${objective} pontos). Valor positivo indica acima; negativo indica abaixo. Esta comparação não determina elegibilidade para promoção. ${source.reason}`,
    dataUsed: [...source.dataUsed, `referenceObjective=${objective}`],
  };
}

export function ElderProspects({ report }: { report: Report }) {
  const admin = useAdmin();
  return admin ? (
    <PrivateProspects report={report} />
  ) : (
    <ProspectList report={report} />
  );
}
function PrivateProspects({ report }: { report: Report }) {
  const notes = useApi<ActiveNotes>("/leadership/notes/active", 60000);
  return (
    <>
      {notes.error && (
        <p role="alert">
          Não foi possível consultar acompanhamentos privados.{" "}
          <button onClick={() => notes.refetch()}>Tentar novamente</button>
        </p>
      )}
      <ProspectList report={report} admin notes={notes.data} />
    </>
  );
}
function ProspectList({
  report,
  admin = false,
  notes,
}: {
  report: Report;
  admin?: boolean;
  notes?: ActiveNotes;
}) {
  const [objectiveInput, setObjectiveInput] = useState("2400");
  const [toleranceInput, setToleranceInput] = useState("5");
  const [band, setBand] = useState("");
  const [sample, setSample] = useState("");
  const [search, setSearch] = useState("");
  const [sort, setSort] = useState("recentAverage");
  const [watched, setWatched] = useState(false);
  const [page, setPage] = useState(0);
  const objective = Number(objectiveInput),
    tolerance = Number(toleranceInput);
  const validObjective =
    objectiveInput.trim() !== "" && Number.isFinite(objective) && objective > 0;
  const validTolerance =
    toleranceInput.trim() !== "" &&
    Number.isFinite(tolerance) &&
    tolerance >= 0 &&
    tolerance <= 20;
  const watches = (tag: string) =>
    notes?.notes.filter(
      (n) =>
        n.playerTag === tag && n.kind === "CANDIDACY" && n.status === "ACTIVE",
    ) || [];
  const all = orderedProspects(report.players).map((p) => ({
    p,
    full: fourKnown(p),
    assessment: classifyProspect(
      p,
      report,
      validObjective ? objective : NaN,
      validTolerance ? tolerance : NaN,
    ),
  }));
  const players = all
    .filter(
      ({ p, full, assessment }) =>
        (!band || assessment.band === band) &&
        (!sample || (sample === "full" ? full : !full)) &&
        (!watched || watches(p.tag).length > 0) &&
        (p.name + p.tag).toLowerCase().includes(search.toLowerCase()),
    )
    .sort(
      (a, b) =>
        Number(b.full) - Number(a.full) ||
        (numeric(b.p.metrics[sort]) ?? -Infinity) -
          (numeric(a.p.metrics[sort]) ?? -Infinity) ||
        a.p.tag.localeCompare(b.p.tag),
    );
  const currentPage = Math.min(
    page,
    Math.max(0, Math.ceil(players.length / 10) - 1),
  );
  return (
    <section
      aria-labelledby="elder-prospects-title"
      className="elder-prospects"
    >
      <h2 id="elder-prospects-title">Possíveis candidatos a Ancião</h2>
      <p className="muted">
        Todos os membros sem cargo continuam acessíveis. Primeiro aparecem os
        membros com quatro pontuações conhecidas; depois, o histórico em
        formação. A ordenação escolhida vale dentro de cada grupo.
      </p>
      <div className="toolbar">
        <label htmlFor="elder-objective">Objetivo de referência (pontos)</label>
        <input
          id="elder-objective"
          type="number"
          min="1"
          step="any"
          value={objectiveInput}
          aria-invalid={!validObjective}
          onChange={(e) => {
            setObjectiveInput(e.target.value);
            setPage(0);
          }}
        />
        <label>
          Faixa próxima (%)
          <input
            type="number"
            min="0"
            max="20"
            step="any"
            value={toleranceInput}
            aria-invalid={!validTolerance}
            onChange={(e) => {
              setToleranceInput(e.target.value);
              setPage(0);
            }}
          />
        </label>
        <input
          aria-label="Buscar candidato"
          placeholder="Nome ou tag…"
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPage(0);
          }}
        />
        <select
          aria-label="Filtrar sinal de candidatura"
          value={band}
          onChange={(e) => {
            setBand(e.target.value);
            setPage(0);
          }}
        >
          <option value="">Todos os sinais</option>
          {Object.entries(prospectBands).map(([k, v]) => (
            <option key={k} value={k}>
              {v} ({all.filter((r) => r.assessment.band === k).length})
            </option>
          ))}
        </select>
        <select
          aria-label="Filtrar amostra de candidatura"
          value={sample}
          onChange={(e) => {
            setSample(e.target.value);
            setPage(0);
          }}
        >
          <option value="">Todas as amostras</option>
          <option value="full">Quatro pontuações conhecidas</option>
          <option value="partial">Histórico em formação</option>
        </select>
        <select
          aria-label="Ordenar candidatos"
          value={sort}
          onChange={(e) => {
            setSort(e.target.value);
            setPage(0);
          }}
        >
          <option value="recentAverage">Maior média recente</option>
          <option value="completeStandardWars">
            Mais guerras com 16 decks
          </option>
          <option value="recentDecks">Mais decks usados</option>
          <option value="weeklyDonations">Mais doações observadas</option>
          <option value="recentTrendPoints">Maior evolução recente</option>
        </select>
        {admin && (
          <label>
            <input
              type="checkbox"
              checked={watched}
              disabled={!notes}
              onChange={(e) => {
                setWatched(e.target.checked);
                setPage(0);
              }}
            />
            Somente acompanhados para Ancião
          </label>
        )}
      </div>
      {(!validObjective || !validTolerance) && (
        <p role="alert">
          Informe um objetivo maior que zero e uma faixa de 0% a 20%.
        </p>
      )}
      <p className="muted">
        Verde: objetivo atingido e critérios atuais atendidos. Amarelo:
        pontuação próxima ou acima, com pontos a desenvolver. Azul: pontuação
        abaixo da faixa. Cinza: dados insuficientes para a avaliação completa.
        São sinais de candidatura, separados de MANTER/OBSERVAR/REVISAR.
      </p>
      <p className="muted">
        Referências desta consulta; não alteram regras salvas. Doações: semana
        de {date(report.week)}. As coletas podem ser incompletas. Indicadores
        abrem período, amostra e evidências.
      </p>
      <p>{players.length} membros nesta seleção.</p>
      <Table
        headers={[
          "Membro / candidatura",
          "Quatro últimas guerras",
          "Média / objetivo",
          "Participação e decks",
          "Doações / tempo",
          "Contexto e acompanhamento",
        ]}
      >
        {players
          .slice(currentPage * 10, currentPage * 10 + 10)
          .map(({ p, full, assessment }) => {
            const average = recentAverage(p),
              metric = p.metrics.recentAverage;
            const diff =
              average === null || !validObjective ? null : average - objective;
            return (
              <tr key={p.tag}>
                <td className="prospect-context">
                  <Person player={p} />
                  <span className={`badge prospect-${assessment.band}`}>
                    {prospectBands[assessment.band]}
                  </span>
                  <p>
                    {full ? "4 pontuações conhecidas" : "Histórico em formação"}
                  </p>
                  <details>
                    <summary>Por que recebeu esta sinalização?</summary>
                    <ul>
                      {assessment.reasons.map((r, i) => (
                        <li key={i}>{r}</li>
                      ))}
                    </ul>
                    <MetricView
                      compact
                      name="Candidatura pelas regras atuais"
                      metric={p.elderCandidate}
                    />
                  </details>
                </td>
                <td>
                  <small>Da mais recente à mais antiga</small>
                  <ol className="prospect-war-list">
                    {Array.from({ length: 4 }, (_, i) => {
                      const w = p.wars[i];
                      return (
                        <li key={w?.warId ?? `missing-${i}`}>
                          {w ? (
                            <>
                              <Link to={`/guerras/${w.warId}`}>
                                Publicada em {date(w.publishedAt)}
                              </Link>
                              <br />
                              {format(w.points.value)} pontos ·{" "}
                              {format(w.decksUsed.value)} decks
                            </>
                          ) : (
                            "Guerra indisponível na janela"
                          )}
                        </li>
                      );
                    })}
                  </ol>
                  <MetricView
                    compact
                    name="Evolução: duas últimas vs. duas anteriores"
                    metric={p.metrics.recentTrendPoints}
                  />
                </td>
                <td>
                  <MetricView
                    compact
                    name="Média das últimas 4 guerras"
                    metric={metric}
                  />
                  <small>
                    {metric?.warsConsidered ?? 0} pontuações conhecidas / até 4
                    guerras
                  </small>
                  {metric &&
                    (metric.insufficientSample ||
                      metric.incompletePeriod ||
                      !full) && <p>Amostra limitada: comparar com cautela.</p>}
                  <p>
                    <strong>
                      {diff === null
                        ? "Dados insuficientes"
                        : diff === 0
                          ? "No objetivo"
                          : `${format(Math.abs(diff))} pontos ${diff > 0 ? "acima" : "abaixo"}`}
                    </strong>
                  </p>
                  {metric && validObjective && (
                    <MetricView
                      compact
                      name="Diferença para o objetivo"
                      metric={objectiveDistance(metric, objective)}
                    />
                  )}
                </td>
                <td>
                  <MetricView
                    compact
                    name="Participação"
                    metric={p.metrics.recentParticipation}
                  />
                  <MetricView
                    compact
                    name="Decks usados"
                    metric={p.metrics.recentDecks}
                  />
                  <MetricView
                    compact
                    name="Guerras com 16 decks"
                    metric={p.metrics.completeStandardWars}
                  />
                </td>
                <td>
                  <MetricView
                    compact
                    name="Doações observadas na semana"
                    metric={p.metrics.weeklyDonations}
                  />
                  <MetricView
                    compact
                    name="Dias observados no clã"
                    metric={p.metrics.observedTenureDays}
                  />
                </td>
                <td className="prospect-context">
                  <Badge value={p.operationalStatus.value} />
                  <p>{p.operationalStatus.reason}</p>
                  <MetricView
                    compact
                    name="Alertas registrados"
                    metric={p.metrics.recentAlerts}
                  />
                  {admin && (
                    <CandidacyWatch
                      tag={p.tag}
                      notes={watches(p.tag)}
                      available={!!notes}
                    />
                  )}
                </td>
              </tr>
            );
          })}
      </Table>
      {!players.length && (
        <Empty>
          Nenhum membro nesta seleção. Ajuste os filtros para consultar os
          demais.
        </Empty>
      )}
      {players.length > 10 && (
        <div className="pagination">
          <span>
            Página {currentPage + 1} de {Math.ceil(players.length / 10)}
          </span>
          <button
            disabled={currentPage === 0}
            onClick={() => setPage(currentPage - 1)}
          >
            Candidatos anteriores
          </button>
          <button
            disabled={(currentPage + 1) * 10 >= players.length}
            onClick={() => setPage(currentPage + 1)}
          >
            Próximos candidatos
          </button>
        </div>
      )}
      <p className="muted">
        Uma sinalização verde não promove ninguém. Consulte o histórico e as
        justificativas antes de decidir.
      </p>
    </section>
  );
}
