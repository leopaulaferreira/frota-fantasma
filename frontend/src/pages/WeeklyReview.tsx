import { twoClosedBelow, closedAndLiveBelow } from "../lib/weeklyReview";
import { WeeklyDecision } from "../components/WeeklyDecision";
import { Fragment, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { useMutation, useQueryClient, useQuery } from "@tanstack/react-query";
import { useAdmin } from "../lib/auth";
import { api, ApiError, useApi, date, format, tagPath } from "../lib/api";
import { type ActiveNotes, type LeaderNote } from "../lib/leadership";
import {
  type WeeklyReviewReport,
  type WeeklyReviewMember,
  reviewStatuses,
  decisionLabels,
  decisionPending,
  type ReviewDecision,
  saoPauloTime,
  offlineDuration,
  reviewOrder,
  participationGroups,
} from "../lib/weeklyReview";
import { Heading, QueryState, Empty } from "../components/ui";
import { NoteText } from "../components/LeaderNotes";

export function WeeklyReview() {
  return useAdmin() ? (
    <PrivateWeeklyReview />
  ) : (
    <>
      <Heading title="Expulsões semanais" />
      <p>Esta revisão e as justificativas são reservadas à liderança.</p>
    </>
  );
}
function PrivateWeeklyReview() {
  const [week, setWeek] = useState("");
  const [filter, setFilter] = useState("");
  const [expanded, setExpanded] = useState<string | null>(null);
  const [sort, setSort] = useState("priority");
  const [descending, setDescending] = useState(false);
  const [search, setSearch] = useState("");
  const [justified, setJustified] = useState(false);
  const [progress, setProgress] = useState("");
  const [criterion, setCriterion] = useState("");
  const [donationTarget, setDonationTarget] = useState("");
  const [offlineTarget, setOfflineTarget] = useState("5");
  const [notice, setNotice] = useState("");
  const q = useApi<WeeklyReviewReport>(
    "/leadership/weekly-review" +
      (week ? `?week=${encodeURIComponent(week)}` : ""),
    60000,
  );
  const notes = useApi<ActiveNotes>("/leadership/notes/active", 60000);
  const r = q.data;
  const matchesRule = (p: WeeklyReviewMember) => {
    switch (criterion) {
      case "points":
        return p.belowPoints;
      case "zero":
        return !!r?.membersFresh && p.points === 0;
      case "decks":
        return !!r?.membersFresh && p.decksUsed === 0;
      case "two":
        return !!p.participation?.twoZero;
      case "twoBelow":
        return twoClosedBelow(p, r?.pointsTarget ?? 1600);
      case "twoEligible":
        return !!p.participation?.twoBelow;
      case "closedLive":
        return !!r && closedAndLiveBelow(p, r);
      case "zeroDonations":
        return !!p.participation?.twoZeroDonations;
      case "new":
        return !!p.participation?.newcomer;
      case "wait":
        return history(p.tag)[0]?.action === "WAIT";
      case "reviewed":
        return !!decisions.data && !decisionPending(history(p.tag)[0]);
      case "dismissed":
        return history(p.tag)[0]?.action === "DISMISS";
      case "offline":
        return (
          !!r?.membersFresh &&
          offlineTarget !== "" &&
          Number(offlineTarget) >= 0 &&
          p.offlineSeconds !== null &&
          p.offlineSeconds > Number(offlineTarget) * 86400
        );
      case "donations":
        return (
          donationTarget !== "" &&
          Number(donationTarget) >= 0 &&
          p.donations != null &&
          p.donations < Number(donationTarget)
        );
      default:
        return true;
    }
  };
  const decisions = useQuery({
    queryKey: ["weekly-review-decisions", r?.week],
    queryFn: () =>
      api<ReviewDecision[]>(
        "/leadership/weekly-review/decisions?week=" + r!.week,
      ),
    retry: (count, error) =>
      count < 1 && (!(error instanceof ApiError) || error.status >= 500),
    enabled: !!r,
    refetchInterval: 60000,
  });
  const history = (tag: string) =>
    decisions.data?.filter((d) => d.playerTag === tag) || [];
  const matchesProgress = (tag: string) =>
    !progress ||
    (!!decisions.data &&
      (progress === "pending"
        ? decisionPending(history(tag)[0])
        : !decisionPending(history(tag)[0])));
  const activeNotes = (tag: string) =>
    notes.data?.notes.filter(
      (n) => n.playerTag === tag && n.status === "ACTIVE",
    ) || [];
  const rows =
    r?.members
      .filter(
        (p) =>
          matchesProgress(p.tag) &&
          (!filter || p.participation?.group === filter) &&
          matchesRule(p) &&
          (p.name + p.tag)
            .toLocaleLowerCase("pt-BR")
            .includes(search.trim().toLocaleLowerCase("pt-BR")) &&
          (!justified ||
            activeNotes(p.tag).some((n) => n.kind === "JUSTIFICATION")),
      )
      .sort((a, b) => {
        const value = (p: WeeklyReviewMember): number | string | null => {
          switch (sort) {
            case "name":
              return p.name;
            case "previous":
              return p.participation?.wars[0]?.points ?? null;
            case "points":
              return p.points;
            case "decks":
              return p.decksUsed;
            case "donations":
              return p.participation?.donationWeeks[1]?.observed ?? null;
            case "tenure":
              return p.participation?.tenureDays ?? null;
            case "offline":
              return p.offlineSeconds;
            case "group":
              return p.participation?.group ?? null;
            default:
              return null;
          }
        };
        if (sort === "priority") return reviewOrder(a, b);
        const x = value(a),
          y = value(b);
        if (x === null || y === null)
          return x === y ? a.tag.localeCompare(b.tag) : x === null ? 1 : -1;
        return (
          (typeof x === "string" && typeof y === "string"
            ? x.localeCompare(y)
            : Number(x) - Number(y)) * (descending ? -1 : 1) ||
          a.tag.localeCompare(b.tag)
        );
      }) || [];
  return (
    <>
      <Heading eyebrow="FAXINA NO CONVÉS" title="Expulsões semanais">
        <Link className="button" to="/operacoes">
          Ver coletas →
        </Link>
      </Heading>
      <p className="lede">
        Revise a participação com resultados oficiais, captura da corrida,
        doações, vínculo com o clã e atividade. Selecione um critério para
        identificar reincidência ou sinais recentes. A decisão é sua, após
        conferir o contexto e as justificativas de cada tripulante.
      </p>
      <div className="toolbar">
        <label>
          Semana da revisão (segunda-feira)
          <input
            type="date"
            aria-label="Semana da revisão"
            value={week}
            onChange={(e) => {
              setNotice("");
              setProgress("");
              setWeek(e.target.value);
              setFilter("");
            }}
          />
        </label>
        <button
          onClick={() => {
            setNotice("");
            setProgress("");
            setWeek("");
            setFilter("");
          }}
        >
          Revisão mais recente
        </button>
        <button
          onClick={() => {
            q.refetch();
            notes.refetch();
            decisions.refetch();
          }}
        >
          Recarregar lista
        </button>
      </div>
      <QueryState query={q}>
        {r && (
          <>
            <div className="review-deadline">
              <strong>
                {r.phase === "LIVE"
                  ? "Prévia em atualização"
                  : "Recorte de domingo às 22h"}{" "}
                · semana de {date(r.week)}
              </strong>
              <p>
                Horário de revisão: {saoPauloTime(r.deadline)} · São Paulo.{" "}
                {r.phase === "LIVE"
                  ? "Os pontos ainda podem aumentar até esse horário."
                  : "A lista usa somente capturas feitas até esse horário; atualizações posteriores pertencem ao acompanhamento seguinte."}
              </p>
              <details className="review-help">
                <summary>Como funciona a revisão</summary>
                <p>
                  Coletas reforçadas a cada 5 minutos no domingo, das 18h às
                  21h55, e uma tentativa às 21h59. O sistema precisa estar
                  ligado e com acesso à API. Este recorte serve à decisão do
                  líder e não confirma o encerramento oficial da guerra.
                </p>
                <small>
                  O painel consulta os dados a cada minuto. De segunda a quarta,
                  “Revisão mais recente” abre a semana anterior; de quinta a
                  domingo, abre a semana em andamento.
                </small>
                <p>
                  Quem fez exatamente 1.600 pontos ou está há exatamente 5 dias
                  offline não aciona o respectivo critério. A inatividade é
                  medida na captura. A lista começa pelos zerados, depois pelos
                  membros abaixo da meta em ordem crescente de decks usados. Os
                  demais aparecem em seguida. Dados desconhecidos não equivalem
                  a zero.
                </p>
              </details>
            </div>
            {!r.collectionEnabled && (
              <p role="alert" className="error">
                A coleta automática está desativada. Ative-a ou use “Atualizar
                dados” antes do horário de revisão.
              </p>
            )}
            {(!r.membersFresh || !r.riverFresh || !r.rosterComplete) && (
              <p role="alert" className="error">
                Há fontes ausentes, incompletas ou sem coleta válida na última
                hora do recorte. Confira os dados antes de tomar uma decisão.
                Registros ausentes não são tratados como zero.
              </p>
            )}
            <div className="review-sources">
              <span>
                Elenco e atividade: <b>{saoPauloTime(r.membersCapturedAt)}</b>
              </span>
              <span>
                Corrida da semana: <b>{saoPauloTime(r.riverCapturedAt)}</b>
              </span>
            </div>
            <div
              className="prospect-summary"
              aria-label="Resumo da revisão semanal"
            >
              {Object.entries(participationGroups).map(([key, label]) => (
                <button
                  key={key}
                  className={filter === key ? "selected" : ""}
                  aria-pressed={filter === key}
                  onClick={() => setFilter(key)}
                >
                  <strong>
                    {
                      r.members.filter((p) => p.participation?.group === key)
                        .length
                    }
                  </strong>
                  {label}
                </button>
              ))}
            </div>
            <div className="toolbar" aria-label="Andamento das avaliações">
              <label>
                Andamento
                <select
                  aria-label="Filtrar andamento"
                  value={progress}
                  disabled={!decisions.data}
                  onChange={(e) => setProgress(e.target.value)}
                >
                  <option value="">Pendentes e avaliados</option>
                  <option value="pending">Pendentes</option>
                  <option value="done">Avaliados</option>
                </select>
              </label>
              {decisions.data ? (
                <span>
                  {
                    r.members.filter((p) => decisionPending(history(p.tag)[0]))
                      .length
                  }{" "}
                  pendentes ·{" "}
                  {
                    r.members.filter((p) => !decisionPending(history(p.tag)[0]))
                      .length
                  }{" "}
                  avaliados no elenco da semana
                </span>
              ) : (
                <span>Andamento indisponível até carregar as decisões.</span>
              )}
            </div>
            {decisions.error && (
              <p role="alert" className="error">
                Não foi possível carregar as decisões.{" "}
                <button onClick={() => decisions.refetch()}>
                  Recarregar decisões
                </button>
              </p>
            )}
            {notice && <p role="status">{notice}</p>}
            <div className="toolbar">
              <input
                aria-label="Buscar na revisão"
                placeholder="Nome ou tag…"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
              <select
                aria-label="Filtrar revisão"
                value={filter}
                onChange={(e) => setFilter(e.target.value)}
              >
                <option value="">Todos os membros do recorte</option>
                {Object.entries(participationGroups).map(([key, label]) => (
                  <option key={key} value={key}>
                    {label}
                  </option>
                ))}
              </select>
              <label className="check">
                <input
                  type="checkbox"
                  checked={justified}
                  disabled={!notes.data}
                  onChange={(e) => setJustified(e.target.checked)}
                />
                Com justificativa vigente
              </label>
              <button
                onClick={() => {
                  setCriterion("");
                  setSearch("");
                  setFilter("");
                  setJustified(false);
                  setProgress("");
                }}
              >
                Mostrar todos
              </button>
            </div>
            <div className="toolbar" aria-label="Filtros por regra">
              <label>
                Critério de revisão
                <select
                  value={criterion}
                  onChange={(e) => {
                    setCriterion(e.target.value);
                    setFilter("");
                  }}
                >
                  <option value="">Todos os critérios</option>
                  <option value="points">Abaixo da meta de pontos</option>
                  <option value="zero">Zero pontos</option>
                  <option value="decks">Nenhum deck usado nesta semana</option>
                  <option value="twoBelow">Duas guerras abaixo da meta</option>
                  <option value="twoEligible">
                    Duas encerradas · vínculo elegível
                  </option>
                  <option value="closedLive">
                    Encerrada + atual abaixo · prévia
                  </option>
                  <option value="two">Duas guerras com zero ataques</option>
                  <option value="zeroDonations">
                    Zero doações nas últimas duas semanas completas
                  </option>
                  <option value="new">
                    Novatos (menos de 14 dias observados)
                  </option>
                  <option value="wait">Aguardando justificativa</option>
                  <option value="reviewed">Revisados</option>
                  <option value="dismissed">Dispensados</option>
                  <option value="offline">Dias offline acima do limite</option>
                  <option value="donations">Doações abaixo da meta</option>
                </select>
              </label>
              {criterion === "offline" && (
                <label>
                  Limite de dias offline
                  <input
                    type="number"
                    min="0"
                    step="1"
                    value={offlineTarget}
                    onChange={(e) => setOfflineTarget(e.target.value)}
                  />
                </label>
              )}
              {criterion === "donations" && (
                <label>
                  Meta semanal de doações
                  <input
                    type="number"
                    min="0"
                    step="1"
                    placeholder="Informe a meta"
                    value={donationTarget}
                    onChange={(e) => setDonationTarget(e.target.value)}
                  />
                </label>
              )}
            </div>
            {criterion === "donations" && (
              <p className="muted">
                Contador de doações na captura do elenco, não o total
                reconstruído por diferenças. Informe a meta; dados desconhecidos
                não entram como zero.
              </p>
            )}
            {(criterion === "two" || criterion === "twoBelow") && (
              <p className="muted">
                Filtro das duas últimas guerras oficiais disponíveis no recorte.
                Resultados registrados não confirmam disponibilidade integral de
                ataques. O filtro “Duas guerras abaixo da meta” inclui
                pontuações oficiais conhecidas mesmo com vínculo pendente;
                “vínculo elegível” mantém a avaliação conservadora. Ausência de
                registro nunca vira zero.
              </p>
            )}
            {criterion === "closedLive" && (
              <p className="callout">
                Prévia: última guerra publicada nesta semana + captura atual
                atualizada. A pontuação atual pode aumentar. Não são duas
                guerras encerradas nem uma reincidência confirmada.
              </p>
            )}
            {criterion === "zeroDonations" && (
              <p className="muted">
                Exige duas semanas completas com contadores zero, sem reset ou
                reingresso e cobertura de no máximo uma hora entre capturas.
                Lacunas não contam como zero.
              </p>
            )}
            {notes.error && (
              <p role="alert" className="error">
                Não foi possível consultar as justificativas.{" "}
                <button onClick={() => notes.refetch()}>
                  Tentar novamente
                </button>
              </p>
            )}
            <p role="status">
              {rows.length} membros nesta seleção · {r.members.length} no elenco
              do recorte · todas as funções incluídas.
            </p>
            <div className="table-scroll review-table">
              <table>
                <caption>
                  Revisão individual · resultados oficiais e captura parcial
                  identificados separadamente
                </caption>
                <thead>
                  <tr>
                    {[
                      ["name", "Jogador"],
                      ["previous", "Última encerrada"],
                      ["points", "Atual · parcial"],
                      ["decks", "Decks"],
                      ["donations", "Doações"],
                      ["tenure", "Tempo no clã"],
                      ["offline", "Dias offline"],
                      ["group", "Situação"],
                    ].map(([key, label]) => (
                      <th
                        key={key}
                        aria-sort={
                          sort === key
                            ? descending
                              ? "descending"
                              : "ascending"
                            : "none"
                        }
                      >
                        <button
                          onClick={() => {
                            setSort(key);
                            setDescending(sort === key ? !descending : false);
                          }}
                        >
                          {label}
                          {sort === key ? (descending ? " ↓" : " ↑") : " ↕"}
                        </button>
                      </th>
                    ))}
                    {["twoBelow", "twoEligible"].includes(criterion) && (
                      <th>Penúltima encerrada</th>
                    )}
                  </tr>
                </thead>
                <tbody>
                  {rows.map((p, i) => (
                    <Fragment key={r.week + p.tag}>
                      <tr className="review-member-row">
                        <td className="review-identity">
                          <button
                            className="review-name"
                            aria-expanded={expanded === p.tag}
                            aria-controls={"review-" + i}
                            onClick={() =>
                              setExpanded(expanded === p.tag ? null : p.tag)
                            }
                          >
                            {i + 1}. {p.name}
                          </button>
                          <small>{p.tag}</small>
                        </td>
                        <td>
                          <ReviewValue
                            value={p.participation?.wars[0]?.points}
                            unit="pts"
                          />
                          <small>
                            Oficial ·{" "}
                            {p.participation?.wars[0]
                              ? date(p.participation.wars[0].publishedAt)
                              : "Sem histórico"}
                          </small>
                        </td>
                        <td>
                          <ReviewValue value={p.points} unit="pts" />
                          <small className="review-live-label">
                            Captura parcial
                          </small>
                        </td>
                        <td>
                          <ReviewValue value={p.decksUsed} />
                          <small>
                            Anterior:{" "}
                            {format(p.participation?.wars[0]?.decks ?? null)}
                          </small>
                        </td>
                        <td>
                          <ReviewValue
                            value={p.participation?.donationWeeks[1]?.observed}
                          />
                          <small>
                            Semana anterior ·{" "}
                            {p.participation?.donationWeeks[1]?.complete
                              ? "cobertura observada"
                              : "cobertura insuficiente"}
                          </small>
                        </td>
                        <td>
                          <ReviewValue
                            value={p.participation?.tenureDays}
                            unit="dias"
                          />
                          <small>
                            {p.participation?.rejoined
                              ? "Reingresso"
                              : p.participation?.newcomer
                                ? "Novato · " + p.participation.tenureBasis
                                : p.participation?.tenureBasis}
                          </small>
                        </td>
                        <td>
                          <span className="review-activity">
                            {offlineDuration(p.offlineSeconds)}
                          </span>
                        </td>
                        <td>
                          <span
                            className={
                              "review-status " +
                              (p.participation?.group === "PRIORITY"
                                ? "warn"
                                : "")
                            }
                          >
                            {p.participation
                              ? participationGroups[p.participation.group]
                              : "Dados insuficientes"}
                          </span>
                          <small>
                            {!decisions.data
                              ? "Decisão indisponível"
                              : history(p.tag)[0]
                                ? decisionLabels[history(p.tag)[0].action]
                                : "Pendente"}
                          </small>
                        </td>
                        {["twoBelow", "twoEligible"].includes(criterion) && (
                          <td>
                            <ReviewValue
                              value={p.participation?.wars[1]?.points}
                              unit="pts"
                            />
                            <small>
                              Oficial ·{" "}
                              {p.participation?.wars[1]
                                ? date(p.participation.wars[1].publishedAt)
                                : "Sem registro"}
                            </small>
                            <small>
                              {p.participation?.twoBelow
                                ? "Vínculo elegível nas duas"
                                : "Conferir vínculo e disponibilidade; resultados não são faltas confirmadas"}
                            </small>
                          </td>
                        )}
                      </tr>
                      {expanded === p.tag && (
                        <tr id={"review-" + i}>
                          <td
                            colSpan={
                              ["twoBelow", "twoEligible"].includes(criterion)
                                ? 9
                                : 8
                            }
                          >
                            <ReviewMember
                              player={p}
                              position={i + 1}
                              report={r}
                              notes={activeNotes(p.tag)}
                              notesAvailable={!!notes.data}
                              decisions={history(p.tag)}
                              decisionsAvailable={!!decisions.data}
                              onSaved={setNotice}
                            />
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  ))}
                </tbody>
              </table>
            </div>
            <button
              onClick={() => {
                setSort("priority");
                setDescending(false);
              }}
            >
              Restaurar ordem: zerados e decks crescentes
            </button>
            {!rows.length && (
              <Empty>
                {!r.members.length
                  ? "Sem elenco coletado para este recorte. Consulte as coletas."
                  : progress
                    ? "Nenhum membro corresponde ao andamento e aos demais filtros selecionados."
                    : filter === "REVIEW"
                      ? "Nenhum sinal confirmado nesta seleção. Confira também o grupo de dados pendentes e as fontes acima."
                      : "Nenhum membro nesta seleção."}
              </Empty>
            )}
          </>
        )}
      </QueryState>
    </>
  );
}
function ReviewMember({
  player: p,
  position,
  report,
  notes,
  notesAvailable,
  decisions,
  decisionsAvailable,
  onSaved,
}: {
  player: WeeklyReviewMember;
  position: number;
  report: WeeklyReviewReport;
  notes: LeaderNote[];
  notesAvailable: boolean;
  decisions: ReviewDecision[];
  decisionsAvailable: boolean;
  onSaved: (message: string) => void;
}) {
  const justifications = notes.filter((n) => n.kind === "JUSTIFICATION");
  const reasons = [
    p.belowPoints ? "Abaixo de 1.600 pontos" : "",
    p.offlineExceeded ? "Mais de 5 dias offline" : "",
  ].filter(Boolean);
  return (
    <details open className="prospect-card weekly-review-member">
      <summary className="prospect-row-summary">
        <span className="prospect-position">{position}º</span>
        <span className="prospect-row-copy">
          <span className="prospect-row-title">
            <strong>{p.name}</strong>
            <span
              className={`badge ${p.status === "REVIEW" ? "warn" : p.status === "CLEAR" ? "good" : ""}`}
            >
              {justifications.length
                ? "Tem justificativa"
                : p.participation
                  ? participationGroups[p.participation.group]
                  : reviewStatuses[p.status]}
            </span>
          </span>
          <span className="prospect-row-stats">
            {decisionsAvailable
              ? decisions[0]
                ? decisionLabels[decisions[0].action]
                : "Pendente"
              : "Decisão não carregada"}{" "}
            ·{" "}
            {p.points === null
              ? "Pontos desconhecidos"
              : `${format(p.points)} pontos`}{" "}
            · Decks: {format(p.decksUsed)} · Doações:{" "}
            {format(p.donations ?? null)}· Offline:{" "}
            {offlineDuration(p.offlineSeconds)}
            {reasons.length ? " · " + reasons.join(" e ") : ""}
          </span>
        </span>
        <span className="prospect-expand" aria-hidden="true">
          ⌄
        </span>
      </summary>
      <div className="prospect-card-content">
        <h3>
          {p.name} · {p.tag}
        </h3>
        <p>
          <strong>
            {p.participation
              ? participationGroups[p.participation.group]
              : reviewStatuses[p.status]}
          </strong>{" "}
          · Cargo no recorte:{" "}
          {(
            {
              member: "Membro",
              elder: "Ancião",
              coLeader: "Colíder",
              leader: "Líder",
            } as Record<string, string>
          )[p.role] || p.role}
        </p>
        {reasons.length > 0 && (
          <ul>
            {reasons.map((reason) => (
              <li key={reason}>{reason}</li>
            ))}
          </ul>
        )}
        <div className="facts">
          <div>
            <small>Pontos nesta corrida</small>
            <strong>
              {p.points === null ? "Desconhecidos" : format(p.points)}
            </strong>
          </div>
          <div>
            <small>Decks usados</small>
            <strong>
              {p.decksUsed === null ? "Desconhecidos" : format(p.decksUsed)}
            </strong>
          </div>
          <div>
            <small>Doações na captura do elenco</small>
            <strong>
              {p.donations == null ? "Desconhecidas" : format(p.donations)}
            </strong>
          </div>
          <div>
            <small>Tempo offline na captura</small>
            <b>{offlineDuration(p.offlineSeconds)}</b>
          </div>
          <div>
            <small>Última atividade oficial</small>
            <b>{saoPauloTime(p.lastSeen)}</b>
          </div>
        </div>
        {p.missingEvidence.length > 0 && (
          <div className="callout">
            <strong>Evidências pendentes</strong>
            <ul>
              {p.missingEvidence.map((reason) => (
                <li key={reason}>{reason}</li>
              ))}
            </ul>
          </div>
        )}
        <p className="muted">
          Associação observada desde{" "}
          {p.observedSince
            ? saoPauloTime(p.observedSince)
            : "data desconhecida"}
          . A primeira observação não comprova ingresso real. Confira se o
          membro entrou durante a guerra ou avisou uma ausência.
        </p>
        {p.participation && (
          <section className="review-evidence">
            <h3>Participação e reincidência</h3>
            <ul>
              {p.participation.reasons.map((reason) => (
                <li key={reason}>{reason}</li>
              ))}
            </ul>
            <p>
              Média: {format(p.participation.averageFour)} ·{" "}
              {p.participation.warsConsidered}/4 guerras com pontos ·{" "}
              {p.participation.belowTarget} abaixo de 1.600 ·{" "}
              {p.participation.consecutiveZeroAttacks} seguidas sem ataques.{" "}
              {p.participation.trend}.
            </p>
            <h3>Histórico oficial disponível até o recorte</h3>
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Publicação</th>
                    <th>Pontos</th>
                    <th>Decks</th>
                    <th>Contexto</th>
                  </tr>
                </thead>
                <tbody>
                  {p.participation.wars.map((w) => (
                    <tr key={w.id}>
                      <td>
                        {date(w.publishedAt)}
                        <small>Oficial</small>
                      </td>
                      <td>{format(w.points)}</td>
                      <td>{format(w.decks)}</td>
                      <td>
                        {w.reason}
                        <small>Capturado: {saoPauloTime(w.capturedAt)}</small>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <h3>Histórico de doações</h3>
            <p>
              Quatro semanas completas: {format(p.participation.totalFourWeeks)}{" "}
              observadas ·{" "}
              {p.participation.donationCoverageComplete
                ? "Cobertura observacional completa"
                : "Cobertura incompleta; não representa total exato"}{" "}
              · {p.participation.zeroDonationWeeks} semanas com zero confirmado
              pelas capturas.
            </p>
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Semana</th>
                    <th>Observadas</th>
                    <th>Último contador</th>
                    <th>Cobertura</th>
                  </tr>
                </thead>
                <tbody>
                  {p.participation.donationWeeks.map((d) => (
                    <tr key={d.week}>
                      <td>{date(d.week)}</td>
                      <td>{format(d.observed)}</td>
                      <td>{format(d.lastCounter)}</td>
                      <td>
                        {d.samples} capturas · {d.resets} resets ·{" "}
                        {d.zeroConfirmed
                          ? "Zero confirmado nas capturas"
                          : d.complete
                            ? "Cobertura observada"
                            : "Dados parciais/insuficientes"}
                        <small>{d.reason}</small>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <h3>Entrada, saída e reingressos</h3>
            <p>
              {p.participation.tenureBasis}. Tempo observado não comprova data
              real de entrada nem todos os ataques disponíveis.
            </p>
            <ul>
              {p.participation.events.map((e, i) => (
                <li key={i}>
                  {(
                    {
                      JOIN: "Entrada",
                      LEAVE: "Saída",
                      BASELINE: "Primeira observação",
                      ROLE_CHANGE: "Mudança de cargo",
                    } as Record<string, string>
                  )[e.type] || e.type}{" "}
                  · {saoPauloTime(e.at)}
                </li>
              ))}
            </ul>
          </section>
        )}
        {decisionsAvailable ? (
          <WeeklyDecision
            tag={p.tag}
            week={report.week}
            history={decisions}
            onSaved={onSaved}
          />
        ) : (
          <p>
            Carregue as decisões para registrar uma avaliação com segurança.
          </p>
        )}
        <h3>Justificativas e contexto vigentes hoje</h3>
        <p className="muted">
          As notas abaixo são atuais, inclusive ao consultar semanas passadas.
          Uma justificativa ajuda na sua decisão e mantém os sinais visíveis.
        </p>
        {!notesAvailable ? (
          <p>Justificativas ainda não disponíveis.</p>
        ) : notes.length ? (
          notes.map((n) => <NoteText key={n.id} note={n} />)
        ) : (
          <p>Nenhuma anotação vigente registrada.</p>
        )}
        <details>
          <summary>Registrar justificativa para esta revisão</summary>
          <JustificationForm tag={p.tag} week={report.week} />
        </details>
        <div className="prospect-card-footer">
          <Link to={`/membros/${tagPath(p.tag)}?tab=notas`}>
            Perfil, histórico e notas →
          </Link>
          <Link to="/guerras">Conferir guerras →</Link>
        </div>
      </div>
    </details>
  );
}
function JustificationForm({ tag, week }: { tag: string; week: string }) {
  const [body, setBody] = useState("");
  const [expires, setExpires] = useState("");
  const key = useRef(crypto.randomUUID());
  const client = useQueryClient();
  const save = useMutation({
    mutationFn: () =>
      api(`/leadership/players/${tagPath(tag)}/notes`, {
        requestKey: key.current,
        kind: "JUSTIFICATION",
        body: `Revisão semanal de ${date(week)}: ${body.trim()}`,
        expiresOn: expires || null,
        followUpOn: null,
      }),
    onSuccess: async () => {
      setBody("");
      setExpires("");
      key.current = crypto.randomUUID();
      await client.invalidateQueries({
        predicate: (q) => String(q.queryKey[0]).startsWith("/leadership/"),
      });
    },
  });
  return (
    <form
      className="note-form"
      onSubmit={(e) => {
        e.preventDefault();
        save.mutate();
      }}
    >
      <fieldset disabled={save.isPending}>
        <label>
          Motivo informado pelo membro
          <textarea
            required
            maxLength={1800}
            value={body}
            onChange={(e) => {
              setBody(e.target.value);
              key.current = crypto.randomUUID();
              save.reset();
            }}
            placeholder="Ex.: avisou viagem; manter nesta semana e reavaliar no próximo domingo."
          />
        </label>
        <label>
          Justificativa válida até (opcional)
          <input
            type="date"
            value={expires}
            onChange={(e) => {
              setExpires(e.target.value);
              key.current = crypto.randomUUID();
              save.reset();
            }}
          />
        </label>
        <button disabled={!body.trim()}>
          {save.isPending ? "Salvando…" : "Salvar justificativa"}
        </button>
      </fieldset>
      {save.error && <p role="alert">{save.error.message}</p>}
      {save.isSuccess && (
        <p role="status">Justificativa registrada no histórico do membro.</p>
      )}
    </form>
  );
}

function ReviewValue({
  value,
  unit = "",
}: {
  value: number | null | undefined;
  unit?: string;
}) {
  return (
    <strong
      className="review-number"
      aria-label={value == null ? "Dado não disponível" : undefined}
      title={value == null ? "Dado não disponível" : undefined}
    >
      {value == null ? "—" : format(value)}
      {value != null && unit && <span> {unit}</span>}
    </strong>
  );
}
