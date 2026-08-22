package com.tripfuel.app;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Trailbound 6.2 audit layer.
 * Fresh-state calculations, tested trip math, distance-based route sampling,
 * exact EPA variant selection, and more transparent maintenance estimates.
 */
public class TrailboundAuditActivity extends TrailboundDiscoveryActivity {
    private static final String PREFS = "trailbound_v5";
    private static final String TRIPS = "trips";
    private static final String VEHICLES = "vehicles";
    private static final String HOTELS = "hotels";

    private SharedPreferences prefs;
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean patching;
    private boolean restoringOilInterval;
    private String lastGasAuditSignature = "";

    private final Set<Button> auditedUpdateButtons = Collections.newSetFromMap(new WeakHashMap<Button, Boolean>());
    private final Set<Button> auditedRefreshButtons = Collections.newSetFromMap(new WeakHashMap<Button, Boolean>());
    private final Set<Button> auditedMapButtons = Collections.newSetFromMap(new WeakHashMap<Button, Boolean>());
    private final Set<Button> auditedEpaButtons = Collections.newSetFromMap(new WeakHashMap<Button, Boolean>());
    private final Set<Button> auditedRouteButtons = Collections.newSetFromMap(new WeakHashMap<Button, Boolean>());

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (!patching && !isFinishing()) patchAudit();
        });
        main.postDelayed(this::patchAudit, 650);
    }

    @Override protected void onResume() {
        super.onResume();
        main.postDelayed(this::patchAudit, 450);
    }

    private void patchAudit() {
        if (patching || isFinishing()) return;
        patching = true;
        try {
            View root = getWindow().getDecorView();
            if (findExactText(root, "Trip profile") != null) {
                patchTripCalculations(root);
                patchAccurateRouteDiscovery(root);
            }
            if (findExactText(root, "Vehicle profile") != null) patchVehicleAccuracy(root);
        } catch (Exception ignored) {
        } finally {
            patching = false;
        }
    }

    private void patchTripCalculations(View root) {
        Button update = findButton(root, "Update estimate");
        if (update != null && !auditedUpdateButtons.contains(update)) {
            auditedUpdateButtons.add(update);
            update.setOnClickListener(v -> updateAuditedTrip(getWindow().getDecorView()));
        }

        Button refresh = findButton(root, "Refresh route + automatic gas average");
        if (refresh != null && !auditedRefreshButtons.contains(refresh)) {
            auditedRefreshButtons.add(refresh);
            refresh.setOnClickListener(v -> refreshRouteAndGas(true));
        }

        Button map = findButton(root, "Map actual round trip");
        if (map != null && !auditedMapButtons.contains(map)) {
            auditedMapButtons.add(map);
            map.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    EditText from = exactField(getWindow().getDecorView(), "FROM");
                    EditText to = exactField(getWindow().getDecorView(), "TO");
                    String expectedStart = text(from).trim();
                    String expectedEnd = text(to).trim();
                    main.postDelayed(() -> pollMappedRoute(expectedStart, expectedEnd, 0, true), 1200);
                }
                return false;
            });
        }

        ensureAuditedSummary(root);
        updateAuditedTrip(root);

        JSONObject trip = activeTrip();
        if (routeComplete(trip)) {
            String sig = routeSignature(trip);
            String source = trip.optString("gasSource", "");
            if (!sig.equals(lastGasAuditSignature)) {
                lastGasAuditSignature = sig;
                if (!source.startsWith("Distance-weighted route average")) {
                    main.postDelayed(() -> refreshDistanceWeightedGas(activeTrip(), false), 900);
                }
            }
        }
    }

    private TextView ensureAuditedSummary(View root) {
        TextView existing = findTaggedText(root, "audited_trip_summary");
        if (existing != null) return existing;

        ArrayList<TextView> views = new ArrayList<>();
        collect(root, TextView.class, views);
        for (TextView t : views) {
            if (t.getTag() != null) continue;
            String s = t.getText() == null ? "" : t.getText().toString();
            if (s.startsWith("LINKED PLAN") || s.startsWith("Map or load a trip") || (s.contains("ROUND TRIP") && s.contains("FULL TRIP TOTAL"))) {
                t.setTag("audited_trip_summary");
                return t;
            }
        }

        TextView header = findExactText(root, "Trip profile");
        if (header == null || !(header.getParent() instanceof LinearLayout)) return null;
        LinearLayout card = (LinearLayout) header.getParent();
        TextView summary = new TextView(this);
        summary.setTag("audited_trip_summary");
        summary.setTextColor(Color.WHITE);
        summary.setTextSize(15);
        summary.setLineSpacing(0, 1.15f);
        summary.setPadding(dp(14), dp(14), dp(14), dp(14));
        summary.setBackground(round(Color.rgb(8, 10, 7), 14, Color.rgb(100, 107, 79)));
        card.addView(summary, topMargin(-1, -2, 12));
        return summary;
    }

    private void updateAuditedTrip(View root) {
        TextView summary = ensureAuditedSummary(root);
        TextView dual = findTaggedText(root, "dual_gas_summary");
        if (summary == null) return;

        JSONObject trip = activeTrip();
        if (trip.optString("id", "").isEmpty()) {
            summary.setText("TRIP CALCULATION\nMap and save a trip first. Trailbound will calculate from the freshly saved outbound and return routes.");
            if (dual != null) dual.setText("FUEL BUDGET COMPARISON\nMap the round trip first so the fuel calculation has real route miles.");
            return;
        }

        EditText fromField = exactField(root, "FROM");
        EditText toField = exactField(root, "TO");
        String uiStart = text(fromField).trim();
        String uiEnd = text(toField).trim();
        boolean endpointsMatch = uiStart.equals(trip.optString("start", "")) && uiEnd.equals(trip.optString("end", ""));
        if (!endpointsMatch || !routeComplete(trip)) {
            summary.setText("ROUTE NEEDS REFRESH\nThe visible FROM/TO fields do not have a complete matching saved outbound + return route yet. Trailbound is withholding mileage-based totals so stale route data cannot produce a misleading budget.\n\nTap “Refresh route + automatic gas average”.");
            if (dual != null) dual.setText("FUEL BUDGET COMPARISON\nRoute incomplete or stale — refresh the actual round trip before calculating fuel cost.");
            return;
        }

        Spinner carSpinner = exactSpinner(root, "CAR FOR THIS TRIP");
        Spinner hotelSpinner = exactSpinner(root, "HOTEL FOR THIS TRIP");
        String vehicleId = selectedProfileId(VEHICLES, carSpinner == null ? 0 : carSpinner.getSelectedItemPosition());
        if (vehicleId.isEmpty()) vehicleId = trip.optString("vehicleId", prefs.getString("activeVehicleId", ""));
        String hotelId = selectedProfileId(HOTELS, hotelSpinner == null ? 0 : hotelSpinner.getSelectedItemPosition());
        if (hotelId.isEmpty()) hotelId = trip.optString("hotelId", prefs.getString("activeHotelId", ""));
        JSONObject vehicle = profileById(VEHICLES, vehicleId);
        JSONObject hotel = profileById(HOTELS, hotelId);

        double out = positive(trip.optString("outMiles", "0"));
        double back = positive(trip.optString("backMiles", "0"));
        double roundMiles = out + back;
        double outHours = positive(trip.optString("outHours", "0"));
        double backHours = positive(trip.optString("backHours", "0"));
        double baseMpg = positive(vehicle.optString("mpg", "0"));
        double payload = positive(vehicle.optString("payload", "0"));
        double adjustedMpg = TrailboundTripMath.adjustedMpg(baseMpg, payload);
        double gallons = TrailboundTripMath.gallonsConsumed(roundMiles, adjustedMpg);

        EditText avgField = exactField(root, "PREPARED GAS $ / GAL");
        EditText conservativeField = findTaggedEditText(root, "conservative_gas_field");
        EditText snacksField = exactField(root, "SNACKS & DRINKS");
        EditText extrasField = exactField(root, "OTHER TRIP MONEY");
        EditText tankField = exactField(root, "Tank size (gal)");
        EditText departureField = exactField(root, "Departure gas (gal)");
        CheckBox includeHotel = findCheckBoxContaining(root, "Include linked hotel");

        double averagePrice = positive(text(avgField));
        double conservativePrice = positive(text(conservativeField));
        double snacks = positive(text(snacksField));
        double extras = positive(text(extrasField));
        double hotelCost = positive(hotel.optString("cost", "0"));
        boolean hotelIncluded = includeHotel != null && includeHotel.isChecked();
        double averageGasCost = gallons * averagePrice;
        double conservativeGasCost = gallons * conservativePrice;
        double averageTotal = TrailboundTripMath.tripTotal(averageGasCost, snacks, extras, hotelCost, hotelIncluded);
        double conservativeTotal = TrailboundTripMath.tripTotal(conservativeGasCost, snacks, extras, hotelCost, hotelIncluded);

        double tank = positive(text(tankField));
        double depart = positive(text(departureField));
        double reserve = TrailboundTripMath.reserveGallons(tank);
        List<Double> stops = TrailboundTripMath.fuelStopMiles(roundMiles, adjustedMpg, tank, depart);

        double odometer = positive(vehicle.optString("odometer", "0"));
        double due = positive(vehicle.optString("nextOil", "0"));
        double interval = positive(vehicle.optString("oilInterval", prefs.getString("draftOilInterval", "5000")));
        if (interval <= 0) interval = 5000;
        double oilNow = TrailboundTripMath.oilIntervalPercent(odometer, due, interval);
        double oilAfter = TrailboundTripMath.oilIntervalPercent(odometer + roundMiles, due, interval);

        StringBuilder s = new StringBuilder();
        s.append("AUDITED TRIP CALCULATION\n");
        s.append(vehicle.optString("label", "No vehicle selected")).append("\n");
        s.append("START: ").append(uiStart).append("\nDESTINATION: ").append(uiEnd).append("\n");
        s.append("HOTEL: ").append(hotel.optString("label", "No hotel selected")).append("\n\n");
        s.append("ACTUAL SAVED ROUTE\n");
        s.append("Outbound ").append(one(out)).append(" mi");
        if (outHours > 0) s.append(" • ").append(one(outHours)).append(" hr");
        s.append("\nReturn   ").append(one(back)).append(" mi");
        if (backHours > 0) s.append(" • ").append(one(backHours)).append(" hr");
        s.append("\nRound trip ").append(one(roundMiles)).append(" mi");
        if (outHours + backHours > 0) s.append(" • ").append(one(outHours + backHours)).append(" hr");
        s.append("\n\nVEHICLE / FUEL\n");
        s.append(one(baseMpg)).append(" EPA combined MPG → ").append(one(adjustedMpg)).append(" planning MPG");
        if (payload > 0) s.append(" with ").append(Math.round(payload)).append(" lb payload");
        s.append("\nFuel consumed over complete round trip: ").append(two(gallons)).append(" gal");
        s.append("\nStarting fuel affects fill-up timing, not gallons physically consumed.");
        if (tank > 0) {
            s.append("\nTank ").append(one(tank)).append(" gal • departure ").append(one(Math.min(tank, depart))).append(" gal • reserve ").append(one(reserve)).append(" gal");
            if (!stops.isEmpty()) {
                s.append("\nPlanned fill-ups: ").append(stops.size()).append(" — ");
                for (int i = 0; i < Math.min(stops.size(), 6); i++) {
                    if (i > 0) s.append(", ");
                    double mile = stops.get(i);
                    s.append(mile < 1 ? "before/at departure" : "mile " + Math.round(mile));
                }
                if (stops.size() > 6) s.append("…");
            } else {
                s.append("\nPlanned fill-ups: 0");
            }
        }
        s.append("\n\nBUDGET\n");
        if (averagePrice > 0) {
            s.append("Automatic route average ").append(money(averagePrice)).append("/gal → gas ").append(money(averageGasCost)).append("\n");
            String source = trip.optString("gasSource", "").trim();
            if (!source.isEmpty()) s.append(source).append("\n");
            s.append("Average-price full trip: ").append(money(averageTotal)).append("\n");
        } else {
            s.append("Automatic route average unavailable — refresh gas data.\n");
        }
        if (conservativePrice > 0) {
            s.append("Conservative ").append(money(conservativePrice)).append("/gal → gas ").append(money(conservativeGasCost)).append("\n");
            s.append("Conservative full trip: ").append(money(conservativeTotal)).append("\n");
        } else {
            s.append("Conservative scenario: enter your optional $/gal value.\n");
        }
        s.append("Hotel ").append(hotelIncluded ? "included" : "excluded").append(" • snacks ").append(money(snacks)).append(" • other ").append(money(extras));
        if (hotelIncluded) s.append(" • hotel ").append(money(hotelCost));
        s.append("\n\nMAINTENANCE ESTIMATE\n");
        s.append("Odometer after trip: ").append(Math.round(odometer + roundMiles)).append(" mi");
        if (due > 0) {
            s.append("\nService-interval remaining: ").append(Math.round(oilNow)).append("% now → ").append(Math.round(oilAfter)).append("% after trip");
            s.append(" (using your ").append(Math.round(interval)).append(" mi interval and next-due mileage)");
        }
        summary.setText(s.toString());

        if (dual != null) {
            StringBuilder d = new StringBuilder("FUEL BUDGET COMPARISON\n");
            d.append(one(roundMiles)).append(" actual round-trip mi • ").append(two(gallons)).append(" gal consumed\n\n");
            if (averagePrice > 0) d.append("AUTOMATIC ROUTE AVERAGE\n").append(money(averagePrice)).append("/gal • gas ").append(money(averageGasCost)).append(" • full trip ").append(money(averageTotal)).append("\n\n");
            else d.append("AUTOMATIC ROUTE AVERAGE\nUnavailable until refreshed\n\n");
            if (conservativePrice > 0) d.append("YOUR CONSERVATIVE PRICE\n").append(money(conservativePrice)).append("/gal • gas ").append(money(conservativeGasCost)).append(" • full trip ").append(money(conservativeTotal));
            else d.append("YOUR CONSERVATIVE PRICE\nEnter a value to see the separate scenario.");
            dual.setText(d.toString());
        }
    }

    private void refreshRouteAndGas(boolean showToast) {
        View root = getWindow().getDecorView();
        EditText from = exactField(root, "FROM");
        EditText to = exactField(root, "TO");
        String start = text(from).trim();
        String end = text(to).trim();
        if (start.isEmpty() || end.isEmpty()) {
            toast("Enter both FROM and TO first");
            return;
        }
        if (showToast) toast("Refreshing actual round trip…");
        clearStoredRoute(start, end);
        Button map = findButton(root, "Map actual round trip");
        if (map == null) {
            toast("Route mapper is unavailable on this screen");
            return;
        }
        map.performClick();
        pollMappedRoute(start, end, 0, true);
    }

    private void clearStoredRoute(String start, String end) {
        String id = prefs.getString("activeTripId", "");
        if (id.isEmpty()) return;
        try {
            JSONObject trip = profileById(TRIPS, id);
            if (trip.optString("id", "").isEmpty()) return;
            trip.put("start", start);
            trip.put("end", end);
            for (String key : new String[]{"routeOut", "routeBack", "startLat", "startLon", "endLat", "endLon", "outMiles", "backMiles", "outHours", "backHours"}) trip.put(key, "");
            upsert(TRIPS, trip);
        } catch (Exception ignored) { }
    }

    private void pollMappedRoute(String expectedStart, String expectedEnd, int attempt, boolean refreshGas) {
        main.postDelayed(() -> {
            JSONObject trip = activeTrip();
            boolean endpoints = expectedStart.equals(trip.optString("start", "")) && expectedEnd.equals(trip.optString("end", ""));
            if (endpoints && routeComplete(trip)) {
                updateAuditedTrip(getWindow().getDecorView());
                if (refreshGas) refreshDistanceWeightedGas(trip, true);
            } else if (attempt < 16) {
                pollMappedRoute(expectedStart, expectedEnd, attempt + 1, refreshGas);
            } else if (attempt == 16) {
                toast("Route refresh did not finish. Check the addresses and connection.");
                updateAuditedTrip(getWindow().getDecorView());
            }
        }, attempt == 0 ? 900 : 650);
    }

    private void refreshDistanceWeightedGas(JSONObject trip, boolean userRequested) {
        if (!routeComplete(trip)) return;
        if (userRequested) toast("Averaging public gas prices along the complete route…");
        io.execute(() -> {
            ArrayList<GeoPoint> route = roundTripGeometry(trip);
            ArrayList<String> sampledStates = new ArrayList<>();
            LinkedHashSet<String> uniqueStates = new LinkedHashSet<>();
            ArrayList<Double> successfulPrices = new ArrayList<>();
            boolean nationalFallback = false;
            try {
                int samples = route.size() < 2 ? 0 : 7;
                for (int i = 0; i < samples; i++) {
                    double fraction = samples == 1 ? 0.5 : i / (double)(samples - 1);
                    GeoPoint p = pointAtFraction(route, fraction);
                    if (p == null) continue;
                    String state = reverseState(p.getLatitude(), p.getLongitude());
                    if (!state.isEmpty()) {
                        sampledStates.add(state);
                        uniqueStates.add(state);
                    }
                    if (i < samples - 1) {
                        try { Thread.sleep(250); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                    }
                }

                int fuelIndex = linkedFuelIndex(trip);
                Map<String, Double> prices = new LinkedHashMap<>();
                for (String state : sampledStates) {
                    Double price = prices.get(state);
                    if (price == null) {
                        price = aaaStatePrice(state, fuelIndex);
                        prices.put(state, price);
                    }
                    if (price != null && price > 0) successfulPrices.add(price);
                }
                if (successfulPrices.isEmpty()) {
                    double national = aaaNationalPrice(fuelIndex);
                    if (national > 0) {
                        successfulPrices.add(national);
                        nationalFallback = true;
                    }
                }
            } catch (Exception ignored) { }

            boolean live = !successfulPrices.isEmpty();
            double average = 0;
            if (live) {
                for (double price : successfulPrices) average += price;
                average /= successfulPrices.size();
            } else {
                average = positive(prefs.getString("lastRouteAverageGas", "0"));
            }
            final double result = average;
            final boolean finalLive = live;
            final boolean finalNational = nationalFallback;
            final int successfulCount = successfulPrices.size();
            final int routeStateCount = sampledStates.size();
            final String states = join(uniqueStates);

            main.post(() -> {
                JSONObject latest = activeTrip();
                if (!routeSignature(latest).equals(routeSignature(trip))) return;
                if (result > 0) {
                    String value = String.format(Locale.US, "%.2f", result);
                    EditText avg = exactField(getWindow().getDecorView(), "PREPARED GAS $ / GAL");
                    if (avg != null) avg.setText(value);
                    String source;
                    if (finalLive && finalNational) {
                        source = "Distance-weighted route average • current national public fallback";
                    } else if (finalLive) {
                        source = "Distance-weighted route average • " + successfulCount + "/" + Math.max(1, routeStateCount) + " successful route samples" + (states.isEmpty() ? "" : " • " + states);
                    } else {
                        source = "Cached automatic average • live public pricing unavailable right now";
                    }
                    persistProfileField(TRIPS, latest.optString("id", ""), "gas", value);
                    persistProfileField(TRIPS, latest.optString("id", ""), "gasSource", source);
                    persistProfileField(TRIPS, latest.optString("id", ""), "gasUpdatedAt", System.currentTimeMillis());
                    if (finalLive) prefs.edit().putString("lastRouteAverageGas", value).apply();
                    lastGasAuditSignature = routeSignature(latest);
                    updateAuditedTrip(getWindow().getDecorView());
                    toast(finalLive ? "Automatic route average refreshed: $" + value + "/gal" : "Using cached automatic average $" + value + "/gal");
                } else if (userRequested) {
                    toast("Live automatic gas pricing is unavailable; your conservative scenario still works.");
                }
            });
        });
    }

    private void patchVehicleAccuracy(View root) {
        ensureOilIntervalField(root);
        patchExactEpaLookup(root);
        migrateVehicleAuditFields();
        updateVehicleAuditHub(root);
    }

    private void ensureOilIntervalField(View root) {
        if (findTaggedEditText(root, "oil_service_interval_field") != null) return;
        TextView header = findExactText(root, "Vehicle profile");
        if (header == null || !(header.getParent() instanceof LinearLayout)) return;
        LinearLayout card = (LinearLayout) header.getParent();

        TextView label = new TextView(this);
        label.setText("OIL SERVICE INTERVAL (MI)");
        label.setTextColor(Color.rgb(249, 241, 222));
        label.setTextSize(12);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        EditText field = new EditText(this);
        field.setTag("oil_service_interval_field");
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        field.setTextColor(Color.WHITE);
        field.setHintTextColor(Color.rgb(190, 190, 180));
        field.setTextSize(16);
        field.setPadding(dp(12), 0, dp(12), 0);
        field.setBackground(round(Color.rgb(9, 12, 8), 14, Color.rgb(120, 128, 94)));

        JSONObject vehicle = activeVehicle();
        String value = vehicle.optString("oilInterval", prefs.getString("draftOilInterval", "5000"));
        restoringOilInterval = true;
        field.setText(value.isEmpty() ? "5000" : value);
        restoringOilInterval = false;

        int insert = card.getChildCount();
        TextView payloadLabel = findExactText(root, "TRIP PAYLOAD (LB)");
        if (payloadLabel != null && payloadLabel.getParent() == card) insert = card.indexOfChild(payloadLabel);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-1, -2);
        labelLp.topMargin = dp(8);
        card.addView(label, Math.max(0, insert), labelLp);
        LinearLayout.LayoutParams fieldLp = new LinearLayout.LayoutParams(-1, dp(52));
        fieldLp.topMargin = dp(5);
        card.addView(field, Math.min(card.getChildCount(), insert + 1), fieldLp);

        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (restoringOilInterval) return;
                String v = s == null ? "" : s.toString();
                prefs.edit().putString("draftOilInterval", v).apply();
                String id = prefs.getString("activeVehicleId", "");
                if (!id.isEmpty()) persistProfileField(VEHICLES, id, "oilInterval", v);
                updateVehicleAuditHub(getWindow().getDecorView());
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void patchExactEpaLookup(View root) {
        Button epa = findButton(root, "Auto MPG");
        if (epa == null || auditedEpaButtons.contains(epa)) return;
        auditedEpaButtons.add(epa);
        epa.setText("Choose exact EPA MPG");
        epa.setOnClickListener(v -> lookupEpaVariants());
    }

    private void lookupEpaVariants() {
        View root = getWindow().getDecorView();
        String year = text(exactField(root, "YEAR")).trim();
        String make = text(exactField(root, "MAKE")).trim();
        String model = text(exactField(root, "MODEL")).trim();
        if (year.isEmpty() || make.isEmpty() || model.isEmpty()) {
            toast("Enter year, make and model first");
            return;
        }
        toast("Loading EPA variants…");
        io.execute(() -> {
            try {
                Document doc = xml("https://www.fueleconomy.gov/ws/rest/vehicle/menu/options?year=" + enc(year) + "&make=" + enc(make) + "&model=" + enc(model));
                NodeList items = doc.getElementsByTagName("menuItem");
                ArrayList<EpaOption> options = new ArrayList<>();
                for (int i = 0; i < items.getLength(); i++) {
                    Element item = (Element) items.item(i);
                    String label = childText(item, "text");
                    String id = childText(item, "value");
                    if (!id.isEmpty()) options.add(new EpaOption(id, label.isEmpty() ? "EPA vehicle option " + (i + 1) : label));
                }
                if (options.isEmpty()) throw new Exception("No EPA variants");
                runOnUiThread(() -> {
                    if (options.size() == 1) {
                        loadSelectedEpa(options.get(0));
                        return;
                    }
                    String[] labels = new String[options.size()];
                    for (int i = 0; i < options.size(); i++) labels[i] = options.get(i).label;
                    new AlertDialog.Builder(this)
                            .setTitle("Choose your exact EPA variant")
                            .setMessage("Selecting the correct engine/drivetrain prevents Trailbound from silently using the wrong MPG trim.")
                            .setItems(labels, (dialog, which) -> loadSelectedEpa(options.get(which)))
                            .setNegativeButton("Cancel", null)
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast("EPA vehicle variants could not be loaded"));
            }
        });
    }

    private void loadSelectedEpa(EpaOption option) {
        toast("Loading EPA MPG for selected variant…");
        io.execute(() -> {
            try {
                Document doc = xml("https://www.fueleconomy.gov/ws/rest/vehicle/" + enc(option.id));
                String mpg = firstTag(doc, "comb08");
                if (mpg.isEmpty()) throw new Exception("No combined MPG");
                runOnUiThread(() -> {
                    EditText field = exactField(getWindow().getDecorView(), "EPA COMBINED MPG");
                    if (field != null) field.setText(mpg);
                    String id = prefs.getString("activeVehicleId", "");
                    prefs.edit().putString("draftEpaVariant", option.label).putString("draftEpaVehicleId", option.id).apply();
                    if (!id.isEmpty()) {
                        persistProfileField(VEHICLES, id, "epaVariant", option.label);
                        persistProfileField(VEHICLES, id, "epaVehicleId", option.id);
                        persistProfileField(VEHICLES, id, "mpg", mpg);
                    }
                    updateVehicleAuditHub(getWindow().getDecorView());
                    toast("EPA combined MPG loaded for the selected variant");
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast("EPA MPG could not be loaded for that variant"));
            }
        });
    }

    private void migrateVehicleAuditFields() {
        String id = prefs.getString("activeVehicleId", "");
        if (id.isEmpty()) return;
        JSONObject vehicle = profileById(VEHICLES, id);
        String interval = prefs.getString("draftOilInterval", "5000");
        if (!vehicle.has("oilInterval") && !interval.isEmpty()) persistProfileField(VEHICLES, id, "oilInterval", interval);
        String variant = prefs.getString("draftEpaVariant", "");
        String epaId = prefs.getString("draftEpaVehicleId", "");
        if (!variant.isEmpty() && !vehicle.has("epaVariant")) persistProfileField(VEHICLES, id, "epaVariant", variant);
        if (!epaId.isEmpty() && !vehicle.has("epaVehicleId")) persistProfileField(VEHICLES, id, "epaVehicleId", epaId);
    }

    private void updateVehicleAuditHub(View root) {
        TextView overview = findTaggedText(root, "vehicle_hub_overview");
        if (overview == null) return;
        JSONObject vehicle = activeVehicle();
        double base = positive(text(exactField(root, "EPA COMBINED MPG"), vehicle.optString("mpg", "0")));
        double payload = positive(text(exactField(root, "TRIP PAYLOAD (LB)"), vehicle.optString("payload", "0")));
        double od = positive(text(exactField(root, "CURRENT MILEAGE"), vehicle.optString("odometer", "0")));
        double due = positive(text(exactField(root, "NEXT OIL CHANGE DUE AT"), vehicle.optString("nextOil", "0")));
        EditText intervalField = findTaggedEditText(root, "oil_service_interval_field");
        double interval = positive(text(intervalField, vehicle.optString("oilInterval", "5000")));
        if (interval <= 0) interval = 5000;
        double adjusted = TrailboundTripMath.adjustedMpg(base, payload);
        double pct = TrailboundTripMath.oilIntervalPercent(od, due, interval);
        String variant = vehicle.optString("epaVariant", prefs.getString("draftEpaVariant", ""));
        StringBuilder s = new StringBuilder();
        s.append(vehicle.optString("label", "Vehicle profile")).append("\n");
        s.append(Math.round(od)).append(" mi odometer\n");
        s.append(one(base)).append(" EPA combined MPG → ").append(one(adjusted)).append(" estimated loaded MPG");
        if (payload > 0) s.append(" with ").append(Math.round(payload)).append(" lb payload");
        if (!variant.isEmpty()) s.append("\nEPA variant: ").append(variant);
        if (due > 0) {
            s.append("\nService interval remaining: ").append(Math.round(pct)).append("% • ").append(Math.max(0, Math.round(due - od))).append(" mi until entered due mileage");
            s.append("\nInterval basis: ").append(Math.round(interval)).append(" mi (editable; this is not the vehicle's electronic oil-life monitor)");
        }
        overview.setText(s.toString());
    }

    private void patchAccurateRouteDiscovery(View root) {
        String[][] buttons = new String[][]{
                {"Scenic & viewpoints", "Scenic"}, {"Food & coffee", "Food"},
                {"Parks & landmarks", "Parks"}, {"Rest areas", "Rest"},
                {"Supplies & pharmacy", "Supplies"}, {"Useful towns", "Towns"},
                {"Fuel near planned fill-up points", "FuelStops"}
        };
        for (String[] pair : buttons) {
            Button b = findButton(root, pair[0]);
            if (b == null || auditedRouteButtons.contains(b)) continue;
            auditedRouteButtons.add(b);
            String category = pair[1];
            b.setOnClickListener(v -> searchRouteAccurate(category));
        }
    }

    private void searchRouteAccurate(String category) {
        JSONObject trip = activeTrip();
        if (!routeComplete(trip)) {
            toast("Refresh and save the complete round trip first");
            return;
        }
        TextView box = findTaggedText(getWindow().getDecorView(), "route_discovery_results");
        if (box != null) box.setText("Searching the actual route corridor…");
        io.execute(() -> {
            try {
                boolean roundTripSearch = "FuelStops".equals(category);
                ArrayList<GeoPoint> geometry = roundTripSearch ? roundTripGeometry(trip) : geometry(trip.optString("routeOut", ""));
                double routeMiles = roundTripSearch ? positive(trip.optString("outMiles", "0")) + positive(trip.optString("backMiles", "0")) : positive(trip.optString("outMiles", "0"));
                ArrayList<RouteAnchor> anchors = roundTripSearch ? auditedFuelAnchors(trip, geometry) : distanceAnchors(geometry);
                if (anchors.isEmpty()) throw new Exception("No route anchors");
                JSONArray places = overpassPlaces(anchors, discoveryFilters(category), roundTripSearch ? 12000 : 10000, geometry, routeMiles, trip, roundTripSearch);
                String id = trip.optString("id", "");
                JSONObject byCategory;
                try { byCategory = new JSONObject(trip.optString("routePlacesByCategory", "{}")); }
                catch (Exception ignored) { byCategory = new JSONObject(); }
                byCategory.put(category, places);
                persistProfileField(TRIPS, id, "routePlacesByCategory", byCategory.toString());
                persistProfileField(TRIPS, id, "routePlaces", places.toString());
                persistProfileField(TRIPS, id, "routePlacesCategory", category);
                runOnUiThread(() -> plotAuditedPlaces(places, category, trip));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    TextView result = findTaggedText(getWindow().getDecorView(), "route_discovery_results");
                    if (result != null) result.setText("Route discovery is unavailable right now. The saved trip and route remain unchanged.");
                });
            }
        });
    }

    private ArrayList<RouteAnchor> distanceAnchors(List<GeoPoint> route) {
        ArrayList<RouteAnchor> out = new ArrayList<>();
        for (double f : new double[]{0.10, 0.30, 0.50, 0.70, 0.90}) {
            GeoPoint p = pointAtFraction(route, f);
            if (p != null) out.add(new RouteAnchor(p.getLatitude(), p.getLongitude()));
        }
        return out;
    }

    private ArrayList<RouteAnchor> auditedFuelAnchors(JSONObject trip, List<GeoPoint> roundGeometry) {
        ArrayList<RouteAnchor> anchors = new ArrayList<>();
        double out = positive(trip.optString("outMiles", "0"));
        double back = positive(trip.optString("backMiles", "0"));
        double round = out + back;
        JSONObject vehicle = profileById(VEHICLES, trip.optString("vehicleId", prefs.getString("activeVehicleId", "")));
        double mpg = TrailboundTripMath.adjustedMpg(positive(vehicle.optString("mpg", "0")), positive(vehicle.optString("payload", "0")));
        double tank = positive(trip.optString("tankSize", prefs.getString("fuelTankSize", "0")));
        double depart = positive(trip.optString("departureFuel", prefs.getString("departureFuel", "0")));
        List<Double> stops = TrailboundTripMath.fuelStopMiles(round, mpg, tank, depart);
        for (int i = 0; i < stops.size() && i < 12; i++) {
            double mile = stops.get(i);
            GeoPoint p = pointAtFraction(roundGeometry, round > 0 ? mile / round : 0);
            if (p != null) anchors.add(new RouteAnchor(p.getLatitude(), p.getLongitude()));
        }
        if (anchors.isEmpty()) anchors.addAll(distanceAnchors(roundGeometry));
        return anchors;
    }

    private JSONArray overpassPlaces(List<RouteAnchor> anchors, List<String> filters, int radiusMeters,
                                     List<GeoPoint> route, double routeMiles, JSONObject trip, boolean roundTrip) throws Exception {
        StringBuilder q = new StringBuilder("[out:json][timeout:25];(");
        for (RouteAnchor a : anchors) {
            for (String filter : filters) {
                q.append("node(around:").append(radiusMeters).append(',').append(a.lat).append(',').append(a.lon).append(')').append(filter).append(';');
                q.append("way(around:").append(radiusMeters).append(',').append(a.lat).append(',').append(a.lon).append(')').append(filter).append(';');
                q.append("relation(around:").append(radiusMeters).append(',').append(a.lat).append(',').append(a.lon).append(')').append(filter).append(';');
            }
        }
        q.append(");out center tags 120;");
        JSONObject json = new JSONObject(http("https://overpass-api.de/api/interpreter?data=" + enc(q.toString())));
        JSONArray elements = json.optJSONArray("elements");
        ArrayList<AuditedPlace> found = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (elements != null) {
            for (int i = 0; i < elements.length(); i++) {
                JSONObject el = elements.optJSONObject(i);
                if (el == null) continue;
                JSONObject tags = el.optJSONObject("tags");
                double lat = el.optDouble("lat", Double.NaN);
                double lon = el.optDouble("lon", Double.NaN);
                JSONObject center = el.optJSONObject("center");
                if ((Double.isNaN(lat) || Double.isNaN(lon)) && center != null) {
                    lat = center.optDouble("lat", Double.NaN);
                    lon = center.optDouble("lon", Double.NaN);
                }
                if (Double.isNaN(lat) || Double.isNaN(lon)) continue;
                String name = placeName(tags);
                if (name.isEmpty()) continue;
                String key = name.toLowerCase(Locale.US) + "|" + Math.round(lat * 10000) + "|" + Math.round(lon * 10000);
                if (!seen.add(key)) continue;
                double mile = nearestScaledMile(route, routeMiles, lat, lon);
                String leg = roundTrip && mile > positive(trip.optString("outMiles", "0")) ? "Return" : "Outbound";
                found.add(new AuditedPlace(name, lat, lon, mile, leg));
            }
        }
        Collections.sort(found, Comparator.comparingDouble(p -> p.mile));
        JSONArray out = new JSONArray();
        for (int i = 0; i < found.size() && i < 24; i++) {
            AuditedPlace p = found.get(i);
            JSONObject o = new JSONObject();
            o.put("name", p.name); o.put("lat", p.lat); o.put("lon", p.lon); o.put("mile", p.mile); o.put("leg", p.leg);
            out.put(o);
        }
        return out;
    }

    private void plotAuditedPlaces(JSONArray places, String category, JSONObject trip) {
        MapView map = firstMap(getWindow().getDecorView());
        TextView box = findTaggedText(getWindow().getDecorView(), "route_discovery_results");
        if (map == null) return;
        ArrayList<Overlay> remove = new ArrayList<>();
        for (Overlay overlay : map.getOverlays()) {
            if (overlay instanceof Marker && "route_discovery".equals(((Marker) overlay).getRelatedObject())) remove.add(overlay);
        }
        map.getOverlays().removeAll(remove);
        StringBuilder lines = new StringBuilder();
        double outMiles = positive(trip.optString("outMiles", "0"));
        for (int i = 0; i < places.length(); i++) {
            JSONObject o = places.optJSONObject(i);
            if (o == null) continue;
            String name = o.optString("name", "Place");
            double lat = o.optDouble("lat", 0), lon = o.optDouble("lon", 0), mile = o.optDouble("mile", 0);
            String leg = o.optString("leg", mile > outMiles ? "Return" : "Outbound");
            Marker m = new Marker(map);
            m.setPosition(new GeoPoint(lat, lon));
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            m.setTitle(name);
            m.setSnippet(leg + " • around round-trip mile " + Math.round(mile));
            m.setRelatedObject("route_discovery");
            map.getOverlays().add(m);
            if (i < 12) {
                if (lines.length() > 0) lines.append('\n');
                lines.append("• ").append(name).append(" — ").append(leg.toLowerCase(Locale.US)).append(" around mile ").append(Math.round(mile));
            }
        }
        map.invalidate();
        if (box != null) box.setText(places.length() + " " + routeCategoryLabel(category) + " result" + (places.length() == 1 ? "" : "s") + ":\n" + lines);
        toast(places.length() == 0 ? "No named results found in this route corridor." : "Found " + places.length() + " route stops.");
    }

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
        switch (category) {
            case "Scenic": return "scenic";
            case "Food": return "food/coffee";
            case "Parks": return "park/landmark";
            case "Rest": return "rest-area";
            case "Supplies": return "supply/pharmacy";
            case "Towns": return "town";
            case "FuelStops": return "fuel-near-stop";
            default: return "route";
        }
    }

    private String placeName(JSONObject tags) {
        if (tags == null) return "";
        String name = tags.optString("name", "").trim();
        if (name.isEmpty()) name = tags.optString("brand", "").trim();
        if (name.isEmpty()) name = tags.optString("operator", "").trim();
        if (name.isEmpty() && "rest_area".equals(tags.optString("highway", ""))) name = "Rest area";
        if (name.isEmpty() && "fuel".equals(tags.optString("amenity", ""))) name = "Fuel station";
        return name;
    }

    private boolean routeComplete(JSONObject trip) {
        return trip != null && !trip.optString("routeOut", "").isEmpty() && !trip.optString("routeBack", "").isEmpty()
                && positive(trip.optString("outMiles", "0")) > 0 && positive(trip.optString("backMiles", "0")) > 0;
    }

    private String routeSignature(JSONObject trip) {
        if (trip == null) return "";
        return trip.optString("id", "") + "|" + trip.optString("start", "") + "|" + trip.optString("end", "") + "|"
                + trip.optString("routeOut", "").hashCode() + "|" + trip.optString("routeBack", "").hashCode();
    }

    private ArrayList<GeoPoint> roundTripGeometry(JSONObject trip) {
        ArrayList<GeoPoint> out = geometry(trip.optString("routeOut", ""));
        ArrayList<GeoPoint> back = geometry(trip.optString("routeBack", ""));
        if (!out.isEmpty() && !back.isEmpty()) {
            GeoPoint last = out.get(out.size() - 1);
            GeoPoint first = back.get(0);
            if (geoMiles(last, first) < 0.01) back.remove(0);
        }
        out.addAll(back);
        return out;
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

    private GeoPoint pointAtFraction(List<GeoPoint> route, double fraction) {
        if (route == null || route.isEmpty()) return null;
        if (route.size() == 1) return route.get(0);
        fraction = Math.max(0, Math.min(1, fraction));
        double total = polylineMiles(route);
        if (total <= 0) return route.get(0);
        double target = total * fraction;
        double walked = 0;
        for (int i = 1; i < route.size(); i++) {
            GeoPoint a = route.get(i - 1), b = route.get(i);
            double segment = geoMiles(a, b);
            if (segment <= 0) continue;
            if (walked + segment >= target) {
                double f = (target - walked) / segment;
                return new GeoPoint(a.getLatitude() + (b.getLatitude() - a.getLatitude()) * f,
                        a.getLongitude() + (b.getLongitude() - a.getLongitude()) * f);
            }
            walked += segment;
        }
        return route.get(route.size() - 1);
    }

    private double nearestScaledMile(List<GeoPoint> route, double routeMiles, double lat, double lon) {
        if (route == null || route.isEmpty() || routeMiles <= 0) return 0;
        GeoPoint target = new GeoPoint(lat, lon);
        int bestIndex = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < route.size(); i++) {
            double d = geoMiles(target, route.get(i));
            if (d < best) { best = d; bestIndex = i; }
        }
        double before = 0;
        for (int i = 1; i <= bestIndex; i++) before += geoMiles(route.get(i - 1), route.get(i));
        double total = polylineMiles(route);
        return total > 0 ? routeMiles * before / total : 0;
    }

    private double polylineMiles(List<GeoPoint> route) {
        double total = 0;
        if (route == null) return 0;
        for (int i = 1; i < route.size(); i++) total += geoMiles(route.get(i - 1), route.get(i));
        return total;
    }

    private double geoMiles(GeoPoint a, GeoPoint b) {
        double r = 3958.7613;
        double lat1 = Math.toRadians(a.getLatitude()), lat2 = Math.toRadians(b.getLatitude());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.getLongitude() - a.getLongitude());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    private String reverseState(double lat, double lon) {
        try {
            JSONObject o = new JSONObject(http("https://nominatim.openstreetmap.org/reverse?format=jsonv2&addressdetails=1&zoom=5&lat=" + lat + "&lon=" + lon));
            JSONObject a = o.optJSONObject("address");
            if (a == null) return "";
            String code = a.optString("ISO3166-2-lvl4", "");
            if (code.startsWith("US-") && code.length() >= 5) return code.substring(3);
            code = a.optString("state_code", "");
            return code.isEmpty() ? "" : code.toUpperCase(Locale.US);
        } catch (Exception e) { return ""; }
    }

    private int linkedFuelIndex(JSONObject trip) {
        JSONObject vehicle = profileById(VEHICLES, trip.optString("vehicleId", prefs.getString("activeVehicleId", "")));
        String type = vehicle.optString("fuelType", "Regular");
        if ("Midgrade".equalsIgnoreCase(type)) return 1;
        if ("Premium".equalsIgnoreCase(type)) return 2;
        if ("Diesel".equalsIgnoreCase(type)) return 3;
        return 0;
    }

    private double aaaStatePrice(String state, int fuelIndex) {
        try { return parseAaaCurrentAverage(http("https://gasprices.aaa.com/?state=" + enc(state)), fuelIndex); }
        catch (Exception e) { return 0; }
    }

    private double aaaNationalPrice(int fuelIndex) {
        try { return parseAaaCurrentAverage(http("https://gasprices.aaa.com/"), fuelIndex); }
        catch (Exception e) { return 0; }
    }

    private double parseAaaCurrentAverage(String html, int fuelIndex) {
        if (html == null || html.isEmpty()) return 0;
        int idx = html.toLowerCase(Locale.US).indexOf("current avg");
        String slice = idx >= 0 ? html.substring(idx, Math.min(html.length(), idx + 3500)) : html;
        Matcher matcher = Pattern.compile("\\$\\s*([0-9]+\\.[0-9]{2,4})").matcher(slice);
        ArrayList<Double> values = new ArrayList<>();
        while (matcher.find() && values.size() < 4) {
            double value = positive(matcher.group(1));
            if (value > 1 && value < 10) values.add(value);
        }
        if (values.isEmpty()) return 0;
        return values.get(Math.max(0, Math.min(fuelIndex, values.size() - 1)));
    }

    private String join(Set<String> states) {
        StringBuilder s = new StringBuilder();
        for (String state : states) {
            if (s.length() > 0) s.append(" / ");
            s.append(state);
        }
        return s.toString();
    }

    private JSONObject activeTrip() { return profileById(TRIPS, prefs.getString("activeTripId", "")); }
    private JSONObject activeVehicle() { return profileById(VEHICLES, prefs.getString("activeVehicleId", "")); }

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

    private synchronized void persistProfileField(String key, String id, String field, Object value) {
        if (id == null || id.isEmpty()) return;
        try {
            JSONArray input = profiles(key), output = new JSONArray();
            for (int i = 0; i < input.length(); i++) {
                JSONObject o = input.optJSONObject(i);
                if (o == null) continue;
                if (id.equals(o.optString("id", ""))) o.put(field, value);
                output.put(o);
            }
            prefs.edit().putString(key, output.toString()).commit();
        } catch (Exception ignored) { }
    }

    private synchronized void upsert(String key, JSONObject profile) throws Exception {
        JSONArray input = profiles(key), output = new JSONArray();
        boolean replaced = false;
        for (int i = 0; i < input.length(); i++) {
            JSONObject o = input.optJSONObject(i);
            if (o == null) continue;
            if (profile.optString("id", "").equals(o.optString("id", ""))) { output.put(profile); replaced = true; }
            else output.put(o);
        }
        if (!replaced) output.put(profile);
        if (!prefs.edit().putString(key, output.toString()).commit()) throw new Exception("storage");
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
        for (TextView t : views) if (tag.equals(t.getTag())) return t;
        return null;
    }

    private Button findButton(View root, String text) {
        ArrayList<Button> buttons = new ArrayList<>();
        collect(root, Button.class, buttons);
        for (Button b : buttons) if (b.getText() != null && text.equalsIgnoreCase(b.getText().toString().trim())) return b;
        return null;
    }

    private TextView findExactText(View root, String text) {
        ArrayList<TextView> views = new ArrayList<>();
        collect(root, TextView.class, views);
        for (TextView t : views) if (t.getText() != null && text.equalsIgnoreCase(t.getText().toString().trim())) return t;
        return null;
    }

    private CheckBox findCheckBoxContaining(View root, String text) {
        ArrayList<CheckBox> boxes = new ArrayList<>();
        collect(root, CheckBox.class, boxes);
        for (CheckBox b : boxes) if (b.getText() != null && b.getText().toString().contains(text)) return b;
        return null;
    }

    private MapView firstMap(View root) {
        ArrayList<MapView> maps = new ArrayList<>();
        collect(root, MapView.class, maps);
        return maps.isEmpty() ? null : maps.get(0);
    }

    private <T extends View> void collect(View root, Class<T> type, List<T> out) {
        if (type.isInstance(root)) out.add(type.cast(root));
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), type, out);
        }
    }

    private String childText(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        return list.getLength() == 0 ? "" : list.item(0).getTextContent().trim();
    }

    private String firstTag(Document doc, String tag) {
        NodeList list = doc.getElementsByTagName(tag);
        return list.getLength() == 0 ? "" : list.item(0).getTextContent().trim();
    }

    private Document xml(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(22000);
        c.setRequestProperty("User-Agent", "TrailboundAndroid/6.2");
        try (InputStream in = c.getInputStream()) {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
        } finally { c.disconnect(); }
    }

    private String http(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(25000);
        c.setRequestProperty("User-Agent", "TrailboundAndroid/6.2");
        c.setRequestProperty("Accept", "application/json,text/html,*/*");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
            StringBuilder s = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) s.append(line);
            return s.toString();
        } finally { c.disconnect(); }
    }

    private String enc(String s) throws Exception { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); }
    private String text(EditText e) { return e == null || e.getText() == null ? "" : e.getText().toString(); }
    private String text(EditText e, String fallback) { String s = text(e).trim(); return s.isEmpty() ? fallback : s; }
    private double positive(String s) { try { return TrailboundTripMath.nonNegative(Double.parseDouble(s == null ? "" : s.trim())); } catch (Exception e) { return 0; } }
    private String one(double d) { return String.format(Locale.US, "%.1f", d); }
    private String two(double d) { return String.format(Locale.US, "%.2f", d); }
    private String money(double d) { return NumberFormat.getCurrencyInstance(Locale.US).format(d); }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private LinearLayout.LayoutParams topMargin(int w, int h, int margin) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h); lp.topMargin = dp(margin); return lp; }
    private GradientDrawable round(int fill, int radius, int stroke) { GradientDrawable g = new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp(radius)); g.setStroke(dp(1), stroke); return g; }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    @Override protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private static class EpaOption {
        final String id, label;
        EpaOption(String id, String label) { this.id = id; this.label = label; }
    }

    private static class RouteAnchor {
        final double lat, lon;
        RouteAnchor(double lat, double lon) { this.lat = lat; this.lon = lon; }
    }

    private static class AuditedPlace {
        final String name, leg;
        final double lat, lon, mile;
        AuditedPlace(String name, double lat, double lon, double mile, String leg) {
            this.name = name; this.lat = lat; this.lon = lon; this.mile = mile; this.leg = leg;
        }
    }
}
