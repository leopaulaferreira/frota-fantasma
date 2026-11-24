import { Link } from "react-router-dom";
import { dateTime, useApi } from "../lib/api";

import { collectionNames } from "../lib/collections";
export function CollectionFreshness({ generatedAt }: { generatedAt: string }) {
  const query = useApi<{ sources: { name: string; lastSuccess: string | null; stale: boolean }[] }>("/operations");
  return <details className="collection-freshness">
    <summary>Análise calculada em {dateTime(generatedAt)} · Ver horários das coletas</summary>
    <p>O cálculo usa os registros disponíveis. Seu horário não indica uma nova coleta.</p>
    {query.isPending ? <p role="status">Consultando coletas…</p> : query.error ? <p role="alert">Não foi possível consultar os horários. <button onClick={() => query.refetch()}>Tentar novamente</button></p> : <ul>
      {query.data?.sources.map(source => <li key={source.name}>
        <strong>{collectionNames[source.name] ?? source.name}</strong>
        <span>{source.lastSuccess ? `Último sucesso: ${dateTime(source.lastSuccess)}` : "Sem coleta bem-sucedida registrada"}</span>
        <span className={source.stale || !source.lastSuccess ? "collection-warning" : ""}>{!source.lastSuccess ? "Aguardando dados" : source.stale ? "Coleta desatualizada" : "Dentro do intervalo esperado"}</span>
      </li>)}
    </ul>}
    <Link to="/operacoes">Consultar operações e falhas →</Link>
  </details>;
}
