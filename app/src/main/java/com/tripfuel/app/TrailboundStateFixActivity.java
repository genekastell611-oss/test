package com.tripfuel.app;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Trailbound 5.4: keeps active-trip values persistent and makes nearby hotel searches real. */
public class TrailboundStateFixActivity extends TrailboundGasStopMapActivity {
    private static final String PREFS = "trailbound_v5";
    private SharedPreferences prefs;
    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private boolean patching;
    private boolean restoring;
    private String patchedTripId = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (!patching && !isFinishing()) patchVisibleScreen();
        });
        getWindow().getDecorView().postDelayed(this::patchVisibleScreen, 250);
    }

    @Override protected void onResume() {
        super.onResume();
        getWindow().getDecorView().postDelayed(this::patchVisibleScreen, 250);
    }

    private void patchVisibleScreen() {
        if (patching || isFinishing()) return;
        patching = true;
        try {
            View root = getWindow().getDecorView();
            if (findExactText(root, "Trip profile") != null) patchTripState(root);
            if (findExactText(root, "Hotel profile") != null) patchNearbyButtons(root);
        } catch (Exception ignored) {
        } finally {
            patching = false;
        }
    }

    private void patchTripState(View root) {
        String tripId = prefs.getString("activeTripId", "");
        if (tripId.isEmpty()) return;
        JSONObject trip = profileById("trips", tripId);
        if (trip.optString("id", "").isEmpty()) return;

        EditText snacks = fieldAfterLabel(root, "SNACKS & DRINKS");
        EditText extras = fieldAfterLabel(root, "OTHER TRIP MONEY");
        EditText gas = fieldAfterLabel(root, "PREPARED GAS $ / GAL");
        EditText tank = fieldAfterLabel(root, "Tank size (gal)");
        EditText departure = fieldAfterLabel(root, "Departure gas (gal)");
        CheckBox hotelToggle = findCheckBoxContaining(root, "Include linked hotel");
        Spinner carSpinner = spinnerAfterLabel(root, "CAR FOR THIS TRIP");
        Spinner hotelSpinner = spinnerAfterLabel(root, "HOTEL FOR THIS TRIP");
        Button update = findButton(root, "Update estimate");

        restoring = true;
        try {
            restoreIfPresent(snacks, trip, "snacks");
            restoreIfPresent(extras, trip, "extras");
            restoreIfPresent(gas, trip, "gas");
            restoreIfPresent(tank, trip, "tankSize");
            restoreIfPresent(departure, trip, "departureFuel");
            if (hotelToggle != null && trip.has("includeHotel")) hotelToggle.setChecked(trip.optBoolean("includeHotel", true));
        } finally {
            restoring = false;
        }

        if (!tripId.equals(patchedTripId)) {
            patchedTripId = tripId;
            attachTripWatcher(snacks, "snacks", update);
            attachTripWatcher(extras, "extras", update);
            attachTripWatcher(gas, "gas", update);
            attachTripWatcher(tank, "tankSize", update);
            attachTripWatcher(departure, "departureFuel", update);
            if (hotelToggle != null && !"statefix".equals(hotelToggle.getTag())) {
                hotelToggle.setTag("statefix");
                hotelToggle.setOnCheckedChangeListener((b, checked) -> {
                    if (restoring) return;
                    persistActiveTripValue("includeHotel", checked);
                    if (update != null) update.post(update::performClick);
                });
            }
            if (carSpinner != null && !"statefix".equals(carSpinner.getTag())) {
                carSpinner.setTag("statefix");
                carSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                        if (restoring) return;
                        persistActiveTripValue("vehicleId", selectedProfileId("vehicles", position));
                        if (update != null) update.post(update::performClick);
                    }
                    @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
                });
            }
            if (hotelSpinner != null && !"statefix".equals(hotelSpinner.getTag())) {
                hotelSpinner.setTag("statefix");
                hotelSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                        if (restoring) return;
                        persistActiveTripValue("hotelId", selectedProfileId("hotels", position));
                        if (update != null) update.post(update::performClick);
                    }
                    @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
                });
            }
        }

        // Keep the fuel planner's fallback preferences synchronized with this trip.
        SharedPreferences.Editor e = prefs.edit();
        if (tank != null) e.putString("fuelTankSize", safeText(tank));
        if (departure != null) e.putString("departureFuel", safeText(departure));
        e.apply();
    }

    private void attachTripWatcher(EditText field, String key, Button update) {
        if (field == null || "statefix_".concat(key).equals(field.getTag())) return;
        field.setTag("statefix_" + key);
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (restoring) return;
                persistActiveTripValue(key, s == null ? "" : s.toString());
                if ("tankSize".equals(key)) prefs.edit().putString("fuelTankSize", s == null ? "" : s.toString()).apply();
                if ("departureFuel".equals(key)) prefs.edit().putString("departureFuel", s == null ? "" : s.toString()).apply();
                if (update != null) update.removeCallbacks(update::performClick);
                if (update != null) update.postDelayed(update::performClick, 120);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void restoreIfPresent(EditText field, JSONObject trip, String key) {
        if (field == null || !trip.has(key)) return;
        String saved = trip.optString(key, "");
        if (!saved.equals(field.getText() == null ? "" : field.getText().toString())) field.setText(saved);
    }

    private synchronized void persistActiveTripValue(String key, Object value) {
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

    private void patchNearbyButtons(View root) {
        for (String category : new String[]{"Restaurants", "Coffee", "Groceries", "Parks", "Attractions", "Fuel", "Pharmacy"}) {
            Button b = findButton(root, category);
            if (b != null && !"nearby_live".equals(b.getTag())) {
                b.setTag("nearby_live");
                final String cat = category;
                b.setOnClickListener(v -> searchNearby(cat));
            }
        }
    }

    private void searchNearby(String category) {
        String hotelId = activeHotelIdForCurrentTrip();
        JSONObject hotel = profileById("hotels", hotelId);
        double lat = positive(hotel.optString("lat", "0"));
        double lon = signed(hotel.optString("lon", "0"));
        if (lat == 0 && lon == 0) {
            Toast.makeText(this, "Find and save the hotel address first.", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Finding nearby " + category.toLowerCase(Locale.US) + "…", Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            try {
                String filter = overpassFilter(category);
                String q = "[out:json][timeout:20];(" +
                        "node(around:8000," + lat + "," + lon + ")" + filter + ";" +
                        "way(around:8000," + lat + "," + lon + ")" + filter + ";" +
                        "relation(around:8000," + lat + "," + lon + ")" + filter + ";);out center tags 20;";
                JSONObject result = new JSONObject(http("https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(q, "UTF-8")));
                JSONArray elements = result.optJSONArray("elements");
                ArrayList<Place> places = new ArrayList<>();
                if (elements != null) {
                    for (int i = 0; i < elements.length() && places.size() < 15; i++) {
                        JSONObject el = elements.optJSONObject(i);
                        if (el == null) continue;
                        JSONObject tags = el.optJSONObject("tags");
                        String name = tags == null ? "" : tags.optString("name", "");
                        if (name.isEmpty()) continue;
                        double plat = el.optDouble("lat", Double.NaN), plon = el.optDouble("lon", Double.NaN);
                        JSONObject center = el.optJSONObject("center");
                        if ((Double.isNaN(plat) || Double.isNaN(plon)) && center != null) {
                            plat = center.optDouble("lat", Double.NaN); plon = center.optDouble("lon", Double.NaN);
                        }
                        if (Double.isNaN(plat) || Double.isNaN(plon)) continue;
                        places.add(new Place(name, plat, plon));
                    }
                }
                runOnUiThread(() -> showNearbyPlaces(category, hotel, places));
            } catch (Exception ex) {
                runOnUiThread(() -> Toast.makeText(this, "Nearby search is unavailable right now. Your hotel and trip are still saved.", Toast.LENGTH_LONG).show());
            }
        });
    }

    private void showNearbyPlaces(String category, JSONObject hotel, List<Place> places) {
        MapView map = firstMap(getWindow().getDecorView());
        if (map == null) return;
        clearNearbyMarkers(map);
        double lat = positive(hotel.optString("lat", "0"));
        double lon = signed(hotel.optString("lon", "0"));
        Marker hotelMarker = new Marker(map);
        hotelMarker.setPosition(new GeoPoint(lat, lon));
        hotelMarker.setTitle(hotel.optString("label", "Hotel"));
        hotelMarker.setSnippet("Saved hotel");
        hotelMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        hotelMarker.setRelatedObject("nearby_hotel");
        map.getOverlays().add(hotelMarker);
        for (Place p : places) {
            Marker m = new Marker(map);
            m.setPosition(new GeoPoint(p.lat, p.lon));
            m.setTitle(p.name);
            m.setSnippet(category + " near your hotel");
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            m.setRelatedObject("nearby_result");
            map.getOverlays().add(m);
        }
        map.getController().setCenter(new GeoPoint(lat, lon));
        map.getController().setZoom(13.0);
        map.invalidate();
        Toast.makeText(this, places.isEmpty() ? "No named results found nearby." : "Found " + places.size() + " nearby places on the map.", Toast.LENGTH_SHORT).show();
    }

    private String overpassFilter(String category) {
        switch (category) {
            case "Restaurants": return "[\"amenity\"~\"restaurant|fast_food\"]";
            case "Coffee": return "[\"amenity\"=\"cafe\"]";
            case "Groceries": return "[\"shop\"~\"supermarket|convenience|grocery\"]";
            case "Parks": return "[\"leisure\"~\"park|nature_reserve\"]";
            case "Attractions": return "[\"tourism\"~\"attraction|museum|viewpoint|theme_park|zoo\"]";
            case "Fuel": return "[\"amenity\"=\"fuel\"]";
            case "Pharmacy": return "[\"amenity\"=\"pharmacy\"]";
            default: return "[\"name\"]";
        }
    }

    private String activeHotelIdForCurrentTrip() {
        JSONObject trip = profileById("trips", prefs.getString("activeTripId", ""));
        String id = trip.optString("hotelId", "");
        return id.isEmpty() ? prefs.getString("activeHotelId", "") : id;
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

    private String selectedProfileId(String key, int spinnerIndex) {
        if (spinnerIndex <= 0) return "";
        JSONArray a = profiles(key);
        JSONObject o = a.optJSONObject(spinnerIndex - 1);
        return o == null ? "" : o.optString("id", "");
    }

    private EditText fieldAfterLabel(View root, String label) {
        TextView t = findExactText(root, label);
        if (t == null || !(t.getParent() instanceof ViewGroup)) return null;
        ViewGroup p = (ViewGroup) t.getParent();
        for (int i = 0; i < p.getChildCount(); i++) if (p.getChildAt(i) instanceof EditText) return (EditText) p.getChildAt(i);
        return null;
    }

    private Spinner spinnerAfterLabel(View root, String label) {
        TextView t = findExactText(root, label);
        if (t == null || !(t.getParent() instanceof ViewGroup)) return null;
        ViewGroup p = (ViewGroup) t.getParent();
        for (int i = 0; i < p.getChildCount(); i++) if (p.getChildAt(i) instanceof Spinner) return (Spinner) p.getChildAt(i);
        return null;
    }

    private CheckBox findCheckBoxContaining(View root, String text) {
        ArrayList<CheckBox> list = new ArrayList<>(); collect(root, CheckBox.class, list);
        for (CheckBox c : list) if (c.getText() != null && c.getText().toString().contains(text)) return c;
        return null;
    }

    private Button findButton(View root, String text) {
        ArrayList<Button> list = new ArrayList<>(); collect(root, Button.class, list);
        for (Button b : list) if (b.getText() != null && text.equalsIgnoreCase(b.getText().toString().trim())) return b;
        return null;
    }

    private TextView findExactText(View root, String target) {
        ArrayList<TextView> list = new ArrayList<>(); collect(root, TextView.class, list);
        for (TextView t : list) if (t.getText() != null && target.equalsIgnoreCase(t.getText().toString().trim())) return t;
        return null;
    }

    private MapView firstMap(View root) {
        ArrayList<MapView> list = new ArrayList<>(); collect(root, MapView.class, list);
        return list.isEmpty() ? null : list.get(0);
    }

    private <T extends View> void collect(View v, Class<T> cls, List<T> out) {
        if (cls.isInstance(v)) out.add(cls.cast(v));
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), cls, out);
        }
    }

    private void clearNearbyMarkers(MapView map) {
        ArrayList<Overlay> remove = new ArrayList<>();
        for (Overlay o : map.getOverlays()) {
            if (o instanceof Marker) {
                Object related = ((Marker) o).getRelatedObject();
                if ("nearby_result".equals(related) || "nearby_hotel".equals(related)) remove.add(o);
            }
        }
        map.getOverlays().removeAll(remove);
    }

    private String http(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(12000); c.setReadTimeout(22000);
        c.setRequestProperty("User-Agent", "TrailboundAndroid/5.4");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
            StringBuilder s = new StringBuilder(); String line;
            while ((line = br.readLine()) != null) s.append(line);
            return s.toString();
        } finally { c.disconnect(); }
    }

    private double positive(String s) {
        try { return Math.max(0, Double.parseDouble(s == null ? "" : s.trim())); }
        catch (Exception e) { return 0; }
    }

    private double signed(String s) {
        try { return Double.parseDouble(s == null ? "" : s.trim()); }
        catch (Exception e) { return 0; }
    }

    private String safeText(EditText e) { return e == null || e.getText() == null ? "" : e.getText().toString(); }

    @Override protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private static class Place {
        final String name; final double lat; final double lon;
        Place(String name, double lat, double lon) { this.name = name; this.lat = lat; this.lon = lon; }
    }
}
