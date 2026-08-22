package com.tripfuel.app;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.Polyline;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Trailbound persistence/link layer.
 * Keeps hotel coordinates, route geometry, linked profile IDs, extension fields,
 * and map overlays persistent without clearing fuel-stop or nearby-place markers.
 */
public class TrailboundPersistentLinkActivity extends TrailboundStateFixActivity {
    private static final String PREFS = "trailbound_v5";
    private SharedPreferences prefs;
    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private boolean patching;
    private String lastTripMapSignature = "";
    private String lastHotelMapSignature = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (!patching && !isFinishing()) patchCurrentScreen();
        });
        getWindow().getDecorView().postDelayed(this::patchCurrentScreen, 300);
    }

    @Override protected void onResume() {
        super.onResume();
        getWindow().getDecorView().postDelayed(this::patchCurrentScreen, 300);
    }

    private void patchCurrentScreen() {
        if (patching || isFinishing()) return;
        patching = true;
        try {
            View root = getWindow().getDecorView();
            if (findExactText(root, "Hotel profile") != null) patchHotel(root);
            if (findExactText(root, "Trip profile") != null) patchTrip(root);
        } catch (Exception ignored) {
        } finally {
            patching = false;
        }
    }

    private void patchHotel(View root) {
        EditText address = exactFieldAfterLabel(root, "HOTEL ADDRESS");
        EditText name = exactFieldAfterLabel(root, "HOTEL NAME");
        EditText cost = exactFieldAfterLabel(root, "TOTAL HOTEL PRICE");
        Button find = findButton(root, "Find hotel from address");
        Button saveNew = findButton(root, "Save new hotel profile");
        Button saveUpdate = findButton(root, "Update hotel profile");
        Button save = saveUpdate != null ? saveUpdate : saveNew;
        MapView map = firstMap(root);

        JSONObject active = profileById("hotels", prefs.getString("activeHotelId", ""));
        String signature = active.optString("id", "") + "|" + active.optString("lat", "") + "|" + active.optString("lon", "");
        if (map != null && !active.optString("lat", "").isEmpty() && !active.optString("lon", "").isEmpty() && !signature.equals(lastHotelMapSignature)) {
            lastHotelMapSignature = signature;
            drawHotelMap(map, active);
        }

        if (find != null && !"persistent_find_hotel".equals(find.getTag())) {
            find.setTag("persistent_find_hotel");
            find.setOnClickListener(v -> findHotel(address, name, map));
        }
        if (save != null && !"persistent_save_hotel".equals(save.getTag())) {
            save.setTag("persistent_save_hotel");
            save.setOnClickListener(v -> saveHotel(address, name, cost, save, map));
        }
    }

    private void findHotel(EditText address, EditText name, MapView map) {
        String query = text(address).trim();
        if (query.isEmpty()) { toast("Enter the hotel address first"); return; }
        toast("Finding hotel…");
        io.execute(() -> {
            try {
                String u = "https://nominatim.openstreetmap.org/search?format=json&limit=5&addressdetails=1&namedetails=1&extratags=1&q=" + enc(query);
                JSONArray arr = new JSONArray(http(u));
                JSONObject best = null;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    String kind = (o.optString("type", "") + " " + o.optString("class", "")).toLowerCase(Locale.US);
                    if (kind.contains("hotel") || kind.contains("motel") || kind.contains("hostel") || kind.contains("tourism")) { best = o; break; }
                    if (best == null) best = o;
                }
                if (best == null) throw new Exception("not found");
                String foundName = "";
                JSONObject names = best.optJSONObject("namedetails");
                if (names != null) foundName = names.optString("name", "").trim();
                if (foundName.isEmpty()) foundName = best.optString("name", "").trim();
                String display = best.optString("display_name", query).trim();
                if (foundName.isEmpty()) foundName = display.contains(",") ? display.substring(0, display.indexOf(',')) : display;
                final String hotelName = foundName.isEmpty() ? "Hotel" : foundName;
                final String hotelAddress = removeLeadingName(display, hotelName);
                final String lat = best.optString("lat", "");
                final String lon = best.optString("lon", "");
                prefs.edit().putString("draftHotelLat", lat).putString("draftHotelLon", lon).apply();
                runOnUiThread(() -> {
                    if (name != null) name.setText(hotelName);
                    if (address != null) address.setText(hotelAddress);
                    if (map != null && !lat.isEmpty() && !lon.isEmpty()) {
                        JSONObject draft = new JSONObject();
                        try {
                            draft.put("lat", lat);
                            draft.put("lon", lon);
                            draft.put("label", hotelName);
                        } catch (Exception ignored) { }
                        lastHotelMapSignature = "draft|" + lat + "|" + lon;
                        drawHotelMap(map, draft);
                    }
                    toast("Hotel found. Add price, then save.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast("Could not identify that hotel automatically"));
            }
        });
    }

    private void saveHotel(EditText address, EditText name, EditText cost, Button button, MapView map) {
        try {
            String activeId = prefs.getString("activeHotelId", "");
            String id = activeId.isEmpty() ? UUID.randomUUID().toString() : activeId;
            JSONObject old = profileById("hotels", id);
            JSONObject p = old.length() == 0 ? new JSONObject() : new JSONObject(old.toString());
            String lat = prefs.getString("draftHotelLat", old.optString("lat", ""));
            String lon = prefs.getString("draftHotelLon", old.optString("lon", ""));
            p.put("id", id);
            p.put("name", text(name).trim());
            p.put("address", text(address).trim());
            p.put("cost", text(cost).trim());
            p.put("lat", lat);
            p.put("lon", lon);
            p.put("label", text(name).trim().isEmpty() ? text(address).trim() : text(name).trim());
            upsert("hotels", p);
            prefs.edit().putString("activeHotelId", id).remove("draftHotelLat").remove("draftHotelLon").commit();
            if (button != null) button.setText("Hotel saved ✓");
            if (map != null) {
                lastHotelMapSignature = id + "|" + lat + "|" + lon;
                drawHotelMap(map, p);
            }
            toast("Hotel and map saved");
        } catch (Exception e) {
            toast("Could not save hotel. Existing data is still safe.");
        }
    }

    private void patchTrip(View root) {
        EditText from = exactFieldAfterLabel(root, "FROM");
        EditText to = exactFieldAfterLabel(root, "TO");
        Spinner car = exactSpinnerAfterLabel(root, "CAR FOR THIS TRIP");
        Spinner hotel = exactSpinnerAfterLabel(root, "HOTEL FOR THIS TRIP");
        CheckBox includeHotel = findCheckBoxContaining(root, "Include linked hotel");
        EditText snacks = exactFieldAfterLabel(root, "SNACKS & DRINKS");
        EditText extras = exactFieldAfterLabel(root, "OTHER TRIP MONEY");
        EditText gas = exactFieldAfterLabel(root, "PREPARED GAS $ / GAL");
        EditText tank = exactFieldAfterLabel(root, "Tank size (gal)");
        EditText depart = exactFieldAfterLabel(root, "Departure gas (gal)");
        Button mapButton = findButton(root, "Map actual round trip");
        MapView map = firstMap(root);

        JSONObject active = profileById("trips", prefs.getString("activeTripId", ""));
        String routeSignature = active.optString("id", "") + "|" + active.optString("routeOut", "").hashCode() + "|" + active.optString("routeBack", "").hashCode();
        if (map != null && !active.optString("routeOut", "").isEmpty() && !routeSignature.equals(lastTripMapSignature)) {
            lastTripMapSignature = routeSignature;
            drawTripMap(map, active);
        }

        TextView header = findExactText(root, "Trip profile");
        if (header != null && header.getParent() instanceof LinearLayout) {
            LinearLayout card = (LinearLayout) header.getParent();
            if (card.findViewWithTag("use_hotel_destination") == null) {
                Button useHotel = new Button(this);
                useHotel.setTag("use_hotel_destination");
                useHotel.setText("Use linked hotel as destination");
                useHotel.setAllCaps(false);
                useHotel.setTextColor(0xFFFFFFFF);
                useHotel.setBackgroundColor(0xFF6F4F30);
                int insert = Math.min(6, card.getChildCount());
                card.addView(useHotel, insert, new LinearLayout.LayoutParams(-1, dp(52)));
                useHotel.setOnClickListener(v -> {
                    String hotelId = selectedProfileId("hotels", hotel == null ? 0 : hotel.getSelectedItemPosition());
                    if (hotelId.isEmpty()) hotelId = active.optString("hotelId", prefs.getString("activeHotelId", ""));
                    JSONObject hp = profileById("hotels", hotelId);
                    String addr = hp.optString("address", "").trim();
                    if (addr.isEmpty()) { toast("Choose a saved hotel with an address first"); return; }
                    String originalStart = text(from).trim();
                    if (to != null) to.setText(addr);
                    persistTripField("hotelId", hotelId);
                    persistTripField("end", addr);
                    if (!originalStart.isEmpty()) persistTripField("start", originalStart);
                    invalidateStoredRouteIfEndpointsChanged(originalStart, addr);
                    toast("Hotel set as TO destination");
                });
            }
        }

        if (mapButton != null && !"persistent_route".equals(mapButton.getTag())) {
            mapButton.setTag("persistent_route");
            mapButton.setOnClickListener(v -> mapAndPersistTrip(from, to, car, hotel, includeHotel, snacks, extras, gas, tank, depart, map));
        }
    }

    private void mapAndPersistTrip(EditText from, EditText to, Spinner car, Spinner hotel, CheckBox includeHotel,
                                   EditText snacks, EditText extras, EditText gas, EditText tank, EditText depart, MapView map) {
        String s = text(from).trim();
        String e = text(to).trim();
        if (s.isEmpty() || e.isEmpty()) { toast("Enter start and destination first"); return; }
        String existingId = prefs.getString("activeTripId", "");
        String id = existingId.isEmpty() ? UUID.randomUUID().toString() : existingId;
        toast("Mapping and saving round trip…");
        io.execute(() -> {
            try {
                double[] a = geocode(s);
                double[] b = geocode(e);
                JSONObject out = route(a, b).getJSONArray("routes").getJSONObject(0);
                JSONObject back = route(b, a).getJSONArray("routes").getJSONObject(0);
                JSONObject old = profileById("trips", id);
                JSONObject p = old.length() == 0 ? new JSONObject() : new JSONObject(old.toString());

                String selectedVehicle = selectedProfileId("vehicles", car == null ? 0 : car.getSelectedItemPosition());
                String selectedHotel = selectedProfileId("hotels", hotel == null ? 0 : hotel.getSelectedItemPosition());
                if (selectedVehicle.isEmpty()) selectedVehicle = old.optString("vehicleId", prefs.getString("activeVehicleId", ""));
                if (selectedHotel.isEmpty()) selectedHotel = old.optString("hotelId", prefs.getString("activeHotelId", ""));

                p.put("id", id);
                p.put("start", s);
                p.put("end", e);
                p.put("snacks", text(snacks));
                p.put("extras", text(extras));
                p.put("gas", text(gas));
                p.put("tankSize", text(tank));
                p.put("departureFuel", text(depart));
                p.put("includeHotel", includeHotel == null || includeHotel.isChecked());
                p.put("vehicleId", selectedVehicle);
                p.put("hotelId", selectedHotel);
                p.put("routeOut", out.getJSONObject("geometry").getJSONArray("coordinates").toString());
                p.put("routeBack", back.getJSONObject("geometry").getJSONArray("coordinates").toString());
                p.put("startLat", String.valueOf(a[0]));
                p.put("startLon", String.valueOf(a[1]));
                p.put("endLat", String.valueOf(b[0]));
                p.put("endLon", String.valueOf(b[1]));
                p.put("outMiles", String.format(Locale.US, "%.1f", out.getDouble("distance") / 1609.344));
                p.put("backMiles", String.format(Locale.US, "%.1f", back.getDouble("distance") / 1609.344));
                p.put("outHours", String.format(Locale.US, "%.2f", out.getDouble("duration") / 3600.0));
                p.put("backHours", String.format(Locale.US, "%.2f", back.getDouble("duration") / 3600.0));
                p.put("label", e + " trip");
                upsert("trips", p);
                prefs.edit().putString("activeTripId", id)
                        .putString("fuelTankSize", text(tank))
                        .putString("departureFuel", text(depart)).commit();
                runOnUiThread(() -> {
                    if (map != null) {
                        lastTripMapSignature = id + "|" + p.optString("routeOut", "").hashCode() + "|" + p.optString("routeBack", "").hashCode();
                        drawTripMap(map, p);
                    }
                    toast("Round-trip map saved to this trip");
                    getWindow().getDecorView().postDelayed(this::patchCurrentScreen, 200);
                });
            } catch (Exception ex) {
                runOnUiThread(() -> toast("Route lookup failed. Check addresses and connection."));
            }
        });
    }

    private void invalidateStoredRouteIfEndpointsChanged(String start, String end) {
        String id = prefs.getString("activeTripId", "");
        if (id.isEmpty()) return;
        JSONObject trip = profileById("trips", id);
        if (trip.optString("id", "").isEmpty()) return;
        if (start.equals(trip.optString("start", "")) && end.equals(trip.optString("end", ""))) return;
        try {
            trip.put("start", start);
            trip.put("end", end);
            clearRouteFields(trip);
            upsert("trips", trip);
            lastTripMapSignature = "";
        } catch (Exception ignored) { }
    }

    private void clearRouteFields(JSONObject trip) throws Exception {
        for (String key : new String[]{"routeOut", "routeBack", "startLat", "startLon", "endLat", "endLon", "outMiles", "backMiles", "outHours", "backHours"}) {
            trip.put(key, "");
        }
    }

    private void drawHotelMap(MapView map, JSONObject hotel) {
        try {
            double lat = signed(hotel.optString("lat", "0"));
            double lon = signed(hotel.optString("lon", "0"));
            if (lat == 0 && lon == 0) return;
            ArrayList<Overlay> remove = new ArrayList<>();
            for (Overlay overlay : map.getOverlays()) {
                if (overlay instanceof Marker) {
                    Object related = ((Marker) overlay).getRelatedObject();
                    if (related == null) remove.add(overlay);
                }
            }
            map.getOverlays().removeAll(remove);
            Marker m = new Marker(map);
            m.setPosition(new GeoPoint(lat, lon));
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            m.setTitle(hotel.optString("label", "Hotel"));
            m.setSnippet(hotel.optString("address", "Saved hotel"));
            map.getOverlays().add(m);
            map.getController().setZoom(15.0);
            map.getController().setCenter(new GeoPoint(lat, lon));
            map.invalidate();
        } catch (Exception ignored) { }
    }

    private void drawTripMap(MapView map, JSONObject trip) {
        try {
            ArrayList<Overlay> remove = new ArrayList<>();
            for (Overlay overlay : map.getOverlays()) {
                if (overlay instanceof Polyline) {
                    remove.add(overlay);
                } else if (overlay instanceof Marker) {
                    String title = ((Marker) overlay).getTitle();
                    if ("Start / Return".equals(title) || "Destination".equals(title)) remove.add(overlay);
                }
            }
            map.getOverlays().removeAll(remove);

            ArrayList<GeoPoint> all = new ArrayList<>();
            addLine(map, trip.optString("routeOut", ""), 0xFFE2B05A, 8f, all);
            addLine(map, trip.optString("routeBack", ""), 0xFF7EBE69, 5f, all);
            double sl = signed(trip.optString("startLat", "0"));
            double so = signed(trip.optString("startLon", "0"));
            double el = signed(trip.optString("endLat", "0"));
            double eo = signed(trip.optString("endLon", "0"));
            if (sl != 0 || so != 0) addMarker(map, new GeoPoint(sl, so), "Start / Return", all);
            if (el != 0 || eo != 0) addMarker(map, new GeoPoint(el, eo), "Destination", all);
            if (all.size() > 1) map.zoomToBoundingBox(BoundingBox.fromGeoPoints(all), true, dp(35));
            map.invalidate();
        } catch (Exception ignored) { }
    }

    private void addLine(MapView map, String json, int color, float width, List<GeoPoint> all) throws Exception {
        if (json == null || json.isEmpty()) return;
        JSONArray a = new JSONArray(json);
        ArrayList<GeoPoint> pts = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONArray p = a.getJSONArray(i);
            GeoPoint g = new GeoPoint(p.getDouble(1), p.getDouble(0));
            pts.add(g);
            all.add(g);
        }
        Polyline line = new Polyline();
        line.setPoints(pts);
        line.getOutlinePaint().setColor(color);
        line.getOutlinePaint().setStrokeWidth(width);
        map.getOverlays().add(line);
    }

    private void addMarker(MapView map, GeoPoint p, String title, List<GeoPoint> all) {
        Marker m = new Marker(map);
        m.setPosition(p);
        m.setTitle(title);
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        map.getOverlays().add(m);
        all.add(p);
    }

    private void persistTripField(String key, Object value) {
        String id = prefs.getString("activeTripId", "");
        if (id.isEmpty()) return;
        try {
            JSONArray input = profiles("trips");
            JSONArray output = new JSONArray();
            for (int i = 0; i < input.length(); i++) {
                JSONObject o = input.optJSONObject(i);
                if (o == null) continue;
                if (id.equals(o.optString("id", ""))) o.put(key, value);
                output.put(o);
            }
            prefs.edit().putString("trips", output.toString()).commit();
        } catch (Exception ignored) { }
    }

    private JSONObject route(double[] a, double[] b) throws Exception {
        return new JSONObject(http("https://router.project-osrm.org/route/v1/driving/" + a[1] + "," + a[0] + ";" + b[1] + "," + b[0] + "?overview=full&geometries=geojson"));
    }

    private double[] geocode(String q) throws Exception {
        JSONArray a = new JSONArray(http("https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" + enc(q)));
        if (a.length() == 0) throw new Exception("not found");
        JSONObject o = a.getJSONObject(0);
        return new double[]{o.getDouble("lat"), o.getDouble("lon")};
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

    private void upsert(String key, JSONObject profile) throws Exception {
        JSONArray a = profiles(key);
        JSONArray out = new JSONArray();
        boolean replaced = false;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            if (profile.optString("id", "").equals(o.optString("id", ""))) {
                out.put(profile);
                replaced = true;
            } else {
                out.put(o);
            }
        }
        if (!replaced) out.put(profile);
        if (!prefs.edit().putString(key, out.toString()).commit()) throw new Exception("storage");
    }

    private String selectedProfileId(String key, int index) {
        if (index <= 0) return "";
        JSONArray a = profiles(key);
        JSONObject o = a.optJSONObject(index - 1);
        return o == null ? "" : o.optString("id", "");
    }

    private EditText exactFieldAfterLabel(View root, String label) {
        TextView t = findExactText(root, label);
        if (t == null || !(t.getParent() instanceof ViewGroup)) return null;
        ViewGroup p = (ViewGroup) t.getParent();
        int start = p.indexOfChild(t) + 1;
        for (int i = start; i < p.getChildCount(); i++) {
            View child = p.getChildAt(i);
            if (child instanceof EditText) return (EditText) child;
            if (child instanceof TextView && !(child instanceof EditText)) break;
        }
        return null;
    }

    private Spinner exactSpinnerAfterLabel(View root, String label) {
        TextView t = findExactText(root, label);
        if (t == null || !(t.getParent() instanceof ViewGroup)) return null;
        ViewGroup p = (ViewGroup) t.getParent();
        int start = p.indexOfChild(t) + 1;
        for (int i = start; i < p.getChildCount(); i++) {
            View child = p.getChildAt(i);
            if (child instanceof Spinner) return (Spinner) child;
            if (child instanceof TextView) break;
        }
        return null;
    }

    private CheckBox findCheckBoxContaining(View root, String text) {
        ArrayList<CheckBox> list = new ArrayList<>();
        collect(root, CheckBox.class, list);
        for (CheckBox c : list) if (c.getText() != null && c.getText().toString().contains(text)) return c;
        return null;
    }

    private Button findButton(View root, String text) {
        ArrayList<Button> list = new ArrayList<>();
        collect(root, Button.class, list);
        for (Button b : list) if (b.getText() != null && text.equalsIgnoreCase(b.getText().toString().trim())) return b;
        return null;
    }

    private TextView findExactText(View root, String target) {
        ArrayList<TextView> list = new ArrayList<>();
        collect(root, TextView.class, list);
        for (TextView t : list) if (t.getText() != null && target.equalsIgnoreCase(t.getText().toString().trim())) return t;
        return null;
    }

    private MapView firstMap(View root) {
        ArrayList<MapView> maps = new ArrayList<>();
        collect(root, MapView.class, maps);
        return maps.isEmpty() ? null : maps.get(0);
    }

    private <T extends View> void collect(View v, Class<T> cls, List<T> out) {
        if (cls.isInstance(v)) out.add(cls.cast(v));
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), cls, out);
        }
    }

    private String removeLeadingName(String display, String name) {
        String d = display == null ? "" : display.trim();
        String n = name == null ? "" : name.trim();
        if (!n.isEmpty() && d.toLowerCase(Locale.US).startsWith(n.toLowerCase(Locale.US) + ",")) {
            return d.substring(n.length() + 1).trim();
        }
        return d;
    }

    private String text(EditText e) {
        return e == null || e.getText() == null ? "" : e.getText().toString();
    }

    private double signed(String s) {
        try { return Double.parseDouble(s == null ? "" : s.trim()); }
        catch (Exception e) { return 0; }
    }

    private String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private String http(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(22000);
        c.setRequestProperty("User-Agent", "TrailboundAndroid/6.0");
        c.setRequestProperty("Accept", "application/json,*/*");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
            StringBuilder s = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) s.append(line);
            return s.toString();
        } finally { c.disconnect(); }
    }

    @Override protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }
}
