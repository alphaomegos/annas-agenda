# Achievement pictures

The app looks for `app/src/main/assets/achievements/<id>.webp` (or `.png`, 06.10) and
shows a trophy while a picture is missing. One picture per achievement: the
app greys it out itself while the achievement is not earned.

- 1024 × 1024 px, PNG, square, sRGB.
- One subject, centred, about 10 % margin on every side; it must read at
  64 × 64 (the card) as well as at 220 dp (the dialog).
- A plain or softly graded background filling the square (no transparency);
  the app rounds the corners.
- The app's style (06.10): an oil painting on canvas — visible brushwork and
  canvas grain, warm muted old-master colours, a quietly absurd, deadpan
  humour — as in the reference picture of the white cat the generator is
  shown with every prompt. The same style for the whole set.
- No text, letters or digits in the picture; no real logos, liveries or
  brands.
- The card shows the picture square across its whole width, the dialog
  larger still: keep the subject large and simple enough to read small.

| id | name |
|---|---|
| first_flight | First flight |
| flights_100 | A hundred flights |
| flights_1000 | A thousand flights |
| around_the_earth | Round the Earth |
| to_the_moon | To the Moon |
| day_in_the_air | A day in the air |
| week_in_the_air | A week in the air |
| month_in_the_air | A month in the air |
| year_in_the_air | A year in the air |
| countries_10 | Ten countries |
| countries_50 | Fifty countries |
| airlines_10 | Ten airlines |
| aircraft_10 | Ten aircraft |
| airports_10 | Ten airports |
| airports_100 | A hundred airports |
| airports_1000 | A thousand airports |
| time_killer | Time killer |
| time_traveller | Time traveller |
| flight_attendant | Flight attendant |

Shipped as WebP, 1024 × 1024, quality 75 (Pillow: `im.save(path, 'WEBP',
quality=75, method=6)`), about 170 KB each.
