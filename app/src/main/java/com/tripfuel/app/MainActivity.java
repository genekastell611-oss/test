package com.tripfuel.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.parsers.DocumentBuilderFactory;

public class MainActivity extends Activity {
    private final int GREEN = Color.rgb(76, 99, 54);
    private final int BROWN = Color.rgb(95, 72, 45);
    private final int CREAM = Color.rgb(244, 235, 214);
    private final int CARD = Color.argb(238, 39, 38, 31);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private LinearLayout content;
    private Button tripTab, vehicleTab, hotelTab, infoTab;
    private EditText start, end, miles, gasPrice, snacks, hotelCost, extras;
    private EditText year, make, model, mpg;
    private EditText hotelName, hotelAddress;
    private TextView tripResult, areaResult;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("trailbound", MODE_PRIVATE);
        HighwayLayout root = new HighwayLayout(this);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(12), dp(18), dp(12), dp(12));
        root.addView(shell, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView badge = text("ROUTE 66", 15, true); badge.setTextColor(Color.rgb(117,54,38)); badge.setGravity(Gravity.CENTER); badge.setBackground(round(Color.rgb(239,228,199), 16, Color.rgb(117,54,38), 3));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(110), dp(48)); bp.bottomMargin=dp(8); shell.addView(badge,bp);
        TextView title=text("TRAILBOUND",30,true); shell.addView(title);
        TextView sub=text("Adventure awaits. We’ll do the math.",14,false); sub.setTextColor(Color.rgb(225,216,197)); shell.addView(sub);

        LinearLayout tabs = new LinearLayout(this); tabs.setOrientation(LinearLayout.HORIZONTAL); tabs.setPadding(0,dp(12),0,dp(8));
        tripTab=tab("TRIP"); vehicleTab=tab("VEHICLE"); hotelTab=tab("HOTEL"); infoTab=tab("AREA INFO");
        tabs.addView(tripTab,weight()); tabs.addView(vehicleTab,weight()); tabs.addView(hotelTab,weight()); tabs.addView(infoTab,weight()); shell.addView(tabs);

        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(0,0,0,dp(28)); scroll.addView(content); shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        tripTab.setOnClickListener(v->showTrip()); vehicleTab.setOnClickListener(v->showVehicle()); hotelTab.setOnClickListener(v->showHotel()); infoTab.setOnClickListener(v->showInfo());
        showTrip();
        setContentView(root);
    }

    private void showTrip(){ select(tripTab); content.removeAllViews(); LinearLayout c=card(); c.addView(section("Trip details"));
        start=input("Starting point",prefs.getString("start","Home"),false); end=input("Destination",prefs.getString("end","Destination"),false); miles=input("One-way route miles",prefs.getString("miles",""),true); gasPrice=input("Average gas price / gallon",prefs.getString("gas","3.25"),true); snacks=input("Snacks & drinks budget",prefs.getString("snacks","40"),true); hotelCost=input("Hotel total",prefs.getString("hotelCost","160"),true); extras=input("Other trip money",prefs.getString("extras","25"),true);
        addField(c,"START",start); addField(c,"DESTINATION",end); addField(c,"ONE-WAY MILES",miles); addField(c,"AVG GAS PRICE",gasPrice); addField(c,"SNACKS & DRINKS",snacks); addField(c,"HOTEL TOTAL",hotelCost); addField(c,"OTHER TRIP MONEY",extras);
        Button route=primary("AUTO ROUTE + CALCULATE"); route.setOnClickListener(v->lookupRouteAndCalculate()); c.addView(route,buttonLp());
        Button calc=secondary("CALCULATE WITH CURRENT NUMBERS"); calc.setOnClickListener(v->calculateTrip()); c.addView(calc,buttonLp());
        tripResult=infoBox("Your full trip cost, fuel use, round-trip distance and drive estimate will appear here."); c.addView(tripResult);
        content.addView(c); }

    private void showVehicle(){ select(vehicleTab); content.removeAllViews(); LinearLayout c=card(); c.addView(section("Saved vehicle"));
        year=input("Year",prefs.getString("year","2020"),true); make=input("Make",prefs.getString("make","Toyota"),false); model=input("Model",prefs.getString("model","Camry"),false); mpg=input("Combined MPG",prefs.getString("mpg","28"),true);
        addField(c,"YEAR",year); addField(c,"MAKE",make); addField(c,"MODEL",model); addField(c,"COMBINED MPG",mpg);
        Button auto=primary("GET MPG AUTOMATICALLY"); auto.setOnClickListener(v->lookupMpg()); c.addView(auto,buttonLp());
        Button save=secondary("SAVE VEHICLE"); save.setOnClickListener(v->{saveVehicle(); toast("Vehicle saved");}); c.addView(save,buttonLp());
        c.addView(infoBox("FuelEconomy.gov is used to look up a matching vehicle and fill combined MPG automatically when available. You can always correct the number manually.")); content.addView(c); }

    private void showHotel(){ select(hotelTab); content.removeAllViews(); LinearLayout c=card(); c.addView(section("Hotel base"));
        hotelName=input("Hotel name",prefs.getString("hotelName",""),false); hotelAddress=input("Hotel address",prefs.getString("hotelAddress",""),false); hotelCost=input("Hotel total",prefs.getString("hotelCost","160"),true);
        addField(c,"HOTEL NAME",hotelName); addField(c,"HOTEL ADDRESS",hotelAddress); addField(c,"HOTEL TOTAL",hotelCost);
        Button save=primary("SAVE HOTEL"); save.setOnClickListener(v->{saveHotel();toast("Hotel saved");}); c.addView(save,buttonLp());
        Button explore=secondary("GET HOTEL AREA INFO"); explore.setOnClickListener(v->{saveHotel(); fetchHotelAreaInfo();}); c.addView(explore,buttonLp());
        areaResult=infoBox("Use the hotel as a second anchor for neighborhood character, nearby services, weather, traveler basics and useful local context."); c.addView(areaResult); content.addView(c); }

    private void showInfo(){ select(infoTab); content.removeAllViews(); LinearLayout c=card(); c.addView(section("Area & trip intelligence"));
        c.addView(infoBox("This screen is about the whole trip, not just destinations: drive distance and time, fuel needs, arrival-area weather, area character, hotel base and practical road-trip context."));
        Button update=primary("UPDATE TRIP + AREA INFO"); update.setOnClickListener(v->fetchTripAndAreaInfo()); c.addView(update,buttonLp());
        areaResult=infoBox(buildStoredSummary()); c.addView(areaResult); content.addView(c); }

    private void lookupRouteAndCalculate(){ saveTripInputs(); String s=prefs.getString("start","").trim(), e=prefs.getString("end","").trim(); if(s.isEmpty()||e.isEmpty()){toast("Enter a start and destination");return;} tripResult.setText("Finding your driving route…"); executor.execute(()->{try{double[] a=geocode(s), b=geocode(e); String url="https://router.project-osrm.org/route/v1/driving/"+a[1]+","+a[0]+";"+b[1]+","+b[0]+"?overview=false"; JSONObject j=new JSONObject(get(url)); JSONObject r=j.getJSONArray("routes").getJSONObject(0); double routeMiles=r.getDouble("distance")/1609.344; double durationHours=r.getDouble("duration")/3600.0; prefs.edit().putString("miles",String.format(Locale.US,"%.1f",routeMiles)).putFloat("duration",(float)durationHours).apply(); runOnUiThread(()->{miles.setText(String.format(Locale.US,"%.1f",routeMiles));calculateTrip();});}catch(Exception ex){runOnUiThread(()->tripResult.setText("Automatic route lookup failed. You can still enter miles manually.\n\n"+ex.getMessage()));}}); }

    private void calculateTrip(){ saveTripInputs(); double mi=num(prefs.getString("miles","0")), m=num(prefs.getString("mpg","28")), gp=num(prefs.getString("gas","3.25")); if(mi<=0||m<=0||gp<=0){toast("Enter miles, MPG and gas price");return;} double rt=mi*2, gallons=rt/m, gas=gallons*gp, total=gas+num(prefs.getString("snacks","0"))+num(prefs.getString("hotelCost","0"))+num(prefs.getString("extras","0")); double dur= prefs.getFloat("duration",0); String out="ROUND TRIP  "+one(rt)+" mi\n"+"DRIVING TIME  "+(dur>0?one(dur*2)+" hr round trip":"Route first for time")+"\n"+"FUEL NEEDED  "+one(gallons)+" gal\n"+"ROUND-TRIP GAS  "+money(gas)+"\n\n"+"ENTIRE TRIP ESTIMATE\n"+money(total)+"\n\nIncludes gas, hotel, snacks and other trip money."; tripResult.setText(out); }

    private void lookupMpg(){ saveVehicle(); String y=prefs.getString("year",""), mk=prefs.getString("make",""), md=prefs.getString("model",""); if(y.isEmpty()||mk.isEmpty()||md.isEmpty()){toast("Enter year, make and model");return;} executor.execute(()->{try{String q="https://www.fueleconomy.gov/ws/rest/vehicle/menu/options?year="+enc(y)+"&make="+enc(mk)+"&model="+enc(md); Document d=xml(q); NodeList vals=d.getElementsByTagName("value"); if(vals.getLength()==0)throw new Exception("No matching EPA vehicle found"); String id=vals.item(0).getTextContent().trim(); Document vd=xml("https://www.fueleconomy.gov/ws/rest/vehicle/"+id); NodeList mpgNodes=vd.getElementsByTagName("comb08"); if(mpgNodes.getLength()==0)throw new Exception("MPG unavailable"); String found=mpgNodes.item(0).getTextContent().trim(); prefs.edit().putString("mpg",found).apply(); runOnUiThread(()->{mpg.setText(found);toast("Combined MPG filled automatically");});}catch(Exception ex){runOnUiThread(()->toast("MPG lookup failed: "+ex.getMessage()));}}); }

    private void fetchTripAndAreaInfo(){ String dest=prefs.getString("end","").trim(); if(dest.isEmpty()||dest.equalsIgnoreCase("Destination")){toast("Add a destination on the Trip screen");return;} areaResult.setText("Building trip and area overview…"); executor.execute(()->{try{double[] g=geocode(dest); JSONObject w=new JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude="+g[0]+"&longitude="+g[1]+"&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m&temperature_unit=fahrenheit&wind_speed_unit=mph")).getJSONObject("current"); String summary=buildStoredSummary()+"\n\nARRIVAL AREA NOW\n"+one(w.optDouble("temperature_2m"))+"°F, feels like "+one(w.optDouble("apparent_temperature"))+"°F\nWind: "+one(w.optDouble("wind_speed_10m"))+" mph\nWeather code: "+w.optInt("weather_code")+"\n\nAREA LOCATION\nApprox. "+String.format(Locale.US,"%.4f, %.4f",g[0],g[1])+"\n\nTRIP CONTEXT\nUse your hotel and destination as separate anchors. Trailbound keeps the drive, fuel math, lodging, arrival weather and local-area context together so you can judge the trip as a whole."; runOnUiThread(()->areaResult.setText(summary));}catch(Exception ex){runOnUiThread(()->areaResult.setText(buildStoredSummary()+"\n\nLive area update failed: "+ex.getMessage()));}}); }

    private void fetchHotelAreaInfo(){ String query=prefs.getString("hotelAddress","").trim(); if(query.isEmpty()){toast("Enter a hotel address first");return;} areaResult.setText("Looking up the hotel area…"); executor.execute(()->{try{double[] g=geocode(query); JSONObject w=new JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude="+g[0]+"&longitude="+g[1]+"&current=temperature_2m,apparent_temperature,wind_speed_10m&temperature_unit=fahrenheit&wind_speed_unit=mph")).getJSONObject("current"); String s="HOTEL AREA\n"+prefs.getString("hotelName","Hotel")+"\n"+query+"\n\nCURRENT CONDITIONS\n"+one(w.optDouble("temperature_2m"))+"°F, feels like "+one(w.optDouble("apparent_temperature"))+"°F\nWind: "+one(w.optDouble("wind_speed_10m"))+" mph\n\nTRAVELER CONTEXT\nThis hotel is saved as your local base. The Area Info screen combines it with your destination and driving plan so you can compare where you are staying with the wider trip area.";runOnUiThread(()->areaResult.setText(s));}catch(Exception ex){runOnUiThread(()->areaResult.setText("Hotel saved, but live area information could not load.\n\n"+ex.getMessage()));}}); }

    private String buildStoredSummary(){ double mi=num(prefs.getString("miles","0")), dur=prefs.getFloat("duration",0), m=num(prefs.getString("mpg","28")), gp=num(prefs.getString("gas","3.25")); String dest=prefs.getString("end","Destination"), hotel=prefs.getString("hotelName",""), hotelAddr=prefs.getString("hotelAddress",""); StringBuilder s=new StringBuilder(); s.append("TRIP OVERVIEW\n").append(prefs.getString("start","Home")).append(" → ").append(dest); if(mi>0)s.append("\n").append(one(mi)).append(" mi one way • ").append(one(mi*2)).append(" mi round trip"); if(dur>0)s.append("\nApprox. ").append(one(dur)).append(" hr each way"); if(mi>0&&m>0)s.append("\nEstimated fuel: ").append(one(mi*2/m)).append(" gal"); if(mi>0&&m>0&&gp>0)s.append(" • ").append(money(mi*2/m*gp)).append(" gas"); s.append("\n\nAREA CHARACTER\nDestination-area context and current conditions can be refreshed here. The app treats the destination as a region to understand, not only a pin on a map."); s.append("\n\nROAD-TRIP NOTES\nLong drives are easier to plan when you consider drive time, fuel use, service stops, weather at arrival and where your hotel sits relative to the destination."); if(!hotel.isEmpty()||!hotelAddr.isEmpty())s.append("\n\nHOTEL BASE\n").append(hotel.isEmpty()?"Saved hotel":hotel).append(hotelAddr.isEmpty()?"":"\n"+hotelAddr); return s.toString(); }

    private double[] geocode(String q) throws Exception { JSONArray a=new JSONArray(get("https://nominatim.openstreetmap.org/search?format=json&limit=1&q="+enc(q))); if(a.length()==0)throw new Exception("Place not found: "+q); JSONObject o=a.getJSONObject(0); return new double[]{o.getDouble("lat"),o.getDouble("lon")}; }
    private String get(String u) throws Exception { HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection(); c.setConnectTimeout(12000); c.setReadTimeout(15000); c.setRequestProperty("User-Agent","TrailboundAndroid/1.0"); c.setRequestProperty("Accept","application/json,application/xml,text/xml,*/*"); try(BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream()))){StringBuilder s=new StringBuilder();String line;while((line=br.readLine())!=null)s.append(line);return s.toString();}finally{c.disconnect();} }
    private Document xml(String u) throws Exception { HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection(); c.setRequestProperty("User-Agent","TrailboundAndroid/1.0"); c.setRequestProperty("Accept","application/xml"); try{return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(c.getInputStream());}finally{c.disconnect();} }
    private String enc(String s) throws Exception{return URLEncoder.encode(s,"UTF-8");}

    private void saveTripInputs(){ if(start!=null)prefs.edit().putString("start",start.getText().toString()).putString("end",end.getText().toString()).putString("miles",miles.getText().toString()).putString("gas",gasPrice.getText().toString()).putString("snacks",snacks.getText().toString()).putString("hotelCost",hotelCost.getText().toString()).putString("extras",extras.getText().toString()).apply(); }
    private void saveVehicle(){ if(year!=null)prefs.edit().putString("year",year.getText().toString()).putString("make",make.getText().toString()).putString("model",model.getText().toString()).putString("mpg",mpg.getText().toString()).apply(); }
    private void saveHotel(){ if(hotelName!=null)prefs.edit().putString("hotelName",hotelName.getText().toString()).putString("hotelAddress",hotelAddress.getText().toString()).putString("hotelCost",hotelCost.getText().toString()).apply(); }

    private void select(Button b){ if(tripTab==null)return; for(Button x:new Button[]{tripTab,vehicleTab,hotelTab,infoTab})x.setBackground(round(x==b?GREEN:Color.argb(235,48,45,35),14,Color.argb(120,220,210,180),1)); }
    private Button tab(String s){Button b=new Button(this);b.setText(s);b.setTextColor(CREAM);b.setTextSize(11);b.setAllCaps(false);b.setGravity(Gravity.CENTER);return b;}
    private LinearLayout.LayoutParams weight(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(50),1);p.setMargins(dp(3),0,dp(3),0);return p;}
    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(16),dp(16),dp(16));c.setBackground(round(CARD,18,Color.rgb(86,88,67),1));return c;}
    private TextView section(String s){TextView t=text(s,20,true);t.setTextColor(Color.rgb(217,229,194));t.setPadding(0,0,0,dp(8));return t;}
    private EditText input(String hint,String value,boolean number){EditText e=new EditText(this);e.setText(value);e.setHint(hint);e.setTextColor(Color.WHITE);e.setHintTextColor(Color.rgb(156,151,136));e.setTextSize(16);e.setSingleLine(true);e.setPadding(dp(12),0,dp(12),0);e.setBackground(round(Color.rgb(28,30,24),14,Color.rgb(89,92,72),1)); if(number)e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);return e;}
    private void addField(LinearLayout c,String label,EditText e){TextView l=text(label,12,true);l.setTextColor(Color.rgb(211,201,182));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);c.addView(l,lp);LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,dp(52));ep.topMargin=dp(5);c.addView(e,ep);}
    private Button primary(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setBackground(round(GREEN,14,Color.rgb(145,163,106),1));return b;}
    private Button secondary(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setBackground(round(BROWN,14,Color.rgb(139,111,78),1));return b;}
    private LinearLayout.LayoutParams buttonLp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));p.topMargin=dp(12);return p;}
    private TextView infoBox(String s){TextView t=text(s,15,false);t.setTextColor(CREAM);t.setLineSpacing(0,1.16f);t.setPadding(dp(14),dp(14),dp(14),dp(14));t.setBackground(round(Color.rgb(29,31,24),14,Color.rgb(76,80,61),1));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(12);t.setLayoutParams(p);return t;}
    private TextView text(String s,int sp,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextColor(CREAM);t.setTextSize(sp);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private GradientDrawable round(int fill,int radius,int stroke,int sw){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));g.setStroke(dp(sw),stroke);return g;}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private double num(String s){try{return Double.parseDouble(s.trim());}catch(Exception e){return 0;}}
    private String one(double d){return String.format(Locale.US,"%.1f",d);}
    private String money(double d){return NumberFormat.getCurrencyInstance(Locale.US).format(d);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    @Override protected void onDestroy(){executor.shutdownNow();super.onDestroy();}

    public static class HighwayLayout extends FrameLayout {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        public HighwayLayout(Context c){super(c);setWillNotDraw(false);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();p.setColor(Color.rgb(86,96,60));c.drawRect(0,0,w,h*.30f,p);p.setColor(Color.rgb(150,113,69));c.drawRect(0,h*.30f,w,h,p);p.setColor(Color.rgb(66,65,56));Path road=new Path();road.moveTo(w*.43f,h*.20f);road.lineTo(w*.57f,h*.20f);road.lineTo(w*.94f,h);road.lineTo(w*.06f,h);road.close();c.drawPath(road,p);p.setStrokeWidth(Math.max(3,w*.009f));p.setColor(Color.rgb(239,207,130));for(float y=h*.28f;y<h;y+=h*.11f)c.drawLine(w/2,y,w/2,y+h*.055f,p);p.setColor(Color.argb(90,20,22,16));c.drawRect(0,0,w,h,p);}
    }
}
