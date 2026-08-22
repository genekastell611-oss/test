package com.tripfuel.app;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TrailboundFlowActivity extends TrailboundActivity {
    private SharedPreferences flowPrefs;
    private final ExecutorService flowIo = Executors.newSingleThreadExecutor();
    private boolean patching;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        flowPrefs = getSharedPreferences("trailbound", MODE_PRIVATE);
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (!patching) patchCurrentScreen();
        });
        getWindow().getDecorView().post(this::patchCurrentScreen);
    }

    @Override protected void onResume() {
        super.onResume();
        getWindow().getDecorView().postDelayed(this::patchCurrentScreen, 120);
    }

    private void patchCurrentScreen() {
        if (patching) return;
        patching = true;
        try {
            View root = getWindow().getDecorView();
            patchTripScreen(root);
            patchHotelScreen(root);
        } finally {
            patching = false;
        }
    }

    private void patchTripScreen(View root) {
        Button save = findButton(root, "Save current vacation");
        if (save != null && !"trailbound_safe_save".equals(save.getTag())) {
            save.setTag("trailbound_safe_save");
            save.setOnClickListener(v -> safeSaveVacation(save));
        }

        Button clear = findButton(root, "Clear current planner");
        if (clear != null && !"trailbound_clear_preserve_hotel".equals(clear.getTag())) {
            clear.setTag("trailbound_clear_preserve_hotel");
            clear.setOnClickListener(v -> {
                clearCurrentPlannerSafely();
                Toast.makeText(this, "Current trip cleared. Saved hotel and car kept.", Toast.LENGTH_SHORT).show();
                recreate();
            });
        }

        TextView tripBudget = findText(root, "Trip budget");
        if (tripBudget != null) {
            ViewGroup parent = (ViewGroup) tripBudget.getParent();
            if (parent != null && parent.findViewWithTag("hotel_trip_toggle") == null) {
                TextView hint = new TextView(this);
                hint.setTag("hotel_trip_hint");
                hint.setText("Hotel: set it up on the Hotel tab first. Then choose whether to include it in this trip total.");
                hint.setTextColor(0xFFF0E8D7);
                hint.setTextSize(13);
                hint.setPadding(0, 8, 0, 8);

                CheckBox include = new CheckBox(this);
                include.setTag("hotel_trip_toggle");
                include.setText("Include saved hotel in trip total");
                include.setTextColor(0xFFFFFFFF);
                include.setTextSize(16);
                include.setChecked(flowPrefs.getBoolean("includeHotel", true));
                include.setOnCheckedChangeListener((button, checked) -> flowPrefs.edit().putBoolean("includeHotel", checked).apply());

                int index = parent.indexOfChild(tripBudget);
                parent.addView(hint, Math.min(index + 1, parent.getChildCount()));
                parent.addView(include, Math.min(index + 2, parent.getChildCount()));
            }
        }
    }

    private void patchHotelScreen(View root) {
        TextView hotelHeader = findText(root, "Hotel base");
        if (hotelHeader == null) return;
        ViewGroup card = (ViewGroup) hotelHeader.getParent();
        if (card == null || card.findViewWithTag("hotel_flow_lookup") != null) return;

        List<EditText> fields = editTexts(card);
        if (fields.size() < 3) return;

        EditText hotelName = fields.get(0);
        EditText hotelAddress = fields.get(1);
        EditText hotelPrice = fields.get(2);
        hotelAddress.setHint("Enter hotel street address first");
        hotelName.setHint("Hotel name auto-fills when found");
        hotelPrice.setHint("Enter total hotel price");

        TextView helper = new TextView(this);
        helper.setTag("hotel_flow_helper");
        helper.setText("1. Enter address  •  2. Find hotel  •  3. Enter price");
        helper.setTextColor(0xFFF0E8D7);
        helper.setTextSize(13);
        helper.setPadding(0, 8, 0, 8);
        card.addView(helper, Math.min(1, card.getChildCount()));

        Button lookup = new Button(this);
        lookup.setTag("hotel_flow_lookup");
        lookup.setText("Find hotel from address");
        lookup.setAllCaps(false);
        lookup.setTextColor(0xFFFFFFFF);
        lookup.setBackgroundColor(0xFF5B7640);
        lookup.setOnClickListener(v -> lookupHotel(hotelAddress, hotelName));
        int addressIndex = card.indexOfChild(hotelAddress);
        card.addView(lookup, Math.min(addressIndex + 1, card.getChildCount()), new LinearLayout.LayoutParams(-1, dp(52)));

        for (CheckBox cb : checkBoxes(card)) {
            if (textOf(cb).toLowerCase(Locale.US).contains("include hotel")) cb.setVisibility(View.GONE);
        }
    }

    private void lookupHotel(EditText address, EditText name) {
        String q = address.getText().toString().trim();
        if (q.isEmpty()) {
            Toast.makeText(this, "Enter the hotel address first.", Toast.LENGTH_SHORT).show();
            return;
        }
        name.setText("Looking up hotel…");
        flowIo.execute(() -> {
            try {
                String url = "https://nominatim.openstreetmap.org/search?format=json&limit=5&addressdetails=1&namedetails=1&extratags=1&q=" + URLEncoder.encode(q, "UTF-8");
                JSONArray arr = new JSONArray(httpGet(url));
                JSONObject best = null;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    String type = (o.optString("type", "") + " " + o.optString("class", "")).toLowerCase(Locale.US);
                    if (type.contains("hotel") || type.contains("motel") || type.contains("hostel") || type.contains("tourism")) { best = o; break; }
                    if (best == null) best = o;
                }
                if (best == null) throw new Exception("No result");
                String found = "";
                JSONObject names = best.optJSONObject("namedetails");
                if (names != null) found = names.optString("name", "");
                if (found.isEmpty()) found = best.optString("name", "");
                if (found.isEmpty()) {
                    String display = best.optString("display_name", "");
                    found = display.contains(",") ? display.substring(0, display.indexOf(',')) : display;
                }
                final String hotel = found.isEmpty() ? "Hotel" : found;
                final String displayAddress = best.optString("display_name", q);
                final double lat = best.optDouble("lat", 0), lon = best.optDouble("lon", 0);
                flowPrefs.edit().putString("hotelName", hotel).putString("hotelAddress", displayAddress)
                        .putString("hotelLat", String.valueOf(lat)).putString("hotelLon", String.valueOf(lon)).apply();
                runOnUiThread(() -> {
                    name.setText(hotel);
                    address.setText(displayAddress);
                    Toast.makeText(this, "Hotel found. Now enter the price.", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    name.setText("");
                    Toast.makeText(this, "Could not identify the hotel automatically. You can still type the name.", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void safeSaveVacation(Button saveButton) {
        try {
            JSONArray arr;
            try { arr = new JSONArray(flowPrefs.getString("vacations", "[]")); }
            catch (Exception badSavedData) { arr = new JSONArray(); }

            JSONObject o = new JSONObject();
            String[] stringKeys = {"start","end","miles","gas","gasSource","snacks","extras","year","make","model","mpg","fuelType","odo","nextOil","payload","hotelName","hotelAddress","hotelCost","routeOut","routeBack","startLat","startLon","endLat","endLon","hotelLat","hotelLon"};
            for (String k : stringKeys) o.put(k, flowPrefs.getString(k, ""));
            o.put("returnMiles", flowPrefs.getFloat("returnMiles", 0));
            o.put("duration", flowPrefs.getFloat("duration", 0));
            o.put("returnDuration", flowPrefs.getFloat("returnDuration", 0));
            o.put("includeHotel", flowPrefs.getBoolean("includeHotel", true));
            String destination = flowPrefs.getString("end", "").trim();
            o.put("name", destination.isEmpty() ? "Planned trip" : destination + " vacation");
            arr.put(o);
            boolean ok = flowPrefs.edit().putString("vacations", arr.toString()).commit();
            if (!ok) throw new Exception("Storage write failed");
            saveButton.setText("Saved ✓");
            Toast.makeText(this, "Vacation saved", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not save trip. Your current planner data is still safe.", Toast.LENGTH_LONG).show();
        }
    }

    private void clearCurrentPlannerSafely() {
        String[] clear = {"start","end","miles","gasSource","snacks","extras","routeOut","routeBack","startLat","startLon","endLat","endLon"};
        SharedPreferences.Editor e = flowPrefs.edit();
        for (String k : clear) e.remove(k);
        e.remove("returnMiles").remove("duration").remove("returnDuration").apply();
    }

    private String httpGet(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(12000); c.setReadTimeout(16000);
        c.setRequestProperty("User-Agent", "TrailboundAndroid/4.2");
        c.setRequestProperty("Accept", "application/json");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
            StringBuilder s = new StringBuilder(); String line;
            while ((line = br.readLine()) != null) s.append(line);
            return s.toString();
        } finally { c.disconnect(); }
    }

    private Button findButton(View root, String text) {
        for (Button b : buttons(root)) if (textOf(b).equalsIgnoreCase(text)) return b;
        return null;
    }

    private TextView findText(View root, String text) {
        for (TextView t : textViews(root)) if (textOf(t).equalsIgnoreCase(text)) return t;
        return null;
    }

    private List<Button> buttons(View root) { List<Button> out = new ArrayList<>(); collect(root, Button.class, out); return out; }
    private List<TextView> textViews(View root) { List<TextView> out = new ArrayList<>(); collect(root, TextView.class, out); return out; }
    private List<EditText> editTexts(View root) { List<EditText> out = new ArrayList<>(); collect(root, EditText.class, out); return out; }
    private List<CheckBox> checkBoxes(View root) { List<CheckBox> out = new ArrayList<>(); collect(root, CheckBox.class, out); return out; }

    private <T extends View> void collect(View v, Class<T> cls, List<T> out) {
        if (cls.isInstance(v)) out.add(cls.cast(v));
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), cls, out);
        }
    }

    private String textOf(TextView v) { return v.getText() == null ? "" : v.getText().toString().trim(); }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }

    @Override protected void onDestroy() {
        flowIo.shutdownNow();
        super.onDestroy();
    }
}
