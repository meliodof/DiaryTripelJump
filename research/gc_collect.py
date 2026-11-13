# -*- coding: utf-8 -*-
"""GoldenCheetah OpenData (OSF 6hfpz): у каждого спортсмена берётся только сводка по тренировкам
(дата, вид спорта, длительность, нагрузка TSS, максимальная мощность на отрезках). Архив после разбора удаляется."""
import json, sys, os, io, time, zipfile, urllib.request

OUT = sys.argv[1]
N = int(sys.argv[2]) if len(sys.argv) > 2 else 60
os.makedirs(OUT, exist_ok=True)
KEEP = ["coggan_tss", "trimp_points", "time_riding", "workout_time", "1s_critical_power", "10s_critical_power",
        "1m_critical_power", "5m_critical_power", "20m_critical_power", "average_power"]

files, page = [], 1
while len(files) < 1500 and page <= 30:
    d = json.load(urllib.request.urlopen(
        f"https://api.osf.io/v2/nodes/6hfpz/files/osfstorage/?page[size]=50&page={page}", timeout=60))
    for x in d["data"]:
        a = x["attributes"]
        if a["kind"] == "file" and a["name"].endswith(".zip") and 1_500_000 <= (a.get("size") or 0) <= 15_000_000:
            files.append((a["name"], x["links"]["download"]))
    page += 1
    time.sleep(0.5)
print("candidates", len(files), flush=True)

saved = len([f for f in os.listdir(OUT) if f.endswith(".json")])
for name, url in files:
    if saved >= N:
        break
    fn = os.path.join(OUT, name.replace(".zip", ".json"))
    if os.path.exists(fn):
        continue
    try:
        b = urllib.request.urlopen(url, timeout=300).read()
        z = zipfile.ZipFile(io.BytesIO(b))
        js = [n for n in z.namelist() if n.endswith(".json")][0]
        j = json.loads(z.read(js))
        rides = []
        for r in j.get("RIDES", []):
            m = r.get("METRICS", {})
            def val(k):
                v = m.get(k)
                if isinstance(v, list): v = v[0]
                try: return float(v)
                except Exception: return None
            rides.append({"date": r.get("date"), "sport": r.get("sport"), **{k: val(k) for k in KEEP}})
        if len(rides) >= 100:
            json.dump({"id": name[:-4], "athlete": j.get("ATHLETE"), "rides": rides}, open(fn, "w", encoding="utf-8"))
            saved += 1
            print(saved, name, len(rides), flush=True)
        del b, z
    except Exception as e:
        print("fail", name, e, flush=True)
    time.sleep(1.0)
print("saved", saved)
