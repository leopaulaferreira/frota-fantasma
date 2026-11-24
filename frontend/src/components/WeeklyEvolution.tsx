import { date, type Report } from "../lib/api";
import { evolutionSeries } from "../lib/evolution";

const charts = [
  ["averagePoints", "Média de pontos", ""],
  ["participationPercent", "Participação", "%"],
  ["observedDonations", "Doações observadas", ""],
] as const;

export function WeeklyEvolution({ report }: { report: Report }) {
  return (
    <section className="weekly-evolution" aria-labelledby="evolution-title">
      <div className="section-head">
        <h2 id="evolution-title">Evolução entre semanas</h2>
        <span className="muted">Últimas 8 semanas</span>
      </div>
      <p className="muted">Guerras por semana de publicação. Participantes podem variar; doações são limites inferiores observados, incluindo ex-membros.</p>
      <div className="evolution-grid">
        {charts.map(([key, title, unit]) => {
          const series = evolutionSeries(report, key);
          const maximum = key === "participationPercent" ? 100 : Math.max(1, ...series.map(s => s.value ?? 0));
          return <article className="evolution-card" key={key}>
            <h3>{title}</h3>
            <ol className="evolution-bars" aria-label={`${title} por semana`}>
              {series.map(({ week, value, partial }) => {
                const formatted = value === null ? "Sem dados" : value.toLocaleString("pt-BR", { maximumFractionDigits: 1 }) + unit;
                return <li key={week} aria-label={`Semana de ${date(week)}: ${formatted}${partial ? ", dados parciais" : ""}`}>
                  <span className="evolution-value" aria-hidden="true">{value === null ? "—" : formatted}{partial && "*"}</span>
                  <div className="evolution-track" aria-hidden="true">
                    {value !== null ? <span className={partial ? "partial" : ""} style={{ height: `${Math.min(100, Math.max(0, value / maximum * 100))}%` }} /> : <span className="evolution-gap">—</span>}
                  </div>
                  <span className="evolution-date" aria-hidden="true">{date(week).slice(0, 5)}</span>
                </li>;
              })}
            </ol>
            <p className="evolution-legend">— Sem dados · * Período incompleto ou amostra insuficiente</p>
          </article>;
        })}
      </div>
    </section>
  );
}
