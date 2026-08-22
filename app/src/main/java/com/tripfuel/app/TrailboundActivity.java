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
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

public class TrailboundActivity extends Activity {
    private final int GREEN=Color.rgb(78,103,58), BROWN=Color.rgb(105,77,47), CREAM=Color.rgb(255,250,238), CARD=Color.argb(246,18,20,16);
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private LinearLayout page;
    private Button tripTab,carTab,hotelTab,areaTab;
    private MapView map;
    private EditText start,end,miles,gas,snacks,extras,year,make,model,mpg,odo,nextOil,payload,hotelName,hotelAddress,hotelCost;
    private CheckBox includeHotel;
    private Spinner fuelType;
    private TextView result,gasSource,vehicleSummary,hotelSummary,areaSummary;
    private ImageView carImage;
    private final String BG_URL="https://upload.wikimedia.org/wikipedia/commons/thumb/e/e4/Route_66_through_the_Mojave_in_California.JPG/1280px-Route_66_through_the_Mojave_in_California.JPG";

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        prefs=getSharedPreferences("trailbound",MODE_PRIVATE);
        Configuration.getInstance().setUserAgentValue("TrailboundAndroid/4.0");
        buildShell();
        showTrip();
    }

    private void buildShell(){
        FrameLayout root=new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(28,29,23));
        ImageView bg=new ImageView(this); bg.setId(1001); bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(bg,new FrameLayout.LayoutParams(-1,-1));
        View shade=new View(this); shade.setBackgroundColor(Color.argb(165,8,10,7)); root.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout shell=new LinearLayout(this); shell.setOrientation(LinearLayout.VERTICAL); shell.setPadding(dp(12),dp(14),dp(12),dp(8));
        root.addView(shell,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge=t("66",18,true); badge.setGravity(Gravity.CENTER); badge.setTextColor(Color.rgb(105,40,28)); badge.setBackground(round(Color.rgb(255,244,214),16,Color.rgb(105,40,28),3));
        top.addView(badge,new LinearLayout.LayoutParams(dp(56),dp(46)));
        LinearLayout titles=new LinearLayout(this); titles.setOrientation(LinearLayout.VERTICAL); titles.setPadding(dp(10),0,0,0);
        titles.addView(t("TRAILBOUND",25,true)); TextView sub=t("Route 66 road-trip planner",12,true); sub.setTextColor(Color.rgb(238,231,214)); titles.addView(sub);
        top.addView(titles,new LinearLayout.LayoutParams(0,-2,1)); shell.addView(top);

        ScrollView sc=new ScrollView(this); sc.setFillViewport(true);
        page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(0,dp(12),0,dp(18)); sc.addView(page);
        shell.addView(sc,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout nav=new LinearLayout(this); nav.setOrientation(LinearLayout.HORIZONTAL); nav.setPadding(0,dp(5),0,0);
        tripTab=navButton("Trip"); carTab=navButton("Car"); hotelTab=navButton("Hotel"); areaTab=navButton("Area");
        nav.addView(tripTab,weight());nav.addView(carTab,weight());nav.addView(hotelTab,weight());nav.addView(areaTab,weight());shell.addView(nav);
        tripTab.setOnClickListener(v->{saveAll();showTrip();});
        carTab.setOnClickListener(v->{saveAll();showCar();});
        hotelTab.setOnClickListener(v->{saveAll();showHotel();});
        areaTab.setOnClickListener(v->{saveAll();showArea();});
        setContentView(root);
        loadRealBackground(bg);
    }

    private void showTrip(){
        select(tripTab); page.removeAllViews();
        LinearLayout c=card(); c.addView(h("Plan the drive"));
        start=field(c,"FROM",prefs.getString("start",""),false); end=field(c,"TO",prefs.getString("end",""),false);
        Button route=primary("Map round trip & calculate"); c.addView(route,blp()); route.setOnClickListener(v->routeTrip());
        map=makeMap(); c.addView(map,new LinearLayout.LayoutParams(-1,dp(280))); restoreRouteMap();

        LinearLayout mini=row();
        miles=fieldBox("ONE-WAY MILES",prefs.getString("miles",""),true); gas=fieldBox("PREPARED GAS",prefs.getString("gas","3.75"),true);
        mini.addView(miles.getParent() instanceof LinearLayout?(LinearLayout)miles.getParent():new LinearLayout(this),new LinearLayout.LayoutParams(0,-2,1));
        mini.addView(gas.getParent() instanceof LinearLayout?(LinearLayout)gas.getParent():new LinearLayout(this),new LinearLayout.LayoutParams(0,-2,1));
        c.addView(mini);
        Button refresh=secondary("Refresh free live gas data"); c.addView(refresh,blp()); refresh.setOnClickListener(v->autoGasPrice());
        gasSource=smallBox(prefs.getString("gasSource","Uses route-area live/public averages when available, then caches the last successful estimate.")); c.addView(gasSource);

        c.addView(h("Trip budget"));
        LinearLayout moneyRow=row();
        snacks=fieldBox("SNACKS",prefs.getString("snacks","40"),true); extras=fieldBox("OTHER",prefs.getString("extras","25"),true);
        moneyRow.addView((LinearLayout)snacks.getParent(),new LinearLayout.LayoutParams(0,-2,1));
        moneyRow.addView((LinearLayout)extras.getParent(),new LinearLayout.LayoutParams(0,-2,1)); c.addView(moneyRow);

        Button calc=primary("Update total"); c.addView(calc,blp()); calc.setOnClickListener(v->calculate());
        result=info("Map a route to calculate fuel, full trip cost, and oil life after the trip."); c.addView(result);
        page.addView(c);

        LinearLayout stops=card(); stops.addView(h("Stops along your route")); stops.addView(note("Search multiple points along the actual mapped route."));
        for(String q:new String[]{"Food","Fuel","Parks","Attractions","Rest areas"}){Button b=secondary(q);stops.addView(b,blp());b.setOnClickListener(v->findRouteStops(q));}
        page.addView(stops);

        LinearLayout save=card(); save.addView(h("Planned vacations")); save.addView(note("Save this entire setup, clear the planner, and add another vacation later."));
        Button sv=primary("Save current vacation"); save.addView(sv,blp()); sv.setOnClickListener(v->{saveAll();saveVacation();showTrip();});
        Button cl=secondary("Clear current planner"); save.addView(cl,blp()); cl.setOnClickListener(v->{clearCurrent();showTrip();});
        renderSavedVacations(save); page.addView(save);

        if(!prefs.getString("miles","").isEmpty()) calculate();
    }

    private EditText fieldBox(String label,String value,boolean number){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(3),dp(4),dp(3),0);
        TextView l=t(label,12,true); l.setTextColor(Color.rgb(244,236,218)); box.addView(l);
        EditText e=input(value,number); LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,dp(52)); ep.topMargin=dp(5); box.addView(e,ep); return e;
    }

    private void showCar(){
        select(carTab); page.removeAllViews(); LinearLayout c=card(); c.addView(h("Your vehicle"));
        carImage=new ImageView(this); carImage.setScaleType(ImageView.ScaleType.CENTER_CROP); carImage.setBackground(round(Color.rgb(28,32,24),16,Color.rgb(112,123,87),1));
        c.addView(carImage,new LinearLayout.LayoutParams(-1,dp(180)));
        vehicleSummary=info(""); c.addView(vehicleSummary);
        year=field(c,"YEAR",prefs.getString("year","2020"),true); make=field(c,"MAKE",prefs.getString("make","Toyota"),false); model=field(c,"MODEL",prefs.getString("model","Camry"),false); mpg=field(c,"EPA COMBINED MPG",prefs.getString("mpg","28"),true);
        TextView fl=t("FUEL TYPE",12,true); fl.setTextColor(Color.rgb(245,237,218)); LinearLayout.LayoutParams flp=new LinearLayout.LayoutParams(-1,-2);flp.topMargin=dp(8);c.addView(fl,flp);
        fuelType=new Spinner(this); String[] fuels={"Regular","Midgrade","Premium","Diesel"}; ArrayAdapter<String> fa=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,fuels); fuelType.setAdapter(fa);
        String savedFuel=prefs.getString("fuelType","Regular"); for(int i=0;i<fuels.length;i++)if(fuels[i].equals(savedFuel))fuelType.setSelection(i);
        LinearLayout.LayoutParams fsp=new LinearLayout.LayoutParams(-1,dp(52));fsp.topMargin=dp(5);c.addView(fuelType,fsp);
        LinearLayout actions=row(); Button auto=primary("Auto MPG"); Button photo=secondary("Load car photo");
        actions.addView(auto,new LinearLayout.LayoutParams(0,dp(52),1)); actions.addView(photo,new LinearLayout.LayoutParams(0,dp(52),1)); c.addView(actions);
        auto.setOnClickListener(v->{saveCar();lookupMpg();}); photo.setOnClickListener(v->{saveCar();loadCarPhoto();});

        c.addView(h("Mileage & maintenance"));
        odo=field(c,"CURRENT ODOMETER",prefs.getString("odo","50000"),true); nextOil=field(c,"NEXT OIL CHANGE DUE AT",prefs.getString("nextOil","55000"),true); payload=field(c,"TRIP PAYLOAD (LB)",prefs.getString("payload","0"),true);
        Button sv=primary("Save vehicle"); c.addView(sv,blp()); sv.setOnClickListener(v->{saveCar();updateCarSummary();loadCarPhoto();toast("Vehicle saved");});
        updateCarSummary(); loadCarPhoto(); page.addView(c);
    }

    private void showHotel(){
        select(hotelTab); page.removeAllViews(); LinearLayout c=card(); c.addView(h("Hotel base"));
        hotelName=field(c,"HOTEL NAME",prefs.getString("hotelName",""),false); hotelAddress=field(c,"HOTEL ADDRESS",prefs.getString("hotelAddress",""),false); hotelCost=field(c,"HOTEL TOTAL",prefs.getString("hotelCost","160"),true);
        includeHotel=new CheckBox(this); includeHotel.setText("Include hotel in trip total"); includeHotel.setTextColor(Color.WHITE); includeHotel.setTextSize(16); includeHotel.setChecked(prefs.getBoolean("includeHotel",true)); includeHotel.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.rgb(145,174,105))); c.addView(includeHotel);
        Button save=primary("Save hotel"); c.addView(save,blp()); save.setOnClickListener(v->{saveHotel();toast("Hotel saved");});
        page.addView(c);

        map=makeMap(); page.addView(map,new LinearLayout.LayoutParams(-1,dp(280)));
        LinearLayout near=card(); near.addView(h("Around your hotel")); hotelSummary=info("Map the hotel, then explore useful places nearby."); near.addView(hotelSummary);
        Button mapHotel=primary("Map hotel & area"); near.addView(mapHotel,blp()); mapHotel.setOnClickListener(v->{saveHotel();mapHotel();});
        for(String q:new String[]{"Restaurants","Coffee","Groceries","Parks","Attractions","Fuel","Pharmacy"}){Button b=secondary(q);near.addView(b,blp());b.setOnClickListener(v->findHotelPlaces(q));}
        page.addView(near);
        restoreHotelMap();
    }

    private void showArea(){
        select(areaTab); page.removeAllViews(); LinearLayout c=card(); c.addView(h("Trip & area intelligence"));
        c.addView(note("Distance, drive time, arrival weather, hotel base, fuel needs, route context, towns, services, and useful places along the trip."));
        areaSummary=info(summary()); c.addView(areaSummary);
        Button refresh=primary("Refresh live area info"); c.addView(refresh,blp()); refresh.setOnClickListener(v->refreshArea());
        page.addView(c);
    }

    private void routeTrip(){
        saveTrip();
        String s=prefs.getString("start","").trim(), e=prefs.getString("end","").trim();
        if(s.isEmpty()||e.isEmpty()){toast("Enter start and destination");return;}
        result.setText("Mapping outbound and return routes…");
        io.execute(()->{
            try{
                double[] a=geocode(s), b=geocode(e);
                JSONObject out=route(a,b), back=route(b,a);
                JSONObject ro=out.getJSONArray("routes").getJSONObject(0), rb=back.getJSONArray("routes").getJSONObject(0);
                double om=ro.getDouble("distance")/1609.344, bm=rb.getDouble("distance")/1609.344, oh=ro.getDouble("duration")/3600.0, bh=rb.getDouble("duration")/3600.0;
                String og=ro.getJSONObject("geometry").getJSONArray("coordinates").toString(), bg=rb.getJSONObject("geometry").getJSONArray("coordinates").toString();
                prefs.edit().putString("miles",String.format(Locale.US,"%.1f",om)).putFloat("returnMiles",(float)bm).putFloat("duration",(float)oh).putFloat("returnDuration",(float)bh)
                        .putString("routeOut",og).putString("routeBack",bg).putString("startLat",""+a[0]).putString("startLon",""+a[1]).putString("endLat",""+b[0]).putString("endLon",""+b[1]).apply();
                runOnUiThread(()->{miles.setText(String.format(Locale.US,"%.1f",om));restoreRouteMap();autoGasPrice();});
            }catch(Exception ex){runOnUiThread(()->result.setText("Route lookup failed: "+ex.getMessage()));}
        });
    }

    private JSONObject route(double[] a,double[] b)throws Exception{
        return new JSONObject(get("https://router.project-osrm.org/route/v1/driving/"+a[1]+","+a[0]+";"+b[1]+","+b[0]+"?overview=full&geometries=geojson"));
    }

    private void restoreRouteMap(){
        if(map==null)return; map.getOverlays().clear();
        try{
            ArrayList<GeoPoint> all=new ArrayList<>(); addPolyline(prefs.getString("routeOut",""),Color.rgb(225,175,90),8f,all); addPolyline(prefs.getString("routeBack",""),Color.rgb(126,187,105),5f,all);
            double sl=n(prefs.getString("startLat","0")),so=n(prefs.getString("startLon","0")),el=n(prefs.getString("endLat","0")),eo=n(prefs.getString("endLon","0"));
            if(sl!=0||so!=0){mark(map,new GeoPoint(sl,so),"Start / Return");all.add(new GeoPoint(sl,so));}
            if(el!=0||eo!=0){mark(map,new GeoPoint(el,eo),"Destination");all.add(new GeoPoint(el,eo));}
            if(all.size()>1)map.zoomToBoundingBox(BoundingBox.fromGeoPoints(all),true,dp(36)); map.invalidate();
        }catch(Exception ignored){}
    }

    private void addPolyline(String json,int color,float width,List<GeoPoint> all)throws Exception{
        if(json==null||json.isEmpty())return; JSONArray a=new JSONArray(json); ArrayList<GeoPoint> pts=new ArrayList<>();
        for(int i=0;i<a.length();i++){JSONArray p=a.getJSONArray(i);GeoPoint g=new GeoPoint(p.getDouble(1),p.getDouble(0));pts.add(g);all.add(g);}
        Polyline line=new Polyline();line.setPoints(pts);line.getOutlinePaint().setColor(color);line.getOutlinePaint().setStrokeWidth(width);map.getOverlays().add(line);
    }

    private void autoGasPrice(){
        saveTrip();
        if(gasSource!=null)gasSource.setText("Collecting free live/public route fuel data…");
        io.execute(()->{
            ArrayList<Double> samples=new ArrayList<>(); ArrayList<String> sources=new ArrayList<>();
            try{
                double nat=parseAaaNational(get("https://gasprices.aaa.com/"));
                if(nat>0){samples.add(nat);sources.add("AAA national");}
            }catch(Exception ignored){}
            try{
                Set<String> states=routeStateCodes();
                for(String code:states){
                    try{
                        double p=parseAaaState(get("https://gasprices.aaa.com/?state="+code),prefs.getString("fuelType","Regular"));
                        if(p>0){samples.add(p);sources.add("AAA "+code);}
                    }catch(Exception ignored){}
                }
            }catch(Exception ignored){}
            double prepared;
            if(!samples.isEmpty()){
                Collections.sort(samples);
                int from=Math.max(0,samples.size()/2);
                double sum=0; for(int i=from;i<samples.size();i++)sum+=samples.get(i);
                double highAvg=sum/(samples.size()-from);
                prepared=highAvg*1.07;
                prefs.edit().putString("lastLiveGas",String.format(Locale.US,"%.2f",prepared)).putLong("lastLiveGasAt",System.currentTimeMillis()).apply();
            }else{
                double cached=n(prefs.getString("lastLiveGas","0"));
                prepared=cached>0?cached:n(prefs.getString("gas","3.75"));
                if(cached>0)sources.add("cached last live estimate"); else sources.add("manual fallback");
            }
            String src=sources.isEmpty()?"manual fallback":android.text.TextUtils.join(", ",sources);
            String msg="Prepared high-side: "+money(prepared)+" / gal • Sources: "+src+". Uses upper-half live route-area values + 7% buffer.";
            prefs.edit().putString("gas",String.format(Locale.US,"%.2f",prepared)).putString("gasSource",msg).apply();
            final double f=prepared; runOnUiThread(()->{if(gas!=null)gas.setText(String.format(Locale.US,"%.2f",f));if(gasSource!=null)gasSource.setText(msg);calculate();});
        });
    }

    private double parseAaaNational(String html){
        Matcher m=Pattern.compile("National Average[^$]{0,300}\\$([0-9]+\\.[0-9]{3,4})",Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(html);
        return m.find()?n(m.group(1)):0;
    }

    private double parseAaaState(String html,String fuel){
        Matcher row=Pattern.compile("Current Avg\\.[\\s\\S]{0,2200}",Pattern.CASE_INSENSITIVE).matcher(html);
        String s=row.find()?row.group():""; Matcher vals=Pattern.compile("\\$([0-9]+\\.[0-9]{3,4})").matcher(s); ArrayList<Double> v=new ArrayList<>();
        while(vals.find()&&v.size()<4)v.add(n(vals.group(1))); if(v.isEmpty())return 0;
        int idx=fuel.equalsIgnoreCase("Midgrade")?1:fuel.equalsIgnoreCase("Premium")?2:fuel.equalsIgnoreCase("Diesel")?3:0;
        return idx<v.size()?v.get(idx):v.get(0);
    }

    private Set<String> routeStateCodes()throws Exception{
        HashSet<String> out=new HashSet<>(); String geo=prefs.getString("routeOut","");
        if(geo.isEmpty())return out; JSONArray a=new JSONArray(geo); int[] fractions={0,1,2,3,4};
        for(int f:fractions){int i=(int)Math.round((a.length()-1)*(f/4.0));JSONArray p=a.getJSONArray(i);String code=reverseStateCode(p.getDouble(1),p.getDouble(0));if(!code.isEmpty())out.add(code);}
        return out;
    }

    private String reverseStateCode(double lat,double lon)throws Exception{
        JSONObject j=new JSONObject(get("https://nominatim.openstreetmap.org/reverse?format=json&zoom=5&addressdetails=1&lat="+lat+"&lon="+lon));
        JSONObject a=j.optJSONObject("address"); if(a==null)return "";
        String iso=a.optString("ISO3166-2-lvl4",""); if(iso.startsWith("US-"))return iso.substring(3);
        return stateCode(a.optString("state",""));
    }

    private String stateCode(String name){
        HashMap<String,String> m=new HashMap<>();
        String[][] pairs={{"Alabama","AL"},{"Alaska","AK"},{"Arizona","AZ"},{"Arkansas","AR"},{"California","CA"},{"Colorado","CO"},{"Connecticut","CT"},{"Delaware","DE"},{"Florida","FL"},{"Georgia","GA"},{"Hawaii","HI"},{"Idaho","ID"},{"Illinois","IL"},{"Indiana","IN"},{"Iowa","IA"},{"Kansas","KS"},{"Kentucky","KY"},{"Louisiana","LA"},{"Maine","ME"},{"Maryland","MD"},{"Massachusetts","MA"},{"Michigan","MI"},{"Minnesota","MN"},{"Mississippi","MS"},{"Missouri","MO"},{"Montana","MT"},{"Nebraska","NE"},{"Nevada","NV"},{"New Hampshire","NH"},{"New Jersey","NJ"},{"New Mexico","NM"},{"New York","NY"},{"North Carolina","NC"},{"North Dakota","ND"},{"Ohio","OH"},{"Oklahoma","OK"},{"Oregon","OR"},{"Pennsylvania","PA"},{"Rhode Island","RI"},{"South Carolina","SC"},{"South Dakota","SD"},{"Tennessee","TN"},{"Texas","TX"},{"Utah","UT"},{"Vermont","VT"},{"Virginia","VA"},{"Washington","WA"},{"West Virginia","WV"},{"Wisconsin","WI"},{"Wyoming","WY"},{"District of Columbia","DC"}};
        for(String[] p:pairs)m.put(p[0],p[1]); return m.containsKey(name)?m.get(name):"";
    }

    private void calculate(){
        saveTrip();
        double out=n(prefs.getString("miles","0")), back=prefs.getFloat("returnMiles",0);if(back<=0)back=out;
        double base=n(prefs.getString("mpg","28")),pay=n(prefs.getString("payload","0")),adj=adjustedMpg(base,pay),gp=n(prefs.getString("gas","3.75"));
        if(out<=0||adj<=0||gp<=0){if(result!=null)result.setText("Map a route or enter valid miles, MPG and gas price.");return;}
        double rt=out+back,gals=rt/adj,fuel=gals*gp,hotel=prefs.getBoolean("includeHotel",true)?n(prefs.getString("hotelCost","0")):0,total=fuel+n(prefs.getString("snacks","0"))+n(prefs.getString("extras","0"))+hotel;
        double current=n(prefs.getString("odo","0")),due=n(prefs.getString("nextOil","0")),afterMiles=current+rt,beforeOil=oilPct(current,due),afterOil=oilPct(afterMiles,due);
        double dh=prefs.getFloat("duration",0)+prefs.getFloat("returnDuration",0);
        result.setText("ROUND TRIP  "+one(rt)+" mi\nDRIVE TIME  "+(dh>0?one(dh)+" hr":"map route for time")+"\nADJUSTED MPG  "+one(adj)+"\nFUEL NEEDED  "+one(gals)+" gal\nPREPARED GAS BUDGET  "+money(fuel)+"\n\nOIL LIFE  "+Math.round(beforeOil)+"% now → "+Math.round(afterOil)+"% after trip\nODOMETER AFTER TRIP  "+Math.round(afterMiles)+" mi\n\nTRIP TOTAL  "+money(total)+"\nHotel "+(prefs.getBoolean("includeHotel",true)?"included":"excluded"));
    }

    private double adjustedMpg(double base,double pounds){if(base<=0)return 0;return base*(1-Math.min(.25,Math.max(0,pounds)/100.0*.01));}
    private double oilPct(double mileage,double due){if(due<=0)return 0;return Math.max(0,Math.min(100,Math.max(0,due-mileage)/5000.0*100.0));}

    private void lookupMpg(){
        saveCar(); String y=prefs.getString("year",""),mk=prefs.getString("make",""),md=prefs.getString("model","");
        io.execute(()->{
            try{
                Document d=xml("https://www.fueleconomy.gov/ws/rest/vehicle/menu/options?year="+enc(y)+"&make="+enc(mk)+"&model="+enc(md));
                NodeList vals=d.getElementsByTagName("value"); if(vals.getLength()==0)throw new Exception("No EPA match");
                Document vd=xml("https://www.fueleconomy.gov/ws/rest/vehicle/"+vals.item(0).getTextContent().trim());
                String found=vd.getElementsByTagName("comb08").item(0).getTextContent().trim(); prefs.edit().putString("mpg",found).apply();
                runOnUiThread(()->{if(mpg!=null)mpg.setText(found);updateCarSummary();toast("EPA MPG loaded");});
            }catch(Exception e){runOnUiThread(()->toast("MPG lookup failed"));}
        });
    }

    private void loadCarPhoto(){
        if(carImage==null)return; saveCar(); String q=(prefs.getString("year","")+" "+prefs.getString("make","")+" "+prefs.getString("model","")+" car").trim();
        io.execute(()->{
            try{
                String api="https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch="+enc(q)+"&gsrlimit=6&prop=pageimages&pithumbsize=1000&format=json&origin=*";
                JSONObject pages=new JSONObject(get(api)).getJSONObject("query").getJSONObject("pages"); String src=null; Iterator<String> keys=pages.keys();
                while(keys.hasNext()){JSONObject p=pages.getJSONObject(keys.next());JSONObject th=p.optJSONObject("thumbnail");if(th!=null){src=th.optString("source","");if(!src.isEmpty())break;}}
                if(src==null||src.isEmpty())throw new Exception();
                Bitmap bm;try(InputStream in=new URL(src).openStream()){bm=BitmapFactory.decodeStream(in);}Bitmap finalBm=bm;runOnUiThread(()->carImage.setImageBitmap(finalBm));
            }catch(Exception e){runOnUiThread(()->carImage.setImageResource(android.R.drawable.ic_menu_gallery));}
        });
    }

    private void updateCarSummary(){
        if(vehicleSummary==null)return;saveCar();
        double base=n(prefs.getString("mpg","28")),pay=n(prefs.getString("payload","0")),adj=adjustedMpg(base,pay),od=n(prefs.getString("odo","0")),due=n(prefs.getString("nextOil","0"));
        double out=n(prefs.getString("miles","0")),back=prefs.getFloat("returnMiles",0);if(back<=0)back=out;double afterMiles=od+out+back;
        vehicleSummary.setText(prefs.getString("year","")+" "+prefs.getString("make","")+" "+prefs.getString("model","")+"\nEPA MPG: "+one(base)+" • Payload estimate: "+one(adj)+" MPG\nOil life now: "+Math.round(oilPct(od,due))+"% • After trip: "+Math.round(oilPct(afterMiles,due))+"%\nOdometer: "+Math.round(od)+" → "+Math.round(afterMiles)+" mi after planned trip");
    }

    private void mapHotel(){
        saveHotel(); String q=prefs.getString("hotelAddress",""); if(q.isEmpty()){toast("Enter hotel address");return;}
        hotelSummary.setText("Mapping hotel…");
        io.execute(()->{try{double[] g=geocode(q);prefs.edit().putString("hotelLat",""+g[0]).putString("hotelLon",""+g[1]).apply();runOnUiThread(()->{restoreHotelMap();hotelSummary.setText(prefs.getString("hotelName","Hotel")+"\n"+q+"\nTap a category to add nearby places to the map.");});}catch(Exception e){runOnUiThread(()->hotelSummary.setText("Hotel map failed"));}});
    }

    private void restoreHotelMap(){
        if(map==null)return; double lat=n(prefs.getString("hotelLat","0")),lon=n(prefs.getString("hotelLon","0")); if(lat==0&&lon==0)return;
        map.getOverlays().clear();GeoPoint p=new GeoPoint(lat,lon);mark(map,p,prefs.getString("hotelName","Hotel"));map.getController().setZoom(14);map.getController().setCenter(p);map.invalidate();
    }

    private void findHotelPlaces(String cat){
        saveHotel();String q=prefs.getString("hotelAddress","");if(q.isEmpty()){toast("Save hotel first");return;}
        io.execute(()->{try{double[] g=geocode(q);List<Place> places=overpass(g[0],g[1],tag(cat),3500,20);runOnUiThread(()->showPlacesOnMap(places,cat,true));}catch(Exception e){runOnUiThread(()->toast("Nearby search failed"));}});
    }

    private void findRouteStops(String cat){
        String geo=prefs.getString("routeOut","");if(geo.isEmpty()){toast("Map route first");return;}
        io.execute(()->{
            try{
                JSONArray a=new JSONArray(geo);ArrayList<Place> all=new ArrayList<>();HashSet<String> seen=new HashSet<>();
                for(int f=0;f<5;f++){int i=(int)Math.round((a.length()-1)*(f/4.0));JSONArray p=a.getJSONArray(i);for(Place pl:overpass(p.getDouble(1),p.getDouble(0),tag(cat),9000,8)){if(seen.add(pl.name))all.add(pl);if(all.size()>=24)break;}if(all.size()>=24)break;}
                runOnUiThread(()->showPlacesOnMap(all,cat,false));
            }catch(Exception e){runOnUiThread(()->toast("Route-stop search failed"));}
        });
    }

    private void showPlacesOnMap(List<Place> places,String cat,boolean hotelMode){
        if(map==null)return;if(hotelMode)restoreHotelMap();else restoreRouteMap();
        for(Place p:places)mark(map,new GeoPoint(p.lat,p.lon),p.name+" • "+cat);
        map.invalidate();toast(places.size()+" "+cat.toLowerCase(Locale.US)+" places mapped");
    }

    private List<Place> overpass(double lat,double lon,String tag,int radius,int limit)throws Exception{
        String q="[out:json][timeout:20];(nwr(around:"+radius+","+lat+","+lon+")"+tag+";);out center tags "+limit+";";
        JSONObject j=new JSONObject(post("https://overpass-api.de/api/interpreter","data="+enc(q)));JSONArray els=j.optJSONArray("elements");ArrayList<Place> out=new ArrayList<>();if(els==null)return out;
        for(int i=0;i<els.length();i++){JSONObject e=els.getJSONObject(i),t=e.optJSONObject("tags");if(t==null)continue;String name=t.optString("name","").trim();if(name.isEmpty())continue;double la=e.has("lat")?e.optDouble("lat"):e.optJSONObject("center")!=null?e.getJSONObject("center").optDouble("lat"):0;double lo=e.has("lon")?e.optDouble("lon"):e.optJSONObject("center")!=null?e.getJSONObject("center").optDouble("lon"):0;if(la!=0||lo!=0)out.add(new Place(name,la,lo));}
        return out;
    }

    private String tag(String c){
        if(c.equals("Food")||c.equals("Restaurants"))return "[amenity=restaurant]";
        if(c.equals("Coffee"))return "[amenity=cafe]";
        if(c.equals("Fuel"))return "[amenity=fuel]";
        if(c.equals("Parks"))return "[leisure=park]";
        if(c.equals("Attractions"))return "[tourism=attraction]";
        if(c.equals("Rest areas"))return "[highway=rest_area]";
        if(c.equals("Groceries"))return "[shop=supermarket]";
        if(c.equals("Pharmacy"))return "[amenity=pharmacy]";
        return "[name]";
    }

    private void refreshArea(){
        String dest=prefs.getString("end","").trim();if(dest.isEmpty()){toast("Add destination first");return;}
        areaSummary.setText("Refreshing live area information…");
        io.execute(()->{try{double[] g=geocode(dest);JSONObject w=new JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude="+g[0]+"&longitude="+g[1]+"&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m&temperature_unit=fahrenheit&wind_speed_unit=mph")).getJSONObject("current");String s=summary()+"\n\nARRIVAL WEATHER NOW\n"+one(w.optDouble("temperature_2m"))+"°F • feels "+one(w.optDouble("apparent_temperature"))+"°F • wind "+one(w.optDouble("wind_speed_10m"))+" mph";runOnUiThread(()->areaSummary.setText(s));}catch(Exception e){runOnUiThread(()->areaSummary.setText(summary()+"\n\nLive weather unavailable."));}});
    }

    private String summary(){
        double out=n(prefs.getString("miles","0")),back=prefs.getFloat("returnMiles",0);if(back<=0)back=out;double dh=prefs.getFloat("duration",0)+prefs.getFloat("returnDuration",0);
        StringBuilder s=new StringBuilder("TRIP\n"+prefs.getString("start","")+" → "+prefs.getString("end","")+" → "+prefs.getString("start",""));
        if(out>0)s.append("\n").append(one(out+back)).append(" mi round trip");if(dh>0)s.append(" • ").append(one(dh)).append(" hr total");
        s.append("\n\nCAR\n").append(prefs.getString("year","")).append(" ").append(prefs.getString("make","")).append(" ").append(prefs.getString("model",""));
        if(!prefs.getString("hotelName","").isEmpty())s.append("\n\nHOTEL\n").append(prefs.getString("hotelName","")).append("\n").append(prefs.getString("hotelAddress",""));
        s.append("\n\nFUEL DATA\n").append(prefs.getString("gasSource","Refresh live gas data on the Trip screen."));
        s.append("\n\nTRAVEL CONTEXT\nUse the route map for actual road geometry and route-stop searches; use Hotel for neighborhood services; use this Area page for weather and whole-trip context.");
        return s.toString();
    }

    private void saveVacation(){
        try{
            JSONArray arr=new JSONArray(prefs.getString("vacations","[]"));JSONObject o=snapshot();o.put("name",(prefs.getString("end","").isEmpty()?"Planned trip":prefs.getString("end",""))+" vacation");arr.put(o);prefs.edit().putString("vacations",arr.toString()).apply();toast("Vacation saved");
        }catch(Exception e){toast("Could not save vacation");}
    }

    private void renderSavedVacations(LinearLayout parent){
        try{
            JSONArray arr=new JSONArray(prefs.getString("vacations","[]")); if(arr.length()==0){parent.addView(note("No saved vacations yet."));return;}
            for(int i=0;i<arr.length();i++){JSONObject o=arr.getJSONObject(i);LinearLayout r=row();Button load=secondary(o.optString("name","Saved trip"));Button del=secondary("Delete");final int idx=i;load.setOnClickListener(v->{loadVacation(idx);showTrip();});del.setOnClickListener(v->{deleteVacation(idx);showTrip();});r.addView(load,new LinearLayout.LayoutParams(0,dp(52),3));r.addView(del,new LinearLayout.LayoutParams(0,dp(52),1));parent.addView(r);}
        }catch(Exception e){parent.addView(note("Saved vacations could not be loaded."));}
    }

    private JSONObject snapshot()throws Exception{
        JSONObject o=new JSONObject();String[] keys={"start","end","miles","gas","gasSource","snacks","extras","year","make","model","mpg","fuelType","odo","nextOil","payload","hotelName","hotelAddress","hotelCost","routeOut","routeBack","startLat","startLon","endLat","endLon","hotelLat","hotelLon"};
        for(String k:keys)o.put(k,prefs.getString(k,""));o.put("returnMiles",prefs.getFloat("returnMiles",0));o.put("duration",prefs.getFloat("duration",0));o.put("returnDuration",prefs.getFloat("returnDuration",0));o.put("includeHotel",prefs.getBoolean("includeHotel",true));return o;
    }

    private void loadVacation(int index){
        try{
            JSONArray arr=new JSONArray(prefs.getString("vacations","[]"));JSONObject o=arr.getJSONObject(index);SharedPreferences.Editor e=prefs.edit();Iterator<String> keys=o.keys();while(keys.hasNext()){String k=keys.next();Object v=o.get(k);if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Number&&(k.equals("returnMiles")||k.equals("duration")||k.equals("returnDuration")))e.putFloat(k,((Number)v).floatValue());else if(!k.equals("name"))e.putString(k,String.valueOf(v));}e.apply();toast("Vacation loaded");
        }catch(Exception e){toast("Could not load vacation");}
    }

    private void deleteVacation(int index){
        try{JSONArray arr=new JSONArray(prefs.getString("vacations","[]")),out=new JSONArray();for(int i=0;i<arr.length();i++)if(i!=index)out.put(arr.get(i));prefs.edit().putString("vacations",out.toString()).apply();}catch(Exception ignored){}
    }

    private void clearCurrent(){
        String[] keys={"start","end","miles","gasSource","snacks","extras","hotelName","hotelAddress","hotelCost","routeOut","routeBack","startLat","startLon","endLat","endLon","hotelLat","hotelLon"};
        SharedPreferences.Editor e=prefs.edit();for(String k:keys)e.remove(k);e.remove("returnMiles").remove("duration").remove("returnDuration").apply();toast("Current planner cleared");
    }

    private void saveTrip(){if(start!=null)prefs.edit().putString("start",start.getText().toString()).putString("end",end.getText().toString()).putString("miles",miles==null?prefs.getString("miles",""):miles.getText().toString()).putString("gas",gas==null?prefs.getString("gas","3.75"):gas.getText().toString()).putString("snacks",snacks==null?prefs.getString("snacks","40"):snacks.getText().toString()).putString("extras",extras==null?prefs.getString("extras","25"):extras.getText().toString()).apply();}
    private void saveCar(){if(year!=null)prefs.edit().putString("year",year.getText().toString()).putString("make",make.getText().toString()).putString("model",model.getText().toString()).putString("mpg",mpg.getText().toString()).putString("fuelType",fuelType==null?prefs.getString("fuelType","Regular"):String.valueOf(fuelType.getSelectedItem())).putString("odo",odo==null?prefs.getString("odo","50000"):odo.getText().toString()).putString("nextOil",nextOil==null?prefs.getString("nextOil","55000"):nextOil.getText().toString()).putString("payload",payload==null?prefs.getString("payload","0"):payload.getText().toString()).apply();}
    private void saveHotel(){if(hotelName!=null)prefs.edit().putString("hotelName",hotelName.getText().toString()).putString("hotelAddress",hotelAddress.getText().toString()).putString("hotelCost",hotelCost.getText().toString()).putBoolean("includeHotel",includeHotel==null?prefs.getBoolean("includeHotel",true):includeHotel.isChecked()).apply();}
    private void saveAll(){saveTrip();saveCar();saveHotel();}

    private void loadRealBackground(ImageView bg){io.execute(()->{try(InputStream in=new URL(BG_URL).openStream()){Bitmap bm=BitmapFactory.decodeStream(in);runOnUiThread(()->bg.setImageBitmap(bm));}catch(Exception ignored){}});}
    private MapView makeMap(){MapView m=new MapView(this);m.setTileSource(TileSourceFactory.MAPNIK);m.setMultiTouchControls(true);m.getController().setZoom(5.0);return m;}
    private void mark(MapView m,GeoPoint p,String title){Marker x=new Marker(m);x.setPosition(p);x.setTitle(title);m.getOverlays().add(x);}
    private double[] geocode(String q)throws Exception{JSONArray a=new JSONArray(get("https://nominatim.openstreetmap.org/search?format=json&limit=1&q="+enc(q)));if(a.length()==0)throw new Exception("Place not found");JSONObject o=a.getJSONObject(0);return new double[]{o.getDouble("lat"),o.getDouble("lon")};}
    private String get(String u)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(18000);c.setRequestProperty("User-Agent","TrailboundAndroid/4.0");c.setRequestProperty("Accept","application/json,text/html,application/xml,*/*");try(BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream()))){StringBuilder s=new StringBuilder();String line;while((line=br.readLine())!=null)s.append(line);return s.toString();}finally{c.disconnect();}}
    private String post(String u,String body)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(20000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/x-www-form-urlencoded");c.setRequestProperty("User-Agent","TrailboundAndroid/4.0");c.getOutputStream().write(body.getBytes("UTF-8"));try(BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream()))){StringBuilder s=new StringBuilder();String line;while((line=br.readLine())!=null)s.append(line);return s.toString();}finally{c.disconnect();}}
    private Document xml(String u)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setRequestProperty("User-Agent","TrailboundAndroid/4.0");try{return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(c.getInputStream());}finally{c.disconnect();}}
    private String enc(String s)throws Exception{return URLEncoder.encode(s,"UTF-8");}

    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(16),dp(16),dp(16));c.setBackground(round(CARD,20,Color.rgb(91,96,73),1));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);c.setLayoutParams(p);return c;}
    private TextView h(String s){TextView x=t(s,20,true);x.setTextColor(Color.rgb(239,255,209));x.setPadding(0,0,0,dp(8));return x;}
    private TextView note(String s){TextView x=t(s,13,false);x.setTextColor(Color.rgb(235,228,211));x.setPadding(0,0,0,dp(8));return x;}
    private EditText field(LinearLayout p,String label,String value,boolean number){TextView l=t(label,12,true);l.setTextColor(Color.rgb(245,237,218));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);p.addView(l,lp);EditText e=input(value,number);LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,dp(52));ep.topMargin=dp(5);p.addView(e,ep);return e;}
    private EditText input(String value,boolean number){EditText e=new EditText(this);e.setText(value);e.setTextColor(Color.WHITE);e.setHintTextColor(Color.rgb(196,192,178));e.setTextSize(16);e.setSingleLine(true);e.setPadding(dp(12),0,dp(12),0);e.setBackground(round(Color.rgb(10,12,9),14,Color.rgb(119,126,94),1));if(number)e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);return e;}
    private TextView info(String s){TextView x=t(s,15,false);x.setTextColor(Color.WHITE);x.setLineSpacing(0,1.15f);x.setPadding(dp(14),dp(14),dp(14),dp(14));x.setBackground(round(Color.rgb(8,10,7),14,Color.rgb(100,107,79),1));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(10);x.setLayoutParams(p);return x;}
    private TextView smallBox(String s){TextView x=info(s);x.setTextSize(13);return x;}
    private Button primary(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(round(Color.rgb(91,118,64),14,Color.rgb(190,215,145),1));return b;}
    private Button secondary(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(round(BROWN,14,Color.rgb(193,157,110),1));return b;}
    private Button navButton(String s){Button b=secondary(s);b.setTextSize(13);return b;}
    private void select(Button b){for(Button x:new Button[]{tripTab,carTab,hotelTab,areaTab})x.setBackground(round(x==b?GREEN:Color.rgb(34,37,29),14,x==b?Color.rgb(181,201,143):Color.rgb(79,84,65),1));}
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setPadding(0,dp(6),0,0);return r;}
    private LinearLayout.LayoutParams weight(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(54),1);p.setMargins(dp(3),0,dp(3),0);return p;}
    private LinearLayout.LayoutParams blp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));p.topMargin=dp(10);return p;}
    private TextView t(String s,int sp,boolean bold){TextView x=new TextView(this);x.setText(s);x.setTextColor(CREAM);x.setTextSize(sp);if(bold)x.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return x;}
    private GradientDrawable round(int fill,int radius,int stroke,int sw){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));g.setStroke(dp(sw),stroke);return g;}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private double n(String s){try{return Double.parseDouble(s.trim());}catch(Exception e){return 0;}}
    private String one(double d){return String.format(Locale.US,"%.1f",d);}
    private String money(double d){return NumberFormat.getCurrencyInstance(Locale.US).format(d);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}

    @Override protected void onPause(){saveAll();if(map!=null)map.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(map!=null)map.onResume();}
    @Override protected void onDestroy(){saveAll();io.shutdownNow();super.onDestroy();}

    private static class Place{final String name;final double lat,lon;Place(String n,double a,double o){name=n;lat=a;lon=o;}}
}
