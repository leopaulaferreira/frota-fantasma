import { Link } from "react-router-dom";
import { ElderProspects } from "../components/ElderProspects";
import { Heading, QueryState } from "../components/ui";
import { useApi, type Report } from "../lib/api";
export function ElderCandidates() {
  const query = useApi<Report>("/analytics", 60000);
  return (
    <>
      <Heading
        eyebrow="DESENVOLVIMENTO DA TRIPULAÇÃO"
        title="Candidatos a Ancião"
      >
        <Link className="button" to="/comparar">
          Comparar membros →
        </Link>
      </Heading>
      <p className="lede">
        Avalie constância nas guerras, contribuição, tempo observado e contexto
        antes de reconhecer um novo Ancião.
      </p>
      <QueryState query={query}>
        {query.data && <ElderProspects report={query.data} />}
      </QueryState>
    </>
  );
}
