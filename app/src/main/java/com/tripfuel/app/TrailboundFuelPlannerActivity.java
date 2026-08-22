package com.tripfuel.app;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Trailbound 5.1 fuel-range planner.
 * Keeps the stable 5.0 profile/save implementation untouched and adds
 * persistent departure-fuel planning to the native Trip screen.
 */
public class TrailboundFuelPlannerActivity extends TrailboundVehiclePhotoActivity {
    private static final String PREFS = "trailbound_v5";
    private static final String TAG_SECTION = "trailbound_fuel_planner_section";
    private static final Pattern ROUND_TRIP = Pattern.compile("ROUND TRIP\\s+([0-9]+(?:\\.[0-9]+)?)\\s+mi", Pattern.CASE_INSENSITIVE);
    private static final Pattern ADJUSTED_MPG = Pattern.compile("ADJUSTED MPG\\s+([0-9]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE);

    private SharedPreferences fuelPrefs;
    private boolean patching;
    private EditText tankSize;
    private EditText departureFuel;
    private TextView fuelPlan;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        fuelPrefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (!patching && !isFinishing()) patchTripFuelPlanner();
        });
        getWindow().getDecorView().post(this::patchTripFuelPlanner);
    }

    @Override protected void onResume() {
        super.onResume();
        getWindow().getDecorView().postDelayed(this::patchTripFuelPlanner, 150);
    }

    private void patchTripFuelPlanner() {
        if (patching || isFinishing()) return;
        patching = true;
        try {
            View root = getWindow().getDecorView();
            TextView tripHeader = findExactText(root, "Trip profile");
            if (tripHeader == null || !(tripHeader.getParent() instanceof LinearLayout)) return;
            LinearLayout card = (LinearLayout) tripHeader.getParent();

            View existing = card.findViewWithTag(TAG_SECTION);
            if (existing instanceof LinearLayout) {
                bindExisting((LinearLayout) existing);
                updateFuelPlan(root);
                return;
            }

            LinearLayout section = new LinearLayout(this);
            section.setTag(TAG_SECTION);
            section.setOrientation(LinearLayout.VERTICAL);
            section.setPadding(0, dp(12), 0, dp(4));

            TextView title = new TextView(this);
            title.setText("Fuel range & stops");
            title.setTextColor(0xFFEFFFD1);
            title.setTextSize(18);
            title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
            section.addView(title);

            TextView note = new TextView(this);
            note.setText("Trailbound uses the car's payload-adjusted MPG, how much gas you leave with, and a 15% reserve to estimate fill-ups.");
            note.setTextColor(0xFFEEE7D8);
            note.setTextSize(13);
            note.setPadding(0, dp(4), 0, dp(8));
            section.addView(note);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            tankSize = numberField("Tank size (gal)", fuelPrefs.getString("fuelTankSize", "14.5"));
            departureFuel = numberField("Departure gas (gal)", fuelPrefs.getString("departureFuel", "12.0"));
            row.addView((View) tankSize.getParent(), new LinearLayout.LayoutParams(0, -2, 1));
            row.addView((View) departureFuel.getParent(), new LinearLayout.LayoutParams(0, -2, 1));
            section.addView(row);

            fuelPlan = new TextView(this);
            fuelPlan.setTag("trailbound_fuel_plan_text");
            fuelPlan.setTextColor(0xFFFFFFFF);
            fuelPlan.setTextSize(15);
            fuelPlan.setLineSpacing(0, 1.15f);
            fuelPlan.setPadding(dp(14), dp(12), dp(14), dp(12));
            fuelPlan.setBackground(roundRect(0xFF10130D, 14, 0xFF697052));
            section.addView(fuelPlan, new LinearLayout.LayoutParams(-1, -2));

            TextWatcher watcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    fuelPrefs.edit()
                            .putString("fuelTankSize", safeText(tankSize))
                            .putString("departureFuel", safeText(departureFuel))
                            .apply();
                    updateFuelPlan(getWindow().getDecorView());
                }
                @Override public void afterTextChanged(Editable s) { }
            };
            tankSize.addTextChangedListener(watcher);
            departureFuel.addTextChangedListener(watcher);

            int insertAt = findInsertIndex(card);
            card.addView(section, Math.max(0, Math.min(insertAt, card.getChildCount())));
            updateFuelPlan(root);
        } catch (Exception ignored) {
            // A UI enhancement must never be able to take down the base planner.
        } finally {
            patching = false;
        }
    }

    private void bindExisting(LinearLayout section) {
        if (tankSize == null || departureFuel == null) {
            List<EditText> fields = new ArrayList<>();
            collect(section, EditText.class, fields);
            if (fields.size() >= 2) {
                tankSize = fields.get(0);
                departureFuel = fields.get(1);
            }
        }
        if (fuelPlan == null) {
            View v = section.findViewWithTag("trailbound_fuel_plan_text");
            if (v instanceof TextView) fuelPlan = (TextView) v;
        }
    }

    private int findInsertIndex(LinearLayout card) {
        for (int i = 0; i < card.getChildCount(); i++) {
            View v = card.getChildAt(i);
            if (v instanceof TextView) {
                String text = ((TextView) v).getText() == null ? "" : ((TextView) v).getText().toString();
                if (text.contains("Map actual round trip")) return i;
            }
            if (v instanceof android.widget.Button) {
                String text = ((android.widget.Button) v).getText() == null ? "" : ((android.widget.Button) v).getText().toString();
                if (text.equalsIgnoreCase("Map actual round trip")) return i;
            }
        }
        return Math.min(12, card.getChildCount());
    }

    private EditText numberField(String labelText, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(3), dp(4), dp(3), dp(6));
        TextView label = new TextView(this);
        label.setText(labelText);
        label.setTextColor(0xFFFFF6E5);
        label.setTextSize(12);
        label.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        box.addView(label);
        EditText field = new EditText(this);
        field.setText(value);
        field.setSingleLine(true);
        field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        field.setTextColor(0xFFFFFFFF);
        field.setHintTextColor(0xFFC9C4B7);
        field.setTextSize(16);
        field.setPadding(dp(12), 0, dp(12), 0);
        field.setBackground(roundRect(0xFF0A0C09, 14, 0xFF78805E));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.topMargin = dp(5);
        box.addView(field, lp);
        return field;
    }

    private void updateFuelPlan(View root) {
        if (fuelPlan == null) return;
        try {
            String estimateText = findEstimateText(root);
            double roundMiles = matchNumber(ROUND_TRIP, estimateText);
            double adjustedMpg = matchNumber(ADJUSTED_MPG, estimateText);

            if (adjustedMpg <= 0) adjustedMpg = selectedVehicleAdjustedMpg();
            if (roundMiles <= 0) roundMiles = activeTripRoundMiles();

            double tank = positive(safeText(tankSize));
            double depart = positive(safeText(departureFuel));
            if (tank <= 0 || adjustedMpg <= 0 || roundMiles <= 0) {
                fuelPlan.setText("Map the trip and enter tank size plus departure fuel to estimate fill-ups.");
                return;
            }
            depart = Math.min(depart, tank);
            double reserveGallons = Math.max(0.5, tank * 0.15);
            reserveGallons = Math.min(reserveGallons, tank * 0.30);
            double firstUsable = Math.max(0, depart - reserveGallons);
            double fullUsable = Math.max(0.1, tank - reserveGallons);
            double firstRange = firstUsable * adjustedMpg;
            double fullRange = fullUsable * adjustedMpg;

            ArrayList<Double> stops = new ArrayList<>();
            double next = firstRange;
            int guard = 0;
            while (next < roundMiles && guard++ < 30) {
                stops.add(next);
                next += fullRange;
            }

            if (stops.isEmpty()) {
                fuelPlan.setText("0 planned fuel stops • your departure fuel should cover the round trip while keeping about a 15% reserve.");
                return;
            }

            double outboundMiles = activeTripOutboundMiles();
            if (outboundMiles <= 0) outboundMiles = roundMiles / 2.0;
            StringBuilder where = new StringBuilder();
            for (int i = 0; i < stops.size(); i++) {
                double d = stops.get(i);
                if (i > 0) where.append("\n");
                where.append("Stop ").append(i + 1).append(": ");
                if (d <= outboundMiles) {
                    where.append("outbound around mile ").append(Math.round(d));
                } else {
                    where.append("return leg about ").append(Math.round(d - outboundMiles)).append(" mi after leaving the destination");
                }
            }
            fuelPlan.setText(stops.size() + " planned fuel stop" + (stops.size() == 1 ? "" : "s") +
                    " • estimated with " + one(adjustedMpg) + " payload-adjusted MPG and a 15% reserve.\n" + where);
        } catch (Exception ignored) {
            fuelPlan.setText("Fuel-stop estimate unavailable right now. Your trip data is still safe.");
        }
    }

    private String findEstimateText(View root) {
        List<TextView> all = new ArrayList<>();
        collect(root, TextView.class, all);
        for (TextView t : all) {
            String s = t.getText() == null ? "" : t.getText().toString();
            if (s.contains("ROUND TRIP") && s.contains("ADJUSTED MPG")) return s;
        }
        return "";
    }

    private double selectedVehicleAdjustedMpg() {
        try {
            String activeTripId = fuelPrefs.getString("activeTripId", "");
            JSONObject trip = profileById("trips", activeTripId);
            String vehicleId = trip.optString("vehicleId", fuelPrefs.getString("activeVehicleId", ""));
            JSONObject vehicle = profileById("vehicles", vehicleId);
            double base = positive(vehicle.optString("mpg", "0"));
            double payload = positive(vehicle.optString("payload", "0"));
            return base <= 0 ? 0 : base * (1.0 - Math.min(0.25, payload / 100.0 * 0.01));
        } catch (Exception e) {
            return 0;
        }
    }

    private double activeTripRoundMiles() {
        JSONObject t = profileById("trips", fuelPrefs.getString("activeTripId", ""));
        return positive(t.optString("outMiles", "0")) + positive(t.optString("backMiles", "0"));
    }

    private double activeTripOutboundMiles() {
        JSONObject t = profileById("trips", fuelPrefs.getString("activeTripId", ""));
        return positive(t.optString("outMiles", "0"));
    }

    private JSONObject profileById(String key, String id) {
        if (id == null || id.isEmpty()) return new JSONObject();
        try {
            JSONArray a = new JSONArray(fuelPrefs.getString(key, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null && id.equals(o.optString("id", ""))) return o;
            }
        } catch (Exception ignored) { }
        return new JSONObject();
    }

    private TextView findExactText(View root, String target) {
        List<TextView> all = new ArrayList<>();
        collect(root, TextView.class, all);
        for (TextView t : all) {
            if (t.getText() != null && target.equalsIgnoreCase(t.getText().toString().trim())) return t;
        }
        return null;
    }

    private <T extends View> void collect(View v, Class<T> type, List<T> out) {
        if (type.isInstance(v)) out.add(type.cast(v));
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), type, out);
        }
    }

    private double matchNumber(Pattern p, String s) {
        Matcher m = p.matcher(s == null ? "" : s);
        return m.find() ? positive(m.group(1)) : 0;
    }

    private double positive(String s) {
        try { return Math.max(0, Double.parseDouble(s == null ? "" : s.trim())); }
        catch (Exception e) { return 0; }
    }

    private String safeText(EditText e) {
        return e == null || e.getText() == null ? "" : e.getText().toString();
    }

    private String one(double d) { return String.format(Locale.US, "%.1f", d); }
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }

    private android.graphics.drawable.GradientDrawable roundRect(int fill, int radius, int stroke) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(1), stroke);
        return g;
    }
}