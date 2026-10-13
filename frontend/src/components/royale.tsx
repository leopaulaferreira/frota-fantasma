import { Link } from "react-router-dom";
import { ArrowUpRight } from "lucide-react";
import { date, type Highlight, type Player } from "../lib/api";
import { title } from "../lib/labels";
import { MetricView } from "./ui";

export const art = (name: string) => "/assets/royale/" + name + ".png";

export function FleetBanner({ week }: { week?: string }) {
  return (
    <section className="fleet-banner">
      <div className="fleet-banner-copy">
        <div className="eyebrow">
          <span className="live-dot" /> FROTA FANTASMA · CENTRAL DO CLÃ
        </div>
        <h2>
          Uma tripulação.
          <br />
          <span>Um legado.</span>
        </h2>
        <p>
          Cada guerra conta uma história. Conheça quem está ao seu lado e trace
          o próximo rumo da Frota.
        </p>
        <div className="banner-actions">
          <Link to="/membros" className="button primary">
            Explorar a tripulação <ArrowUpRight size={17} />
          </Link>
          <Link className="banner-link" to="/guerras">
            Diário de guerras →
          </Link>
        </div>
        <small className="banner-period">
          {week
            ? "SEMANA DE " + date(week)
            : "HISTÓRICO · PARTICIPAÇÃO · RECONHECIMENTO"}
        </small>
      </div>
      <div className="fleet-banner-art" aria-hidden="true">
        <div className="crest-ring" />
        <img className="banner-fisherman" src={art("fisherman")} alt="" />
        <img
          className="banner-ghost"
          src={art("royal-ghost")}
          alt=""
          fetchPriority="high"
        />
        <div className="banner-seal">
          <img src={art("clan-emblem")} alt="" />
          <span>
            FROTA
            <br />
            <b>FANTASMA</b>
          </span>
        </div>
      </div>
    </section>
  );
}

const awardArt: Record<
  string,
  { character: string; badge: string; label: string; number: string }
> = {
  CAPITAO_DA_GUERRA: {
    character: "skeleton-king",
    badge: "war-emblem",
    label: "Pontos na guerra",
    number: "01",
  },
  GUARDIAO_DA_FROTA: {
    character: "fisherman",
    badge: "cards-token",
    label: "Doações observadas",
    number: "02",
  },
  ALMA_IMORTAL: {
    character: "royal-ghost",
    badge: "crown-emblem",
    label: "Média de pontuação",
    number: "03",
  },
};

export function AwardCard({
  highlight: h,
  players,
  week,
}: {
  highlight: Highlight;
  players: Player[];
  week?: string;
}) {
  const visual = awardArt[h.title] || awardArt.CAPITAO_DA_GUERRA;
  const winner = players.find((p) => p.tag === h.playerTag);
  return (
    <article className={"royale-award award-" + visual.number}>
      <div className="award-stage" aria-hidden="true">
        <span className="award-number">{visual.number}</span>
        <img className="award-character" src={art(visual.character)} alt="" />
        <img className="award-badge" src={art(visual.badge)} alt="" />
      </div>
      <div className="award-content">
        <small className="eyebrow">
          PROVISÓRIO{week ? " · " + date(week) : ""}
        </small>
        <h3>{title(h.title)}</h3>
        {h.playerTag ? (
          <Link
            className="award-winner"
            to={"/membros/" + encodeURIComponent(h.playerTag)}
          >
            {winner?.name || h.playerTag} <ArrowUpRight size={14} />
          </Link>
        ) : (
          <p className="muted">Aguardando candidato elegível</p>
        )}
        <MetricView compact name={visual.label} metric={h.result} />
        {h.title === "CAPITAO_DA_GUERRA" && winner?.wars[0] && (
          <MetricView
            compact
            name="Decks na guerra"
            metric={winner.wars[0].decksUsed}
          />
        )}
      </div>
    </article>
  );
}
