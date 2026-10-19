import { useState, useRef, useId, type ReactNode } from "react";
import { Link, useLocation } from "react-router-dom";
import { createPortal } from "react-dom";
import { ChevronLeft, ChevronRight, Info, RefreshCw, X } from "lucide-react";
import {
  dateTime,
  format,
  useApi,
  type Metric,
  type Page,
  type Player,
} from "../lib/api";
import { label } from "../lib/labels";
export function Heading({
  eyebrow,
  title,
  children,
}: {
  eyebrow?: string;
  title: string;
  children?: ReactNode;
}) {
  const path = useLocation().pathname;
  const banner = ["/guerras", "/membros", "/premiacoes", "/anciao"].some(
    (route) => path.startsWith(route),
  );
  const portrait = path.startsWith("/guerras")
    ? "skeleton-king"
    : path.startsWith("/membros")
      ? "fisherman"
      : path.startsWith("/premiacoes")
        ? "royal-ghost"
        : "crown-emblem";
  return (
    <header className={"page-heading" + (banner ? " illustrated-heading" : "")}>
      {banner && (
        <img
          className="heading-art"
          src={"/assets/royale/" + portrait + ".png"}
          alt=""
        />
      )}
      <div>
        <div className="eyebrow">{eyebrow || "CENTRAL DE COMANDO"}</div>
        <h1>{title}</h1>
      </div>
      {children}
    </header>
  );
}
export function Empty({ children }: { children?: ReactNode }) {
  return (
    <div className="empty">
      <Info size={22} />
      <p>{children || "Ainda não há registros neste período."}</p>
    </div>
  );
}
export function QueryState({
  query,
  children,
}: {
  query: { isPending: boolean; error: Error | null; refetch: () => unknown };
  children: ReactNode;
}) {
  if (query.isPending)
    return (
      <div className="empty" role="status">
        Carregando registros…
      </div>
    );
  if (query.error)
    return (
      <div className="error" role="alert">
        <b>Consulta indisponível</b>
        <p>{query.error.message}</p>
        <button onClick={() => query.refetch()}>
          <RefreshCw size={16} /> Tentar novamente
        </button>
      </div>
    );
  return <>{children}</>;
}
export function Badge({ value }: { value: unknown }) {
  const s = String(value ?? "Dados insuficientes");
  return (
    <span
      className={
        "badge " +
        (s === "MANTER"
          ? "good"
          : s === "REVISAR"
            ? "bad"
            : s === "OBSERVAR"
              ? "warn"
              : "")
      }
    >
      {format(value)}
    </span>
  );
}
const originLabels: Record<string, string> = {
  DIRECT_API: "Informado pela API oficial",
  DERIVED: "Calculado com dados da API",
  SNAPSHOT_DERIVED: "Calculado com capturas históricas",
  UNAVAILABLE: "Informação indisponível",
};
const qualityLabels: Record<string, string> = {
  AVAILABLE: "Dados disponíveis",
  INSUFFICIENT_DATA: "Amostra insuficiente",
  INCOMPLETE: "Período incompleto",
  UNAVAILABLE: "Informação indisponível",
};
function readableReason(reason: string) {
  return reason
    .replace(/decksUsed/g, "decks utilizados")
    .replace(/fame/g, "pontuação")
    .replace(/Numerador:/g, "Participações:")
    .replace(/denominador:/g, "base de comparação:");
}
export function MetricView({
  metric,
  name,
  compact = false,
  icon,
}: {
  metric?: Metric;
  name: string;
  compact?: boolean;
  icon?: string;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const id = useId();
  if (!metric) return <span className="muted">Dados insuficientes</span>;
  const missing = metric.value === null || metric.value === undefined;
  const value =
    name === "Última atividade oficial" && !missing
      ? dateTime(String(metric.value))
      : missing && metric.quality === "UNAVAILABLE"
        ? "Indisponível"
        : format(metric.value);
  const quality = metric.insufficientSample
    ? "Amostra insuficiente"
    : metric.quality === "UNAVAILABLE"
      ? "Indisponível"
      : metric.incompletePeriod
        ? "Período incompleto"
        : "Ver explicação";
  return (
    <>
      <div className={"metric " + (compact ? "compact" : "")}>
        <button
          type="button"
          className="metric-trigger"
          aria-haspopup="dialog"
          onClick={() => dialog.current?.showModal()}
        >
          {icon && (
            <img className="metric-icon" src={icon} alt="" loading="lazy" />
          )}
          <span className="metric-label">
            {name}
            <Info size={13} />
          </span>
          <strong>{value}</strong>
          <span className="quality">{quality}</span>
        </button>
      </div>
      {createPortal(
        <dialog
          ref={dialog}
          className="metric-dialog"
          aria-labelledby={id}
          onClick={(e) => {
            if (e.target === e.currentTarget) {
              const r = e.currentTarget.getBoundingClientRect();
              if (
                e.clientX < r.left ||
                e.clientX > r.right ||
                e.clientY < r.top ||
                e.clientY > r.bottom
              )
                e.currentTarget.close();
            }
          }}
        >
          <header className="metric-dialog-header">
            <div>
              <div className="eyebrow">ENTENDA O INDICADOR</div>
              <h2 id={id}>{name}</h2>
            </div>
            <button
              type="button"
              className="dialog-close"
              aria-label="Fechar explicação"
              autoFocus
              onClick={() => dialog.current?.close()}
            >
              <X size={20} />
            </button>
          </header>
          <div className="metric-dialog-body">
            <div className="metric-result">
              <strong>{value}</strong>
              <span className="badge">
                {qualityLabels[metric.quality] || metric.quality}
              </span>
            </div>
            <section>
              <h3>Como foi calculado</h3>
              <p>{readableReason(metric.reason)}</p>
            </section>
            <dl className="metric-context">
              <div>
                <dt>Fonte dos dados</dt>
                <dd>{originLabels[metric.origin] || metric.origin}</dd>
              </div>
              <div>
                <dt>Guerras consideradas</dt>
                <dd>{metric.warsConsidered}</dd>
              </div>
              <div>
                <dt>Início do período analisado</dt>
                <dd>{dateTime(metric.period?.from)}</dd>
              </div>
              <div>
                <dt>Fim do período analisado</dt>
                <dd>{dateTime(metric.period?.to)}</dd>
              </div>
            </dl>
            {(metric.insufficientSample || metric.incompletePeriod) && (
              <div className="metric-limitations">
                <h3>Limitações desta análise</h3>
                {metric.insufficientSample && (
                  <p>
                    A amostra ainda é insuficiente. O valor descreve apenas os
                    registros observados.
                  </p>
                )}
                {metric.incompletePeriod && (
                  <p>
                    O período está incompleto. O resultado pode representar
                    apenas parte da atividade.
                  </p>
                )}
              </div>
            )}
            <details className="metric-technical">
              <summary>
                Consultar dados técnicos e registros utilizados{" "}
                <span>{metric.dataUsed?.length || 0} registros</span>
              </summary>
              <dl>
                <dt>Origem</dt>
                <dd>{metric.origin}</dd>
                <dt>Qualidade</dt>
                <dd>{metric.quality}</dd>
                <dt>Base temporal original</dt>
                <dd>{metric.period?.basis || "Não informada"}</dd>
                <dt>Descrição original da fórmula</dt>
                <dd>{metric.reason}</dd>
              </dl>
              <h3>Dados utilizados</h3>
              {metric.dataUsed?.length ? (
                <ul>
                  {metric.dataUsed.map((x, i) => (
                    <li key={i}>{x}</li>
                  ))}
                </ul>
              ) : (
                <p>
                  Nenhum registro informado pelo backend para este indicador.
                </p>
              )}
            </details>
          </div>
        </dialog>,
        document.body,
      )}
    </>
  );
}
export function Metrics({
  metrics,
  keys,
}: {
  metrics: Record<string, Metric>;
  keys?: string[];
}) {
  return (
    <div className="metrics">
      {(keys || Object.keys(metrics))
        .filter((k) => metrics[k])
        .map((k) => (
          <MetricView
            key={k}
            name={label(k)}
            metric={metrics[k]}
            icon={
              "/assets/royale/" +
              (/donation/i.test(k)
                ? "cards-token"
                : /points|average|score|troph/i.test(k)
                  ? "crown-emblem"
                  : /war|deck|participation/i.test(k)
                    ? "war-emblem"
                    : "clan-emblem") +
              ".png"
            }
          />
        ))}
    </div>
  );
}
export function Person({ player }: { player: Pick<Player, "name" | "tag"> }) {
  return (
    <Link className="person" to={"/membros/" + encodeURIComponent(player.tag)}>
      <span className="avatar">{player.name.slice(0, 2).toUpperCase()}</span>
      <span>
        <b>{player.name}</b>
        <small>{player.tag}</small>
      </span>
    </Link>
  );
}
type PagedProps<T> = {
  path: string;
  render: (items: T[]) => ReactNode;
  empty?: string;
  refetchInterval?: number;
};
export function Paged<T>(props: PagedProps<T>) {
  return <PagedContent key={props.path} {...props} />;
}
function PagedContent<T>({
  path,
  render,
  empty,
  refetchInterval,
}: PagedProps<T>) {
  const [page, setPage] = useState(0);
  const q = useApi<Page<T>>(
    path + (path.includes("?") ? "&" : "?") + "page=" + page + "&size=20",
    refetchInterval,
  );
  return (
    <QueryState query={q}>
      {q.data &&
        (q.data.items.length ? render(q.data.items) : <Empty>{empty}</Empty>)}
      {q.data && q.data.totalElements > 0 && (
        <div className="pagination">
          <span>
            {q.data.totalElements} registros · página {page + 1} de{" "}
            {Math.ceil(q.data.totalElements / 20)}
          </span>
          <button
            aria-label="Página anterior"
            disabled={!page}
            onClick={() => setPage((p) => p - 1)}
          >
            <ChevronLeft size={16} />
          </button>
          <button
            aria-label="Próxima página"
            disabled={(page + 1) * 20 >= q.data.totalElements}
            onClick={() => setPage((p) => p + 1)}
          >
            <ChevronRight size={16} />
          </button>
        </div>
      )}
    </QueryState>
  );
}
export function Table({
  headers,
  children,
}: {
  headers: string[];
  children: ReactNode;
}) {
  return (
    <div className="table-scroll">
      <table>
        <thead>
          <tr>
            {headers.map((h, i) => (
              <th scope="col" key={i}>
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  );
}
