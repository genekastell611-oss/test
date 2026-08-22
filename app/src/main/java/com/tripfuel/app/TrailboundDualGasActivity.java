package com.tripfuel.app;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Trailbound 5.8 - separate automatic-average and conservative fuel budget scenarios. */
public class TrailboundDualGasActivity extends TrailboundCleanFixActivity {
    private static final String PREFS = "trailbound_v5";
    private SharedPreferences prefs;
    private boolean patching;
    private boolean restoring;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (!patching && !isFinishing()) patchTripGasScenarios();
        });
        getWindow().getDecorView().postDelayed(this::patchTripGasScenarios, 350);
    }

    @Override protected void onResume() {
        super.onResume();
        getWindow().getDecorView().postDelayed(this::patchTripGasScenarios, 350);
    }

    private void patchTripGasScenarios() {
        if (patching || isFinishing()) return;
        patching = true;
        try {
            View root = getWindow().getDecorView();
            TextView tripHeader = findExactText(root, "Trip profile");
            if (tripHeader == null || !(tripHeader.getParent() instanceof LinearLayout)) return;
            LinearLayout card = (LinearLayout) tripHeader.getParent();

            EditText averageGas = exactField(root, "PREPARED GAS $ / GAL");
            if (averageGas == null) return;

            EditText conservative = findTaggedEditText(root, "conservative_gas_field");
            TextView comparison = findTaggedTextView(root, "dual_gas_summary");

            if (conservative == null) {
                int avgIndex = card.indexOfChild(averageGas);
                TextView label = new TextView(this);
                label.setText("CONSERVATIVE GAS $ / GAL");
                label.setTextColor(Color.rgb(249, 241, 222));
                label.setTextSize(12);
                label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-1, -2);
                labelLp.topMargin = dp(10);

                conservative = new EditText(this);
                conservative.setTag("conservative_gas_field");
                conservative.setSingleLine(true);
                conservative.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                conservative.setTextColor(Color.WHITE);
                conservative.setHintTextColor(Color.rgb(190, 190, 180));
                conservative.setTextSize(16);
                conservative.setHint("Optional, e.g. 4.25");
                conservative.setPadding(dp(12), 0, dp(12), 0);
                conservative.setBackgroundColor(Color.rgb(9, 12, 8));
                LinearLayout.LayoutParams fieldLp = new LinearLayout.LayoutParams(-1, dp(52));
                fieldLp.topMargin = dp(5);

                card.addView(label, Math.min(avgIndex + 2, card.getChildCount()), labelLp);
                card.addView(conservative, Math.min(avgIndex + 3, card.getChildCount()), fieldLp);

                JSONObject trip = activeTrip();
                restoring = true;
                String saved = trip.optString("conservativeGas", "");
                conservative.setText(saved);
                restoring = false;

                final EditText conservativeField = conservative;
                conservative.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                        if (restoring) return;
                        persistTripField("conservativeGas", s == null ? "" : s.toString());
                        conservativeField.postDelayed(() -> updateDualSummary(getWindow().getDecorView()), 120);
                    }
                    @Override public void afterTextChanged(Editable s) { }
                });
            }

            if (comparison == null) {
                comparison = new TextView(this);
                comparison.setTag("dual_gas_summary");
                comparison.setTextColor(Color.WHITE);
                comparison.setTextSize(15);
                comparison.setLineSpacing(0, 1.15f);
                comparison.setPadding(dp(14), dp(14), dp(14), dp(14));
                comparison.setBackgroundColor(Color.rgb(8, 10, 7));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.topMargin = dp(12);
                card.addView(comparison, lp);
            }

            Button update = findButton(root, "Update estimate");
            if (update != null && !"dual_gas_touch".equals(update.getTag())) {
                update.setTag("dual_gas_touch");
                update.setOnTouchListener((v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        v.postDelayed(() -> updateDualSummary(getWindow().getDecorView()), 180);
                    }
                    return false;
                });
            }

            attachRefreshWatcher(exactField(root, "SNACKS & DRINKS"));
            attachRefreshWatcher(exactField(root, "OTHER TRIP MONEY"));
            attachRefreshWatcher(averageGas);

            updateDualSummary(root);
        } catch (Exception ignored) {
        } finally {
            patching = false;
        }
    }

    private void updateDualSummary(View root) {
        try {
            TextView summary = findTaggedTextView(root, "dual_gas_summary");
            if (summary == null) return;

            JSONObject trip = activeTrip();
            Spinner carSpinner = exactSpinner(root, "CAR FOR THIS TRIP");
            Spinner hotelSpinner = exactSpinner(root, "HOTEL FOR THIS TRIP");
            JSONObject vehicle = profileById("vehicles", selectedProfileId("vehicles", carSpinner == null ? 0 : carSpinner.getSelectedItemPosition()));
            JSONObject hotel = profileById("hotels", selectedProfileId("hotels", hotelSpinner == null ? 0 : hotelSpinner.getSelectedItemPosition()));

            double outMiles = number(trip.optString("outMiles", "0"));
            double backMiles = number(trip.optString("backMiles", "0"));
            if (backMiles <= 0) backMiles = outMiles;
            double roundMiles = outMiles + backMiles;
            double baseMpg = number(vehicle.optString("mpg", "0"));
            double payload = number(vehicle.optString("payload", "0"));
            double adjustedMpg = baseMpg <= 0 ? 0 : baseMpg * (1 - Math.min(.25, Math.max(0, payload) / 100.0 * .01));
            double gallons = adjustedMpg > 0 ? roundMiles / adjustedMpg : 0;

            EditText avgField = exactField(root, "PREPARED GAS $ / GAL");
            EditText conservativeField = findTaggedEditText(root, "conservative_gas_field");
            EditText snacksField = exactField(root, "SNACKS & DRINKS");
            EditText extrasField = exactField(root, "OTHER TRIP MONEY");
            CheckBox includeHotel = findCheckBoxContaining(root, "Include linked hotel");

            double averagePrice = number(text(avgField));
            double conservativePrice = number(text(conservativeField));
            double snacks = number(text(snacksField));
            double extras = number(text(extrasField));
            double hotelCost = includeHotel != null && includeHotel.isChecked() ? number(hotel.optString("cost", "0")) : 0;

            double fixedCosts = snacks + extras + hotelCost;
            double averageGasCost = gallons * averagePrice;
            double averageTotal = fixedCosts + averageGasCost;

            StringBuilder s = new StringBuilder();
            s.append("FUEL BUDGET COMPARISON\n");
            s.append("Round trip: ").append(one(roundMiles)).append(" mi • ").append(one(gallons)).append(" gal needed\n\n");
            s.append("AUTOMATIC / ROUTE AVERAGE\n");
            s.append(money(averagePrice)).append("/gal • gas ").append(money(averageGasCost)).append("\n");
            s.append("Full trip: ").append(money(averageTotal)).append("\n\n");
            s.append("YOUR CONSERVATIVE PRICE\n");
            if (conservativePrice > 0) {
                double conservativeGas = gallons * conservativePrice;
                double conservativeTotal = fixedCosts + conservativeGas;
                s.append(money(conservativePrice)).append("/gal • gas ").append(money(conservativeGas)).append("\n");
                s.append("Full trip: ").append(money(conservativeTotal));
            } else {
                s.append("Enter a conservative $/gal price to see the second scenario.");
            }
            summary.setText(s.toString());
        } catch (Exception ignored) { }
    }

    private void attachRefreshWatcher(EditText field) {
        if (field == null) return;
        Object tag = field.getTag();
        if (tag != null && tag.toString().contains("dual_refresh")) return;
        field.setTag((tag == null ? "" : tag.toString()) + " dual_refresh");
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                field.postDelayed(() -> updateDualSummary(getWindow().getDecorView()), 120);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private JSONObject activeTrip() {
        return profileById("trips", prefs.getString("activeTripId", ""));
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

    private EditText findTaggedEditText(View root, String tag) {
        ArrayList<EditText> fields = new ArrayList<>(); collect(root, EditText.class, fields);
        for (EditText e : fields) if (tag.equals(e.getTag())) return e;
        return null;
    }

    private TextView findTaggedTextView(View root, String tag) {
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

    private CheckBox findCheckBoxContaining(View root, String target) {
        ArrayList<CheckBox> boxes = new ArrayList<>(); collect(root, CheckBox.class, boxes);
        for (CheckBox b : boxes) if (b.getText() != null && b.getText().toString().contains(target)) return b;
        return null;
    }

    private <T extends View> void collect(View v, Class<T> cls, List<T> out) {
        if (cls.isInstance(v)) out.add(cls.cast(v));
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), cls, out);
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

    private String selectedProfileId(String key, int index) {
        if (index <= 0) return "";
        JSONArray a = profiles(key);
        JSONObject o = a.optJSONObject(index - 1);
        return o == null ? "" : o.optString("id", "");
    }

    private String text(EditText e) { return e == null || e.getText() == null ? "" : e.getText().toString(); }
    private double number(String s) { try { return Double.parseDouble(s == null ? "" : s.trim()); } catch (Exception e) { return 0; } }
    private String one(double d) { return String.format(Locale.US, "%.1f", d); }
    private String money(double d) { return NumberFormat.getCurrencyInstance(Locale.US).format(d); }
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }
}
