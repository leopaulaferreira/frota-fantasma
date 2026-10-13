#!/usr/bin/env python3
"""Read-only checks of local analytics and official fields. Does not log credentials."""
import json, os, math, urllib.request, urllib.parse, http.cookiejar
from pathlib import Path
from datetime import datetime, timezone
root=Path(__file__).resolve().parent.parent
values={}
for line in (root/".env").read_text().splitlines():
    if "=" in line and not line.lstrip().startswith("#"):
        key,value=line.split("=",1);values[key.strip()]=value.strip().strip('"').strip("'")
base=os.environ.get("FROTA_URL","http://127.0.0.1:"+values.get("FRONTEND_PORT","5173"))
opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
def local(path,body=None,form=False):
    headers={};payload=None
    if body is not None:
        csrf=local("/auth/csrf");headers[csrf["headerName"]]=csrf["token"]
        headers["Content-Type"]="application/x-www-form-urlencoded" if form else "application/json"
        payload=(urllib.parse.urlencode(body) if form else json.dumps(body)).encode()
    return json.load(opener.open(urllib.request.Request(base+"/api"+path,data=payload,headers=headers),timeout=180))
local("/auth/login",{"username":values.get("AUTH_ADMIN_USERNAME","lider"),"password":values["AUTH_ADMIN_PASSWORD"]},True)
report=local("/analytics")
results=[];issues=[]
def check(name,ok,details):
    results.append({"check":name,"passed":bool(ok),"details":details})
def number(v):return isinstance(v,(int,float)) and not isinstance(v,bool)
def equal(a,b):return a is None and b is None or number(a) and number(b) and math.isclose(a,b,rel_tol=1e-9,abs_tol=1e-8)
# All available player war observations, including explicit zeros and former members.
for key,limit in [("recentAverage",4),("historicalAverage",None)]:
    failed=0;tested=0
    for p in report["players"]:
        points=[w["points"]["value"] for w in p["wars"][:limit] if number(w["points"]["value"])]
        expected=sum(points)/len(points) if points else None
        tested+=1
        if not equal(expected,p["metrics"][key]["value"]):failed+=1
    check(key,failed==0,{"players":tested,"mismatches":failed})
bad_decks=sum(1 for p in report["players"] if p["metrics"]["deckUtilizationPercent"]["value"] is not None)
check("unknown_individual_deck_capacity",bad_decks==0,{"fabricatedValues":bad_decks})
negative=sum(1 for p in report["players"] for d in p["donations"] if number(d["observedDonations"]["value"]) and d["observedDonations"]["value"]<0)
unflagged=sum(1 for p in report["players"] for d in p["donations"] if not d["observedDonations"]["incompletePeriod"])
check("donation_lower_bounds",negative==0 and unflagged==0,{"negativePeriods":negative,"unflaggedPeriods":unflagged})
awards=[h["playerTag"] for h in report["highlights"] if h["playerTag"]]
check("exclusive_provisional_titles",len(awards)==len(set(awards)),{"eligibleTitles":len(awards)})
war_map={w["warId"]:w for p in report["players"] for w in p["wars"]}
rank_bad=0;rank_count=0
for wid in war_map:
    detail=local("/wars/"+str(wid))
    known={p["playerTag"]:p["contribution"] for p in detail["participants"] if number(p["contribution"]) and p["contribution"]>=0}
    for p in report["players"]:
        w=next((w for w in p["wars"] if w["warId"]==wid),None)
        if not w:continue
        expected=1+sum(v>known[p["tag"]] for v in known.values()) if p["tag"] in known else None
        rank_count+=1
        if w.get("rank",{}).get("value")!=expected:rank_bad+=1
check("war_ranking",rank_bad==0,{"observations":rank_count,"mismatches":rank_bad})
# Independent official reads. Changes between captures are recorded as differences, not proof of a bug.
clan=values.get("CLAN_TAG","#RQJGC9GV")
official_base="https://api.clashroyale.com/v1/clans/"+urllib.parse.quote(clan,safe="")
def official(path):
    req=urllib.request.Request(official_base+path,headers={"Authorization":"Bearer "+values["CLASH_ROYALE_API_TOKEN"]})
    return json.load(urllib.request.urlopen(req,timeout=30))
official_checks=[]
try:
    current=official("")
    official_checks.append({"endpoint":"clan","httpStatus":200,"officialMembers":current.get("members"),"capturedMembers":report["clan"]["currentMembers"]["value"],"note":"Capturas em instantes diferentes podem divergir."})
    log=official("/riverracelog?limit=50")
    matched=0;mismatch=0;wars=0
    for entry in log.get("items",[]):
        local_war=next((w for w in war_map.values() if w["seasonId"]==entry.get("seasonId") and w["sectionIndex"]==entry.get("sectionIndex")),None)
        if not local_war:continue
        standing=next((s for s in entry.get("standings",[]) if s.get("clan",{}).get("tag")==clan),None)
        if not standing:continue
        detail=local("/wars/"+str(local_war["warId"]));known={p["playerTag"]:p for p in detail["participants"]};wars+=1
        for p in standing["clan"].get("participants",[]):
            stored=known.get(p["tag"])
            if stored is None:continue
            matched+=1
            if stored["contribution"]!=p.get("fame") or stored["decksUsed"]!=p.get("decksUsed"):mismatch+=1
    official_checks.append({"endpoint":"riverracelog","httpStatus":200,"matchingWars":wars,"participantsCompared":matched,"differences":mismatch})
except urllib.error.HTTPError as e:
    official_checks.append({"status":"UNVERIFIED","httpStatus":e.code,"note":"Não foi possível completar a comparação oficial nesta execução."})
except Exception as e:
    official_checks.append({"status":"UNVERIFIED","errorType":type(e).__name__})
output={"validatedAt":datetime.now(timezone.utc).isoformat(),"clanTag":clan,"players":len(report["players"]),"availableWars":len(war_map),"analyticsChecks":results,"officialChecks":official_checks,
        "limitations":["Doações permanecem limites inferiores incompletos.","Resets futuros exigem novas capturas; cenários são cobertos pelos testes.","Validação oficial cobre apenas guerras coincidentes disponíveis nesta resposta.","Classificações não são decisões automáticas.","Sem inventar dados indisponíveis."]}
(root/"docs/LIVE-VALIDATION.json").write_text(json.dumps(output,ensure_ascii=False,indent=2)+"\n")
local("/auth/logout",{})
print(json.dumps({"checks":len(results),"passed":sum(x["passed"] for x in results),"players":len(report["players"]),"officialChecks":official_checks},ensure_ascii=False))
if any(not r["passed"] for r in results):raise SystemExit(1)
