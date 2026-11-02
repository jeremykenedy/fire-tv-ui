# Hourly weather scope and sources

The approved hourly forecast request is a real weather source, manual location,
units and refresh/error handling. This implementation uses the National Weather
Service and covers only locations for which its points response supplies an hourly
forecast. It requests no location permission or API key and buys no service.
Radar imagery and UV are separate implemented widgets; observations, air quality
and pollen are not part of this hourly forecast widget. The NWS weather API's
radar endpoints contain status rather than display imagery.

Official references checked for this implementation:

- https://www.weather.gov/documentation/services-web-api
- https://www.weather.gov/disclaimer
- https://api.weather.gov/openapi.json

Requests carry an identifying project User-Agent. The points mapping is refreshed
within its HTTP freshness lifetime, capped at 24 hours. Hourly freshness is capped
at one hour while respecting a shorter provider lifetime. Conditional requests
retain ETag and Last-Modified. No-store responses are not persisted. No-cache and
must-revalidate entries are not shown stale after an error. Other stale forecasts
are labeled and limited to a day of cache age; source issue timestamps are always
shown. The UI honors location or TV time zones and never treats missing values as
zero. At most four entries contain bodies of up to one MiB plus bounded headers.
Cache files contain public forecast data and the manually selected location;
no credentials or account data are used.

Live anonymous example reads confirmed points/forecastHourly discovery and
per-response cache lifetimes. Fixtures establish parser, HTTP/cache and native
lifecycle behavior separately from live service availability or physical TV proof.


2026-11-02 live check at 09:38 UTC: the official NWS points request for
45.5152,-122.6784 returned 200 and discovered
https://api.weather.gov/gridpoints/PQR/113,104/forecast/hourly . That endpoint
returned 200 with 156 hourly periods and updateTime 2026-11-02T08:41:24+00:00.
A separate official KRTX radar GIF request returned 200, 32660 bytes, HTTP
Last-Modified Mon, 02 Nov 2026 09:33:29 GMT. These are host transport observations,
not target-device or general coverage proof. Weather host tests and SDK36
compilation pass in this continuation.

## City selection

The settings screen can search U.S. Census TIGERweb incorporated places and
census designated places on explicit request. Search sends the city name to
the Census Bureau. The user chooses a named state and approximate Census place
center; no match is selected automatically and no existing choice is saved
until the user selects Save. A place center is not proof of NWS forecast
coverage. Manual coordinates remain available. Search is limited to these U.S.
Census place layers and is not a worldwide or full address geocoder.

Census layer/service documentation:

- https://tigerweb.geo.census.gov/arcgis/rest/services/TIGERweb/Places_CouSub_ConCity_SubMCD/MapServer
- https://tigerweb.geo.census.gov/arcgis/rest/services/TIGERweb/Places_CouSub_ConCity_SubMCD/MapServer/4
- https://tigerweb.geo.census.gov/arcgis/rest/services/TIGERweb/Places_CouSub_ConCity_SubMCD/MapServer/5

Host fixture tests cover duplicate names across states, invalid data and query
boundaries. A live host search for Portland returned distinct Oregon and Maine
choices. Native search selection, lifecycle and physical-TV behavior remain
unverified. Grouped Weather has per-instance configuration and retains legacy location and unit fields. Native grouped editing and migration proof remain outstanding.
