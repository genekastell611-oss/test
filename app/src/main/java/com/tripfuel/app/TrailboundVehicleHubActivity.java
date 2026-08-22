package com.tripfuel.app;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Trailbound 6.0 UI/readiness layer.
 * Turns the Cars screen into a vehicle hub, adds persistent pre-trip safety
 * checks, preserves extension fields through older save routines, and applies
 * small Android UI polish without changing the stable profile storage format.
 */
public class TrailboundVehicleHubActivity extends TrailboundRouteGasRefreshActivity {
    private static final String PREFS = "trailbound_v5";
    private static final String VEHICLES = "vehicles";
    private static final String HOTELS = "hotels";
    private static final String TRIPS = "trips";

    private SharedPreferences prefs;
    private boolean patching;
    private final Set<Button> protectedTripSaveButtons = Collections.newSetFromMap(new WeakHashMap<Button, Boolean>());
    private final Set<Button> protectedVehicleSaveButtons = Collections.newSetFromMap(new WeakHashMap<Button, Boolean>());
    private final Set<Button> hotelDestinationButtons = Collections.newSetFromMap(new WeakHashMap<Button, Boolean>());

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (!patching && !isFinishing()) patchVisibleUi();
        });
        getWindow().getDecorView().postDelayed(this::patchVisibleUi, 450);
    }

    @Override protected void onResume() {
        super.onResume();
        getWindow().getDecorView().postDelayed(this::patchVisibleUi, 350);
    }

    private void patchVisibleUi() {
        if (patching || isFinishing()) return;
        patching = true;
        try {
            View root = getWindow().getDecorView();
            polishCommonUi(root);
            if (findExactText(root, "Vehicle profile") != null) patchVehicleHub(root);
            if (findExactText(root, "Trip profile") != null) patchTripSafety(root);
        } catch (Exception ignored) {
        } finally {
            patching = false;
        }
    }

    private void polishCommonUi(View root) {
        ArrayList<Spinner> spinners = new ArrayList<>();
        collect(root, Spinner.class, spinners);
        for (Spinner spinner : spinners) {
            View selected = spinner.getSelectedView();
            if (selected instanceof TextView) {
                TextView t = (TextView) selected;
                t.setTextColor(Color.WHITE);
                t.setTextSize(15);
            }
            try {
                spinner.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(104, 115, 79)));
                spinner.setPopupBackgroundDrawable(round(Color.rgb(28, 32, 24), 12, Color.rgb(106, 116, 83)));
            } catch (Exception ignored) { }
        }

        for (String nav : new String[]{"Trip", "Cars", "Hotels", "Area"}) {
            Button b = findButton(root, nav);
            if (b != null) {
                b.setTextSize(14);
                b.setMinHeight(dp(54));
            }
        }
    }

    private void patchVehicleHub(View root) {
        TextView header = findExactText(root, "Vehicle profile");
        if (header == null || !(header.getParent() instanceof LinearLayout)) return;
        LinearLayout card = (LinearLayout) header.getParent();

        TextView note = nextTextView(card, header);
        if (note != null) {
            note.setText("Your saved vehicle hub: mileage, maintenance, payload-adjusted MPG, linked adventure and pre-trip readiness in one place.");
        }

        hideLegacyVehicleSummary(card);

        LinearLayout hub = (LinearLayout) card.findViewWithTag("vehicle_hub_panel");
        if (hub == null) {
            hub = new LinearLayout(this);
            hub.setTag("vehicle_hub_panel");
            hub.setOrientation(LinearLayout.VERTICAL);
            hub.setPadding(dp(14), dp(14), dp(14), dp(14));
            hub.setBackground(round(Color.rgb(20, 27, 18), 18, Color.rgb(117, 139, 84)));

            TextView title = label("VEHICLE HUB", 12, true);
            title.setTextColor(Color.rgb(199, 226, 154));
            hub.addView(title);

            TextView overview = block("");
            overview.setTag("vehicle_hub_overview");
            hub.addView(overview, topMargin(-1, -2, 8));

            TextView linked = block("");
            linked.setTag("vehicle_hub_linked");
            hub.addView(linked, topMargin(-1, -2, 8));

            int insert = afterFirstImage(card);
            card.addView(hub, Math.max(0, Math.min(insert, card.getChildCount())), topMargin(-1, -2, 10));
        }

        LinearLayout safety = (LinearLayout) card.findViewWithTag("vehicle_safety_section");
        if (safety == null) {
            safety = buildSafetySection(root);
            Button save = firstVehicleSaveButton(root);
            int insert = save != null && save.getParent() == card ? card.indexOfChild(save) : card.getChildCount();
            card.addView(safety, Math.max(0, insert), topMargin(-1, -2, 14));
        }

        protectVehicleSave(root);
        updateVehicleHub(root);
        updateSafetyStatus(root);
    }

    private LinearLayout buildSafetySection(View root) {
        LinearLayout section = new LinearLayout(this);
        section.setTag("vehicle_safety_section");
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(14), dp(14), dp(14), dp(14));
        section.setBackground(round(Color.rgb(31, 29, 20), 18, Color.rgb(153, 126, 78)));

        TextView title = label("PRE-TRIP SAFETY CHECK", 17, true);
        title.setTextColor(Color.rgb(255, 236, 190));
        section.addView(title);

        TextView note = label("Check these before departure. This is a planning aid—follow your owner's manual and use professional service when needed.", 12, false);
        note.setTextColor(Color.rgb(229, 223, 207));
        section.addView(note, topMargin(-1, -2, 5));

        TextView status = block("");
        status.setTag("vehicle_safety_status");
        section.addView(status, topMargin(-1, -2, 10));

        JSONObject saved = readSafety();
        addSafetyBox(section, "tires", "Tire pressures and visible tire condition", saved.optBoolean("tires", false));
        addSafetyBox(section, "tread", "Tread, spare/inflator and jack/tools", saved.optBoolean("tread", false));
        addSafetyBox(section, "fluids", "Engine oil, coolant and washer fluid", saved.optBoolean("fluids", false));
        addSafetyBox(section, "brakes", "Brakes feel normal; no brake/critical warning lights", saved.optBoolean("brakes", false));
        addSafetyBox(section, "lights", "Headlights, brake lights and turn signals", saved.optBoolean("lights", false));
        addSafetyBox(section, "wipers", "Wipers, washer spray and windshield condition", saved.optBoolean("wipers", false));
        addSafetyBox(section, "battery", "Battery/startup/charging behavior looks normal", saved.optBoolean("battery", false));
        addSafetyBox(section, "emergency", "Emergency kit, charger and basic roadside gear", saved.optBoolean("emergency", false));
        addSafetyBox(section, "documents", "License, registration, insurance and roadside info", saved.optBoolean("documents", false));

        Button reset = new Button(this);
        reset.setText("Reset checklist for next trip");
        reset.setAllCaps(false);
        reset.setTextColor(Color.WHITE);
        reset.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        reset.setBackground(round(Color.rgb(104, 75, 47), 14, Color.rgb(188, 151, 103)));
        section.addView(reset, topMargin(-1, dp(52), 10));
        reset.setOnClickListener(v -> {
            prefs.edit().remove(safetyKey(currentVehicleStorageId())).apply();
            ArrayList<CheckBox> boxes = new ArrayList<>();
            collect(section, CheckBox.class, boxes);
            for (CheckBox box : boxes) box.setChecked(false);
            updateSafetyStatus(getWindow().getDecorView());
        });
        return section;
    }

    private void addSafetyBox(LinearLayout parent, String key, String text, boolean checked) {
        CheckBox box = new CheckBox(this);
        box.setTag("vehicle_safety_" + key);
        box.setText(text);
        box.setTextColor(Color.WHITE);
        box.setTextSize(14);
        box.setChecked(checked);
        box.setButtonTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{Color.rgb(168, 204, 111), Color.rgb(157, 150, 126)}));
        parent.addView(box, topMargin(-1, -2, 4));
        box.setOnCheckedChangeListener((buttonView, isChecked) -> {
            JSONObject state = readSafety();
            try {
                state.put(key, isChecked);
                state.put("updatedAt", System.currentTimeMillis());
                prefs.edit().putString(safetyKey(currentVehicleStorageId()), state.toString()).apply();
            } catch (Exception ignored) { }
            updateSafetyStatus(getWindow().getDecorView());
        });
    }

    private void updateVehicleHub(View root) {
        TextView overview = findTaggedText(root, "vehicle_hub_overview");
        TextView linked = findTaggedText(root, "vehicle_hub_linked");
        if (overview == null || linked == null) return;

        String id = prefs.getString("activeVehicleId", "");
        JSONObject vehicle = profileById(VEHICLES, id);
        String year = text(exactField(root, "YEAR"), vehicle.optString("year", ""));
        String make = text(exactField(root, "MAKE"), vehicle.optString("make", ""));
        String model = text(exactField(root, "MODEL"), vehicle.optString("model", ""));
        double mpg = number(text(exactField(root, "EPA COMBINED MPG"), vehicle.optString("mpg", "0")));
        double odometer = number(text(exactField(root, "CURRENT MILEAGE"), vehicle.optString("odometer", "0")));
        double due = number(text(exactField(root, "NEXT OIL CHANGE DUE AT"), vehicle.optString("nextOil", "0")));
        double payload = number(text(exactField(root, "TRIP PAYLOAD (LB)"), vehicle.optString("payload", "0")));
        Spinner fuel = exactSpinner(root, "FUEL TYPE");
        String fuelType = fuel != null && fuel.getSelectedItem() != null ? String.valueOf(fuel.getSelectedItem()) : vehicle.optString("fuelType", "Regular");
        double adjusted = adjustedMpg(mpg, payload);

        JSONObject trip = linkedTripForVehicle(id);
        double roundMiles = roundMiles(trip);
        double oilNow = oilPct(odometer, due);
        double oilAfter = oilPct(odometer + roundMiles, due);
        long milesToService = Math.round(due - odometer);

        String vehicleName = (year + " " + make + " " + model).trim();
        if (vehicleName.isEmpty()) vehicleName = "Unsaved vehicle";
        StringBuilder o = new StringBuilder();
        o.append(vehicleName).append("\n");
        o.append(Math.round(odometer)).append(" mi odometer • ").append(fuelType).append("\n");
        o.append(one(mpg)).append(" EPA MPG → ").append(one(adjusted)).append(" estimated loaded MPG");
        if (payload > 0) o.append(" with ").append(Math.round(payload)).append(" lb payload");
        o.append("\nOil life: ").append(Math.round(oilNow)).append("% now");
        if (roundMiles > 0) o.append(" → ").append(Math.round(oilAfter)).append("% after trip");
        if (due > 0) o.append("\nNext oil service: ").append(milesToService > 0 ? milesToService + " mi remaining" : "due now");
        overview.setText(o.toString());

        if (trip.optString("id", "").isEmpty()) {
            linked.setText("LINKED ADVENTURE\nNo saved trip is currently linked to this vehicle. Choose this car on a Trip profile to project post-trip mileage, oil life and fuel stops.");
        } else {
            JSONObject hotel = profileById(HOTELS, trip.optString("hotelId", ""));
            String destination = trip.optString("end", trip.optString("label", "Saved trip"));
            linked.setText("LINKED ADVENTURE\n" + destination +
                    (roundMiles > 0 ? " • " + one(roundMiles) + " mi round trip" : "") +
                    "\nHotel: " + hotel.optString("label", "No hotel linked") +
                    (roundMiles > 0 ? "\nProjected odometer after trip: " + Math.round(odometer + roundMiles) + " mi" : ""));
        }
    }

    private void updateSafetyStatus(View root) {
        TextView status = findTaggedText(root, "vehicle_safety_status");
        if (status == null) return;
        JSONObject state = readSafety();
        String[] keys = new String[]{"tires", "tread", "fluids", "brakes", "lights", "wipers", "battery", "emergency", "documents"};
        int done = 0;
        for (String key : keys) if (state.optBoolean(key, false)) done++;

        String id = prefs.getString("activeVehicleId", "");
        JSONObject vehicle = profileById(VEHICLES, id);
        double od = number(vehicle.optString("odometer", "0"));
        double due = number(vehicle.optString("nextOil", "0"));
        JSONObject trip = linkedTripForVehicle(id);
        double miles = roundMiles(trip);
        ArrayList<String> warnings = new ArrayList<>();
        if (due > 0 && due <= od) warnings.add("oil service is due now");
        else if (due > 0 && miles > 0 && due <= od + miles) warnings.add("oil service comes due before you return");
        else if (due > 0 && oilPct(od + miles, due) < 20 && miles > 0) warnings.add("oil life will be low after the trip");

        if (done == keys.length && warnings.isEmpty()) {
            status.setText("READY CHECK • 9/9 safety items complete" + (miles > 0 ? " • maintenance projection looks okay for the mapped trip" : ""));
            status.setBackground(round(Color.rgb(32, 65, 39), 14, Color.rgb(120, 184, 109)));
        } else {
            StringBuilder s = new StringBuilder();
            s.append("READINESS • ").append(done).append("/9 checks complete");
            if (done < keys.length) s.append(" • ").append(keys.length - done).append(" remaining");
            if (!warnings.isEmpty()) {
                s.append("\nReview before departure: ");
                for (int i = 0; i < warnings.size(); i++) {
                    if (i > 0) s.append("; ");
                    s.append(warnings.get(i));
                }
            }
            if (id.isEmpty()) s.append("\nSave the vehicle profile so this checklist is tied to the car.");
            status.setText(s.toString());
            status.setBackground(round(warnings.isEmpty() ? Color.rgb(75, 59, 31) : Color.rgb(83, 43, 31), 14,
                    warnings.isEmpty() ? Color.rgb(187, 151, 87) : Color.rgb(205, 117, 84)));
        }
    }

    private void patchTripSafety(View root) {
        protectTripSave(root);
        Button hotelDestination = findButton(root, "Use linked hotel as destination");
        if (hotelDestination != null && !hotelDestinationButtons.contains(hotelDestination)) {
            hotelDestinationButtons.add(hotelDestination);
            hotelDestination.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    v.postDelayed(() -> {
                        Button refresh = findButton(getWindow().getDecorView(), "Refresh route + automatic gas average");
                        if (refresh != null) refresh.performClick();
                    }, 350);
                }
                return false;
            });
        }
    }

    private void protectTripSave(View root) {
        Button save = findButton(root, "Update this trip profile");
        if (save == null) save = findButton(root, "Save as new trip profile");
        if (save == null || protectedTripSaveButtons.contains(save)) return;
        protectedTripSaveButtons.add(save);
        save.setOnTouchListener((v, event) -> {
            if (event.getAction() != MotionEvent.ACTION_UP) return false;
            String beforeId = prefs.getString("activeTripId", "");
            JSONObject before = profileById(TRIPS, beforeId);
            String tank = text(exactField(root, "Tank size (gal)"), prefs.getString("fuelTankSize", ""));
            String depart = text(exactField(root, "Departure gas (gal)"), prefs.getString("departureFuel", ""));
            EditText conservativeField = findTaggedEditText(root, "conservative_gas_field");
            String conservative = conservativeField == null ? before.optString("conservativeGas", "") : value(conservativeField);
            v.postDelayed(() -> repairTripAfterBaseSave(before, tank, depart, conservative), 350);
            return false;
        });
    }

    private void repairTripAfterBaseSave(JSONObject before, String tank, String depart, String conservative) {
        try {
            String id = prefs.getString("activeTripId", "");
            if (id.isEmpty()) return;
            JSONObject current = profileById(TRIPS, id);
            JSONObject merged = current.length() == 0 ? new JSONObject() : new JSONObject(current.toString());
            if (before != null) {
                Iterator<String> keys = before.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    if (!merged.has(key)) merged.put(key, before.opt(key));
                }
            }
            merged.put("id", id);
            if (tank != null && !tank.trim().isEmpty()) merged.put("tankSize", tank.trim());
            if (depart != null && !depart.trim().isEmpty()) merged.put("departureFuel", depart.trim());
            if (conservative != null) merged.put("conservativeGas", conservative.trim());
            upsert(TRIPS, merged);
            prefs.edit().putString("fuelTankSize", tank == null ? "" : tank)
                    .putString("departureFuel", depart == null ? "" : depart).apply();
        } catch (Exception ignored) { }
    }

    private void protectVehicleSave(View root) {
        Button save = firstVehicleSaveButton(root);
        if (save == null || protectedVehicleSaveButtons.contains(save)) return;
        protectedVehicleSaveButtons.add(save);
        save.setOnTouchListener((v, event) -> {
            if (event.getAction() != MotionEvent.ACTION_UP) return false;
            String beforeId = prefs.getString("activeVehicleId", "");
            if (beforeId.isEmpty()) {
                String draftSafety = prefs.getString(safetyKey("draft"), "");
                v.postDelayed(() -> {
                    String newId = prefs.getString("activeVehicleId", "");
                    if (!newId.isEmpty() && !draftSafety.isEmpty()) {
                        prefs.edit().putString(safetyKey(newId), draftSafety).remove(safetyKey("draft")).apply();
                    }
                    updateVehicleHub(getWindow().getDecorView());
                    updateSafetyStatus(getWindow().getDecorView());
                }, 350);
            }
            return false;
        });
    }

    private Button firstVehicleSaveButton(View root) {
        Button b = findButton(root, "Update vehicle profile");
        return b != null ? b : findButton(root, "Save new vehicle profile");
    }

    private JSONObject linkedTripForVehicle(String vehicleId) {
        JSONObject active = profileById(TRIPS, prefs.getString("activeTripId", ""));
        if (!vehicleId.isEmpty() && vehicleId.equals(active.optString("vehicleId", ""))) return active;
        JSONArray trips = profiles(TRIPS);
        for (int i = 0; i < trips.length(); i++) {
            JSONObject trip = trips.optJSONObject(i);
            if (trip != null && vehicleId.equals(trip.optString("vehicleId", ""))) return trip;
        }
        return new JSONObject();
    }

    private double roundMiles(JSONObject trip) {
        double out = number(trip.optString("outMiles", "0"));
        double back = number(trip.optString("backMiles", "0"));
        if (back <= 0 && out > 0) back = out;
        return Math.max(0, out + back);
    }

    private double adjustedMpg(double base, double pounds) {
        if (base <= 0) return 0;
        return base * (1.0 - Math.min(0.25, Math.max(0, pounds) / 100.0 * 0.01));
    }

    private double oilPct(double mileage, double due) {
        if (due <= mileage) return 0;
        return Math.max(0, Math.min(100, (due - mileage) / 5000.0 * 100.0));
    }

    private JSONObject readSafety() {
        try { return new JSONObject(prefs.getString(safetyKey(currentVehicleStorageId()), "{}")); }
        catch (Exception e) { return new JSONObject(); }
    }

    private String currentVehicleStorageId() {
        String id = prefs.getString("activeVehicleId", "");
        return id.isEmpty() ? "draft" : id;
    }

    private String safetyKey(String vehicleId) {
        return "vehicleSafety_" + (vehicleId == null || vehicleId.isEmpty() ? "draft" : vehicleId.replaceAll("[^A-Za-z0-9_-]", ""));
    }

    private void hideLegacyVehicleSummary(LinearLayout card) {
        ArrayList<TextView> texts = new ArrayList<>();
        collect(card, TextView.class, texts);
        for (TextView t : texts) {
            String value = t.getText() == null ? "" : t.getText().toString();
            if (value.contains("EPA MPG:") && value.contains("Oil life estimate:")) {
                t.setVisibility(View.GONE);
            }
        }
    }

    private int afterFirstImage(LinearLayout card) {
        for (int i = 0; i < card.getChildCount(); i++) {
            if (card.getChildAt(i) instanceof ImageView) return i + 1;
        }
        return Math.min(2, card.getChildCount());
    }

    private TextView nextTextView(LinearLayout parent, View after) {
        int start = parent.indexOfChild(after) + 1;
        for (int i = start; i < parent.getChildCount(); i++) {
            if (parent.getChildAt(i) instanceof TextView) return (TextView) parent.getChildAt(i);
        }
        return null;
    }

    private TextView block(String text) {
        TextView t = label(text, 14, false);
        t.setTextColor(Color.WHITE);
        t.setLineSpacing(0, 1.12f);
        t.setPadding(dp(12), dp(11), dp(12), dp(11));
        t.setBackground(round(Color.rgb(10, 14, 9), 14, Color.rgb(87, 104, 69)));
        return t;
    }

    private TextView label(String text, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(Color.WHITE);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private LinearLayout.LayoutParams topMargin(int w, int h, int marginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.topMargin = dp(marginDp);
        return lp;
    }

    private GradientDrawable round(int fill, int radius, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(1), stroke);
        return g;
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

    private Spinner exactSpinner(View root, String label) {
        TextView l = findExactText(root, label);
        if (l == null || !(l.getParent() instanceof ViewGroup)) return null;
        ViewGroup p = (ViewGroup) l.getParent();
        int start = p.indexOfChild(l) + 1;
        for (int i = start; i < p.getChildCount(); i++) {
            View child = p.getChildAt(i);
            if (child instanceof Spinner) return (Spinner) child;
            if (child instanceof TextView) break;
        }
        return null;
    }

    private EditText findTaggedEditText(View root, String tag) {
        ArrayList<EditText> fields = new ArrayList<>();
        collect(root, EditText.class, fields);
        for (EditText field : fields) if (tag.equals(field.getTag())) return field;
        return null;
    }

    private TextView findTaggedText(View root, String tag) {
        ArrayList<TextView> views = new ArrayList<>();
        collect(root, TextView.class, views);
        for (TextView view : views) if (tag.equals(view.getTag())) return view;
        return null;
    }

    private Button findButton(View root, String text) {
        ArrayList<Button> buttons = new ArrayList<>();
        collect(root, Button.class, buttons);
        for (Button b : buttons) {
            if (b.getText() != null && text.equalsIgnoreCase(b.getText().toString().trim())) return b;
        }
        return null;
    }

    private TextView findExactText(View root, String text) {
        ArrayList<TextView> views = new ArrayList<>();
        collect(root, TextView.class, views);
        for (TextView t : views) {
            if (t.getText() != null && text.equalsIgnoreCase(t.getText().toString().trim())) return t;
        }
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

    private void upsert(String key, JSONObject profile) throws Exception {
        JSONArray input = profiles(key);
        JSONArray output = new JSONArray();
        boolean replaced = false;
        for (int i = 0; i < input.length(); i++) {
            JSONObject o = input.optJSONObject(i);
            if (o == null) continue;
            if (profile.optString("id", "").equals(o.optString("id", ""))) {
                output.put(profile);
                replaced = true;
            } else {
                output.put(o);
            }
        }
        if (!replaced) output.put(profile);
        if (!prefs.edit().putString(key, output.toString()).commit()) throw new Exception("storage");
    }

    private String text(EditText field, String fallback) {
        String value = field == null || field.getText() == null ? "" : field.getText().toString().trim();
        return value.isEmpty() ? fallback : value;
    }

    private String value(EditText field) {
        return field == null || field.getText() == null ? "" : field.getText().toString();
    }

    private double number(String value) {
        try { return Double.parseDouble(value == null ? "" : value.trim()); }
        catch (Exception e) { return 0; }
    }

    private String one(double value) { return String.format(Locale.US, "%.1f", value); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }

    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_SHORT).show(); }
}
