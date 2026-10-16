import { useId, useRef } from "react";
import { createPortal } from "react-dom";
import { Link, useParams } from "react-router-dom";
import {
  useApi,
  date,
  dateTime,
  format,
  rankRows,
  type Report,
  type Metric,
} from "../lib/api";
import {
  Heading,
  QueryState,
  Paged,
  Table,
  Empty,
  MetricView,
} from "../components/ui";
interface War {
  id: number;
  seasonId: number;
  sectionIndex: number;
  publishedAt: string;
  startDate: string | null;
  endDate: string | null;
  clanPlacement: number | null;
  clanScore: number | null;
  fame: number | null;
  clanTrophyChange: number | null;
  capturedAt: string;
}
interface Participant {
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
export function Wars() {
  const { id } = useParams();
  return (
    <>
      <Heading eyebrow="DIÁRIO DE BORDO" title="Guerras da Frota" />
      <p className="lede">
        Navegue pelas guerras registradas e consulte a contribuição de cada
        membro.
      </p>
      <div className="war-layout">
        <aside className="panel war-nav">
          <h2>Arquivo de guerras</h2>
          <Link className={!id ? "selected" : ""} to="/guerras">
            Corrida atual
          </Link>
          <Paged<War>
            path="/wars"
            render={(items) => (
              <nav>
                {items.map((w) => (
                  <Link
                    key={w.id}
                    className={String(w.id) === id ? "selected" : ""}
                    to={"/guerras/" + w.id}
                  >
                    <b>
                      Temporada {w.seasonId} · seção {w.sectionIndex}
                    </b>
                    <small>Publicada em {date(w.publishedAt)}</small>
                  </Link>
                ))}
              </nav>
            )}
          />
        </aside>
        <section className="war-body">
          {id ? <WarDetail id={id} /> : <CurrentWar />}
        </section>
      </div>
    </>
  );
}
function WarDetail({ id }: { id: string }) {
  const q = useApi<{ war: War; participants: Participant[] }>("/wars/" + id);
  const analytics = useApi<Report>("/analytics");
  return (
    <QueryState query={q}>
      {q.data && (
        <>
          <div className="panel">
            <small>GUERRA REGISTRADA #{q.data.war.id}</small>
            <h2>
              Temporada {q.data.war.seasonId} · seção {q.data.war.sectionIndex}
            </h2>
            <p>
              Publicação: {date(q.data.war.publishedAt)} · Captura:{" "}
              {date(q.data.war.capturedAt)}
            </p>
            <p className="muted">
              Período oficial: {date(q.data.war.startDate)} →{" "}
              {date(q.data.war.endDate)}
            </p>
            <div className="facts">
              {(
                [
                  "clanPlacement",
                  "clanScore",
                  "fame",
                  "clanTrophyChange",
                ] as const
              ).map((k, i) => (
                <div key={k}>
                  <small>
                    {
                      [
                        "Posição do clã",
                        "Score do clã",
                        "Fama",
                        "Variação de troféus",
                      ][i]
                    }
                  </small>
                  <strong>{format(q.data!.war[k])}</strong>
                </div>
              ))}
            </div>
          </div>
          <h2>Ranking da guerra</h2>
          <p className="muted">
            Pontuação oficial em ordem decrescente. Empates compartilham
            posição; pontuação desconhecida permanece sem classificação. Inclui
            ex-membros registrados.
          </p>
          <Table headers={["Posição", "Membro", "Pontos", "Decks", "Detalhes"]}>
            {rankRows(q.data.participants).map((p) => (
              <tr key={p.playerTag}>
                <td className="rank">{p.rank === null ? "—" : p.rank + "º"}</td>
                <td>
                  <Link to={"/membros/" + encodeURIComponent(p.playerTag)}>
                    {p.name}
                  </Link>
                  <small>{p.playerTag}</small>
                </td>
                <td>{format(p.contribution)}</td>
                <td>{format(p.decksUsed)}</td>
                <td>
                  <WarEvidence
                    participant={p}
                    war={q.data!.war}
                    participation={
                      analytics.data?.players
                        .find((x) => x.tag === p.playerTag)
                        ?.wars.find((w) => String(w.warId) === id)
                        ?.participation
                    }
                  />
                </td>
              </tr>
            ))}
          </Table>
          {!q.data.participants.length && <Empty />}
        </>
      )}
    </QueryState>
  );
}
function WarEvidence({
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
              Dados da API oficial, preservados na captura desta guerra. Campos
              não informados não significam zero nem falha do jogador.
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
                <dt>Captura do registro</dt>
                <dd>{dateTime(war.capturedAt)}</dd>
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
                <dd>DIRECT_API</dd>
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
function CurrentWar() {
  const q = useApi<{
    snapshot: { capturedAt: string; state: string; periodType: string };
    race: {
      clan?: {
        name: string;
        participants?: {
          tag: string;
          name: string;
          fame: number | null;
          decksUsed: number | null;
          decksUsedToday: number | null;
        }[];
      };
    };
  }>("/wars/current");
  return (
    <QueryState query={q}>
      {q.data && (
        <>
          <div className="panel">
            <small>ÚLTIMA CAPTURA DA CORRIDA ATUAL</small>
            <h2>{q.data.race.clan?.name || "Corrida atual"}</h2>
            <p>
              {format(q.data.snapshot.state)} ·{" "}
              {format(q.data.snapshot.periodType)}
            </p>
            <p>
              Capturada em{" "}
              {new Date(q.data.snapshot.capturedAt).toLocaleString("pt-BR")}
            </p>
            <p className="muted">
              Dados parciais da corrida. A classificação final aparece no
              arquivo quando publicada pela API.
            </p>
          </div>
          <Table
            headers={[
              "Posição",
              "Membro",
              "Pontos observados",
              "Decks usados",
              "Decks hoje",
            ]}
          >
            {rankRows(
              (q.data.race.clan?.participants ?? []).map((p) => ({
                ...p,
                contribution: p.fame,
                playerTag: p.tag,
              })),
            ).map((p) => (
              <tr key={p.tag}>
                <td className="rank">{p.rank === null ? "—" : p.rank + "º"}</td>
                <td>
                  <Link to={"/membros/" + encodeURIComponent(p.tag)}>
                    {p.name}
                  </Link>
                  <small>{p.tag}</small>
                </td>
                <td>{format(p.fame)}</td>
                <td>{format(p.decksUsed)}</td>
                <td>{format(p.decksUsedToday)}</td>
              </tr>
            ))}
          </Table>
        </>
      )}
    </QueryState>
  );
}
