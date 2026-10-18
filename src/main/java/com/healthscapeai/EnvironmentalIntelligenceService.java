package com.healthscapeai;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.*;

/** Retrieves model-based environmental conditions from Open-Meteo. Values are forecasts/model estimates, not ground sensors. */
@Service
public class EnvironmentalIntelligenceService {
    private final RestClient http = RestClient.builder()
            .requestFactory(timeouts())
            .defaultHeader("User-Agent", "HealthScapeAI/1.0")
            .build();

    private static org.springframework.http.client.SimpleClientHttpRequestFactory timeouts() {
        var f = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        f.setConnectTimeout(java.time.Duration.ofSeconds(5));
        f.setReadTimeout(java.time.Duration.ofSeconds(10));
        return f;
    }

    private List<Map<String, Object>> searchOpenMeteo(String query) {
        String q = query == null ? "" : query.trim();
        if (q.length() < 2 || q.length() > 100) return List.of();
        JsonNode root = http.get().uri(keyed(UriComponentsBuilder.fromUriString(host("geocoding-api.open-meteo.com","customer-geocoding-api.open-meteo.com") + "/v1/search"))
                .queryParam("name", q).queryParam("count", 8).queryParam("language", "en").queryParam("format", "json").build().encode().toUri())
                .retrieve().body(JsonNode.class);
        List<Map<String, Object>> out = new ArrayList<>();
        JsonNode results = root == null ? null : root.path("results");
        if (results != null && results.isArray()) for (JsonNode n : results) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", n.path("name").asText()); item.put("admin1", n.path("admin1").asText(""));
            item.put("country", n.path("country").asText("")); item.put("latitude", n.path("latitude").asDouble());
            item.put("longitude", n.path("longitude").asDouble()); item.put("timezone", n.path("timezone").asText("auto"));
            out.add(item);
        }
        return out;
    }

    // ---- Caching: one upstream fetch per ~0.01° grid cell per 15 minutes, to stay inside Open-Meteo's free limits.
    private static final long FRESH_MS = 15 * 60_000L, STALE_OK_MS = 6 * 3_600_000L;
    private record Cached(Map<String, Object> data, long at) {}
    private final java.util.concurrent.ConcurrentHashMap<String, Cached> cache = new java.util.concurrent.ConcurrentHashMap<>();
    private final String apiKey = System.getenv().getOrDefault("OPEN_METEO_API_KEY", "").trim();
    private String host(String free, String paid) { return "https://" + (apiKey.isEmpty() ? free : paid); }
    private UriComponentsBuilder keyed(UriComponentsBuilder b) { return apiKey.isEmpty() ? b : b.queryParam("apikey", apiKey); }

    public Map<String, Object> getIntelligence(double latitude, double longitude, String place) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180)
            throw new IllegalArgumentException("Latitude must be -90..90 and longitude -180..180.");
        String key = String.format(java.util.Locale.ROOT, "%.2f,%.2f", latitude, longitude);
        long now = System.currentTimeMillis();
        Cached hit = cache.get(key);
        if (hit != null && now - hit.at() < FRESH_MS) return withPlace(hit.data(), place, false);
        try {
            Map<String, Object> fresh = fetchIntelligence(latitude, longitude, place);
            cache.put(key, new Cached(fresh, now));
            if (cache.size() > 500) cache.entrySet().removeIf(e -> now - e.getValue().at() > STALE_OK_MS);
            return fresh;
        } catch (RuntimeException e) {
            // Serve the last good (stale) reading rather than a blank dashboard, clearly marked as such.
            if (hit != null && now - hit.at() < STALE_OK_MS) return withPlace(hit.data(), place, true);
            throw e;
        }
    }

    private Map<String, Object> withPlace(Map<String, Object> data, String place, boolean stale) {
        Map<String, Object> copy = new LinkedHashMap<>(data);
        copy.put("place", place == null || place.isBlank() ? "Selected location" : place.trim());
        if (stale) copy.put("stale", true);
        return copy;
    }

    private Map<String, Object> fetchOpenMeteo(double latitude, double longitude, String place) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180)
            throw new IllegalArgumentException("Latitude must be -90..90 and longitude -180..180.");
        JsonNode weather = http.get().uri(keyed(UriComponentsBuilder.fromUriString(host("api.open-meteo.com","customer-api.open-meteo.com") + "/v1/forecast"))
                .queryParam("latitude", latitude).queryParam("longitude", longitude)
                .queryParam("current", "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,wind_speed_10m,wind_gusts_10m,uv_index,visibility,cloud_cover")
                .queryParam("hourly", "temperature_2m,relative_humidity_2m,precipitation_probability,precipitation,uv_index,wind_speed_10m,wind_gusts_10m,visibility,cloud_cover")
                .queryParam("forecast_days", 2).queryParam("timezone", "auto").build().encode().toUri())
                .retrieve().body(JsonNode.class);
        JsonNode air = http.get().uri(keyed(UriComponentsBuilder.fromUriString(host("air-quality-api.open-meteo.com","customer-air-quality-api.open-meteo.com") + "/v1/air-quality"))
                .queryParam("latitude", latitude).queryParam("longitude", longitude)
                .queryParam("current", "us_aqi,european_aqi,pm10,pm2_5,ozone,nitrogen_dioxide,sulphur_dioxide,carbon_monoxide,dust,grass_pollen,birch_pollen,ragweed_pollen")
                .queryParam("hourly", "us_aqi,pm2_5,pm10,ozone,nitrogen_dioxide,carbon_monoxide,dust,grass_pollen,birch_pollen,ragweed_pollen").queryParam("forecast_days", 2)
                .queryParam("timezone", "auto").build().encode().toUri()).retrieve().body(JsonNode.class);
        if (weather == null || air == null) throw new IllegalStateException("Environmental data provider returned an empty response.");

        JsonNode currentWeather = weather.path("current"), currentAir = air.path("current");
        double aqi = num(currentAir, "us_aqi", -1), pm25 = num(currentAir, "pm2_5", -1), pm10 = num(currentAir, "pm10", -1);
        double temp = num(currentWeather, "temperature_2m", -99), humidity = num(currentWeather, "relative_humidity_2m", -1);
        double feels = num(currentWeather, "apparent_temperature", temp), uv = num(currentWeather, "uv_index", -1);
        double precipitation = num(currentWeather, "precipitation", -1);
        int currentHourIndex = findHourIndex(weather.path("hourly").path("time"), currentWeather.path("time").asText());
        double rainChance = atNum(weather.path("hourly").path("precipitation_probability"), currentHourIndex);
        double wind = num(currentWeather, "wind_speed_10m", -1);
        double gust = num(currentWeather, "wind_gusts_10m", -1);
        double visibility = num(currentWeather, "visibility", -1);
        double dust = num(currentAir, "dust", -1);
        double co = num(currentAir, "carbon_monoxide", -1);
        List<Map<String, Object>> insights = buildInsights(aqi, pm25, temp, feels, humidity, uv, wind, gust, precipitation, rainChance, visibility, dust, co);
        Map<String, Object> current = new LinkedHashMap<>();
        current.put("time", currentWeather.path("time").asText(Instant.now().toString())); current.put("temperature", temp);
        current.put("feelsLike", feels); current.put("humidity", humidity); current.put("precipitation", num(currentWeather,"precipitation",-1));
        current.put("windSpeed", wind); current.put("windGusts", gust); current.put("uvIndex", uv);
        current.put("precipitationProbability", rainChance); current.put("visibility", visibility); current.put("cloudCover", num(currentWeather,"cloud_cover",-1));
        current.put("usAqi", aqi); current.put("europeanAqi", num(currentAir,"european_aqi",-1)); current.put("pm25",pm25);
        current.put("pm10",pm10); current.put("ozone",num(currentAir,"ozone",-1)); current.put("nitrogenDioxide",num(currentAir,"nitrogen_dioxide",-1));
        current.put("sulphurDioxide",num(currentAir,"sulphur_dioxide",-1));
        current.put("carbonMonoxide",co); current.put("dust",dust);
        current.put("grassPollen", num(currentAir, "grass_pollen", -1));
        current.put("birchPollen", num(currentAir, "birch_pollen", -1));
        current.put("ragweedPollen", num(currentAir, "ragweed_pollen", -1));

        List<Map<String,Object>> hourly = new ArrayList<>();
        JsonNode wt = weather.path("hourly").path("time"), at = air.path("hourly").path("time");
        JsonNode temps = weather.path("hourly").path("temperature_2m"), hums = weather.path("hourly").path("relative_humidity_2m");
        JsonNode uvh = weather.path("hourly").path("uv_index"), winds = weather.path("hourly").path("wind_speed_10m");
        JsonNode rainChanceH = weather.path("hourly").path("precipitation_probability"), rainH = weather.path("hourly").path("precipitation");
        JsonNode gustsH = weather.path("hourly").path("wind_gusts_10m"), visibilityH = weather.path("hourly").path("visibility");
        JsonNode aqis = air.path("hourly").path("us_aqi"), pmh = air.path("hourly").path("pm2_5");
        JsonNode dustH = air.path("hourly").path("dust"), grassH = air.path("hourly").path("grass_pollen");
        int count = Math.min(24, Math.min(wt.size(), at.size()));
        for (int i=0;i<count;i++) {
            Map<String,Object> h = new LinkedHashMap<>(); h.put("time", wt.get(i).asText()); h.put("temperature", atNum(temps,i));
            h.put("humidity",atNum(hums,i)); h.put("uvIndex",atNum(uvh,i)); h.put("windSpeed",atNum(winds,i));
            h.put("windGusts",atNum(gustsH,i)); h.put("precipitationProbability",atNum(rainChanceH,i));
            h.put("precipitation",atNum(rainH,i)); h.put("visibility",atNum(visibilityH,i));
            h.put("dust",atNum(dustH,i)); h.put("grassPollen",atNum(grassH,i));
            h.put("usAqi",atNum(aqis,i)); h.put("pm25",atNum(pmh,i)); hourly.add(h);
        }
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("place", place == null || place.isBlank() ? "Selected location" : place.trim());
        result.put("latitude", latitude); result.put("longitude", longitude); result.put("timezone", weather.path("timezone").asText("local"));
        result.put("updatedAt", Instant.now().toString()); result.put("provider", "Open-Meteo · weather models and CAMS air-quality forecasts");
        result.put("current", current); result.put("hourly", hourly); result.put("insights", insights);
        result.put("disclaimer", "Modelled conditions and forecasts are not readings from a nearby regulatory monitoring station. This is environmental information, not a medical diagnosis or an official public-health warning.");
        return result;
    }


    // ================= OpenWeatherMap (primary when OPENWEATHER_API_KEY is set) =================
    private final String owmKey = System.getenv().getOrDefault("OPENWEATHER_API_KEY", "").trim();
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EnvironmentalIntelligenceService.class);

    /** Calls OpenWeatherMap. Errors are sanitised so the API key (part of the URL) never reaches logs or responses. */
    private JsonNode owm(String path, Map<String, Object> params) {
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString("https://api.openweathermap.org" + path);
        for (Map.Entry<String, Object> e : params.entrySet()) b.queryParam(e.getKey(), e.getValue());
        b.queryParam("appid", owmKey);
        try {
            return http.get().uri(b.build().encode().toUri()).retrieve().body(JsonNode.class);
        } catch (org.springframework.web.client.RestClientResponseException e) {
            throw new IllegalStateException("OpenWeatherMap HTTP " + e.getStatusCode().value() + " on " + path);
        } catch (RuntimeException e) {
            throw new IllegalStateException("OpenWeatherMap request failed on " + path + ": " + e.getClass().getSimpleName());
        }
    }

    public List<Map<String, Object>> searchLocations(String query) {
        String q = query == null ? "" : query.trim();
        if (q.length() < 2 || q.length() > 100) return List.of();
        if (!owmKey.isEmpty()) {
            try {
                Map<String, Object> gp = new LinkedHashMap<>(); gp.put("q", q); gp.put("limit", 8);
                JsonNode arr = owm("/geo/1.0/direct", gp);
                List<Map<String, Object>> out = new ArrayList<>();
                if (arr != null && arr.isArray()) for (JsonNode n : arr) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", n.path("name").asText()); item.put("admin1", n.path("state").asText(""));
                    item.put("country", n.path("country").asText("")); item.put("latitude", n.path("lat").asDouble());
                    item.put("longitude", n.path("lon").asDouble()); item.put("timezone", "auto");
                    out.add(item);
                }
                return out;
            } catch (RuntimeException e) { log.warn("OpenWeatherMap geocoding failed, falling back to Open-Meteo: {}", e.getMessage()); }
        }
        return searchOpenMeteo(q);
    }

    private Map<String, Object> fetchIntelligence(double latitude, double longitude, String place) {
        if (!owmKey.isEmpty()) {
            try { return fetchOpenWeather(latitude, longitude, place); }
            catch (RuntimeException e) { log.warn("OpenWeatherMap failed, falling back to Open-Meteo: {}", e.getMessage()); }
        }
        return fetchOpenMeteo(latitude, longitude, place);
    }

    private record Pt(long t, double temp, double hum, double wind, double gust, double pop, double rain, double vis) {}
    private static double ms2kmh(JsonNode n) { return n.isNumber() ? n.asDouble() * 3.6 : -1; }
    private static double lerp(double a, double b, double f) { return a + (b - a) * f; }
    private static double lerpOrHold(double a, double b, double f) { return (a < 0 || b < 0) ? a : lerp(a, b, f); }

    /** US EPA AQI sub-index from a concentration using classic breakpoints. */
    private static double aqiSub(double c, double[][] bp) {
        if (c < 0) return -1;
        for (double[] r : bp) if (c <= r[1]) return r[2] + Math.max(0, c - r[0]) * (r[3] - r[2]) / (r[1] - r[0]);
        return 500;
    }
    private static final double[][] PM25_BP = {{0,12,0,50},{12.1,35.4,51,100},{35.5,55.4,101,150},{55.5,150.4,151,200},{150.5,250.4,201,300},{250.5,500.4,301,500}};
    private static final double[][] PM10_BP = {{0,54,0,50},{55,154,51,100},{155,254,101,150},{255,354,151,200},{355,424,201,300},{425,604,301,500}};
    private static double usAqi(double pm25, double pm10) {
        double v = Math.max(aqiSub(pm25, PM25_BP), aqiSub(pm10, PM10_BP));
        return v < 0 ? -1 : Math.round(v);
    }

    private Map<String, Object> fetchOpenWeather(double lat, double lon, String place) {
        Map<String, Object> geo = new LinkedHashMap<>(); geo.put("lat", lat); geo.put("lon", lon);
        Map<String, Object> metric = new LinkedHashMap<>(geo); metric.put("units", "metric");
        Map<String, Object> fcParams = new LinkedHashMap<>(metric); fcParams.put("cnt", 12);

        JsonNode cw = owm("/data/2.5/weather", metric);
        JsonNode air = owm("/data/2.5/air_pollution", geo);
        JsonNode fc = null, airFc = null;
        try { fc = owm("/data/2.5/forecast", fcParams); } catch (RuntimeException e) { log.warn("Forecast unavailable: {}", e.getMessage()); }
        try { airFc = owm("/data/2.5/air_pollution/forecast", geo); } catch (RuntimeException e) { log.warn("Air forecast unavailable: {}", e.getMessage()); }
        if (cw == null || air == null || !cw.has("main")) throw new IllegalStateException("OpenWeatherMap returned an empty response.");

        long nowSec = cw.path("dt").asLong(Instant.now().getEpochSecond());
        int offset = cw.path("timezone").asInt(0);
        JsonNode main = cw.path("main"), ac = air.path("list").path(0).path("components");
        double temp = num(main, "temp", -99), feels = num(main, "feels_like", temp), humidity = num(main, "humidity", -1);
        double wind = ms2kmh(cw.path("wind").path("speed")), gust = ms2kmh(cw.path("wind").path("gust"));
        double visibility = num(cw, "visibility", -1), precipitation = num(cw.path("rain"), "1h", 0);
        double pm25 = num(ac, "pm2_5", -1), pm10 = num(ac, "pm10", -1), co = num(ac, "co", -1);
        double aqi = usAqi(pm25, pm10);
        double uv = -1, dust = -1;

        List<Pt> anchors = new ArrayList<>();
        double firstPop = -1;
        if (fc != null && fc.path("list").isArray() && fc.path("list").size() > 0) firstPop = fc.path("list").get(0).path("pop").asDouble(-1) * 100;
        anchors.add(new Pt(nowSec, temp, humidity, wind, gust, firstPop, precipitation, visibility));
        if (fc != null && fc.path("list").isArray()) for (JsonNode e : fc.path("list")) {
            anchors.add(new Pt(e.path("dt").asLong(), num(e.path("main"), "temp", temp), num(e.path("main"), "humidity", -1),
                    ms2kmh(e.path("wind").path("speed")), ms2kmh(e.path("wind").path("gust")),
                    e.path("pop").isNumber() ? e.path("pop").asDouble() * 100 : -1,
                    num(e.path("rain"), "3h", 0) / 3.0, num(e, "visibility", -1)));
        }
        double rainChance = firstPop;

        Map<Long, JsonNode> airByHour = new HashMap<>();
        if (airFc != null && airFc.path("list").isArray()) for (JsonNode e : airFc.path("list")) airByHour.put(e.path("dt").asLong() / 3600, e.path("components"));
        airByHour.putIfAbsent(air.path("list").path(0).path("dt").asLong(nowSec) / 3600, ac);

        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        List<Map<String, Object>> hourly = new ArrayList<>();
        long base = (nowSec / 3600) * 3600;
        for (int i = 0; i < 24; i++) {
            long t = base + i * 3600L;
            int idx = 0; for (int k = 0; k < anchors.size(); k++) if (anchors.get(k).t() <= t) idx = k;
            Pt a = anchors.get(idx), b = idx + 1 < anchors.size() ? anchors.get(idx + 1) : null;
            double f = (b == null || t <= a.t()) ? 0 : Math.min(1.0, (double) (t - a.t()) / (b.t() - a.t()));
            Pt bb = b == null ? a : b;
            JsonNode hc = airByHour.get(t / 3600);
            double hp25 = hc == null ? -1 : num(hc, "pm2_5", -1), hp10 = hc == null ? -1 : num(hc, "pm10", -1);
            Map<String, Object> h = new LinkedHashMap<>();
            h.put("time", java.time.LocalDateTime.ofEpochSecond(t + offset, 0, java.time.ZoneOffset.UTC).format(fmt));
            h.put("temperature", lerp(a.temp(), bb.temp(), f)); h.put("humidity", lerpOrHold(a.hum(), bb.hum(), f));
            h.put("uvIndex", -1); h.put("windSpeed", lerpOrHold(a.wind(), bb.wind(), f)); h.put("windGusts", lerpOrHold(a.gust(), bb.gust(), f));
            h.put("precipitationProbability", a.pop()); h.put("precipitation", a.rain()); h.put("visibility", a.vis());
            h.put("dust", -1); h.put("grassPollen", -1); h.put("usAqi", usAqi(hp25, hp10)); h.put("pm25", hp25);
            hourly.add(h);
        }

        List<Map<String, Object>> insights = buildInsights(aqi, pm25, temp, feels, humidity, uv, wind, gust, precipitation, rainChance, visibility, dust, co);
        Map<String, Object> current = new LinkedHashMap<>();
        current.put("time", java.time.LocalDateTime.ofEpochSecond(nowSec + offset, 0, java.time.ZoneOffset.UTC).format(fmt));
        current.put("temperature", temp); current.put("feelsLike", feels); current.put("humidity", humidity); current.put("precipitation", precipitation);
        current.put("windSpeed", wind); current.put("windGusts", gust); current.put("uvIndex", uv);
        current.put("precipitationProbability", rainChance); current.put("visibility", visibility); current.put("cloudCover", num(cw.path("clouds"), "all", -1));
        current.put("usAqi", aqi); current.put("europeanAqi", -1); current.put("pm25", pm25); current.put("pm10", pm10);
        current.put("ozone", num(ac, "o3", -1)); current.put("nitrogenDioxide", num(ac, "no2", -1)); current.put("sulphurDioxide", num(ac, "so2", -1));
        current.put("carbonMonoxide", co); current.put("dust", dust);
        current.put("grassPollen", -1); current.put("birchPollen", -1); current.put("ragweedPollen", -1);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("place", place == null || place.isBlank() ? "Selected location" : place.trim());
        result.put("latitude", lat); result.put("longitude", lon);
        result.put("timezone", "UTC" + java.time.ZoneOffset.ofTotalSeconds(offset).getId().replace("Z", "+00:00"));
        result.put("updatedAt", Instant.now().toString());
        result.put("provider", "OpenWeatherMap · weather and air-pollution model estimates (US AQI derived from PM2.5/PM10)");
        result.put("current", current); result.put("hourly", hourly); result.put("insights", insights);
        result.put("disclaimer", "Modelled conditions and forecasts are not readings from a nearby regulatory monitoring station. UV, dust and pollen are unavailable from this provider. This is environmental information, not a medical diagnosis or an official public-health warning.");
        return result;
    }

    private List<Map<String,Object>> buildInsights(double aqi,double pm25,double temp,double feels,double humidity,double uv,double wind,double gust,double precipitation,double rainChance,double visibility,double dust,double co) {
        List<Map<String,Object>> list = new ArrayList<>();
        if (aqi >= 0 && aqi >= 151) add(list,"air","High priority","Air quality is very unhealthy by the US AQI scale.","Reduce prolonged outdoor exertion. Sensitive people should consider staying indoors and follow local official guidance.","high");
        else if (aqi >= 101) add(list,"air","Sensitive groups","Air quality may affect sensitive groups.","People with asthma or heart/lung conditions, children, and older adults should consider reducing prolonged outdoor exertion.","medium");
        else if (aqi >= 0) add(list,"air","Air quality","Current modelled AQI is in a lower US AQI band.","Keep checking local conditions; a model estimate may differ from a nearby station.","low");
        if (pm25 >= 35.5) add(list,"pm25","Fine particles elevated","PM2.5 is elevated in the current model estimate.","Reduce smoke and dust exposure where possible and keep windows closed when outdoor air is worse than indoor air.","high");
        if (feels >= 40 || temp >= 38) add(list,"heat","Heat precautions","High heat or apparent temperature increases heat strain risk.","Hydrate, take breaks in shade or air-conditioning, and avoid strenuous activity during the hottest hours.","high");
        else if (feels >= 33 || temp >= 35) add(list,"heat","Heat awareness","Warm conditions may increase heat stress during exertion.","Plan strenuous outdoor activity for cooler hours and drink water regularly.","medium");
        if (uv >= 6) add(list,"uv","UV protection","UV index is high enough to warrant protection.","Seek shade, wear protective clothing and sunglasses, and use broad-spectrum sunscreen.","medium");
        if (humidity >= 80 && temp >= 30) add(list,"humidity","Humid heat","Warm, humid conditions can make cooling harder.","Slow down during outdoor activity and watch for dizziness, weakness, or confusion.","medium");
        if (rainChance >= 70 || precipitation >= 10) add(list,"rain","Heavy-rain watch","Forecast precipitation suggests a wet-weather disruption window.","Check official weather alerts, avoid waterlogged underpasses, and do not cross flowing floodwater.","medium");
        if (gust >= 50 || wind >= 35) add(list,"wind","Strong-wind conditions","Forecast wind speeds or gusts are elevated.","Secure loose outdoor objects and use caution near trees, temporary structures, and exposed routes.","medium");
        if (visibility >= 0 && visibility < 1000) add(list,"visibility","Low visibility","Modelled visibility is below one kilometre.","Allow extra travel time and follow local transport advisories.","medium");
        if (dust >= 100) add(list,"dust","Elevated airborne dust","The model indicates elevated dust concentration.","Limit dust-generating activity and consider a well-fitted particulate respirator if exposure cannot be avoided.","medium");
        if (co >= 10000) add(list,"gas","Carbon monoxide elevated","Modelled carbon monoxide concentration is elevated.","Move away from combustion sources and consult local air-quality guidance; this model is not a detector for indoor CO emergencies.","high");
        if (list.isEmpty()) add(list,"check","Keep monitoring","No high-priority trigger was identified from the available values.","This does not mean there is no risk. Check local advisories and pay attention to how you feel.","low");
        return list;
    }
    private void add(List<Map<String,Object>> l,String type,String title,String summary,String action,String severity) {
        Map<String,Object> m = new LinkedHashMap<>(); m.put("type",type);m.put("title",title);m.put("summary",summary);m.put("action",action);m.put("severity",severity);l.add(m);
    }
    private int findHourIndex(JsonNode times, String currentTime) {
        if (times == null || !times.isArray() || currentTime == null || currentTime.length() < 13) return 0;
        String hour = currentTime.substring(0, 13);
        for (int i = 0; i < times.size(); i++) if (times.get(i).asText().startsWith(hour)) return i;
        return 0;
    }
    private double num(JsonNode n,String key,double fallback) { JsonNode v=n.path(key); return v.isNumber()?v.asDouble():fallback; }
    private double atNum(JsonNode n,int i) { return n != null && n.size()>i && n.get(i).isNumber()?n.get(i).asDouble():-1; }
}
