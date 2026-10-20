import { WeeklyDecision } from "../components/WeeklyDecision";
import { useRef, useState } from "react";
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
  const [filter, setFilter] = useState("REVIEW");
  const [search, setSearch] = useState("");
  const [justified, setJustified] = useState(false);
  const [progress, setProgress] = useState("");
  const [notice, setNotice] = useState("");
  const q = useApi<WeeklyReviewReport>(
    "/leadership/weekly-review" +
      (week ? `?week=${encodeURIComponent(week)}` : ""),
    60000,
  );
  const notes = useApi<ActiveNotes>("/leadership/notes/active", 60000);
  const r = q.data;
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
    r?.members.filter(
      (p) =>
        matchesProgress(p.tag) &&
        (!filter || p.status === filter) &&
        (p.name + p.tag)
          .toLocaleLowerCase("pt-BR")
          .includes(search.trim().toLocaleLowerCase("pt-BR")) &&
        (!justified ||
          activeNotes(p.tag).some((n) => n.kind === "JUSTIFICATION")),
    ) || [];
  return (
    <>
      <Heading eyebrow="FAXINA NO CONVÉS" title="Expulsões semanais">
        <Link className="button" to="/operacoes">
          Ver coletas →
        </Link>
      </Heading>
      <p className="lede">
        Lista de revisão: menos de 1.600 pontos na corrida da semana ou mais de
        5 dias offline. A decisão é sua, após conferir o contexto e as
        justificativas de cada tripulante.
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
              setFilter("REVIEW");
            }}
          />
        </label>
        <button
          onClick={() => {
            setNotice("");
            setProgress("");
            setWeek("");
            setFilter("REVIEW");
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
                  medida na captura. A ordem prioriza quem acionou as duas
                  regras, depois a menor pontuação.
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
              {Object.entries(reviewStatuses).map(([key, label]) => (
                <button
                  key={key}
                  className={filter === key ? "selected" : ""}
                  aria-pressed={filter === key}
                  onClick={() => setFilter(key)}
                >
                  <strong>
                    {r.members.filter((p) => p.status === key).length}
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
                {Object.entries(reviewStatuses).map(([key, label]) => (
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
                  setSearch("");
                  setFilter("");
                  setJustified(false);
                  setProgress("");
                }}
              >
                Mostrar todos
              </button>
            </div>
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
            <div className="prospect-list">
              {rows.map((p, i) => (
                <ReviewMember
                  key={r.week + p.tag}
                  player={p}
                  position={i + 1}
                  report={r}
                  notes={activeNotes(p.tag)}
                  notesAvailable={!!notes.data}
                  decisions={history(p.tag)}
                  decisionsAvailable={!!decisions.data}
                  onSaved={setNotice}
                />
              ))}
            </div>
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
    <details className="prospect-card weekly-review-member">
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
            · Offline: {offlineDuration(p.offlineSeconds)}
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
          <strong>{reviewStatuses[p.status]}</strong> · Cargo no recorte:{" "}
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
