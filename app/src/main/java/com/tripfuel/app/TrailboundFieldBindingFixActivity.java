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
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Trailbound 5.6 - fixes label-to-field binding for FROM/TO and hotel name/address. */
public class TrailboundFieldBindingFixActivity extends TrailboundPersistentLinkActivity {
    private static final String PREFS = "trailbound_v5";
    private SharedPreferences prefs;
    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private boolean patching;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (!patching && !isFinishing()) patchBindings();
        });
        getWindow().getDecorView().postDelayed(this::patchBindings, 350);
    }

    @Override protected void onResume() {
        super.onResume();
        getWindow().getDecorView().postDelayed(this::patchBindings, 350);
    }

    private void patchBindings() {
        if (patching || isFinishing()) return;
        patching = true;
        try {
            View root = getWindow().getDecorView();
            patchHotelDestination(root);
            patchHotelLookup(root);
        } catch (Exception ignored) {
        } finally {
            patching = false;
        }
    }

    private void patchHotelDestination(View root) {
        Button useHotel = findButton(root, "Use linked hotel as destination");
        if (useHotel == null || "binding_fix_destination".equals(useHotel.getTag())) return;
        useHotel.setTag("binding_fix_destination");
        useHotel.setOnClickListener(v -> {
            EditText from = exactFieldForLabel(root, "FROM");
            EditText to = exactFieldForLabel(root, "TO");
            Spinner hotelSpinner = exactSpinnerForLabel(root, "HOTEL FOR THIS TRIP");
            if (to == null) {
                toast("Destination field is unavailable. Reopen the Trip screen.");
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

            // Critical: only TO is allowed to change here.
            to.setText(address);
            if (from != null && !originalFrom.equals(text(from))) from.setText(originalFrom);
            persistTripField("hotelId", hotelId);
            persistTripField("end", address);
            if (!originalFrom.isEmpty()) persistTripField("start", originalFrom);
            toast("Hotel set as TO destination");
        });
    }

    private void patchHotelLookup(View root) {
        if (findExactText(root, "Hotel profile") == null) return;
        Button find = findButton(root, "Find hotel from address");
        Button save = findButton(root, "Update hotel profile");
        if (save == null) save = findButton(root, "Save new hotel profile");
        MapView map = firstMap(root);

        if (find != null && !"binding_fix_find_hotel".equals(find.getTag())) {
            find.setTag("binding_fix_find_hotel");
            find.setOnClickListener(v -> {
                EditText address = exactFieldForLabel(root, "HOTEL ADDRESS");
                EditText name = exactFieldForLabel(root, "HOTEL NAME");
                findHotel(address, name, map);
            });
        }
        if (save != null && !"binding_fix_save_hotel".equals(save.getTag())) {
            final Button saveButton = save;
            save.setTag("binding_fix_save_hotel");
            save.setOnClickListener(v -> {
                EditText address = exactFieldForLabel(root, "HOTEL ADDRESS");
                EditText name = exactFieldForLabel(root, "HOTEL NAME");
                EditText cost = exactFieldForLabel(root, "TOTAL HOTEL PRICE");
                saveHotel(address, name, cost, saveButton, map);
            });
        }
    }

    /** Returns the first EditText AFTER the exact label, never the first field in the whole card. */
    private EditText exactFieldForLabel(View root, String label) {
        TextView labelView = findExactText(root, label);
        if (labelView == null || !(labelView.getParent() instanceof ViewGroup)) return null;
        ViewGroup parent = (ViewGroup) labelView.getParent();
        int labelIndex = parent.indexOfChild(labelView);
        for (int i = labelIndex + 1; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof EditText) return (EditText) child;
            if (child instanceof TextView && !(child instanceof EditText)) break;
        }
        return null;
    }

    private Spinner exactSpinnerForLabel(View root, String label) {
        TextView labelView = findExactText(root, label);
        if (labelView == null || !(labelView.getParent() instanceof ViewGroup)) return null;
        ViewGroup parent = (ViewGroup) labelView.getParent();
        int labelIndex = parent.indexOfChild(labelView);
        for (int i = labelIndex + 1; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof Spinner) return (Spinner) child;
            if (child instanceof TextView) break;
        }
        return null;
    }

    private void findHotel(EditText addressField, EditText nameField, MapView map) {
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

                String foundName = "";
                JSONObject names = best.optJSONObject("namedetails");
                if (names != null) foundName = names.optString("name", "").trim();
                if (foundName.isEmpty()) foundName = best.optString("name", "").trim();
                String fullAddress = best.optString("display_name", query).trim();
                if (foundName.isEmpty()) {
                    JSONObject addr = best.optJSONObject("address");
                    if (addr != null) foundName = addr.optString("hotel", addr.optString("tourism", "")).trim();
                }
                if (foundName.isEmpty()) foundName = "Hotel";

                final String hotelName = foundName;
                final String hotelAddress = removeLeadingName(fullAddress, hotelName);
                final String lat = best.optString("lat", "");
                final String lon = best.optString("lon", "");
                prefs.edit().putString("draftHotelLat", lat).putString("draftHotelLon", lon).apply();

                runOnUiThread(() -> {
                    if (nameField != null) nameField.setText(hotelName);
                    if (addressField != null) addressField.setText(hotelAddress);
                    if (map != null && !lat.isEmpty() && !lon.isEmpty()) {
                        map.getOverlays().clear();
                        Marker marker = new Marker(map);
                        marker.setPosition(new GeoPoint(number(lat), number(lon)));
                        marker.setTitle(hotelName);
                        marker.setSnippet(hotelAddress);
                        map.getOverlays().add(marker);
                        map.getController().setZoom(15.0);
                        map.getController().setCenter(marker.getPosition());
                        map.invalidate();
                    }
                    toast("Hotel name and address separated");
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast("Could not identify that hotel automatically"));
            }
        });
    }

    private void saveHotel(EditText address, EditText name, EditText cost, Button button, MapView map) {
        try {
            String existingId = prefs.getString("activeHotelId", "");
            String id = existingId.isEmpty() ? UUID.randomUUID().toString() : existingId;
            JSONObject old = profileById("hotels", id);
            String lat = prefs.getString("draftHotelLat", old.optString("lat", ""));
            String lon = prefs.getString("draftHotelLon", old.optString("lon", ""));
            JSONObject hotel = new JSONObject();
            hotel.put("id", id);
            hotel.put("name", text(name).trim());
            hotel.put("address", text(address).trim());
            hotel.put("cost", text(cost).trim());
            hotel.put("lat", lat);
            hotel.put("lon", lon);
            hotel.put("label", text(name).trim().isEmpty() ? text(address).trim() : text(name).trim());
            upsert("hotels", hotel);
            prefs.edit().putString("activeHotelId", id).remove("draftHotelLat").remove("draftHotelLon").commit();
            if (button != null) button.setText("Hotel saved ✓");
            toast("Hotel profile saved");
        } catch (Exception e) {
            toast("Could not save hotel. Existing data is still safe.");
        }
    }

    private String removeLeadingName(String display, String hotelName) {
        if (display == null) return "";
        String d = display.trim();
        if (hotelName != null && !hotelName.trim().isEmpty()) {
            String n = hotelName.trim();
            if (d.toLowerCase(Locale.US).startsWith(n.toLowerCase(Locale.US) + ",")) {
                d = d.substring(n.length() + 1).trim();
            }
        }
        return d;
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

    private void upsert(String key, JSONObject profile) throws Exception {
        JSONArray a = profiles(key), out = new JSONArray();
        boolean replaced = false;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            if (profile.optString("id", "").equals(o.optString("id", ""))) { out.put(profile); replaced = true; }
            else out.put(o);
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

    private Button findButton(View root, String target) {
        ArrayList<Button> buttons = new ArrayList<>();
        collect(root, Button.class, buttons);
        for (Button b : buttons) if (b.getText() != null && target.equalsIgnoreCase(b.getText().toString().trim())) return b;
        return null;
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
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), cls, out);
        }
    }

    private String text(EditText e) { return e == null || e.getText() == null ? "" : e.getText().toString(); }
    private double number(String s) { try { return Double.parseDouble(s); } catch (Exception e) { return 0; } }
    private String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private String http(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(22000);
        c.setRequestProperty("User-Agent", "TrailboundAndroid/5.6");
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
