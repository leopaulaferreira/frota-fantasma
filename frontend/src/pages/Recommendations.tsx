import { ElderProspects } from "../components/ElderProspects";
import { useAdmin } from "../lib/auth";
import { AwardCard } from "../components/royale";
import { useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  api,
  useApi,
  date,
  type Report,
  type Evaluation,
  type Award,
} from "../lib/api";
import { WarHistory, Donations } from "./Players";
import { title } from "../lib/labels";
import {
  Heading,
  QueryState,
  Metrics,
  MetricView,
  Badge,
  Person,
  Paged,
} from "../components/ui";
export function Recommendations() {
  const { id } = useParams();
  return id ? <EvaluationDetail id={id} /> : <RecommendationList />;
}
function RecommendationList() {
  const admin = useAdmin();
  const q = useApi<Report>("/analytics");
  const client = useQueryClient();
  const [week, setWeek] = useState("");
  const requestKey = useRef({ week: "", key: crypto.randomUUID() });
  const save = useMutation({
    mutationFn: () =>
      api<Evaluation>("/recommendations/evaluations", {
        week: week || null,
        idempotencyKey: requestKey.current.key,
      }),
    onSuccess: () => {
      requestKey.current.key = crypto.randomUUID();
      return client.invalidateQueries();
    },
  });
  return (
    <>
      <Heading eyebrow="APOIO À LIDERANÇA" title="Recomendações com contexto" />
      <div className="callout">
        Classificações analíticas para revisão humana. Nenhuma expulsão ou
        promoção é executada pelo sistema.
      </div>
      <QueryState query={q}>
        {q.data && (
          <>
            <h2>Acompanhamento operacional</h2>
            <ul
              className="recommendation-list"
              aria-label="Acompanhamento operacional"
            >
              {q.data.players
                .filter(
                  (p) =>
                    p.currentMember &&
                    ["OBSERVAR", "REVISAR"].includes(
                      String(p.operationalStatus.value),
                    ),
                )
                .map((p) => (
                  <li className="recommendation-row" key={p.tag}>
                    <Person player={p} />
                    <Badge value={p.operationalStatus.value} />
                    <p className="recommendation-reason">
                      {p.operationalStatus.reason}
                    </p>
                    <MetricView
                      name="Evidências do status"
                      compact
                      metric={p.operationalStatus}
                    />
                  </li>
                ))}
            </ul>
            <h2>Candidaturas a Ancião</h2>
            <p className="muted">
              Elegíveis e não elegíveis, com os motivos da avaliação.
            </p>
            {q.data.players
              .filter((p) => p.currentMember && p.role === "member")
              .map((p) => (
                <details className="panel" key={p.tag}>
                  <summary>
                    {p.name} ·{" "}
                    {p.elderCandidate.value === true
                      ? "Candidato"
                      : "Não elegível / dados insuficientes"}
                  </summary>
                  <p>{p.elderCandidate.reason}</p>
                  <MetricView name="Candidatura" metric={p.elderCandidate} />
                  <Link to={"/membros/" + encodeURIComponent(p.tag)}>
                    Investigar histórico completo →
                  </Link>
                </details>
              ))}
          </>
        )}
      </QueryState>
      <h2>Registrar avaliação</h2>
      {!admin && (
        <p className="muted">
          Registro reservado ao líder; sua conta permite consulta das
          avaliações.
        </p>
      )}
      <p className="muted">
        Guarda uma cópia dos resultados e regras para consultar mudanças de
        status. Selecione a segunda-feira da semana; vazio usa a semana atual.
      </p>
      <div className="toolbar">
        <input
          type="date"
          aria-label="Semana da avaliação"
          value={week}
          onChange={(e) => {
            setWeek(e.target.value);
            requestKey.current = {
              week: e.target.value,
              key: crypto.randomUUID(),
            };
          }}
        />
        <button
          className="primary"
          disabled={save.isPending || !admin}
          onClick={() => save.mutate()}
        >
          {save.isPending ? "Registrando…" : "Registrar avaliação"}
        </button>
      </div>
      {save.error && (
        <p role="alert" className="error">
          {save.error.message}
        </p>
      )}
      {save.data && (
        <p role="status">
          Avaliação registrada.{" "}
          <Link to={"/recomendacoes/" + save.data.id}>
            Consultar evidências →
          </Link>
        </p>
      )}
      <h2>Avaliações anteriores</h2>
      <Paged<Evaluation>
        path="/recommendations/evaluations"
        render={(items) => (
          <div className="timeline">
            {items.map((e) => (
              <article key={e.id}>
                <small>
                  Registrada em {date(e.createdAt)} · {e.formulaVersion}
                </small>
                <h3>
                  <Link to={"/recomendacoes/" + e.id}>
                    Semana de {date(e.week)}
                  </Link>
                </h3>
              </article>
            ))}
          </div>
        )}
      />
    </>
  );
}
function EvaluationDetail({ id }: { id: string }) {
  const admin = useAdmin();
  const q = useApi<Evaluation>("/recommendations/evaluations/" + id);
  const client = useQueryClient();
  const final = useMutation({
    mutationFn: () =>
      api("/recommendations/evaluations/" + id + "/finalize-awards", {}),
    onSuccess: () => client.invalidateQueries(),
  });
  return (
    <>
      <Link to="/recomendacoes">← Avaliações</Link>
      <Heading title="Evidências preservadas" />
      <QueryState query={q}>
        {q.data && (
          <>
            <p className="lede">
              Semana de {date(q.data.week)} · registrada em{" "}
              {date(q.data.createdAt)} · corte em {date(q.data.analysisCutoff)}{" "}
              · fórmula {q.data.formulaVersion}
            </p>
            <Metrics metrics={q.data.report.clan} />
            <h2>Destaques desta avaliação</h2>
            <div className="awards">
              {q.data.report.highlights.map((h) => (
                <article className="panel" key={h.title}>
                  <h3>{title(h.title)}</h3>
                  <p>{h.playerTag || "Sem elegível"}</p>
                  <MetricView name="Critérios" metric={h.result} />
                </article>
              ))}
            </div>
            <div className="callout">
              <b>Formalizar títulos locais</b>
              <p>
                Preserva os vencedores acima de forma definitiva para esta
                semana. Só é permitido para uma semana encerrada e elegível; não
                altera membros na Supercell.
              </p>
              <button
                disabled={final.isPending || final.isSuccess || !admin}
                onClick={() => final.mutate()}
              >
                {final.isSuccess
                  ? "Títulos formalizados"
                  : final.isPending
                    ? "Formalizando…"
                    : "Formalizar estes títulos"}
              </button>
              {final.error && (
                <p className="error" role="alert">
                  {final.error.message}
                </p>
              )}
            </div>
            <h2>Membros na avaliação</h2>
            {q.data.report.players.map((p) => (
              <details className="panel" key={p.tag}>
                <summary>
                  {p.name} · {String(p.operationalStatus.value || "Sem status")}
                </summary>
                <p>{p.operationalStatus.reason}</p>
                <MetricView name="Status" metric={p.operationalStatus} />
                <MetricView name="Candidatura" metric={p.elderCandidate} />
                <MetricView
                  name="Contexto histórico"
                  metric={p.historicalContext}
                />
                <MetricView name="Participação" metric={p.participation} />
                <Metrics metrics={p.metrics} />
                <WarHistory player={p} />
                <Donations player={p} />
              </details>
            ))}
            <details className="panel">
              <summary>Regras preservadas</summary>
              <pre>
                {JSON.stringify(q.data.report.recommendationRules, null, 2)}
              </pre>
            </details>
          </>
        )}
      </QueryState>
    </>
  );
}
export function Awards() {
  const q = useApi<Report>("/analytics", 60000);
  const automation = useApi<{
    enabled: boolean;
    schedule: string;
    timezone: string;
    closingWeek: string;
    pending: boolean;
  }>("/recommendations/automation", 60000);
  return (
    <>
      <Heading eyebrow="RECONHECIMENTO" title="Mural de premiações" />
      <p className="lede">
        Um título por jogador em cada semana. Projeções e títulos formalizados
        ficam separados.
      </p>
      {automation.data && (
        <div className="callout">
          <b>
            {automation.data.enabled
              ? `Fechamento automático: ${automation.data.schedule} · ${automation.data.timezone}`
              : "Fechamento automático desativado"}
          </b>
          <p>
            {automation.data.pending
              ? "Aguardando resultado oficial ou dados suficientes para publicar o fechamento de "
              : "Premiações publicadas para o fechamento de "}
            {date(automation.data.closingWeek)}.
          </p>
          <p>
            Com o sistema ligado, novas tentativas ocorrem a cada 15 minutos.
            Categorias sem amostra suficiente ficam sem vencedor; as limitações
            permanecem nas evidências. Doações usam a semana anterior à
            publicação da guerra.
          </p>
        </div>
      )}
      <h2>Títulos formalizados</h2>
      <Paged<Award>
        path="/recommendations/awards"
        refetchInterval={60000}
        render={(items) => (
          <div className="timeline">
            {items.map((a) => (
              <article key={a.week + a.title}>
                <small>
                  Semana de {date(a.week)} · formalizado em{" "}
                  {date(a.finalizedAt)}
                </small>
                <h3>{title(a.title)}</h3>
                <Link to={"/membros/" + encodeURIComponent(a.playerTag)}>
                  {q.data?.players.find((p) => p.tag === a.playerTag)?.name ||
                    a.playerTag}
                </Link>
                <p>{a.evidence.result.reason}</p>
                <MetricView
                  compact
                  name="Evidências"
                  metric={a.evidence.result}
                />
                <Link to={"/recomendacoes/" + a.evaluationId}>
                  Avaliação original →
                </Link>
              </article>
            ))}
          </div>
        )}
      />
      <h2>Projeção da semana</h2>
      <QueryState query={q}>
        <div className="awards">
          {q.data?.highlights.map((h) => (
            <AwardCard
              key={h.title}
              highlight={h}
              players={q.data.players}
              week={q.data.week}
            />
          ))}
        </div>
      </QueryState>
      {q.data && <ElderProspects report={q.data} />}
    </>
  );
}
