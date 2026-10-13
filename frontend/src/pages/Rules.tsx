import { useAdmin } from "../lib/auth";
import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ApiError, api, useApi, date, type RuleVersion } from "../lib/api";
import { Heading, QueryState, Paged } from "../components/ui";
const names: Record<string, string> = {
  pointsTarget: "Meta de pontuação",
  offlineDays: "Dias offline para revisão",
  minimumWars: "Amostra mínima de guerras",
  immortalMinimumWars: "Amostra mínima • Alma Imortal",
  reviewLowWars: "Guerras abaixo da meta para revisão",
  adequateStandardDeckPercent: "Meta de utilização padrão (%)",
  promotionTenureDays: "Tempo observado para Ancião (dias)",
  promotionDonations: "Doações observadas para Ancião",
  freshness: "Validade da captura (duração ISO, ex.: PT2H)",
  timezone: "Fuso horário",
  recentAlertDays: "Janela de alertas (dias)",
  warWeight: "Peso • guerras (%)",
  decksWeight: "Peso • decks (%)",
  donationsWeight: "Peso • doações (%)",
  warTarget: "Meta do score • pontos",
  donationTarget: "Meta do score • doações",
};
export function Rules() {
  const q = useApi<RuleVersion>("/recommendations/rules");
  return (
    <>
      <Heading eyebrow="CRITÉRIOS TRANSPARENTES" title="Regras da Frota" />
      <QueryState query={q}>
        {q.data && <RuleForm key={q.data.id || "initial"} rule={q.data} />}
      </QueryState>
      <h2>Histórico de versões</h2>
      <Paged<RuleVersion>
        path="/recommendations/rules/history"
        render={(items) => (
          <div>
            {items.map((r) => (
              <details className="panel" key={r.id}>
                <summary>
                  Revisão {r.revision} · {date(r.createdAt)} · {r.reason}
                </summary>
                <pre>{JSON.stringify(r.settings, null, 2)}</pre>
              </details>
            ))}
          </div>
        )}
      />
    </>
  );
}
function RuleForm({ rule }: { rule: RuleVersion }) {
  const admin = useAdmin();
  const [settings, setSettings] = useState(() =>
    structuredClone(rule.settings),
  );
  const [reason, setReason] = useState("");
  const client = useQueryClient();
  const save = useMutation({
    mutationFn: () =>
      api("/recommendations/rules", {
        expectedVersion: rule.id,
        settings,
        reason,
      }),
    onSuccess: () => client.invalidateQueries(),
  });
  const total =
    settings.score.warWeight +
    settings.score.decksWeight +
    settings.score.donationsWeight;
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        save.mutate();
      }}
    >
      <p className="lede">
        Revisão {rule.revision} · alterações criam uma nova versão. Avaliações
        anteriores preservam os critérios originais.
      </p>
      {!admin && (
        <p className="callout">
          Sua conta tem acesso de consulta. Alterações de regras são reservadas
          ao líder.
        </p>
      )}
      <fieldset disabled={!admin}>
        <h2>Participação e contexto</h2>
        <div className="form-grid">
          {Object.entries(settings.analytics).map(([k, v]) => (
            <label key={k}>
              {names[k] || k}
              <input
                required
                disabled={k === "timezone"}
                type={typeof v === "number" ? "number" : "text"}
                min={
                  [
                    "minimumWars",
                    "immortalMinimumWars",
                    "reviewLowWars",
                  ].includes(k)
                    ? 2
                    : k === "promotionDonations"
                      ? 0
                      : typeof v === "number"
                        ? 1
                        : undefined
                }
                max={
                  [
                    "minimumWars",
                    "immortalMinimumWars",
                    "reviewLowWars",
                  ].includes(k)
                    ? 4
                    : k === "adequateStandardDeckPercent"
                      ? 100
                      : undefined
                }
                value={v}
                onChange={(e) =>
                  setSettings((s) => ({
                    ...s,
                    analytics: {
                      ...s.analytics,
                      [k]:
                        typeof v === "number"
                          ? Number(e.target.value)
                          : e.target.value,
                    },
                  }))
                }
              />
            </label>
          ))}
        </div>
        <h2>Frota Score</h2>
        <label className="check">
          <input
            type="checkbox"
            checked={settings.score.enabled}
            onChange={(e) =>
              setSettings((s) => ({
                ...s,
                score: { ...s.score, enabled: e.target.checked },
              }))
            }
          />{" "}
          Habilitar Frota Score
        </label>
        <p className="muted">
          Componentes sem evidência não recebem zero nem têm seus pesos
          redistribuídos.
        </p>
        <div className="form-grid">
          {Object.entries(settings.score)
            .filter(([k]) => k !== "enabled")
            .map(([k, v]) => (
              <label key={k}>
                {names[k]}
                <input
                  type="number"
                  required
                  min={k.endsWith("Weight") ? 0 : 1}
                  max={k.endsWith("Weight") ? 100 : undefined}
                  value={Number(v)}
                  onChange={(e) =>
                    setSettings((s) => ({
                      ...s,
                      score: { ...s.score, [k]: Number(e.target.value) },
                    }))
                  }
                />
              </label>
            ))}
        </div>
        <p className={total === 100 ? "muted" : "error"}>
          Soma dos pesos: {total}% (necessário 100%).
        </p>
        <div className="form-grid">
          <label>
            Janela de alertas recentes (dias)
            <input
              type="number"
              min={1}
              max={365}
              required
              value={settings.recentAlertDays}
              onChange={(e) =>
                setSettings((s) => ({
                  ...s,
                  recentAlertDays: Number(e.target.value),
                }))
              }
            />
          </label>
          <label className="check">
            <input
              type="checkbox"
              checked={settings.immortalAllowPartial}
              onChange={(e) =>
                setSettings((s) => ({
                  ...s,
                  immortalAllowPartial: e.target.checked,
                }))
              }
            />{" "}
            Permitir amostra parcial para Alma Imortal conforme critérios do
            backend
          </label>
        </div>
        <label>
          Motivo da alteração
          <textarea
            required
            maxLength={500}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="Explique a mudança para futuras consultas."
          />
        </label>
        {save.error && (
          <p className="error" role="alert">
            {save.error.message}{" "}
            {save.error instanceof ApiError && save.error.status === 409
              ? "Recarregue para consultar a versão atual."
              : ""}
          </p>
        )}
        {save.isSuccess && <p role="status">Nova versão salva.</p>}
        <button
          className="primary"
          disabled={save.isPending || total !== 100 || !reason.trim()}
        >
          {save.isPending ? "Salvando…" : "Salvar nova versão"}
        </button>
      </fieldset>
    </form>
  );
}
