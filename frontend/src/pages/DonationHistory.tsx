import { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useApi, date, dateTime, format, num, type Report } from "../lib/api";
import { donationWeeks, observedWindow } from "../lib/donationHistory";
import {
  Heading,
  QueryState,
  Table,
  Person,
  MetricView,
  Empty,
} from "../components/ui";
import { shiftWeek } from "../lib/weeks";

export function DonationHistory() {
  const query = useApi<Report>("/analytics", 60000);
  const [params, setParams] = useSearchParams();
  const [search, setSearch] = useState(params.get("membro") || "");
  const [former, setFormer] = useState(params.get("ex") === "1");
  const [mode, setMode] = useState("counter");
  const report = query.data;
  const weeks = report ? donationWeeks(report.week) : [];
  const requestedWeek = params.get("week");
  const selectedWeek =
    requestedWeek === "all" || weeks.includes(requestedWeek || "")
      ? requestedWeek!
      : report?.week || "";
  const visibleWeeks =
    selectedWeek === "all" ? weeks : selectedWeek ? [selectedWeek] : [];
  const selectWeek = (week: string) => {
    const next = new URLSearchParams(params);
    next.set("week", week);
    setParams(next);
  };
  const rows = (report?.players || [])
    .filter(
      (p) =>
        (former || p.currentMember) &&
        (p.name + p.tag)
          .toLocaleLowerCase("pt-BR")
          .includes(search.trim().toLocaleLowerCase("pt-BR")),
    )
    .map((p) => ({ p, ...observedWindow(p, visibleWeeks) }))
    .sort((a, b) => {
      const value = (row: typeof a) =>
        mode === "observed"
          ? row.total
          : num(
              row.p.donations.find(
                (d) =>
                  d.week ===
                  (selectedWeek === "all" ? report?.week : selectedWeek),
              )?.lastCounter,
            );
      return (
        (value(b) ?? -1) - (value(a) ?? -1) || a.p.tag.localeCompare(b.p.tag)
      );
    });
  return (
    <>
      <Heading eyebrow="MEMÓRIA DA TRIPULAÇÃO" title="Doações">
        <Link className="button" to="/membros">
          Voltar à tripulação →
        </Link>
      </Heading>
      <p className="lede">
        Os números são salvos nas coletas do clã e continuam disponíveis depois
        que o jogo reinicia o contador.
      </p>
      <QueryState query={query}>
        {report && (
          <>
            <div className="callout">
              <strong>
                Semana atual + 5 anteriores · desde {date(weeks[0])}
              </strong>
              <p>
                Na semana de {date(shiftWeek(report.week, 1))}, a semana de{" "}
                {date(weeks[0])} sai deste painel e uma nova entra. O acumulado
                é recalculado com essa janela. O histórico completo permanece
                nas consultas por data.
              </p>
              <small>
                Calendário:{" "}
                {String(report.recommendationRules.settings.analytics.timezone)}{" "}
                · Consulta de {dateTime(report.generatedAt)}. A coleta
                automática depende do sistema ligado.
              </small>
            </div>
            <div className="toolbar">
              <label>
                Semana
                <select
                  aria-label="Filtrar semana de doações"
                  value={selectedWeek}
                  onChange={(e) => selectWeek(e.target.value)}
                >
                  {[...weeks].reverse().map((week) => (
                    <option key={week} value={week}>
                      {week === report.week
                        ? "Semana atual"
                        : "Semana de " + date(week)}
                      {week === report.week ? " · " + date(week) : ""}
                    </option>
                  ))}
                  <option value="all">Todas as últimas 6 semanas</option>
                </select>
              </label>
              <input
                aria-label="Buscar no histórico de doações"
                placeholder="Nome ou tag…"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
              <select
                aria-label="Valores do histórico de doações"
                value={mode}
                onChange={(e) => setMode(e.target.value)}
              >
                <option value="counter">
                  Último contador salvo por semana
                </option>
                <option value="observed">Doações observadas no período</option>
              </select>
              <label className="check">
                <input
                  type="checkbox"
                  checked={former}
                  onChange={(e) => setFormer(e.target.checked)}
                />
                Incluir ex-membros
              </label>
            </div>
            <p className="muted">
              {mode === "counter"
                ? "Cada coluna mostra o último número informado pelo jogo naquela semana, com horário da captura. O número pode ser anterior ao fechamento ou posterior a um reset; abra a explicação para conferir. Ordem: contador da semana selecionada (semana atual na visão de seis semanas)."
                : "Diferenças entre coletas, considerando resets detectados. São limites inferiores: doações anteriores à primeira coleta e lacunas podem ficar de fora. Ordem: doações observadas no período selecionado."}{" "}
              Sem coleta significa desconhecido, não zero.
            </p>
            <p role="status">
              {rows.length} membros nesta seleção ·{" "}
              {visibleWeeks.length === 1
                ? "semana de " + date(visibleWeeks[0])
                : "6 semanas na consulta"}
            </p>
            <div
              className={
                selectedWeek === "all" ? "donation-window" : "donation-week"
              }
            >
              <Table
                headers={[
                  "Membro",
                  ...visibleWeeks.map(
                    (w) => date(w) + (w === report.week ? " · atual" : ""),
                  ),
                  selectedWeek === "all"
                    ? "Acumulado observado"
                    : "Doações observadas no período",
                ]}
              >
                {rows.map(({ p, total, knownWeeks }) => (
                  <tr key={p.tag}>
                    <td>
                      <Person player={p} />
                      {!p.currentMember && <small>Ex-membro</small>}
                    </td>
                    {visibleWeeks.map((week) => {
                      const period = p.donations.find((d) => d.week === week);
                      const metric =
                        mode === "counter"
                          ? period?.lastCounter
                          : period?.observedDonations;
                      return (
                        <td key={week}>
                          {metric ? (
                            <MetricView
                              compact
                              name={
                                (mode === "counter"
                                  ? "Contador salvo"
                                  : "Doações observadas") +
                                " · " +
                                date(week)
                              }
                              metric={metric}
                            />
                          ) : (
                            <span className="muted">Sem coleta</span>
                          )}
                          {mode === "counter" && period?.counterCapturedAt && (
                            <small>
                              Captura: {dateTime(period.counterCapturedAt)}
                            </small>
                          )}
                        </td>
                      );
                    })}
                    <td>
                      <strong>
                        {total === null ? "Sem dados" : format(total)}
                      </strong>
                      <small>
                        {knownWeeks}/{visibleWeeks.length}{" "}
                        {visibleWeeks.length === 1
                          ? "semana com diferenças calculáveis"
                          : "semanas com diferenças calculáveis"}
                      </small>
                      <small>
                        Parcial · considera doações observadas, não a soma dos
                        contadores
                      </small>
                    </td>
                  </tr>
                ))}
              </Table>
            </div>
            {!rows.length && (
              <Empty>Nenhum membro corresponde aos filtros.</Empty>
            )}
          </>
        )}
      </QueryState>
    </>
  );
}
