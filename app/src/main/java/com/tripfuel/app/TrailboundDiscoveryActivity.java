package com.tripfuel.app;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Trailbound 6.1 discovery layer.
 * Adds real, persistent discovery along the mapped route and around the linked hotel.
 */
public class TrailboundDiscoveryActivity extends TrailboundVehicleHubActivity {
    private static final String PREFS = "trailbound_v5";
    private SharedPreferences prefs;
    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private boolean patching;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (!patching && !isFinishing()) patchDiscovery();
        });
        getWindow().getDecorView().postDelayed(this::patchDiscovery, 550);
    }

    @Override protected void onResume() {
        super.onResume();
        getWindow().getDecorView().postDelayed(this::patchDiscovery, 400);
    }

    private void patchDiscovery() {
        if (patching || isFinishing()) return;
        patching = true;
        try {
            View root = getWindow().getDecorView();
            if (findExactText(root, "Trip profile") != null) patchTripDiscovery(root);
            if (findExactText(root, "Hotel profile") != null) patchHotelDiscovery(root);
        } catch (Exception ignored) {
        } finally {
            patching = false;
        }
    }

    private void patchTripDiscovery(View root) {
        TextView header = findExactText(root, "Trip profile");
        if (header == null || !(header.getParent() instanceof LinearLayout)) return;
        LinearLayout card = (LinearLayout) header.getParent();
        LinearLayout section = (LinearLayout) card.findViewWithTag("route_discovery_section");
        if (section == null) {
            section = panel("route_discovery_section");
            TextView title = title("EXPLORE ALONG THE ROUTE");
            section.addView(title);
            section.addView(note("Map and save the trip first. Trailbound searches a corridor along the actual outbound route and keeps the results with this trip."));
            section.addView(buttonRow(routeButton("Scenic & viewpoints", "Scenic"), routeButton("Food & coffee", "Food")));
            section.addView(buttonRow(routeButton("Parks & landmarks", "Parks"), routeButton("Rest areas", "Rest")));
            section.addView(buttonRow(routeButton("Supplies & pharmacy", "Supplies"), routeButton("Useful towns", "Towns")));
            section.addView(routeButton("Fuel near planned fill-up points", "FuelStops"), topMargin(-1, 52, 8));
            TextView results = resultBox("Map the trip, then choose a category. Results will be plotted on the route map and saved with the trip.");
            results.setTag("route_discovery_results");
            section.addView(results, topMargin(-1, -2, 10));
            card.addView(section, topMargin(-1, -2, 14));
        }
        JSONObject trip = activeTrip();
        restorePlaces(firstMap(root), trip.optString("routePlaces", "[]"), "route_discovery", findTaggedText(root, "route_discovery_results"), true);
    }

    private Button routeButton(String label, String category) {
        Button b = action(label);
        b.setOnClickListener(v -> searchAlongRoute(category));
        return b;
    }

    private void searchAlongRoute(String category) {
        JSONObject trip = activeTrip();
        if (trip.optString("id", "").isEmpty() || trip.optString("routeOut", "").isEmpty()) {
            toast("Map the trip first so Trailbound knows the actual route.");
            return;
        }
        TextView results = findTaggedText(getWindow().getDecorView(), "route_discovery_results");
        if (results != null) results.setText("Searching the mapped route for " + routeLabel(category).toLowerCase(Locale.US) + "…");
        io.execute(() -> {
            try {
                ArrayList<Anchor> anchors = "FuelStops".equals(category) ? fuelStopAnchors(trip) : sampledRouteAnchors(trip);
                if (anchors.isEmpty()) throw new Exception("no anchors");
                JSONArray found = queryPlaces(anchors, routeFilters(category), "FuelStops".equals(category) ? 12000 : 10000, trip, true);
                persistProfileField("trips", trip.optString("id", ""), "routePlaces", found.toString());
                persistProfileField("trips", trip.optString("id", ""), "routePlacesCategory", routeLabel(category));
                runOnUiThread(() -> {
                    MapView map = firstMap(getWindow().getDecorView());
                    TextView box = findTaggedText(getWindow().getDecorView(), "route_discovery_results");
                    restorePlaces(map, found.toString(), "route_discovery", box, true);
                    toast(found.length() == 0 ? "No named stops found in this route corridor." : "Found " + found.length() + " stops along the route.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    TextView box = findTaggedText(getWindow().getDecorView(), "route_discovery_results");
                    if (box != null) box.setText("Route discovery is unavailable right now. Your saved route and trip data are unchanged.");
                });
            }
        });
    }

    private void patchHotelDiscovery(View root) {
        for (String category : new String[]{"Restaurants", "Coffee", "Groceries", "Parks", "Attractions", "Fuel", "Pharmacy"}) {
            Button b = findButton(root, category);
            if (b != null) {
                b.setTag("nearby_live");
                b.setOnClickListener(v -> searchAroundHotel(category));
            }
        }

        Button pharmacy = findButton(root, "Pharmacy");
        LinearLayout nearbyCard = pharmacy != null && pharmacy.getParent() instanceof LinearLayout ? (LinearLayout) pharmacy.getParent() : null;
        if (nearbyCard != null && nearbyCard.findViewWithTag("hotel_discovery_extra") == null) {
            LinearLayout extras = panel("hotel_discovery_extra");
            extras.setPadding(0, dp(8), 0, 0);
            extras.setBackgroundColor(Color.TRANSPARENT);
            extras.addView(buttonRow(hotelButton("Shopping", "Shopping"), hotelButton("Medical", "Medical")));
            extras.addView(hotelButton("Entertainment & recreation", "Entertainment"), topMargin(-1, 52, 8));
            TextView results = resultBox("Choose a nearby category. Results stay tied to this hotel profile and reappear when you reopen it.");
            results.setTag("hotel_discovery_results");
            extras.addView(results, topMargin(-1, -2, 10));
            nearbyCard.addView(extras);
        }

        JSONObject hotel = activeHotel();
        restorePlaces(firstMap(root), hotel.optString("nearbyPlaces", "[]"), "hotel_discovery", findTaggedText(root, "hotel_discovery_results"), false);
    }

    private Button hotelButton(String label, String category) {
        Button b = action(label);
        b.setTag("nearby_live");
        b.setOnClickListener(v -> searchAroundHotel(category));
        return b;
    }

    private void searchAroundHotel(String category) {
        JSONObject hotel = activeHotel();
        double lat = number(hotel.optString("lat", "0"));
        double lon = number(hotel.optString("lon", "0"));
        if (lat == 0 && lon == 0) {
            toast("Find and save the hotel address first.");
            return;
        }
        TextView results = findTaggedText(getWindow().getDecorView(), "hotel_discovery_results");
        if (results != null) results.setText("Searching around " + hotel.optString("label", "your hotel") + " for " + category.toLowerCase(Locale.US) + "…");
        io.execute(() -> {
            try {
                ArrayList<Anchor> anchors = new ArrayList<>();
                anchors.add(new Anchor(lat, lon, 0, "Hotel"));
                JSONArray found = queryPlaces(anchors, hotelFilters(category), 8000, null, false);
                persistProfileField("hotels", hotel.optString("id", ""), "nearbyPlaces", found.toString());
                persistProfileField("hotels", hotel.optString("id", ""), "nearbyPlacesCategory", category);
                runOnUiThread(() -> {
                    MapView map = firstMap(getWindow().getDecorView());
                    TextView box = findTaggedText(getWindow().getDecorView(), "hotel_discovery_results");
                    restorePlaces(map, found.toString(), "hotel_discovery", box, false);
                    toast(found.length() == 0 ? "No named results found nearby." : "Found " + found.length() + " places near the hotel.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    TextView box = findTaggedText(getWindow().getDecorView(), "hotel_discovery_results");
                    if (box != null) box.setText("Nearby search is unavailable right now. Your saved hotel data is unchanged.");
                });
            }
        });
    }

    private JSONArray queryPlaces(List<Anchor> anchors, List<String> filters, int radiusMeters, JSONObject trip, boolean alongRoute) throws Exception {
        StringBuilder q = new StringBuilder("[out:json][timeout:25];(");
        for (Anchor a : anchors) {
            for (String filter : filters) {
                q.append("node(around:").append(radiusMeters).append(',').append(a.lat).append(',').append(a.lon).append(')').append(filter).append(';');
                q.append("way(around:").append(radiusMeters).append(',').append(a.lat).append(',').append(a.lon).append(')').append(filter).append(';');
                q.append("relation(around:").append(radiusMeters).append(',').append(a.lat).append(',').append(a.lon).append(')').append(filter).append(';');
            }
        }
        q.append(");out center tags 120;");
        JSONObject json = new JSONObject(http("https://overpass-api.de/api/interpreter?data=" + enc(q.toString())));
        JSONArray elements = json.optJSONArray("elements");
        ArrayList<Place> places = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (elements != null) {
            for (int i = 0; i < elements.length(); i++) {
                JSONObject el = elements.optJSONObject(i);
                if (el == null) continue;
                JSONObject tags = el.optJSONObject("tags");
                double lat = el.optDouble("lat", Double.NaN), lon = el.optDouble("lon", Double.NaN);
                JSONObject center = el.optJSONObject("center");
                if ((Double.isNaN(lat) || Double.isNaN(lon)) && center != null) {
                    lat = center.optDouble("lat", Double.NaN);
                    lon = center.optDouble("lon", Double.NaN);
                }
                if (Double.isNaN(lat) || Double.isNaN(lon)) continue;
                String name = placeName(tags);
                if (name.isEmpty()) continue;
                String key = name.toLowerCase(Locale.US) + "|" + Math.round(lat * 10000) + "|" + Math.round(lon * 10000);
                if (!seen.add(key)) continue;
                double mile = alongRoute && trip != null ? nearestTripMile(trip, lat, lon) : nearestAnchorMiles(anchors, lat, lon);
                places.add(new Place(name, lat, lon, mile));
            }
        }
        Collections.sort(places, Comparator.comparingDouble(p -> p.mile));
        JSONArray out = new JSONArray();
        for (int i = 0; i < places.size() && i < 24; i++) {
            Place p = places.get(i);
            JSONObject o = new JSONObject();
            o.put("name", p.name); o.put("lat", p.lat); o.put("lon", p.lon); o.put("mile", p.mile);
            out.put(o);
        }
        return out;
    }

    private String placeName(JSONObject tags) {
        if (tags == null) return "";
        String name = tags.optString("name", "").trim();
        if (name.isEmpty()) name = tags.optString("brand", "").trim();
        if (name.isEmpty()) name = tags.optString("operator", "").trim();
        if (name.isEmpty() && "rest_area".equals(tags.optString("highway", ""))) name = "Rest area";
        if (name.isEmpty() && "fuel".equals(tags.optString("amenity", ""))) name = "Fuel station";
        return name;
    }

    private List<String> routeFilters(String category) {
        ArrayList<String> f = new ArrayList<>();
        switch (category) {
            case "Scenic": f.add("[\"tourism\"~\"viewpoint|attraction\"]"); f.add("[\"natural\"~\"peak|waterfall\"]"); f.add("[\"historic\"]"); break;
            case "Food": f.add("[\"amenity\"~\"restaurant|fast_food|cafe\"]"); break;
            case "Parks": f.add("[\"leisure\"~\"park|nature_reserve\"]"); f.add("[\"tourism\"~\"museum|zoo|theme_park|attraction\"]"); break;
            case "Rest": f.add("[\"highway\"=\"rest_area\"]"); f.add("[\"amenity\"=\"toilets\"]"); break;
            case "Supplies": f.add("[\"shop\"~\"supermarket|convenience|department_store\"]"); f.add("[\"amenity\"=\"pharmacy\"]"); break;
            case "Towns": f.add("[\"place\"~\"city|town\"]"); break;
            case "FuelStops": f.add("[\"amenity\"=\"fuel\"]"); break;
            default: f.add("[\"tourism\"=\"attraction\"]");
        }
        return f;
    }

    private List<String> hotelFilters(String category) {
        ArrayList<String> f = new ArrayList<>();
        switch (category) {
            case "Restaurants": f.add("[\"amenity\"~\"restaurant|fast_food\"]"); break;
            case "Coffee": f.add("[\"amenity\"=\"cafe\"]"); break;
            case "Groceries": f.add("[\"shop\"~\"supermarket|convenience|grocery\"]"); break;
            case "Parks": f.add("[\"leisure\"~\"park|nature_reserve\"]"); break;
            case "Attractions": f.add("[\"tourism\"~\"attraction|museum|viewpoint|theme_park|zoo\"]"); f.add("[\"historic\"]"); break;
            case "Fuel": f.add("[\"amenity\"=\"fuel\"]"); break;
            case "Pharmacy": f.add("[\"amenity\"=\"pharmacy\"]"); break;
            case "Shopping": f.add("[\"shop\"~\"mall|department_store|supermarket|clothes|general\"]"); break;
            case "Medical": f.add("[\"amenity\"~\"hospital|clinic|doctors\"]"); break;
            case "Entertainment": f.add("[\"amenity\"~\"cinema|theatre\"]"); f.add("[\"leisure\"~\"bowling_alley|sports_centre|water_park|amusement_arcade\"]"); break;
            default: f.add("[\"name\"]");
        }
        return f;
    }

    private String routeLabel(String category) {
        switch (category) {
            case "Scenic": return "Scenic stops & viewpoints";
            case "Food": return "Food & coffee";
            case "Parks": return "Parks & landmarks";
            case "Rest": return "Rest areas";
            case "Supplies": return "Supplies & pharmacy";
            case "Towns": return "Useful towns & detours";
            case "FuelStops": return "Fuel near planned fill-up points";
            default: return category;
        }
    }

    private ArrayList<Anchor> sampledRouteAnchors(JSONObject trip) {
        ArrayList<GeoPoint> points = geometry(trip.optString("routeOut", ""));
        ArrayList<Anchor> anchors = new ArrayList<>();
        if (points.isEmpty()) return anchors;
        double[] fractions = new double[]{0.10, 0.30, 0.50, 0.70, 0.90};
        for (double f : fractions) {
            int idx = Math.max(0, Math.min(points.size() - 1, (int)Math.round(f * (points.size() - 1))));
            GeoPoint p = points.get(idx);
            anchors.add(new Anchor(p.getLatitude(), p.getLongitude(), 0, "Route"));
        }
        return anchors;
    }

    private ArrayList<Anchor> fuelStopAnchors(JSONObject trip) {
        ArrayList<GeoPoint> route = geometry(trip.optString("routeOut", ""));
        ArrayList<GeoPoint> back = geometry(trip.optString("routeBack", ""));
        if (!back.isEmpty()) route.addAll(back);
        ArrayList<Anchor> anchors = new ArrayList<>();
        double outMiles = number(trip.optString("outMiles", "0"));
        double backMiles = number(trip.optString("backMiles", "0"));
        if (backMiles <= 0) backMiles = outMiles;
        double roundMiles = outMiles + backMiles;
        JSONObject vehicle = profileById("vehicles", trip.optString("vehicleId", prefs.getString("activeVehicleId", "")));
        double mpg = adjustedMpg(number(vehicle.optString("mpg", "0")), number(vehicle.optString("payload", "0")));
        double tank = number(trip.optString("tankSize", prefs.getString("fuelTankSize", "0")));
        double depart = Math.min(tank, number(trip.optString("departureFuel", prefs.getString("departureFuel", "0"))));
        if (route.size() < 2 || roundMiles <= 0 || mpg <= 0 || tank <= 0) return anchors;
        double reserve = Math.min(tank * 0.30, Math.max(0.5, tank * 0.15));
        double firstRange = Math.max(0, depart - reserve) * mpg;
        double fullRange = Math.max(0.1, tank - reserve) * mpg;
        double next = firstRange;
        int guard = 0;
        while (next < roundMiles && guard++ < 20) {
            double fraction = Math.max(0, Math.min(1, next / roundMiles));
            int idx = Math.max(0, Math.min(route.size() - 1, (int)Math.round(fraction * (route.size() - 1))));
            GeoPoint p = route.get(idx);
            anchors.add(new Anchor(p.getLatitude(), p.getLongitude(), next, next <= outMiles ? "Outbound" : "Return"));
            next += fullRange;
        }
        if (anchors.isEmpty()) return sampledRouteAnchors(trip);
        return anchors;
    }

    private double nearestTripMile(JSONObject trip, double lat, double lon) {
        ArrayList<GeoPoint> pts = geometry(trip.optString("routeOut", ""));
        double miles = number(trip.optString("outMiles", "0"));
        if (pts.isEmpty() || miles <= 0) return 0;
        int step = Math.max(1, pts.size() / 500), best = 0;
        double bestD = Double.MAX_VALUE;
        GeoPoint target = new GeoPoint(lat, lon);
        for (int i = 0; i < pts.size(); i += step) {
            double d = geoMiles(target, pts.get(i));
            if (d < bestD) { bestD = d; best = i; }
        }
        return miles * best / Math.max(1.0, pts.size() - 1.0);
    }

    private double nearestAnchorMiles(List<Anchor> anchors, double lat, double lon) {
        double best = Double.MAX_VALUE;
        GeoPoint p = new GeoPoint(lat, lon);
        for (Anchor a : anchors) best = Math.min(best, geoMiles(p, new GeoPoint(a.lat, a.lon)));
        return best == Double.MAX_VALUE ? 0 : best;
    }

    private ArrayList<GeoPoint> geometry(String json) {
        ArrayList<GeoPoint> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(json == null || json.isEmpty() ? "[]" : json);
            for (int i = 0; i < a.length(); i++) {
                JSONArray p = a.optJSONArray(i);
                if (p != null && p.length() >= 2) out.add(new GeoPoint(p.optDouble(1), p.optDouble(0)));
            }
        } catch (Exception ignored) { }
        return out;
    }

    private void restorePlaces(MapView map, String json, String markerKind, TextView results, boolean alongRoute) {
        if (map == null) return;
        clearMarkers(map, markerKind);
        try {
            JSONArray a = new JSONArray(json == null || json.isEmpty() ? "[]" : json);
            StringBuilder text = new StringBuilder();
            if (a.length() == 0) {
                if (results != null && results.getText().toString().trim().isEmpty()) results.setText("No saved discovery results yet.");
                map.invalidate();
                return;
            }
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                String name = o.optString("name", "Place");
                double lat = o.optDouble("lat", 0), lon = o.optDouble("lon", 0), mile = o.optDouble("mile", 0);
                Marker m = new Marker(map);
                m.setPosition(new GeoPoint(lat, lon));
                m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                m.setTitle(name);
                m.setSnippet(alongRoute ? "Along route around mile " + Math.round(mile) : String.format(Locale.US, "About %.1f mi from hotel", mile));
                m.setRelatedObject(markerKind);
                map.getOverlays().add(m);
                if (i < 12) {
                    if (text.length() > 0) text.append('\n');
                    text.append("• ").append(name).append(alongRoute ? " — around mile " + Math.round(mile) : String.format(Locale.US, " — %.1f mi away", mile));
                }
            }
            if (results != null) results.setText(a.length() + " saved result" + (a.length() == 1 ? "" : "s") + ":\n" + text);
            map.invalidate();
        } catch (Exception ignored) { }
    }

    private void clearMarkers(MapView map, String markerKind) {
        ArrayList<Overlay> remove = new ArrayList<>();
        for (Overlay overlay : map.getOverlays()) {
            if (overlay instanceof Marker && markerKind.equals(((Marker) overlay).getRelatedObject())) remove.add(overlay);
        }
        map.getOverlays().removeAll(remove);
    }

    private JSONObject activeTrip() {
        return profileById("trips", prefs.getString("activeTripId", ""));
    }

    private JSONObject activeHotel() {
        String id = prefs.getString("activeHotelId", "");
        if (id.isEmpty()) id = activeTrip().optString("hotelId", "");
        return profileById("hotels", id);
    }

    private JSONArray profiles(String key) {
        try { return new JSONArray(prefs.getString(key, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private JSONObject profileById(String key, String id) {
        if (id == null || id.isEmpty()) return new JSONObject();
        JSONArray a = profiles(key);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && id.equals(o.optString("id", ""))) return o;
        }
        return new JSONObject();
    }

    private synchronized void persistProfileField(String key, String id, String field, Object value) {
        if (id == null || id.isEmpty()) return;
        try {
            JSONArray a = profiles(key), out = new JSONArray();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                if (id.equals(o.optString("id", ""))) o.put(field, value);
                out.put(o);
            }
            prefs.edit().putString(key, out.toString()).commit();
        } catch (Exception ignored) { }
    }

    private LinearLayout panel(String tag) {
        LinearLayout p = new LinearLayout(this);
        p.setTag(tag);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(14), dp(14), dp(14), dp(14));
        p.setBackground(round(Color.rgb(24, 29, 20), 18, Color.rgb(117, 135, 86)));
        return p;
    }

    private TextView title(String s) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(Color.rgb(211, 236, 171)); t.setTextSize(16); t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private TextView note(String s) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(Color.rgb(232, 226, 211)); t.setTextSize(12);
        return t;
    }

    private TextView resultBox(String s) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(Color.WHITE); t.setTextSize(13); t.setPadding(dp(12), dp(12), dp(12), dp(12));
        t.setBackground(round(Color.rgb(10, 13, 9), 14, Color.rgb(99, 112, 75)));
        return t;
    }

    private Button action(String s) {
        Button b = new Button(this);
        b.setText(s); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setTextSize(13); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(round(Color.rgb(91, 72, 47), 14, Color.rgb(184, 151, 103)));
        return b;
    }

    private LinearLayout buttonRow(Button a, Button b) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1);
        lp.setMargins(dp(2), dp(8), dp(2), 0);
        row.addView(a, lp); row.addView(b, lp);
        return row;
    }

    private LinearLayout.LayoutParams topMargin(int w, int h, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.topMargin = dp(top);
        return lp;
    }

    private GradientDrawable round(int fill, int radius, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill); g.setCornerRadius(dp(radius)); g.setStroke(dp(1), stroke);
        return g;
    }

    private TextView findTaggedText(View root, String tag) {
        ArrayList<TextView> views = new ArrayList<>(); collect(root, TextView.class, views);
        for (TextView t : views) if (tag.equals(t.getTag())) return t;
        return null;
    }

    private Button findButton(View root, String target) {
        ArrayList<Button> buttons = new ArrayList<>(); collect(root, Button.class, buttons);
        for (Button b : buttons) if (b.getText() != null && target.equalsIgnoreCase(b.getText().toString().trim())) return b;
        return null;
    }

    private TextView findExactText(View root, String target) {
        ArrayList<TextView> views = new ArrayList<>(); collect(root, TextView.class, views);
        for (TextView t : views) if (t.getText() != null && target.equalsIgnoreCase(t.getText().toString().trim())) return t;
        return null;
    }

    private MapView firstMap(View root) {
        ArrayList<MapView> maps = new ArrayList<>(); collect(root, MapView.class, maps);
        return maps.isEmpty() ? null : maps.get(0);
    }

    private <T extends View> void collect(View v, Class<T> type, List<T> out) {
        if (type.isInstance(v)) out.add(type.cast(v));
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), type, out);
        }
    }

    private double adjustedMpg(double base, double pounds) {
        return base <= 0 ? 0 : base * (1.0 - Math.min(0.25, Math.max(0, pounds) / 100.0 * 0.01));
    }

    private double geoMiles(GeoPoint a, GeoPoint b) {
        double r = 3958.7613;
        double lat1 = Math.toRadians(a.getLatitude()), lat2 = Math.toRadians(b.getLatitude());
        double dLat = lat2 - lat1, dLon = Math.toRadians(b.getLongitude() - a.getLongitude());
        double h = Math.sin(dLat/2)*Math.sin(dLat/2) + Math.cos(lat1)*Math.cos(lat2)*Math.sin(dLon/2)*Math.sin(dLon/2);
        return 2*r*Math.asin(Math.min(1, Math.sqrt(h)));
    }

    private double number(String s) {
        try { return Double.parseDouble(s == null ? "" : s.trim()); }
        catch (Exception e) { return 0; }
    }

    private String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private String http(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(u).openConnection();
        c.setConnectTimeout(12000); c.setReadTimeout(26000);
        c.setRequestProperty("User-Agent", "TrailboundAndroid/6.1");
        c.setRequestProperty("Accept", "application/json,*/*");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
            StringBuilder s = new StringBuilder(); String line;
            while ((line = br.readLine()) != null) s.append(line);
            return s.toString();
        } finally { c.disconnect(); }
    }

    @Override protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private static class Anchor {
        final double lat, lon, mile; final String leg;
        Anchor(double lat, double lon, double mile, String leg) { this.lat = lat; this.lon = lon; this.mile = mile; this.leg = leg; }
    }

    private static class Place {
        final String name; final double lat, lon, mile;
        Place(String name, double lat, double lon, double mile) { this.name = name; this.lat = lat; this.lon = lon; this.mile = mile; }
    }
}
