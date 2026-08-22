package com.tripfuel.app;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Final 6.2 integrity pass: keeps UI text and saved audited values synchronized. */
public class TrailboundIntegrityActivity extends TrailboundAuditActivity {
    private static final String PREFS = "trailbound_v5";
    private SharedPreferences prefs;
    private boolean patching;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (!patching && !isFinishing()) patchIntegrity();
        });
        getWindow().getDecorView().postDelayed(this::patchIntegrity, 750);
    }

    @Override protected void onResume() {
        super.onResume();
        getWindow().getDecorView().postDelayed(this::patchIntegrity, 550);
    }

    private void patchIntegrity() {
        if (patching || isFinishing()) return;
        patching = true;
        try {
            View root = getWindow().getDecorView();
            if (findExactText(root, "Trip profile") != null) syncGasSourceUi(root);
            if (findExactText(root, "Vehicle profile") != null) syncSafetyMaintenanceBasis(root);
        } catch (Exception ignored) {
        } finally {
            patching = false;
        }
    }

    /** The legacy save code reads this TextView, so keep it equal to the authoritative saved source. */
    private void syncGasSourceUi(View root) {
        JSONObject trip = profileById("trips", prefs.getString("activeTripId", ""));
        String source = trip.optString("gasSource", "").trim();
        if (source.isEmpty()) return;
        TextView label = findExactText(root, "PREPARED GAS $ / GAL");
        if (label == null || !(label.getParent() instanceof ViewGroup)) return;
        ViewGroup card = (ViewGroup) label.getParent();
        EditText gas = exactField(root, "PREPARED GAS $ / GAL");
        if (gas == null || gas.getParent() != card) return;
        int index = card.indexOfChild(gas);
        if (index >= 0 && index + 1 < card.getChildCount()) {
            View candidate = card.getChildAt(index + 1);
            if (candidate instanceof TextView && !(candidate instanceof EditText)) {
                TextView sourceView = (TextView) candidate;
                if (!source.equals(sourceView.getText() == null ? "" : sourceView.getText().toString())) sourceView.setText(source);
            }
        }
    }

    private void syncSafetyMaintenanceBasis(View root) {
        TextView status = findTaggedText(root, "vehicle_safety_status");
        if (status == null) return;
        String id = prefs.getString("activeVehicleId", "");
        JSONObject vehicle = profileById("vehicles", id);
        double odometer = positive(vehicle.optString("odometer", "0"));
        double due = positive(vehicle.optString("nextOil", "0"));
        double interval = positive(vehicle.optString("oilInterval", prefs.getString("draftOilInterval", "5000")));
        if (interval <= 0) interval = 5000;
        JSONObject trip = linkedTrip(id);
        double out = positive(trip.optString("outMiles", "0"));
        double back = positive(trip.optString("backMiles", "0"));
        if (back <= 0 && out > 0) back = out;
        double round = out + back;

        ArrayList<CheckBox> checks = new ArrayList<>();
        collect(root, CheckBox.class, checks);
        int done = 0, total = 0;
        for (CheckBox box : checks) {
            Object tag = box.getTag();
            if (tag != null && tag.toString().startsWith("vehicle_safety_")) {
                total++;
                if (box.isChecked()) done++;
            }
        }
        if (total == 0) return;

        ArrayList<String> warnings = new ArrayList<>();
        if (due > 0 && due <= odometer) warnings.add("oil service is due now");
        else if (due > 0 && round > 0 && due <= odometer + round) warnings.add("oil service comes due before you return");
        else if (due > 0 && round > 0 && TrailboundTripMath.oilIntervalPercent(odometer + round, due, interval) < 20) warnings.add("service interval will be under 20% remaining after the trip");

        StringBuilder s = new StringBuilder();
        s.append("READINESS • ").append(done).append('/').append(total).append(" checks complete");
        if (done < total) s.append(" • ").append(total - done).append(" remaining");
        if (!warnings.isEmpty()) {
            s.append("\nReview before departure: ");
            for (int i = 0; i < warnings.size(); i++) {
                if (i > 0) s.append("; ");
                s.append(warnings.get(i));
            }
        }
        s.append("\nMaintenance basis: ").append(Math.round(interval)).append(" mi service interval");
        if (id.isEmpty()) s.append(" • save this vehicle to tie the checklist to it");
        status.setText(s.toString());
        status.setTextColor(Color.WHITE);
    }

    private JSONObject linkedTrip(String vehicleId) {
        JSONObject active = profileById("trips", prefs.getString("activeTripId", ""));
        if (!vehicleId.isEmpty() && vehicleId.equals(active.optString("vehicleId", ""))) return active;
        JSONArray trips = profiles("trips");
        for (int i = 0; i < trips.length(); i++) {
            JSONObject t = trips.optJSONObject(i);
            if (t != null && vehicleId.equals(t.optString("vehicleId", ""))) return t;
        }
        return new JSONObject();
    }

    private EditText exactField(View root, String label) {
        TextView l = findExactText(root, label);
        if (l == null || !(l.getParent() instanceof ViewGroup)) return null;
        ViewGroup p = (ViewGroup) l.getParent();
        int start = p.indexOfChild(l) + 1;
        for (int i = start; i < p.getChildCount(); i++) {
            View child = p.getChildAt(i);
            if (child instanceof EditText) return (EditText) child;
            if (child instanceof TextView && !(child instanceof EditText)) break;
        }
        return null;
    }

    private TextView findTaggedText(View root, String tag) {
        ArrayList<TextView> views = new ArrayList<>();
        collect(root, TextView.class, views);
        for (TextView t : views) if (tag.equals(t.getTag())) return t;
        return null;
    }

    private TextView findExactText(View root, String text) {
        ArrayList<TextView> views = new ArrayList<>();
        collect(root, TextView.class, views);
        for (TextView t : views) if (t.getText() != null && text.equalsIgnoreCase(t.getText().toString().trim())) return t;
        return null;
    }

    private <T extends View> void collect(View root, Class<T> type, List<T> out) {
        if (type.isInstance(root)) out.add(type.cast(root));
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), type, out);
        }
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

    private double positive(String s) {
        try { return Math.max(0, Double.parseDouble(s == null ? "" : s.trim())); }
        catch (Exception e) { return 0; }
    }
}
