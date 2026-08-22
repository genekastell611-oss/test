package com.tripfuel.app;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
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
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.Polyline;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Trailbound 7.0 app-forward presentation layer over the audited planner. */
public class TrailboundPolishedActivity extends TrailboundIntegrityActivity {
    private static final String PREFS = "trailbound_v5";
    private static final String TRIPS = "trips";
    private static final String VEHICLES = "vehicles";
    private static final String HOTELS = "hotels";

    private static final int SURFACE = Color.rgb(28, 36, 24);
    private static final int DEEP = Color.rgb(14, 19, 13);
    private static final int BORDER = Color.rgb(91, 109, 73);
    private static final int GREEN = Color.rgb(105, 139, 76);
    private static final int GREEN_DARK = Color.rgb(68, 93, 50);
    private static final int CREAM = Color.rgb(247, 239, 220);
    private static final int MUTED = Color.rgb(204, 201, 187);
    private static final int GOLD = Color.rgb(224, 169, 78);
    private static final int BLUE = Color.rgb(70, 132, 171);
    private static final int RED = Color.rgb(196, 78, 66);

    private SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private final Map<String, Drawable> markerIcons = new HashMap<>();
    private final Set<Button> wiredButtons = Collections.newSetFromMap(new WeakHashMap<Button, Boolean>());
    private boolean patching;
    private String attemptedHotelPhoto = "";
    private String attemptedAreaBrief = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (!patching && !isFinishing()) polish();
        });
        main.postDelayed(this::polish, 650);
    }

    @Override protected void onResume() {
        super.onResume();
        main.postDelayed(this::polish, 350);
    }

    private void polish() {
        if (patching || isFinishing()) return;
        patching = true;
        try {
            View root = getWindow().getDecorView();
            polishShell(root);
            if (findExactText(root, "Trip profile") != null) polishTrip(root);
            else if (findExactText(root, "Vehicle profile") != null) polishVehicle(root);
            else if (findExactText(root, "Hotel profile") != null) polishHotel(root);
            else if (findExactText(root, "Linked adventure") != null) polishArea(root);
        } catch (Exception ignored) {
            // UI polish must never be able to crash the audited planner.
        } finally {
            patching = false;
        }
    }

    // ---------- app shell ----------

    private void polishShell(View root) {
        TextView appTitle = findTaggedText(root, "polish_title");
        if (appTitle == null) {
            appTitle = findExactText(root, "TRAILBOUND");
            if (appTitle != null) appTitle.setTag("polish_title");
        }
        if (appTitle != null) {
            appTitle.setText("Trailbound");
            appTitle.setTextSize(27);
            appTitle.setTextColor(Color.WHITE);
            appTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }

        TextView subtitle = findTaggedText(root, "polish_subtitle");
        if (subtitle == null) {
            subtitle = findExactText(root, "Adventure trip planner");
            if (subtitle != null) subtitle.setTag("polish_subtitle");
        }
        if (subtitle != null) {
            if (findExactText(root, "Trip profile") != null) subtitle.setText("Route • budget • fuel • stops");
            else if (findExactText(root, "Vehicle profile") != null) subtitle.setText("Garage • maintenance • readiness");
            else if (findExactText(root, "Hotel profile") != null) subtitle.setText("Stay • area • nearby essentials");
            else subtitle.setText("Adventure intelligence");
            subtitle.setTextColor(MUTED);
        }

        Button plan = dockButton(root, "Trip", "dock_plan", "🧭\nPlan");
        Button garage = dockButton(root, "Cars", "dock_garage", "🚙\nGarage");
        Button stay = dockButton(root, "Hotels", "dock_stay", "🏨\nStay");
        Button explore = dockButton(root, "Area", "dock_explore", "📍\nExplore");
        Button active = findExactText(root, "Trip profile") != null ? plan :
                findExactText(root, "Vehicle profile") != null ? garage :
                        findExactText(root, "Hotel profile") != null ? stay : explore;
        Button[] dock = new Button[]{plan, garage, stay, explore};
        LinearLayout dockParent = null;
        for (Button b : dock) if (b != null && b.getParent() instanceof LinearLayout) { dockParent = (LinearLayout) b.getParent(); break; }
        if (dockParent != null) {
            dockParent.setPadding(dp(5), dp(5), dp(5), dp(5));
            dockParent.setBackground(round(Color.rgb(15, 20, 14), 20, Color.rgb(78, 95, 62)));
        }
        for (Button b : dock) {
            if (b == null) continue;
            b.setGravity(Gravity.CENTER);
            b.setTextSize(12);
            b.setTextColor(Color.WHITE);
            b.setAllCaps(false);
            b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            if (b.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) b.getLayoutParams();
                lp.height = dp(60);
                lp.setMargins(dp(2), 0, dp(2), 0);
                b.setLayoutParams(lp);
            }
            b.setBackground(round(b == active ? GREEN_DARK : Color.TRANSPARENT, 15,
                    b == active ? Color.rgb(151, 178, 112) : Color.TRANSPARENT));
        }

        ArrayList<EditText> fields = new ArrayList<>();
        collect(root, EditText.class, fields);
        for (EditText e : fields) {
            e.setTextColor(Color.WHITE);
            e.setHintTextColor(Color.rgb(157, 164, 148));
            e.setTextSize(16);
            e.setPadding(dp(13), 0, dp(13), 0);
            e.setBackground(round(DEEP, 14, Color.rgb(82, 101, 66)));
        }

        ArrayList<Spinner> spinners = new ArrayList<>();
        collect(root, Spinner.class, spinners);
        for (Spinner s : spinners) {
            try {
                s.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(113, 134, 88)));
                s.setPopupBackgroundDrawable(round(Color.rgb(25, 32, 21), 14, Color.rgb(91, 109, 73)));
            } catch (Exception ignored) { }
            View selected = s.getSelectedView();
            if (selected instanceof TextView) ((TextView) selected).setTextColor(Color.WHITE);
        }

        ArrayList<Button> buttons = new ArrayList<>();
        collect(root, Button.class, buttons);
        for (Button b : buttons) {
            Object tag = b.getTag();
            if (tag != null && tag.toString().startsWith("dock_")) continue;
            String text = b.getText() == null ? "" : b.getText().toString();
            b.setAllCaps(false);
            b.setTextColor(Color.WHITE);
            b.setTextSize(14);
            b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            b.setMinHeight(dp(48));
            boolean primary = text.contains("Save") || text.contains("Refresh route") || text.contains("Find hotel") ||
                    text.contains("Choose exact EPA") || text.contains("Refresh destination");
            boolean secondary = text.contains("another") || text.contains("new trip") || text.contains("Reset checklist");
            int fill = primary ? GREEN_DARK : secondary ? Color.rgb(74, 57, 40) : Color.rgb(39, 48, 33);
            int stroke = primary ? Color.rgb(151, 178, 112) : secondary ? Color.rgb(143, 108, 74) : BORDER;
            b.setBackground(round(fill, 14, stroke));
        }
    }

    private Button dockButton(View root, String original, String tag, String newText) {
        Button b = findTaggedButton(root, tag);
        if (b == null) {
            b = findButton(root, original);
            if (b != null) b.setTag(tag);
        }
        if (b != null) b.setText(newText);
        return b;
    }

    // ---------- Plan hub ----------

    private void polishTrip(View root) {
        TextView header = findExactText(root, "Trip profile");
        if (header == null || !(header.getParent() instanceof LinearLayout)) return;
        LinearLayout card = (LinearLayout) header.getParent();
        hideIntro(card, header);
        card.setBackground(round(Color.argb(248, 17, 23, 15), 22, Color.rgb(78, 94, 63)));
        card.setPadding(dp(14), dp(14), dp(14), dp(16));

        LinearLayout hero = findTaggedLinear(root, "trip_dashboard");
        if (hero == null) {
            hero = dashboard("trip_dashboard", "ROAD TRIP");
            TextView route = bigText("Set your route"); route.setTag("trip_dash_route"); hero.addView(route, topMargin(-1, -2, 2));
            TextView status = body(""); status.setTag("trip_dash_status"); hero.addView(status, topMargin(-1, -2, 3));
            hero.addView(metricRow(metric("trip_dash_miles", "ROUND TRIP"), metric("trip_dash_time", "DRIVE TIME")), topMargin(-1, -2, 11));
            hero.addView(metricRow(metric("trip_dash_auto", "AUTO BUDGET"), metric("trip_dash_safe", "CONSERVATIVE")), topMargin(-1, -2, 7));
            TextView source = small(""); source.setTag("trip_dash_source"); hero.addView(source, topMargin(-1, -2, 8));
            card.addView(hero, Math.min(1, card.getChildCount()), topMargin(-1, -2, 5));
        }
        updateTripDashboard(root, hero);

        MapView map = firstMap(root);
        if (map != null) {
            LinearLayout.LayoutParams lp = map.getLayoutParams() instanceof LinearLayout.LayoutParams ?
                    (LinearLayout.LayoutParams) map.getLayoutParams() : new LinearLayout.LayoutParams(-1, dp(310));
            lp.height = dp(310); lp.topMargin = dp(10); lp.bottomMargin = dp(4); map.setLayoutParams(lp);
            map.setBackground(round(Color.rgb(17, 23, 16), 18, Color.rgb(87, 105, 70)));
            map.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            map.setClipToOutline(true);
            styleMap(root, map);
            ensureMapLegend(card, map);
        }

        Button rawMap = findButton(root, "Map actual round trip");
        if (rawMap != null) rawMap.setVisibility(View.GONE);
        Button refresh = findButton(root, "Refresh route + automatic gas average");
        if (refresh != null && !wiredButtons.contains(refresh)) {
            wiredButtons.add(refresh);
            refresh.setOnTouchListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                    main.postDelayed(this::polish, 1600);
                    main.postDelayed(this::polish, 3000);
                }
                return false;
            });
        }

        rewriteFuelPlan(root);
        TextView audited = findTaggedText(root, "audited_trip_summary");
        TextView dual = findTaggedText(root, "dual_gas_summary");
        boolean details = prefs.getBoolean("showTripAuditDetails", false);
        if (audited != null) audited.setVisibility(details ? View.VISIBLE : View.GONE);
        if (dual != null) dual.setVisibility(details ? View.VISIBLE : View.GONE);
        ensureDetailToggle(card, audited, dual);
    }

    private void updateTripDashboard(View root, LinearLayout hero) {
        JSONObject trip = activeTrip();
        String start = text(exactField(root, "FROM")).trim();
        String end = text(exactField(root, "TO")).trim();
        setText(findTaggedText(hero, "trip_dash_route"), shortPlace(start) + "  →  " + shortPlace(end));
        boolean current = !start.isEmpty() && !end.isEmpty() && start.equals(trip.optString("start", "")) && end.equals(trip.optString("end", ""));
        if (!current || !routeComplete(trip)) {
            setText(findTaggedText(hero, "trip_dash_status"), "Refresh the route before trusting mileage, fuel stops, or trip totals.");
            setMetric(findTaggedText(hero, "trip_dash_miles_value"), "—");
            setMetric(findTaggedText(hero, "trip_dash_time_value"), "—");
            setMetric(findTaggedText(hero, "trip_dash_auto_value"), "—");
            setMetric(findTaggedText(hero, "trip_dash_safe_value"), "—");
            setText(findTaggedText(hero, "trip_dash_source"), "Your saved profiles remain intact while the route refreshes.");
            return;
        }

        double out = positive(trip.optString("outMiles", "0"));
        double back = positive(trip.optString("backMiles", "0"));
        double roundMiles = out + back;
        double hours = positive(trip.optString("outHours", "0")) + positive(trip.optString("backHours", "0"));
        JSONObject vehicle = linkedVehicle(root, trip);
        JSONObject hotel = linkedHotel(root, trip);
        double mpg = TrailboundTripMath.adjustedMpg(positive(vehicle.optString("mpg", "0")), positive(vehicle.optString("payload", "0")));
        double gallons = TrailboundTripMath.gallonsConsumed(roundMiles, mpg);
        double avgPrice = positive(text(exactField(root, "PREPARED GAS $ / GAL")));
        double safePrice = positive(text(findTaggedEditText(root, "conservative_gas_field")));
        double snacks = positive(text(exactField(root, "SNACKS & DRINKS")));
        double extras = positive(text(exactField(root, "OTHER TRIP MONEY")));
        CheckBox includeHotel = findCheckBoxContaining(root, "Include linked hotel");
        boolean include = includeHotel != null && includeHotel.isChecked();
        double hotelCost = positive(hotel.optString("cost", "0"));
        double avgTotal = TrailboundTripMath.tripTotal(gallons * avgPrice, snacks, extras, hotelCost, include);
        double safeTotal = TrailboundTripMath.tripTotal(gallons * safePrice, snacks, extras, hotelCost, include);
        List<Double> stops = TrailboundTripMath.fuelStopMiles(roundMiles, mpg,
                positive(text(exactField(root, "Tank size (gal)"))), positive(text(exactField(root, "Departure gas (gal)"))));
        int outboundStops = 0, returnStops = 0;
        for (double mile : stops) { if (mile <= out) outboundStops++; else returnStops++; }

        setText(findTaggedText(hero, "trip_dash_status"), one(out) + " mi out • " + one(back) + " mi back • fuel " + outboundStops + " out / " + returnStops + " back");
        setMetric(findTaggedText(hero, "trip_dash_miles_value"), one(roundMiles) + " mi");
        setMetric(findTaggedText(hero, "trip_dash_time_value"), hours > 0 ? one(hours) + " hr" : "—");
        setMetric(findTaggedText(hero, "trip_dash_auto_value"), avgPrice > 0 ? money(avgTotal) : "—");
        setMetric(findTaggedText(hero, "trip_dash_safe_value"), safePrice > 0 ? money(safeTotal) : "Set price");
        String source = trip.optString("gasSource", "").trim();
        setText(findTaggedText(hero, "trip_dash_source"), (avgPrice > 0 ? "Automatic " + money(avgPrice) + "/gal" : "Automatic gas unavailable") + (source.isEmpty() ? "" : " • " + source));
    }

    private void styleMap(View root, MapView map) {
        JSONObject trip = activeTrip();
        if (!routeComplete(trip)) return;
        int line = 0;
        ArrayList<Overlay> remove = new ArrayList<>();
        for (Overlay overlay : map.getOverlays()) {
            if (overlay instanceof Polyline) {
                Polyline p = (Polyline) overlay;
                if (line == 0) { p.getOutlinePaint().setColor(GOLD); p.getOutlinePaint().setStrokeWidth(dp(5)); }
                else if (line == 1) { p.getOutlinePaint().setColor(BLUE); p.getOutlinePaint().setStrokeWidth(dp(4)); }
                line++;
            } else if (overlay instanceof Marker) {
                Marker m = (Marker) overlay;
                String title = m.getTitle() == null ? "" : m.getTitle();
                if ("Start / Return".equals(title)) { m.setIcon(markerIcon(GREEN, "S")); m.setSnippet("Trip start • final return point"); }
                else if ("Destination".equals(title)) { m.setIcon(markerIcon(RED, "D")); m.setSnippet("Destination • turnaround point"); }
                else if (title.toLowerCase(Locale.US).startsWith("fuel stop ")) remove.add(overlay);
            }
        }
        map.getOverlays().removeAll(remove);

        double out = positive(trip.optString("outMiles", "0"));
        double roundMiles = out + positive(trip.optString("backMiles", "0"));
        JSONObject vehicle = linkedVehicle(root, trip);
        double mpg = TrailboundTripMath.adjustedMpg(positive(vehicle.optString("mpg", "0")), positive(vehicle.optString("payload", "0")));
        double tank = positive(text(exactField(root, "Tank size (gal)")));
        double depart = positive(text(exactField(root, "Departure gas (gal)")));
        List<Double> stops = TrailboundTripMath.fuelStopMiles(roundMiles, mpg, tank, depart);
        ArrayList<GeoPoint> route = roundTripGeometry(trip);
        for (int i = 0; i < stops.size(); i++) {
            double mile = stops.get(i);
            GeoPoint point = pointAtFraction(route, roundMiles > 0 ? mile / roundMiles : 0);
            if (point == null) continue;
            boolean returning = mile > out;
            Marker m = new Marker(map);
            m.setPosition(point);
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            m.setIcon(markerIcon(returning ? BLUE : GOLD, "G"));
            m.setTitle("Fuel stop " + (i + 1) + (returning ? " • RETURN" : " • OUTBOUND"));
            m.setSnippet((returning ? "Return leg mile " + Math.round(mile - out) : "Outbound mile " + Math.round(mile)) + " • round-trip mile " + Math.round(mile));
            m.setRelatedObject("polished_fuel");
            map.getOverlays().add(m);
        }
        map.invalidate();
    }

    private void ensureMapLegend(LinearLayout card, MapView map) {
        if (card.findViewWithTag("map_legend") != null || map.getParent() != card) return;
        TextView legend = small("🟢 Start   🔴 Destination   🟠 Outbound fuel   🔵 Return fuel");
        legend.setTag("map_legend"); legend.setGravity(Gravity.CENTER);
        int index = card.indexOfChild(map);
        card.addView(legend, Math.min(index + 1, card.getChildCount()), topMargin(-1, -2, 3));
    }

    private void rewriteFuelPlan(View root) {
        TextView plan = findTaggedText(root, "trailbound_fuel_plan_text");
        JSONObject trip = activeTrip();
        if (plan == null || !routeComplete(trip)) return;
        double out = positive(trip.optString("outMiles", "0"));
        double roundMiles = out + positive(trip.optString("backMiles", "0"));
        JSONObject vehicle = linkedVehicle(root, trip);
        double mpg = TrailboundTripMath.adjustedMpg(positive(vehicle.optString("mpg", "0")), positive(vehicle.optString("payload", "0")));
        List<Double> stops = TrailboundTripMath.fuelStopMiles(roundMiles, mpg,
                positive(text(exactField(root, "Tank size (gal)"))), positive(text(exactField(root, "Departure gas (gal)"))));
        ArrayList<String> there = new ArrayList<>(), back = new ArrayList<>();
        for (double mile : stops) {
            if (mile <= out) there.add(mile < 1 ? "before departure" : "mile " + Math.round(mile));
            else back.add("mile " + Math.round(mile - out) + " after destination");
        }
        plan.setText("FUEL PLAN\nOUTBOUND • " + (there.isEmpty() ? "No planned fill-up" : join(there)) +
                "\nRETURN • " + (back.isEmpty() ? "No planned fill-up" : join(back)) + "\nReserve target: about 15% of tank.");
        plan.setBackground(round(Color.rgb(20, 27, 18), 15, Color.rgb(92, 111, 73)));
    }

    private void ensureDetailToggle(LinearLayout card, TextView audited, TextView dual) {
        View existing = card.findViewWithTag("trip_details_toggle");
        if (existing instanceof Button) {
            ((Button) existing).setText(prefs.getBoolean("showTripAuditDetails", false) ? "Hide calculation details" : "Show calculation details");
            return;
        }
        Button b = compactButton(prefs.getBoolean("showTripAuditDetails", false) ? "Hide calculation details" : "Show calculation details");
        b.setTag("trip_details_toggle");
        b.setOnClickListener(v -> {
            boolean show = !prefs.getBoolean("showTripAuditDetails", false);
            prefs.edit().putBoolean("showTripAuditDetails", show).apply();
            if (audited != null) audited.setVisibility(show ? View.VISIBLE : View.GONE);
            if (dual != null) dual.setVisibility(show ? View.VISIBLE : View.GONE);
            b.setText(show ? "Hide calculation details" : "Show calculation details");
        });
        card.addView(b, topMargin(-1, dp(48), 10));
    }

    // ---------- Garage hub ----------

    private void polishVehicle(View root) {
        TextView header = findExactText(root, "Vehicle profile");
        if (header == null || !(header.getParent() instanceof LinearLayout)) return;
        LinearLayout card = (LinearLayout) header.getParent();
        hideIntro(card, header);
        card.setBackground(round(Color.argb(248, 17, 23, 15), 22, Color.rgb(78, 94, 63)));

        ImageView vehiclePhoto = firstImage(card);
        if (vehiclePhoto != null) {
            LinearLayout.LayoutParams lp = vehiclePhoto.getLayoutParams() instanceof LinearLayout.LayoutParams ?
                    (LinearLayout.LayoutParams) vehiclePhoto.getLayoutParams() : new LinearLayout.LayoutParams(-1, dp(225));
            lp.height = dp(225); lp.topMargin = dp(7); lp.bottomMargin = dp(9); vehiclePhoto.setLayoutParams(lp);
            vehiclePhoto.setBackground(round(SURFACE, 20, BORDER));
            vehiclePhoto.setOutlineProvider(ViewOutlineProvider.BACKGROUND); vehiclePhoto.setClipToOutline(true);
        }

        LinearLayout oldHub = findTaggedLinear(root, "vehicle_hub_panel");
        if (oldHub != null) oldHub.setVisibility(View.GONE);

        LinearLayout dash = findTaggedLinear(root, "garage_dashboard");
        if (dash == null) {
            dash = dashboard("garage_dashboard", "GARAGE REPORT");
            TextView name = bigText("Your vehicle"); name.setTag("garage_name"); dash.addView(name, topMargin(-1, -2, 2));
            TextView state = body(""); state.setTag("garage_state"); dash.addView(state, topMargin(-1, -2, 3));
            dash.addView(metricRow(metric("garage_odometer", "ODOMETER"), metric("garage_service", "SERVICE")), topMargin(-1, -2, 11));
            dash.addView(metricRow(metric("garage_mpg", "LOADED MPG"), metric("garage_safety", "SAFETY")), topMargin(-1, -2, 7));
            TextView linked = small(""); linked.setTag("garage_linked"); dash.addView(linked, topMargin(-1, -2, 9));
            int insert = vehiclePhoto != null && vehiclePhoto.getParent() == card ? card.indexOfChild(vehiclePhoto) + 1 : Math.min(1, card.getChildCount());
            card.addView(dash, Math.max(0, insert), topMargin(-1, -2, 4));
        }
        updateGarage(root, dash);
        sectionBefore(root, card, "YEAR", "garage_vehicle_details", "VEHICLE DETAILS");
        sectionBefore(root, card, "CURRENT MILEAGE", "garage_maintenance", "MAINTENANCE & REPORTING");
        ensureServiceButton(root, card);

        LinearLayout safety = findTaggedLinear(root, "vehicle_safety_section");
        if (safety != null) safety.setBackground(round(Color.rgb(40, 34, 23), 18, Color.rgb(145, 117, 72)));
    }

    private void updateGarage(View root, LinearLayout dash) {
        JSONObject vehicle = activeVehicle();
        String year = text(exactField(root, "YEAR"), vehicle.optString("year", ""));
        String make = text(exactField(root, "MAKE"), vehicle.optString("make", ""));
        String model = text(exactField(root, "MODEL"), vehicle.optString("model", ""));
        String name = (year + " " + make + " " + model).trim();
        if (name.isEmpty()) name = "Unsaved vehicle";
        double od = positive(text(exactField(root, "CURRENT MILEAGE"), vehicle.optString("odometer", "0")));
        double due = positive(text(exactField(root, "NEXT OIL CHANGE DUE AT"), vehicle.optString("nextOil", "0")));
        double base = positive(text(exactField(root, "EPA COMBINED MPG"), vehicle.optString("mpg", "0")));
        double payload = positive(text(exactField(root, "TRIP PAYLOAD (LB)"), vehicle.optString("payload", "0")));
        double loaded = TrailboundTripMath.adjustedMpg(base, payload);
        int checks = 0, totalChecks = 0;
        ArrayList<CheckBox> boxes = new ArrayList<>(); collect(root, CheckBox.class, boxes);
        for (CheckBox box : boxes) {
            Object tag = box.getTag();
            if (tag != null && tag.toString().startsWith("vehicle_safety_")) { totalChecks++; if (box.isChecked()) checks++; }
        }
        JSONObject trip = linkedTrip(vehicle.optString("id", prefs.getString("activeVehicleId", "")));
        double roundMiles = positive(trip.optString("outMiles", "0")) + positive(trip.optString("backMiles", "0"));
        long remaining = Math.round(due - od);
        String service = due <= 0 ? "Not set" : remaining <= 0 ? "Due now" : remaining + " mi";
        String state = due > 0 && due <= od ? "Service due before departure" :
                due > 0 && roundMiles > 0 && due <= od + roundMiles ? "Service comes due during linked trip" : "Maintenance planning looks current";
        setText(findTaggedText(dash, "garage_name"), name);
        setText(findTaggedText(dash, "garage_state"), state);
        setMetric(findTaggedText(dash, "garage_odometer_value"), Math.round(od) + " mi");
        setMetric(findTaggedText(dash, "garage_service_value"), service);
        setMetric(findTaggedText(dash, "garage_mpg_value"), one(loaded));
        setMetric(findTaggedText(dash, "garage_safety_value"), totalChecks > 0 ? checks + "/" + totalChecks : "—");
        if (trip.optString("id", "").isEmpty()) setText(findTaggedText(dash, "garage_linked"), "No trip linked • payload " + Math.round(payload) + " lb");
        else setText(findTaggedText(dash, "garage_linked"), "Linked to " + shortPlace(trip.optString("end", "Trip")) +
                (roundMiles > 0 ? " • " + one(roundMiles) + " mi • post-trip odometer " + Math.round(od + roundMiles) + " mi" : ""));
    }

    private void ensureServiceButton(View root, LinearLayout card) {
        if (card.findViewWithTag("log_service_button") != null) return;
        EditText interval = findTaggedEditText(root, "oil_service_interval_field");
        if (interval == null || interval.getParent() != card) return;
        Button b = compactButton("Log oil/service completed now");
        b.setTag("log_service_button");
        b.setBackground(round(GREEN_DARK, 14, Color.rgb(151, 178, 112)));
        int index = card.indexOfChild(interval) + 1;
        card.addView(b, Math.min(index, card.getChildCount()), topMargin(-1, dp(50), 8));
        b.setOnClickListener(v -> confirmService());
    }

    private void confirmService() {
        View root = getWindow().getDecorView();
        double od = positive(text(exactField(root, "CURRENT MILEAGE")));
        double interval = positive(text(findTaggedEditText(root, "oil_service_interval_field")));
        if (od <= 0 || interval <= 0) { toast("Enter current mileage and service interval first"); return; }
        new AlertDialog.Builder(this)
                .setTitle("Record completed service?")
                .setMessage("Record service at " + Math.round(od) + " mi and set the next due mileage to " + Math.round(od + interval) + " mi?")
                .setPositiveButton("Record", (dialog, which) -> recordService(od, interval))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void recordService(double od, double interval) {
        String id = prefs.getString("activeVehicleId", "");
        String next = String.valueOf(Math.round(od + interval));
        EditText due = exactField(getWindow().getDecorView(), "NEXT OIL CHANGE DUE AT");
        if (due != null) due.setText(next);
        if (!id.isEmpty()) {
            JSONObject vehicle = profileById(VEHICLES, id);
            try {
                JSONArray history;
                try { history = new JSONArray(vehicle.optString("serviceHistory", "[]")); }
                catch (Exception e) { history = new JSONArray(); }
                JSONObject event = new JSONObject();
                event.put("type", "Oil/service completed");
                event.put("mileage", Math.round(od));
                event.put("timestamp", System.currentTimeMillis());
                history.put(event);
                saveField(VEHICLES, id, "serviceHistory", history.toString());
                saveField(VEHICLES, id, "lastServiceMileage", String.valueOf(Math.round(od)));
                saveField(VEHICLES, id, "lastServiceAt", System.currentTimeMillis());
                saveField(VEHICLES, id, "nextOil", next);
                saveField(VEHICLES, id, "oilInterval", String.valueOf(Math.round(interval)));
            } catch (Exception ignored) { }
        }
        toast("Service recorded • next due at " + next + " mi");
        main.postDelayed(this::polish, 200);
    }

    // ---------- Stay hub ----------

    private void polishHotel(View root) {
        TextView header = findExactText(root, "Hotel profile");
        if (header == null || !(header.getParent() instanceof LinearLayout)) return;
        LinearLayout card = (LinearLayout) header.getParent();
        hideIntro(card, header);
        card.setBackground(round(Color.argb(248, 17, 23, 15), 22, Color.rgb(78, 94, 63)));

        LinearLayout hub = findTaggedLinear(root, "stay_dashboard");
        if (hub == null) {
            hub = dashboard("stay_dashboard", "STAY HUB");
            ImageView photo = new ImageView(this);
            photo.setTag("stay_photo"); photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            photo.setBackground(round(SURFACE, 18, BORDER)); photo.setOutlineProvider(ViewOutlineProvider.BACKGROUND); photo.setClipToOutline(true);
            hub.addView(photo, topMargin(-1, dp(215), 7));
            TextView credit = small("No hotel/area photo cached yet"); credit.setTag("stay_photo_credit"); hub.addView(credit, topMargin(-1, -2, 4));
            TextView name = bigText("Your stay"); name.setTag("stay_name"); hub.addView(name, topMargin(-1, -2, 8));
            TextView address = body(""); address.setTag("stay_address"); hub.addView(address, topMargin(-1, -2, 2));
            hub.addView(metricRow(metric("stay_cost", "STAY COST"), metric("stay_nearby", "SAVED NEARBY")), topMargin(-1, -2, 10));
            TextView brief = body("Save a hotel to build local information."); brief.setTag("stay_area_brief"); brief.setLineSpacing(0, 1.12f); hub.addView(brief, topMargin(-1, -2, 9));
            LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
            Button photoButton = compactButton("Refresh photo"); Button areaButton = compactButton("Refresh area briefing");
            actions.addView(photoButton, weight()); actions.addView(areaButton, weight()); hub.addView(actions, topMargin(-1, dp(50), 8));
            photoButton.setOnClickListener(v -> loadStayPhoto(true));
            areaButton.setOnClickListener(v -> refreshAreaBrief(true));
            card.addView(hub, Math.min(1, card.getChildCount()), topMargin(-1, -2, 5));
        }
        updateStayHub(root, hub);

        MapView map = firstMap(root);
        if (map != null) {
            if (map.getParent() != hub) {
                if (map.getParent() instanceof ViewGroup) ((ViewGroup) map.getParent()).removeView(map);
                int photoIndex = childIndexByTag(hub, "stay_photo");
                hub.addView(map, Math.min(photoIndex + 2, hub.getChildCount()), topMargin(-1, dp(230), 8));
            }
            map.setBackground(round(Color.rgb(17, 23, 16), 18, BORDER));
            map.setOutlineProvider(ViewOutlineProvider.BACKGROUND); map.setClipToOutline(true);
            for (Overlay overlay : map.getOverlays()) if (overlay instanceof Marker) {
                Marker m = (Marker) overlay;
                if (m.getRelatedObject() == null && m.getTitle() != null) m.setIcon(markerIcon(RED, "H"));
            }
        }

        Button save = firstButton(root, "Save new hotel profile", "Update hotel profile", "Hotel saved ✓");
        if (save != null && !wiredButtons.contains(save)) {
            wiredButtons.add(save);
            save.setOnTouchListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                    main.postDelayed(this::polish, 500);
                    main.postDelayed(() -> loadStayPhoto(false), 800);
                }
                return false;
            });
        }
        loadStayPhoto(false);
        maybeAreaBrief();
    }

    private void updateStayHub(View root, LinearLayout hub) {
        JSONObject hotel = activeHotel();
        String name = text(exactField(root, "HOTEL NAME"), hotel.optString("name", "")).trim();
        String address = text(exactField(root, "HOTEL ADDRESS"), hotel.optString("address", "")).trim();
        double cost = positive(text(exactField(root, "TOTAL HOTEL PRICE"), hotel.optString("cost", "0")));
        int nearby = 0; try { nearby = new JSONArray(hotel.optString("nearbyPlaces", "[]")).length(); } catch (Exception ignored) { }
        setText(findTaggedText(hub, "stay_name"), name.isEmpty() ? "Your stay" : name);
        setText(findTaggedText(hub, "stay_address"), address.isEmpty() ? "Find and save a hotel address" : address);
        setMetric(findTaggedText(hub, "stay_cost_value"), cost > 0 ? money(cost) : "—");
        setMetric(findTaggedText(hub, "stay_nearby_value"), String.valueOf(nearby));
        String brief = hotel.optString("areaBriefing", "").trim();
        if (brief.isEmpty()) brief = "Your saved hotel becomes the local anchor for weather, food, fuel, shopping, medical needs, parks, attractions and practical area context.";
        setText(findTaggedText(hub, "stay_area_brief"), brief);
        String kind = hotel.optString("hotelPhotoKind", "");
        String license = hotel.optString("hotelPhotoLicense", "");
        String credit = hotel.optString("hotelPhotoCredit", "");
        if (!kind.isEmpty()) setText(findTaggedText(hub, "stay_photo_credit"), kind + (credit.isEmpty() ? "" : " • " + shorten(stripHtml(credit), 36)) + (license.isEmpty() ? "" : " • " + license));
    }

    private void loadStayPhoto(boolean userRequested) {
        View root = getWindow().getDecorView();
        ImageView image = findTaggedImage(root, "stay_photo");
        if (image == null) return;
        String id = prefs.getString("activeHotelId", "");
        JSONObject hotel = profileById(HOTELS, id);
        String storage = id.isEmpty() ? "draft" : id;
        File cached = new File(getFilesDir(), hotelImageFile(storage));
        if (cached.exists()) {
            Bitmap bitmap = BitmapFactory.decodeFile(cached.getAbsolutePath());
            if (bitmap != null) image.setImageBitmap(bitmap);
            return;
        }
        String name = text(exactField(root, "HOTEL NAME"), hotel.optString("name", "")).trim();
        String address = text(exactField(root, "HOTEL ADDRESS"), hotel.optString("address", "")).trim();
        if (name.isEmpty() && address.isEmpty()) return;
        String signature = storage + "|" + name + "|" + address;
        if (!userRequested && signature.equals(attemptedHotelPhoto)) return;
        attemptedHotelPhoto = signature;
        if (userRequested) toast("Finding a useful stay photo…");
        io.execute(() -> {
            try {
                PhotoResult result = null;
                String kind = "Area photo";
                if (!name.isEmpty() && !"Hotel".equalsIgnoreCase(name)) {
                    result = commonsPhoto(name + " " + areaQuery(address), name);
                    if (result != null && result.hotelMatch) kind = "Hotel photo";
                    else result = null;
                }
                if (result == null) result = commonsPhoto(areaQuery(address), "");
                if (result == null) throw new Exception("photo");
                Bitmap bitmap = downloadBitmap(result.url);
                if (bitmap == null) throw new Exception("decode");
                try (FileOutputStream out = openFileOutput(hotelImageFile(storage), MODE_PRIVATE)) { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out); }
                final PhotoResult chosen = result; final String finalKind = kind; final Bitmap finalBitmap = bitmap;
                if (!id.isEmpty()) {
                    saveField(HOTELS, id, "hotelPhotoKind", finalKind);
                    saveField(HOTELS, id, "hotelPhotoCredit", chosen.credit);
                    saveField(HOTELS, id, "hotelPhotoLicense", chosen.license);
                    saveField(HOTELS, id, "hotelPhotoSource", chosen.source);
                }
                runOnUiThread(() -> {
                    ImageView target = findTaggedImage(getWindow().getDecorView(), "stay_photo");
                    if (target != null) target.setImageBitmap(finalBitmap);
                    LinearLayout hub = findTaggedLinear(getWindow().getDecorView(), "stay_dashboard");
                    if (hub != null) updateStayHub(getWindow().getDecorView(), hub);
                    if (userRequested) toast(finalKind + " saved to this stay");
                });
            } catch (Exception e) {
                if (userRequested) runOnUiThread(() -> toast("No useful hotel or area photo found right now"));
            }
        });
    }

    private PhotoResult commonsPhoto(String query, String hotelName) {
        if (query == null || query.trim().isEmpty()) return null;
        try {
            String api = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrnamespace=6&gsrlimit=18&gsrsearch=" + enc(query) +
                    "&prop=imageinfo&iiprop=url%7Cextmetadata&iiurlwidth=1200&format=json&origin=*";
            JSONObject queryObj = new JSONObject(http(api)).optJSONObject("query");
            JSONObject pages = queryObj == null ? null : queryObj.optJSONObject("pages");
            if (pages == null) return null;
            Iterator<String> keys = pages.keys();
            String target = normalize(hotelName);
            while (keys.hasNext()) {
                JSONObject page = pages.optJSONObject(keys.next()); if (page == null) continue;
                String title = page.optString("title", "");
                String lower = title.toLowerCase(Locale.US);
                if (lower.contains("logo") || lower.contains("map of") || lower.contains("coat of arms") || lower.contains("flag of")) continue;
                JSONArray info = page.optJSONArray("imageinfo"); if (info == null || info.length() == 0) continue;
                JSONObject image = info.optJSONObject(0); if (image == null) continue;
                String url = image.optString("thumburl", image.optString("url", "")); if (url.isEmpty()) continue;
                JSONObject meta = image.optJSONObject("extmetadata");
                String credit = metaValue(meta, "Artist");
                String license = metaValue(meta, "LicenseShortName");
                String source = image.optString("descriptionurl", "");
                boolean match = target.isEmpty() || normalize(title).contains(target) || target.contains(normalize(title.replace("File:", "")));
                if (!target.isEmpty() && !match) continue;
                return new PhotoResult(url, stripHtml(credit), stripHtml(license), source, !target.isEmpty() && match);
            }
        } catch (Exception ignored) { }
        return null;
    }

    private void maybeAreaBrief() {
        String id = prefs.getString("activeHotelId", "");
        if (id.isEmpty()) return;
        JSONObject hotel = profileById(HOTELS, id);
        if (hotel.optString("areaBriefing", "").isEmpty() && !id.equals(attemptedAreaBrief)) {
            attemptedAreaBrief = id;
            refreshAreaBrief(false);
        }
    }

    private void refreshAreaBrief(boolean userRequested) {
        String id = prefs.getString("activeHotelId", "");
        JSONObject hotel = profileById(HOTELS, id);
        double lat = signed(hotel.optString("lat", prefs.getString("draftHotelLat", "0")));
        double lon = signed(hotel.optString("lon", prefs.getString("draftHotelLon", "0")));
        if (lat == 0 && lon == 0) { if (userRequested) toast("Find and save the hotel first"); return; }
        if (userRequested) toast("Refreshing stay-area briefing…");
        io.execute(() -> {
            try {
                JSONObject reverse = new JSONObject(http("https://nominatim.openstreetmap.org/reverse?format=jsonv2&addressdetails=1&zoom=10&lat=" + lat + "&lon=" + lon));
                JSONObject address = reverse.optJSONObject("address");
                String city = firstValue(address, "city", "town", "village", "municipality", "hamlet");
                String county = address == null ? "" : address.optString("county", "");
                String state = address == null ? "" : address.optString("state", "");
                String weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon +
                        "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&temperature_unit=fahrenheit&wind_speed_unit=mph&forecast_days=1&timezone=auto";
                JSONObject weather = new JSONObject(http(weatherUrl));
                JSONObject current = weather.optJSONObject("current");
                JSONObject daily = weather.optJSONObject("daily");
                double temp = current == null ? Double.NaN : current.optDouble("temperature_2m", Double.NaN);
                double feels = current == null ? Double.NaN : current.optDouble("apparent_temperature", Double.NaN);
                double wind = current == null ? Double.NaN : current.optDouble("wind_speed_10m", Double.NaN);
                int code = current == null ? -1 : current.optInt("weather_code", -1);
                double hi = firstArray(daily, "temperature_2m_max");
                double lo = firstArray(daily, "temperature_2m_min");
                double precip = firstArray(daily, "precipitation_probability_max");
                int nearby = 0; try { nearby = new JSONArray(hotel.optString("nearbyPlaces", "[]")).length(); } catch (Exception ignored) { }
                StringBuilder b = new StringBuilder();
                b.append("AREA BRIEF • ").append(city.isEmpty() ? state : city + (state.isEmpty() ? "" : ", " + state));
                if (!county.isEmpty()) b.append(" • ").append(county);
                if (!Double.isNaN(temp)) {
                    b.append("\nNow ").append(Math.round(temp)).append("°F");
                    if (!Double.isNaN(feels)) b.append(" • feels ").append(Math.round(feels)).append("°F");
                    String label = weather(code); if (!label.isEmpty()) b.append(" • ").append(label);
                    if (!Double.isNaN(wind)) b.append(" • wind ").append(Math.round(wind)).append(" mph");
                }
                if (!Double.isNaN(hi) && !Double.isNaN(lo)) {
                    b.append("\nToday ").append(Math.round(hi)).append("° / ").append(Math.round(lo)).append("°");
                    if (!Double.isNaN(precip)) b.append(" • precip ").append(Math.round(precip)).append('%');
                }
                b.append("\nSaved nearby places: ").append(nearby);
                String category = hotel.optString("nearbyPlacesCategory", "");
                if (!category.isEmpty()) b.append(" • last search: ").append(category);
                final String brief = b.toString();
                if (!id.isEmpty()) {
                    saveField(HOTELS, id, "areaBriefing", brief);
                    saveField(HOTELS, id, "areaLocality", city);
                    saveField(HOTELS, id, "areaState", state);
                    saveField(HOTELS, id, "areaUpdatedAt", System.currentTimeMillis());
                }
                runOnUiThread(() -> {
                    TextView view = findTaggedText(getWindow().getDecorView(), "stay_area_brief");
                    if (view != null) view.setText(brief);
                    if (userRequested) toast("Stay-area briefing refreshed");
                });
            } catch (Exception e) {
                if (userRequested) runOnUiThread(() -> toast("Area briefing unavailable right now"));
            }
        });
    }

    // ---------- Explore hub ----------

    private void polishArea(View root) {
        TextView header = findExactText(root, "Linked adventure");
        if (header == null || !(header.getParent() instanceof LinearLayout)) return;
        LinearLayout card = (LinearLayout) header.getParent();
        header.setVisibility(View.GONE);
        card.setBackground(round(Color.argb(248, 17, 23, 15), 22, Color.rgb(78, 94, 63)));
        LinearLayout dash = findTaggedLinear(root, "explore_dashboard");
        if (dash == null) {
            dash = dashboard("explore_dashboard", "ADVENTURE SNAPSHOT");
            TextView text = body(""); text.setTag("explore_summary"); text.setTextSize(15); text.setTextColor(Color.WHITE); text.setLineSpacing(0, 1.14f); dash.addView(text, topMargin(-1, -2, 6));
            card.addView(dash, Math.min(1, card.getChildCount()), topMargin(-1, -2, 5));
        }
        JSONObject trip = activeTrip();
        JSONObject vehicle = profileById(VEHICLES, trip.optString("vehicleId", prefs.getString("activeVehicleId", "")));
        JSONObject hotel = profileById(HOTELS, trip.optString("hotelId", prefs.getString("activeHotelId", "")));
        double miles = positive(trip.optString("outMiles", "0")) + positive(trip.optString("backMiles", "0"));
        double hours = positive(trip.optString("outHours", "0")) + positive(trip.optString("backHours", "0"));
        int routePlaces = 0, hotelPlaces = 0;
        try { routePlaces = new JSONArray(trip.optString("routePlaces", "[]")).length(); } catch (Exception ignored) { }
        try { hotelPlaces = new JSONArray(hotel.optString("nearbyPlaces", "[]")).length(); } catch (Exception ignored) { }
        setText(findTaggedText(dash, "explore_summary"), shortPlace(trip.optString("start", "Start")) + " → " + shortPlace(trip.optString("end", "Destination")) +
                (miles > 0 ? "\n" + one(miles) + " mi round trip" : "") + (hours > 0 ? " • " + one(hours) + " hr" : "") +
                "\nGarage: " + vehicle.optString("label", "No vehicle linked") + "\nStay: " + hotel.optString("label", "No hotel linked") +
                "\nSaved discoveries: " + routePlaces + " along route • " + hotelPlaces + " near stay");
    }

    // ---------- data helpers ----------

    private JSONObject activeTrip() { return profileById(TRIPS, prefs.getString("activeTripId", "")); }
    private JSONObject activeVehicle() { return profileById(VEHICLES, prefs.getString("activeVehicleId", "")); }
    private JSONObject activeHotel() { return profileById(HOTELS, prefs.getString("activeHotelId", "")); }

    private JSONObject linkedVehicle(View root, JSONObject trip) {
        Spinner s = exactSpinner(root, "CAR FOR THIS TRIP");
        String id = selectedId(VEHICLES, s == null ? 0 : s.getSelectedItemPosition());
        if (id.isEmpty()) id = trip.optString("vehicleId", prefs.getString("activeVehicleId", ""));
        return profileById(VEHICLES, id);
    }

    private JSONObject linkedHotel(View root, JSONObject trip) {
        Spinner s = exactSpinner(root, "HOTEL FOR THIS TRIP");
        String id = selectedId(HOTELS, s == null ? 0 : s.getSelectedItemPosition());
        if (id.isEmpty()) id = trip.optString("hotelId", prefs.getString("activeHotelId", ""));
        return profileById(HOTELS, id);
    }

    private JSONObject linkedTrip(String vehicleId) {
        JSONObject active = activeTrip();
        if (!vehicleId.isEmpty() && vehicleId.equals(active.optString("vehicleId", ""))) return active;
        JSONArray trips = profiles(TRIPS);
        for (int i = 0; i < trips.length(); i++) {
            JSONObject t = trips.optJSONObject(i);
            if (t != null && vehicleId.equals(t.optString("vehicleId", ""))) return t;
        }
        return new JSONObject();
    }

    private boolean routeComplete(JSONObject trip) {
        return trip != null && !trip.optString("routeOut", "").isEmpty() && !trip.optString("routeBack", "").isEmpty() &&
                positive(trip.optString("outMiles", "0")) > 0 && positive(trip.optString("backMiles", "0")) > 0;
    }

    private JSONArray profiles(String key) {
        try { return new JSONArray(prefs.getString(key, "[]")); } catch (Exception e) { return new JSONArray(); }
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

    private String selectedId(String key, int index) {
        if (index <= 0) return "";
        JSONObject o = profiles(key).optJSONObject(index - 1);
        return o == null ? "" : o.optString("id", "");
    }

    private synchronized void saveField(String key, String id, String field, Object value) {
        if (id == null || id.isEmpty()) return;
        try {
            JSONArray a = profiles(key), out = new JSONArray();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i); if (o == null) continue;
                if (id.equals(o.optString("id", ""))) o.put(field, value);
                out.put(o);
            }
            prefs.edit().putString(key, out.toString()).commit();
        } catch (Exception ignored) { }
    }

    private ArrayList<GeoPoint> roundTripGeometry(JSONObject trip) {
        ArrayList<GeoPoint> route = geometry(trip.optString("routeOut", ""));
        ArrayList<GeoPoint> back = geometry(trip.optString("routeBack", ""));
        if (!route.isEmpty() && !back.isEmpty() && distance(route.get(route.size() - 1), back.get(0)) < .01) back.remove(0);
        route.addAll(back);
        return route;
    }

    private ArrayList<GeoPoint> geometry(String json) {
        ArrayList<GeoPoint> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(json == null || json.isEmpty() ? "[]" : json);
            for (int i = 0; i < a.length(); i++) {
                JSONArray p = a.optJSONArray(i);
                if (p != null && p.length() >= 2) out.add(new GeoPoint(p.optDouble(1), p.optDouble(0)));
            }
        } catch (Exception ignored) { }
        return out;
    }

    private GeoPoint pointAtFraction(List<GeoPoint> points, double fraction) {
        if (points == null || points.isEmpty()) return null;
        if (points.size() == 1) return points.get(0);
        fraction = Math.max(0, Math.min(1, fraction));
        double total = 0; for (int i = 1; i < points.size(); i++) total += distance(points.get(i - 1), points.get(i));
        if (total <= 0) return points.get(0);
        double target = total * fraction, walked = 0;
        for (int i = 1; i < points.size(); i++) {
            GeoPoint a = points.get(i - 1), b = points.get(i);
            double segment = distance(a, b);
            if (segment <= 0) continue;
            if (walked + segment >= target) {
                double f = (target - walked) / segment;
                return new GeoPoint(a.getLatitude() + (b.getLatitude() - a.getLatitude()) * f,
                        a.getLongitude() + (b.getLongitude() - a.getLongitude()) * f);
            }
            walked += segment;
        }
        return points.get(points.size() - 1);
    }

    private double distance(GeoPoint a, GeoPoint b) {
        double r = 3958.7613;
        double lat1 = Math.toRadians(a.getLatitude()), lat2 = Math.toRadians(b.getLatitude());
        double dLat = lat2 - lat1, dLon = Math.toRadians(b.getLongitude() - a.getLongitude());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    // ---------- view helpers ----------

    private void hideIntro(LinearLayout card, TextView header) {
        header.setVisibility(View.GONE);
        int start = card.indexOfChild(header) + 1;
        if (start >= 0 && start < card.getChildCount() && card.getChildAt(start) instanceof TextView) card.getChildAt(start).setVisibility(View.GONE);
    }

    private LinearLayout dashboard(String tag, String eyebrow) {
        LinearLayout p = new LinearLayout(this);
        p.setTag(tag); p.setOrientation(LinearLayout.VERTICAL); p.setPadding(dp(14), dp(14), dp(14), dp(14));
        p.setBackground(round(SURFACE, 18, Color.rgb(103, 125, 80)));
        TextView e = small(eyebrow); e.setTextColor(Color.rgb(187, 212, 146)); e.setTypeface(Typeface.DEFAULT, Typeface.BOLD); p.addView(e);
        return p;
    }

    private LinearLayout metric(String tag, String label) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(10), dp(9), dp(10), dp(9));
        box.setBackground(round(DEEP, 14, Color.rgb(72, 88, 59)));
        TextView l = small(label); l.setTextSize(10); box.addView(l);
        TextView v = bigText("—"); v.setTextSize(18); v.setTag(tag + "_value"); box.addView(v, topMargin(-1, -2, 1));
        box.setTag(tag); return box;
    }

    private LinearLayout metricRow(View left, View right) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(left, weight()); row.addView(right, weight()); return row;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1); p.setMargins(dp(3), 0, dp(3), 0); return p;
    }

    private Button compactButton(String text) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setTextSize(13); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(round(Color.rgb(42, 52, 35), 14, BORDER)); return b;
    }

    private TextView bigText(String text) { TextView t = new TextView(this); t.setText(text); t.setTextColor(Color.WHITE); t.setTextSize(22); t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t; }
    private TextView body(String text) { TextView t = new TextView(this); t.setText(text); t.setTextColor(CREAM); t.setTextSize(13); return t; }
    private TextView small(String text) { TextView t = new TextView(this); t.setText(text); t.setTextColor(MUTED); t.setTextSize(11); return t; }

    private void sectionBefore(View root, LinearLayout card, String label, String tag, String title) {
        if (card.findViewWithTag(tag) != null) return;
        TextView anchor = findExactText(root, label); if (anchor == null || anchor.getParent() != card) return;
        TextView section = small(title); section.setTag(tag); section.setTextColor(Color.rgb(187, 212, 146)); section.setTypeface(Typeface.DEFAULT, Typeface.BOLD); section.setTextSize(12);
        card.addView(section, Math.max(0, card.indexOfChild(anchor)), topMargin(-1, -2, 15));
    }

    private Drawable markerIcon(int color, String text) {
        String key = color + "|" + text;
        Drawable existing = markerIcons.get(key); if (existing != null) return existing;
        int w = dp(38), h = dp(45);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color); c.drawCircle(w / 2f, dp(18), dp(16), p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2)); p.setColor(Color.WHITE); c.drawCircle(w / 2f, dp(18), dp(14), p);
        p.setStyle(Paint.Style.FILL); p.setColor(Color.WHITE); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(dp(13)); c.drawText(text, w / 2f, dp(23), p);
        android.graphics.Path path = new android.graphics.Path(); path.moveTo(w / 2f - dp(6), dp(31)); path.lineTo(w / 2f + dp(6), dp(31)); path.lineTo(w / 2f, dp(42)); path.close(); p.setColor(color); c.drawPath(path, p);
        Drawable d = new BitmapDrawable(getResources(), bitmap); markerIcons.put(key, d); return d;
    }

    private GradientDrawable round(int fill, int radius, int stroke) {
        GradientDrawable g = new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp(radius)); g.setStroke(dp(1), stroke); return g;
    }

    private LinearLayout.LayoutParams topMargin(int w, int h, int margin) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h); p.topMargin = dp(margin); return p; }
    private void setText(TextView view, String value) { if (view != null) view.setText(value); }
    private void setMetric(TextView view, String value) { if (view != null) view.setText(value); }

    private EditText exactField(View root, String label) {
        TextView l = findExactText(root, label); if (l == null || !(l.getParent() instanceof ViewGroup)) return null;
        ViewGroup parent = (ViewGroup) l.getParent(); int start = parent.indexOfChild(l) + 1;
        for (int i = start; i < parent.getChildCount(); i++) { View child = parent.getChildAt(i); if (child instanceof EditText) return (EditText) child; if (child instanceof TextView && !(child instanceof EditText)) break; }
        return null;
    }

    private Spinner exactSpinner(View root, String label) {
        TextView l = findExactText(root, label); if (l == null || !(l.getParent() instanceof ViewGroup)) return null;
        ViewGroup parent = (ViewGroup) l.getParent(); int start = parent.indexOfChild(l) + 1;
        for (int i = start; i < parent.getChildCount(); i++) { View child = parent.getChildAt(i); if (child instanceof Spinner) return (Spinner) child; if (child instanceof TextView) break; }
        return null;
    }

    private TextView findExactText(View root, String target) { ArrayList<TextView> all = new ArrayList<>(); collect(root, TextView.class, all); for (TextView t : all) if (t.getText() != null && target.equalsIgnoreCase(t.getText().toString().trim())) return t; return null; }
    private TextView findTaggedText(View root, String tag) { ArrayList<TextView> all = new ArrayList<>(); collect(root, TextView.class, all); for (TextView t : all) if (tag.equals(t.getTag())) return t; return null; }
    private LinearLayout findTaggedLinear(View root, String tag) { ArrayList<LinearLayout> all = new ArrayList<>(); collect(root, LinearLayout.class, all); for (LinearLayout l : all) if (tag.equals(l.getTag())) return l; return null; }
    private EditText findTaggedEditText(View root, String tag) { ArrayList<EditText> all = new ArrayList<>(); collect(root, EditText.class, all); for (EditText e : all) if (tag.equals(e.getTag())) return e; return null; }
    private ImageView findTaggedImage(View root, String tag) { ArrayList<ImageView> all = new ArrayList<>(); collect(root, ImageView.class, all); for (ImageView i : all) if (tag.equals(i.getTag())) return i; return null; }
    private Button findButton(View root, String text) { ArrayList<Button> all = new ArrayList<>(); collect(root, Button.class, all); for (Button b : all) if (b.getText() != null && text.equalsIgnoreCase(b.getText().toString().trim())) return b; return null; }
    private Button findTaggedButton(View root, String tag) { ArrayList<Button> all = new ArrayList<>(); collect(root, Button.class, all); for (Button b : all) if (tag.equals(b.getTag())) return b; return null; }
    private Button firstButton(View root, String... labels) { for (String s : labels) { Button b = findButton(root, s); if (b != null) return b; } return null; }
    private CheckBox findCheckBoxContaining(View root, String target) { ArrayList<CheckBox> all = new ArrayList<>(); collect(root, CheckBox.class, all); for (CheckBox b : all) if (b.getText() != null && b.getText().toString().contains(target)) return b; return null; }
    private MapView firstMap(View root) { ArrayList<MapView> all = new ArrayList<>(); collect(root, MapView.class, all); return all.isEmpty() ? null : all.get(0); }
    private ImageView firstImage(View root) { ArrayList<ImageView> all = new ArrayList<>(); collect(root, ImageView.class, all); return all.isEmpty() ? null : all.get(0); }
    private <T extends View> void collect(View root, Class<T> type, List<T> out) { if (type.isInstance(root)) out.add(type.cast(root)); if (root instanceof ViewGroup) { ViewGroup g = (ViewGroup) root; for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), type, out); } }
    private int childIndexByTag(ViewGroup group, String tag) { for (int i = 0; i < group.getChildCount(); i++) if (tag.equals(group.getChildAt(i).getTag())) return i; return 0; }

    // ---------- misc helpers ----------

    private String text(EditText e) { return e == null || e.getText() == null ? "" : e.getText().toString(); }
    private String text(EditText e, String fallback) { String s = text(e).trim(); return s.isEmpty() ? fallback : s; }
    private double positive(String s) { try { return TrailboundTripMath.nonNegative(Double.parseDouble(s == null ? "" : s.trim())); } catch (Exception e) { return 0; } }
    private double signed(String s) { try { return Double.parseDouble(s == null ? "" : s.trim()); } catch (Exception e) { return 0; } }
    private String one(double d) { return String.format(Locale.US, "%.1f", d); }
    private String money(double d) { return NumberFormat.getCurrencyInstance(Locale.US).format(d); }
    private String shortPlace(String s) { if (s == null || s.trim().isEmpty()) return "Set location"; String t = s.trim(); int comma = t.indexOf(','); String out = comma > 0 ? t.substring(0, comma).trim() : t; return shorten(out, 28); }
    private String shorten(String s, int max) { if (s == null) return ""; return s.length() > max ? s.substring(0, max) + "…" : s; }
    private String join(List<String> items) { StringBuilder b = new StringBuilder(); for (String s : items) { if (b.length() > 0) b.append(" • "); b.append(s); } return b.toString(); }
    private String normalize(String s) { return s == null ? "" : s.toLowerCase(Locale.US).replaceAll("[^a-z0-9]", ""); }
    private String hotelImageFile(String id) { return "hotel_" + (id == null ? "draft" : id.replaceAll("[^A-Za-z0-9_-]", "")) + ".jpg"; }
    private String areaQuery(String address) { if (address == null || address.trim().isEmpty()) return "travel destination"; String[] p = address.split(","); if (p.length >= 2) return p[p.length - 2].trim() + " " + p[p.length - 1].trim(); return address; }
    private String enc(String s) throws Exception { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); }
    private String metaValue(JSONObject meta, String key) { JSONObject o = meta == null ? null : meta.optJSONObject(key); return o == null ? "" : o.optString("value", ""); }
    private String stripHtml(String s) { return s == null ? "" : s.replaceAll("<[^>]+>", "").replace("&amp;", "&").trim(); }
    private String firstValue(JSONObject o, String... keys) { if (o == null) return ""; for (String k : keys) { String v = o.optString(k, "").trim(); if (!v.isEmpty()) return v; } return ""; }
    private double firstArray(JSONObject o, String key) { JSONArray a = o == null ? null : o.optJSONArray(key); return a == null || a.length() == 0 ? Double.NaN : a.optDouble(0, Double.NaN); }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private String weather(int code) {
        if (code == 0) return "clear"; if (code == 1 || code == 2) return "partly cloudy"; if (code == 3) return "overcast";
        if (code == 45 || code == 48) return "fog"; if (code >= 51 && code <= 57) return "drizzle"; if (code >= 61 && code <= 67) return "rain";
        if (code >= 71 && code <= 77) return "snow"; if (code >= 80 && code <= 82) return "showers"; if (code >= 95) return "thunderstorms"; return "";
    }

    private String http(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(12000); c.setReadTimeout(24000); c.setRequestProperty("User-Agent", "TrailboundAndroid/7.0"); c.setRequestProperty("Accept", "application/json,text/html,*/*");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()))) { StringBuilder s = new StringBuilder(); String line; while ((line = br.readLine()) != null) s.append(line); return s.toString(); }
        finally { c.disconnect(); }
    }

    private Bitmap downloadBitmap(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection(); c.setConnectTimeout(12000); c.setReadTimeout(22000); c.setRequestProperty("User-Agent", "TrailboundAndroid/7.0");
        try (InputStream in = c.getInputStream()) { return BitmapFactory.decodeStream(in); } finally { c.disconnect(); }
    }

    @Override protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private static class PhotoResult {
        final String url, credit, license, source; final boolean hotelMatch;
        PhotoResult(String url, String credit, String license, String source, boolean hotelMatch) {
            this.url = url; this.credit = credit; this.license = license; this.source = source; this.hotelMatch = hotelMatch;
        }
    }
}
