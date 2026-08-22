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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Trailbound 7.0 presentation and hub layer.
 *
 * Keeps the audited 6.3 calculation/storage path underneath while turning the
 * native Android UI into a compact road-trip app: route dashboard, visually
 * distinct outbound/return map markers, maintenance-focused Garage, Stay hub
 * with cached hotel/area imagery and local briefing, and complete round-trip
 * discovery labeling.
 */
public class TrailboundPolishedActivity extends TrailboundIntegrityActivity {
    private static final String PREFS = "trailbound_v5";
    private static final String TRIPS = "trips";
    private static final String VEHICLES = "vehicles";
    private static final String HOTELS = "hotels";

    private static final int INK = Color.rgb(12, 16, 11);
    private static final int SURFACE = Color.rgb(22, 28, 19);
    private static final int SURFACE_2 = Color.rgb(30, 38, 25);
    private static final int BORDER = Color.rgb(86, 103, 69);
    private static final int GREEN = Color.rgb(111, 143, 80);
    private static final int GREEN_DARK = Color.rgb(70, 94, 51);
    private static final int CREAM = Color.rgb(247, 239, 220);
    private static final int GOLD = Color.rgb(222, 169, 83);
    private static final int BLUE = Color.rgb(74, 132, 166);
    private static final int RED = Color.rgb(197, 86, 70);
    private static final int MUTED = Color.rgb(205, 201, 187);

    private SharedPreferences prefs;
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean patching;
    private String lastTripMarkerSignature = "";
    private String attemptedHotelPhoto = "";
    private String attemptedHotelArea = "";

    private final Set<Button> routeButtons = Collections.newSetFromMap(new WeakHashMap<Button, Boolean>());
    private final Set<Button> refreshTouchButtons = Collections.newSetFromMap(new WeakHashMap<Button, Boolean>());
    private final Set<Button> hotelSaveTouchButtons = Collections.newSetFromMap(new WeakHashMap<Button, Boolean>());
    private final Map<String, Drawable> markerIcons = new HashMap<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (!patching && !isFinishing()) patchPolish();
        });
        main.postDelayed(this::patchPolish, 800);
    }

    @Override protected void onResume() {
        super.onResume();
        main.postDelayed(this::patchPolish, 500);
    }

    private void patchPolish() {
        if (patching || isFinishing()) return;
        patching = true;
        try {
            View root = getWindow().getDecorView();
            polishHeader(root);
            polishNavigation(root);
            polishControls(root);
            if (findExactText(root, "Trip profile") != null) polishTrip(root);
            else if (findExactText(root, "Vehicle profile") != null) polishVehicle(root);
            else if (findExactText(root, "Hotel profile") != null) polishHotel(root);
            else if (findExactText(root, "Linked adventure") != null) polishArea(root);
        } catch (Exception ignored) {
            // Presentation must never be able to take down the audited planner.
        } finally {
            patching = false;
        }
    }

    // ---------------------------------------------------------------------
    // App shell / controls
    // ---------------------------------------------------------------------

    private void polishHeader(View root) {
        TextView title = findTaggedText(root, "polish_app_title");
        if (title == null) {
            title = findExactText(root, "TRAILBOUND");
            if (title != null) {
                title.setTag("polish_app_title");
                title.setText("Trailbound");
            }
        }
        if (title != null) {
            title.setTextSize(27);
            title.setTextColor(Color.WHITE);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }

        TextView sub = findTaggedText(root, "polish_app_subtitle");
        if (sub == null) {
            sub = findExactText(root, "Adventure trip planner");
            if (sub != null) sub.setTag("polish_app_subtitle");
        }
        if (sub != null) {
            if (findExactText(root, "Trip profile") != null) sub.setText("Route • budget • fuel • stops");
            else if (findExactText(root, "Vehicle profile") != null) sub.setText("Garage • maintenance • readiness");
            else if (findExactText(root, "Hotel profile") != null) sub.setText("Stay • area • nearby essentials");
            else sub.setText("Adventure intelligence");
            sub.setTextColor(MUTED);
        }
    }

    private void polishNavigation(View root) {
        Button trip = navButton(root, "Trip", "nav_trip", "🧭\nPlan");
        Button cars = navButton(root, "Cars", "nav_cars", "🚙\nGarage");
        Button hotels = navButton(root, "Hotels", "nav_hotels", "🏨\nStay");
        Button area = navButton(root, "Area", "nav_area", "📍\nExplore");
        Button[] all = new Button[]{trip, cars, hotels, area};
        Button active = findExactText(root, "Trip profile") != null ? trip :
                findExactText(root, "Vehicle profile") != null ? cars :
                        findExactText(root, "Hotel profile") != null ? hotels : area;

        LinearLayout nav = null;
        for (Button b : all) if (b != null && b.getParent() instanceof LinearLayout) { nav = (LinearLayout) b.getParent(); break; }
        if (nav != null) {
            nav.setPadding(dp(6), dp(5), dp(6), dp(5));
            nav.setBackground(round(Color.rgb(16, 21, 14), 20, Color.rgb(78, 94, 63)));
        }
        for (Button b : all) {
            if (b == null) continue;
            b.setAllCaps(false);
            b.setGravity(Gravity.CENTER);
            b.setTextSize(12);
            b.setTextColor(Color.WHITE);
            b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            b.setPadding(dp(4), dp(3), dp(4), dp(3));
            if (b.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) b.getLayoutParams();
                lp.height = dp(60);
                lp.setMargins(dp(2), 0, dp(2), 0);
                b.setLayoutParams(lp);
            }
            b.setBackground(round(b == active ? GREEN_DARK : Color.TRANSPARENT, 15,
                    b == active ? Color.rgb(151, 178, 113) : Color.TRANSPARENT));
        }
    }

    private Button navButton(View root, String original, String tag, String label) {
        Button b = findTaggedButton(root, tag);
        if (b == null) {
            b = findButton(root, original);
            if (b != null) b.setTag(tag);
        }
        if (b != null && !label.equals(b.getText() == null ? "" : b.getText().toString())) b.setText(label);
        return b;
    }

    private void polishControls(View root) {
        ArrayList<EditText> fields = new ArrayList<>();
        collect(root, EditText.class, fields);
        for (EditText e : fields) {
            e.setTextColor(Color.WHITE);
            e.setHintTextColor(Color.rgb(156, 164, 147));
            e.setTextSize(16);
            e.setPadding(dp(13), 0, dp(13), 0);
            e.setBackground(round(Color.rgb(13, 18, 12), 14, Color.rgb(85, 102, 68)));
        }

        ArrayList<Spinner> spinners = new ArrayList<>();
        collect(root, Spinner.class, spinners);
        for (Spinner s : spinners) {
            try {
                s.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(117, 137, 91)));
                s.setPopupBackgroundDrawable(round(Color.rgb(27, 34, 23), 14, Color.rgb(91, 108, 73)));
            } catch (Exception ignored) { }
            View selected = s.getSelectedView();
            if (selected instanceof TextView) {
                ((TextView) selected).setTextColor(Color.WHITE);
                ((TextView) selected).setTextSize(15);
            }
        }

        ArrayList<Button> buttons = new ArrayList<>();
        collect(root, Button.class, buttons);
        for (Button b : buttons) {
            Object tag = b.getTag();
            if (tag != null && tag.toString().startsWith("nav_")) continue;
            String text = b.getText() == null ? "" : b.getText().toString();
            b.setAllCaps(false);
            b.setTextSize(14);
            b.setTextColor(Color.WHITE);
            b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            b.setMinHeight(dp(48));
            b.setPadding(dp(12), dp(5), dp(12), dp(5));
            boolean primary = text.contains("Save") || text.contains("Refresh route") || text.contains("Find hotel") ||
                    text.contains("Choose exact EPA") || text.contains("Refresh destination") || text.contains("Refresh area briefing");
            boolean dangerish = text.contains("new trip") || text.contains("another vehicle") || text.contains("another hotel") || text.contains("Reset checklist");
            int fill = primary ? GREEN_DARK : dangerish ? Color.rgb(73, 56, 40) : Color.rgb(41, 49, 34);
            int stroke = primary ? Color.rgb(152, 178, 112) : dangerish ? Color.rgb(139, 106, 72) : Color.rgb(91, 108, 73);
            b.setBackground(round(fill, 14, stroke));
        }
    }

    // ---------------------------------------------------------------------
    // Trip: route-first dashboard and fully differentiated map
    // ---------------------------------------------------------------------

    private void polishTrip(View root) {
        TextView header = findExactText(root, "Trip profile");
        if (header == null || !(header.getParent() instanceof LinearLayout)) return;
        LinearLayout card = (LinearLayout) header.getParent();
        card.setPadding(dp(14), dp(14), dp(14), dp(16));
        card.setBackground(round(Color.argb(248, 18, 23, 16), 22, Color.rgb(78, 94, 63)));

        LinearLayout hero = ensureTripHero(card);
        updateTripHero(root, hero);
        moveTripMapNearRoute(root, card, hero);
        styleTripMap(root);
        styleTripFuelPlan(root);
        attachRoundTripDiscovery(root);

        Button mapButton = findButton(root, "Map actual round trip");
        if (mapButton != null) mapButton.setVisibility(View.GONE);
        Button refresh = findButton(root, "Refresh route + automatic gas average");
        if (refresh != null) {
            refresh.setTextSize(15);
            refresh.setMinHeight(dp(54));
            if (!refreshTouchButtons.contains(refresh)) {
                refreshTouchButtons.add(refresh);
                refresh.setOnTouchListener((v, event) -> {
                    if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                        main.postDelayed(this::patchPolish, 1800);
                        main.postDelayed(this::patchPolish, 3200);
                    }
                    return false;
                });
            }
        }

        TextView audited = findTaggedText(root, "audited_trip_summary");
        TextView dual = findTaggedText(root, "dual_gas_summary");
        boolean show = prefs.getBoolean("polishTripDetails", false);
        if (audited != null) audited.setVisibility(show ? View.VISIBLE : View.GONE);
        if (dual != null) dual.setVisibility(show ? View.VISIBLE : View.GONE);
        ensureTripDetailsButton(card, audited, dual);
    }

    private LinearLayout ensureTripHero(LinearLayout card) {
        View existing = card.findViewWithTag("polish_trip_hero");
        if (existing instanceof LinearLayout) return (LinearLayout) existing;
        LinearLayout hero = panel("polish_trip_hero", SURFACE_2, Color.rgb(106, 128, 83));
        TextView eyebrow = label("ROAD TRIP", 11, true, Color.rgb(185, 211, 144));
        hero.addView(eyebrow);
        TextView route = label("Plan your route", 22, true, Color.WHITE);
        route.setTag("polish_trip_route");
        hero.addView(route, topMargin(-1, -2, 3));
        TextView status = label("", 13, false, MUTED);
        status.setTag("polish_trip_status");
        hero.addView(status, topMargin(-1, -2, 4));
        hero.addView(metricRow(metric("polish_trip_distance", "ROUND TRIP"), metric("polish_trip_time", "DRIVE TIME")), topMargin(-1, -2, 12));
        hero.addView(metricRow(metric("polish_trip_auto", "AUTO BUDGET"), metric("polish_trip_safe", "CONSERVATIVE")), topMargin(-1, -2, 8));
        TextView source = label("", 11, false, Color.rgb(185, 190, 176));
        source.setTag("polish_trip_source");
        hero.addView(source, topMargin(-1, -2, 9));
        int insert = Math.min(2, card.getChildCount());
        card.addView(hero, insert, topMargin(-1, -2, 10));
        return hero;
    }

    private void updateTripHero(View root, LinearLayout hero) {
        JSONObject trip = activeTrip();
        EditText fromField = exactField(root, "FROM");
        EditText toField = exactField(root, "TO");
        String start = text(fromField).trim();
        String end = text(toField).trim();
        TextView route = findTaggedText(hero, "polish_trip_route");
        TextView status = findTaggedText(hero, "polish_trip_status");
        TextView distance = findTaggedText(hero, "polish_trip_distance_value");
        TextView time = findTaggedText(hero, "polish_trip_time_value");
        TextView auto = findTaggedText(hero, "polish_trip_auto_value");
        TextView safe = findTaggedText(hero, "polish_trip_safe_value");
        TextView source = findTaggedText(hero, "polish_trip_source");
        if (route != null) route.setText(shortPlace(start) + "  →  " + shortPlace(end));

        boolean endpoints = !start.isEmpty() && !end.isEmpty() && start.equals(trip.optString("start", "")) && end.equals(trip.optString("end", ""));
        boolean complete = endpoints && routeComplete(trip);
        if (!complete) {
            if (status != null) status.setText("Route needs refresh before Trailbound will trust mileage-based totals.");
            setMetricValue(distance, "—"); setMetricValue(time, "—"); setMetricValue(auto, "—"); setMetricValue(safe, "—");
            if (source != null) source.setText("Use Refresh route + automatic gas average after setting FROM and TO.");
            return;
        }

        double out = positive(trip.optString("outMiles", "0"));
        double back = positive(trip.optString("backMiles", "0"));
        double round = out + back;
        double hours = positive(trip.optString("outHours", "0")) + positive(trip.optString("backHours", "0"));
        JSONObject vehicle = linkedVehicle(root, trip);
        JSONObject hotel = linkedHotel(root, trip);
        double mpg = TrailboundTripMath.adjustedMpg(positive(vehicle.optString("mpg", "0")), positive(vehicle.optString("payload", "0")));
        double gallons = TrailboundTripMath.gallonsConsumed(round, mpg);
        double avgPrice = positive(text(exactField(root, "PREPARED GAS $ / GAL")));
        double conservative = positive(text(findTaggedEditText(root, "conservative_gas_field")));
        double snacks = positive(text(exactField(root, "SNACKS & DRINKS")));
        double extras = positive(text(exactField(root, "OTHER TRIP MONEY")));
        CheckBox include = findCheckBoxContaining(root, "Include linked hotel");
        boolean hotelIncluded = include != null && include.isChecked();
        double hotelCost = positive(hotel.optString("cost", "0"));
        double avgTotal = TrailboundTripMath.tripTotal(gallons * avgPrice, snacks, extras, hotelCost, hotelIncluded);
        double safeTotal = TrailboundTripMath.tripTotal(gallons * conservative, snacks, extras, hotelCost, hotelIncluded);
        List<Double> stops = TrailboundTripMath.fuelStopMiles(round, mpg,
                positive(text(exactField(root, "Tank size (gal)"))), positive(text(exactField(root, "Departure gas (gal)"))));
        int outStops = 0, backStops = 0;
        for (double mile : stops) { if (mile <= out) outStops++; else backStops++; }

        if (status != null) status.setText(one(out) + " mi out • " + one(back) + " mi back • fuel stops " + outStops + " out / " + backStops + " back");
        setMetricValue(distance, one(round) + " mi");
        setMetricValue(time, hours > 0 ? one(hours) + " hr" : "—");
        setMetricValue(auto, avgPrice > 0 ? money(avgTotal) : "—");
        setMetricValue(safe, conservative > 0 ? money(safeTotal) : "Set price");
        if (source != null) {
            String gasSource = trip.optString("gasSource", "").trim();
            source.setText((avgPrice > 0 ? "Automatic " + money(avgPrice) + "/gal" : "Automatic gas unavailable") +
                    (gasSource.isEmpty() ? "" : " • " + gasSource));
        }
    }

    private void moveTripMapNearRoute(View root, LinearLayout card, LinearLayout hero) {
        MapView map = firstMap(root);
        EditText to = exactField(root, "TO");
        if (map == null || map.getParent() != card || to == null || to.getParent() != card) return;
        int desired = card.indexOfChild(to) + 1;
        if (desired < 0) desired = card.indexOfChild(hero) + 1;
        int current = card.indexOfChild(map);
        if (current != desired && current != desired - 1) {
            card.removeView(map);
            card.addView(map, Math.min(desired, card.getChildCount()), new LinearLayout.LayoutParams(-1, dp(300)));
        } else if (map.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) map.getLayoutParams();
            lp.height = dp(300);
            lp.topMargin = dp(10);
            lp.bottomMargin = dp(7);
            map.setLayoutParams(lp);
        }
        map.setBackground(round(Color.rgb(17, 23, 17), 18, Color.rgb(88, 106, 71)));
        map.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        map.setClipToOutline(true);

        TextView legend = findTaggedText(root, "polish_map_legend");
        if (legend == null) {
            legend = label("🟢 Start   🔴 Destination   🟠 Outbound fuel   🔵 Return fuel", 11, true, CREAM);
            legend.setTag("polish_map_legend");
            legend.setGravity(Gravity.CENTER);
            int idx = card.indexOfChild(map);
            card.addView(legend, Math.min(idx + 1, card.getChildCount()), topMargin(-1, -2, 3));
        }
    }

    private void styleTripMap(View root) {
        JSONObject trip = activeTrip();
        MapView map = firstMap(root);
        if (map == null || !routeComplete(trip)) return;
        String signature = trip.optString("id", "") + "|" + trip.optString("routeOut", "").hashCode() + "|" + trip.optString("routeBack", "").hashCode() + "|" +
                text(exactField(root, "Tank size (gal)")) + "|" + text(exactField(root, "Departure gas (gal)"));

        int polylineIndex = 0;
        ArrayList<Overlay> removeFuel = new ArrayList<>();
        for (Overlay overlay : map.getOverlays()) {
            if (overlay instanceof Polyline) {
                Polyline p = (Polyline) overlay;
                if (polylineIndex == 0) { p.getOutlinePaint().setColor(GOLD); p.getOutlinePaint().setStrokeWidth(dp(5)); }
                else if (polylineIndex == 1) { p.getOutlinePaint().setColor(BLUE); p.getOutlinePaint().setStrokeWidth(dp(4)); }
                polylineIndex++;
            } else if (overlay instanceof Marker) {
                Marker m = (Marker) overlay;
                String title = m.getTitle() == null ? "" : m.getTitle();
                if ("Start / Return".equals(title)) {
                    m.setIcon(markerIcon(GREEN, "S"));
                    m.setSnippet("Trip start • return point");
                } else if ("Destination".equals(title)) {
                    m.setIcon(markerIcon(RED, "D"));
                    m.setSnippet("Destination • outbound turnaround point");
                } else if (title.toLowerCase(Locale.US).startsWith("fuel stop ")) {
                    removeFuel.add(overlay);
                }
            }
        }
        map.getOverlays().removeAll(removeFuel);

        double outMiles = positive(trip.optString("outMiles", "0"));
        double backMiles = positive(trip.optString("backMiles", "0"));
        double roundMiles = outMiles + backMiles;
        JSONObject vehicle = linkedVehicle(root, trip);
        double mpg = TrailboundTripMath.adjustedMpg(positive(vehicle.optString("mpg", "0")), positive(vehicle.optString("payload", "0")));
        double tank = positive(text(exactField(root, "Tank size (gal)"));
        double depart = positive(text(exactField(root, "Departure gas (gal)"));
        List<Double> stops = TrailboundTripMath.fuelStopMiles(roundMiles, mpg, tank, depart);
        ArrayList<GeoPoint> geometry = roundTripGeometry(trip);
        for (int i = 0; i < stops.size(); i++) {
            double mile = stops.get(i);
            GeoPoint point = pointAtTripMile(geometry, roundMiles, mile);
            if (point == null) continue;
            boolean returning = mile > outMiles;
            double legMile = returning ? mile - outMiles : mile;
            Marker marker = new Marker(map);
            marker.setPosition(point);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setIcon(markerIcon(returning ? BLUE : GOLD, "G"));
            marker.setTitle("Fuel stop " + (i + 1) + (returning ? " • RETURN" : " • OUTBOUND"));
            marker.setSnippet((returning ? "Return leg" : "Outbound") + " around leg mile " + Math.round(legMile) + " • round-trip mile " + Math.round(mile));
            marker.setRelatedObject("polished_fuel_stop");
            map.getOverlays().add(marker);
        }
        lastTripMarkerSignature = signature;
        map.invalidate();
    }

    private void styleTripFuelPlan(View root) {
        TextView plan = findTaggedText(root, "trailbound_fuel_plan_text");
        JSONObject trip = activeTrip();
        if (plan == null || !routeComplete(trip)) return;
        double out = positive(trip.optString("outMiles", "0"));
        double round = out + positive(trip.optString("backMiles", "0"));
        JSONObject vehicle = linkedVehicle(root, trip);
        double mpg = TrailboundTripMath.adjustedMpg(positive(vehicle.optString("mpg", "0")), positive(vehicle.optString("payload", "0")));
        List<Double> stops = TrailboundTripMath.fuelStopMiles(round, mpg,
                positive(text(exactField(root, "Tank size (gal)"))), positive(text(exactField(root, "Departure gas (gal)"))));
        ArrayList<String> outbound = new ArrayList<>(), returning = new ArrayList<>();
        for (double mile : stops) {
            if (mile <= out) outbound.add(mile < 1 ? "before departure" : "mile " + Math.round(mile));
            else returning.add("mile " + Math.round(mile - out) + " after destination");
        }
        String outText = outbound.isEmpty() ? "No planned fill-up" : joinStrings(outbound);
        String backText = returning.isEmpty() ? "No planned fill-up" : joinStrings(returning);
        plan.setText("FUEL STOPS\nOUTBOUND  •  " + outText + "\nRETURN  •  " + backText + "\nReserve target: about 15% of tank.");
        plan.setBackground(round(Color.rgb(20, 27, 18), 15, Color.rgb(91, 111, 72)));
    }

    private void ensureTripDetailsButton(LinearLayout card, TextView audited, TextView dual) {
        View existing = card.findViewWithTag("polish_trip_details_button");
        if (existing instanceof Button) {
            ((Button) existing).setText(prefs.getBoolean("polishTripDetails", false) ? "Hide calculation details" : "Show calculation details");
            return;
        }
        Button b = new Button(this);
        b.setTag("polish_trip_details_button");
        b.setText(prefs.getBoolean("polishTripDetails", false) ? "Hide calculation details" : "Show calculation details");
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(round(Color.rgb(38, 46, 32), 14, Color.rgb(88, 104, 71)));
        b.setOnClickListener(v -> {
            boolean show = !prefs.getBoolean("polishTripDetails", false);
            prefs.edit().putBoolean("polishTripDetails", show).apply();
            if (audited != null) audited.setVisibility(show ? View.VISIBLE : View.GONE);
            if (dual != null) dual.setVisibility(show ? View.VISIBLE : View.GONE);
            b.setText(show ? "Hide calculation details" : "Show calculation details");
        });
        card.addView(b, topMargin(-1, dp(48), 10));
    }

    // ---------------------------------------------------------------------
    // Complete round-trip discovery: outbound + return for every category
    // ---------------------------------------------------------------------

    private void attachRoundTripDiscovery(View root) {
        String[][] pairs = new String[][]{
                {"Scenic & viewpoints", "Scenic"}, {"Food & coffee", "Food"}, {"Parks & landmarks", "Parks"},
                {"Rest areas", "Rest"}, {"Supplies & pharmacy", "Supplies"}, {"Useful towns", "Towns"},
                {"Fuel near planned fill-up points", "FuelStops"}
        };
        for (String[] pair : pairs) {
            Button b = findButton(root, pair[0]);
            if (b == null || routeButtons.contains(b)) continue;
            routeButtons.add(b);
            String category = pair[1];
            b.setOnClickListener(v -> searchCompleteRoute(category));
        }
    }

    private void searchCompleteRoute(String category) {
        JSONObject trip = activeTrip();
        if (!routeComplete(trip)) { toast("Refresh the complete round trip first"); return; }
        TextView box = findTaggedText(getWindow().getDecorView(), "route_discovery_results");
        if (box != null) box.setText("Searching outbound + return route…");
        io.execute(() -> {
            try {
                ArrayList<GeoPoint> route = roundTripGeometry(trip);
                double routeMiles = positive(trip.optString("outMiles", "0")) + positive(trip.optString("backMiles", "0"));
                ArrayList<RouteAnchor> anchors = "FuelStops".equals(category) ? polishedFuelAnchors(trip, route) : routeAnchors(route);
                JSONArray found = overpassRoundTrip(anchors, discoveryFilters(category), "FuelStops".equals(category) ? 12000 : 9000, route, routeMiles, trip);
                String id = trip.optString("id", "");
                JSONObject byCategory;
                try { byCategory = new JSONObject(trip.optString("routePlacesByCategory", "{}")); }
                catch (Exception e) { byCategory = new JSONObject(); }
                byCategory.put(category, found);
                persistProfileField(TRIPS, id, "routePlacesByCategory", byCategory.toString());
                persistProfileField(TRIPS, id, "routePlaces", found.toString());
                persistProfileField(TRIPS, id, "routePlacesCategory", category);
                runOnUiThread(() -> plotRoundTripPlaces(found, category, trip));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    TextView result = findTaggedText(getWindow().getDecorView(), "route_discovery_results");
                    if (result != null) result.setText("Route discovery is unavailable right now. Saved trip data is unchanged.");
                });
            }
        });
    }

    private ArrayList<RouteAnchor> routeAnchors(List<GeoPoint> route) {
        ArrayList<RouteAnchor> out = new ArrayList<>();
        for (double f : new double[]{0.08, 0.22, 0.38, 0.50, 0.62, 0.78, 0.92}) {
            GeoPoint p = pointAtFraction(route, f);
            if (p != null) out.add(new RouteAnchor(p.getLatitude(), p.getLongitude()));
        }
        return out;
    }

    private ArrayList<RouteAnchor> polishedFuelAnchors(JSONObject trip, List<GeoPoint> route) {
        ArrayList<RouteAnchor> out = new ArrayList<>();
        double outbound = positive(trip.optString("outMiles", "0"));
        double round = outbound + positive(trip.optString("backMiles", "0"));
        JSONObject vehicle = profileById(VEHICLES, trip.optString("vehicleId", prefs.getString("activeVehicleId", "")));
        double mpg = TrailboundTripMath.adjustedMpg(positive(vehicle.optString("mpg", "0")), positive(vehicle.optString("payload", "0")));
        double tank = positive(trip.optString("tankSize", prefs.getString("fuelTankSize", "0")));
        double depart = positive(trip.optString("departureFuel", prefs.getString("departureFuel", "0")));
        for (double mile : TrailboundTripMath.fuelStopMiles(round, mpg, tank, depart)) {
            GeoPoint p = pointAtTripMile(route, round, mile);
            if (p != null) out.add(new RouteAnchor(p.getLatitude(), p.getLongitude()));
            if (out.size() >= 12) break;
        }
        if (out.isEmpty()) out.addAll(routeAnchors(route));
        return out;
    }

    private JSONArray overpassRoundTrip(List<RouteAnchor> anchors, List<String> filters, int radius,
                                        List<GeoPoint> route, double routeMiles, JSONObject trip) throws Exception {
        StringBuilder q = new StringBuilder("[out:json][timeout:25];(");
        for (RouteAnchor a : anchors) {
            for (String filter : filters) {
                q.append("node(around:").append(radius).append(',').append(a.lat).append(',').append(a.lon).append(')').append(filter).append(';');
                q.append("way(around:").append(radius).append(',').append(a.lat).append(',').append(a.lon).append(')').append(filter).append(';');
                q.append("relation(around:").append(radius).append(',').append(a.lat).append(',').append(a.lon).append(')').append(filter).append(';');
            }
        }
        q.append(");out center tags 140;");
        JSONObject json = new JSONObject(http("https://overpass-api.de/api/interpreter?data=" + enc(q.toString())));
        JSONArray elements = json.optJSONArray("elements");
        Set<String> seen = new HashSet<>();
        ArrayList<RoutePlace> places = new ArrayList<>();
        double outMiles = positive(trip.optString("outMiles", "0"));
        if (elements != null) {
            for (int i = 0; i < elements.length(); i++) {
                JSONObject el = elements.optJSONObject(i); if (el == null) continue;
                double lat = el.optDouble("lat", Double.NaN), lon = el.optDouble("lon", Double.NaN);
                JSONObject center = el.optJSONObject("center");
                if ((Double.isNaN(lat) || Double.isNaN(lon)) && center != null) {
                    lat = center.optDouble("lat", Double.NaN); lon = center.optDouble("lon", Double.NaN);
                }
                if (Double.isNaN(lat) || Double.isNaN(lon)) continue;
                String name = placeName(el.optJSONObject("tags"));
                if (name.isEmpty()) continue;
                String key = name.toLowerCase(Locale.US) + "|" + Math.round(lat * 10000) + "|" + Math.round(lon * 10000);
                if (!seen.add(key)) continue;
                double mile = nearestScaledMile(route, routeMiles, lat, lon);
                String leg = mile > outMiles ? "Return" : "Outbound";
                places.add(new RoutePlace(name, lat, lon, mile, leg));
            }
        }
        Collections.sort(places, Comparator.comparingDouble(p -> p.mile));
        JSONArray out = new JSONArray();
        for (int i = 0; i < places.size() && i < 28; i++) {
            RoutePlace p = places.get(i);
            JSONObject o = new JSONObject();
            o.put("name", p.name); o.put("lat", p.lat); o.put("lon", p.lon); o.put("mile", p.mile); o.put("leg", p.leg);
            out.put(o);
        }
        return out;
    }

    private void plotRoundTripPlaces(JSONArray places, String category, JSONObject trip) {
        MapView map = firstMap(getWindow().getDecorView());
        TextView box = findTaggedText(getWindow().getDecorView(), "route_discovery_results");
        if (map == null) return;
        ArrayList<Overlay> remove = new ArrayList<>();
        for (Overlay overlay : map.getOverlays()) {
            if (overlay instanceof Marker) {
                Object related = ((Marker) overlay).getRelatedObject();
                if ("route_discovery".equals(related) || "polish_route_discovery".equals(related)) remove.add(overlay);
            }
        }
        map.getOverlays().removeAll(remove);
        StringBuilder text = new StringBuilder();
        double outMiles = positive(trip.optString("outMiles", "0"));
        for (int i = 0; i < places.length(); i++) {
            JSONObject o = places.optJSONObject(i); if (o == null) continue;
            String name = o.optString("name", "Place");
            double mile = o.optDouble("mile", 0);
            String leg = o.optString("leg", mile > outMiles ? "Return" : "Outbound");
            Marker m = new Marker(map);
            m.setPosition(new GeoPoint(o.optDouble("lat", 0), o.optDouble("lon", 0)));
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            m.setIcon(markerIcon("Return".equals(leg) ? BLUE : GOLD, "•"));
            m.setTitle(name);
            m.setSnippet(leg + " • round-trip mile " + Math.round(mile));
            m.setRelatedObject("polish_route_discovery");
            map.getOverlays().add(m);
            if (i < 14) {
                if (text.length() > 0) text.append('\n');
                text.append("• ").append(name).append(" — ").append(leg).append(" • mile ").append(Math.round(mile));
            }
        }
        map.invalidate();
        if (box != null) box.setText(places.length() + " saved " + routeCategoryLabel(category) + " results across outbound + return:\n" + text);
        toast(places.length() == 0 ? "No named results found in this round-trip corridor." : "Found " + places.length() + " round-trip stops.");
    }

    // ---------------------------------------------------------------------
    // Garage: maintenance/reporting hub
    // ---------------------------------------------------------------------

    private void polishVehicle(View root) {
        TextView header = findExactText(root, "Vehicle profile");
        if (header == null || !(header.getParent() instanceof LinearLayout)) return;
        LinearLayout card = (LinearLayout) header.getParent();
        card.setBackground(round(Color.argb(248, 18, 23, 16), 22, Color.rgb(78, 94, 63)));

        ImageView image = firstImage(card);
        if (image != null) {
            LinearLayout.LayoutParams lp = image.getLayoutParams() instanceof LinearLayout.LayoutParams ?
                    (LinearLayout.LayoutParams) image.getLayoutParams() : new LinearLayout.LayoutParams(-1, dp(220));
            lp.height = dp(225); lp.topMargin = dp(8); lp.bottomMargin = dp(10); image.setLayoutParams(lp);
            image.setBackground(round(Color.rgb(24, 31, 21), 20, Color.rgb(93, 112, 74)));
            image.setOutlineProvider(ViewOutlineProvider.BACKGROUND); image.setClipToOutline(true);
        }

        LinearLayout dash = ensureVehicleDashboard(card, image);
        updateVehicleDashboard(root, dash);
        insertVehicleSectionLabels(root, card);
        ensureServiceLogAction(root, card);

        LinearLayout hub = findTaggedLinear(root, "vehicle_hub_panel");
        if (hub != null) hub.setBackground(round(Color.rgb(25, 33, 22), 18, Color.rgb(94, 115, 74)));
        LinearLayout safety = findTaggedLinear(root, "vehicle_safety_section");
        if (safety != null) safety.setBackground(round(Color.rgb(39, 34, 23), 18, Color.rgb(145, 117, 72)));
    }

    private LinearLayout ensureVehicleDashboard(LinearLayout card, ImageView image) {
        View existing = card.findViewWithTag("polish_vehicle_dashboard");
        if (existing instanceof LinearLayout) return (LinearLayout) existing;
        LinearLayout dash = panel("polish_vehicle_dashboard", SURFACE_2, Color.rgb(104, 127, 81));
        dash.addView(label("GARAGE REPORT", 11, true, Color.rgb(186, 211, 146)));
        TextView name = label("Your vehicle", 22, true, Color.WHITE); name.setTag("polish_vehicle_name"); dash.addView(name, topMargin(-1, -2, 3));
        TextView state = label("", 13, false, MUTED); state.setTag("polish_vehicle_state"); dash.addView(state, topMargin(-1, -2, 4));
        dash.addView(metricRow(metric("polish_vehicle_odometer", "ODOMETER"), metric("polish_vehicle_service", "SERVICE")), topMargin(-1, -2, 12));
        dash.addView(metricRow(metric("polish_vehicle_mpg", "LOADED MPG"), metric("polish_vehicle_checks", "SAFETY")), topMargin(-1, -2, 8));
        TextView linked = label("", 12, false, CREAM); linked.setTag("polish_vehicle_linked"); dash.addView(linked, topMargin(-1, -2, 10));
        int insert = image != null && image.getParent() == card ? card.indexOfChild(image) + 1 : Math.min(2, card.getChildCount());
        card.addView(dash, Math.max(0, Math.min(insert, card.getChildCount())), topMargin(-1, -2, 4));
        return dash;
    }

    private void updateVehicleDashboard(View root, LinearLayout dash) {
        JSONObject vehicle = activeVehicle();
        String year = text(exactField(root, "YEAR"), vehicle.optString("year", ""));
        String make = text(exactField(root, "MAKE"), vehicle.optString("make", ""));
        String model = text(exactField(root, "MODEL"), vehicle.optString("model", ""));
        String name = (year + " " + make + " " + model).trim(); if (name.isEmpty()) name = "Unsaved vehicle";
        double od = positive(text(exactField(root, "CURRENT MILEAGE"), vehicle.optString("odometer", "0")));
        double due = positive(text(exactField(root, "NEXT OIL CHANGE DUE AT"), vehicle.optString("nextOil", "0")));
        EditText intervalField = findTaggedEditText(root, "oil_service_interval_field");
        double interval = positive(text(intervalField, vehicle.optString("oilInterval", "5000"))); if (interval <= 0) interval = 5000;
        double base = positive(text(exactField(root, "EPA COMBINED MPG"), vehicle.optString("mpg", "0")));
        double payload = positive(text(exactField(root, "TRIP PAYLOAD (LB)"), vehicle.optString("payload", "0")));
        double loaded = TrailboundTripMath.adjustedMpg(base, payload);
        int checks = 0, totalChecks = 0;
        ArrayList<CheckBox> boxes = new ArrayList<>(); collect(root, CheckBox.class, boxes);
        for (CheckBox b : boxes) {
            Object tag = b.getTag();
            if (tag != null && tag.toString().startsWith("vehicle_safety_")) { totalChecks++; if (b.isChecked()) checks++; }
        }
        JSONObject trip = linkedTripForVehicle(vehicle.optString("id", prefs.getString("activeVehicleId", "")));
        double round = positive(trip.optString("outMiles", "0")) + positive(trip.optString("backMiles", "0"));
        long serviceMiles = Math.round(due - od);
        String service = due <= 0 ? "Not set" : serviceMiles <= 0 ? "Due now" : serviceMiles + " mi";
        String state = due > 0 && due <= od ? "Service due before departure" :
                due > 0 && round > 0 && due <= od + round ? "Service comes due during linked trip" : "Vehicle planning data looks current";
        setText(findTaggedText(dash, "polish_vehicle_name"), name);
        setText(findTaggedText(dash, "polish_vehicle_state"), state);
        setMetricValue(findTaggedText(dash, "polish_vehicle_odometer_value"), Math.round(od) + " mi");
        setMetricValue(findTaggedText(dash, "polish_vehicle_service_value"), service);
        setMetricValue(findTaggedText(dash, "polish_vehicle_mpg_value"), one(loaded));
        setMetricValue(findTaggedText(dash, "polish_vehicle_checks_value"), totalChecks > 0 ? checks + "/" + totalChecks : "—");
        TextView linked = findTaggedText(dash, "polish_vehicle_linked");
        if (linked != null) {
            if (trip.optString("id", "").isEmpty()) linked.setText("No trip linked yet • payload " + Math.round(payload) + " lb • service interval " + Math.round(interval) + " mi");
            else linked.setText("Linked: " + shortPlace(trip.optString("end", "Trip")) + (round > 0 ? " • " + one(round) + " mi round trip" : "") +
                    " • projected odometer " + Math.round(od + round) + " mi");
        }
    }

    private void insertVehicleSectionLabels(View root, LinearLayout card) {
        insertHeadingBefore(root, card, "YEAR", "polish_vehicle_details_heading", "VEHICLE DETAILS");
        insertHeadingBefore(root, card, "CURRENT MILEAGE", "polish_vehicle_maintenance_heading", "MAINTENANCE & MILEAGE");
    }

    private void ensureServiceLogAction(View root, LinearLayout card) {
        if (card.findViewWithTag("polish_log_service") != null) return;
        EditText interval = findTaggedEditText(root, "oil_service_interval_field");
        if (interval == null || interval.getParent() != card) return;
        Button b = new Button(this);
        b.setTag("polish_log_service");
        b.setText("Log oil/service completed now");
        b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setTextSize(14); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(round(GREEN_DARK, 14, Color.rgb(151, 178, 112)));
        int index = card.indexOfChild(interval) + 1;
        card.addView(b, Math.min(index, card.getChildCount()), topMargin(-1, dp(50), 8));
        b.setOnClickListener(v -> confirmServiceLog());
    }

    private void confirmServiceLog() {
        View root = getWindow().getDecorView();
        double od = positive(text(exactField(root, "CURRENT MILEAGE")));
        double interval = positive(text(findTaggedEditText(root, "oil_service_interval_field")));
        if (od <= 0 || interval <= 0) { toast("Enter current mileage and service interval first"); return; }
        new AlertDialog.Builder(this)
                .setTitle("Record completed service?")
                .setMessage("Trailbound will record service at " + Math.round(od) + " mi and set the next due mileage to " + Math.round(od + interval) + " mi.")
                .setPositiveButton("Record service", (d, w) -> recordService(od, interval))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void recordService(double odometer, double interval) {
        String id = prefs.getString("activeVehicleId", "");
        EditText dueField = exactField(getWindow().getDecorView(), "NEXT OIL CHANGE DUE AT");
        String next = String.valueOf(Math.round(odometer + interval));
        if (dueField != null) dueField.setText(next);
        if (!id.isEmpty()) {
            try {
                JSONObject vehicle = profileById(VEHICLES, id);
                JSONArray history;
                try { history = new JSONArray(vehicle.optString("serviceHistory", "[]")); }
                catch (Exception e) { history = new JSONArray(); }
                JSONObject event = new JSONObject();
                event.put("mileage", Math.round(odometer));
                event.put("type", "Oil/service interval reset");
                event.put("timestamp", System.currentTimeMillis());
                history.put(event);
                persistProfileField(VEHICLES, id, "serviceHistory", history.toString());
                persistProfileField(VEHICLES, id, "lastServiceMileage", String.valueOf(Math.round(odometer)));
                persistProfileField(VEHICLES, id, "lastServiceAt", System.currentTimeMillis());
                persistProfileField(VEHICLES, id, "nextOil", next);
                persistProfileField(VEHICLES, id, "oilInterval", String.valueOf(Math.round(interval)));
            } catch (Exception ignored) { }
        }
        toast("Service recorded • next due at " + next + " mi");
        main.postDelayed(this::patchPolish, 200);
    }

    // ---------------------------------------------------------------------
    // Stay: hotel photo, local briefing, map, nearby hub
    // ---------------------------------------------------------------------

    private void polishHotel(View root) {
        TextView header = findExactText(root, "Hotel profile");
        if (header == null || !(header.getParent() instanceof LinearLayout)) return;
        LinearLayout card = (LinearLayout) header.getParent();
        card.setBackground(round(Color.argb(248, 18, 23, 16), 22, Color.rgb(78, 94, 63)));

        LinearLayout hero = ensureHotelHero(card);
        updateHotelHub(root, hero);
        moveHotelMapIntoHub(root, card, hero);
        loadHotelPhoto(false);
        maybeRefreshHotelArea();

        Button save = findButton(root, "Save hotel profile");
        if (save == null) save = findButton(root, "Hotel saved ✓");
        if (save != null && !hotelSaveTouchButtons.contains(save)) {
            hotelSaveTouchButtons.add(save);
            save.setOnTouchListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                    main.postDelayed(this::patchPolish, 500);
                    main.postDelayed(() -> loadHotelPhoto(false), 800);
                }
                return false;
            });
        }

        LinearLayout extra = findTaggedLinear(root, "hotel_discovery_extra");
        if (extra != null) extra.setBackgroundColor(Color.TRANSPARENT);
    }

    private LinearLayout ensureHotelHero(LinearLayout card) {
        View existing = card.findViewWithTag("polish_hotel_hub");
        if (existing instanceof LinearLayout) return (LinearLayout) existing;
        LinearLayout hero = panel("polish_hotel_hub", SURFACE_2, Color.rgb(104, 127, 81));
        hero.addView(label("STAY HUB", 11, true, Color.rgb(186, 211, 146)));
        ImageView photo = new ImageView(this);
        photo.setTag("polish_hotel_photo");
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photo.setBackground(round(Color.rgb(29, 35, 27), 18, Color.rgb(95, 111, 80)));
        photo.setOutlineProvider(ViewOutlineProvider.BACKGROUND); photo.setClipToOutline(true);
        hero.addView(photo, topMargin(-1, dp(220), 8));
        TextView caption = label("No hotel photo cached yet", 11, false, MUTED); caption.setTag("polish_hotel_photo_caption"); hero.addView(caption, topMargin(-1, -2, 5));
        TextView name = label("Your stay", 22, true, Color.WHITE); name.setTag("polish_hotel_name"); hero.addView(name, topMargin(-1, -2, 8));
        TextView address = label("", 13, false, MUTED); address.setTag("polish_hotel_address"); hero.addView(address, topMargin(-1, -2, 3));
        hero.addView(metricRow(metric("polish_hotel_cost", "STAY COST"), metric("polish_hotel_nearby", "SAVED NEARBY")), topMargin(-1, -2, 12));
        TextView area = label("Add/save the hotel to build a local briefing.", 13, false, CREAM);
        area.setTag("polish_hotel_area"); area.setLineSpacing(0, 1.12f); area.setPadding(0, dp(8), 0, dp(3)); hero.addView(area);
        LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button photoBtn = compactAction("Refresh hotel photo");
        Button areaBtn = compactAction("Refresh area briefing");
        actions.addView(photoBtn, weightedButtonLp()); actions.addView(areaBtn, weightedButtonLp());
        hero.addView(actions, topMargin(-1, dp(52), 10));
        photoBtn.setOnClickListener(v -> loadHotelPhoto(true));
        areaBtn.setOnClickListener(v -> refreshHotelArea(true));
        int insert = Math.min(2, card.getChildCount());
        card.addView(hero, insert, topMargin(-1, -2, 10));
        return hero;
    }

    private void updateHotelHub(View root, LinearLayout hero) {
        JSONObject hotel = activeHotel();
        String name = text(exactField(root, "HOTEL NAME"), hotel.optString("name", ""));
        String address = text(exactField(root, "HOTEL ADDRESS"), hotel.optString("address", ""));
        double cost = positive(text(exactField(root, "TOTAL HOTEL PRICE"), hotel.optString("cost", "0")));
        JSONArray nearby;
        try { nearby = new JSONArray(hotel.optString("nearbyPlaces", "[]")); }
        catch (Exception e) { nearby = new JSONArray(); }
        setText(findTaggedText(hero, "polish_hotel_name"), name.isEmpty() ? "Your stay" : name);
        setText(findTaggedText(hero, "polish_hotel_address"), address.isEmpty() ? "Find and save a hotel address" : address);
        setMetricValue(findTaggedText(hero, "polish_hotel_cost_value"), cost > 0 ? money(cost) : "—");
        setMetricValue(findTaggedText(hero, "polish_hotel_nearby_value"), String.valueOf(nearby.length()));
        TextView area = findTaggedText(hero, "polish_hotel_area");
        if (area != null) {
            String briefing = hotel.optString("areaBriefing", "").trim();
            if (briefing.isEmpty()) briefing = "Use the saved hotel as your local base for weather, nearby food, fuel, shopping, medical needs, parks, attractions and practical area context.";
            area.setText(briefing);
        }
        TextView caption = findTaggedText(hero, "polish_hotel_photo_caption");
        if (caption != null) {
            String kind = hotel.optString("hotelPhotoKind", "");
            String credit = hotel.optString("hotelPhotoCredit", "");
            String license = hotel.optString("hotelPhotoLicense", "");
            if (!kind.isEmpty()) caption.setText(kind + (credit.isEmpty() ? "" : " • " + cleanCredit(credit)) + (license.isEmpty() ? "" : " • " + license));
        }
    }

    private void moveHotelMapIntoHub(View root, LinearLayout card, LinearLayout hero) {
        MapView map = firstMap(root);
        if (map == null) return;
        if (map.getParent() != hero) {
            if (map.getParent() instanceof ViewGroup) ((ViewGroup) map.getParent()).removeView(map);
            int imageIndex = indexOfTag(hero, "polish_hotel_photo");
            int insert = imageIndex >= 0 ? imageIndex + 2 : Math.min(3, hero.getChildCount());
            hero.addView(map, Math.min(insert, hero.getChildCount()), topMargin(-1, dp(235), 9));
        }
        map.setBackground(round(Color.rgb(17, 23, 17), 18, Color.rgb(88, 106, 71)));
        map.setOutlineProvider(ViewOutlineProvider.BACKGROUND); map.setClipToOutline(true);
        for (Overlay overlay : map.getOverlays()) {
            if (overlay instanceof Marker) {
                Marker m = (Marker) overlay;
                if (m.getTitle() != null && !m.getTitle().isEmpty() && m.getRelatedObject() == null) m.setIcon(markerIcon(RED, "H"));
            }
        }
    }

    private void loadHotelPhoto(boolean userRequested) {
        View root = getWindow().getDecorView();
        ImageView image = findTaggedImage(root, "polish_hotel_photo");
        if (image == null) return;
        String id = prefs.getString("activeHotelId", "");
        JSONObject hotel = profileById(HOTELS, id);
        String storageId = id.isEmpty() ? "draft" : id;
        File cached = new File(getFilesDir(), hotelImageFile(storageId));
        if (cached.exists()) {
            Bitmap bm = BitmapFactory.decodeFile(cached.getAbsolutePath());
            if (bm != null) { image.setImageBitmap(bm); updateHotelHub(root, findTaggedLinear(root, "polish_hotel_hub")); return; }
        }
        String name = text(exactField(root, "HOTEL NAME"), hotel.optString("name", "")).trim();
        String address = text(exactField(root, "HOTEL ADDRESS"), hotel.optString("address", "")).trim();
        if (name.isEmpty() && address.isEmpty()) return;
        String sig = storageId + "|" + name + "|" + address;
        if (!userRequested && sig.equals(attemptedHotelPhoto)) return;
        attemptedHotelPhoto = sig;
        if (userRequested) toast("Finding a hotel or area photo…");
        io.execute(() -> {
            try {
                String locality = hotel.optString("areaLocality", "");
                PhotoResult result = null;
                if (!name.isEmpty() && !"Hotel".equalsIgnoreCase(name)) result = commonsPhoto(name + (locality.isEmpty() ? "" : " " + locality));
                String kind = "Hotel photo";
                if (result == null) {
                    String areaQuery = !locality.isEmpty() ? locality + " " + hotel.optString("areaState", "") : areaQueryFromAddress(address);
                    result = commonsPhoto(areaQuery);
                    kind = "Area photo";
                }
                if (result == null) throw new Exception("No photo");
                Bitmap bm = downloadBitmap(result.url);
                if (bm == null) throw new Exception("Image decode");
                try (FileOutputStream out = openFileOutput(hotelImageFile(storageId), MODE_PRIVATE)) { bm.compress(Bitmap.CompressFormat.JPEG, 90, out); }
                final PhotoResult photo = result; final String photoKind = kind; final Bitmap finalBm = bm;
                if (!id.isEmpty()) {
                    persistProfileField(HOTELS, id, "hotelPhotoKind", photoKind);
                    persistProfileField(HOTELS, id, "hotelPhotoCredit", photo.credit);
                    persistProfileField(HOTELS, id, "hotelPhotoLicense", photo.license);
                    persistProfileField(HOTELS, id, "hotelPhotoSource", photo.source);
                }
                runOnUiThread(() -> {
                    ImageView target = findTaggedImage(getWindow().getDecorView(), "polish_hotel_photo");
                    if (target != null) target.setImageBitmap(finalBm);
                    LinearLayout hub = findTaggedLinear(getWindow().getDecorView(), "polish_hotel_hub");
                    if (hub != null) updateHotelHub(getWindow().getDecorView(), hub);
                    if (userRequested) toast(photoKind + " cached for this stay");
                });
            } catch (Exception e) {
                if (userRequested) runOnUiThread(() -> toast("No useful hotel/area photo found right now"));
            }
        });
    }

    private PhotoResult commonsPhoto(String query) {
        if (query == null || query.trim().isEmpty()) return null;
        try {
            String api = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrnamespace=6&gsrlimit=16&gsrsearch=" + enc(query) +
                    "&prop=imageinfo&iiprop=url%7Cextmetadata&iiurlwidth=1200&format=json&origin=*";
            JSONObject pages = new JSONObject(http(api)).optJSONObject("query");
            if (pages == null) return null;
            pages = pages.optJSONObject("pages"); if (pages == null) return null;
            Iterator<String> keys = pages.keys();
            while (keys.hasNext()) {
                JSONObject p = pages.optJSONObject(keys.next()); if (p == null) continue;
                String title = p.optString("title", "").toLowerCase(Locale.US);
                if (title.contains("logo") || title.contains("icon") || title.contains("map of") || title.contains("coat of arms") || title.contains("flag of")) continue;
                JSONArray info = p.optJSONArray("imageinfo"); if (info == null || info.length() == 0) continue;
                JSONObject ii = info.optJSONObject(0); if (ii == null) continue;
                String url = ii.optString("thumburl", ii.optString("url", "")); if (url.isEmpty()) continue;
                JSONObject meta = ii.optJSONObject("extmetadata");
                String artist = metaValue(meta, "Artist");
                String license = metaValue(meta, "LicenseShortName");
                String source = ii.optString("descriptionurl", "");
                return new PhotoResult(url, stripHtml(artist), stripHtml(license), source);
            }
        } catch (Exception ignored) { }
        return null;
    }

    private void maybeRefreshHotelArea() {
        String id = prefs.getString("activeHotelId", "");
        if (id.isEmpty()) return;
        JSONObject hotel = profileById(HOTELS, id);
        if (hotel.optString("areaBriefing", "").isEmpty() && !id.equals(attemptedHotelArea)) {
            attemptedHotelArea = id;
            refreshHotelArea(false);
        }
    }

    private void refreshHotelArea(boolean userRequested) {
        String id = prefs.getString("activeHotelId", "");
        JSONObject hotel = profileById(HOTELS, id);
        double lat = signed(hotel.optString("lat", prefs.getString("draftHotelLat", "0")));
        double lon = signed(hotel.optString("lon", prefs.getString("draftHotelLon", "0")));
        if (lat == 0 && lon == 0) { if (userRequested) toast("Find and save the hotel address first"); return; }
        if (userRequested) toast("Refreshing local stay briefing…");
        io.execute(() -> {
            try {
                JSONObject reverse = new JSONObject(http("https://nominatim.openstreetmap.org/reverse?format=jsonv2&addressdetails=1&zoom=10&lat=" + lat + "&lon=" + lon));
                JSONObject a = reverse.optJSONObject("address");
                String locality = firstNonEmpty(a, "city", "town", "village", "municipality", "hamlet");
                String county = a == null ? "" : a.optString("county", "");
                String state = a == null ? "" : a.optString("state", "");
                String weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon +
                        "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&temperature_unit=fahrenheit&wind_speed_unit=mph&forecast_days=1&timezone=auto";
                JSONObject weather = new JSONObject(http(weatherUrl));
                JSONObject current = weather.optJSONObject("current");
                JSONObject daily = weather.optJSONObject("daily");
                double temp = current == null ? Double.NaN : current.optDouble("temperature_2m", Double.NaN);
                double feels = current == null ? Double.NaN : current.optDouble("apparent_temperature", Double.NaN);
                double wind = current == null ? Double.NaN : current.optDouble("wind_speed_10m", Double.NaN);
                int code = current == null ? -1 : current.optInt("weather_code", -1);
                double hi = arrayDouble(daily, "temperature_2m_max");
                double lo = arrayDouble(daily, "temperature_2m_min");
                double precip = arrayDouble(daily, "precipitation_probability_max");
                int nearbyCount = 0;
                try { nearbyCount = new JSONArray(hotel.optString("nearbyPlaces", "[]")).length(); } catch (Exception ignored) { }
                String category = hotel.optString("nearbyPlacesCategory", "");
                StringBuilder brief = new StringBuilder();
                brief.append("AREA BRIEF • ").append(locality.isEmpty() ? state : locality + (state.isEmpty() ? "" : ", " + state));
                if (!county.isEmpty()) brief.append(" • ").append(county);
                if (!Double.isNaN(temp)) {
                    brief.append("\nNow ").append(Math.round(temp)).append("°F");
                    if (!Double.isNaN(feels)) brief.append(" • feels ").append(Math.round(feels)).append("°F");
                    String w = weatherLabel(code); if (!w.isEmpty()) brief.append(" • ").append(w);
                    if (!Double.isNaN(wind)) brief.append(" • wind ").append(Math.round(wind)).append(" mph");
                }
                if (!Double.isNaN(hi) && !Double.isNaN(lo)) {
                    brief.append("\nToday ").append(Math.round(hi)).append("° / ").append(Math.round(lo)).append("°");
                    if (!Double.isNaN(precip)) brief.append(" • precip ").append(Math.round(precip)).append('%');
                }
                brief.append("\nNearby saved: ").append(nearbyCount).append(" places");
                if (!category.isEmpty()) brief.append(" • last category: ").append(category);
                final String briefing = brief.toString();
                if (!id.isEmpty()) {
                    persistProfileField(HOTELS, id, "areaBriefing", briefing);
                    persistProfileField(HOTELS, id, "areaLocality", locality);
                    persistProfileField(HOTELS, id, "areaState", state);
                    persistProfileField(HOTELS, id, "areaUpdatedAt", System.currentTimeMillis());
                }
                runOnUiThread(() -> {
                    LinearLayout hub = findTaggedLinear(getWindow().getDecorView(), "polish_hotel_hub");
                    if (hub != null) {
                        TextView area = findTaggedText(hub, "polish_hotel_area");
                        if (area != null) area.setText(briefing);
                    }
                    if (userRequested) toast("Stay briefing refreshed");
                });
            } catch (Exception e) {
                if (userRequested) runOnUiThread(() -> toast("Area briefing is unavailable right now"));
            }
        });
    }

    // ---------------------------------------------------------------------
    // Explore screen snapshot
    // ---------------------------------------------------------------------

    private void polishArea(View root) {
        TextView header = findExactText(root, "Linked adventure");
        if (header == null || !(header.getParent() instanceof LinearLayout)) return;
        LinearLayout card = (LinearLayout) header.getParent();
        card.setBackground(round(Color.argb(248, 18, 23, 16), 22, Color.rgb(78, 94, 63)));
        View existing = card.findViewWithTag("polish_area_snapshot");
        LinearLayout panel;
        if (existing instanceof LinearLayout) panel = (LinearLayout) existing;
        else {
            panel = panel("polish_area_snapshot", SURFACE_2, Color.rgb(103, 126, 80));
            panel.addView(label("ADVENTURE SNAPSHOT", 11, true, Color.rgb(186, 211, 146)));
            TextView summary = label("", 15, false, Color.WHITE); summary.setTag("polish_area_snapshot_text"); summary.setLineSpacing(0, 1.14f); panel.addView(summary, topMargin(-1, -2, 7));
            card.addView(panel, Math.min(1, card.getChildCount()), topMargin(-1, -2, 8));
        }
        JSONObject trip = activeTrip();
        JSONObject vehicle = profileById(VEHICLES, trip.optString("vehicleId", prefs.getString("activeVehicleId", "")));
        JSONObject hotel = profileById(HOTELS, trip.optString("hotelId", prefs.getString("activeHotelId", "")));
        double miles = positive(trip.optString("outMiles", "0")) + positive(trip.optString("backMiles", "0"));
        double hours = positive(trip.optString("outHours", "0")) + positive(trip.optString("backHours", "0"));
        int routePlaces = 0, hotelPlaces = 0;
        try { routePlaces = new JSONArray(trip.optString("routePlaces", "[]")).length(); } catch (Exception ignored) { }
        try { hotelPlaces = new JSONArray(hotel.optString("nearbyPlaces", "[]")).length(); } catch (Exception ignored) { }
        TextView summary = findTaggedText(panel, "polish_area_snapshot_text");
        if (summary != null) summary.setText(shortPlace(trip.optString("start", "Start")) + " → " + shortPlace(trip.optString("end", "Destination")) +
                (miles > 0 ? "\n" + one(miles) + " mi round trip" : "") + (hours > 0 ? " • " + one(hours) + " hr" : "") +
                "\nVehicle: " + vehicle.optString("label", "No vehicle linked") + "\nStay: " + hotel.optString("label", "No hotel linked") +
                "\nSaved discoveries: " + routePlaces + " route • " + hotelPlaces + " near hotel");
    }

    // ---------------------------------------------------------------------
    // UI helpers
    // ---------------------------------------------------------------------

    private LinearLayout panel(String tag, int fill, int stroke) {
        LinearLayout p = new LinearLayout(this);
        p.setTag(tag); p.setOrientation(LinearLayout.VERTICAL); p.setPadding(dp(14), dp(14), dp(14), dp(14));
        p.setBackground(round(fill, 18, stroke));
        return p;
    }

    private LinearLayout metricRow(View left, View right) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(left, weightedMetricLp()); row.addView(right, weightedMetricLp()); return row;
    }

    private LinearLayout metric(String tag, String title) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(11), dp(10), dp(11), dp(10));
        box.setBackground(round(Color.rgb(15, 20, 13), 14, Color.rgb(72, 88, 59)));
        box.addView(label(title, 10, true, Color.rgb(173, 184, 158)));
        TextView value = label("—", 18, true, Color.WHITE); value.setTag(tag + "_value"); box.addView(value, topMargin(-1, -2, 2));
        box.setTag(tag); return box;
    }

    private LinearLayout.LayoutParams weightedMetricLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.setMargins(dp(3), 0, dp(3), 0); return lp;
    }

    private LinearLayout.LayoutParams weightedButtonLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1); lp.setMargins(dp(3), 0, dp(3), 0); return lp;
    }

    private Button compactAction(String text) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setTextSize(13); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(round(Color.rgb(48, 59, 39), 14, Color.rgb(103, 124, 81))); return b;
    }

    private TextView label(String text, int sp, boolean bold, int color) {
        TextView t = new TextView(this); t.setText(text); t.setTextSize(sp); t.setTextColor(color); if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t;
    }

    private void insertHeadingBefore(View root, LinearLayout card, String fieldLabel, String tag, String title) {
        if (card.findViewWithTag(tag) != null) return;
        TextView anchor = findExactText(root, fieldLabel); if (anchor == null || anchor.getParent() != card) return;
        TextView h = label(title, 12, true, Color.rgb(187, 211, 147)); h.setTag(tag);
        int index = card.indexOfChild(anchor); card.addView(h, Math.max(0, index), topMargin(-1, -2, 16));
    }

    private Drawable markerIcon(int color, String text) {
        String key = color + "|" + text;
        Drawable cached = markerIcons.get(key); if (cached != null) return cached;
        int w = dp(38), h = dp(46); Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); Canvas c = new Canvas(bitmap);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(Color.argb(80, 0, 0, 0)); c.drawCircle(w / 2f + dp(1), dp(19) + dp(2), dp(16), p);
        p.setColor(color); c.drawCircle(w / 2f, dp(19), dp(16), p); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2)); p.setColor(Color.WHITE); c.drawCircle(w / 2f, dp(19), dp(14), p);
        p.setStyle(Paint.Style.FILL); p.setColor(Color.WHITE); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize("•".equals(text) ? dp(24) : dp(14));
        c.drawText(text, w / 2f, dp(24), p);
        p.setColor(color); c.drawPath(triangle(w / 2f, dp(33), dp(7)), p);
        Drawable d = new BitmapDrawable(getResources(), bitmap); markerIcons.put(key, d); return d;
    }

    private android.graphics.Path triangle(float x, float y, float size) {
        android.graphics.Path path = new android.graphics.Path(); path.moveTo(x - size, y); path.lineTo(x + size, y); path.lineTo(x, y + size); path.close(); return path;
    }

    private GradientDrawable round(int fill, int radius, int stroke) {
        GradientDrawable g = new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp(radius)); g.setStroke(dp(1), stroke); return g;
    }

    private LinearLayout.LayoutParams topMargin(int w, int h, int margin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h); lp.topMargin = dp(margin); return lp;
    }

    private void setMetricValue(TextView view, String value) { if (view != null) view.setText(value); }
    private void setText(TextView view, String value) { if (view != null) view.setText(value); }

    // ---------------------------------------------------------------------
    // Data / network helpers
    // ---------------------------------------------------------------------

    private JSONObject activeTrip() { return profileById(TRIPS, prefs.getString("activeTripId", "")); }
    private JSONObject activeVehicle() { return profileById(VEHICLES, prefs.getString("activeVehicleId", "")); }
    private JSONObject activeHotel() { return profileById(HOTELS, prefs.getString("activeHotelId", "")); }

    private JSONObject linkedVehicle(View root, JSONObject trip) {
        Spinner spinner = exactSpinner(root, "CAR FOR THIS TRIP");
        String id = selectedProfileId(VEHICLES, spinner == null ? 0 : spinner.getSelectedItemPosition());
        if (id.isEmpty()) id = trip.optString("vehicleId", prefs.getString("activeVehicleId", ""));
        return profileById(VEHICLES, id);
    }

    private JSONObject linkedHotel(View root, JSONObject trip) {
        Spinner spinner = exactSpinner(root, "HOTEL FOR THIS TRIP");
        String id = selectedProfileId(HOTELS, spinner == null ? 0 : spinner.getSelectedItemPosition());
        if (id.isEmpty()) id = trip.optString("hotelId", prefs.getString("activeHotelId", ""));
        return profileById(HOTELS, id);
    }

    private JSONObject linkedTripForVehicle(String vehicleId) {
        JSONObject active = activeTrip(); if (!vehicleId.isEmpty() && vehicleId.equals(active.optString("vehicleId", ""))) return active;
        JSONArray a = profiles(TRIPS); for (int i = 0; i < a.length(); i++) { JSONObject t = a.optJSONObject(i); if (t != null && vehicleId.equals(t.optString("vehicleId", ""))) return t; }
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
        JSONArray a = profiles(key); for (int i = 0; i < a.length(); i++) { JSONObject o = a.optJSONObject(i); if (o != null && id.equals(o.optString("id", ""))) return o; }
        return new JSONObject();
    }

    private synchronized void persistProfileField(String key, String id, String field, Object value) {
        if (id == null || id.isEmpty()) return;
        try {
            JSONArray input = profiles(key), output = new JSONArray();
            for (int i = 0; i < input.length(); i++) { JSONObject o = input.optJSONObject(i); if (o == null) continue; if (id.equals(o.optString("id", ""))) o.put(field, value); output.put(o); }
            prefs.edit().putString(key, output.toString()).commit();
        } catch (Exception ignored) { }
    }

    private String selectedProfileId(String key, int index) {
        if (index <= 0) return ""; JSONArray a = profiles(key); JSONObject o = a.optJSONObject(index - 1); return o == null ? "" : o.optString("id", "");
    }

    private ArrayList<GeoPoint> roundTripGeometry(JSONObject trip) {
        ArrayList<GeoPoint> out = geometry(trip.optString("routeOut", "")); ArrayList<GeoPoint> back = geometry(trip.optString("routeBack", ""));
        if (!out.isEmpty() && !back.isEmpty() && geoMiles(out.get(out.size() - 1), back.get(0)) < 0.01) back.remove(0); out.addAll(back); return out;
    }

    private ArrayList<GeoPoint> geometry(String json) {
        ArrayList<GeoPoint> out = new ArrayList<>();
        try { JSONArray a = new JSONArray(json == null || json.isEmpty() ? "[]" : json); for (int i = 0; i < a.length(); i++) { JSONArray p = a.optJSONArray(i); if (p != null && p.length() >= 2) out.add(new GeoPoint(p.optDouble(1), p.optDouble(0))); } } catch (Exception ignored) { }
        return out;
    }

    private GeoPoint pointAtTripMile(List<GeoPoint> route, double tripMiles, double mile) {
        if (route == null || route.isEmpty() || tripMiles <= 0) return null; return pointAtFraction(route, mile / tripMiles);
    }

    private GeoPoint pointAtFraction(List<GeoPoint> route, double fraction) {
        if (route == null || route.isEmpty()) return null; if (route.size() == 1) return route.get(0);
        fraction = Math.max(0, Math.min(1, fraction)); double total = polylineMiles(route); if (total <= 0) return route.get(0); double target = total * fraction, walked = 0;
        for (int i = 1; i < route.size(); i++) { GeoPoint a = route.get(i - 1), b = route.get(i); double seg = geoMiles(a, b); if (seg <= 0) continue; if (walked + seg >= target) { double f = (target - walked) / seg; return new GeoPoint(a.getLatitude() + (b.getLatitude() - a.getLatitude()) * f, a.getLongitude() + (b.getLongitude() - a.getLongitude()) * f); } walked += seg; }
        return route.get(route.size() - 1);
    }

    private double nearestScaledMile(List<GeoPoint> route, double routeMiles, double lat, double lon) {
        if (route == null || route.isEmpty() || routeMiles <= 0) return 0; GeoPoint target = new GeoPoint(lat, lon); int bestIndex = 0; double best = Double.MAX_VALUE;
        for (int i = 0; i < route.size(); i++) { double d = geoMiles(target, route.get(i)); if (d < best) { best = d; bestIndex = i; } }
        double before = 0; for (int i = 1; i <= bestIndex; i++) before += geoMiles(route.get(i - 1), route.get(i)); double total = polylineMiles(route); return total > 0 ? routeMiles * before / total : 0;
    }

    private double polylineMiles(List<GeoPoint> route) { double total = 0; if (route != null) for (int i = 1; i < route.size(); i++) total += geoMiles(route.get(i - 1), route.get(i)); return total; }
    private double geoMiles(GeoPoint a, GeoPoint b) { double r = 3958.7613; double lat1 = Math.toRadians(a.getLatitude()), lat2 = Math.toRadians(b.getLatitude()); double dLat = lat2 - lat1, dLon = Math.toRadians(b.getLongitude() - a.getLongitude()); double h = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2); return 2 * r * Math.asin(Math.min(1, Math.sqrt(h))); }

    private List<String> discoveryFilters(String category) {
        ArrayList<String> f = new ArrayList<>();
        switch (category) {
            case "Scenic": f.add("[\"tourism\"~\"viewpoint|attraction\"]"); f.add("[\"natural\"~\"peak|waterfall\"]"); f.add("[\"historic\"]"); break;
            case "Food": f.add("[\"amenity\"~\"restaurant|fast_food|cafe\"]"); break;
            case "Parks": f.add("[\"leisure\"~\"park|nature_reserve\"]"); f.add("[\"tourism\"~\"museum|zoo|theme_park|attraction\"]"); break;
            case "Rest": f.add("[\"highway\"=\"rest_area\"]"); f.add("[\"amenity\"=\"toilets\"]"); break;
            case "Supplies": f.add("[\"shop\"~\"supermarket|convenience|department_store\"]"); f.add("[\"amenity\"=\"pharmacy\"]"); break;
            case "Towns": f.add("[\"place\"~\"city|town\"]"); break;
            case "FuelStops": f.add("[\"amenity\"=\"fuel\"]"); break;
            default: f.add("[\"tourism\"=\"attraction\"]");
        }
        return f;
    }

    private String routeCategoryLabel(String category) {
        switch (category) { case "Scenic": return "scenic"; case "Food": return "food/coffee"; case "Parks": return "parks/landmarks"; case "Rest": return "rest-area"; case "Supplies": return "supplies/pharmacy"; case "Towns": return "town"; case "FuelStops": return "fuel"; default: return "route"; }
    }

    private String placeName(JSONObject tags) {
        if (tags == null) return ""; String name = tags.optString("name", "").trim(); if (name.isEmpty()) name = tags.optString("brand", "").trim(); if (name.isEmpty()) name = tags.optString("operator", "").trim(); if (name.isEmpty() && "rest_area".equals(tags.optString("highway", ""))) name = "Rest area"; if (name.isEmpty() && "fuel".equals(tags.optString("amenity", ""))) name = "Fuel station"; return name;
    }

    private String http(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection(); c.setConnectTimeout(12000); c.setReadTimeout(25000); c.setRequestProperty("User-Agent", "TrailboundAndroid/7.0"); c.setRequestProperty("Accept", "application/json,text/html,*/*");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()))) { StringBuilder s = new StringBuilder(); String line; while ((line = br.readLine()) != null) s.append(line); return s.toString(); } finally { c.disconnect(); }
    }

    private Bitmap downloadBitmap(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection(); c.setConnectTimeout(12000); c.setReadTimeout(22000); c.setRequestProperty("User-Agent", "TrailboundAndroid/7.0");
        try (InputStream in = c.getInputStream()) { return BitmapFactory.decodeStream(in); } finally { c.disconnect(); }
    }

    private String metaValue(JSONObject meta, String key) { JSONObject o = meta == null ? null : meta.optJSONObject(key); return o == null ? "" : o.optString("value", ""); }
    private String stripHtml(String s) { return s == null ? "" : s.replaceAll("<[^>]+>", "").replace("&amp;", "&").trim(); }
    private String cleanCredit(String s) { String clean = stripHtml(s); return clean.length() > 45 ? clean.substring(0, 45) + "…" : clean; }
    private String hotelImageFile(String id) { return "hotel_" + (id == null ? "draft" : id.replaceAll("[^A-Za-z0-9_-]", "")) + ".jpg"; }
    private String areaQueryFromAddress(String address) { if (address == null) return "travel destination"; String[] parts = address.split(","); if (parts.length >= 2) return parts[parts.length - 2].trim() + " " + parts[parts.length - 1].trim(); return address; }
    private String firstNonEmpty(JSONObject a, String... keys) { if (a == null) return ""; for (String k : keys) { String v = a.optString(k, "").trim(); if (!v.isEmpty()) return v; } return ""; }
    private double arrayDouble(JSONObject parent, String key) { if (parent == null) return Double.NaN; JSONArray a = parent.optJSONArray(key); return a == null || a.length() == 0 ? Double.NaN : a.optDouble(0, Double.NaN); }

    private String weatherLabel(int code) {
        if (code == 0) return "clear"; if (code == 1 || code == 2) return "partly cloudy"; if (code == 3) return "overcast";
        if (code == 45 || code == 48) return "fog"; if (code >= 51 && code <= 57) return "drizzle"; if (code >= 61 && code <= 67) return "rain";
        if (code >= 71 && code <= 77) return "snow"; if (code >= 80 && code <= 82) return "showers"; if (code >= 95) return "thunderstorms"; return "";
    }

    private EditText exactField(View root, String label) {
        TextView l = findExactText(root, label); if (l == null || !(l.getParent() instanceof ViewGroup)) return null; ViewGroup p = (ViewGroup) l.getParent(); int start = p.indexOfChild(l) + 1;
        for (int i = start; i < p.getChildCount(); i++) { View child = p.getChildAt(i); if (child instanceof EditText) return (EditText) child; if (child instanceof TextView && !(child instanceof EditText)) break; } return null;
    }

    private Spinner exactSpinner(View root, String label) {
        TextView l = findExactText(root, label); if (l == null || !(l.getParent() instanceof ViewGroup)) return null; ViewGroup p = (ViewGroup) l.getParent(); int start = p.indexOfChild(l) + 1;
        for (int i = start; i < p.getChildCount(); i++) { View child = p.getChildAt(i); if (child instanceof Spinner) return (Spinner) child; if (child instanceof TextView) break; } return null;
    }

    private TextView findExactText(View root, String target) { ArrayList<TextView> all = new ArrayList<>(); collect(root, TextView.class, all); for (TextView t : all) if (t.getText() != null && target.equalsIgnoreCase(t.getText().toString().trim())) return t; return null; }
    private TextView findTaggedText(View root, String tag) { ArrayList<TextView> all = new ArrayList<>(); collect(root, TextView.class, all); for (TextView t : all) if (tag.equals(t.getTag())) return t; return null; }
    private Button findButton(View root, String target) { ArrayList<Button> all = new ArrayList<>(); collect(root, Button.class, all); for (Button b : all) if (b.getText() != null && target.equalsIgnoreCase(b.getText().toString().trim())) return b; return null; }
    private Button findTaggedButton(View root, String tag) { ArrayList<Button> all = new ArrayList<>(); collect(root, Button.class, all); for (Button b : all) if (tag.equals(b.getTag())) return b; return null; }
    private EditText findTaggedEditText(View root, String tag) { ArrayList<EditText> all = new ArrayList<>(); collect(root, EditText.class, all); for (EditText e : all) if (tag.equals(e.getTag())) return e; return null; }
    private LinearLayout findTaggedLinear(View root, String tag) { ArrayList<LinearLayout> all = new ArrayList<>(); collect(root, LinearLayout.class, all); for (LinearLayout l : all) if (tag.equals(l.getTag())) return l; return null; }
    private ImageView findTaggedImage(View root, String tag) { ArrayList<ImageView> all = new ArrayList<>(); collect(root, ImageView.class, all); for (ImageView i : all) if (tag.equals(i.getTag())) return i; return null; }
    private CheckBox findCheckBoxContaining(View root, String target) { ArrayList<CheckBox> all = new ArrayList<>(); collect(root, CheckBox.class, all); for (CheckBox b : all) if (b.getText() != null && b.getText().toString().contains(target)) return b; return null; }
    private MapView firstMap(View root) { ArrayList<MapView> all = new ArrayList<>(); collect(root, MapView.class, all); return all.isEmpty() ? null : all.get(0); }
    private ImageView firstImage(View root) { ArrayList<ImageView> all = new ArrayList<>(); collect(root, ImageView.class, all); for (ImageView i : all) if (!(i instanceof MapView)) return i; return null; }
    private <T extends View> void collect(View root, Class<T> type, List<T> out) { if (type.isInstance(root)) out.add(type.cast(root)); if (root instanceof ViewGroup) { ViewGroup g = (ViewGroup) root; for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), type, out); } }

    private int indexOfTag(ViewGroup group, String tag) { for (int i = 0; i < group.getChildCount(); i++) if (tag.equals(group.getChildAt(i).getTag())) return i; return -1; }
    private String text(EditText e) { return e == null || e.getText() == null ? "" : e.getText().toString(); }
    private String text(EditText e, String fallback) { String s = text(e).trim(); return s.isEmpty() ? fallback : s; }
    private double positive(String s) { try { return TrailboundTripMath.nonNegative(Double.parseDouble(s == null ? "" : s.trim())); } catch (Exception e) { return 0; } }
    private double signed(String s) { try { return Double.parseDouble(s == null ? "" : s.trim()); } catch (Exception e) { return 0; } }
    private String shortPlace(String s) { if (s == null || s.trim().isEmpty()) return "Set location"; String t = s.trim(); int comma = t.indexOf(','); String shortName = comma > 0 ? t.substring(0, comma).trim() : t; return shortName.length() > 28 ? shortName.substring(0, 28) + "…" : shortName; }
    private String joinStrings(List<String> s) { StringBuilder b = new StringBuilder(); for (String v : s) { if (b.length() > 0) b.append(" • "); b.append(v); } return b.toString(); }
    private String one(double d) { return String.format(Locale.US, "%.1f", d); }
    private String money(double d) { return NumberFormat.getCurrencyInstance(Locale.US).format(d); }
    private String enc(String s) throws Exception { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    @Override protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private static class RouteAnchor {
        final double lat, lon;
        RouteAnchor(double lat, double lon) { this.lat = lat; this.lon = lon; }
    }

    private static class RoutePlace {
        final String name, leg; final double lat, lon, mile;
        RoutePlace(String name, double lat, double lon, double mile, String leg) { this.name = name; this.lat = lat; this.lon = lon; this.mile = mile; this.leg = leg; }
    }

    private static class PhotoResult {
        final String url, credit, license, source;
        PhotoResult(String url, String credit, String license, String source) { this.url = url; this.credit = credit; this.license = license; this.source = source; }
    }
}
