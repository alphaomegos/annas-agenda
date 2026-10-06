# Flight reference data

`make_flight_assets.py` builds `app/src/main/assets/flights/` (06.10.2026):

| File | From | Licence |
|---|---|---|
| `airports.tsv` | [mwgg/Airports](https://github.com/mwgg/Airports) `airports.json`, every airport with an IATA code, plus `overrides_airports.tsv` (codes it lacks: KIV, FRU, TSE, SXF) and `overrides_cities.tsv` (city names it gets wrong) | MIT |
| `airlines.tsv` | [OpenFlights](https://openflights.org/data) `airlines.dat`, by IATA and ICAO code, an active airline winning a shared code, corrected by `overrides_airlines.tsv` | ODbL 1.0 — "Contains information from OpenFlights, made available under the Open Database License" |
| `aircraft.tsv` | `aircraft.tsv` here, written by hand from the public IATA type codes | — |

```
curl -O https://raw.githubusercontent.com/mwgg/Airports/master/airports.json
curl -O https://raw.githubusercontent.com/jpatokal/openflights/master/data/airlines.dat
python3 tools/flights/make_flight_assets.py airports.json airlines.dat app/src/main/assets/flights
```

Kosovo is `XK`, as the travel base names it (the source says `KS`).
