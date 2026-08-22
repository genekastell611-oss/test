package com.tripfuel.app;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Trailbound 5.9 - fixes stale routes and refreshes real route-average fuel pricing. */
public class TrailboundRouteGasRefreshActivity extends TrailboundDualGasActivity {
    private static final String PREFS = "trailbound_v5";
    private SharedPreferences prefs;
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean patching;
    private boolean autoRefreshAttempted;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (!patching && !isFinishing()) patchTrip();
        });
        main.postDelayed(this::patchTrip, 450);
    }

    @Override protected void onResume() {
        super.onResume();
        main.postDelayed(this::patchTrip, 350);
    }

    private void patchTrip() {
        if (patching || isFinishing()) return;
        patching = true;
        try {
            View root = getWindow().getDecorView();
            TextView header = findExactText(root, "Trip profile");
            if (header == null || !(header.getParent() instanceof LinearLayout)) return;
            LinearLayout card = (LinearLayout) header.getParent();

            EditText from = exactField(root, "FROM");
            EditText to = exactField(root, "TO");
            EditText avgGas = exactField(root, "PREPARED GAS $ / GAL");
            Button map = findButton(root, "Map actual round trip");
            Button update = findButton(root, "Update estimate");

            if (avgGas != null && card.findViewWithTag("route_gas_refresh") == null) {
                Button refresh = new Button(this);
                refresh.setTag("route_gas_refresh");
                refresh.setText("Refresh route + automatic gas average");
                refresh.setAllCaps(false);
                refresh.setTextColor(0xFFFFFFFF);
                refresh.setBackgroundColor(0xFF526C3D);
                int index = Math.min(card.indexOfChild(avgGas) + 1, card.getChildCount());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54));
                lp.topMargin = dp(8);
                card.addView(refresh, index, lp);
                refresh.setOnClickListener(v -> refreshRouteThenGas(from, to, map, update, avgGas, true));
            }

            correctLinkedPlan(root, from, to);

            JSONObject trip = activeTrip();
            double miles = number(trip.optString("outMiles", "0")) + number(trip.optString("backMiles", "0"));
            if (!autoRefreshAttempted && from != null && to != null && !text(from).trim().isEmpty() && !text(to).trim().isEmpty() && miles <= 0) {
                autoRefreshAttempted = true;
                main.postDelayed(() -> refreshRouteThenGas(from, to, map, update, avgGas, false), 700);
            }
        } catch (Exception ignored) {
        } finally {
            patching = false;
        }
    }

    private void refreshRouteThenGas(EditText from, EditText to, Button mapButton, Button updateButton, EditText avgGas, boolean showToast) {
        String start = text(from).trim();
        String end = text(to).trim();
        if (start.isEmpty() || end.isEmpty()) {
            toast("Enter both FROM and TO first");
            return;
        }
        if (showToast) toast("Refreshing route and gas prices…");

        // Reuse the app's native map action so route geometry, miles, time and linked profile state stay canonical.
        if (mapButton != null) mapButton.performClick();

        // Wait for the asynchronous map save, then sample the newly persisted route.
        pollForFreshRoute(start, end, 0, updateButton, avgGas);
    }

    private void pollForFreshRoute(String expectedStart, String expectedEnd, int attempt, Button updateButton, EditText avgGas) {
        main.postDelayed(() -> {
            JSONObject trip = activeTrip();
            boolean endpointsMatch = expectedStart.equals(trip.optString("start", "")) && expectedEnd.equals(trip.optString("end", ""));
            boolean hasRoute = !trip.optString("routeOut", "").isEmpty() && number(trip.optString("outMiles", "0")) > 0;
            if (endpointsMatch && hasRoute) {
                refreshGasAverage(trip, avgGas, updateButton);
            } else if (attempt < 12) {
                pollForFreshRoute(expectedStart, expectedEnd, attempt + 1, updateButton, avgGas);
            } else {
                toast("Route refresh did not finish. Check the addresses and connection.");
            }
        }, attempt == 0 ? 1300 : 700);
    }

    private void refreshGasAverage(JSONObject trip, EditText avgGas, Button updateButton) {
        io.execute(() -> {
            ArrayList<Double> samples = new ArrayList<>();
            LinkedHashSet<String> states = new LinkedHashSet<>();
            try {
                JSONArray route = new JSONArray(trip.optString("routeOut", "[]"));
                if (route.length() > 0) {
                    int[] positions = new int[]{0, route.length()/4, route.length()/2, (route.length()*3)/4, route.length()-1};
                    for (int pos : positions) {
                        if (pos < 0 || pos >= route.length()) continue;
                        JSONArray p = route.optJSONArray(pos);
                        if (p == null || p.length() < 2) continue;
                        String state = reverseState(p.optDouble(1), p.optDouble(0));
                        if (!state.isEmpty()) states.add(state);
                    }
                }

                int fuelIndex = linkedFuelIndex(trip);
                for (String state : states) {
                    double price = aaaStatePrice(state, fuelIndex);
                    if (price > 0) samples.add(price);
                }
                if (samples.isEmpty()) {
                    double national = aaaNationalPrice(fuelIndex);
                    if (national > 0) samples.add(national);
                }
            } catch (Exception ignored) { }

            final double average;
            if (!samples.isEmpty()) {
                double total = 0;
                for (double d : samples) total += d;
                average = total / samples.size();
            } else {
                average = number(prefs.getString("lastRouteAverageGas", "0"));
            }

            main.post(() -> {
                if (average > 0) {
                    String value = String.format(Locale.US, "%.2f", average);
                    if (avgGas != null) avgGas.setText(value);
                    persistTripField("gas", value);
                    persistTripField("gasSource", "Automatic route average from " + Math.max(1, samples.size()) + " public price sample(s)");
                    prefs.edit().putString("lastRouteAverageGas", value).apply();
                    if (updateButton != null) updateButton.performClick();
                    main.postDelayed(() -> {
                        correctLinkedPlan(getWindow().getDecorView(), exactField(getWindow().getDecorView(), "FROM"), exactField(getWindow().getDecorView(), "TO"));
                        getWindow().getDecorView().requestLayout();
                    }, 250);
                    toast("Automatic route gas average refreshed: $" + value + "/gal");
                } else {
                    toast("Live gas average unavailable. Keeping your last saved automatic estimate.");
                }
            });
        });
    }

    private String reverseState(double lat, double lon) {
        try {
            String url = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=5&lat=" + lat + "&lon=" + lon;
            JSONObject o = new JSONObject(http(url));
            JSONObject a = o.optJSONObject("address");
            if (a == null) return "";
            String code = a.optString("ISO3166-2-lvl4", "");
            if (code.startsWith("US-") && code.length() >= 5) return code.substring(3);
            code = a.optString("state_code", "");
            if (!code.isEmpty()) return code.toUpperCase(Locale.US);
        } catch (Exception ignored) { }
        return "";
    }

    private double aaaStatePrice(String state, int fuelIndex) {
        try {
            String html = http("https://gasprices.aaa.com/?state=" + URLEncoder.encode(state, "UTF-8"));
            return parseAaaCurrentAverage(html, fuelIndex);
        } catch (Exception e) { return 0; }
    }

    private double aaaNationalPrice(int fuelIndex) {
        try { return parseAaaCurrentAverage(http("https://gasprices.aaa.com/"), fuelIndex); }
        catch (Exception e) { return 0; }
    }

    private double parseAaaCurrentAverage(String html, int fuelIndex) {
        if (html == null || html.isEmpty()) return 0;
        int start = html.toLowerCase(Locale.US).indexOf("current avg");
        String slice = start >= 0 ? html.substring(start, Math.min(html.length(), start + 5000)) : html;
        Matcher m = Pattern.compile("\\$\\s*([0-9]+\\.[0-9]{2,4})").matcher(slice);
        ArrayList<Double> vals = new ArrayList<>();
        while (m.find() && vals.size() < 12) {
            double v = number(m.group(1));
            if (v > 1 && v < 10) vals.add(v);
        }
        if (vals.isEmpty()) return 0;
        int idx = Math.max(0, Math.min(fuelIndex, vals.size() - 1));
        return vals.get(idx);
    }

    private int linkedFuelIndex(JSONObject trip) {
        JSONObject vehicle = profileById("vehicles", trip.optString("vehicleId", prefs.getString("activeVehicleId", "")));
        String type = vehicle.optString("fuelType", "Regular");
        if ("Midgrade".equalsIgnoreCase(type)) return 1;
        if ("Premium".equalsIgnoreCase(type)) return 2;
        if ("Diesel".equalsIgnoreCase(type)) return 3;
        return 0;
    }

    private void correctLinkedPlan(View root, EditText from, EditText to) {
        try {
            JSONObject trip = activeTrip();
            if (trip.optString("id", "").isEmpty()) return;
            String start = text(from).trim();
            String end = text(to).trim();
            if (start.isEmpty()) start = trip.optString("start", "Start");
            if (end.isEmpty()) end = trip.optString("end", "Destination");
            JSONObject vehicle = profileById("vehicles", trip.optString("vehicleId", prefs.getString("activeVehicleId", "")));
            JSONObject hotel = profileById("hotels", trip.optString("hotelId", prefs.getString("activeHotelId", "")));

            ArrayList<TextView> all = new ArrayList<>();
            collect(root, TextView.class, all);
            for (TextView t : all) {
                String s = t.getText() == null ? "" : t.getText().toString();
                if (s.startsWith("LINKED PLAN")) {
                    t.setText("LINKED PLAN\n" + vehicle.optString("label", "No car selected") +
                            "\nSTART: " + start +
                            "\nDESTINATION: " + end +
                            "\nHOTEL: " + hotel.optString("label", "No hotel selected"));
                    break;
                }
            }
        } catch (Exception ignored) { }
    }

    private JSONObject activeTrip() { return profileById("trips", prefs.getString("activeTripId", "")); }

    private void persistTripField(String key, Object value) {
        String id = prefs.getString("activeTripId", "");
        if (id.isEmpty()) return;
        try {
            JSONArray a = profiles("trips"), out = new JSONArray();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                if (id.equals(o.optString("id", ""))) o.put(key, value);
                out.put(o);
            }
            prefs.edit().putString("trips", out.toString()).commit();
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

    private <T extends View> void collect(View v, Class<T> cls, List<T> out) {
        if (cls.isInstance(v)) out.add(cls.cast(v));
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), cls, out);
        }
    }

    private String text(EditText e) { return e == null || e.getText() == null ? "" : e.getText().toString(); }
    private double number(String s) { try { return Double.parseDouble(s == null ? "" : s.trim()); } catch (Exception e) { return 0; } }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private String http(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(u).openConnection();
        c.setConnectTimeout(12000); c.setReadTimeout(22000);
        c.setRequestProperty("User-Agent", "TrailboundAndroid/5.9");
        c.setRequestProperty("Accept", "application/json,text/html,*/*");
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
}
