import { useId, useRef } from "react";
import { createPortal } from "react-dom";
import { dateTime, format, type Metric } from "../lib/api";
import { MetricView } from "./ui";
interface War {
  source?: string;
  id: number;
  seasonId: number;
  sectionIndex: number;
  publishedAt: string;
  startDate: string | null;
  endDate: string | null;
  clanPlacement?: number | null;
  clanScore: number | null;
  fame: number | null;
  clanTrophyChange?: number | null;
  capturedAt: string;
}
interface Participant {
  source?: string;
  capturedAt?: string;
  playerTag: string;
  name: string;
  contribution: number | null;
  decksUsed: number | null;
  decksUsedToday: number | null;
  boatAttacks: number | null;
  repairPoints: number | null;
  battlesPlayed: number | null;
  wins: number | null;
  losses: number | null;
}
export function WarEvidence({
  participant: p,
  war,
  participation,
}: {
  participant: Participant;
  war: War;
  participation?: Metric;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const fields = [
    ["contribution", "Pontuação na guerra"],
    ["decksUsed", "Decks usados na guerra"],
    ["decksUsedToday", "Decks usados no dia da captura"],
    ["boatAttacks", "Ataques a barcos"],
    ["repairPoints", "Pontos de reparo"],
    ["battlesPlayed", "Batalhas disputadas"],
    ["wins", "Vitórias"],
    ["losses", "Derrotas"],
  ] as const;
  return (
    <>
      <button
        type="button"
        aria-haspopup="dialog"
        onClick={() => dialog.current?.showModal()}
      >
        Evidências
      </button>
      {createPortal(
        <dialog
          ref={dialog}
          className="metric-dialog"
          aria-labelledby={titleId}
          onClick={(e) => {
            if (e.target !== e.currentTarget) return;
            const r = e.currentTarget.getBoundingClientRect();
            if (
              e.clientX < r.left ||
              e.clientX > r.right ||
              e.clientY < r.top ||
              e.clientY > r.bottom
            )
              e.currentTarget.close();
          }}
        >
          <header className="metric-dialog-header">
            <div>
              <div className="eyebrow">REGISTRO DA GUERRA</div>
              <h2 id={titleId}>Evidências de {p.name}</h2>
            </div>
            <button
              type="button"
              className="dialog-close"
              autoFocus
              aria-label="Fechar evidências"
              onClick={() => dialog.current?.close()}
            >
              ×
            </button>
          </header>
          <div className="metric-dialog-body">
            <p>
              {p.source === "ROYALEAPI_IMPORT"
                ? "Dados de backfill RoyaleAPI confirmado; o CSV pode representar um período incompleto."
                : "Dados da API oficial, preservados na captura desta guerra."}{" "}
              Campos não informados não significam zero nem falha do jogador.
            </p>
            <dl className="metric-context">
              <div>
                <dt>Guerra analisada</dt>
                <dd>
                  Temporada {war.seasonId} · seção {war.sectionIndex}
                </dd>
              </div>
              <div>
                <dt>Jogador</dt>
                <dd>
                  {p.name} · {p.playerTag}
                </dd>
              </div>
              <div>
                <dt>Publicação</dt>
                <dd>{dateTime(war.publishedAt)}</dd>
              </div>
              <div>
                <dt>
                  {p.source === "ROYALEAPI_IMPORT"
                    ? "Registro local da importação (não é data da batalha)"
                    : "Captura do registro"}
                </dt>
                <dd>{dateTime(p.capturedAt || war.capturedAt)}</dd>
              </div>
              {fields.map(([key, title]) => (
                <div key={key}>
                  <dt>{title}</dt>
                  <dd>
                    {p[key] == null
                      ? "Não informado neste registro"
                      : format(p[key])}
                  </dd>
                </div>
              ))}
            </dl>
            {participation && (
              <MetricView
                name="Participação nesta guerra"
                metric={participation}
              />
            )}
            <details className="metric-technical">
              <summary>Consultar registro técnico original</summary>
              <p>
                Os nomes abaixo identificam os campos do registro armazenado.
              </p>
              <dl>
                <dt>Origem dos valores de guerra</dt>
                <dd>{p.source || "DIRECT_API"}</dd>
                <dt>Identificador local da guerra</dt>
                <dd>{war.id}</dd>
                {Object.entries(p).map(([key, value]) => (
                  <div key={key}>
                    <dt>{key}</dt>
                    <dd>{value == null ? "Não informado" : String(value)}</dd>
                  </div>
                ))}
              </dl>
            </details>
          </div>
        </dialog>,
        document.body,
      )}
    </>
  );
}
