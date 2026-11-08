#!/usr/bin/env python3
"""
Offline Timetable Headway Preprocessor for DepartureBoard.

Downloads official schedules from:
1. TfL Unified API (/Line/{id}/Timetable/{stopId} and /Line/{id}/StopPoints)
2. National Rail Darwin API (GetDepBoardWithDetails/{crs} with 120m window)

Computes empirical Peak (06:30-09:30, 16:00-19:30), Off-Peak (09:30-16:00, 19:30-22:30),
and Late-Night (22:30-06:00) median headways in minutes for lines, branches, and operators.

Usage:
    python3 scripts/preprocess_headways.py
"""
import json
import os
import pathlib
import ssl
import statistics
import time
import urllib.request
from collections import defaultdict

ROOT_DIR = pathlib.Path(__file__).resolve().parent.parent


def load_local_properties():
    props = {}
    prop_file = ROOT_DIR / "local.properties"
    if prop_file.exists():
        for line in prop_file.read_text().splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                props[k.strip()] = v.strip()
    return props


PROPS = load_local_properties()
TFL_KEY = os.environ.get("TFL_API_KEY") or PROPS.get("TFL_API_KEY", "")
NR_KEY = os.environ.get("NATIONAL_RAIL_API_KEY") or PROPS.get("NATIONAL_RAIL_API_KEY", "")
SSL_CTX = ssl._create_unverified_context()


def fetch_json(url, headers=None):
    hdrs = {"User-Agent": "Mozilla/5.0"}
    if headers:
        hdrs.update(headers)
    req = urllib.request.Request(url, headers=hdrs)
    with urllib.request.urlopen(req, context=SSL_CTX, timeout=25) as resp:
        return json.loads(resp.read().decode("utf-8"))


def compute_period_headways(known_journeys):
    times = sorted(set(
        (int(kj["hour"]) + (24 if int(kj["hour"]) < 4 else 0)) * 60 + int(kj["minute"])
        for kj in known_journeys
        if str(kj.get("hour", "")).isdigit() and str(kj.get("minute", "")).isdigit()
    ))
    if len(times) < 2:
        return None

    peak_gaps, offpeak_gaps, night_gaps = [], [], []
    for a, b in zip(times, times[1:]):
        gap = b - a
        if gap <= 0 or gap > 90:
            continue
        mid = (a + b) / 2.0
        if (390 <= mid < 570) or (960 <= mid < 1170):
            peak_gaps.append(gap)
        elif (570 <= mid < 960) or (1170 <= mid < 1350):
            offpeak_gaps.append(gap)
        else:
            night_gaps.append(gap)

    def med(lst, fallback):
        return int(round(statistics.median(lst))) if lst else fallback

    off = med(offpeak_gaps, 15)
    pk = med(peak_gaps, off)
    nt = med(night_gaps, max(off, int(round(off * 1.5))))
    return {
        "peakMinutes": max(1, pk),
        "offPeakMinutes": max(1, off),
        "lateNightMinutes": max(1, nt),
        "dailyDepartures": len(times),
    }


def fetch_tfl_timetable(line_id, stop_id):
    url = f"https://api.tfl.gov.uk/Line/{line_id}/Timetable/{stop_id}?app_key={TFL_KEY}"
    data = fetch_json(url)
    if not data.get("timetable") and data.get("disambiguation"):
        opts = data["disambiguation"].get("disambiguationOptions", [])
        for opt in opts:
            uri = opt.get("uri", "")
            if uri:
                sep = "&" if "?" in uri else "?"
                url2 = f"https://api.tfl.gov.uk{uri}{sep}app_key={TFL_KEY}"
                try:
                    data2 = fetch_json(url2)
                    if data2.get("timetable", {}).get("routes"):
                        data = data2
                        break
                except Exception:
                    pass

    routes = data.get("timetable", {}).get("routes", [])
    if not routes:
        return None

    best_journeys = []
    for r in routes:
        for sched in r.get("schedules", []):
            name = sched.get("name", "").lower()
            if "mon" in name or "weekday" in name or "friday" in name:
                kjs = sched.get("knownJourneys", [])
                if len(kjs) > len(best_journeys):
                    best_journeys = kjs
        if not best_journeys and r.get("schedules"):
            best_journeys = r["schedules"][0].get("knownJourneys", [])

    return compute_period_headways(best_journeys) if best_journeys else None


TFL_TARGETS = [
    ("victoria", "940GZZLUOXC", "Victoria trunk"),
    ("jubilee", "940GZZLUWLO", "Jubilee trunk"),
    ("central", "940GZZLUOXC", "Central trunk"),
    ("northern", "940GZZLUEUS", "Northern trunk"),
    ("piccadilly", "940GZZLULSQ", "Piccadilly trunk"),
    ("bakerloo", "940GZZLUOXC", "Bakerloo trunk"),
    ("district", "940GZZLUVIC", "District trunk"),
    ("circle", "940GZZLUVIC", "Circle trunk"),
    ("metropolitan", "940GZZLUBST", "Metropolitan trunk"),
    ("hammersmith-city", "940GZZLUBST", "Hammersmith & City trunk"),
    ("waterloo-city", "940GZZLUWLO", "Waterloo & City"),
    ("dlr", "940GZZDLBNK", "DLR trunk"),
    ("tram", "940GZZCRECR", "London Trams (East Croydon)"),
    ("northern", "940GZZLUMHL", "Branch: Mill Hill East"),
    ("metropolitan", "940GZZLUCSM", "Branch: Chesham"),
    ("metropolitan", "940GZZLUAMS", "Branch: Amersham"),
    ("metropolitan", "940GZZLUWAF", "Branch: Watford"),
    ("piccadilly", "940GZZLUHR4", "Branch: Heathrow Terminal 4"),
    ("piccadilly", "940GZZLUHR5", "Branch: Heathrow Terminal 5"),
    ("central", "940GZZLURVY", "Branch: Roding Valley"),
    ("central", "940GZZLUCWL", "Branch: Chigwell"),
    ("central", "940GZZLUGGH", "Branch: Grange Hill"),
]


def main():
    print("=== Downloading & Preprocessing TfL Timetables ===")
    for line_id, stop_id, label in TFL_TARGETS:
        try:
            res = fetch_tfl_timetable(line_id, stop_id)
            print(f"{label:35s} ({line_id:16s} @ {stop_id}) -> {res}")
        except Exception as e:
            print(f"{label:35s} ({line_id:16s} @ {stop_id}) -> ERROR: {e}")
        time.sleep(0.15)


if __name__ == "__main__":
    main()
