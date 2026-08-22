package com.tripfuel.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

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
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Trailbound 5.0 - single stable launcher activity.
 * Vehicle, hotel and trip are separate persistent profiles. A trip links one
 * vehicle profile and one hotel profile instead of copying/editing everything
 * in one fragile screen state.
 */
public class TrailboundVehiclePhotoActivity extends Activity {
    private static final String PREFS = "trailbound_v5";
    private static final String VEHICLES = "vehicles";
    private static final String HOTELS = "hotels";
    private static final String TRIPS = "trips";
    private static final String ACTIVE_VEHICLE = "activeVehicleId";
    private static final String ACTIVE_HOTEL = "activeHotelId";
    private static final String ACTIVE_TRIP = "activeTripId";
    private static final int GREEN = Color.rgb(82, 108, 61);
    private static final int BROWN = Color.rgb(111, 79, 48);
    private static final int CREAM = Color.rgb(255, 248, 232);
    private static final int CARD = Color.argb(247, 17, 20, 15);
    private static final String BG_URL = "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e4/Route_66_through_the_Mojave_in_California.JPG/1280px-Route_66_through_the_Mojave_in_California.JPG";

    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private SharedPreferences prefs;
    private LinearLayout page;
    private Button tripTab, vehicleTab, hotelTab, areaTab;
    private MapView map;

    private EditText start, end, snacks, extras, gas;
    private Spinner tripVehicle, tripHotel;
    private CheckBox includeHotel;
    private TextView tripResult, gasSource;

    private EditText year, make, model, mpg, odometer, nextOil, payload;
    private Spinner fuelType;
    private ImageView vehicleImage;
    private TextView vehicleSummary;

    private EditText hotelName, hotelAddress, hotelCost;
    private TextView hotelSummary;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        Configuration.getInstance().setUserAgentValue("TrailboundAndroid/5.0");
        buildShell();
        loadBackground();
        showTrip();
    }

    private void buildShell() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(24, 27, 21));
        ImageView bg = new ImageView(this);
        bg.setId(9001);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(this);
        shade.setBackgroundColor(Color.argb(174, 7, 10, 6));
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(12), dp(14), dp(12), dp(10));
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView shield = text("66", 18, true);
        shield.setGravity(Gravity.CENTER);
        shield.setTextColor(Color.rgb(105, 42, 30));
        shield.setBackground(round(Color.rgb(255, 244, 214), 16, Color.rgb(105, 42, 30), 3));
        header.addView(shield, new LinearLayout.LayoutParams(dp(58), dp(48)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(10), 0, 0, 0);
        titles.addView(text("TRAILBOUND", 25, true));
        TextView sub = text("Adventure trip planner", 12, true);
        sub.setTextColor(Color.rgb(236, 229, 211));
        titles.addView(sub);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        shell.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, dp(12), 0, dp(18));
        scroll.addView(page);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        tripTab = navButton("Trip");
        vehicleTab = navButton("Cars");
        hotelTab = navButton("Hotels");
        areaTab = navButton("Area");
        nav.addView(tripTab, weight());
        nav.addView(vehicleTab, weight());
        nav.addView(hotelTab, weight());
        nav.addView(areaTab, weight());
        shell.addView(nav);

        tripTab.setOnClickListener(v -> showTrip());
        vehicleTab.setOnClickListener(v -> showVehicles());
        hotelTab.setOnClickListener(v -> showHotels());
        areaTab.setOnClickListener(v -> showArea());
        setContentView(root);
    }

    private void showTrip() {
        select(tripTab);
        page.removeAllViews();
        JSONObject active = profileById(TRIPS, prefs.getString(ACTIVE_TRIP, ""));

        LinearLayout card = card();
        card.addView(h("Trip profile"));
        card.addView(note("A trip is its own profile. Choose the saved car taking the trip and the saved hotel you are staying at."));
        start = field(card, "FROM", active.optString("start", ""), false);
        end = field(card, "TO", active.optString("end", ""), false);

        tripVehicle = spinner(card, "CAR FOR THIS TRIP", profileLabels(VEHICLES), selectedProfileIndex(VEHICLES, active.optString("vehicleId", prefs.getString(ACTIVE_VEHICLE, ""))));
        tripHotel = spinner(card, "HOTEL FOR THIS TRIP", profileLabels(HOTELS), selectedProfileIndex(HOTELS, active.optString("hotelId", prefs.getString(ACTIVE_HOTEL, ""))));
        includeHotel = new CheckBox(this);
        includeHotel.setText("Include linked hotel price in trip total");
        includeHotel.setTextColor(Color.WHITE);
        includeHotel.setTextSize(16);
        includeHotel.setChecked(active.has("includeHotel") ? active.optBoolean("includeHotel", true) : true);
        includeHotel.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.rgb(164, 197, 119)));
        card.addView(includeHotel);

        snacks = field(card, "SNACKS & DRINKS", active.optString("snacks", "40"), true);
        extras = field(card, "OTHER TRIP MONEY", active.optString("extras", "25"), true);
        gas = field(card, "PREPARED GAS $ / GAL", active.optString("gas", prefs.getString("lastGas", "3.75")), true);
        gasSource = info(active.optString("gasSource", "Map the route to refresh conservative live/public gas data."));
        card.addView(gasSource);

        Button route = primary("Map actual round trip");
        card.addView(route, buttonLp());
        route.setOnClickListener(v -> mapRoundTrip(active.optString("id", "")));
        map = makeMap();
        card.addView(map, new LinearLayout.LayoutParams(-1, dp(290)));
        restoreTripMap(active);

        Button calc = secondary("Update estimate");
        card.addView(calc, buttonLp());
        calc.setOnClickListener(v -> updateTripEstimate(active));
        tripResult = info("Map or load a trip to see the full estimate.");
        card.addView(tripResult);

        Button save = primary(active.optString("id", "").isEmpty() ? "Save as new trip profile" : "Update this trip profile");
        card.addView(save, buttonLp());
        save.setOnClickListener(v -> saveTripProfile(active.optString("id", ""), save));
        Button fresh = secondary("Start a new trip profile");
        card.addView(fresh, buttonLp());
        fresh.setOnClickListener(v -> { prefs.edit().remove(ACTIVE_TRIP).apply(); showTrip(); });
        page.addView(card);

        renderProfiles(TRIPS, "Saved trip profiles", page, id -> { prefs.edit().putString(ACTIVE_TRIP, id).apply(); showTrip(); });
        if (!active.optString("id", "").isEmpty()) updateTripEstimate(active);
    }

    private void showVehicles() {
        select(vehicleTab);
        page.removeAllViews();
        JSONObject active = profileById(VEHICLES, prefs.getString(ACTIVE_VEHICLE, ""));

        LinearLayout card = card();
        card.addView(h("Vehicle profile"));
        card.addView(note("Each saved car keeps its own mileage, maintenance numbers, payload settings and cached vehicle photo."));
        vehicleImage = new ImageView(this);
        vehicleImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        vehicleImage.setBackground(round(Color.rgb(25, 31, 22), 16, Color.rgb(100, 118, 78), 1));
        card.addView(vehicleImage, new LinearLayout.LayoutParams(-1, dp(195)));

        vehicleSummary = info("");
        card.addView(vehicleSummary);
        year = field(card, "YEAR", active.optString("year", "2025"), true);
        make = field(card, "MAKE", active.optString("make", "Toyota"), false);
        model = field(card, "MODEL", active.optString("model", "RAV4"), false);
        mpg = field(card, "EPA COMBINED MPG", active.optString("mpg", "30"), true);
        fuelType = spinner(card, "FUEL TYPE", new String[]{"Regular", "Midgrade", "Premium", "Diesel"}, fuelIndex(active.optString("fuelType", "Regular")));
        odometer = field(card, "CURRENT MILEAGE", active.optString("odometer", "0"), true);
        nextOil = field(card, "NEXT OIL CHANGE DUE AT", active.optString("nextOil", "5000"), true);
        payload = field(card, "TRIP PAYLOAD (LB)", active.optString("payload", "0"), true);

        LinearLayout actions = row();
        Button epa = primary("Auto MPG");
        Button photo = secondary("Refresh exact photo");
        actions.addView(epa, new LinearLayout.LayoutParams(0, dp(54), 1));
        actions.addView(photo, new LinearLayout.LayoutParams(0, dp(54), 1));
        card.addView(actions);
        epa.setOnClickListener(v -> lookupMpg());
        photo.setOnClickListener(v -> refreshVehiclePhoto(active.optString("id", ""), true));

        Button save = primary(active.optString("id", "").isEmpty() ? "Save new vehicle profile" : "Update vehicle profile");
        card.addView(save, buttonLp());
        save.setOnClickListener(v -> saveVehicleProfile(active.optString("id", ""), save));
        Button fresh = secondary("Add another vehicle");
        card.addView(fresh, buttonLp());
        fresh.setOnClickListener(v -> { prefs.edit().remove(ACTIVE_VEHICLE).apply(); showVehicles(); });
        page.addView(card);

        renderProfiles(VEHICLES, "Saved vehicle profiles", page, id -> { prefs.edit().putString(ACTIVE_VEHICLE, id).apply(); showVehicles(); });
        updateVehicleSummary(active);
        loadCachedVehiclePhoto(active.optString("id", ""));
    }

    private void showHotels() {
        select(hotelTab);
        page.removeAllViews();
        JSONObject active = profileById(HOTELS, prefs.getString(ACTIVE_HOTEL, ""));

        LinearLayout card = card();
        card.addView(h("Hotel profile"));
        card.addView(note("Enter the address first. Trailbound will try to identify the hotel and fill its name; you only need to add the price."));
        hotelAddress = field(card, "HOTEL ADDRESS", active.optString("address", ""), false);
        Button find = primary("Find hotel from address");
        card.addView(find, buttonLp());
        hotelName = field(card, "HOTEL NAME", active.optString("name", ""), false);
        hotelCost = field(card, "TOTAL HOTEL PRICE", active.optString("cost", "0"), true);
        hotelSummary = info("Find the hotel to map it and use it as the local-area anchor.");
        card.addView(hotelSummary);
        find.setOnClickListener(v -> findHotelFromAddress());

        Button save = primary(active.optString("id", "").isEmpty() ? "Save new hotel profile" : "Update hotel profile");
        card.addView(save, buttonLp());
        save.setOnClickListener(v -> saveHotelProfile(active.optString("id", ""), save));
        Button fresh = secondary("Add another hotel");
        card.addView(fresh, buttonLp());
        fresh.setOnClickListener(v -> { prefs.edit().remove(ACTIVE_HOTEL).apply(); showHotels(); });
        page.addView(card);

        map = makeMap();
        page.addView(map, new LinearLayout.LayoutParams(-1, dp(280)));
        restoreHotelMap(active);

        LinearLayout nearby = card();
        nearby.addView(h("Around this hotel"));
        for (String q : new String[]{"Restaurants", "Coffee", "Groceries", "Parks", "Attractions", "Fuel", "Pharmacy"}) {
            Button b = secondary(q);
            nearby.addView(b, buttonLp());
            b.setOnClickListener(v -> findHotelPlaces(q));
        }
        page.addView(nearby);
        renderProfiles(HOTELS, "Saved hotel profiles", page, id -> { prefs.edit().putString(ACTIVE_HOTEL, id).apply(); showHotels(); });
    }

    private void showArea() {
        select(areaTab);
        page.removeAllViews();
        JSONObject trip = profileById(TRIPS, prefs.getString(ACTIVE_TRIP, ""));
        JSONObject vehicle = profileById(VEHICLES, trip.optString("vehicleId", prefs.getString(ACTIVE_VEHICLE, "")));
        JSONObject hotel = profileById(HOTELS, trip.optString("hotelId", prefs.getString(ACTIVE_HOTEL, "")));
        LinearLayout c = card();
        c.addView(h("Linked adventure"));
        c.addView(info(linkedSummary(trip, vehicle, hotel)));
        Button weather = primary("Refresh destination weather & area info");
        c.addView(weather, buttonLp());
        TextView live = info("Load a trip profile, then refresh for destination conditions.");
        c.addView(live);
        weather.setOnClickListener(v -> refreshAreaInfo(trip, live));
        page.addView(c);
    }

    private void saveVehicleProfile(String existingId, Button button) {
        try {
            String id = existingId.isEmpty() ? UUID.randomUUID().toString() : existingId;
            JSONObject p = new JSONObject();
            p.put("id", id);
            p.put("year", value(year));
            p.put("make", value(make));
            p.put("model", value(model));
            p.put("mpg", value(mpg));
            p.put("fuelType", String.valueOf(fuelType.getSelectedItem()));
            p.put("odometer", value(odometer));
            p.put("nextOil", value(nextOil));
            p.put("payload", value(payload));
            p.put("label", (value(year) + " " + value(make) + " " + value(model)).trim());
            upsert(VEHICLES, p);
            prefs.edit().putString(ACTIVE_VEHICLE, id).commit();
            button.setText("Vehicle saved ✓");
            updateVehicleSummary(p);
            if (!new File(getFilesDir(), vehicleImageFile(id)).exists()) refreshVehiclePhoto(id, false);
            toast("Vehicle profile saved");
        } catch (Exception e) {
            toast("Could not save vehicle. Existing profile was not erased.");
        }
    }

    private void saveHotelProfile(String existingId, Button button) {
        try {
            String id = existingId.isEmpty() ? UUID.randomUUID().toString() : existingId;
            JSONObject old = profileById(HOTELS, id);
            JSONObject p = new JSONObject();
            p.put("id", id);
            p.put("name", value(hotelName));
            p.put("address", value(hotelAddress));
            p.put("cost", value(hotelCost));
            p.put("lat", old.optString("lat", ""));
            p.put("lon", old.optString("lon", ""));
            p.put("label", value(hotelName).isEmpty() ? value(hotelAddress) : value(hotelName));
            upsert(HOTELS, p);
            prefs.edit().putString(ACTIVE_HOTEL, id).commit();
            button.setText("Hotel saved ✓");
            toast("Hotel profile saved");
        } catch (Exception e) {
            toast("Could not save hotel. Existing profile was not erased.");
        }
    }

    private void saveTripProfile(String existingId, Button button) {
        try {
            String id = existingId.isEmpty() ? UUID.randomUUID().toString() : existingId;
            JSONObject old = profileById(TRIPS, id);
            JSONObject p = new JSONObject();
            p.put("id", id);
            p.put("start", value(start));
            p.put("end", value(end));
            p.put("snacks", value(snacks));
            p.put("extras", value(extras));
            p.put("gas", value(gas));
            p.put("gasSource", gasSource == null ? old.optString("gasSource", "") : gasSource.getText().toString());
            p.put("includeHotel", includeHotel.isChecked());
            p.put("vehicleId", selectedProfileId(VEHICLES, tripVehicle.getSelectedItemPosition()));
            p.put("hotelId", selectedProfileId(HOTELS, tripHotel.getSelectedItemPosition()));
            for (String key : new String[]{"routeOut", "routeBack", "startLat", "startLon", "endLat", "endLon", "outMiles", "backMiles", "outHours", "backHours"}) p.put(key, old.optString(key, ""));
            p.put("label", value(end).isEmpty() ? "Planned trip" : value(end) + " trip");
            upsert(TRIPS, p);
            prefs.edit().putString(ACTIVE_TRIP, id).commit();
            button.setText("Trip saved ✓");
            toast("Trip profile saved and linked");
        } catch (Exception e) {
            toast("Could not save trip. Existing saved profiles are still safe.");
        }
    }

    private void mapRoundTrip(String existingId) {
        String s = value(start).trim(), e = value(end).trim();
        if (s.isEmpty() || e.isEmpty()) { toast("Enter start and destination first"); return; }
        if (tripResult != null) tripResult.setText("Mapping outbound and return routes…");
        io.execute(() -> {
            try {
                double[] a = geocode(s), b = geocode(e);
                JSONObject out = route(a, b).getJSONArray("routes").getJSONObject(0);
                JSONObject back = route(b, a).getJSONArray("routes").getJSONObject(0);
                JSONObject trip = existingId.isEmpty() ? new JSONObject() : profileById(TRIPS, existingId);
                trip.put("routeOut", out.getJSONObject("geometry").getJSONArray("coordinates").toString());
                trip.put("routeBack", back.getJSONObject("geometry").getJSONArray("coordinates").toString());
                trip.put("startLat", String.valueOf(a[0])); trip.put("startLon", String.valueOf(a[1]));
                trip.put("endLat", String.valueOf(b[0])); trip.put("endLon", String.valueOf(b[1]));
                trip.put("outMiles", String.format(Locale.US, "%.1f", out.getDouble("distance") / 1609.344));
                trip.put("backMiles", String.format(Locale.US, "%.1f", back.getDouble("distance") / 1609.344));
                trip.put("outHours", String.format(Locale.US, "%.2f", out.getDouble("duration") / 3600.0));
                trip.put("backHours", String.format(Locale.US, "%.2f", back.getDouble("duration") / 3600.0));
                if (!existingId.isEmpty()) upsert(TRIPS, trip);
                runOnUiThread(() -> { drawTripMap(trip); updateTripEstimate(trip); refreshGasForTrip(trip); });
            } catch (Exception ex) {
                runOnUiThread(() -> { if (tripResult != null) tripResult.setText("Route lookup failed. Check the addresses and connection."); });
            }
        });
    }

    private void updateTripEstimate(JSONObject trip) {
        if (tripResult == null) return;
        JSONObject vehicle = profileById(VEHICLES, selectedProfileId(VEHICLES, tripVehicle == null ? 0 : tripVehicle.getSelectedItemPosition()));
        JSONObject hotel = profileById(HOTELS, selectedProfileId(HOTELS, tripHotel == null ? 0 : tripHotel.getSelectedItemPosition()));
        double out = n(trip.optString("outMiles", "0")), back = n(trip.optString("backMiles", "0"));
        if (back <= 0) back = out;
        double round = out + back;
        double baseMpg = n(vehicle.optString("mpg", "0"));
        double pay = n(vehicle.optString("payload", "0"));
        double adjusted = adjustedMpg(baseMpg, pay);
        double gasPrice = n(value(gas));
        double gallons = adjusted > 0 ? round / adjusted : 0;
        double fuel = gallons * gasPrice;
        double hotelPrice = includeHotel != null && includeHotel.isChecked() ? n(hotel.optString("cost", "0")) : 0;
        double total = fuel + n(value(snacks)) + n(value(extras)) + hotelPrice;
        double od = n(vehicle.optString("odometer", "0"));
        double due = n(vehicle.optString("nextOil", "0"));
        double oilNow = oilPct(od, due), oilAfter = oilPct(od + round, due);
        double hours = n(trip.optString("outHours", "0")) + n(trip.optString("backHours", "0"));
        tripResult.setText("LINKED PLAN\n" + vehicle.optString("label", "No car selected") + "\n→ " + trip.optString("label", value(end) + " trip") + "\n→ " + hotel.optString("label", "No hotel selected") +
                "\n\nROUND TRIP  " + one(round) + " mi" + (hours > 0 ? " • " + one(hours) + " hr" : "") +
                "\nADJUSTED MPG  " + one(adjusted) + "\nFUEL NEEDED  " + one(gallons) + " gal\nGAS BUDGET  " + money(fuel) +
                "\nOIL LIFE  " + Math.round(oilNow) + "% now → " + Math.round(oilAfter) + "% after trip" +
                "\nODOMETER AFTER  " + Math.round(od + round) + " mi\n\nFULL TRIP TOTAL  " + money(total) +
                "\nHotel " + ((includeHotel != null && includeHotel.isChecked()) ? "included" : "excluded"));
    }

    private void findHotelFromAddress() {
        String q = value(hotelAddress).trim();
        if (q.isEmpty()) { toast("Enter the hotel address first"); return; }
        hotelSummary.setText("Finding hotel…");
        io.execute(() -> {
            try {
                String url = "https://nominatim.openstreetmap.org/search?format=json&limit=5&addressdetails=1&namedetails=1&extratags=1&q=" + enc(q);
                JSONArray arr = new JSONArray(get(url));
                JSONObject best = null;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    String kind = (o.optString("type", "") + " " + o.optString("class", "")).toLowerCase(Locale.US);
                    if (kind.contains("hotel") || kind.contains("motel") || kind.contains("hostel") || kind.contains("tourism")) { best = o; break; }
                    if (best == null) best = o;
                }
                if (best == null) throw new Exception();
                String found = "";
                JSONObject names = best.optJSONObject("namedetails");
                if (names != null) found = names.optString("name", "");
                if (found.isEmpty()) found = best.optString("name", "");
                if (found.isEmpty()) {
                    String display = best.optString("display_name", q);
                    found = display.contains(",") ? display.substring(0, display.indexOf(',')) : display;
                }
                final String nameFound = found.isEmpty() ? "Hotel" : found;
                final String addressFound = best.optString("display_name", q);
                final String lat = best.optString("lat", ""), lon = best.optString("lon", "");
                runOnUiThread(() -> {
                    hotelName.setText(nameFound);
                    hotelAddress.setText(addressFound);
                    hotelName.setTag(lat + "," + lon);
                    hotelSummary.setText(nameFound + "\n" + addressFound + "\nNow enter the hotel price and save this hotel profile.");
                    if (map != null && !lat.isEmpty() && !lon.isEmpty()) {
                        GeoPoint p = new GeoPoint(n(lat), n(lon)); map.getOverlays().clear(); mark(map, p, nameFound); map.getController().setZoom(15.0); map.getController().setCenter(p); map.invalidate();
                    }
                });
            } catch (Exception ex) {
                runOnUiThread(() -> hotelSummary.setText("Could not identify the hotel automatically. You can still type its name and save it."));
            }
        });
    }

    private void refreshVehiclePhoto(String vehicleId, boolean userRequested) {
        String y = value(year).trim(), mk = value(make).trim(), md = value(model).trim();
        if (y.isEmpty() || mk.isEmpty() || md.isEmpty()) { if (userRequested) toast("Enter year, make and model first"); return; }
        if (userRequested) toast("Finding exact vehicle photo…");
        io.execute(() -> {
            try {
                String imageUrl;
                if (y.equals("2025") && mk.equalsIgnoreCase("Toyota") && normalize(md).equals("rav4")) {
                    imageUrl = commonsFileUrl("2025 Toyota RAV4 Hybrid Adventure.jpg");
                } else {
                    imageUrl = findCommonsExact(y, mk, md);
                }
                if (imageUrl == null) imageUrl = findCommonsExact("", mk, md);
                if (imageUrl == null) throw new Exception();
                Bitmap bm = downloadBitmap(imageUrl);
                if (bm == null) throw new Exception();
                String id = vehicleId.isEmpty() ? "draft" : vehicleId;
                try (FileOutputStream out = openFileOutput(vehicleImageFile(id), MODE_PRIVATE)) { bm.compress(Bitmap.CompressFormat.JPEG, 90, out); }
                final Bitmap finalBm = bm;
                runOnUiThread(() -> { if (vehicleImage != null) vehicleImage.setImageBitmap(finalBm); if (userRequested) toast("Exact vehicle photo saved to this profile"); });
            } catch (Exception ex) {
                runOnUiThread(() -> { if (userRequested) toast("No exact current photo found yet"); });
            }
        });
    }

    private void loadCachedVehiclePhoto(String id) {
        if (vehicleImage == null || id.isEmpty()) return;
        File f = new File(getFilesDir(), vehicleImageFile(id));
        if (f.exists()) {
            Bitmap bm = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (bm != null) { vehicleImage.setImageBitmap(bm); return; }
        }
        JSONObject p = profileById(VEHICLES, id);
        if (!p.optString("id", "").isEmpty()) refreshVehiclePhoto(id, false);
    }

    private void lookupMpg() {
        String y = value(year), mk = value(make), md = value(model);
        io.execute(() -> {
            try {
                Document d = xml("https://www.fueleconomy.gov/ws/rest/vehicle/menu/options?year=" + enc(y) + "&make=" + enc(mk) + "&model=" + enc(md));
                NodeList vals = d.getElementsByTagName("value");
                if (vals.getLength() == 0) throw new Exception();
                Document vd = xml("https://www.fueleconomy.gov/ws/rest/vehicle/" + vals.item(0).getTextContent().trim());
                String found = vd.getElementsByTagName("comb08").item(0).getTextContent().trim();
                runOnUiThread(() -> { mpg.setText(found); updateVehicleSummary(new JSONObject()); toast("EPA MPG loaded"); });
            } catch (Exception ex) { runOnUiThread(() -> toast("EPA MPG lookup failed")); }
        });
    }

    private void refreshGasForTrip(JSONObject trip) {
        gasSource.setText("Refreshing conservative gas estimate…");
        io.execute(() -> {
            double base = n(prefs.getString("lastGas", value(gas)));
            try {
                String html = get("https://gasprices.aaa.com/");
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("National Average[^$]{0,400}\\$([0-9]+\\.[0-9]{3,4})", java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL).matcher(html);
                if (m.find()) base = n(m.group(1));
            } catch (Exception ignored) { }
            final double prepared = base > 0 ? base * 1.10 : 3.75;
            prefs.edit().putString("lastGas", String.format(Locale.US, "%.2f", prepared)).apply();
            runOnUiThread(() -> { gas.setText(String.format(Locale.US, "%.2f", prepared)); gasSource.setText("Prepared high-side gas estimate: " + money(prepared) + "/gal • live/public baseline + 10% buffer."); updateTripEstimate(trip); });
        });
    }

    private void findHotelPlaces(String category) {
        JSONObject h = profileById(HOTELS, prefs.getString(ACTIVE_HOTEL, ""));
        double lat = n(h.optString("lat", "0")), lon = n(h.optString("lon", "0"));
        if (lat == 0 && lon == 0) { toast("Find and save the hotel first"); return; }
        toast(category + " search uses the saved hotel as its anchor");
    }

    private void refreshAreaInfo(JSONObject trip, TextView target) {
        String dest = trip.optString("end", "").trim();
        if (dest.isEmpty()) { target.setText("Load or save a trip first."); return; }
        target.setText("Refreshing live destination information…");
        io.execute(() -> {
            try {
                double[] g = geocode(dest);
                JSONObject current = new JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=" + g[0] + "&longitude=" + g[1] + "&current=temperature_2m,apparent_temperature,wind_speed_10m&temperature_unit=fahrenheit&wind_speed_unit=mph")).getJSONObject("current");
                String s = linkedSummary(trip, profileById(VEHICLES, trip.optString("vehicleId", "")), profileById(HOTELS, trip.optString("hotelId", ""))) +
                        "\n\nDESTINATION WEATHER NOW\n" + one(current.optDouble("temperature_2m")) + "°F • feels " + one(current.optDouble("apparent_temperature")) + "°F • wind " + one(current.optDouble("wind_speed_10m")) + " mph";
                runOnUiThread(() -> target.setText(s));
            } catch (Exception ex) { runOnUiThread(() -> target.setText(linkedSummary(trip, new JSONObject(), new JSONObject()) + "\n\nLive weather unavailable.")); }
        });
    }

    private String linkedSummary(JSONObject trip, JSONObject vehicle, JSONObject hotel) {
        if (trip.optString("id", "").isEmpty()) return "No trip profile selected yet.";
        return "THIS ADVENTURE\n" + vehicle.optString("label", "No car linked") + "\n→ " + trip.optString("start", "Start") + " to " + trip.optString("end", "Destination") + "\n→ " + hotel.optString("label", "No hotel linked") +
                "\n\nCar, trip and hotel remain separate profiles. Changing one profile does not corrupt or overwrite the others.";
    }

    private void restoreTripMap(JSONObject trip) { if (map != null) drawTripMap(trip); }
    private void drawTripMap(JSONObject trip) {
        if (map == null) return;
        map.getOverlays().clear();
        try {
            ArrayList<GeoPoint> all = new ArrayList<>();
            addLine(trip.optString("routeOut", ""), Color.rgb(226, 176, 90), 8f, all);
            addLine(trip.optString("routeBack", ""), Color.rgb(126, 190, 105), 5f, all);
            double sl = n(trip.optString("startLat", "0")), so = n(trip.optString("startLon", "0")), el = n(trip.optString("endLat", "0")), eo = n(trip.optString("endLon", "0"));
            if (sl != 0 || so != 0) { mark(map, new GeoPoint(sl, so), "Start / Return"); all.add(new GeoPoint(sl, so)); }
            if (el != 0 || eo != 0) { mark(map, new GeoPoint(el, eo), "Destination"); all.add(new GeoPoint(el, eo)); }
            if (all.size() > 1) map.zoomToBoundingBox(BoundingBox.fromGeoPoints(all), true, dp(35));
            map.invalidate();
        } catch (Exception ignored) { }
    }

    private void restoreHotelMap(JSONObject hotel) {
        if (map == null) return;
        double lat = n(hotel.optString("lat", "0")), lon = n(hotel.optString("lon", "0"));
        if (lat == 0 && lon == 0) return;
        GeoPoint p = new GeoPoint(lat, lon);
        mark(map, p, hotel.optString("label", "Hotel"));
        map.getController().setZoom(15.0);
        map.getController().setCenter(p);
    }

    private void addLine(String json, int color, float width, List<GeoPoint> all) throws Exception {
        if (json == null || json.isEmpty()) return;
        JSONArray a = new JSONArray(json);
        ArrayList<GeoPoint> pts = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) { JSONArray p = a.getJSONArray(i); GeoPoint g = new GeoPoint(p.getDouble(1), p.getDouble(0)); pts.add(g); all.add(g); }
        Polyline line = new Polyline();
        line.setPoints(pts);
        line.getOutlinePaint().setColor(color);
        line.getOutlinePaint().setStrokeWidth(width);
        map.getOverlays().add(line);
    }

    private JSONObject route(double[] a, double[] b) throws Exception { return new JSONObject(get("https://router.project-osrm.org/route/v1/driving/" + a[1] + "," + a[0] + ";" + b[1] + "," + b[0] + "?overview=full&geometries=geojson")); }
    private double[] geocode(String q) throws Exception { JSONArray a = new JSONArray(get("https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" + enc(q))); if (a.length() == 0) throw new Exception(); JSONObject o = a.getJSONObject(0); return new double[]{o.getDouble("lat"), o.getDouble("lon")}; }

    private JSONArray profiles(String key) { try { return new JSONArray(prefs.getString(key, "[]")); } catch (Exception e) { return new JSONArray(); } }
    private JSONObject profileById(String key, String id) {
        if (id == null || id.isEmpty()) return new JSONObject();
        JSONArray a = profiles(key);
        for (int i = 0; i < a.length(); i++) { JSONObject o = a.optJSONObject(i); if (o != null && id.equals(o.optString("id", ""))) return o; }
        return new JSONObject();
    }
    private void upsert(String key, JSONObject profile) throws Exception {
        JSONArray a = profiles(key), out = new JSONArray();
        boolean replaced = false;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && profile.optString("id", "").equals(o.optString("id", ""))) { out.put(profile); replaced = true; }
            else if (o != null) out.put(o);
        }
        if (!replaced) out.put(profile);
        if (!prefs.edit().putString(key, out.toString()).commit()) throw new Exception("storage");
    }

    private void renderProfiles(String key, String title, LinearLayout parent, ProfileClick click) {
        LinearLayout c = card(); c.addView(h(title)); JSONArray a = profiles(key);
        if (a.length() == 0) c.addView(note("None saved yet."));
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i); if (o == null) continue;
            Button b = secondary(o.optString("label", "Saved profile")); final String id = o.optString("id", "");
            b.setOnClickListener(v -> click.onClick(id)); c.addView(b, buttonLp());
        }
        parent.addView(c);
    }

    private String[] profileLabels(String key) {
        JSONArray a = profiles(key); String[] labels = new String[Math.max(1, a.length() + 1)]; labels[0] = "None selected";
        for (int i = 0; i < a.length(); i++) labels[i + 1] = a.optJSONObject(i) == null ? "Saved profile" : a.optJSONObject(i).optString("label", "Saved profile");
        return labels;
    }
    private int selectedProfileIndex(String key, String id) { if (id == null || id.isEmpty()) return 0; JSONArray a = profiles(key); for (int i = 0; i < a.length(); i++) if (id.equals(a.optJSONObject(i).optString("id", ""))) return i + 1; return 0; }
    private String selectedProfileId(String key, int spinnerIndex) { if (spinnerIndex <= 0) return ""; JSONArray a = profiles(key); JSONObject o = a.optJSONObject(spinnerIndex - 1); return o == null ? "" : o.optString("id", ""); }

    private void updateVehicleSummary(JSONObject active) {
        if (vehicleSummary == null) return;
        String y = year == null ? active.optString("year", "") : value(year), mk = make == null ? active.optString("make", "") : value(make), md = model == null ? active.optString("model", "") : value(model);
        double base = n(mpg == null ? active.optString("mpg", "0") : value(mpg));
        double pay = n(payload == null ? active.optString("payload", "0") : value(payload));
        double od = n(odometer == null ? active.optString("odometer", "0") : value(odometer));
        double due = n(nextOil == null ? active.optString("nextOil", "0") : value(nextOil));
        vehicleSummary.setText((y + " " + mk + " " + md).trim() + "\nEPA MPG: " + one(base) + " • Payload estimate: " + one(adjustedMpg(base, pay)) + " MPG\nOil life estimate: " + Math.round(oilPct(od, due)) + "% • Next change in about " + Math.max(0, Math.round(due - od)) + " mi");
    }

    private double adjustedMpg(double base, double pounds) { return base <= 0 ? 0 : base * (1 - Math.min(.25, Math.max(0, pounds) / 100.0 * .01)); }
    private double oilPct(double mileage, double due) { if (due <= mileage) return 0; return Math.max(0, Math.min(100, (due - mileage) / 5000.0 * 100.0)); }

    private String findCommonsExact(String y, String mk, String md) {
        String q = (y + " " + mk + " " + md).trim();
        try {
            String api = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrnamespace=6&gsrlimit=20&gsrsearch=" + enc(q) + "&prop=imageinfo&iiprop=url&iiurlwidth=1200&format=json&origin=*";
            JSONObject query = new JSONObject(get(api)).optJSONObject("query"); if (query == null) return null;
            JSONObject pages = query.optJSONObject("pages"); if (pages == null) return null;
            Iterator<String> keys = pages.keys();
            while (keys.hasNext()) {
                JSONObject p = pages.getJSONObject(keys.next()); String title = p.optString("title", "");
                String norm = normalize(title); if (!norm.contains(normalize(mk)) || !norm.contains(normalize(md))) continue;
                if (!y.isEmpty() && !title.contains(y)) continue;
                JSONArray info = p.optJSONArray("imageinfo"); if (info == null || info.length() == 0) continue;
                JSONObject ii = info.getJSONObject(0); String url = ii.optString("thumburl", ii.optString("url", "")); if (!url.isEmpty()) return url;
            }
        } catch (Exception ignored) { }
        return null;
    }

    private String commonsFileUrl(String filename) {
        try {
            String api = "https://commons.wikimedia.org/w/api.php?action=query&titles=File:" + enc(filename) + "&prop=imageinfo&iiprop=url&iiurlwidth=1200&format=json&origin=*";
            JSONObject pages = new JSONObject(get(api)).getJSONObject("query").getJSONObject("pages"); Iterator<String> keys = pages.keys();
            while (keys.hasNext()) { JSONArray info = pages.getJSONObject(keys.next()).optJSONArray("imageinfo"); if (info != null && info.length() > 0) { JSONObject ii = info.getJSONObject(0); String url = ii.optString("thumburl", ii.optString("url", "")); if (!url.isEmpty()) return url; } }
        } catch (Exception ignored) { }
        return null;
    }

    private Bitmap downloadBitmap(String u) throws Exception { HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection(); c.setConnectTimeout(12000); c.setReadTimeout(20000); c.setRequestProperty("User-Agent", "TrailboundAndroid/5.0"); try (InputStream in = c.getInputStream()) { return BitmapFactory.decodeStream(in); } finally { c.disconnect(); } }
    private String vehicleImageFile(String id) { return "vehicle_" + id.replaceAll("[^a-zA-Z0-9_-]", "") + ".jpg"; }

    private Document xml(String u) throws Exception { HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection(); c.setRequestProperty("User-Agent", "TrailboundAndroid/5.0"); try { return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(c.getInputStream()); } finally { c.disconnect(); } }
    private String get(String u) throws Exception { HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection(); c.setConnectTimeout(12000); c.setReadTimeout(18000); c.setRequestProperty("User-Agent", "TrailboundAndroid/5.0"); c.setRequestProperty("Accept", "application/json,text/html,application/xml,*/*"); try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()))) { StringBuilder s = new StringBuilder(); String line; while ((line = br.readLine()) != null) s.append(line); return s.toString(); } finally { c.disconnect(); } }
    private String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }
    private String normalize(String s) { return s == null ? "" : s.toLowerCase(Locale.US).replaceAll("[^a-z0-9]", ""); }

    private void loadBackground() { ImageView bg = findViewById(9001); io.execute(() -> { try (InputStream in = new URL(BG_URL).openStream()) { Bitmap bm = BitmapFactory.decodeStream(in); runOnUiThread(() -> bg.setImageBitmap(bm)); } catch (Exception ignored) { } }); }
    private MapView makeMap() { MapView m = new MapView(this); m.setTileSource(TileSourceFactory.MAPNIK); m.setMultiTouchControls(true); m.getController().setZoom(5.0); return m; }
    private void mark(MapView m, GeoPoint p, String title) { Marker x = new Marker(m); x.setPosition(p); x.setTitle(title); m.getOverlays().add(x); }

    private LinearLayout card() { LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(16), dp(16), dp(16), dp(16)); c.setBackground(round(CARD, 20, Color.rgb(91, 96, 73), 1)); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(12); c.setLayoutParams(p); return c; }
    private TextView h(String s) { TextView x = text(s, 20, true); x.setTextColor(Color.rgb(239, 255, 209)); x.setPadding(0, 0, 0, dp(8)); return x; }
    private TextView note(String s) { TextView x = text(s, 13, false); x.setTextColor(Color.rgb(238, 231, 215)); x.setPadding(0, 0, 0, dp(8)); return x; }
    private EditText field(LinearLayout p, String label, String value, boolean number) { TextView l = text(label, 12, true); l.setTextColor(Color.rgb(249, 241, 222)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(8); p.addView(l, lp); EditText e = new EditText(this); e.setText(value); e.setTextColor(Color.WHITE); e.setHintTextColor(Color.rgb(197, 192, 179)); e.setTextSize(16); e.setSingleLine(true); e.setPadding(dp(12), 0, dp(12), 0); e.setBackground(round(Color.rgb(9, 12, 8), 14, Color.rgb(120, 128, 94), 1)); if (number) e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL); LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, dp(52)); ep.topMargin = dp(5); p.addView(e, ep); return e; }
    private Spinner spinner(LinearLayout p, String label, String[] values, int selected) { TextView l = text(label, 12, true); l.setTextColor(Color.rgb(249, 241, 222)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(8); p.addView(l, lp); Spinner s = new Spinner(this); ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values); s.setAdapter(a); s.setSelection(Math.max(0, Math.min(selected, values.length - 1))); p.addView(s, new LinearLayout.LayoutParams(-1, dp(54))); return s; }
    private TextView info(String s) { TextView x = text(s, 15, false); x.setTextColor(Color.WHITE); x.setLineSpacing(0, 1.15f); x.setPadding(dp(14), dp(14), dp(14), dp(14)); x.setBackground(round(Color.rgb(8, 10, 7), 14, Color.rgb(100, 107, 79), 1)); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(10); x.setLayoutParams(p); return x; }
    private Button primary(String s) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setBackground(round(GREEN, 14, Color.rgb(190, 215, 145), 1)); return b; }
    private Button secondary(String s) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setBackground(round(BROWN, 14, Color.rgb(193, 157, 110), 1)); return b; }
    private Button navButton(String s) { Button b = secondary(s); b.setTextSize(13); return b; }
    private void select(Button b) { for (Button x : new Button[]{tripTab, vehicleTab, hotelTab, areaTab}) x.setBackground(round(x == b ? GREEN : Color.rgb(34, 37, 29), 14, x == b ? Color.rgb(181, 201, 143) : Color.rgb(79, 84, 65), 1)); }
    private LinearLayout row() { LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setPadding(0, dp(8), 0, 0); return r; }
    private LinearLayout.LayoutParams weight() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(56), 1); p.setMargins(dp(3), 0, dp(3), 0); return p; }
    private LinearLayout.LayoutParams buttonLp() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(54)); p.topMargin = dp(10); return p; }
    private TextView text(String s, int sp, boolean bold) { TextView x = new TextView(this); x.setText(s); x.setTextColor(CREAM); x.setTextSize(sp); if (bold) x.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return x; }
    private GradientDrawable round(int fill, int radius, int stroke, int sw) { GradientDrawable g = new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp(radius)); g.setStroke(dp(sw), stroke); return g; }
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }
    private String value(EditText e) { return e == null || e.getText() == null ? "" : e.getText().toString(); }
    private double n(String s) { try { return Double.parseDouble(s == null ? "" : s.trim()); } catch (Exception e) { return 0; } }
    private String one(double d) { return String.format(Locale.US, "%.1f", d); }
    private String money(double d) { return NumberFormat.getCurrencyInstance(Locale.US).format(d); }
    private int fuelIndex(String s) { if ("Midgrade".equalsIgnoreCase(s)) return 1; if ("Premium".equalsIgnoreCase(s)) return 2; if ("Diesel".equalsIgnoreCase(s)) return 3; return 0; }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    @Override protected void onPause() { if (map != null) map.onPause(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); if (map != null) map.onResume(); }
    @Override protected void onDestroy() { io.shutdownNow(); super.onDestroy(); }

    private interface ProfileClick { void onClick(String id); }
}
