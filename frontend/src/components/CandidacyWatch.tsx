import { useRef, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { api, tagPath } from "../lib/api";
import type { LeaderNote } from "../lib/leadership";
import { NoteText } from "./LeaderNotes";
export function CandidacyWatch({
  tag,
  notes,
  available,
}: {
  tag: string;
  notes: LeaderNote[];
  available: boolean;
}) {
  const [body, setBody] = useState("");
  const [followUpOn, setFollowUpOn] = useState("");
  const key = useRef(crypto.randomUUID());
  const client = useQueryClient();
  const save = useMutation({
    mutationFn: () =>
      api(`/leadership/players/${tagPath(tag)}/notes`, {
        requestKey: key.current,
        kind: "CANDIDACY",
        body,
        followUpOn,
        expiresOn: null,
      }),
    onSuccess: async () => {
      key.current = crypto.randomUUID();
      setBody("");
      setFollowUpOn("");
      await client.invalidateQueries({
        predicate: (q) => String(q.queryKey[0]).startsWith("/leadership/"),
      });
    },
  });
  return (
    <details>
      <summary>
        {notes.length
          ? "Candidatura em acompanhamento"
          : "Acompanhar para Ancião"}
      </summary>
      {notes.map((n) => (
        <NoteText key={n.id} note={n} />
      ))}
      {available && !notes.length && (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            save.mutate();
          }}
        >
          <fieldset disabled={save.isPending} className="watch-form">
            <label>
              Motivo do acompanhamento
              <textarea
                required
                maxLength={2000}
                value={body}
                onChange={(e) => {
                  setBody(e.target.value);
                  key.current = crypto.randomUUID();
                }}
              />
            </label>
            <label>
              Data para reavaliar
              <input
                type="date"
                required
                value={followUpOn}
                onChange={(e) => {
                  setFollowUpOn(e.target.value);
                  key.current = crypto.randomUUID();
                }}
              />
            </label>
            <button disabled={!body.trim() || !followUpOn}>
              Salvar acompanhamento
            </button>
          </fieldset>
        </form>
      )}
      {save.error && <p role="alert">{save.error.message}</p>}
      <Link to={`/membros/${tagPath(tag)}?tab=notas`}>
        Ver notas e encerrar acompanhamento →
      </Link>
    </details>
  );
}
