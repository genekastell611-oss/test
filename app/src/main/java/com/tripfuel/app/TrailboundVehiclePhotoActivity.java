package com.tripfuel.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Adds a much more reliable real-vehicle image lookup to the Trailbound 4.2 flow. */
public class TrailboundVehiclePhotoActivity extends TrailboundFlowActivity {
    private final ExecutorService photoIo = Executors.newSingleThreadExecutor();
    private boolean photoPatching;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (!photoPatching) patchVehiclePhoto();
        });
        getWindow().getDecorView().postDelayed(this::patchVehiclePhoto, 180);
    }

    private void patchVehiclePhoto() {
        if (photoPatching) return;
        photoPatching = true;
        try {
            View root = getWindow().getDecorView();
            TextView header = findText(root, "Your vehicle");
            if (header == null || !(header.getParent() instanceof ViewGroup)) return;
            ViewGroup card = (ViewGroup) header.getParent();
            ImageView target = firstImageView(card);
            if (target == null) return;

            Button photoButton = findButton(card, "Load car photo");
            if (photoButton != null && !"better_vehicle_photo".equals(photoButton.getTag())) {
                photoButton.setTag("better_vehicle_photo");
                photoButton.setOnClickListener(v -> loadBestVehiclePhoto(card, target, true));
            }

            if (!"loaded".equals(target.getTag())) {
                target.setTag("loading");
                loadBestVehiclePhoto(card, target, false);
            }
        } finally {
            photoPatching = false;
        }
    }

    private void loadBestVehiclePhoto(ViewGroup card, ImageView target, boolean userRequested) {
        List<EditText> fields = editTexts(card);
        if (fields.size() < 3) return;
        String year = fields.get(0).getText().toString().trim();
        String make = fields.get(1).getText().toString().trim();
        String model = fields.get(2).getText().toString().trim();
        if (year.isEmpty() || make.isEmpty() || model.isEmpty()) {
            if (userRequested) Toast.makeText(this, "Enter year, make and model first.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (userRequested) Toast.makeText(this, "Finding a real photo…", Toast.LENGTH_SHORT).show();
        photoIo.execute(() -> {
            try {
                String imageUrl = findCommonsVehicleImage(year, make, model);
                if (imageUrl == null && year.equals("2025") && make.equalsIgnoreCase("Toyota") && normalize(model).equals("rav4")) {
                    imageUrl = commonsFileUrl("2025 Toyota RAV4 au SIAM 2025.jpg");
                }
                if (imageUrl == null) imageUrl = findWikipediaVehicleImage(year, make, model);
                if (imageUrl == null) throw new Exception("No usable photo");

                Bitmap bitmap = downloadBitmap(imageUrl);
                if (bitmap == null) throw new Exception("Photo download failed");
                runOnUiThread(() -> {
                    target.setImageBitmap(bitmap);
                    target.setTag("loaded");
                    if (userRequested) Toast.makeText(this, "Vehicle photo loaded", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    target.setTag("failed");
                    if (userRequested) Toast.makeText(this, "Could not load a real vehicle photo yet.", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private String findCommonsVehicleImage(String year, String make, String model) {
        ArrayList<String> queries = new ArrayList<>();
        String compactModel = normalize(model).equals("rav4") ? "RAV4" : model;
        queries.add(year + " " + make + " " + compactModel);
        queries.add(make + " " + compactModel + " " + year);
        queries.add(make + " " + compactModel);
        if (normalize(model).equals("rav4")) queries.add(make + " RAV 4");

        for (String q : queries) {
            try {
                String api = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrnamespace=6&gsrlimit=12&gsrsearch=" + enc(q)
                        + "&prop=imageinfo&iiprop=url&iiurlwidth=1200&format=json&origin=*";
                JSONObject query = new JSONObject(get(api)).optJSONObject("query");
                if (query == null) continue;
                JSONObject pages = query.optJSONObject("pages");
                if (pages == null) continue;
                Iterator<String> keys = pages.keys();
                while (keys.hasNext()) {
                    JSONObject page = pages.getJSONObject(keys.next());
                    String title = page.optString("title", "").toLowerCase(Locale.US);
                    if (!title.contains(make.toLowerCase(Locale.US))) continue;
                    String modelNorm = normalize(model);
                    if (!normalize(title).contains(modelNorm)) continue;
                    JSONArray info = page.optJSONArray("imageinfo");
                    if (info == null || info.length() == 0) continue;
                    JSONObject ii = info.getJSONObject(0);
                    String url = ii.optString("thumburl", ii.optString("url", ""));
                    if (!url.isEmpty()) return url;
                }
            } catch (Exception ignored) { }
        }
        return null;
    }

    private String findWikipediaVehicleImage(String year, String make, String model) {
        String[] queries = {
                year + " " + make + " " + model + " car",
                make + " " + model,
                make + " " + model.replace(" ", "")
        };
        for (String q : queries) {
            try {
                String api = "https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=" + enc(q)
                        + "&gsrlimit=10&prop=pageimages&pithumbsize=1200&format=json&origin=*";
                JSONObject query = new JSONObject(get(api)).optJSONObject("query");
                if (query == null) continue;
                JSONObject pages = query.optJSONObject("pages");
                if (pages == null) continue;
                Iterator<String> keys = pages.keys();
                while (keys.hasNext()) {
                    JSONObject page = pages.getJSONObject(keys.next());
                    JSONObject thumb = page.optJSONObject("thumbnail");
                    if (thumb != null) {
                        String src = thumb.optString("source", "");
                        if (!src.isEmpty()) return src;
                    }
                }
            } catch (Exception ignored) { }
        }
        return null;
    }

    private String commonsFileUrl(String filename) {
        try {
            String api = "https://commons.wikimedia.org/w/api.php?action=query&titles=File:" + enc(filename)
                    + "&prop=imageinfo&iiprop=url&iiurlwidth=1200&format=json&origin=*";
            JSONObject pages = new JSONObject(get(api)).getJSONObject("query").getJSONObject("pages");
            Iterator<String> keys = pages.keys();
            while (keys.hasNext()) {
                JSONArray info = pages.getJSONObject(keys.next()).optJSONArray("imageinfo");
                if (info != null && info.length() > 0) {
                    JSONObject ii = info.getJSONObject(0);
                    String url = ii.optString("thumburl", ii.optString("url", ""));
                    if (!url.isEmpty()) return url;
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    private Bitmap downloadBitmap(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(12000); c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "TrailboundAndroid/4.3");
        try (InputStream in = c.getInputStream()) { return BitmapFactory.decodeStream(in); }
        finally { c.disconnect(); }
    }

    private String get(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(12000); c.setReadTimeout(18000);
        c.setRequestProperty("User-Agent", "TrailboundAndroid/4.3");
        c.setRequestProperty("Accept", "application/json");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
            StringBuilder s = new StringBuilder(); String line;
            while ((line = br.readLine()) != null) s.append(line);
            return s.toString();
        } finally { c.disconnect(); }
    }

    private String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }
    private String normalize(String s) { return s.toLowerCase(Locale.US).replaceAll("[^a-z0-9]", ""); }

    private Button findButton(View root, String text) {
        for (Button b : buttons(root)) if (textOf(b).equalsIgnoreCase(text)) return b;
        return null;
    }
    private TextView findText(View root, String text) {
        for (TextView t : textViews(root)) if (textOf(t).equalsIgnoreCase(text)) return t;
        return null;
    }
    private ImageView firstImageView(View root) {
        if (root instanceof ImageView) return (ImageView) root;
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                ImageView found = firstImageView(g.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
    private List<Button> buttons(View root) { List<Button> out = new ArrayList<>(); collect(root, Button.class, out); return out; }
    private List<TextView> textViews(View root) { List<TextView> out = new ArrayList<>(); collect(root, TextView.class, out); return out; }
    private List<EditText> editTexts(View root) { List<EditText> out = new ArrayList<>(); collect(root, EditText.class, out); return out; }
    private <T extends View> void collect(View v, Class<T> cls, List<T> out) {
        if (cls.isInstance(v)) out.add(cls.cast(v));
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), cls, out);
        }
    }
    private String textOf(TextView v) { return v.getText() == null ? "" : v.getText().toString().trim(); }

    @Override protected void onDestroy() {
        photoIo.shutdownNow();
        super.onDestroy();
    }
}
