import { useEffect, useRef, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api, date, dateTime, useApi } from "../lib/api";
import { QueryState } from "./ui";

interface Candidate {
  key: string;
  kind: string;
  targetId: number;
  seasonId: number | null;
  sectionIndex: number | null;
  periodIndex: number | null;
  periodType: string | null;
  officialAt: string;
  equalFingerprint: boolean;
  matchingTags: number;
  identicalFame: number;
  identicalDecks: number;
  identicalRows: number;
  officialGreaterValues: number;
  incompatibleValues: number;
  unknownValues: number;
  csvOnlyTags: number;
  officialOnlyTags: number;
  evidence: string[];
}
interface Resolution {
  revision: number;
  status: string;
  resolvedBy: string;
  resolvedAt: string;
  resolutionMethod: string;
  reason: string;
  targetKind: string | null;
  targetId: number | null;
  seasonId: number | null;
  sectionIndex: number | null;
  capturedAt: string | null;
  timezone: string | null;
  evidence: unknown;
}
interface Identification {
  batchId: string;
  format: string;
  filename: string;
  clanTag: string;
  clanName: string;
  match: string;
  candidates: Candidate[];
  warnings: string[];
  suggestedCapturedAt: string | null;
  suggestedTimezone: string;
  evidenceVersion: string;
  history: Resolution[];
}
export const identificationStatus = (value: string) =>
  ({
    PENDING_IDENTIFICATION: "Pendente de identificação",
    IDENTIFIED: "Identificado",
    CONFLICT: "Conflito",
    REJECTED: "Rejeitado",
  })[value] || value;

function targetLabel(candidate: Candidate) {
  return candidate.kind === "WAR"
    ? `Temporada ${candidate.seasonId} • Seção ${candidate.sectionIndex} • Guerra #${candidate.targetId}`
    : `Snapshot #${candidate.targetId} • Seção ${candidate.sectionIndex ?? "desconhecida"} • Período ${candidate.periodIndex ?? "desconhecido"} (${candidate.periodType ?? "tipo desconhecido"}) • Temporada não informada`;
}
export function ImportIdentification({
  batchId,
  status,
}: {
  batchId: string;
  status: string;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (open) dialog.current?.showModal();
  }, [open]);
  return (
    <>
      <button onClick={() => setOpen(true)}>
        {status === "PENDING_IDENTIFICATION"
          ? "Identificar dados"
          : "Revisar identificação"}
      </button>
      <dialog
        ref={dialog}
        className="metric-dialog"
        aria-labelledby={`identify-${batchId}`}
        onCancel={(event) => {
          if (busy) event.preventDefault();
        }}
        onClose={() => setOpen(false)}
      >
        <header className="metric-dialog-header">
          <h2 id={`identify-${batchId}`}>Identificar dados importados</h2>
        </header>
        <div className="metric-dialog-body">
          {open && (
            <IdentificationContent batchId={batchId} setBusy={setBusy} />
          )}
          <button disabled={busy} onClick={() => dialog.current?.close()}>
            Fechar identificação
          </button>
        </div>
      </dialog>
    </>
  );
}
function IdentificationContent({
  batchId,
  setBusy,
}: {
  batchId: string;
  setBusy: (value: boolean) => void;
}) {
  const query = useApi<Identification>(
    `/imports/royaleapi/${batchId}/identification`,
  );
  const [feedback, setFeedback] = useState("");
  return (
    <>
      <p className="callout">
        Confirmar associação não sobrescreve dados oficiais. Mesmo identificado,
        este lote permanece fora do analytics até a Fase 3.
      </p>
      {feedback && <p role="status">{feedback}</p>}
      <QueryState query={query}>
        {query.data && (
          <ResolutionForm
            key={`${query.data.evidenceVersion}:${query.data.history[0]?.revision ?? 0}`}
            data={query.data}
            setBusy={setBusy}
            refresh={() => query.refetch()}
            saved={(result) =>
              setFeedback(
                `Resolução registrada: ${identificationStatus(result.status)}. Dados oficiais preservados.`,
              )
            }
          />
        )}
      </QueryState>
    </>
  );
}
function ResolutionForm({
  data,
  setBusy,
  refresh,
  saved,
}: {
  data: Identification;
  setBusy: (value: boolean) => void;
  refresh: () => unknown;
  saved: (result: Resolution) => void;
}) {
  const [candidateKey, setCandidateKey] = useState("");
  const [visibleCandidates, setVisibleCandidates] = useState(20);
  const [manualDate, setManualDate] = useState(!data.suggestedCapturedAt);
  const [capturedAtLocal, setCapturedAtLocal] = useState(
    data.suggestedCapturedAt?.slice(0, 16) || "",
  );
  const [timezone, setTimezone] = useState(data.suggestedTimezone);
  const [reason, setReason] = useState("");
  const [confirmed, setConfirmed] = useState(false);
  const [action, setAction] = useState("IDENTIFY");
  const key = useRef(crypto.randomUUID());
  const client = useQueryClient();
  const mutation = useMutation({
    mutationFn: () =>
      api<Resolution>(`/imports/royaleapi/${data.batchId}/identification`, {
        requestKey: key.current,
        expectedRevision: data.history[0]?.revision ?? 0,
        evidenceVersion: data.evidenceVersion,
        action,
        candidateKey: data.format === "RIVER" ? candidateKey : null,
        clanTag: data.clanTag,
        capturedAtLocal:
          data.format === "MEMBERS" ? capturedAtLocal || null : null,
        timezone: data.format === "MEMBERS" ? timezone : null,
        timestampOrigin:
          data.format === "MEMBERS"
            ? manualDate
              ? "USER_ENTERED"
              : "USER_CONFIRMED_FILENAME"
            : null,
        confirmed,
        reason,
      }),
    onMutate: () => setBusy(true),
    onSuccess: async (result) => {
      saved(result);
      await client.invalidateQueries({
        predicate: (q) =>
          String(q.queryKey[0]).startsWith("/imports/royaleapi"),
      });
    },
    onSettled: () => setBusy(false),
  });
  function changed() {
    key.current = crypto.randomUUID();
    mutation.reset();
    setConfirmed(false);
  }
  const selected = data.candidates.find((c) => c.key === candidateKey);
  const canIdentify =
    data.format === "MEMBERS" ? !!capturedAtLocal && !!timezone : !!selected;
  return (
    <>
      <p>
        <strong>{data.filename}</strong>
      </p>
      <p>
        Estado atual:{" "}
        {identificationStatus(
          data.history[0]?.status || "PENDING_IDENTIFICATION",
        )}
      </p>
      <fieldset disabled={mutation.isPending}>
        <legend>Revisão da origem</legend>
        <label>
          Ação
          <select
            aria-label="Ação"
            value={action}
            onChange={(e) => {
              changed();
              setAction(e.target.value);
            }}
          >
            <option value="IDENTIFY">Confirmar identificação</option>
            <option value="REJECTED">Rejeitar lote</option>
            <option value="PENDING_IDENTIFICATION">
              Reabrir como pendente
            </option>
          </select>
        </label>
        {data.format === "RIVER" ? (
          <>
            <h3>
              {data.match === "EXACT_MATCH"
                ? "Correspondência encontrada"
                : data.match === "POSSIBLE_MATCH"
                  ? "Possíveis correspondências"
                  : "Sem correspondência segura"}
            </h3>
            {data.match === "UNRESOLVED" && (
              <p>
                O lote continua pendente. Nenhum alvo foi selecionado por
                aproximação.
              </p>
            )}
            {data.candidates.length > 0 && (
              <p>
                Exibindo {Math.min(visibleCandidates, data.candidates.length)}{" "}
                de {data.candidates.length} candidatos, ordenados por igualdade,
                incompatibilidades e coincidências. Nenhum alvo é selecionado
                automaticamente.
              </p>
            )}
            {data.candidates.slice(0, visibleCandidates).map((candidate) => (
              <article className="panel" key={candidate.key}>
                <label>
                  <input
                    type="radio"
                    name={`target-${data.batchId}`}
                    checked={candidateKey === candidate.key}
                    onChange={() => {
                      changed();
                      setCandidateKey(candidate.key);
                    }}
                  />
                  {targetLabel(candidate)}
                </label>
                <p>
                  {dateTime(candidate.officialAt)} ·{" "}
                  {candidate.kind === "WAR"
                    ? "Publicação oficial"
                    : "Captura oficial"}
                </p>
                <p>
                  {candidate.matchingTags} jogadores coincidentes ·{" "}
                  {candidate.identicalRows} registros integralmente iguais
                </p>
                <p>
                  {candidate.identicalFame} pontuações idênticas ·{" "}
                  {candidate.identicalDecks} quantidades de decks idênticas
                </p>
                <p>
                  {candidate.officialGreaterValues} valores oficiais maiores ·{" "}
                  {candidate.incompatibleValues} incompatibilidades
                </p>
                <details>
                  <summary>Ver detalhes</summary>
                  <p>
                    {candidate.csvOnlyTags} tags somente no CSV;{" "}
                    {candidate.officialOnlyTags} somente na fonte oficial;{" "}
                    {candidate.unknownValues} campos sem comparação.
                  </p>
                  <p>
                    {candidate.equalFingerprint
                      ? "Fingerprint completo igual. A classificação depende de haver um único alvo igual."
                      : "Compatibilidade parcial: exige avaliação humana."}
                  </p>
                  <ul>
                    {candidate.evidence.map((e, i) => (
                      <li key={i}>{e}</li>
                    ))}
                  </ul>
                </details>
              </article>
            ))}
            {visibleCandidates < data.candidates.length && (
              <button
                onClick={() => setVisibleCandidates((count) => count + 20)}
              >
                Mostrar mais correspondências
              </button>
            )}
            {selected && selected.incompatibleValues > 0 && (
              <p className="error">
                Esta confirmação registrará CONFLITO, não um lote identificado
                para backfill.
              </p>
            )}
          </>
        ) : (
          <>
            <p>
              Clã sugerido:{" "}
              <strong>
                {data.clanName} {data.clanTag}
              </strong>
            </p>
            <p>
              Data sugerida pelo arquivo:{" "}
              {data.suggestedCapturedAt
                ? `${date(data.suggestedCapturedAt.slice(0, 10))} ${data.suggestedCapturedAt.slice(11, 16)}`
                : "Não disponível"}
              . Sem fuso implícito.
            </p>
            <label>
              Data e hora da captura
              <input
                type="datetime-local"
                value={capturedAtLocal}
                readOnly={!manualDate}
                onChange={(e) => {
                  changed();
                  setCapturedAtLocal(e.target.value);
                }}
              />
            </label>
            {!manualDate && (
              <button
                onClick={() => {
                  changed();
                  setManualDate(true);
                }}
              >
                Alterar
              </button>
            )}
            <label>
              Fuso horário da captura
              <input
                value={timezone}
                placeholder="America/Sao_Paulo"
                maxLength={100}
                onChange={(e) => {
                  changed();
                  setTimezone(e.target.value);
                }}
              />
            </label>
            <p>
              Procedência:{" "}
              {manualDate
                ? "Data informada pelo usuário"
                : "Sugestão do filename, a confirmar pelo usuário"}
              .
            </p>
          </>
        )}
        {data.warnings.map((w) => (
          <p className="muted" key={w}>
            {w}
          </p>
        ))}
        <label>
          Motivo da resolução
          <textarea
            value={reason}
            maxLength={2000}
            onChange={(e) => {
              changed();
              setReason(e.target.value);
            }}
          />
        </label>
        <label>
          <input
            type="checkbox"
            checked={confirmed}
            onChange={(e) => {
              key.current = crypto.randomUUID();
              setConfirmed(e.target.checked);
            }}
          />
          Confirmo o alvo ou clã, a data e o fuso informados quando aplicáveis,
          e entendo que dados oficiais não serão substituídos.
        </label>
        <div className="toolbar">
          <button
            disabled={
              !confirmed ||
              !reason.trim() ||
              (action === "IDENTIFY" && !canIdentify)
            }
            onClick={() => mutation.mutate()}
          >
            {mutation.isPending
              ? "Registrando resolução…"
              : action !== "IDENTIFY"
                ? "Registrar revisão"
                : data.format === "MEMBERS"
                  ? "Confirmar origem"
                  : "Confirmar correspondência"}
          </button>
          <button
            onClick={() => {
              setConfirmed(false);
              refresh();
            }}
          >
            Atualizar evidências
          </button>
        </div>
      </fieldset>
      {mutation.error && (
        <p role="alert" className="error">
          {mutation.error.message}
        </p>
      )}
      <h3>Histórico de resoluções</h3>
      {data.history.length === 0 && <p>Nenhuma resolução registrada.</p>}
      {data.history.map((item) => (
        <details key={item.revision} className="panel">
          <summary>
            {identificationStatus(item.status)} · {dateTime(item.resolvedAt)} ·{" "}
            {item.resolvedBy}
          </summary>
          <p>{item.reason}</p>
          <p>Método: {item.resolutionMethod}</p>
          {item.targetId && (
            <p>
              Alvo: {item.targetKind} #{item.targetId} · Temporada{" "}
              {item.seasonId ?? "não informada"} · Seção{" "}
              {item.sectionIndex ?? "não informada"}
            </p>
          )}
          {item.capturedAt && (
            <p>
              Captura:{" "}
              {new Intl.DateTimeFormat("pt-BR", {
                timeZone: item.timezone || "UTC",
                dateStyle: "short",
                timeStyle: "short",
              }).format(new Date(item.capturedAt))}{" "}
              · Fuso confirmado: {item.timezone}
            </p>
          )}
          <details>
            <summary>Evidências preservadas</summary>
            <pre>{JSON.stringify(item.evidence, null, 2)}</pre>
          </details>
        </details>
      ))}
    </>
  );
}
