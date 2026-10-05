#!/usr/bin/env python3
"""
Builds the travel section's two assets from public data (05.10.2026):

  app/src/main/assets/travel/countries.tsv  - the country base: ISO 3166-1
      alpha-2 code, default continent, English name.
  app/src/main/assets/travel/world_map.txt  - every country's outline,
      projected (Robinson) onto a fixed grid, simplified, delta-encoded.

Inputs:
  ne_50m_admin_0_countries.geojson - Natural Earth 1:50m admin-0 countries
      (public domain, naturalearthdata.com), e.g. from
      https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/
  iso.tsv - "code<TAB>English name" for every ISO 3166-1 code, as
      java.util.Locale.getISOCountries() lists them (see README.md).

Run:  python3 make_travel_assets.py ne50.geojson iso.tsv <repo>/app/src/main/assets/travel
"""
import json, math, sys, os

W = 8000                     # grid width; height follows the projection

# Robinson projection table (Snyder): latitude every 5 degrees -> (X, Y).
ROB = [(1.0000, 0.0000), (0.9986, 0.0620), (0.9954, 0.1240), (0.9900, 0.1860),
       (0.9822, 0.2480), (0.9730, 0.3100), (0.9600, 0.3720), (0.9427, 0.4340),
       (0.9216, 0.4958), (0.8962, 0.5571), (0.8679, 0.6176), (0.8350, 0.6769),
       (0.7986, 0.7346), (0.7597, 0.7903), (0.7186, 0.8435), (0.6732, 0.8936),
       (0.6213, 0.9394), (0.5722, 0.9761), (0.5322, 1.0000)]
HALF_H = 0.5072 * W / 2      # Robinson: height / width = 0.5072
H = int(round(HALF_H * 2))

def robinson(lon, lat):
    a = min(abs(lat), 90.0) / 5.0
    i = min(int(a), 17)
    f = a - i
    x = ROB[i][0] + (ROB[i + 1][0] - ROB[i][0]) * f
    y = ROB[i][1] + (ROB[i + 1][1] - ROB[i][1]) * f
    px = W / 2 + x * (lon / 180.0) * (W / 2)
    py = HALF_H - math.copysign(y, lat) * HALF_H
    return px, py

def simplify(points, tol):
    """Douglas-Peucker, iterative."""
    if len(points) < 4:
        return points
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        s, e = stack.pop()
        ax, ay = points[s]; bx, by = points[e]
        dx, dy = bx - ax, by - ay
        norm = math.hypot(dx, dy)
        best, at = -1.0, -1
        for k in range(s + 1, e):
            px, py = points[k]
            # A closed ring starts and ends on the same point: then the
            # distance is to that point, not to a line through it.
            d = (abs(dy * px - dx * py + bx * ay - by * ax) / norm if norm > 1e-9
                 else math.hypot(px - ax, py - ay))
            if d > best:
                best, at = d, k
        if best > tol:
            keep[at] = True
            stack.append((s, at)); stack.append((at, e))
    return [p for p, k in zip(points, keep) if k]

# Where the base disagrees with Natural Earth's own continent: the usual
# travellers' choice (Eduard, 05.10).
CONTINENT_FIX = {
    'RU': 'EUROPE', 'TR': 'EUROPE', 'CY': 'EUROPE', 'GE': 'EUROPE', 'AM': 'EUROPE',
    'AZ': 'EUROPE', 'KZ': 'ASIA', 'EG': 'AFRICA',
}
NE_CONTINENT = {
    'Africa': 'AFRICA', 'Antarctica': 'ANTARCTICA', 'Asia': 'ASIA', 'Europe': 'EUROPE',
    'North America': 'NORTH_AMERICA', 'Oceania': 'OCEANIA', 'South America': 'SOUTH_AMERICA',
}
UN_REGION = {'Africa': 'AFRICA', 'Asia': 'ASIA', 'Europe': 'EUROPE', 'Oceania': 'OCEANIA',
             'Americas': 'NORTH_AMERICA', 'Antarctica': 'ANTARCTICA'}

# Codes Natural Earth draws inside another country's outline, or not at all:
# where they are (lat, lon), how far around that their islands lie, and their
# continent, and the feature that holds them. Polygons of that feature lying
# within the radius become theirs; a code left with no polygon is a dot.
TERRITORIES = {
    'BQ': (12.18, -68.25, 1.5, 'NORTH_AMERICA', 'NL'), 'BV': (-54.42, 3.36, 1.0, 'ANTARCTICA', 'NO'),
    'CC': (-12.17, 96.84, 1.0, 'ASIA', '*Indian Ocean Territories'),
    'CX': (-10.49, 105.62, 1.0, 'ASIA', '*Indian Ocean Territories'),
    'GF': (4.0, -53.0, 3.5, 'SOUTH_AMERICA', 'FR'), 'GI': (36.14, -5.35, 0.2, 'EUROPE', 'GB'),
    'GP': (16.25, -61.58, 1.0, 'NORTH_AMERICA', 'FR'), 'MQ': (14.64, -61.02, 0.5, 'NORTH_AMERICA', 'FR'),
    'RE': (-21.11, 55.53, 1.0, 'AFRICA', 'FR'), 'SJ': (78.5, 18.0, 8.0, 'EUROPE', 'NO'),
    'TK': (-9.2, -171.85, 1.0, 'OCEANIA', 'NZ'), 'UM': (19.28, 166.65, 1.0, 'OCEANIA', 'US'),
    'YT': (-12.83, 45.17, 0.6, 'AFRICA', 'FR'),
}
# Natural Earth features without an ISO code: folded into the country ISO
# counts them in; Siachen is left out.
FOLD = {'Somaliland': 'SO', 'Northern Cyprus': 'CY', 'Indian Ocean Territories': None,
        'Ashmore and Cartier Islands': 'AU', 'Siachen Glacier': None}

def centroid(ring):
    xs = [p[0] for p in ring]; ys = [p[1] for p in ring]
    return sum(ys) / len(ys), sum(xs) / len(xs)   # lat, lon

def main(ne_path, iso_path, out_dir):
    names = {}
    for line in open(iso_path, encoding='utf-8'):
        code, name = line.rstrip('\n').split('\t')[:2]
        names[code] = name
    names.setdefault('XK', 'Kosovo')

    shapes = {}          # code -> list of rings (lon, lat)
    continent = {}
    for f in json.load(open(ne_path, encoding='utf-8'))['features']:
        p = f['properties']
        code = p['ISO_A2_EH'] if p['ISO_A2_EH'] != '-99' else p['ISO_A2']
        if p['ADMIN'] in FOLD:
            code = FOLD[p['ADMIN']] or ('*' + p['ADMIN'])
        g = f['geometry']
        polys = g['coordinates'] if g['type'] == 'MultiPolygon' else [g['coordinates']]
        for poly in polys:
            shapes.setdefault(code, []).append(poly)       # [outer, hole, ...]
        if code in names and code not in continent:
            c = NE_CONTINENT.get(p['CONTINENT']) or UN_REGION.get(p['REGION_UN'])
            if p['REGION_UN'] == 'Americas' and p['SUBREGION'] == 'South America':
                c = 'SOUTH_AMERICA'
            continent[code] = c

    points = {}
    for code, (lat, lon, radius, cont, parent) in TERRITORIES.items():
        continent[code] = cont
        taken = []
        for other, polys in list(shapes.items()):
            if other != parent:
                continue
            for poly in list(polys):
                clat, clon = centroid(poly[0])
                if math.hypot(clat - lat, clon - lon) <= radius:
                    polys.remove(poly); taken.append(poly)
        if taken:
            shapes.setdefault(code, []).extend(taken)
        else:
            points[code] = robinson(lon, lat)
    for code in list(shapes):
        if code.startswith('*'):
            del shapes[code]
    continent.update(CONTINENT_FIX)

    missing = sorted(c for c in names if c not in continent)
    if missing:
        sys.exit('no continent for: ' + ', '.join(missing))

    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, 'countries.tsv'), 'w', encoding='utf-8') as out:
        out.write('# The travel section\'s country base: ISO 3166-1 alpha-2 code, default\n'
                  '# continent, English name. Names in other languages come from the\n'
                  '# system by the code; this English one is the fallback. Generated by\n'
                  '# tools/travel/make_travel_assets.py; continents follow Natural Earth,\n'
                  '# with the travellers\' choice for countries on a border.\n')
        for code in sorted(names):
            out.write(f'{code}\t{continent[code]}\t{names[code]}\n')

    total = 0
    with open(os.path.join(out_dir, 'world_map.txt'), 'w', encoding='utf-8') as out:
        out.write('# Country outlines for the travel map. Made with Natural Earth\n'
                  '# (public domain), Robinson projection. Generated by\n'
                  '# tools/travel/make_travel_assets.py.\n'
                  '# "size W H"; then per country "CODE<TAB>ring;ring" where a ring is\n'
                  '# x,y followed by dx,dy steps on the W x H grid, or "CODE<TAB>@x,y"\n'
                  '# for a country drawn as a dot. Rings fill even-odd.\n')
        out.write(f'size {W} {H}\n')
        for code in sorted(shapes):
            if code not in names:
                continue
            rings = []
            for poly in shapes[code]:
                for ring in poly:
                    pts = [robinson(lon, lat) for lon, lat in ring]
                    pts = simplify(pts, 0.7)
                    q = []
                    for x, y in pts:
                        p = (int(round(x)), int(round(y)))
                        if not q or q[-1] != p:
                            q.append(p)
                    if len(q) < 3:
                        continue
                    nums = [q[0][0], q[0][1]]
                    for (ax, ay), (bx, by) in zip(q, q[1:]):
                        nums += [bx - ax, by - ay]
                    rings.append(','.join(map(str, nums)))
            if rings:
                line = f'{code}\t' + ';'.join(rings) + '\n'
            elif not shapes[code]:
                continue
            else:
                lat, lon = centroid(shapes[code][0][0])
                x, y = robinson(lon, lat)
                line = f'{code}\t@{int(round(x))},{int(round(y))}\n'
            total += len(line); out.write(line)
        for code in sorted(points):
            x, y = points[code]
            out.write(f'{code}\t@{int(round(x))},{int(round(y))}\n')
    print(f'{len(names)} countries, map {total // 1024} KB, dots: {sorted(points)}')

if __name__ == '__main__':
    main(*sys.argv[1:4])
