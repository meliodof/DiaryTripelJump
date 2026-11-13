# -*- coding: utf-8 -*-
"""Сбор открытых протоколов TFRRS: истории стартов в тройном у спортсменов NCAA.
Обход: страница спортсмена → протоколы тройного → другие спортсмены. Пауза между запросами."""
import json, re, sys, time, urllib.request, os, datetime

OUT = sys.argv[1]
LIMIT = int(sys.argv[2]) if len(sys.argv) > 2 else 150
os.makedirs(OUT, exist_ok=True)
UA = {"User-Agent": "Mozilla/5.0"}
MONTHS = {m: i for i, m in enumerate(["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"], 1)}


def get(url):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=40) as r:
        return r.read().decode("utf-8", "replace")


def parse_athlete(html):
    rows = []
    for block in html.split('<table class="table table-hover')[1:]:
        dm = re.search(r'<span style="color:black;font-size:14px;">\s*(?:&nbsp;)*\s*([A-Z][a-z]{2})\s+(\d{1,2})[^,<]*,\s*(\d{4})', block)
        if not dm:
            continue
        try:
            d = datetime.date(int(dm.group(3)), MONTHS[dm.group(1)], int(dm.group(2)))
        except Exception:
            continue
        nm = re.search(r'<th[^>]*>\s*<a[^>]*>([^<]+)</a>', block)
        meet = nm.group(1).strip() if nm else ""
        for rm in re.finditer(r'<td class="panel-heading-text" width="27%">\s*(TJ|LJ|HJ|PV)\s*</td>.*?>(\d{1,2}\.\d{2})m</a>(.*?)</td>', block, re.S):
            wind = re.search(r'\(([+-]?\d+\.\d)\)', rm.group(3))
            # в зале ветра нет: на стадионе TFRRS пишет ветер почти всегда, сезон зала — декабрь–март
            rows.append({"event": rm.group(1), "date": d.isoformat(), "mark": float(rm.group(2)), "meet": meet,
                         "wind": float(wind.group(1)) if wind else None,
                         "indoor": wind is None and d.month in (12, 1, 2, 3)})
    tj_results = sorted(set(re.findall(r'href="(https://www\.tfrrs\.org/results/\d+/\d+/[^"]*Triple-Jump)"', html)))
    cls = re.search(r'\b(FR|SO|JR|SR)-(\d)\b', html)
    return rows, tj_results, (cls.group(0) if cls else None)


# стартовые спортсмены для обхода — номера профилей TFRRS через запятую (в репозитории не храним)
REFETCH = len(sys.argv) > 3 and sys.argv[3] == "refetch"
queue = [] if REFETCH else (sys.argv[3].split(",") if len(sys.argv) > 3 else [])
if REFETCH:  # перекачать уже собранных спортсменов с новыми полями, без обхода
    queue = [f[:-5] for f in os.listdir(OUT) if f.endswith(".json")]
seen, results_seen, saved = set(), set(), 0
while queue and saved < LIMIT:
    aid = queue.pop(0)
    if aid in seen:
        continue
    seen.add(aid)
    fn = os.path.join(OUT, aid + ".json")
    try:
        html = get(f"https://www.tfrrs.org/athletes/{aid}")
    except Exception as e:
        print("fail", aid, e); time.sleep(2); continue
    rows, tj_results, cls = parse_athlete(html)
    tj = [r for r in rows if r["event"] == "TJ"]
    if len(tj) >= 6:
        json.dump({"id": aid, "class": cls, "tj": tj, "other": [r for r in rows if r["event"] != "TJ"]},
                  open(fn, "w", encoding="utf-8"))
        saved += 1
        print(saved, aid, len(rows))
    time.sleep(1.5)
    if REFETCH:
        continue
    # новые спортсмены — из протоколов тройного (не больше двух на спортсмена, чтобы разнообразить выборку)
    for url in tj_results[:2]:
        if url in results_seen or len(queue) > 400:
            continue
        results_seen.add(url)
        try:
            page = get(url)
        except Exception:
            continue
        for other in re.findall(r'tfrrs\.org/athletes/(\d+)', page):
            if other not in seen and other not in queue:
                queue.append(other)
        time.sleep(1.5)
print("saved", saved)
