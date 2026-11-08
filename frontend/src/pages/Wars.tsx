import { WarEvidence } from "../components/WarEvidence";
import { Fragment, useState } from "react";
import {
  Link,
  useNavigate,
  useParams,
  useSearchParams,
} from "react-router-dom";
import { useQuery, useQueries } from "@tanstack/react-query";
import {
  api,
  useApi,
  format,
  date,
  num,
  type Report,
  type Page,
} from "../lib/api";
import { Heading, QueryState, MetricView } from "../components/ui";
import {
  warLabel,
  known,
  totals,
  percent,
  recurrence,
  range,
  type WarSummary,
  type WarDetail,
  type WarPlayer,
  type WarEvent,
} from "../lib/warAnalysis";
async function events() {
  const result: WarEvent[] = [];
  for (let page = 0; ; page++) {
    const r = await api<Page<WarEvent>>(`/clan/events?page=${page}&size=100`);
    result.push(...r.items);
    if (result.length >= r.totalElements || !r.items.length) return result;
  }
}
export function Wars() {
  const navigate = useNavigate();
  const { id } = useParams();
  const [url, setUrl] = useSearchParams();
  const [open, setOpen] = useState<string | null>(null);
  const set = (key: string, value: string) =>
    setUrl((p) => {
      const q = new URLSearchParams(p);
      value ? q.set(key, value) : q.delete(key);
      return q;
    });
  const v = (key: string) => url.get(key) || "";
  const count = [2, 4, 6, 10].includes(Number(v("period")))
    ? Number(v("period"))
    : 4;
  const list = useQuery({
    queryKey: ["war-analysis-archive"],
    queryFn: async () => {
      const items: WarSummary[] = [];
      for (let page = 0; ; page++) {
        const r = await api<Page<WarSummary>>(`/wars?page=${page}&size=100`);
        items.push(...r.items);
        if (items.length >= r.totalElements || !r.items.length) break;
      }
      const unique = new Map<string, WarSummary>();
      for (const w of items) {
        const key = w.seasonId + ":" + w.sectionIndex;
        const existing = unique.get(key);
        if (
          !existing ||
          (w.source === "DIRECT_API" && existing.source !== "DIRECT_API")
        )
          unique.set(key, w);
      }
      return {
        items: [...unique.values()].sort((a, b) =>
          b.publishedAt.localeCompare(a.publishedAt),
        ),
        totalElements: unique.size,
      };
    },
  });
  const roster = useApi<{ tag: string; name: string }[]>("/clan/members");
  const analytics = useApi<Report>("/analytics");
  const zone = String(
    analytics.data?.recommendationRules?.settings.analytics.timezone ||
      "America/Sao_Paulo",
  );
  const membership = useQuery({
    queryKey: ["war-analysis-events"],
    queryFn: events,
  });
  const current = useApi<{
    snapshot: { capturedAt: string; periodType: string };
    race: {
      clan?: {
        fame?: number;
        clanScore?: number;
        participants?: {
          tag: string;
          name: string;
          fame: number | null;
          decksUsed: number | null;
        }[];
      };
    };
  }>("/wars/current");
  const selected = useQuery({
    queryKey: ["war-detail", id],
    queryFn: () => api<WarDetail>("/wars/" + id),
    enabled: !!id,
  });
  const summaries = (list.data?.items || [])
    .filter(
      (w) =>
        !id || !selected.data || w.publishedAt <= selected.data.war.publishedAt,
    )
    .slice(0, Math.max(4, count));
  const history = useQueries({
    queries: summaries.map((w) => ({
      queryKey: ["war-detail", String(w.id)],
      queryFn: () => api<WarDetail>("/wars/" + w.id),
    })),
  });
  const historyReady = history.length > 0 && history.every((q) => q.isSuccess);
  const wars = historyReady ? history.map((q) => q.data!) : [];
  const comparison = useQuery({
    queryKey: ["war-detail", v("compare")],
    queryFn: () => api<WarDetail>("/wars/" + v("compare")),
    enabled: /^\d+$/.test(v("compare")),
  });
  const partial = !id;
  const base: WarPlayer[] = id
    ? selected.data?.participants || []
    : (current.data?.race.clan?.participants || []).map((p) => ({
        playerTag: p.tag,
        name: p.name,
        contribution: p.fame,
        decksUsed: p.decksUsed,
      }));
  const tags = roster.data ? new Set(roster.data.map((m) => m.tag)) : null;
  const prior = partial ? wars[0] : wars.find((w) => w.war.id !== Number(id));
  const official = wars;
  const anchor = partial
    ? analytics.data?.generatedAt || new Date().toISOString()
    : selected.data?.war.publishedAt || "";
  const universe =
    v("membership") === "roster" && roster.data
      ? [
          ...base,
          ...roster.data
            .filter((m) => !base.some((p) => p.playerTag === m.tag))
            .map((m) => ({
              playerTag: m.tag,
              name: m.name,
              contribution: null,
              decksUsed: null,
            })),
        ]
      : base;
  const data = universe.map((p) => {
    const player = analytics.data?.players.find((x) => x.tag === p.playerTag);
    const donor = player?.donations.find(
      (d) => d.week === analytics.data?.week,
    )?.observedDonations;
    const previous =
      prior?.participants.find((x) => x.playerTag === p.playerTag)
        ?.contribution ?? null;
    const samples = official
      .slice(0, 4)
      .map((w) =>
        known(
          w.participants.find((x) => x.playerTag === p.playerTag)?.contribution,
        ),
      )
      .filter((x): x is number => x !== null);
    const rec = recurrence(
      p.playerTag,
      official.slice(0, count),
      membership.data || [],
      anchor,
    );
    return {
      ...p,
      previous,
      variation:
        p.contribution !== null && previous !== null
          ? p.contribution - previous
          : null,
      averageSample: samples.length,
      average: samples.length
        ? samples.reduce((a, b) => a + b, 0) / samples.length
        : null,
      donation: num(donor),
      donor,
      rec,
      situation:
        p.contribution === null
          ? "Dados insuficientes"
          : rec.twoBelow || rec.twoZero || rec.threeFour
            ? "Reincidência confirmada"
            : p.contribution < 1600
              ? partial
                ? "Parcial abaixo da meta"
                : "Abaixo da meta"
              : "Meta atingida",
    };
  });
  const rows = data
    .filter((p) => {
      if (
        !`${p.name} ${p.playerTag}`
          .toLocaleLowerCase()
          .includes(v("search").toLocaleLowerCase())
      )
        return false;
      if (
        ["current", "former", "roster"].includes(v("membership")) &&
        (!tags ||
          (v("membership") === "former"
            ? tags.has(p.playerTag)
            : !tags.has(p.playerTag)))
      )
        return false;
      const points = known(p.contribution),
        decks = known(p.decksUsed);
      if (
        (v("points") === "zero" && points !== 0) ||
        (v("points") === "low" && (points === null || points >= 1600)) ||
        (v("points") === "target" && (points === null || points < 1600)) ||
        (v("points") === "range" && !range(points, v("min"), v("max")))
      )
        return false;
      if (v("decks")) {
        const [a, b] = v("decks").split(":");
        if (!range(decks, a, b)) return false;
      }
      if (
        v("recurrence") &&
        (!historyReady ||
          !membership.isSuccess ||
          !p.rec[v("recurrence") as keyof typeof p.rec])
      )
        return false;
      if (
        v("donations") === "zero" &&
        !(
          p.donation === 0 &&
          p.donor?.quality === "AVAILABLE" &&
          !p.donor.incompletePeriod
        )
      )
        return false;
      if (
        v("donations") === "low" &&
        (p.donation === null || p.donation >= 50 || p.donor?.incompletePeriod)
      )
        return false;
      if (
        v("donations") === "range" &&
        (!range(p.donation, v("dmin"), v("dmax")) || p.donor?.incompletePeriod)
      )
        return false;
      return true;
    })
    .sort((a, b) => {
      const key = v("sort") || "contribution";
      const x = a[key as keyof typeof a],
        y = b[key as keyof typeof b];
      if (x == null || y == null)
        return x == y
          ? a.playerTag.localeCompare(b.playerTag)
          : x == null
            ? 1
            : -1;
      return (
        (typeof x === "number" && typeof y === "number"
          ? x - y
          : String(x).localeCompare(String(y))) *
          (v("direction") === "asc" ? 1 : -1) ||
        a.playerTag.localeCompare(b.playerTag)
      );
    });
  const columns = [
    ["contribution", "Pontos"],
    ["decksUsed", "Decks"],
    ["previous", "Guerra anterior"],
    ["variation", "Variação"],
    ["average", "Média de 4"],
    ["donation", "Doações"],
    ["situation", "Situação"],
  ] as const;
  const hidden = v("hide").split(",");
  const select = (key: string, label: string, options: string[][]) => (
    <label>
      {label}
      <select
        aria-label={label}
        value={v(key)}
        onChange={(e) => set(key, e.target.value)}
      >
        <option value="">
          {key === "period" ? "Últimas 4 (padrão)" : "Todos"}
        </option>
        {options.map(([value, text]) => (
          <option key={value} value={value}>
            {text}
          </option>
        ))}
      </select>
    </label>
  );
  const input = (key: string, label: string, type = "number") => (
    <label>
      {label}
      <input
        type={type}
        value={v(key)}
        onChange={(e) => set(key, e.target.value)}
      />
    </label>
  );
  const a = totals(base),
    b = comparison.data
      ? totals(comparison.data.participants)
      : prior
        ? totals(prior.participants)
        : null;
  return (
    <>
      <Heading title="Guerras da Frota" eyebrow="DIÁRIO DE BORDO" />
      <div className="toolbar">
        <label>
          Guerra selecionada
          <select
            value={id || ""}
            onChange={(e) => {
              navigate(
                "/guerras" +
                  (e.target.value ? "/" + e.target.value : "") +
                  "?" +
                  url.toString(),
              );
            }}
          >
            <option value="">Corrida atual · parcial</option>
            {list.data?.items.map((w) => (
              <option key={w.id} value={w.id}>
                {warLabel(w, zone)}
                {w.source === "ROYALEAPI_IMPORT" ? " · Importação" : ""}
              </option>
            ))}
          </select>
        </label>
        {select("period", "Período de análise", [
          ["2", "Últimas 2"],
          ["4", "Últimas 4"],
          ["6", "Últimas 6"],
          ["10", "Últimas 10"],
        ])}
        <label>
          Comparar com
          <select
            value={v("compare")}
            onChange={(e) => set("compare", e.target.value)}
          >
            <option value="">Guerra anterior</option>
            {list.data?.items.map((w) => (
              <option key={w.id} value={w.id}>
                {warLabel(w, zone)}
              </option>
            ))}
          </select>
        </label>
      </div>
      <QueryState query={id ? selected : current}>
        <p className="callout">
          {partial
            ? "Captura parcial da corrida"
            : selected.data?.war.source === "ROYALEAPI_IMPORT"
              ? "Registro importado"
              : "Resultado oficial"}{" "}
          · Tipo: {partial ? current.data?.snapshot.periodType : "histórico"} ·
          Captura:{" "}
          {date(
            id
              ? selected.data?.war.capturedAt
              : current.data?.snapshot.capturedAt,
          )}
          .{" "}
          {id && (
            <>
              Publicação: {date(selected.data?.war.publishedAt)} · Período
              efetivo: {date(selected.data?.war.startDate)} →{" "}
              {date(selected.data?.war.endDate)}.
            </>
          )}{" "}
          Pontos ausentes não significam zero; 16 decks é uma referência, não
          disponibilidade comprovada.
        </p>
        <div className="facts">
          {[
            ["Soma observada de contribuições", a.sum],
            ["Meta atingida", a.target],
            ["Zero ataques explícito", a.zero],
            ["Decks observados", a.decks],
            [
              "Variação da soma (%)",
              a.complete && b?.complete ? percent(a.sum, b.sum) : null,
            ],
          ].map(([label, value]) => (
            <div key={String(label)}>
              <small>{label}</small>
              <strong>{format(value)}</strong>
            </div>
          ))}
        </div>
        <p className="muted">
          Amostra: {a.sample}/{a.total} participantes com pontos; {a.deckSample}
          /{a.total} com decks. Soma individual não é score oficial do clã.
          Variação usa populações que podem ser diferentes.
        </p>
        <details open={!!v("compare")}>
          <summary>Comparação lado a lado</summary>
          <div className="table-scroll analytics-table">
            <table>
              <thead>
                <tr>
                  <th>Indicador</th>
                  <th>Selecionada {partial ? "(parcial)" : ""}</th>
                  <th>
                    Comparação{" "}
                    {comparison.data?.war.source === "ROYALEAPI_IMPORT"
                      ? "(importação)"
                      : "(histórico)"}
                  </th>
                </tr>
              </thead>
              <tbody>
                {[
                  [
                    "Medalhas do clã (campo fame)",
                    id
                      ? selected.data?.war.fame
                      : current.data?.race.clan?.fame,
                    (comparison.data || prior)?.war.fame,
                  ],
                  [
                    "Score do clã (campo clanScore)",
                    id
                      ? selected.data?.war.clanScore
                      : current.data?.race.clan?.clanScore,
                    (comparison.data || prior)?.war.clanScore,
                  ],
                  ["Soma individual observada", a.sum, b?.sum],
                  ["Média por jogador com pontos", a.average, b?.average],
                  ["Zero ataques explícito", a.zero, b?.zero],
                  ["Meta atingida", a.target, b?.target],
                  ["Decks observados", a.decks, b?.decks],
                ].map(([label, x, y]) => (
                  <tr key={String(label)}>
                    <th>{label}</th>
                    <td>{format(x)}</td>
                    <td>{format(y)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p>
            Melhoraram:{" "}
            {base
              .filter((p) => {
                const old = (comparison.data || prior)?.participants.find(
                  (x) => x.playerTag === p.playerTag,
                );
                return (
                  p.contribution !== null &&
                  old?.contribution != null &&
                  p.contribution > old.contribution
                );
              })
              .map((p) => p.name + " (" + p.playerTag + ")")
              .join(", ") || "Nenhum confirmado"}
          </p>
          <p>
            Pioraram:{" "}
            {base
              .filter((p) => {
                const old = (comparison.data || prior)?.participants.find(
                  (x) => x.playerTag === p.playerTag,
                );
                return (
                  p.contribution !== null &&
                  old?.contribution != null &&
                  p.contribution < old.contribution
                );
              })
              .map((p) => p.name + " (" + p.playerTag + ")")
              .join(", ") || "Nenhum confirmado"}
          </p>
        </details>
        <div className="toolbar">
          {input("search", "Nome ou player_tag", "search")}
          {select("membership", "Vínculo", [
            ["current", "Membros atuais participantes"],
            ["former", "Ex-membros participantes"],
            ["present", "Presentes na guerra selecionada"],
            ["roster", "Elenco atual, inclusive sem registro"],
          ])}
          {select("points", "Pontuação", [
            ["zero", "Zero"],
            ["low", "Abaixo de 1.600"],
            ["target", "Meta atingida"],
            ["range", "Intervalo personalizado"],
          ])}
          {v("points") === "range" && (
            <>
              {input("min", "Pontos mínimos")}
              {input("max", "Pontos máximos")}
            </>
          )}
          {select("decks", "Decks usados", [
            ["0:0", "0"],
            ["1:4", "1–4"],
            ["5:8", "5–8"],
            ["9:15", "9–15"],
            ["16:16", "16"],
          ])}
          {select("recurrence", "Reincidência", [
            ["twoZeroPoints", "Duas guerras zeradas"],
            ["twoZero", "Duas guerras sem ataques"],
            ["twoBelow", "Duas consecutivas abaixo da meta"],
            ["threeFour", "Três das últimas quatro abaixo"],
          ])}
          {select("donations", "Doações", [
            ["zero", "Zero confirmado"],
            ["low", "Abaixo de 50 com cobertura"],
            ["range", "Intervalo com cobertura"],
          ])}
          {v("donations") === "range" && (
            <>
              {input("dmin", "Doações mínimas")}
              {input("dmax", "Doações máximas")}
            </>
          )}
          <button onClick={() => setUrl({})}>Limpar filtros</button>
        </div>
        <p className="muted">
          Doações referem-se à semana de {date(analytics.data?.week)}, não ao
          período da batalha selecionada. Filtros de doações exigem cobertura
          completa; diferenças zero em períodos incompletos não confirmam zero.
          Reincidência exige vínculo anterior à guerra; sem datas oficiais, usa
          margem conservadora de sete dias antes da publicação. Nomes iguais são
          separados pela tag.
        </p>
        {(!historyReady ||
          membership.isError ||
          analytics.isError ||
          roster.isError ||
          list.isError ||
          comparison.isError) && (
          <p role="status">
            Histórico, vínculo ou doações incompletos. Filtros dependentes podem
            não ter evidência suficiente.{" "}
            {history.some((q) => q.isError)
              ? "Falha ao carregar histórico."
              : ""}
          </p>
        )}
        <details>
          <summary>Colunas visíveis</summary>
          <div className="toolbar">
            {columns.map(([key, label]) => (
              <label key={key}>
                <input
                  type="checkbox"
                  checked={!hidden.includes(key)}
                  onChange={(e) =>
                    set(
                      "hide",
                      e.target.checked
                        ? hidden.filter((k) => k !== key).join(",")
                        : [...hidden, key].filter(Boolean).join(","),
                    )
                  }
                />
                {label}
              </label>
            ))}
          </div>
        </details>
        <p role="status">
          {rows.length} resultados · {universe.length} registros
        </p>
        <div className="table-scroll analytics-table war-player-table">
          <table>
            <thead>
              <tr>
                {[
                  ["name", "Jogador"],
                  ...columns.filter(([key]) => !hidden.includes(key)),
                ].map(([key, label]) => (
                  <th
                    key={key}
                    aria-sort={
                      v("sort") === key
                        ? v("direction") === "asc"
                          ? "ascending"
                          : "descending"
                        : "none"
                    }
                  >
                    <button
                      onClick={() => {
                        const q = new URLSearchParams(url);
                        q.set("sort", key);
                        q.set(
                          "direction",
                          v("sort") === key && v("direction") === "asc"
                            ? "desc"
                            : "asc",
                        );
                        setUrl(q);
                      }}
                    >
                      {label} ↕
                    </button>
                  </th>
                ))}
                <th>Evidências</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((p) => (
                <Fragment key={p.playerTag}>
                  <tr className="war-member-row">
                    <td>
                      <Link
                        className="analytics-name"
                        to={"/membros/" + encodeURIComponent(p.playerTag)}
                      >
                        {p.name}
                      </Link>
                      <small>
                        {p.playerTag}
                        {tags && !tags.has(p.playerTag) ? " · Ex-membro" : ""}
                      </small>
                    </td>
                    {columns
                      .filter(([key]) => !hidden.includes(key))
                      .map(([key]) => (
                        <td key={key}>
                          <span
                            className={
                              typeof p[key] === "number"
                                ? "analytics-number"
                                : "analytics-text"
                            }
                          >
                            {format(p[key])}
                          </span>
                          {key === "average" && (
                            <small>
                              {p.averageSample}/4 guerras com pontos
                            </small>
                          )}
                          {key === "donation" && p.donor?.incompletePeriod && (
                            <small>Limite inferior · incompleto</small>
                          )}
                        </td>
                      ))}
                    <td>
                      {selected.data && (
                        <WarEvidence
                          war={selected.data.war}
                          participant={{
                            decksUsedToday: null,
                            boatAttacks: null,
                            repairPoints: null,
                            battlesPlayed: null,
                            wins: null,
                            losses: null,
                            ...p,
                          }}
                        />
                      )}
                      <button
                        aria-expanded={open === p.playerTag}
                        onClick={() =>
                          setOpen(open === p.playerTag ? null : p.playerTag)
                        }
                      >
                        Evolução e contexto
                      </button>
                      <Link
                        to={"/comparar?tags=" + encodeURIComponent(p.playerTag)}
                      >
                        Comparar jogador
                      </Link>
                    </td>
                  </tr>
                  {open === p.playerTag && (
                    <tr>
                      <td
                        colSpan={
                          columns.filter(([key]) => !hidden.includes(key))
                            .length + 2
                        }
                      >
                        <h3>{p.name} · evolução</h3>
                        <Evolution
                          tag={p.playerTag}
                          wars={official.slice(0, 4)}
                        />
                        {p.donor && (
                          <MetricView
                            name="Doações observadas"
                            metric={p.donor}
                          />
                        )}
                        <p>
                          {p.situation}. Reincidência não utiliza capturas
                          parciais ou importações. Ausência de evento não
                          comprova permanência anterior.
                        </p>
                        <details>
                          <summary>Registro da guerra selecionada</summary>
                          <pre>
                            {JSON.stringify(
                              {
                                fonte: partial
                                  ? "CAPTURA_PARCIAL"
                                  : selected.data?.war.source || "DIRECT_API",
                                pontos: p.contribution,
                                decks: p.decksUsed,
                              },
                              null,
                              2,
                            )}
                          </pre>
                        </details>
                      </td>
                    </tr>
                  )}
                </Fragment>
              ))}
            </tbody>
          </table>
        </div>
        <details>
          <summary>Histórico visual do clã · {count} guerras</summary>
          {wars.slice(0, count).map((w) => {
            const t = totals(w.participants);
            return (
              <div key={w.war.id} className="panel">
                <Link to={"/guerras/" + w.war.id + "?" + url.toString()}>
                  {warLabel(w.war, zone)}
                </Link>
                <meter
                  min={0}
                  max={Math.max(80000, t.sum || 0)}
                  value={t.sum || 0}
                />
                <span>
                  {format(t.sum)} pontos observados · {t.sample}/{t.total}{" "}
                  registros · {w.war.source || "DIRECT_API"}
                </span>
              </div>
            );
          })}
        </details>
      </QueryState>
    </>
  );
}
function Evolution({ tag, wars }: { tag: string; wars: WarDetail[] }) {
  const data = [...wars].reverse().map((w) => ({
    war: w.war,
    p: w.participants.find((p) => p.playerTag === tag),
  }));
  const max = Math.max(3200, ...data.map((d) => d.p?.contribution || 0));
  return (
    <>
      <svg
        viewBox="0 0 520 200"
        role="img"
        aria-label="Pontos nas últimas quatro guerras; linha de meta em 1.600 pontos"
        style={{ maxWidth: 650, width: "100%" }}
      >
        <line
          x1="0"
          x2="520"
          y1={170 - (1600 / max) * 140}
          y2={170 - (1600 / max) * 140}
          stroke="#dec08a"
          strokeDasharray="5 4"
        />
        <text x="2" y={165 - (1600 / max) * 140} fill="#dec08a">
          Meta 1.600
        </text>
        {data.map((d, i) =>
          d.p?.contribution != null ? (
            <g key={d.war.id}>
              <rect
                x={55 + i * 120}
                y={170 - (d.p.contribution / max) * 140}
                width="45"
                height={Math.max(2, (d.p.contribution / max) * 140)}
                fill={d.p.contribution >= 1600 ? "#8cebd5" : "#dec08a"}
              />
              <text x={55 + i * 120} y="190" fill="#dfe9e9">
                {i + 1}
              </text>
            </g>
          ) : null,
        )}
      </svg>
      <svg
        viewBox="0 0 520 100"
        role="img"
        aria-label="Decks usados nas últimas quatro guerras"
        style={{ maxWidth: 650, width: "100%" }}
      >
        <text x="0" y="12" fill="#dfe9e9">
          Decks usados · referência 16, sem disponibilidade presumida
        </text>
        {data.map((d, i) =>
          d.p?.decksUsed != null ? (
            <g key={d.war.id}>
              <rect
                x={55 + i * 120}
                y={85 - (Math.min(d.p.decksUsed, 32) / 32) * 60}
                width="45"
                height={Math.max(2, (Math.min(d.p.decksUsed, 32) / 32) * 60)}
                fill="#8cebd5"
              />
              <text x={55 + i * 120} y="98" fill="#dfe9e9">
                {d.p.decksUsed}
              </text>
            </g>
          ) : null,
        )}
      </svg>
      <p>
        {data.length >= 2 &&
        data.at(-1)?.p?.contribution != null &&
        data.at(-2)?.p?.contribution != null
          ? data.at(-1)!.p!.contribution! > data.at(-2)!.p!.contribution!
            ? "Pontuação em alta frente à guerra anterior."
            : data.at(-1)!.p!.contribution! < data.at(-2)!.p!.contribution!
              ? "Queda de pontuação frente à guerra anterior."
              : "Pontuação estável."
          : "Tendência: dados insuficientes."}
      </p>
      <div className="table-scroll analytics-table">
        <table>
          <thead>
            <tr>
              <th>Guerra/publicação</th>
              <th>Pontos</th>
              <th>Decks</th>
              <th>Leitura</th>
            </tr>
          </thead>
          <tbody>
            {data.map((d, i) => (
              <tr key={d.war.id}>
                <td>
                  {i + 1}. {date(d.war.publishedAt)} ·{" "}
                  {d.war.source || "DIRECT_API"}
                </td>
                <td>{format(d.p?.contribution)}</td>
                <td>{format(d.p?.decksUsed)}</td>
                <td>
                  {d.p?.decksUsed === 0
                    ? "Zero ataques explícito"
                    : d.p?.contribution == null
                      ? "Dados insuficientes"
                      : d.p.contribution >= 1600
                        ? "Meta atingida"
                        : "Abaixo da meta"}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
