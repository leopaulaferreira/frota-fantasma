import { useEffect, useRef, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api, dateTime, useApi } from "../lib/api";
import { QueryState, Table } from "./ui";

interface Row {
  line: number;
  playerTag: string;
  outcome: string;
  reason: string;
}
interface Result {
  id: string;
  status: string;
  rowsNew: number;
  rowsDuplicated: number;
  rowsConflicted: number;
  rowsIgnored: number;
  appliedAt: string;
  appliedBy: string;
  rows: Row[];
}
interface Plan {
  batchId: string;
  resolutionId: number;
  rowsAnalyzed: number;
  rowsNew: number;
  rowsDuplicated: number;
  rowsConflicted: number;
  rowsIgnored: number;
  playersAffected: number;
  periodFrom: string | null;
  periodTo: string | null;
  evidenceVersion: string;
  rows: Row[];
  warnings: string[];
  previous: Result | null;
}
const outcomes: Record<string, string> = {
  NEW: "Novo histórico",
  DUPLICATE: "Já existente",
  CONFLICT: "Conflito",
  IGNORED: "Ignorado",
};
function Counts({
  value,
}: {
  value: Pick<
    Plan,
    "rowsNew" | "rowsDuplicated" | "rowsConflicted" | "rowsIgnored"
  >;
}) {
  return (
    <ul>
      <li>{value.rowsNew} novos históricos</li>
      <li>{value.rowsDuplicated} já existentes</li>
      <li>{value.rowsConflicted} conflitos</li>
      <li>{value.rowsIgnored} ignorados</li>
    </ul>
  );
}
function Rows({ rows }: { rows: Row[] }) {
  return (
    <details>
      <summary>Ver resultado por registro</summary>
      <Table headers={["Linha", "Jogador", "Resultado", "Motivo"]}>
        {rows.map((row) => (
          <tr key={row.line}>
            <td>{row.line}</td>
            <td>{row.playerTag || "—"}</td>
            <td>{outcomes[row.outcome] || row.outcome}</td>
            <td>{row.reason}</td>
          </tr>
        ))}
      </Table>
    </details>
  );
}
export function ImportBackfill({
  batchId,
  applied,
}: {
  batchId: string;
  applied: boolean;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [open, setOpen] = useState(false),
    [busy, setBusy] = useState(false);
  useEffect(() => {
    if (open) dialog.current?.showModal();
  }, [open]);
  return (
    <>
      <button onClick={() => setOpen(true)}>
        {applied ? "Consultar incorporação" : "Incorporar ao histórico"}
      </button>
      <dialog
        ref={dialog}
        className="metric-dialog"
        aria-labelledby={`backfill-${batchId}`}
        onClose={() => setOpen(false)}
        onCancel={(e) => {
          if (busy) e.preventDefault();
        }}
      >
        <header className="metric-dialog-header">
          <h2 id={`backfill-${batchId}`}>Incorporar ao histórico</h2>
        </header>
        <div className="metric-dialog-body">
          {open && <BackfillContent batchId={batchId} setBusy={setBusy} />}
          <button disabled={busy} onClick={() => dialog.current?.close()}>
            Cancelar / fechar
          </button>
        </div>
      </dialog>
    </>
  );
}
function BackfillContent({
  batchId,
  setBusy,
}: {
  batchId: string;
  setBusy: (value: boolean) => void;
}) {
  const q = useApi<Plan>(`/imports/royaleapi/${batchId}/backfill/preview`);
  const [confirmed, setConfirmed] = useState(false);
  const client = useQueryClient();
  const save = useMutation({
    mutationFn: () =>
      api<Result>(`/imports/royaleapi/${batchId}/backfill`, {
        resolutionId: q.data!.resolutionId,
        evidenceVersion: q.data!.evidenceVersion,
        confirmed,
      }),
    onMutate: () => setBusy(true),
    onSuccess: () => client.invalidateQueries(),
    onSettled: () => setBusy(false),
  });
  const result = save.data || q.data?.previous;
  return (
    <>
      <p className="callout">
        A Supercell permanece autoritativa. Conflitos não substituem dados
        oficiais. Esta operação não promove, expulsa ou concede premiações.
      </p>
      <QueryState query={q}>
        {q.data && !result && (
          <>
            <h3>Prévia da incorporação</h3>
            <p>
              {q.data.rowsAnalyzed} registros analisados ·{" "}
              {q.data.playersAffected} jogadores com novos históricos
            </p>
            <Counts value={q.data} />
            <p>
              Período histórico:{" "}
              {q.data.periodFrom
                ? `${dateTime(q.data.periodFrom)} → ${dateTime(q.data.periodTo)}`
                : "Sem período comprovado para incorporação"}
            </p>
            {q.data.warnings.map((warning) => (
              <p className="muted" key={warning}>
                {warning}
              </p>
            ))}
            <Rows rows={q.data.rows} />
            <label>
              <input
                type="checkbox"
                checked={confirmed}
                disabled={save.isPending}
                onChange={(e) => setConfirmed(e.target.checked)}
              />
              Confirmo a incorporação dos registros novos e entendo que
              conflitos e ignorados não entrarão nas métricas.
            </label>
            <div className="toolbar">
              <button
                className="primary"
                disabled={!confirmed || save.isPending || q.isFetching}
                onClick={() => save.mutate()}
              >
                {save.isPending ? "Incorporando…" : "Incorporar ao histórico"}
              </button>
              <button
                disabled={save.isPending || q.isFetching}
                onClick={() => {
                  setConfirmed(false);
                  save.reset();
                  q.refetch();
                }}
              >
                Atualizar prévia
              </button>
            </div>
          </>
        )}
        {result && (
          <section role="status">
            <h3>
              {result.rowsConflicted || result.rowsIgnored
                ? "Incorporação registrada com pendências"
                : result.rowsNew
                  ? "Incorporação registrada"
                  : "Conferência registrada sem novos históricos"}
            </h3>
            <Counts value={result} />
            <p>
              {dateTime(result.appliedAt)} · {result.appliedBy}
            </p>
            <p>
              O resultado preserva a execução original. A coluna “Analytics” do
              histórico informa se os registros ainda são utilizados; dados
              oficiais posteriores e revisões podem substituí-los ou retirá-los
              da leitura.
            </p>
            <Rows rows={result.rows} />
          </section>
        )}
      </QueryState>
      {save.error && (
        <p className="error" role="alert">
          {save.error.message}
        </p>
      )}
    </>
  );
}
