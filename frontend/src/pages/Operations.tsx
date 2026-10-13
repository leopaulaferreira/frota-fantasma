import { useApi, dateTime, type Page } from "../lib/api";
import { Heading, QueryState, Paged, Table, Badge } from "../components/ui";
interface Run {
  id: string;
  kind: string;
  status: string;
  startedAt: string;
  completedAt: string | null;
  recordCount: number | null;
  errorCode: string | null;
}
interface Source {
  name: string;
  lastSuccess: string | null;
  ageSeconds: number | null;
  expectedIntervalSeconds: number;
  stale: boolean;
  failuresLast24Hours: number;
  latest: Run | null;
}
export function Operations() {
  const q = useApi<{
    generatedAt: string;
    collectionEnabled: boolean;
    sources: Source[];
  }>("/operations");
  return (
    <>
      <Heading eyebrow="SAÚDE DA COLETA" title="Operações da Frota" />
      <QueryState query={q}>
        {q.data && (
          <>
            <p className="lede">
              Coleta automática{" "}
              {q.data.collectionEnabled ? "habilitada" : "desabilitada"} ·
              consulta em {dateTime(q.data.generatedAt)}.
            </p>
            <div className="metrics">
              {q.data.sources.map((s) => (
                <article key={s.name} className="panel">
                  <div className="eyebrow">{s.name}</div>
                  <h2>
                    {s.lastSuccess
                      ? s.stale
                        ? "Dados desatualizados"
                        : "Coleta recente"
                      : "Aguardando primeira coleta"}
                  </h2>
                  <p>Último sucesso: {dateTime(s.lastSuccess)}</p>
                  <p>{s.failuresLast24Hours} falhas nas últimas 24 horas.</p>
                  <small>
                    Intervalo configurado:{" "}
                    {Math.round(s.expectedIntervalSeconds / 60)} minutos. Aviso
                    após dois intervalos. A corrida pode ter cadência diferente
                    fora dos dias de guerra.
                  </small>
                  {s.latest && (
                    <p>
                      Última tentativa: <Badge value={s.latest.status} />{" "}
                      {s.latest.errorCode}
                    </p>
                  )}
                </article>
              ))}
            </div>
          </>
        )}
      </QueryState>
      <h2>Histórico de execuções</h2>
      <Paged<Run>
        path="/collections"
        render={(items) => (
          <Table
            headers={[
              "Fonte",
              "Início",
              "Conclusão",
              "Resultado",
              "Registros",
              "Código",
            ]}
          >
            {items.map((r) => (
              <tr key={r.id}>
                <td>{r.kind}</td>
                <td>{dateTime(r.startedAt)}</td>
                <td>{dateTime(r.completedAt)}</td>
                <td>
                  <Badge value={r.status} />
                </td>
                <td>{r.recordCount ?? "—"}</td>
                <td>{r.errorCode || "—"}</td>
              </tr>
            ))}
          </Table>
        )}
      />
    </>
  );
}
