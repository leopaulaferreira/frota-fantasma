import { useState } from "react";
import { Link } from "react-router-dom";
import type { Report } from "../lib/api";
import { date, format } from "../lib/api";
import { elderAverage } from "../lib/elderAverage";

const labels = {
  meets: "Atende à média",
  below: "Abaixo da meta",
  unknown: "Dados insuficientes",
};
export function ElderAverage({ report }: { report: Report }) {
  const [status, setStatus] = useState("");
  const [search, setSearch] = useState("");
  const rows = report.players
    .filter((p) => p.currentMember && p.role === "member")
    .map((p) => ({ p, ...elderAverage(p) }))
    .sort(
      (a, b) =>
        (b.average ?? -1) - (a.average ?? -1) || a.p.tag.localeCompare(b.p.tag),
    );
  const visible = rows.filter(
    (row) =>
      (!status || row.status === status) &&
      (row.p.name + row.p.tag)
        .toLocaleLowerCase("pt-BR")
        .includes(search.trim().toLocaleLowerCase("pt-BR")),
  );
  return (
    <section className="panel" aria-label="Média para Ancião">
      <h2>Ancião: média mínima de 2.400 pontos nas últimas 4 guerras</h2>
      <p>
        Somente guerras encerradas disponíveis no histórico do clã. A corrida
        atual não entra. São necessárias quatro pontuações conhecidas; ausência
        de registro não é zero. Doações, tempo no clã e alertas não alteram este
        filtro de pontuação.
      </p>
      <div className="toolbar">
        <label>
          Média nas quatro guerras
          <select
            aria-label="Filtrar média para Ancião"
            value={status}
            onChange={(e) => setStatus(e.target.value)}
          >
            <option value="">Todos ({rows.length})</option>
            {Object.entries(labels).map(([key, label]) => (
              <option key={key} value={key}>
                {label} ({rows.filter((r) => r.status === key).length})
              </option>
            ))}
          </select>
        </label>
        <label>
          Buscar por nome ou tag
          <input value={search} onChange={(e) => setSearch(e.target.value)} />
        </label>
      </div>
      <p role="status">{visible.length} membros sem cargo nesta seleção</p>
      {visible.map((row) => (
        <article className="panel" key={row.p.tag}>
          <h3>
            <Link to={`/membros/${encodeURIComponent(row.p.tag)}`}>
              {row.p.name}
            </Link>{" "}
            · {labels[row.status]}
          </h3>
          <p>
            <strong>
              Média:{" "}
              {row.average === null
                ? "indisponível"
                : `${format(row.average)} pontos`}
            </strong>{" "}
            · Meta: 2.400 pontos
          </p>
          <ol>
            {row.wars.map((w) => (
              <li key={w.warId}>
                <Link to={`/guerras/${w.warId}`}>
                  Publicada em {date(w.publishedAt)}
                </Link>
                :{" "}
                {w.points.value == null
                  ? "pontuação desconhecida"
                  : `${format(w.points.value)} pontos`}
              </li>
            ))}
          </ol>
          {row.average !== null ? (
            <p>
              Soma das quatro pontuações ÷ 4 = {format(row.average)}.{" "}
              {row.average < 2400
                ? `Faltam ${format(9600 - row.average * 4)} pontos no total para atingir a meta neste recorte.`
                : "Meta de pontuação atingida."}
            </p>
          ) : (
            <p>
              Não é possível confirmar a regra sem os quatro resultados.
              Consulte as fontes no perfil.
            </p>
          )}
        </article>
      ))}
      {!visible.length && <p>Nenhum membro nesta seleção.</p>}
    </section>
  );
}
