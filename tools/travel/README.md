# Travel assets

`make_travel_assets.py` builds `app/src/main/assets/travel/countries.tsv` (the
country base) and `world_map.txt` (country outlines) from public data. Run it
again only to change the base or the map; the app reads the two files as they
are, and `TravelAssetsTest` checks them.

```bash
curl -sSL -o ne50.geojson \
  https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_50m_admin_0_countries.geojson
cat > Iso.java <<'J'
import java.util.*;
public class Iso { public static void main(String[] a){ for(String c: Locale.getISOCountries())
  System.out.println(c+"\t"+new Locale("",c).getDisplayCountry(Locale.ENGLISH)); } }
J
java -Dstdout.encoding=UTF-8 Iso.java > iso.tsv
python3 make_travel_assets.py ne50.geojson iso.tsv ../../app/src/main/assets/travel
```

Natural Earth is in the public domain; the map file says "Made with Natural
Earth" in its header, as they ask.
