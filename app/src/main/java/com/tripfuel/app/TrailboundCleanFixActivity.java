package com.tripfuel.app;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

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

/** Trailbound 5.7 - stable single hotel-destination control and exact field binding. */
public class TrailboundCleanFixActivity extends TrailboundPersistentLinkActivity {
    private static final String PREFS = "trailbound_v5";
    private SharedPreferences prefs;
    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private boolean patching;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (!patching && !isFinishing()) patchScreen();
        });
        getWindow().getDecorView().postDelayed(this::patchScreen, 250);
    }

    @Override protected void onResume() {
        super.onResume();
        getWindow().getDecorView().postDelayed(this::patchScreen, 250);
    }

    private void patchScreen() {
        if (patching || isFinishing()) return;
        patching = true;
        try {
            View root = getWindow().getDecorView();
            if (findExactText(root, "Trip profile") != null) patchTrip(root);
            if (findExactText(root, "Hotel profile") != null) patchHotel(root);
        } catch (Exception ignored) {
        } finally {
            patching = false;
        }
    }

    private void patchTrip(View root) {
        ArrayList<Button> matches = findButtons(root, "Use linked hotel as destination");
        if (matches.isEmpty()) return;

        // Keep exactly one button. Do not alter the tag set by the parent activity;
        // that tag is what prevents the parent from creating another copy.
        Button keep = matches.get(0);
        for (int i = matches.size() - 1; i >= 1; i--) {
            Button duplicate = matches.get(i);
            if (duplicate.getParent() instanceof ViewGroup) {
                ((ViewGroup) duplicate.getParent()).removeView(duplicate);
            }
        }

        keep.setOnClickListener(v -> {
            EditText from = exactField(root, "FROM");
            EditText to = exactField(root, "TO");
            Spinner hotelSpinner = exactSpinner(root, "HOTEL FOR THIS TRIP");
            if (to == null) {
                toast("Could not find the destination field. Reopen Trip and try again.");
                return;
            }

            String originalFrom = text(from);
            String hotelId = selectedProfileId("hotels", hotelSpinner == null ? 0 : hotelSpinner.getSelectedItemPosition());
            JSONObject trip = profileById("trips", prefs.getString("activeTripId", ""));
            if (hotelId.isEmpty()) hotelId = trip.optString("hotelId", prefs.getString("activeHotelId", ""));
            JSONObject hotel = profileById("hotels", hotelId);
            String address = hotel.optString("address", "").trim();
            if (address.isEmpty()) {
                toast("Choose a saved hotel with an address first");
                return;
            }

            to.setText(address);
            // Safety guard: never allow this action to modify FROM.
            if (from != null && !originalFrom.equals(text(from))) from.setText(originalFrom);
            persistTripField("hotelId", hotelId);
            persistTripField("end", address);
            if (!originalFrom.isEmpty()) persistTripField("start", originalFrom);
            toast("Hotel set as TO destination");
        });
    }

    private void patchHotel(View root) {
        Button find = firstButton(root, "Find hotel from address");
        if (find == null) return;
        find.setOnClickListener(v -> {
            EditText address = exactField(root, "HOTEL ADDRESS");
            EditText name = exactField(root, "HOTEL NAME");
            MapView map = firstMap(root);
            lookupHotel(address, name, map);
        });
    }

    private void lookupHotel(EditText addressField, EditText nameField, MapView map) {
        String query = text(addressField).trim();
        if (query.isEmpty()) { toast("Enter the hotel address first"); return; }
        toast("Finding hotel…");
        io.execute(() -> {
            try {
                JSONArray results = new JSONArray(http("https://nominatim.openstreetmap.org/search?format=json&limit=5&addressdetails=1&namedetails=1&extratags=1&q=" + enc(query)));
                JSONObject best = null;
                for (int i = 0; i < results.length(); i++) {
                    JSONObject item = results.optJSONObject(i);
                    if (item == null) continue;
                    String kind = (item.optString("type", "") + " " + item.optString("class", "")).toLowerCase(Locale.US);
                    if (kind.contains("hotel") || kind.contains("motel") || kind.contains("hostel") || kind.contains("tourism")) { best = item; break; }
                    if (best == null) best = item;
                }
                if (best == null) throw new Exception("not found");

                String hotelName = "";
                JSONObject names = best.optJSONObject("namedetails");
                if (names != null) hotelName = names.optString("name", "").trim();
                if (hotelName.isEmpty()) hotelName = best.optString("name", "").trim();
                JSONObject addr = best.optJSONObject("address");
                if (hotelName.isEmpty() && addr != null) hotelName = addr.optString("hotel", addr.optString("tourism", "")).trim();
                if (hotelName.isEmpty()) hotelName = "Hotel";

                String fullAddress = best.optString("display_name", query).trim();
                String cleanAddress = removeLeadingName(fullAddress, hotelName);
                final String finalName = hotelName;
                final String finalAddress = cleanAddress;
                final String lat = best.optString("lat", "");
                final String lon = best.optString("lon", "");
                prefs.edit().putString("draftHotelLat", lat).putString("draftHotelLon", lon).apply();

                runOnUiThread(() -> {
                    if (nameField != null) nameField.setText(finalName);
                    if (addressField != null) addressField.setText(finalAddress);
                    if (map != null && !lat.isEmpty() && !lon.isEmpty()) {
                        map.getOverlays().clear();
                        Marker marker = new Marker(map);
                        marker.setPosition(new GeoPoint(number(lat), number(lon)));
                        marker.setTitle(finalName);
                        marker.setSnippet(finalAddress);
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                        map.getOverlays().add(marker);
                        map.getController().setCenter(marker.getPosition());
                        map.getController().setZoom(15.0);
                        map.invalidate();
                    }
                    toast("Hotel name and address separated");
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast("Could not identify that hotel automatically"));
            }
        });
    }

    private EditText exactField(View root, String label) {
        TextView labelView = findExactText(root, label);
        if (labelView == null || !(labelView.getParent() instanceof ViewGroup)) return null;
        ViewGroup parent = (ViewGroup) labelView.getParent();
        int index = parent.indexOfChild(labelView);
        for (int i = index + 1; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof EditText) return (EditText) child;
            if (child instanceof TextView) break;
        }
        return null;
    }

    private Spinner exactSpinner(View root, String label) {
        TextView labelView = findExactText(root, label);
        if (labelView == null || !(labelView.getParent() instanceof ViewGroup)) return null;
        ViewGroup parent = (ViewGroup) labelView.getParent();
        int index = parent.indexOfChild(labelView);
        for (int i = index + 1; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof Spinner) return (Spinner) child;
            if (child instanceof TextView) break;
        }
        return null;
    }

    private void persistTripField(String key, Object value) {
        String id = prefs.getString("activeTripId", "");
        if (id.isEmpty()) return;
        try {
            JSONArray input = profiles("trips"), output = new JSONArray();
            for (int i = 0; i < input.length(); i++) {
                JSONObject o = input.optJSONObject(i);
                if (o == null) continue;
                if (id.equals(o.optString("id", ""))) o.put(key, value);
                output.put(o);
            }
            prefs.edit().putString("trips", output.toString()).commit();
        } catch (Exception ignored) { }
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

    private String selectedProfileId(String key, int index) {
        if (index <= 0) return "";
        JSONArray a = profiles(key);
        JSONObject o = a.optJSONObject(index - 1);
        return o == null ? "" : o.optString("id", "");
    }

    private ArrayList<Button> findButtons(View root, String target) {
        ArrayList<Button> all = new ArrayList<>();
        collect(root, Button.class, all);
        ArrayList<Button> matches = new ArrayList<>();
        for (Button b : all) if (b.getText() != null && target.equalsIgnoreCase(b.getText().toString().trim())) matches.add(b);
        return matches;
    }

    private Button firstButton(View root, String target) {
        ArrayList<Button> matches = findButtons(root, target);
        return matches.isEmpty() ? null : matches.get(0);
    }

    private TextView findExactText(View root, String target) {
        ArrayList<TextView> views = new ArrayList<>();
        collect(root, TextView.class, views);
        for (TextView t : views) if (t.getText() != null && target.equalsIgnoreCase(t.getText().toString().trim())) return t;
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
            ViewGroup group = (ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), cls, out);
        }
    }

    private String removeLeadingName(String display, String name) {
        String d = display == null ? "" : display.trim();
        String n = name == null ? "" : name.trim();
        if (!n.isEmpty() && d.toLowerCase(Locale.US).startsWith(n.toLowerCase(Locale.US) + ",")) {
            d = d.substring(n.length() + 1).trim();
        }
        return d;
    }

    private String text(EditText field) { return field == null || field.getText() == null ? "" : field.getText().toString(); }
    private double number(String s) { try { return Double.parseDouble(s); } catch (Exception e) { return 0; } }
    private String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private String http(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(22000);
        c.setRequestProperty("User-Agent", "TrailboundAndroid/5.7");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) out.append(line);
            return out.toString();
        } finally { c.disconnect(); }
    }

    @Override protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }
}
