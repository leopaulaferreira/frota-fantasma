import { Link } from "react-router-dom";
import { useState } from "react";
import { useAdmin } from "../lib/auth";
import {
  classifyProspect,
  compareProspects,
  fourKnown,
  numeric,
  prospectBands,
  promotionSetting,
  criterionLabels,
} from "../lib/prospects";
import type { ActiveNotes } from "../lib/leadership";
import { CandidacyWatch } from "./CandidacyWatch";
import {
  type Player,
  type Report,
  date,
  dateTime,
  format,
  useApi,
} from "../lib/api";
import { Badge, Empty, MetricView } from "./ui";

export function recentAverage(player: Player): number | null {
  return numeric(player.metrics.recentAverage);
}
export function orderedProspects(players: Player[]) {
  return players
    .filter((p) => p.currentMember && p.role === "member")
    .sort((a, b) => {
      const av = recentAverage(a),
        bv = recentAverage(b);
      return av === bv
        ? a.tag.localeCompare(b.tag)
        : av === null
          ? 1
          : bv === null
            ? -1
            : bv - av;
    });
}
export function ElderProspects({ report }: { report: Report }) {
  return useAdmin() ? (
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
  const savedTarget = promotionSetting(report, "pointsTarget");
  const [objectiveInput, setObjectiveInput] = useState("");
  const [toleranceInput, setToleranceInput] = useState("5");
  const [band, setBand] = useState("");
  const [sample, setSample] = useState("");
  const [gap, setGap] = useState("");
  const [search, setSearch] = useState("");
  const [sort, setSort] = useState("readiness");
  const [watched, setWatched] = useState(false);
  const objective =
    objectiveInput === "" ? (savedTarget ?? NaN) : Number(objectiveInput);
  const tolerance = toleranceInput.trim() === "" ? NaN : Number(toleranceInput);
  const valid =
    Number.isFinite(objective) &&
    objective > 0 &&
    Number.isFinite(tolerance) &&
    tolerance >= 0 &&
    tolerance <= 20;
  const watches = (tag: string) =>
    notes?.notes.filter(
      (n) =>
        n.playerTag === tag && n.kind === "CANDIDACY" && n.status === "ACTIVE",
    ) || [];
  const all = orderedProspects(report.players)
    .map((p) => ({
      p,
      full: fourKnown(p),
      assessment: classifyProspect(p, report, objective, tolerance),
    }))
    .sort((a, b) => compareProspects(a, b, sort))
    .map((row, index) => ({ ...row, position: index + 1 }));
  const players = all.filter(
    ({ p, full, assessment }) =>
      (!band || assessment.band === band) &&
      (!sample || (sample === "full" ? full : !full)) &&
      (!gap ||
        assessment.criteria.some((c) => c.key === gap && c.state !== "pass")) &&
      (!watched || watches(p.tag).length > 0) &&
      (p.name + p.tag)
        .toLocaleLowerCase("pt-BR")
        .includes(search.trim().toLocaleLowerCase("pt-BR")),
  );
  const tenureTarget = promotionSetting(report, "promotionTenureDays");
  const observedTenures = all
    .map(({ p }) => numeric(p.metrics.observedTenureDays))
    .filter((n): n is number => n !== null);
  const longestObservation = observedTenures.length
    ? Math.max(...observedTenures)
    : null;
  const reset = () => {
    setBand("");
    setSample("");
    setGap("");
    setSearch("");
    setWatched(false);
    setSort("readiness");
    setObjectiveInput("");
    setToleranceInput("5");
  };
  return (
    <section
      aria-labelledby="elder-prospects-title"
      className="elder-prospects"
    >
      <div className="section-head">
        <h2 id="elder-prospects-title">Possíveis candidatos a Ancião</h2>
        <Link to="/regras">Consultar regras →</Link>
      </div>
      <p className="muted">
        Primeiro os membros prontos para avaliação, depois as pendências de
        desenvolvimento. A numeração segue a ordenação selecionada e permanece
        ao filtrar. Clique no resumo para abrir os critérios. Alertas e falta de
        evidência têm grupos próprios. Uma pontuação alta não compensa outros
        critérios.
      </p>
      {longestObservation !== null &&
        tenureTarget !== null &&
        longestObservation < tenureTarget && (
          <div className="callout">
            <strong>O histórico local ainda está se formando</strong>
            <p>
              Os membros sem cargo têm até {longestObservation} dias de
              associação observada; a regra exige {tenureTarget} dias. Isso pode
              impedir a aprovação oficial de membros que já estavam no clã antes
              do sistema. Use as pendências para acompanhar os destaques e
              registre o contexto nas notas, sem confundir primeira coleta com
              ingresso real.
            </p>
          </div>
        )}
      <div className="prospect-summary" aria-label="Resumo das candidaturas">
        <button
          className={!band ? "selected" : ""}
          aria-pressed={!band}
          onClick={() => {
            setBand("");
          }}
        >
          <strong>{all.length}</strong>Todos os membros
        </button>
        {Object.entries(prospectBands).map(([key, label]) => (
          <button
            key={key}
            className={`prospect-${key} ${band === key ? "selected" : ""}`}
            aria-pressed={band === key}
            onClick={() => {
              setBand(band === key ? "" : key);
            }}
          >
            <strong>
              {all.filter((r) => r.assessment.band === key).length}
            </strong>
            {label}
          </button>
        ))}
      </div>
      <div className="prospect-filters">
        <label>
          Buscar membro
          <input
            aria-label="Buscar candidato"
            placeholder="Nome ou tag…"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
            }}
          />
        </label>
        <label>
          Situação
          <select
            aria-label="Filtrar sinal de candidatura"
            value={band}
            onChange={(e) => {
              setBand(e.target.value);
            }}
          >
            <option value="">Todos os sinais</option>
            {Object.entries(prospectBands).map(([k, v]) => (
              <option key={k} value={k}>
                {v} ({all.filter((r) => r.assessment.band === k).length})
              </option>
            ))}
          </select>
        </label>
        <label>
          Pendência específica
          <select
            aria-label="Filtrar pendência"
            value={gap}
            onChange={(e) => {
              setGap(e.target.value);
            }}
          >
            <option value="">Qualquer critério</option>
            {[
              ["sample", "Histórico de guerras"],
              ["points", "Média recente"],
              ["participation", "Participação"],
              ["decks", "Uso de decks"],
              ["tenure", "Tempo observado"],
              ["donations", "Doações na semana"],
              ["alerts", "Alertas registrados"],
              ["status", "Situação atual"],
            ].map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </label>
        <label>
          Ordenação
          <select
            aria-label="Ordenar candidatos"
            value={sort}
            onChange={(e) => {
              setSort(e.target.value);
            }}
          >
            <option value="readiness">Prontidão e critérios atendidos</option>
            <option value="recentAverage">Maior média recente</option>
            <option value="standardDeckTargetPercent">
              Maior uso do padrão de decks
            </option>
            <option value="weeklyDonations">Mais doações observadas</option>
            <option value="observedTenureDays">Mais tempo observado</option>
            <option value="recentTrendPoints">Maior evolução recente</option>
          </select>
        </label>
      </div>
      <details className="panel prospect-options">
        <summary>Refinar a análise e simular referência</summary>
        <div className="prospect-filters">
          <label>
            Objetivo de referência (pontos)
            <input
              id="elder-objective"
              type="number"
              min="1"
              placeholder={String(savedTarget ?? "")}
              value={objectiveInput}
              aria-invalid={!valid}
              onChange={(e) => {
                setObjectiveInput(e.target.value);
              }}
            />
          </label>
          <label>
            Faixa próxima (%)
            <input
              type="number"
              min="0"
              max="20"
              value={toleranceInput}
              onChange={(e) => {
                setToleranceInput(e.target.value);
              }}
            />
          </label>
          <label>
            Amostra
            <select
              aria-label="Filtrar amostra de candidatura"
              value={sample}
              onChange={(e) => {
                setSample(e.target.value);
              }}
            >
              <option value="">Todas as amostras</option>
              <option value="full">Quatro pontuações conhecidas</option>
              <option value="partial">Histórico em formação</option>
            </select>
          </label>
        </div>
        <p className="muted">
          Referência inicial: {format(savedTarget)} pontos, conforme regras
          salvas. Uma simulação abaixo desse valor não reduz a exigência
          oficial. “Poucas pendências” exige até dois critérios pendentes, média
          dentro da faixa e nenhum alerta ou dado ausente. A simulação não
          altera as regras.
        </p>
      </details>
      <div className="prospect-results">
        <p role="status">
          {players.length} membros nesta seleção · {all.length} membros sem
          cargo no elenco
        </p>
        {admin && (
          <label className="check">
            <input
              type="checkbox"
              checked={watched}
              disabled={!notes}
              onChange={(e) => {
                setWatched(e.target.checked);
              }}
            />
            Somente acompanhados para Ancião
          </label>
        )}
        <button onClick={reset}>Limpar filtros</button>
      </div>
      {!valid && (
        <p role="alert">
          Informe um objetivo maior que zero e uma faixa de 0% a 20%.
        </p>
      )}
      <p className="muted">
        Análise de {dateTime(report.generatedAt)} · Doações da semana de{" "}
        {date(report.week)} são um limite inferior: coleta incompleta pode
        subestimar a contribuição. Tempo observado não é a data real de
        ingresso. Decisão e promoção cabem à liderança.
      </p>
      <div className="prospect-list">
        {players.map(({ p, assessment, position }) => (
          <details className="prospect-card" data-rank={position} key={p.tag}>
            <summary className="prospect-row-summary">
              <span
                className="prospect-position"
                aria-label={`Posição ${position}`}
              >
                {position}º
              </span>
              <span className="prospect-row-copy">
                <span className="prospect-row-title">
                  <strong>{p.name}</strong>
                  <span className={`badge prospect-${assessment.band}`}>
                    {prospectBands[assessment.band]}
                  </span>
                </span>
                <span className="prospect-row-stats">
                  {assessment.passed}/{assessment.criteria.length} critérios ·
                  Média {format(numeric(p.metrics.recentAverage))} pts · Decks{" "}
                  {format(numeric(p.metrics.standardDeckTargetPercent))}% ·
                  Doações {format(numeric(p.metrics.weeklyDonations))}
                </span>
              </span>
              <span className="prospect-expand" aria-hidden="true">
                ⌄
              </span>
            </summary>
            <div className="prospect-card-content">
              <div className="prospect-card-heading">
                <span>{p.tag}</span>
                <Badge value={p.operationalStatus.value} />
              </div>
              <div className="prospect-action">
                <strong>
                  {assessment.passed}/{assessment.criteria.length} critérios
                  atendidos
                </strong>
                <p>{assessment.nextAction}</p>
              </div>
              <ul
                className="prospect-checklist"
                aria-label={`Critérios de ${p.name}`}
              >
                {assessment.criteria.map((c) => (
                  <li key={c.key} className={`criterion-${c.state}`}>
                    <div>
                      <strong>{c.label}</strong>
                      <span>{criterionLabels[c.state]}</span>
                    </div>
                    <p>{c.detail}</p>
                  </li>
                ))}
              </ul>
              <div className="prospect-card-footer">
                <Link to={`/membros/${encodeURIComponent(p.tag)}`}>
                  Abrir perfil e histórico →
                </Link>
                <Link to={`/comparar?tags=${encodeURIComponent(p.tag)}`}>
                  Comparar membro →
                </Link>
                {admin && (
                  <CandidacyWatch
                    tag={p.tag}
                    notes={watches(p.tag)}
                    available={!!notes}
                  />
                )}
              </div>
              <details className="prospect-evidence">
                <summary>Ver guerras, evolução e evidências completas</summary>
                <ol className="prospect-war-list">
                  {p.wars.slice(0, 4).map((w) => (
                    <li key={w.warId}>
                      <Link to={`/guerras/${w.warId}`}>
                        Publicada em {date(w.publishedAt)}
                      </Link>
                      <br />
                      {format(w.points.value)} pontos ·{" "}
                      {format(w.decksUsed.value)} decks
                    </li>
                  ))}
                </ol>
                {!p.wars.length && <p>Nenhuma guerra oficial disponível.</p>}
                <div className="metrics">
                  <MetricView
                    compact
                    name="Evolução recente"
                    metric={p.metrics.recentTrendPoints}
                  />
                  <MetricView
                    compact
                    name="Candidatura pelas regras salvas"
                    metric={p.elderCandidate}
                  />
                  {assessment.criteria
                    .filter((c) => c.metric)
                    .map((c) => (
                      <MetricView
                        key={c.key}
                        compact
                        name={c.label}
                        metric={c.metric}
                      />
                    ))}
                </div>
              </details>
            </div>
          </details>
        ))}
      </div>
      {!players.length && (
        <Empty>
          Nenhum membro nesta seleção. Ajuste os filtros para consultar os
          demais.
        </Empty>
      )}
    </section>
  );
}
