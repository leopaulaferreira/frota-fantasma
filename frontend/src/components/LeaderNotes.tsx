import { useRef, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api, date, dateTime, tagPath } from "../lib/api";
import { type LeaderNote, noteKinds, noteStatuses } from "../lib/leadership";
import { Paged } from "./ui";

export function NoteText({ note }: { note: LeaderNote }) {
  return (
    <div className="leader-note-text">
      <b>
        {noteKinds[note.kind]} · {noteStatuses[note.status]}
      </b>
      <p className="note-body">{note.body}</p>
      <small>
        Registrada por {note.createdBy} em {dateTime(note.createdAt)} ·
        Informação da liderança
      </small>
      <p className="muted">
        Validade:{" "}
        {note.expiresOn
          ? `até ${date(note.expiresOn)} (inclusive)`
          : "sem prazo"}{" "}
        · Retomar: {note.followUpOn ? date(note.followUpOn) : "sem data"}
        {note.followUpDue ? " · Acompanhamento pendente" : ""}
      </p>
      {note.resolvedAt && (
        <p>
          Encerrada por {note.resolvedBy} em {dateTime(note.resolvedAt)}:{" "}
          {note.resolution}
        </p>
      )}
    </div>
  );
}
function NoteEntry({ note }: { note: LeaderNote }) {
  const [reason, setReason] = useState("");
  const client = useQueryClient();
  const resolve = useMutation({
    mutationFn: () => api(`/leadership/notes/${note.id}/resolve`, { reason }),
    onSuccess: () =>
      client.invalidateQueries({
        predicate: (q) => String(q.queryKey[0]).startsWith("/leadership/"),
      }),
  });
  return (
    <article className="leader-note">
      <NoteText note={note} />
      {!note.resolvedAt && (
        <details>
          <summary>Encerrar acompanhamento</summary>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              resolve.mutate();
            }}
          >
            <label>
              Resultado do acompanhamento
              <textarea
                required
                maxLength={1000}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
              />
            </label>
            <button disabled={resolve.isPending || !reason.trim()}>
              Salvar encerramento
            </button>
          </form>
          {resolve.error && <p role="alert">{resolve.error.message}</p>}
        </details>
      )}
    </article>
  );
}
export function LeaderNotes({ tag }: { tag: string }) {
  const [kind, setKind] = useState<LeaderNote["kind"]>("OBSERVATION");
  const [body, setBody] = useState("");
  const [expiresOn, setExpiresOn] = useState("");
  const [followUpOn, setFollowUpOn] = useState("");
  const key = useRef(crypto.randomUUID());
  const client = useQueryClient();
  const save = useMutation({
    mutationFn: () =>
      api(`/leadership/players/${tagPath(tag)}/notes`, {
        requestKey: key.current,
        kind,
        body,
        expiresOn: expiresOn || null,
        followUpOn: followUpOn || null,
      }),
    onSuccess: async () => {
      key.current = crypto.randomUUID();
      setBody("");
      setExpiresOn("");
      setFollowUpOn("");
      await client.invalidateQueries({
        predicate: (q) => String(q.queryKey[0]).startsWith("/leadership/"),
      });
    },
  });
  const changed = () => {
    key.current = crypto.randomUUID();
    save.reset();
  };
  return (
    <section className="leader-notes" aria-label="Notas da liderança">
      <h2>Notas e combinados</h2>
      <p className="muted">
        Visíveis apenas à liderança. Datas no horário de São Paulo. As notas
        contextualizam a análise; não alteram métricas ou classificações. Para
        corrigir um registro, encerre-o e crie outro.
      </p>
      <form
        className="panel note-form"
        onSubmit={(e) => {
          e.preventDefault();
          save.mutate();
        }}
      >
        <fieldset disabled={save.isPending}>
          <label>
            Tipo de anotação
            <select
              value={kind}
              onChange={(e) => {
                setKind(e.target.value as LeaderNote["kind"]);
                changed();
              }}
            >
              {Object.entries(noteKinds).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          </label>
          <label>
            Descrição da anotação
            <textarea
              required
              maxLength={2000}
              rows={3}
              value={body}
              onChange={(e) => {
                setBody(e.target.value);
                changed();
              }}
              placeholder="Ex.: avisou que estará viajando; combinar retorno na próxima guerra."
            />
          </label>
          <div className="toolbar">
            <label>
              Válida até (opcional)
              <input
                type="date"
                value={expiresOn}
                onChange={(e) => {
                  setExpiresOn(e.target.value);
                  changed();
                }}
              />
            </label>
            <label>
              Retomar acompanhamento em (opcional)
              <input
                type="date"
                value={followUpOn}
                onChange={(e) => {
                  setFollowUpOn(e.target.value);
                  changed();
                }}
              />
            </label>
          </div>
          <button type="submit" className="primary" disabled={!body.trim()}>
            {save.isPending ? "Salvando…" : "Salvar anotação"}
          </button>
        </fieldset>
        {save.error && <p role="alert">{save.error.message}</p>}
        {save.isSuccess && <p role="status">Anotação registrada.</p>}
      </form>
      <h3>Histórico de anotações</h3>
      <Paged<LeaderNote>
        path={`/leadership/players/${tagPath(tag)}/notes`}
        empty="Nenhuma anotação registrada para este membro."
        render={(notes) => (
          <div>
            {notes.map((n) => (
              <NoteEntry key={n.id} note={n} />
            ))}
          </div>
        )}
      />
    </section>
  );
}
