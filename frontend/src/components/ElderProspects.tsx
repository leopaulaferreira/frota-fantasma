import { useState } from "react";
import {
  type Player,
  type Report,
  type Metric,
  date,
  format,
} from "../lib/api";
import { Badge, Empty, MetricView, Person, Table } from "./ui";

export function recentAverage(player: Player): number | null {
  const value = player.metrics.recentAverage?.value;
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

export function orderedProspects(players: Player[]) {
  return players
    .filter((p) => p.currentMember && p.role === "member")
    .sort((a, b) => {
      const av = recentAverage(a),
        bv = recentAverage(b);
      if (av === null && bv !== null) return 1;
      if (bv === null && av !== null) return -1;
      return (bv ?? 0) - (av ?? 0) || a.tag.localeCompare(b.tag);
    });
}

function objectiveDistance(source: Metric, objective: number): Metric {
  const average = source.value;
  return {
    ...source,
    value: typeof average === "number" ? average - objective : null,
    origin: "DERIVED",
    reason: `Média recente menos o objetivo de referência (${objective} pontos). Valor positivo indica acima; negativo indica abaixo. Esta comparação não determina elegibilidade para promoção. ${source.reason}`,
    dataUsed: [...source.dataUsed, `referenceObjective=${objective}`],
  };
}

export function ElderProspects({ report }: { report: Report }) {
  const [objectiveInput, setObjectiveInput] = useState("2400");
  const objective = Number(objectiveInput);
  const validObjective =
    objectiveInput.trim() !== "" && Number.isFinite(objective) && objective > 0;
  const players = orderedProspects(report.players);
  return (
    <section
      aria-labelledby="elder-prospects-title"
      className="elder-prospects"
    >
      <h2 id="elder-prospects-title">Possíveis candidatos a Ancião</h2>
      <p className="muted">
        Todos os membros sem cargo, da maior para a menor média nas últimas
        quatro guerras disponíveis do clã. Quem fica abaixo do objetivo continua
        na lista. Empates na média compartilham posição; médias desconhecidas
        ficam ao final.
      </p>
      <div className="toolbar">
        <label htmlFor="elder-objective">Objetivo de referência (pontos)</label>
        <input
          id="elder-objective"
          type="number"
          min="1"
          step="any"
          value={objectiveInput}
          aria-invalid={!validObjective}
          onChange={(e) => setObjectiveInput(e.target.value)}
        />
      </div>
      {!validObjective && (
        <p role="alert">
          Informe um objetivo maior que zero para comparar as médias.
        </p>
      )}
      <p className="muted">
        Referência ajustável apenas nesta consulta, sem alterar as regras
        salvas. Doações: semana de {date(report.week)}. Clique nos indicadores
        para consultar período, amostra e evidências.
      </p>
      <Table
        headers={[
          "Ordem",
          "Membro / contexto",
          "Média recente",
          "Distância do objetivo",
          "Participação e decks",
          "Doações e consistência",
        ]}
      >
        {players.map((p) => {
          const average = recentAverage(p);
          const metric = p.metrics.recentAverage;
          const rank =
            average === null
              ? null
              : players.findIndex((other) => recentAverage(other) === average) +
                1;
          const diff =
            average === null || !validObjective ? null : average - objective;
          return (
            <tr key={p.tag}>
              <td className="rank">{rank === null ? "—" : `${rank}º`}</td>
              <td className="prospect-context">
                <Person player={p} />
                <Badge value={p.operationalStatus.value} />
                <p>{p.operationalStatus.reason}</p>
                <details>
                  <summary>Avaliação pelas regras atuais</summary>
                  <p>{p.elderCandidate.reason}</p>
                  <MetricView
                    compact
                    name="Candidatura pelas regras"
                    metric={p.elderCandidate}
                  />
                </details>
              </td>
              <td>
                <MetricView
                  compact
                  name="Média das últimas 4 guerras"
                  metric={metric}
                />
                <small>
                  {metric?.warsConsidered ?? 0} pontuações conhecidas / até 4
                  guerras
                </small>
                {metric &&
                  (metric.warsConsidered < 4 ||
                    metric.insufficientSample ||
                    metric.incompletePeriod) && (
                    <p className="muted">
                      Amostra limitada: comparar com cautela.
                    </p>
                  )}
              </td>
              <td>
                <strong>
                  {diff === null
                    ? "Dados insuficientes"
                    : diff === 0
                      ? "No objetivo"
                      : `${format(Math.abs(diff))} pontos ${diff > 0 ? "acima" : "abaixo"}`}
                </strong>
                {metric && validObjective && (
                  <MetricView
                    compact
                    name="Diferença para o objetivo"
                    metric={objectiveDistance(metric, objective)}
                  />
                )}
              </td>
              <td>
                <MetricView
                  compact
                  name="Participação"
                  metric={p.metrics.recentParticipation}
                />
                <MetricView
                  compact
                  name="Decks usados"
                  metric={p.metrics.recentDecks}
                />
                <MetricView
                  compact
                  name="Guerras com 16 decks"
                  metric={p.metrics.completeStandardWars}
                />
              </td>
              <td>
                <MetricView
                  compact
                  name="Doações observadas na semana"
                  metric={p.metrics.weeklyDonations}
                />
                <MetricView
                  compact
                  name="Dias observados no clã"
                  metric={p.metrics.observedTenureDays}
                />
                <MetricView
                  compact
                  name="Alertas registrados"
                  metric={p.metrics.recentAlerts}
                />
              </td>
            </tr>
          );
        })}
      </Table>
      {!players.length && (
        <Empty>Nenhum membro sem cargo no elenco atual.</Empty>
      )}
      <p className="muted">
        A ordem compara pontuação, não mérito completo para promoção. Considere
        a amostra, os motivos e o histórico do membro. Nenhuma promoção é
        automática.
      </p>
    </section>
  );
}
