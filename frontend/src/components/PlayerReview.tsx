import { Link } from "react-router-dom";
import { useApi, date } from "../lib/api";
import {
  decisionLabels,
  saoPauloTime,
  type ReviewDecision,
  type WeeklyReviewReport,
} from "../lib/weeklyReview";
import { QueryState } from "./ui";

// Mounted only for leadership accounts; the server also protects these endpoints.
export function PlayerReview({ tag, week }: { tag: string; week: string }) {
  const review = useApi<WeeklyReviewReport>(
    "/leadership/weekly-review" + (week ? `?week=${week}` : ""),
    60000,
  );
  return (
    <section className="panel">
      <h2>Decisões da revisão semanal</h2>
      <QueryState query={review}>
        {review.data && <WeekDecisions tag={tag} week={review.data.week} />}
      </QueryState>
    </section>
  );
}
function WeekDecisions({ tag, week }: { tag: string; week: string }) {
  const query = useApi<ReviewDecision[]>(
    `/leadership/weekly-review/decisions?week=${week}`,
    60000,
  );
  const history = query.data
    ?.filter((d) => d.playerTag === tag)
    .sort((a, b) => b.revision - a.revision);
  return (
    <>
      <p>Semana de {date(week)} · decisões registradas pela liderança</p>
      <QueryState query={query}>
        {history?.length === 0 && (
          <p>Nenhuma decisão registrada para este membro nesta semana.</p>
        )}
        {history?.map((decision, index) => (
          <article key={decision.revision} className="leader-note">
            <strong>
              {index === 0 ? "Última decisão: " : "Decisão anterior: "}
              {decisionLabels[decision.action]}
            </strong>
            <p className="note-body">{decision.reason}</p>
            <small>
              {decision.createdBy} · {saoPauloTime(decision.createdAt)}
            </small>
          </article>
        ))}
      </QueryState>
      <Link to={`/expulsoes?${new URLSearchParams({ membro: tag, week })}`}>
        Abrir revisão deste membro →
      </Link>
    </>
  );
}
