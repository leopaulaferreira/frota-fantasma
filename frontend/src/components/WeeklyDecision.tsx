import { useRef, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api, tagPath } from "../lib/api";
import {
  decisionLabels,
  type ReviewDecision,
  saoPauloTime,
} from "../lib/weeklyReview";

export function WeeklyDecision({
  tag,
  week,
  history,
  onSaved,
}: {
  tag: string;
  week: string;
  history: ReviewDecision[];
  onSaved: (message: string) => void;
}) {
  const latest = history[0];
  const [expectedRevision, setExpectedRevision] = useState(
    latest?.revision || 0,
  );
  const [action, setAction] = useState("");
  const [reason, setReason] = useState("");
  const requestKey = useRef(crypto.randomUUID());
  const client = useQueryClient();
  const stale = expectedRevision !== (latest?.revision || 0);
  const save = useMutation({
    mutationFn: () =>
      api<ReviewDecision>(
        `/leadership/weekly-review/decisions/${tagPath(tag)}`,
        {
          week,
          action,
          reason: reason.trim(),
          expectedRevision,
          requestKey: requestKey.current,
        },
      ),
    onSuccess: async (decision) => {
      setExpectedRevision(decision.revision);
      setAction("");
      setReason("");
      requestKey.current = crypto.randomUUID();
      await client.invalidateQueries({
        queryKey: ["weekly-review-decisions", week],
      });
      onSaved("Decisão registrada: " + decisionLabels[decision.action] + ".");
    },
  });
  const edited = () => {
    requestKey.current = crypto.randomUUID();
    save.reset();
  };
  return (
    <section
      className="weekly-decision-form"
      aria-label="Decisão da revisão semanal"
    >
      <h3>Decisão desta semana</h3>
      <p>
        {latest ? (
          <>
            <strong>{decisionLabels[latest.action]}</strong> ·{" "}
            {latest.createdBy} · {saoPauloTime(latest.createdAt)}
          </>
        ) : (
          "Pendente de avaliação"
        )}
      </p>
      {latest && <p className="note-body">{latest.reason}</p>}
      <p className="muted">
        Aguardar justificativa e reabrir deixam o caso pendente. Dispensar
        registra sua decisão; a remoção deve ser feita manualmente no jogo.
      </p>
      <form
        className="note-form"
        onSubmit={(event) => {
          event.preventDefault();
          if (!stale) save.mutate();
        }}
      >
        <fieldset disabled={save.isPending}>
          <label>
            Decisão
            <select
              aria-label="Decisão"
              required
              value={action}
              onChange={(e) => {
                setAction(e.target.value);
                edited();
              }}
            >
              <option value="">Selecione uma decisão</option>
              {Object.entries(decisionLabels).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          <label>
            Motivo da decisão
            <textarea
              required
              maxLength={2000}
              value={reason}
              onChange={(e) => {
                setReason(e.target.value);
                edited();
              }}
              placeholder="Registre o contexto que orientou sua decisão nesta semana."
            />
          </label>
          {stale && (
            <p role="alert">
              Há uma decisão mais recente. Confira o histórico antes de
              continuar.{" "}
              <button
                type="button"
                onClick={() => {
                  setExpectedRevision(latest?.revision || 0);
                  edited();
                }}
              >
                Usar revisão mais recente
              </button>
            </p>
          )}
          <button disabled={stale || !action || !reason.trim()}>
            {save.isPending ? "Salvando…" : "Salvar decisão"}
          </button>
        </fieldset>
        {save.error && (
          <p role="alert">
            {save.error.message}{" "}
            <button
              type="button"
              onClick={() =>
                client.invalidateQueries({
                  queryKey: ["weekly-review-decisions", week],
                })
              }
            >
              Recarregar decisões
            </button>
          </p>
        )}
      </form>
      {!!history.length && (
        <details className="prospect-evidence">
          <summary>
            Histórico de decisões desta semana ({history.length})
          </summary>
          {history.map((decision) => (
            <article className="leader-note-text" key={decision.revision}>
              <strong>{decisionLabels[decision.action]}</strong>
              <small>
                {decision.createdBy} · {saoPauloTime(decision.createdAt)}
              </small>
              <p className="note-body">{decision.reason}</p>
            </article>
          ))}
        </details>
      )}
    </section>
  );
}
