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
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.NumberFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

public class MainActivity extends Activity {
    private final int GREEN = Color.rgb(76, 99, 54);
    private final int BROWN = Color.rgb(101, 73, 45);
    private final int CREAM = Color.rgb(255, 250, 238);
    private final int CARD = Color.argb(246, 18, 20, 16);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private SharedPreferences prefs;
    private LinearLayout content;
    private Button tripTab, vehicleTab, hotelTab, infoTab;

    private EditText start, end, miles, gasPrice, snacks, hotelCost, extras;
    private EditText year, make, model, mpg, odometer, nextOil, payload;
    private EditText hotelName, hotelAddress;
    private CheckBox includeHotel;
    private TextView tripResult, areaResult, oilLifeText, adjustedMpgText, gasSourceText, carTitle;
    private ImageView carImage;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("trailbound", MODE_PRIVATE);

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(12), dp(18), dp(12), dp(10));
        shell.setBackgroundColor(Color.rgb(20, 22, 17));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = text("66", 18, true);
        badge.setGravity(Gravity.CENTER);
        badge.setTextColor(Color.rgb(113, 43, 31));
        badge.setBackground(round(Color.rgb(255,244,214), 15, Color.rgb(113,43,31), 3));
        top.addView(badge, new LinearLayout.LayoutParams(dp(54), dp(44)));
        TextView title = text("  TRAILBOUND", 26, true);
        title.setTextColor(Color.WHITE);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        shell.addView(top);

        TextView subtitle = text("Road-trip planning without the clutter.", 13, true);
        subtitle.setTextColor(Color.rgb(236, 229, 210));
        subtitle.setPadding(dp(2), dp(4), 0, dp(8));
        shell.addView(subtitle);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(4), 0, dp(18));
        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(0, dp(6), 0, 0);
        tripTab=navButton("Trip");
        vehicleTab=navButton("Car");
        hotelTab=navButton("Hotel");
        infoTab=navButton("Area");
        nav.addView(tripTab, weight());
        nav.addView(vehicleTab, weight());
        nav.addView(hotelTab, weight());
        nav.addView(infoTab, weight());
        shell.addView(nav);

        tripTab.setOnClickListener(v->showTrip());
        vehicleTab.setOnClickListener(v->showVehicle());
        hotelTab.setOnClickListener(v->showHotel());
        infoTab.setOnClickListener(v->showInfo());

        setContentView(shell);
        showTrip();
    }

    private void showTrip() {
        select(tripTab); content.removeAllViews();
        LinearLayout c = card();
        c.addView(section("Plan the drive"));
        start = input("Starting point", prefs.getString("start","Home"), false);
        end = input("Destination", prefs.getString("end","Destination"), false);
        addField(c,"FROM",start); addField(c,"TO",end);

        Button route = primary("Find route & calculate");
        route.setOnClickListener(v->lookupRouteAndCalculate());
        c.addView(route, buttonLp());

        miles = input("One-way miles",prefs.getString("miles",""),true);
        gasPrice = input("Prepared gas price",prefs.getString("gas","3.75"),true);
        LinearLayout row = row();
        row.addView(fieldBlock("ONE-WAY MILES",miles), new LinearLayout.LayoutParams(0,-2,1));
        row.addView(fieldBlock("HIGH-SIDE GAS",gasPrice), new LinearLayout.LayoutParams(0,-2,1));
        c.addView(row);

        Button gas = secondary("Refresh conservative gas estimate");
        gas.setOnClickListener(v->autoGasPrice());
        c.addView(gas, buttonLp());
        gasSourceText = smallBox("Trailbound uses a conservative high-side fuel estimate so your budget is less likely to come up short.");
        c.addView(gasSourceText);

        c.addView(section("Trip budget"));
        snacks=input("Snacks & drinks",prefs.getString("snacks","40"),true);
        extras=input("Other spending",prefs.getString("extras","25"),true);
        LinearLayout budget = row();
        budget.addView(fieldBlock("SNACKS",snacks), new LinearLayout.LayoutParams(0,-2,1));
        budget.addView(fieldBlock("OTHER",extras), new LinearLayout.LayoutParams(0,-2,1));
        c.addView(budget);

        Button calc = primary("Update total");
        calc.setOnClickListener(v->calculateTrip());
        c.addView(calc, buttonLp());

        tripResult = infoBox("Route, fuel, and total trip estimate will appear here.");
        c.addView(tripResult);
        content.addView(c);
    }

    private void showVehicle() {
        select(vehicleTab); content.removeAllViews();
        LinearLayout c=card();
        c.addView(section("Your vehicle"));

        carTitle = text("",20,true);
        carTitle.setTextColor(Color.WHITE);
        c.addView(carTitle);

        carImage = new ImageView(this);
        carImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        carImage.setBackground(round(Color.rgb(32,36,28),16,Color.rgb(91,103,73),1));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, dp(180));
        ip.topMargin=dp(10); ip.bottomMargin=dp(10);
        c.addView(carImage,ip);

        year=input("Year",prefs.getString("year","2020"),true);
        make=input("Make",prefs.getString("make","Toyota"),false);
        model=input("Model",prefs.getString("model","Camry"),false);
        mpg=input("EPA combined MPG",prefs.getString("mpg","28"),true);
        addField(c,"YEAR",year); addField(c,"MAKE",make); addField(c,"MODEL",model); addField(c,"BASE MPG",mpg);

        LinearLayout actions=row();
        Button mpgBtn=primary("Auto MPG");
        Button photoBtn=secondary("Load car photo");
        mpgBtn.setOnClickListener(v->lookupMpg());
        photoBtn.setOnClickListener(v->loadCarPhoto());
        actions.addView(mpgBtn,new LinearLayout.LayoutParams(0,dp(52),1));
        actions.addView(photoBtn,new LinearLayout.LayoutParams(0,dp(52),1));
        c.addView(actions);

        c.addView(section("Mileage & maintenance"));
        odometer=input("Current mileage",prefs.getString("odometer","50000"),true);
        nextOil=input("Next oil change due",prefs.getString("nextOil","55000"),true);
        LinearLayout mr=row();
        mr.addView(fieldBlock("CURRENT MILES",odometer),new LinearLayout.LayoutParams(0,-2,1));
        mr.addView(fieldBlock("NEXT OIL DUE",nextOil),new LinearLayout.LayoutParams(0,-2,1));
        c.addView(mr);

        oilLifeText=infoBox("");
        c.addView(oilLifeText);

        payload=input("Payload in pounds",prefs.getString("payload","0"),true);
        addField(c,"EXTRA PAYLOAD",payload);
        adjustedMpgText=smallBox("");
        c.addView(adjustedMpgText);

        Button save=primary("Save vehicle");
        save.setOnClickListener(v->{saveVehicle(); refreshVehicleSummary(); loadCarPhoto(); toast("Vehicle saved");});
        c.addView(save,buttonLp());

        odometer.setOnFocusChangeListener((v,has)->{ if(!has) refreshVehicleSummary();});
        nextOil.setOnFocusChangeListener((v,has)->{ if(!has) refreshVehicleSummary();});
        payload.setOnFocusChangeListener((v,has)->{ if(!has) refreshVehicleSummary();});
        mpg.setOnFocusChangeListener((v,has)->{ if(!has) refreshVehicleSummary();});

        refreshVehicleSummary();
        loadCarPhoto();
        content.addView(c);
    }

    private void showHotel() {
        select(hotelTab); content.removeAllViews();
        LinearLayout c=card();
        c.addView(section("Hotel"));

        hotelName=input("Hotel name",prefs.getString("hotelName",""),false);
        hotelAddress=input("Hotel address",prefs.getString("hotelAddress",""),false);
        hotelCost=input("Hotel total",prefs.getString("hotelCost","160"),true);
        addField(c,"HOTEL",hotelName);
        addField(c,"ADDRESS",hotelAddress);
        addField(c,"HOTEL COST",hotelCost);

        includeHotel = new CheckBox(this);
        includeHotel.setText("Include hotel in trip total");
        includeHotel.setTextColor(Color.WHITE);
        includeHotel.setTextSize(16);
        includeHotel.setChecked(prefs.getBoolean("includeHotel",true));
        includeHotel.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.rgb(145,174,105)));
        c.addView(includeHotel);

        Button save=primary("Save hotel");
        save.setOnClickListener(v->{saveHotel();toast("Hotel saved");});
        c.addView(save,buttonLp());

        Button explore=secondary("Get hotel & area info");
        explore.setOnClickListener(v->{saveHotel();fetchHotelAreaInfo();});
        c.addView(explore,buttonLp());

        areaResult=infoBox("Save a hotel to use it as your local base. Trailbound will keep it separate from the destination and show area/weather context.");
        c.addView(areaResult);
        content.addView(c);
    }

    private void showInfo() {
        select(infoTab); content.removeAllViews();
        LinearLayout c=card();
        c.addView(section("Trip & area"));
        c.addView(smallBox("This screen focuses on the whole area and drive: distance, driving time, arrival weather, hotel base, fuel needs, and practical road-trip context."));
        Button update=primary("Refresh area info");
        update.setOnClickListener(v->fetchTripAndAreaInfo());
        c.addView(update,buttonLp());
        areaResult=infoBox(buildStoredSummary());
        c.addView(areaResult);
        content.addView(c);
    }

    private void lookupRouteAndCalculate(){
        saveTripInputs();
        String s=prefs.getString("start","").trim(), e=prefs.getString("end","").trim();
        if(s.isEmpty()||e.isEmpty()){toast("Enter a start and destination");return;}
        tripResult.setText("Finding your route…");
        executor.execute(()->{
            try{
                double[] a=geocode(s), b=geocode(e);
                String url="https://router.project-osrm.org/route/v1/driving/"+a[1]+","+a[0]+";"+b[1]+","+b[0]+"?overview=false";
                JSONObject r=new JSONObject(get(url)).getJSONArray("routes").getJSONObject(0);
                double routeMiles=r.getDouble("distance")/1609.344;
                double durationHours=r.getDouble("duration")/3600.0;
                prefs.edit().putString("miles",String.format(Locale.US,"%.1f",routeMiles)).putFloat("duration",(float)durationHours).apply();
                runOnUiThread(()->{
                    miles.setText(String.format(Locale.US,"%.1f",routeMiles));
                    autoGasPrice();
                });
            }catch(Exception ex){
                runOnUiThread(()->tripResult.setText("Automatic route lookup failed. You can still enter miles manually.\n\n"+ex.getMessage()));
            }
        });
    }

    private void autoGasPrice(){
        saveTripInputs();
        if(gasSourceText!=null) gasSourceText.setText("Refreshing a conservative fuel-price estimate…");
        executor.execute(()->{
            double national=0;
            String source="Prepared fallback";
            try{
                String html=get("https://gasprices.aaa.com/");
                Pattern p=Pattern.compile("\\$([0-9]+\\.[0-9]{3})");
                Matcher m=p.matcher(html);
                if(m.find()){ national=Double.parseDouble(m.group(1)); source="AAA national average"; }
            }catch(Exception ignored){}
            if(national<=0) national=3.55;
            String fuel=prefs.getString("fuelType","Regular");
            double fuelAdd=fuel.equals("Premium")?.65:fuel.equals("Diesel")?.50:fuel.equals("Midgrade")?.35:0;
            double prepared=(national+fuelAdd)*1.10;
            prefs.edit().putString("gas",String.format(Locale.US,"%.2f",prepared)).apply();
            final double n=national, pr=prepared; final String src=source;
            runOnUiThread(()->{
                if(gasPrice!=null) gasPrice.setText(String.format(Locale.US,"%.2f",pr));
                if(gasSourceText!=null) gasSourceText.setText(src+": "+money(n)+" • Trailbound prepared estimate: "+money(pr)+" (10% high-side buffer).");
                calculateTrip();
            });
        });
    }

    private void calculateTrip(){
        saveTripInputs();
        double mi=num(prefs.getString("miles","0"));
        double baseMpg=num(prefs.getString("mpg","28"));
        double payload=num(prefs.getString("payload","0"));
        double m=adjustedMpg(baseMpg,payload);
        double gp=num(prefs.getString("gas","3.75"));
        if(mi<=0||m<=0||gp<=0){toast("Enter miles, MPG and gas price");return;}
        boolean inc=prefs.getBoolean("includeHotel",true);
        double rt=mi*2, gallons=rt/m, gas=gallons*gp;
        double hotel=inc?num(prefs.getString("hotelCost","0")):0;
        double total=gas+num(prefs.getString("snacks","0"))+hotel+num(prefs.getString("extras","0"));
        double dur=prefs.getFloat("duration",0);
        String out="ROUND TRIP  "+one(rt)+" mi\n"+
                "DRIVING TIME  "+(dur>0?one(dur*2)+" hr":"Route first for time")+"\n"+
                "ADJUSTED MPG  "+one(m)+"\n"+
                "FUEL NEEDED  "+one(gallons)+" gal\n"+
                "PREPARED GAS BUDGET  "+money(gas)+"\n\n"+
                "TRIP TOTAL  "+money(total)+"\n"+
                (inc?"Hotel included":"Hotel excluded");
        if(tripResult!=null) tripResult.setText(out);
    }

    private void lookupMpg(){
        saveVehicle();
        String y=prefs.getString("year",""), mk=prefs.getString("make",""), md=prefs.getString("model","");
        if(y.isEmpty()||mk.isEmpty()||md.isEmpty()){toast("Enter year, make and model");return;}
        executor.execute(()->{
            try{
                Document d=xml("https://www.fueleconomy.gov/ws/rest/vehicle/menu/options?year="+enc(y)+"&make="+enc(mk)+"&model="+enc(md));
                NodeList vals=d.getElementsByTagName("value");
                if(vals.getLength()==0)throw new Exception("No matching EPA vehicle found");
                String id=vals.item(0).getTextContent().trim();
                Document vd=xml("https://www.fueleconomy.gov/ws/rest/vehicle/"+id);
                NodeList mpgNodes=vd.getElementsByTagName("comb08");
                if(mpgNodes.getLength()==0)throw new Exception("MPG unavailable");
                String found=mpgNodes.item(0).getTextContent().trim();
                prefs.edit().putString("mpg",found).apply();
                runOnUiThread(()->{mpg.setText(found);refreshVehicleSummary();toast("EPA MPG loaded");});
            }catch(Exception ex){runOnUiThread(()->toast("MPG lookup failed: "+ex.getMessage()));}
        });
    }

    private void loadCarPhoto(){
        if(carImage==null)return;
        saveVehicle();
        String query=(prefs.getString("year","")+" "+prefs.getString("make","")+" "+prefs.getString("model","")+" car").trim();
        carTitle.setText(query.replace(" car",""));
        executor.execute(()->{
            try{
                String api="https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch="+enc(query)+"&gsrlimit=5&prop=pageimages&pithumbsize=900&format=json&origin=*";
                JSONObject pages=new JSONObject(get(api)).getJSONObject("query").getJSONObject("pages");
                String imageUrl=null;
                Iterator<String> keys=pages.keys();
                while(keys.hasNext()){
                    JSONObject page=pages.getJSONObject(keys.next());
                    JSONObject thumb=page.optJSONObject("thumbnail");
                    if(thumb!=null){imageUrl=thumb.optString("source",null); if(imageUrl!=null)break;}
                }
                if(imageUrl==null)throw new Exception("No photo found");
                HttpURLConnection c=(HttpURLConnection)new URL(imageUrl).openConnection();
                c.setRequestProperty("User-Agent","TrailboundAndroid/3.1");
                try(InputStream in=c.getInputStream()){
                    Bitmap bmp=BitmapFactory.decodeStream(in);
                    runOnUiThread(()->carImage.setImageBitmap(bmp));
                }finally{c.disconnect();}
            }catch(Exception ex){
                runOnUiThread(()->{
                    carImage.setImageResource(android.R.drawable.ic_menu_gallery);
                    toast("Could not find a matching car photo");
                });
            }
        });
    }

    private void refreshVehicleSummary(){
        saveVehicle();
        double current=num(prefs.getString("odometer","0"));
        double due=num(prefs.getString("nextOil","0"));
        double remaining=Math.max(0,due-current);
        double oilPct=Math.max(0,Math.min(100,(remaining/5000.0)*100.0));
        double base=num(prefs.getString("mpg","28"));
        double pay=num(prefs.getString("payload","0"));
        double adj=adjustedMpg(base,pay);
        if(oilLifeText!=null) oilLifeText.setText("Estimated oil life  "+Math.round(oilPct)+"%\n"+one(remaining)+" miles until the next oil-change target.\nBased on a 5,000-mile service interval.");
        if(adjustedMpgText!=null) adjustedMpgText.setText("Payload-adjusted MPG: "+one(adj)+"\nEstimate only — actual MPG varies with vehicle, speed, terrain, tires, weather and load placement.");
        if(carTitle!=null) carTitle.setText((prefs.getString("year","")+" "+prefs.getString("make","")+" "+prefs.getString("model","")).trim());
    }

    private double adjustedMpg(double base,double pounds){
        if(base<=0)return 0;
        double reduction=Math.min(.25,Math.max(0,pounds)/100.0*0.01);
        return base*(1.0-reduction);
    }

    private void fetchTripAndAreaInfo(){
        String dest=prefs.getString("end","").trim();
        if(dest.isEmpty()||dest.equalsIgnoreCase("Destination")){toast("Add a destination on the Trip screen");return;}
        areaResult.setText("Building area overview…");
        executor.execute(()->{
            try{
                double[] g=geocode(dest);
                JSONObject w=new JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude="+g[0]+"&longitude="+g[1]+"&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m&temperature_unit=fahrenheit&wind_speed_unit=mph")).getJSONObject("current");
                String summary=buildStoredSummary()+"\n\nARRIVAL CONDITIONS\n"+one(w.optDouble("temperature_2m"))+"°F • feels like "+one(w.optDouble("apparent_temperature"))+"°F\nWind "+one(w.optDouble("wind_speed_10m"))+" mph";
                runOnUiThread(()->areaResult.setText(summary));
            }catch(Exception ex){runOnUiThread(()->areaResult.setText(buildStoredSummary()+"\n\nLive area update failed: "+ex.getMessage()));}
        });
    }

    private void fetchHotelAreaInfo(){
        String query=prefs.getString("hotelAddress","").trim();
        if(query.isEmpty()){toast("Enter a hotel address first");return;}
        areaResult.setText("Looking up the hotel area…");
        executor.execute(()->{
            try{
                double[] g=geocode(query);
                JSONObject w=new JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude="+g[0]+"&longitude="+g[1]+"&current=temperature_2m,apparent_temperature,wind_speed_10m&temperature_unit=fahrenheit&wind_speed_unit=mph")).getJSONObject("current");
                String s="HOTEL BASE\n"+prefs.getString("hotelName","Hotel")+"\n"+query+"\n\nCURRENT CONDITIONS\n"+one(w.optDouble("temperature_2m"))+"°F • feels like "+one(w.optDouble("apparent_temperature"))+"°F\nWind "+one(w.optDouble("wind_speed_10m"))+" mph\n\nHotel cost is "+(prefs.getBoolean("includeHotel",true)?"included in":"excluded from")+" your trip total.";
                runOnUiThread(()->areaResult.setText(s));
            }catch(Exception ex){runOnUiThread(()->areaResult.setText("Hotel saved, but live area information could not load.\n\n"+ex.getMessage()));}
        });
    }

    private String buildStoredSummary(){
        double mi=num(prefs.getString("miles","0")), dur=prefs.getFloat("duration",0);
        StringBuilder s=new StringBuilder();
        s.append("TRIP\n").append(prefs.getString("start","Home")).append(" → ").append(prefs.getString("end","Destination"));
        if(mi>0)s.append("\n").append(one(mi)).append(" mi each way");
        if(dur>0)s.append(" • ").append(one(dur)).append(" hr each way");
        s.append("\n\nCAR\n").append(prefs.getString("year","")).append(" ").append(prefs.getString("make","")).append(" ").append(prefs.getString("model",""));
        String hotel=prefs.getString("hotelName","");
        if(!hotel.isEmpty())s.append("\n\nHOTEL\n").append(hotel).append("\n").append(prefs.getString("hotelAddress",""));
        s.append("\n\nBUDGET STYLE\nFuel uses a conservative high-side estimate to leave extra room for price variation.");
        return s.toString();
    }

    private double[] geocode(String q) throws Exception {
        JSONArray a=new JSONArray(get("https://nominatim.openstreetmap.org/search?format=json&limit=1&q="+enc(q)));
        if(a.length()==0)throw new Exception("Place not found: "+q);
        JSONObject o=a.getJSONObject(0);
        return new double[]{o.getDouble("lat"),o.getDouble("lon")};
    }

    private String get(String u) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();
        c.setConnectTimeout(12000); c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent","TrailboundAndroid/3.1");
        c.setRequestProperty("Accept","application/json,application/xml,text/xml,text/html,*/*");
        try(BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream()))){
            StringBuilder s=new StringBuilder(); String line;
            while((line=br.readLine())!=null)s.append(line);
            return s.toString();
        } finally { c.disconnect(); }
    }

    private Document xml(String u) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();
        c.setRequestProperty("User-Agent","TrailboundAndroid/3.1");
        c.setRequestProperty("Accept","application/xml");
        try{return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(c.getInputStream());}
        finally{c.disconnect();}
    }

    private String enc(String s) throws Exception {return URLEncoder.encode(s,"UTF-8");}

    private void saveTripInputs(){
        if(start!=null)prefs.edit()
                .putString("start",start.getText().toString())
                .putString("end",end.getText().toString())
                .putString("miles",miles==null?prefs.getString("miles",""):miles.getText().toString())
                .putString("gas",gasPrice==null?prefs.getString("gas","3.75"):gasPrice.getText().toString())
                .putString("snacks",snacks==null?prefs.getString("snacks","40"):snacks.getText().toString())
                .putString("extras",extras==null?prefs.getString("extras","25"):extras.getText().toString()).apply();
    }

    private void saveVehicle(){
        if(year!=null)prefs.edit()
                .putString("year",year.getText().toString()).putString("make",make.getText().toString())
                .putString("model",model.getText().toString()).putString("mpg",mpg.getText().toString())
                .putString("odometer",odometer==null?prefs.getString("odometer","50000"):odometer.getText().toString())
                .putString("nextOil",nextOil==null?prefs.getString("nextOil","55000"):nextOil.getText().toString())
                .putString("payload",payload==null?prefs.getString("payload","0"):payload.getText().toString()).apply();
    }

    private void saveHotel(){
        if(hotelName!=null)prefs.edit()
                .putString("hotelName",hotelName.getText().toString())
                .putString("hotelAddress",hotelAddress.getText().toString())
                .putString("hotelCost",hotelCost.getText().toString())
                .putBoolean("includeHotel",includeHotel==null?prefs.getBoolean("includeHotel",true):includeHotel.isChecked()).apply();
    }

    private void select(Button b){
        for(Button x:new Button[]{tripTab,vehicleTab,hotelTab,infoTab}){
            x.setTextColor(Color.WHITE);
            x.setBackground(round(x==b?GREEN:Color.rgb(34,37,29),14,x==b?Color.rgb(181,201,143):Color.rgb(79,84,65),1));
        }
    }

    private Button navButton(String s){
        Button b=new Button(this); b.setText(s); b.setAllCaps(false);
        b.setTextSize(13); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return b;
    }

    private LinearLayout.LayoutParams weight(){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(52),1);
        p.setMargins(dp(3),0,dp(3),0); return p;
    }

    private LinearLayout card(){
        LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16),dp(16),dp(16),dp(16));
        c.setBackground(round(CARD,20,Color.rgb(91,96,73),1)); return c;
    }

    private LinearLayout row(){
        LinearLayout r=new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setPadding(0,dp(6),0,0); return r;
    }

    private LinearLayout fieldBlock(String label, EditText e){
        LinearLayout b=new LinearLayout(this); b.setOrientation(LinearLayout.VERTICAL); b.setPadding(dp(3),0,dp(3),0);
        TextView l=text(label,12,true); l.setTextColor(Color.rgb(245,237,218)); b.addView(l);
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,dp(52)); ep.topMargin=dp(5); b.addView(e,ep); return b;
    }

    private TextView section(String s){
        TextView t=text(s,20,true); t.setTextColor(Color.rgb(239,255,209));
        t.setPadding(0,dp(4),0,dp(8)); return t;
    }

    private EditText input(String hint,String value,boolean number){
        EditText e=new EditText(this); e.setText(value); e.setHint(hint);
        e.setTextColor(Color.WHITE); e.setHintTextColor(Color.rgb(198,194,181));
        e.setTextSize(16); e.setSingleLine(true); e.setPadding(dp(12),0,dp(12),0);
        e.setBackground(round(Color.rgb(10,12,9),14,Color.rgb(119,126,94),1));
        if(number)e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return e;
    }

    private void addField(LinearLayout c,String label,EditText e){
        TextView l=text(label,12,true); l.setTextColor(Color.rgb(245,237,218));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.topMargin=dp(10); c.addView(l,lp);
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,dp(52)); ep.topMargin=dp(5); c.addView(e,ep);
    }

    private Button primary(String s){
        Button b=new Button(this); b.setText(s); b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD); b.setAllCaps(false);
        b.setBackground(round(Color.rgb(91,118,64),14,Color.rgb(190,215,145),1)); return b;
    }

    private Button secondary(String s){
        Button b=new Button(this); b.setText(s); b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD); b.setAllCaps(false);
        b.setBackground(round(BROWN,14,Color.rgb(193,157,110),1)); return b;
    }

    private LinearLayout.LayoutParams buttonLp(){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52)); p.topMargin=dp(10); return p;
    }

    private TextView infoBox(String s){
        TextView t=text(s,15,false); t.setTextColor(Color.WHITE); t.setLineSpacing(0,1.16f);
        t.setPadding(dp(14),dp(14),dp(14),dp(14));
        t.setBackground(round(Color.rgb(8,10,7),14,Color.rgb(100,107,79),1));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(12); t.setLayoutParams(p); return t;
    }

    private TextView smallBox(String s){
        TextView t=infoBox(s); t.setTextSize(13); return t;
    }

    private TextView text(String s,int sp,boolean bold){
        TextView t=new TextView(this); t.setText(s); t.setTextColor(CREAM); t.setTextSize(sp);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return t;
    }

    private GradientDrawable round(int fill,int radius,int stroke,int sw){
        GradientDrawable g=new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp(radius)); g.setStroke(dp(sw),stroke); return g;
    }

    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private double num(String s){try{return Double.parseDouble(s.trim());}catch(Exception e){return 0;}}
    private String one(double d){return String.format(Locale.US,"%.1f",d);}
    private String money(double d){return NumberFormat.getCurrencyInstance(Locale.US).format(d);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    @Override protected void onDestroy(){executor.shutdownNow();super.onDestroy();}
}
