package com.tripfuel.app;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Trailbound 5.2: automatically plots estimated fuel stops on the real round-trip map. */
public class TrailboundGasStopMapActivity extends TrailboundFuelPlannerActivity {
    private static final String PREFS = "trailbound_v5";
    private SharedPreferences prefs;
    private boolean plotting;
    private String lastSignature = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (!plotting && !isFinishing()) plotFuelStopsIfNeeded();
        });
        getWindow().getDecorView().postDelayed(this::plotFuelStopsIfNeeded, 300);
    }

    @Override protected void onResume() {
        super.onResume();
        getWindow().getDecorView().postDelayed(this::plotFuelStopsIfNeeded, 300);
    }

    private void plotFuelStopsIfNeeded() {
        if (plotting || isFinishing()) return;
        plotting = true;
        try {
            View root = getWindow().getDecorView();
            TextView tripHeader = findExactText(root, "Trip profile");
            if (tripHeader == null) return;
            MapView map = firstMap(root);
            if (map == null) return;

            JSONObject trip = activeTrip();
            if (trip.optString("routeOut", "").isEmpty()) {
                clearFuelMarkers(map);
                return;
            }

            double mpg = adjustedMpg(trip);
            double tank = inputValue(root, "Tank size (gal)", positive(prefs.getString("fuelTankSize", "0")));
            double depart = inputValue(root, "Departure gas (gal)", positive(prefs.getString("departureFuel", "0")));
            double outMiles = positive(trip.optString("outMiles", "0"));
            double backMiles = positive(trip.optString("backMiles", "0"));
            double roundMiles = outMiles + backMiles;
            if (backMiles <= 0) roundMiles = outMiles * 2.0;

            String signature = trip.optString("id", "") + "|" + trip.optString("routeOut", "").hashCode() + "|" +
                    trip.optString("routeBack", "").hashCode() + "|" + one(mpg) + "|" + one(tank) + "|" + one(depart);
            if (signature.equals(lastSignature) && hasFuelMarkers(map)) return;
            lastSignature = signature;

            clearFuelMarkers(map);
            if (mpg <= 0 || tank <= 0 || roundMiles <= 0) {
                map.invalidate();
                return;
            }

            depart = Math.min(Math.max(0, depart), tank);
            double reserve = Math.min(tank * 0.30, Math.max(0.5, tank * 0.15));
            double firstRange = Math.max(0, depart - reserve) * mpg;
            double fullRange = Math.max(0.1, tank - reserve) * mpg;
            if (fullRange <= 0) return;

            ArrayList<Double> stopMiles = new ArrayList<>();
            double next = firstRange;
            int guard = 0;
            while (next < roundMiles && guard++ < 30) {
                stopMiles.add(next);
                next += fullRange;
            }

            if (stopMiles.isEmpty()) {
                map.invalidate();
                return;
            }

            ArrayList<GeoPoint> roundRoute = routePoints(trip);
            if (roundRoute.size() < 2) return;
            double geometricMiles = polylineMiles(roundRoute);
            if (geometricMiles <= 0) return;

            for (int i = 0; i < stopMiles.size(); i++) {
                double requestedTripMile = stopMiles.get(i);
                double geometricTarget = Math.min(geometricMiles, requestedTripMile / roundMiles * geometricMiles);
                GeoPoint point = pointAtDistance(roundRoute, geometricTarget);
                if (point == null) continue;
                Marker marker = new Marker(map);
                marker.setPosition(point);
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                marker.setTitle("Fuel stop " + (i + 1));
                String leg = requestedTripMile <= outMiles ?
                        "Outbound around mile " + Math.round(requestedTripMile) :
                        "Return leg about " + Math.round(Math.max(0, requestedTripMile - outMiles)) + " miles after the destination";
                marker.setSnippet(leg + " • estimated at " + one(mpg) + " payload-adjusted MPG");
                map.getOverlays().add(marker);
            }
            map.invalidate();
        } catch (Exception ignored) {
            // Map markers are an enhancement; never crash the core trip planner.
        } finally {
            plotting = false;
        }
    }

    private JSONObject activeTrip() {
        String id = prefs.getString("activeTripId", "");
        if (id.isEmpty()) return new JSONObject();
        try {
            JSONArray a = new JSONArray(prefs.getString("trips", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null && id.equals(o.optString("id", ""))) return o;
            }
        } catch (Exception ignored) { }
        return new JSONObject();
    }

    private JSONObject profileById(String key, String id) {
        if (id == null || id.isEmpty()) return new JSONObject();
        try {
            JSONArray a = new JSONArray(prefs.getString(key, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null && id.equals(o.optString("id", ""))) return o;
            }
        } catch (Exception ignored) { }
        return new JSONObject();
    }

    private double adjustedMpg(JSONObject trip) {
        String vehicleId = trip.optString("vehicleId", prefs.getString("activeVehicleId", ""));
        JSONObject vehicle = profileById("vehicles", vehicleId);
        double base = positive(vehicle.optString("mpg", "0"));
        double payload = positive(vehicle.optString("payload", "0"));
        if (base <= 0) return 0;
        return base * (1.0 - Math.min(0.25, payload / 100.0 * 0.01));
    }

    private ArrayList<GeoPoint> routePoints(JSONObject trip) {
        ArrayList<GeoPoint> points = new ArrayList<>();
        addGeometry(points, trip.optString("routeOut", ""));
        addGeometry(points, trip.optString("routeBack", ""));
        return points;
    }

    private void addGeometry(ArrayList<GeoPoint> out, String json) {
        if (json == null || json.isEmpty()) return;
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                JSONArray p = a.getJSONArray(i);
                GeoPoint g = new GeoPoint(p.getDouble(1), p.getDouble(0));
                if (!out.isEmpty()) {
                    GeoPoint last = out.get(out.size() - 1);
                    if (Math.abs(last.getLatitude() - g.getLatitude()) < 1e-8 && Math.abs(last.getLongitude() - g.getLongitude()) < 1e-8) continue;
                }
                out.add(g);
            }
        } catch (Exception ignored) { }
    }

    private double polylineMiles(List<GeoPoint> points) {
        double total = 0;
        for (int i = 1; i < points.size(); i++) total += miles(points.get(i - 1), points.get(i));
        return total;
    }

    private GeoPoint pointAtDistance(List<GeoPoint> points, double targetMiles) {
        if (points.isEmpty()) return null;
        if (targetMiles <= 0) return points.get(0);
        double walked = 0;
        for (int i = 1; i < points.size(); i++) {
            GeoPoint a = points.get(i - 1), b = points.get(i);
            double seg = miles(a, b);
            if (seg <= 0) continue;
            if (walked + seg >= targetMiles) {
                double f = Math.max(0, Math.min(1, (targetMiles - walked) / seg));
                return new GeoPoint(a.getLatitude() + (b.getLatitude() - a.getLatitude()) * f,
                        a.getLongitude() + (b.getLongitude() - a.getLongitude()) * f);
            }
            walked += seg;
        }
        return points.get(points.size() - 1);
    }

    private double miles(GeoPoint a, GeoPoint b) {
        double r = 3958.7613;
        double lat1 = Math.toRadians(a.getLatitude()), lat2 = Math.toRadians(b.getLatitude());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.getLongitude() - a.getLongitude());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    private void clearFuelMarkers(MapView map) {
        ArrayList<Overlay> remove = new ArrayList<>();
        for (Overlay overlay : map.getOverlays()) {
            if (overlay instanceof Marker) {
                String title = ((Marker) overlay).getTitle();
                if (title != null && title.toLowerCase(Locale.US).startsWith("fuel stop ")) remove.add(overlay);
            }
        }
        map.getOverlays().removeAll(remove);
    }

    private boolean hasFuelMarkers(MapView map) {
        for (Overlay overlay : map.getOverlays()) {
            if (overlay instanceof Marker) {
                String title = ((Marker) overlay).getTitle();
                if (title != null && title.toLowerCase(Locale.US).startsWith("fuel stop ")) return true;
            }
        }
        return false;
    }

    private double inputValue(View root, String label, double fallback) {
        TextView labelView = findExactText(root, label);
        if (labelView != null && labelView.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) labelView.getParent();
            for (int i = 0; i < parent.getChildCount(); i++) {
                View v = parent.getChildAt(i);
                if (v instanceof EditText) return positive(((EditText) v).getText().toString());
            }
        }
        return fallback;
    }

    private TextView findExactText(View root, String target) {
        ArrayList<TextView> all = new ArrayList<>();
        collect(root, TextView.class, all);
        for (TextView t : all) {
            if (t.getText() != null && target.equalsIgnoreCase(t.getText().toString().trim())) return t;
        }
        return null;
    }

    private MapView firstMap(View root) {
        ArrayList<MapView> maps = new ArrayList<>();
        collect(root, MapView.class, maps);
        return maps.isEmpty() ? null : maps.get(0);
    }

    private <T extends View> void collect(View v, Class<T> type, List<T> out) {
        if (type.isInstance(v)) out.add(type.cast(v));
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), type, out);
        }
    }

    private double positive(String s) {
        try { return Math.max(0, Double.parseDouble(s == null ? "" : s.trim())); }
        catch (Exception e) { return 0; }
    }

    private String one(double d) { return String.format(Locale.US, "%.1f", d); }
}