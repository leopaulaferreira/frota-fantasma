import {
  ImportIdentification,
  identificationStatus,
} from "./ImportIdentification";
import { ImportBackfill } from "./ImportBackfill";
import { useRef, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api, dateTime } from "../lib/api";
import { Paged, Table } from "./ui";

interface Analysis {
  filename: string;
  rowsReceived: number;
  playersIdentified: number;
  rowsValid: number;
  rowsNew: number;
  rowsExisting: number;
  rowsConflicted: number;
  rowsInvalid: number;
  rowsUnmatched: number;
  warnings: string[];
  errors: string[];
  rows: {
    line: number;
    playerTag: string | null;
    status: string;
    reason: string;
  }[];
  existingBatchId: string | null;
}
interface Preview {
  analysis: Analysis;
  previewToken: string;
}
interface Batch {
  id: string;
  source: string;
  filename: string;
  importedAt: string;
  rowsImported: number;
  rowsSkipped: number;
  rowsConflicted: number;
  rowsInvalid: number;
  rowsUnmatched: number;
  status: string;
  analysis: Analysis;
  incorporation?: {
    status: string;
    rowsNew: number;
    rowsDuplicated: number;
    rowsConflicted: number;
    rowsIgnored: number;
    influencesAnalytics: boolean;
    activeRecords: number;
  } | null;
}
interface Result {
  batch: Batch;
  alreadyProcessed: boolean;
}
const status = (value: string) =>
  ({
    PENDING_IDENTIFICATION: "Pendente de identificação",
    UNMATCHED: "Sem correspondência",
    INVALID: "Inválido",
  })[value] || value;

function AnalysisSummary({ analysis }: { analysis: Analysis }) {
  return (
    <>
      <p>
        <strong>Arquivo: {analysis.filename}</strong>
      </p>
      <p>
        {analysis.playersIdentified} jogadores identificados ·{" "}
        {analysis.rowsReceived} registros analisados · {analysis.rowsValid} com
        campos válidos
      </p>
      <div className="metrics">
        {[
          ["Novos", analysis.rowsNew],
          ["Já existentes", analysis.rowsExisting],
          ["Conflitos", analysis.rowsConflicted],
          ["Inválidos", analysis.rowsInvalid],
          ["Sem correspondência segura", analysis.rowsUnmatched],
        ].map(([label, count]) => (
          <div className="panel" key={label}>
            <strong>{count}</strong>
            <p>{label}</p>
          </div>
        ))}
      </div>
      {analysis.warnings.map((w) => (
        <p className="callout" key={w}>
          {w}
        </p>
      ))}
      {analysis.errors.map((e) => (
        <p className="error" role="alert" key={e}>
          {e}
        </p>
      ))}
      <details>
        <summary>Ver resultados por registro</summary>
        <Table headers={["Registro", "Jogador", "Resultado", "Motivo"]}>
          {analysis.rows.map((row) => (
            <tr key={row.line}>
              <td>{row.line}</td>
              <td>{row.playerTag || "—"}</td>
              <td>{status(row.status)}</td>
              <td>{row.reason}</td>
            </tr>
          ))}
        </Table>
      </details>
    </>
  );
}
export function RoyaleApiImport() {
  const dialog = useRef<HTMLDialogElement>(null);
  const input = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File>();
  const [localError, setLocalError] = useState("");
  const client = useQueryClient();
  const preview = useMutation({
    mutationFn: (selected: File) => {
      const body = new FormData();
      body.set("file", selected);
      return api<Preview>("/imports/royaleapi/preview", body);
    },
  });
  const confirm = useMutation({
    mutationFn: ({ selected, token }: { selected: File; token: string }) => {
      const body = new FormData();
      body.set("file", selected);
      body.set("previewToken", token);
      return api<Result>("/imports/royaleapi", body);
    },
    onSuccess: () =>
      client.invalidateQueries({
        predicate: (q) =>
          String(q.queryKey[0]).startsWith("/imports/royaleapi"),
      }),
  });
  const busy = preview.isPending || confirm.isPending;
  function select(selected?: File) {
    preview.reset();
    confirm.reset();
    setFile(undefined);
    setLocalError("");
    if (!selected) return;
    if (
      !selected.name.toLowerCase().endsWith(".csv") ||
      selected.size === 0 ||
      selected.size > 512 * 1024
    ) {
      setLocalError("Selecione um CSV não vazio de até 512 KiB.");
      return;
    }
    setFile(selected);
  }
  function reset() {
    select();
    if (input.current) input.current.value = "";
  }
  return (
    <section className="panel" aria-labelledby="external-data">
      <h2 id="external-data">Dados externos</h2>
      <p>Complete o histórico da Frota com arquivos exportados do RoyaleAPI.</p>
      <p className="muted">
        Importação manual complementar. A Supercell continua sendo a fonte
        principal. Arquivos sem identificação segura ficam pendentes e não
        entram nas métricas.
      </p>
      <button
        onClick={() => {
          reset();
          dialog.current?.showModal();
        }}
      >
        Importar CSV
      </button>
      <h3>Histórico de importações</h3>
      <Paged<Batch>
        path="/imports/royaleapi"
        render={(items) => (
          <Table
            headers={[
              "Data/hora",
              "Arquivo",
              "Origem",
              "Novos",
              "Ignorados",
              "Conflitos",
              "Inválidos",
              "Pendentes na entrada",
              "Identificação atual",
              "Histórico / Analytics",
              "Ações",
            ]}
          >
            {items.map((batch) => (
              <tr key={batch.id}>
                <td>{dateTime(batch.importedAt)}</td>
                <td>
                  <details>
                    <summary>{batch.filename}</summary>
                    <AnalysisSummary analysis={batch.analysis} />
                  </details>
                </td>
                <td>{batch.source}</td>
                <td>{batch.rowsImported}</td>
                <td>{batch.rowsSkipped}</td>
                <td>{batch.rowsConflicted}</td>
                <td>{batch.rowsInvalid}</td>
                <td>{batch.rowsUnmatched}</td>
                <td>{identificationStatus(batch.status)}</td>
                <td>
                  {batch.incorporation ? (
                    <>
                      <strong>
                        {batch.incorporation.status === "PARTIAL"
                          ? "Incorporação com pendências"
                          : "Incorporado"}
                      </strong>
                      <p>
                        {batch.incorporation.rowsNew} novos ·{" "}
                        {batch.incorporation.rowsDuplicated} duplicados ·{" "}
                        {batch.incorporation.rowsConflicted} conflitos ·{" "}
                        {batch.incorporation.rowsIgnored} ignorados
                      </p>
                      <p>
                        Analytics:{" "}
                        {batch.incorporation.influencesAnalytics
                          ? "Sim"
                          : "Não"}{" "}
                        · {batch.incorporation.activeRecords} registros
                        utilizados
                      </p>
                    </>
                  ) : (
                    <>
                      Arquivo importado · não incorporado<p>Analytics: Não</p>
                    </>
                  )}
                </td>
                <td>
                  <ImportIdentification
                    batchId={batch.id}
                    status={batch.status}
                  />
                  {batch.status === "IDENTIFIED" && (
                    <ImportBackfill
                      batchId={batch.id}
                      applied={!!batch.incorporation}
                    />
                  )}
                </td>
              </tr>
            ))}
          </Table>
        )}
      />
      <dialog
        ref={dialog}
        className="metric-dialog"
        aria-labelledby="import-title"
        onCancel={(event) => {
          if (busy) event.preventDefault();
        }}
      >
        <header className="metric-dialog-header">
          <h2 id="import-title">Importar CSV do RoyaleAPI</h2>
        </header>
        <div className="metric-dialog-body" aria-busy={busy}>
          {!confirm.data && (
            <>
              <div
                className="panel"
                onDragOver={(event) => event.preventDefault()}
                onDrop={(event) => {
                  event.preventDefault();
                  if (!busy) select(event.dataTransfer.files[0]);
                }}
              >
                <label>
                  Selecionar CSV
                  <input
                    ref={input}
                    type="file"
                    accept=".csv,text/csv"
                    disabled={busy}
                    onChange={(event) => select(event.target.files?.[0])}
                  />
                </label>
                <p className="muted">
                  Ou arraste um CSV aqui. UTF-8, até 512 KiB e 2.000 registros.
                </p>
                {file && <p>{file.name}</p>}
              </div>
              {!preview.data && (
                <button
                  disabled={!file || busy}
                  onClick={() => file && preview.mutate(file)}
                >
                  {preview.isPending ? "Analisando…" : "Analisar arquivo"}
                </button>
              )}
              {preview.data && (
                <AnalysisSummary analysis={preview.data.analysis} />
              )}
              {preview.data?.analysis.existingBatchId && (
                <p role="status">
                  Este arquivo já foi registrado. Confirmar consultará o lote
                  anterior sem duplicar registros.
                </p>
              )}
            </>
          )}
          {confirm.data && (
            <div role="status">
              <h3>
                {confirm.data.alreadyProcessed
                  ? "Arquivo já registrado"
                  : "Lote registrado com pendências"}
              </h3>
              <p>
                {confirm.data.batch.rowsImported} registros incorporados às
                métricas; {confirm.data.batch.rowsUnmatched} pendentes de
                identificação.
              </p>
              <AnalysisSummary analysis={confirm.data.batch.analysis} />
            </div>
          )}
          {(localError || preview.error || confirm.error) && (
            <p className="error" role="alert">
              {localError || preview.error?.message || confirm.error?.message}
            </p>
          )}
          <div className="toolbar">
            <button disabled={busy} onClick={() => dialog.current?.close()}>
              {confirm.data ? "Fechar" : "Cancelar"}
            </button>
            {!confirm.data && preview.data && (
              <button
                className="primary"
                disabled={
                  busy || preview.data.analysis.rowsInvalid > 0 || !file
                }
                onClick={() =>
                  file &&
                  preview.data &&
                  confirm.mutate({
                    selected: file,
                    token: preview.data.previewToken,
                  })
                }
              >
                {confirm.isPending
                  ? "Registrando…"
                  : preview.data.analysis.existingBatchId
                    ? "Consultar lote anterior"
                    : "Registrar pendências (sem alterar métricas)"}
              </button>
            )}
          </div>
        </div>
      </dialog>
    </section>
  );
}
