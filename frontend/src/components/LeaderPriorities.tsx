import { useState } from "react";
import { Link } from "react-router-dom";
import { type Report, useApi, tagPath, dateTime } from "../lib/api";
import { type ActiveNotes, leaderSignals } from "../lib/leadership";
import { Empty, MetricView, Person, QueryState } from "./ui";
import { NoteText } from "./LeaderNotes";
export function LeaderPriorities({ report }: { report: Report }) {
  const notes = useApi<ActiveNotes>("/leadership/notes/active", 60000);
  const [page, setPage] = useState(0);
  const [filter, setFilter] = useState("");
  const [objective, setObjective] = useState("2400");
  const [search, setSearch] = useState("");
  const rows = report.players
    .filter((p) => p.currentMember)
    .map((player) => ({
      player,
      signals: leaderSignals(
        player,
        notes.data?.notes || [],
        Number(objective),
      ),
      notes: notes.data?.notes.filter((n) => n.playerTag === player.tag) || [],
    }))
    .filter(
      (r) =>
        r.signals.length &&
        (!filter || r.signals.some((s) => s.label === filter)) &&
        (r.player.name + r.player.tag)
          .toLowerCase()
          .includes(search.toLowerCase()),
    )
    .sort(
      (a, b) =>
        a.signals[0].order - b.signals[0].order ||
        a.player.tag.localeCompare(b.player.tag),
    );
  const currentPage = Math.min(
    page,
    Math.max(0, Math.ceil(rows.length / 10) - 1),
  );
  return (
    <section
      className="leader-priorities"
      aria-labelledby="leader-priorities-title"
    >
      <h2 id="leader-priorities-title">Prioridades do líder</h2>
      <p className="muted">
        Acompanhamentos com data atingida aparecem primeiro, seguidos de
        revisão, atenção e reconhecimento. Os sinais orientam a leitura; a
        decisão é sua. Análise de {dateTime(report.generatedAt)}.
      </p>
      <div className="toolbar">
        <input
          aria-label="Buscar nas prioridades"
          placeholder="Nome ou tag…"
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPage(0);
          }}
        />
        <select
          aria-label="Filtrar prioridade"
          value={filter}
          onChange={(e) => {
            setFilter(e.target.value);
            setPage(0);
          }}
        >
          <option value="">Todos os sinais</option>
          {[
            "Retomar acompanhamento",
            "Revisar contexto",
            "Conversar e acompanhar",
            "Entender queda recente",
            "Considerar para Ancião",
            "Reconhecer evolução",
            "Perto do objetivo",
            "Conhecer melhor o membro",
            "Acompanhamento registrado",
          ].map((s) => (
            <option key={s}>{s}</option>
          ))}
        </select>
        <label>
          Objetivo de referência
          <input
            aria-label="Objetivo das prioridades"
            type="number"
            min="1"
            value={objective}
            onChange={(e) => {
              setObjective(e.target.value);
              setPage(0);
            }}
          />
        </label>
      </div>
      <p className="muted">
        “Perto do objetivo” significa até 5% abaixo da referência, com quatro
        pontuações conhecidas e amostra suficiente. A referência vale só nesta
        consulta. Médias incompletas não geram esse sinal.
      </p>
      <QueryState query={notes}>
        <>
          <p>
            {rows.length} membros nesta seleção · Notas verificadas em{" "}
            {dateTime(notes.data?.generatedAt)}
          </p>
          <ul className="priority-list">
            {rows
              .slice(currentPage * 10, currentPage * 10 + 10)
              .map(({ player, signals, notes: context }) => (
                <li key={player.tag}>
                  <div className="priority-person">
                    <Person player={player} />
                    <Link to={`/membros/${tagPath(player.tag)}?tab=notas`}>
                      Abrir histórico e notas →
                    </Link>
                  </div>
                  <div>
                    <b>{signals.map((s) => s.label).join(" · ")}</b>
                    {signals.map((s) => (
                      <div className="priority-signal" key={s.label}>
                        <p>{s.reason}</p>
                        {s.metric && (
                          <MetricView
                            compact
                            name={s.label}
                            metric={s.metric}
                          />
                        )}
                      </div>
                    ))}
                    {context.length > 0 && (
                      <details>
                        <summary>
                          {context.length} anotações vigentes da liderança
                        </summary>
                        {context.map((n) => (
                          <NoteText key={n.id} note={n} />
                        ))}
                      </details>
                    )}
                  </div>
                </li>
              ))}
          </ul>
          {rows.length > 10 && (
            <div className="pagination">
              <span>
                Página {currentPage + 1} de {Math.ceil(rows.length / 10)} · 10
                membros por página
              </span>
              <button
                disabled={currentPage === 0}
                onClick={() => setPage(currentPage - 1)}
              >
                Prioridades anteriores
              </button>
              <button
                disabled={(currentPage + 1) * 10 >= rows.length}
                onClick={() => setPage(currentPage + 1)}
              >
                Próximas prioridades
              </button>
            </div>
          )}
          {!rows.length && (
            <Empty>
              Nenhum sinal nesta seleção. Isso não comprova ausência de
              problemas fora dos dados disponíveis.
            </Empty>
          )}
        </>
      </QueryState>
    </section>
  );
}
