# -*- coding: utf-8 -*-
"""Сбор открытых данных World Athletics: топ-листы тройного (U20 и взрослые) → страницы спортсменов
(дата рождения, лучшие результаты сезона по годам, старты текущего сезона). Пауза между запросами."""
import json, re, sys, time, urllib.request, os

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
UA = {"User-Agent": "Mozilla/5.0"}


def get(url):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=40) as r:
        return r.read().decode("utf-8", "replace")


def next_data(html):
    m = re.search(r'<script id="__NEXT_DATA__"[^>]*>(.*?)</script>', html, re.S)
    return json.loads(m.group(1)) if m else None


lists = []
for sex in ("men", "women"):
    for cat, year in (("u20", 2022), ("u20", 2023), ("u20", 2026), ("senior", 2026)):
        lists.append((sex, cat, year))

ids = {}
for sex, cat, year in lists:
    url = (f"https://worldathletics.org/records/toplists/jumps/triple-jump/outdoor/{sex}/{cat}/{year}"
           f"?regionType=world&page=1&bestResultsOnly=true")
    try:
        html = get(url)
    except Exception as e:
        print("list fail", url, e); continue
    found = re.findall(r'href="/athletes/(athlete=\d+|[a-z-]+/[a-z0-9-]+-\d+)"', html)
    for f in found:
        ids.setdefault(f, (sex, cat, year))
    print(sex, cat, year, len(set(found)))
    time.sleep(1.5)

print("athletes:", len(ids))
done = 0
for slug, meta in ids.items():
    fn = os.path.join(OUT, re.sub(r"[^0-9A-Za-z_-]", "_", slug) + ".json")
    if os.path.exists(fn):
        continue
    try:
        html = get("https://worldathletics.org/athletes/" + slug)
        d = next_data(html)
        comp = d["props"]["pageProps"]["competitor"]
        keep = {
            "slug": slug, "list": meta,
            "birthDate": comp["basicData"].get("birthDate"),
            "progression": comp.get("progressionOfSeasonsBests"),
            "resultsByYear": comp.get("resultsByYear"),
        }
        json.dump(keep, open(fn, "w", encoding="utf-8"), ensure_ascii=False)
        done += 1
    except Exception as e:
        print("athlete fail", slug, e)
    time.sleep(1.5)
print("saved", done)
