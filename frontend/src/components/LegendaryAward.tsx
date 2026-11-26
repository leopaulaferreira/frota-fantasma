import { useState } from "react";
import { Link } from "react-router-dom";
import { date, format, useApi } from "../lib/api";
import { Badge, Empty, QueryState } from "./ui";
import { art } from "./royale";

export type LegendaryCandidate = {
  position: number | null; tag: string; name: string;
  previousTrophies: number | null; trophies: number | null; netChange: number | null;
  previousRecord: number | null; record: number | null; improvement: number | null;
  warMedals: number | null; decksUsed: number | null; eligible: boolean;
  reasons: string[]; capturedAt: string | null; apiBestTrophies: number | null;
};
export type LegendaryOverview = {
  enabled: boolean; official: boolean; recordSource: "OBSERVED" | "OFFICIAL_API";
  recordEra: string; nextCollectionAt: string; weeks: string[]; page: number; totalWeeks: number;
  result: null | {
    cycle: { week: string; closesAt: string; deadline: string; finalizedAt: string | null;
      status: string; recordSource: "OBSERVED" | "OFFICIAL_API"; recordEra: string;
      official: boolean; winnerTag: string | null };
    candidates: LegendaryCandidate[]; notice: string;
  };
};
const value = (n: number | null) => n === null ? "—" : format(n);
const delta = (n: number | null) => n === null ? "—" : (n > 0 ? "+" : "") + format(n);
const saoPaulo = (s: string) => new Intl.DateTimeFormat("pt-BR", {
  dateStyle: "short", timeStyle: "short", timeZone: "America/Sao_Paulo",
}).format(new Date(s));
const statuses: Record<string, string> = {
  COLLECTING: "Coletando perfis", AWAITING_WAR: "Aguardando guerra oficial",
  COMPLETE: "Ciclo concluído", INCOMPLETE: "Dados incompletos", MISSED: "Coleta não realizada",
};

export function LegendaryAward() {
  const [week, setWeek] = useState("");
  const [page, setPage] = useState(0);
  const q = useApi<LegendaryOverview>(`/legendary-awards?page=${page}${week ? "&week=" + week : ""}`, 60000);
  const data = q.data;
  const result = data?.result;
  const cycle = result?.cycle;
  const winner = result?.candidates.find(c => c.tag === cycle?.winnerTag);
  const source = cycle?.recordSource ?? data?.recordSource;
  return (
    <section className="legendary-section" aria-labelledby="legendary-title">
      <h2 id="legendary-title">👻 Fantasma Lendário</h2>
      <p className="lede">Fantasma Lendário: Maior superação do recorde pessoal de troféus da Ladder + meta de guerra.</p>
      <QueryState query={q}>
        {data && <>
          <div className="toolbar">
            <Badge value={(cycle?.official ?? data.official) ? "Oficial" : "Experimental"} />
            {cycle && <Badge value={statuses[cycle.status] || cycle.status} />}
            {data.weeks.length > 0 && <>
              <label htmlFor="legendary-week">Encerramento do ciclo</label>
              <select id="legendary-week" value={week || cycle?.week || ""} onChange={e => setWeek(e.target.value)}>
                {data.weeks.map(w => <option key={w} value={w}>{date(w)}</option>)}
              </select>
            </>}
            {(page > 0 || data.totalWeeks > 52) && <>
              <button disabled={page === 0} onClick={() => { setPage(page - 1); setWeek(""); }}>Semanas recentes</button>
              <button disabled={(page + 1) * 52 >= data.totalWeeks} onClick={() => { setPage(page + 1); setWeek(""); }}>Semanas anteriores</button>
            </>}
          </div>
          <div className="legendary-summary">
            <article className="royale-award award-03 legendary-card">
              <div className="award-stage" aria-hidden="true">
                <span className="award-number">04</span>
                <img className="award-character" src={art("royal-ghost")} alt="" />
                <img className="award-badge" src={art("crown-emblem")} alt="" />
              </div>
              <div className="award-content">
                <small className="eyebrow">{cycle?.official ? "RESULTADO OFICIAL" : "RESULTADO EXPERIMENTAL"}</small>
                <h3>👻 Fantasma Lendário</h3>
                {winner ? <>
                  <Link className="award-winner" to={"/membros/" + encodeURIComponent(winner.tag)}>{winner.name}</Link>
                  <dl className="legendary-metrics">
                    <div><dt>Recorde anterior da Ladder</dt><dd>{value(winner.previousRecord)}</dd></div>
                    <div><dt>Novo recorde da Ladder</dt><dd>{value(winner.record)}</dd></div>
                    <div><dt>Superação do recorde</dt><dd>{delta(winner.improvement)}</dd></div>
                    <div><dt>Troféus atuais da Ladder</dt><dd>{value(winner.trophies)}</dd></div>
                    <div><dt>Medalhas de guerra</dt><dd>{value(winner.warMedals)}</dd></div>
                    <div><dt>Decks utilizados</dt><dd>{value(winner.decksUsed)} / 16</dd></div>
                  </dl>
                  <Badge value="Elegível" />
                </> : <p className="muted">{cycle?.finalizedAt ? "Sem vencedor neste ciclo" : "Aguardando dados para comparação"}</p>}
              </div>
            </article>
            <div className="callout legendary-explanation">
              <h3>{source === "OFFICIAL_API" ? "Recorde informado pela API oficial" : "Recorde observado pelo sistema"}</h3>
              <p>{source === "OFFICIAL_API"
                ? "A comparação usa o recorde da Ladder informado nos perfis oficiais, na versão validada para este ciclo."
                : "Usamos o maior número de troféus da Ladder observado pelo Frota Analytics nesta versão do acompanhamento. Esse valor não representa necessariamente o recorde histórico oficial da conta."}</p>
              <p>A superação compara recordes. Perder troféus e recuperar o valor anterior não gera pontos. A evolução líquida aparece somente para acompanhamento.</p>
              <p>Critérios: pelo menos 1.600 medalhas, 16 decks, ciclo completo no clã, base anterior válida, superação positiva e não ser o líder. Desempate por medalhas de guerra e depois por tag.</p>
              <p>O mesmo membro pode vencer em semanas consecutivas. Esta categoria é independente dos três títulos tradicionais.</p>
              <p>{result?.notice || "A primeira coleta estabelece a base de comparação. Ainda não há vencedor."}</p>
              {cycle && <p>Encerramento: {saoPaulo(cycle.closesAt)}. Coletas aceitas até {saoPaulo(cycle.deadline)} (São Paulo).</p>}
              <p>{data.enabled ? `Coleta semanal: segunda-feira, 06h, horário de São Paulo. Próxima janela: ${saoPaulo(data.nextCollectionAt)}.` : "Coleta automática desativada."}</p>
              {!result && !data.official && <p>Em fase experimental, os resultados não entram nas mensagens oficiais de premiação.</p>}
            </div>
          </div>
          <h3>Candidatos do ciclo</h3>
          {!result?.candidates.length ? <Empty>Sem perfis coletados neste ciclo. Nenhum valor ausente é tratado como zero.</Empty> : <>
            <p className="muted">Troféus e recordes são exclusivos do Caminho de Troféus. “—” indica dado indisponível. Posição considera apenas membros elegíveis.</p>
            <div className="table-scroll legendary-table" role="region" aria-label="Acompanhamento do Fantasma Lendário" tabIndex={0}>
              <table>
                <caption className="legendary-caption">Candidatos do Fantasma Lendário · encerramento em {date(cycle!.week)}</caption>
                <thead><tr>
                  {["Posição", "Jogador", "Troféus anteriores", "Troféus atuais", "Evolução líquida", "Recorde anterior", "Recorde atual", "Superação", "Medalhas de guerra", "Decks", "Elegibilidade", "Motivo de inelegibilidade"].map(h => <th key={h} scope="col">{h}</th>)}
                </tr></thead>
                <tbody>{result.candidates.map(c => <tr key={c.tag}>
                  <td>{value(c.position)}</td>
                  <th scope="row"><Link to={"/membros/" + encodeURIComponent(c.tag)}>{c.name}</Link><small className="muted">{c.tag}</small></th>
                  <td>{value(c.previousTrophies)}</td><td>{value(c.trophies)}</td><td>{delta(c.netChange)}</td>
                  <td>{value(c.previousRecord)}</td><td>{value(c.record)}</td><td><strong>{delta(c.improvement)}</strong></td>
                  <td>{value(c.warMedals)}</td><td>{value(c.decksUsed)}</td><td><Badge value={c.eligible ? "Elegível" : "Não elegível"} /></td>
                  <td>{c.reasons.join("; ") || "Todos os critérios atendidos"}</td>
                </tr>)}</tbody>
              </table>
            </div>
          </>}
        </>}
      </QueryState>
    </section>
  );
}
