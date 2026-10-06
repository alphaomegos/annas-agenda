#!/usr/bin/env python3
"""
Builds app/src/main/assets/flights/ (06.10.2026):

  airports.tsv  IATA, ICAO, lat, lon, country (ISO 3166-1 alpha-2), IANA time
                zone, city, name — from mwgg/Airports (MIT,
                https://github.com/mwgg/Airports), every airport with an IATA
                code, plus overrides_airports.tsv for codes it lacks and
                overrides_cities.tsv for city names it gets wrong.
  airlines.tsv  code (IATA, or ICAO where an old log used it), name — from
                OpenFlights airlines.dat (ODbL 1.0,
                https://openflights.org/data), corrected by
                overrides_airlines.tsv.
  aircraft.tsv  copied from here (written by hand).

Usage: make_flight_assets.py airports.json airlines.dat OUT_DIR
"""
import csv, json, os, sys

def clean(s):
    return (s or '').replace('\t', ' ').replace('\n', ' ').strip()

def main(airports_json, airlines_dat, out):
    here = os.path.dirname(os.path.abspath(__file__))
    os.makedirs(out, exist_ok=True)

    rows = {}
    for a in json.load(open(airports_json, encoding='utf-8')).values():
        iata = clean(a.get('iata')).upper()
        if len(iata) != 3 or not iata.isalpha() or not a.get('tz'):
            continue
        country = clean(a.get('country')).upper()
        country = {'KS': 'XK'}.get(country, country)   # Kosovo, as the travel base names it
        rows.setdefault(iata, [iata, clean(a.get('icao')), '%.4f' % a['lat'], '%.4f' % a['lon'],
                               country, clean(a['tz']), clean(a.get('city')), clean(a.get('name'))])
    for line in open(os.path.join(here, 'overrides_airports.tsv'), encoding='utf-8'):
        if line.startswith('#') or not line.strip(): continue
        f = line.rstrip('\n').split('\t')
        rows[f[0]] = f
    for line in open(os.path.join(here, 'overrides_cities.tsv'), encoding='utf-8'):
        if line.startswith('#') or not line.strip(): continue
        code, city = line.rstrip('\n').split('\t')
        if code in rows: rows[code][6] = city
    with open(os.path.join(out, 'airports.tsv'), 'w', encoding='utf-8', newline='\n') as w:
        w.write('# IATA\tICAO\tlat\tlon\tcountry\ttz\tcity\tname. mwgg/Airports (MIT); see tools/flights.\n')
        for k in sorted(rows):
            w.write('\t'.join(rows[k]) + '\n')

    names = {}
    for r in csv.reader(open(airlines_dat, encoding='utf-8')):
        if len(r) < 8: continue
        name = clean(r[1]); iata = clean(r[3]).upper(); icao = clean(r[4]).upper(); active = r[7] == 'Y'
        if not name or name == '\\N': continue
        for code, ok in ((iata, len(iata) == 2), (icao, len(icao) == 3 and icao.isalpha())):
            if not ok or code in ('\\N', '-', 'N/A'): continue
            # An active airline wins the code over a dead one.
            if code not in names or (active and not names[code][1]):
                names[code] = (name, active)
    for line in open(os.path.join(here, 'overrides_airlines.tsv'), encoding='utf-8'):
        if line.startswith('#') or not line.strip(): continue
        code, name = line.rstrip('\n').split('\t')
        names[code] = (name, True)
    with open(os.path.join(out, 'airlines.tsv'), 'w', encoding='utf-8', newline='\n') as w:
        w.write('# code\tname. OpenFlights (ODbL 1.0, openflights.org/data); see tools/flights.\n')
        for k in sorted(names):
            w.write(k + '\t' + names[k][0] + '\n')

    with open(os.path.join(here, 'aircraft.tsv'), encoding='utf-8') as r, \
         open(os.path.join(out, 'aircraft.tsv'), 'w', encoding='utf-8', newline='\n') as w:
        w.write(r.read())
    print(len(rows), 'airports,', len(names), 'airline codes')

if __name__ == '__main__':
    main(*sys.argv[1:4])
