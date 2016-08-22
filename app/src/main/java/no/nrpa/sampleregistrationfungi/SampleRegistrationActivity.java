/*
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
// Copyright (c) 2015 Norwegian Radiation Protection Authority
// Contributors: Dag Robøle (dag D0T robole AT gmail D0T com)

package no.nrpa.sampleregistrationfungi;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Environment;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import no.nrpa.sampleregistrationfungi.R;

public class SampleRegistrationActivity extends AppCompatActivity implements LocationListener {

    private String newLine = System.getProperty("line.separator");

    private ViewSwitcher switcher;
    private Button btnBack, btnNextId, btnEditSample;
    private ListView lstProj;
    private ArrayList<String> items;
    private ListAdapter adapter;
    private LocationManager locManager;
    private String locProvider;
    private boolean providerEnabled;
    private TextView tvProjName, tvCurrProvider, tvCurrFix, tvCurrAcc, tvCurrGPSDate, tvCurrLat, tvCurrLon,
            tvCurrentAboveSeaLevel, tvDataID, tvNextID, tvSampleTitle, tvEditing;
    private EditText etNextGrass, etNextHerbs, etNextHeather, etNextReceiver, etNextComment, etNextLocation;
    private AutoCompleteTextView etNextSampleType, etNextCommunity;
    private Spinner etNextLocationType, etNextDensity;
    private MultiSpinner etNextAdjacentHardwoods;
    private ScrollView svSamples;
    private File projDir, cfgDir;
    private int nextId;
    private String collector, collectorAddress, dataId;
    private long syncFrequency;
    private float syncDistance;
    private int nSatellites;
    private float accuracy;

    private boolean modCoords = false;
    private int editIndex = -1;
    private List<String> editSampleArray = new ArrayList<String>();
    ArrayList<String> locationTypes = new ArrayList<String>();
    ArrayList<String> adjacentHardwoods = new ArrayList<String>();
    ArrayList<String> densityList = new ArrayList<String>();

    private Spanned ErrorString(String s) {
        return Html.fromHtml("<font color='#ff8888' ><b>" + s + "</b></font>");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sample_registration);

        try {
            //getWindow().getDecorView().setBackgroundColor(Color.parseColor("#fdf6e3"));

            DisplayMetrics displaymetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
            int winWidth = displaymetrics.widthPixels;
            int winHeight = displaymetrics.heightPixels;

            svSamples = (ScrollView)findViewById(R.id.svSamples);

            int cliWidth = winWidth - 32;
            btnBack = (Button)findViewById(R.id.btnBack);
            btnBack.setWidth(cliWidth / 2);
            btnBack.setOnClickListener(btnBack_onClick);

            btnNextId = (Button)findViewById(R.id.btnNextId);
            btnNextId.setWidth(cliWidth / 2);
            btnNextId.setOnClickListener(btnNextID_onClick);

            tvProjName = (TextView) findViewById(R.id.tvProjName);
            tvProjName.setWidth((cliWidth / 3) * 2);

            btnEditSample = (Button)findViewById(R.id.btnEditSample);
            btnEditSample.setWidth(cliWidth / 3);
            btnEditSample.setOnClickListener(btnEditSample_onClick);

            tvSampleTitle = (TextView)findViewById(R.id.tvSampleTitle);
            tvSampleTitle.setWidth((cliWidth / 3) * 2);

            tvEditing = (TextView)findViewById(R.id.tvEditing);
            tvEditing.setText("");

            ActionBar actionBar = getSupportActionBar();
            if(actionBar != null) {
                actionBar.setDisplayShowHomeEnabled(true);
                actionBar.setLogo(R.mipmap.ic_launcher);
                actionBar.setDisplayUseLogoEnabled(true);
            }

            if(!isExternalStorageWritable())
            {
                Toast.makeText(this, "Tilgang til ekstern lagring avvist", Toast.LENGTH_SHORT).show();
                exitApp();
            }

            projDir = getProjectDir();
            cfgDir = getConfigDir();

            switcher = (ViewSwitcher) findViewById(R.id.viewSwitcher);
            switcher.setInAnimation(AnimationUtils.loadAnimation(SampleRegistrationActivity.this, android.R.anim.slide_in_left));
            switcher.setOutAnimation(AnimationUtils.loadAnimation(SampleRegistrationActivity.this, android.R.anim.slide_out_right));

            EditText etNewProj = (EditText) findViewById(R.id.etNewProj);
            etNewProj.setOnKeyListener(etNewProj_onKey);

            items = new ArrayList<String>();
            adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items);

            lstProj = (ListView) findViewById(R.id.listView);
            lstProj.setOnItemClickListener(lstProj_onItemClick);

            populateProjects();

            tvCurrProvider = (TextView) findViewById(R.id.tvCurrentProvider);
            tvCurrFix = (TextView) findViewById(R.id.tvCurrentFix);
            tvCurrAcc = (TextView) findViewById(R.id.tvCurrentAcc);
            tvCurrGPSDate = (TextView) findViewById(R.id.tvLastDate);
            tvCurrLat = (TextView) findViewById(R.id.tvCurrentLatitude);
            tvCurrLon = (TextView) findViewById(R.id.tvCurrentLongitude);
            tvCurrentAboveSeaLevel = (TextView) findViewById(R.id.tvCurrentAboveSeaLevel);
            tvDataID = (TextView)findViewById(R.id.tvDataId);
            tvNextID = (TextView)findViewById(R.id.tvNextId);
            etNextSampleType = (AutoCompleteTextView)findViewById(R.id.etNextSampleType);
            etNextLocation = (EditText)findViewById(R.id.etNextLocation);
            etNextLocationType = (Spinner)findViewById(R.id.etNextLocationType);
            etNextCommunity = (AutoCompleteTextView)findViewById(R.id.etNextCommunity);
            etNextAdjacentHardwoods = (MultiSpinner)findViewById(R.id.etNextAdjacentHardwoods);
            etNextGrass = (EditText)findViewById(R.id.etNextGrass);
            etNextHerbs = (EditText)findViewById(R.id.etNextHerbs);
            etNextHeather = (EditText)findViewById(R.id.etNextHeather);
            etNextDensity = (Spinner)findViewById(R.id.etNextDensity);
            etNextReceiver = (EditText)findViewById(R.id.etNextReceiver);
            etNextComment = (EditText)findViewById(R.id.etNextComment);

            ArrayList<String> sampleTypes = new ArrayList<String>();
            File file = new File (cfgDir, "sample_types.txt");
            if(!file.exists()) {
                file.createNewFile();
                BufferedWriter writer = new BufferedWriter(new FileWriter(file));
                writer.write("Bjørnebær" + newLine);
                writer.write("Blek piggsopp" + newLine);
                writer.write("Blokkebær" + newLine);
                writer.write("Blåbær" + newLine);
                writer.write("Blå ridderhatt" + newLine);
                writer.write("Boysenbær" + newLine);
                writer.write("Branngul riske" + newLine);
                writer.write("Bringebær" + newLine);
                writer.write("Brun fluesopp" + newLine);
                writer.write("Brun kamfluesopp" + newLine);
                writer.write("Brunkjøttbukkesopp" + newLine);
                writer.write("Brun ringløs fluesopp" + newLine);
                writer.write("Brunskrubb" + newLine);
                writer.write("Fjellskrubb" + newLine);
                writer.write("Fløyelsrørsopp" + newLine);
                writer.write("Franskbrødsopp" + newLine);
                writer.write("Furumatriske" + newLine);
                writer.write("Fåresopp" + newLine);
                writer.write("Granmatriske" + newLine);
                writer.write("Grå kamfluesopp" + newLine);
                writer.write("Grå ringløs fluesopp" + newLine);
                writer.write("Gråfiolett riske" + newLine);
                writer.write("Grønnkremle" + newLine);
                writer.write("Gulrød kremle" + newLine);
                writer.write("Hulriske" + newLine);
                writer.write("Hvit fluesopp" + newLine);
                writer.write("Jordbær" + newLine);
                writer.write("Kantarell" + newLine);
                writer.write("Krekling" + newLine);
                writer.write("Lerkesopp" + newLine);
                writer.write("Mild gulkremle" + newLine);
                writer.write("Multe" + newLine);
                writer.write("Myrskrubb" + newLine);
                writer.write("Mørkebrun slørsopp" + newLine);
                writer.write("Ospeskrubb" + newLine);
                writer.write("Pepperriske" + newLine);
                writer.write("Pepperrørsopp" + newLine);
                writer.write("Pluggsopp" + newLine);
                writer.write("Physalis" + newLine);
                writer.write("Puddertraktsopp" + newLine);
                writer.write("Reddikmusserong" + newLine);
                writer.write("Rimsopp" + newLine);
                writer.write("Rips" + newLine);
                writer.write("Rosa sleipsopp" + newLine);
                writer.write("Rød fluesopp" + newLine);
                writer.write("Rødbelteslørsopp" + newLine);
                writer.write("Rødbrun flathatt" + newLine);
                writer.write("Rødbrun pepperriske" + newLine);
                writer.write("Rødgul piggsopp" + newLine);
                writer.write("Rødnende fluesopp" + newLine);
                writer.write("Rødskrubb" + newLine);
                writer.write("Sandsopp" + newLine);
                writer.write("Seig kusopp" + newLine);
                writer.write("Skarp gulkremle" + newLine);
                writer.write("Skjeggriske" + newLine);
                writer.write("Sleipslørsopp" + newLine);
                writer.write("Smørsopp" + newLine);
                writer.write("Solbær" + newLine);
                writer.write("Steinsopp" + newLine);
                writer.write("Storkremle" + newLine);
                writer.write("Stor kragesopp" + newLine);
                writer.write("Stubbeskjellsopp" + newLine);
                writer.write("Svartbrun rørsopp" + newLine);
                writer.write("Svartkremle" + newLine);
                writer.write("Svartskrubb" + newLine);
                writer.write("Svovelriske" + newLine);
                writer.write("Traktkantarell" + newLine);
                writer.write("Tranebær" + newLine);
                writer.write("Tyttebær" + newLine);
                writer.write("Vinrød kremle" + newLine);
                writer.write("Vårfagerhatt" + newLine);
                writer.write("Annet" + newLine);
                writer.close();
            }

            String line;
            BufferedReader buf = new BufferedReader(new FileReader(file));
            while ((line = buf.readLine()) != null) {
                String l = line.trim();
                if(l.isEmpty())
                    continue;
                sampleTypes.add(l);
            }
            buf.close();

            ArrayAdapter<String> adapterSampleTypes = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, sampleTypes);
            etNextSampleType.setAdapter(adapterSampleTypes);

            file = new File (cfgDir, "location_types.txt");
            if(!file.exists()) {
                file.createNewFile();
                BufferedWriter writer = new BufferedWriter(new FileWriter(file));
                writer.write("Skogsbeite" + newLine);
                writer.write("Fjellbeite" + newLine);
                writer.write("Kysthei" + newLine);
                writer.write("Myr" + newLine);
                writer.write("Barskog" + newLine);
                writer.write("Løvskog" + newLine);
                writer.write("Fjellskog" + newLine);
                writer.write("Snaufjellet" + newLine);
                writer.write("Annet" + newLine);
                writer.close();
            }

            locationTypes.clear();
            locationTypes.add("");
            buf = new BufferedReader(new FileReader(file));
            while ((line = buf.readLine()) != null) {
                String l = line.trim();
                if(l.isEmpty())
                    continue;
                locationTypes.add(l);
            }
            buf.close();

            ArrayAdapter<String> adapterLocationTypes = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, locationTypes);
            adapterLocationTypes.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            etNextLocationType.setAdapter(adapterLocationTypes);

            file = new File (cfgDir, "adjacent_hardwoods.txt");
            if(!file.exists()) {
                file.createNewFile();
                BufferedWriter writer = new BufferedWriter(new FileWriter(file));
                writer.write("Gran" + newLine);
                writer.write("Furu" + newLine);
                writer.write("Bjørk" + newLine);
                writer.write("Selje" + newLine);
                writer.write("Osp" + newLine);
                writer.write("Rogn" + newLine);
                writer.write("Or" + newLine);
                writer.write("Vier" + newLine);
                writer.write("Einer" + newLine);
                writer.write("Annet" + newLine);
                writer.close();
            }

            adjacentHardwoods.clear();
            buf = new BufferedReader(new FileReader(file));
            while ((line = buf.readLine()) != null) {
                String l = line.trim();
                if(l.isEmpty())
                    continue;
                adjacentHardwoods.add(l);
            }
            buf.close();

            etNextAdjacentHardwoods.setItems(adjacentHardwoods, "", new MultiSpinner.MultiSpinnerListener() {
                @Override
                public void onItemsSelected(boolean[] selected) {
                }
            });

            densityList.clear();
            densityList.add("");
            densityList.add("Lite");
            densityList.add("Middels");
            densityList.add("Mye");

            ArrayAdapter<String> adapterDensity = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, densityList);
            adapterDensity.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            etNextDensity.setAdapter(adapterDensity);

            ArrayList<String> communities = new ArrayList<String>();
            file = new File (cfgDir, "communities.txt");
            if(!file.exists()) {
                file.createNewFile();
                BufferedWriter writer = new BufferedWriter(new FileWriter(file));

                writer.write("Rømskog, Østfold" + newLine);
                writer.write("Marker, Østfold" + newLine);
                writer.write("Aremark, Østfold" + newLine);
                writer.write("Halden, Østfold" + newLine);
                writer.write("Trøgstad, Østfold" + newLine);
                writer.write("Spydeberg, Østfold" + newLine);
                writer.write("Hobøl, Østfold" + newLine);
                writer.write("Askim, Østfold" + newLine);
                writer.write("Skiptvet, Østfold" + newLine);
                writer.write("Eidsberg, Østfold" + newLine);
                writer.write("Rakkestad, Østfold" + newLine);
                writer.write("Våler, Østfold" + newLine);
                writer.write("Moss, Østfold" + newLine);
                writer.write("Rygge, Østfold" + newLine);
                writer.write("Råde, Østfold" + newLine);
                writer.write("Sarpsborg, Østfold" + newLine);
                writer.write("Fredrikstad, Østfold" + newLine);
                writer.write("Hvaler, Østfold" + newLine);
                writer.write("Hurdal, Akershus" + newLine);
                writer.write("Eidsvoll, Akershus" + newLine);
                writer.write("Nes, Akershus" + newLine);
                writer.write("Aurskog-Høland, Akershus" + newLine);
                writer.write("Ullensaker, Akershus" + newLine);
                writer.write("Nannestad, Akershus" + newLine);
                writer.write("Gjerdrum, Akershus" + newLine);
                writer.write("Nittedal, Akershus" + newLine);
                writer.write("Sørum, Akershus" + newLine);
                writer.write("Skedsmo, Akershus" + newLine);
                writer.write("Lørenskog, Akershus" + newLine);
                writer.write("Fet, Akershus" + newLine);
                writer.write("Rælingen, Akershus" + newLine);
                writer.write("Enebakk, Akershus" + newLine);
                writer.write("Ski, Akershus" + newLine);
                writer.write("Oppegård, Akershus" + newLine);
                writer.write("Nesodden, Akershus" + newLine);
                writer.write("Bærum, Akershus" + newLine);
                writer.write("Asker, Akershus" + newLine);
                writer.write("Frogn, Akershus" + newLine);
                writer.write("Ås, Akershus" + newLine);
                writer.write("Vestby, Akershus" + newLine);
                writer.write("Oslo, Oslo" + newLine);
                writer.write("Svelvik, Vestfold" + newLine);
                writer.write("Sande, Vestfold" + newLine);
                writer.write("Hof, Vestfold" + newLine);
                writer.write("Holmestrand, Vestfold" + newLine);
                writer.write("Lardal, Vestfold" + newLine);
                writer.write("Larvik, Vestfold" + newLine);
                writer.write("Andebu, Vestfold" + newLine);
                writer.write("Ramnes, Vestfold" + newLine);
                writer.write("Våle, Vestfold" + newLine);
                writer.write("Borre, Vestfold" + newLine);
                writer.write("Tønsberg, Vestfold" + newLine);
                writer.write("Stokke, Vestfold" + newLine);
                writer.write("Sandefjord, Vestfold" + newLine);
                writer.write("Nøtterøy, Vestfold" + newLine);
                writer.write("Tjøme, Vestfold" + newLine);
                writer.write("Vinje, Telemark" + newLine);
                writer.write("Tinn, Telemark" + newLine);
                writer.write("Notodden, Telemark" + newLine);
                writer.write("Hjartdal, Telemark" + newLine);
                writer.write("Seljord, Telemark" + newLine);
                writer.write("Kviteseid, Telemark" + newLine);
                writer.write("Tokke, Telemark" + newLine);
                writer.write("Fyresdal, Telemark" + newLine);
                writer.write("Nissedal, Telemark" + newLine);
                writer.write("Drangedal, Telemark" + newLine);
                writer.write("Bø, Telemark" + newLine);
                writer.write("Nome, Telemark" + newLine);
                writer.write("Sauherad, Telemark" + newLine);
                writer.write("Skien, Telemark" + newLine);
                writer.write("Siljan, Telemark" + newLine);
                writer.write("Porsgrunn, Telemark" + newLine);
                writer.write("Bamble, Telemark" + newLine);
                writer.write("Kragerø, Telemark" + newLine);
                writer.write("Bykle, Aust-Agder" + newLine);
                writer.write("Valle, Aust-Agder" + newLine);
                writer.write("Bygland, Aust-Agder" + newLine);
                writer.write("Evje og Hornnes, Aust-Agder" + newLine);
                writer.write("Åmli, Aust-Agder" + newLine);
                writer.write("Froland, Aust-Agder" + newLine);
                writer.write("Birkenes, Aust-Agder" + newLine);
                writer.write("Iveland, Aust-Agder" + newLine);
                writer.write("Lillesand, Aust-Agder" + newLine);
                writer.write("Grimstad, Aust-Agder" + newLine);
                writer.write("Arendal, Aust-Agder" + newLine);
                writer.write("Tvedestrand, Aust-Agder" + newLine);
                writer.write("Vegårshei, Aust-Agder" + newLine);
                writer.write("Gjerstad, Aust-Agder" + newLine);
                writer.write("Risør, Aust-Agder" + newLine);
                writer.write("Sirdal, Vest-Agder" + newLine);
                writer.write("Flekkefjord, Vest-Agder" + newLine);
                writer.write("Kvinesdal, Vest-Agder" + newLine);
                writer.write("Åseral, Vest-Agder" + newLine);
                writer.write("Hægebostad, Vest-Agder" + newLine);
                writer.write("Farsund, Vest-Agder" + newLine);
                writer.write("Lyngdal, Vest-Agder" + newLine);
                writer.write("Lindesnes, Vest-Agder" + newLine);
                writer.write("Audnedal, Vest-Agder" + newLine);
                writer.write("Mandal, Vest-Agder" + newLine);
                writer.write("Marnardal, Vest-Agder" + newLine);
                writer.write("Søgne, Vest-Agder" + newLine);
                writer.write("Songdalen, Vest-Agder" + newLine);
                writer.write("Vennesla, Vest-Agder" + newLine);
                writer.write("Kristiansand, Vest-Agder" + newLine);
                writer.write("Lund, Rogaland" + newLine);
                writer.write("Sokndal, Rogaland" + newLine);
                writer.write("Eigersund, Rogaland" + newLine);
                writer.write("Hå, Rogaland" + newLine);
                writer.write("Bjerkreim, Rogaland" + newLine);
                writer.write("Gjesdal, Rogaland" + newLine);
                writer.write("Time, Rogaland" + newLine);
                writer.write("Klepp, Rogaland" + newLine);
                writer.write("Sola, Rogaland" + newLine);
                writer.write("Sandnes, Rogaland" + newLine);
                writer.write("Stavanger, Rogaland" + newLine);
                writer.write("Randaberg, Rogaland" + newLine);
                writer.write("Kvitsøy, Rogaland" + newLine);
                writer.write("Rennesøy, Rogaland" + newLine);
                writer.write("Strand, Rogaland" + newLine);
                writer.write("Forsand, Rogaland" + newLine);
                writer.write("Hjelmeland, Rogaland" + newLine);
                writer.write("Finnøy, Rogaland" + newLine);
                writer.write("Suldal, Rogaland" + newLine);
                writer.write("Sauda, Rogaland" + newLine);
                writer.write("Vindafjord, Rogaland" + newLine);
                writer.write("Tysvær, Rogaland" + newLine);
                writer.write("Haugesund, Rogaland" + newLine);
                writer.write("Karmøy, Rogaland" + newLine);
                writer.write("Bokn, Rogaland" + newLine);
                writer.write("Utsira, Rogaland" + newLine);
                writer.write("Ulvik, Hordaland" + newLine);
                writer.write("Eidfjord, Hordaland" + newLine);
                writer.write("Ullensvang, Hordaland" + newLine);
                writer.write("Odda, Hordaland" + newLine);
                writer.write("Jondal, Hordaland" + newLine);
                writer.write("Kvinnherad, Hordaland" + newLine);
                writer.write("Etne, Hordaland" + newLine);
                writer.write("Sveio, Hordaland" + newLine);
                writer.write("Bømlo, Hordaland" + newLine);
                writer.write("Stord, Hordaland" + newLine);
                writer.write("Fitjar, Hordaland" + newLine);
                writer.write("Tysnes, Hordaland" + newLine);
                writer.write("Fusa, Hordaland" + newLine);
                writer.write("Kvam, Hordaland" + newLine);
                writer.write("Granvin, Hordaland" + newLine);
                writer.write("Voss, Hordaland" + newLine);
                writer.write("Vaksdal, Hordaland" + newLine);
                writer.write("Semnanger, Hordaland" + newLine);
                writer.write("Os, Hordaland" + newLine);
                writer.write("Austevoll, Hordaland" + newLine);
                writer.write("Bergen, Hordaland" + newLine);
                writer.write("Osterøy, Hordaland" + newLine);
                writer.write("Modalen, Hordaland" + newLine);
                writer.write("Masfjorden, Hordaland" + newLine);
                writer.write("Lindås, Hordaland" + newLine);
                writer.write("Sund, Hordaland" + newLine);
                writer.write("Fjell, Hordaland" + newLine);
                writer.write("Askøy, Hordaland" + newLine);
                writer.write("Øygarden, Hordaland" + newLine);
                writer.write("Meland, Hordaland" + newLine);
                writer.write("Radøy, Hordaland" + newLine);
                writer.write("Austrheim, Hordaland" + newLine);
                writer.write("Fedje, Hordaland" + newLine);
                writer.write("Hemsedal, Buskerud" + newLine);
                writer.write("Gol, Buskerud" + newLine);
                writer.write("Ål, Buskerud" + newLine);
                writer.write("Hol, Buskerud" + newLine);
                writer.write("Nes, Buskerud" + newLine);
                writer.write("Nore og Uvdal, Buskerud" + newLine);
                writer.write("Flå, Buskerud" + newLine);
                writer.write("Rollag, Buskerud" + newLine);
                writer.write("Sigdal, Buskerud" + newLine);
                writer.write("Krødsherad, Buskerud" + newLine);
                writer.write("Ringerike, Buskerud" + newLine);
                writer.write("Hole, Buskerud" + newLine);
                writer.write("Modum, Buskerud" + newLine);
                writer.write("Flesberg, Buskerud" + newLine);
                writer.write("Kongsberg, Buskerud" + newLine);
                writer.write("Øvre Eiker, Buskerud" + newLine);
                writer.write("Nedre Eiker, Buskerud" + newLine);
                writer.write("Lier, Buskerud" + newLine);
                writer.write("Drammen, Buskerud" + newLine);
                writer.write("Røyken, Buskerud" + newLine);
                writer.write("Hurum, Buskerud" + newLine);
                writer.write("Lærdal, Sogn og Fjordane" + newLine);
                writer.write("Årdal, Sogn og Fjordane" + newLine);
                writer.write("Luster, Sogn og Fjordane" + newLine);
                writer.write("Aurland, Sogn og Fjordane" + newLine);
                writer.write("Vik, Sogn og Fjordane" + newLine);
                writer.write("Høyanger, Sogn og Fjordane" + newLine);
                writer.write("Gulen, Sogn og Fjordane" + newLine);
                writer.write("Hyllestad, Sogn og Fjordane" + newLine);
                writer.write("Solund, Sogn og Fjordane" + newLine);
                writer.write("Sogndal, Sogn og Fjordane" + newLine);
                writer.write("Leikanger, Sogn og Fjordane" + newLine);
                writer.write("Balestrand, Sogn og Fjordane" + newLine);
                writer.write("Stryn, Sogn og Fjordane" + newLine);
                writer.write("Hornindal, Sogn og Fjordane" + newLine);
                writer.write("Eid, Sogn og Fjordane" + newLine);
                writer.write("Gaular, Sogn og Fjordane" + newLine);
                writer.write("Fjaler, Sogn og Fjordane" + newLine);
                writer.write("Askvoll, Sogn og Fjordane" + newLine);
                writer.write("Førde, Sogn og Fjordane" + newLine);
                writer.write("Jølster, Sogn og Fjordane" + newLine);
                writer.write("Gloppen, Sogn og Fjordane" + newLine);
                writer.write("Naustdal, Sogn og Fjordane" + newLine);
                writer.write("Flora, Sogn og Fjordane" + newLine);
                writer.write("Bremanger, Sogn og Fjordane" + newLine);
                writer.write("Vågsøy, Sogn og Fjordane" + newLine);
                writer.write("Selje, Sogn og Fjordane" + newLine);
                writer.write("Skjåk, Oppland" + newLine);
                writer.write("Lesja, Oppland" + newLine);
                writer.write("Dovre, Oppland" + newLine);
                writer.write("Lom, Oppland" + newLine);
                writer.write("Vågå, Oppland" + newLine);
                writer.write("Sel, Oppland" + newLine);
                writer.write("Vang, Oppland" + newLine);
                writer.write("Vestre Slidre, Oppland" + newLine);
                writer.write("Øystre Slidre, Oppland" + newLine);
                writer.write("Nord-Aurdal, Oppland" + newLine);
                writer.write("Etnedal, Oppland" + newLine);
                writer.write("Nord-Fron, Oppland" + newLine);
                writer.write("Sør-Fron, Oppland" + newLine);
                writer.write("Gausdal, Oppland" + newLine);
                writer.write("Ringebu, Oppland" + newLine);
                writer.write("Øyer, Oppland" + newLine);
                writer.write("Lillehammer, Oppland" + newLine);
                writer.write("Nordre Land, Oppland" + newLine);
                writer.write("Gjøvik, Oppland" + newLine);
                writer.write("Sør-Aurdal, Oppland" + newLine);
                writer.write("Søndre Land, Oppland" + newLine);
                writer.write("Vestre Toten, Oppland" + newLine);
                writer.write("Østre Toten, Oppland" + newLine);
                writer.write("Gran, Oppland" + newLine);
                writer.write("Jevnaker, Oppland" + newLine);
                writer.write("Lunner, Oppland" + newLine);
                writer.write("Folldal, Hedmark" + newLine);
                writer.write("Tynset, Hedmark" + newLine);
                writer.write("Tolga, Hedmark" + newLine);
                writer.write("Os, Hedmark" + newLine);
                writer.write("Engerdal, Hedmark" + newLine);
                writer.write("Alvdal, Hedmark" + newLine);
                writer.write("Rendalen, Hedmark" + newLine);
                writer.write("Stor-Elvdal, Hedmark" + newLine);
                writer.write("Trysil, Hedmark" + newLine);
                writer.write("Åmot, Hedmark" + newLine);
                writer.write("Ringsaker, Hedmark" + newLine);
                writer.write("Hamar, Hedmark" + newLine);
                writer.write("Løten, Hedmark" + newLine);
                writer.write("Elverum, Hedmark" + newLine);
                writer.write("Stange, Hedmark" + newLine);
                writer.write("Våler, Hedmark" + newLine);
                writer.write("Åsnes, Hedmark" + newLine);
                writer.write("Nord-Odal, Hedmark" + newLine);
                writer.write("Sør-Odal, Hedmark" + newLine);
                writer.write("Grue, Hedmark" + newLine);
                writer.write("Kongsvinger, Hedmark" + newLine);
                writer.write("Eidskog, Hedmark" + newLine);
                writer.write("Sunndal, Møre og Romsdal" + newLine);
                writer.write("Surnadal, Møre og Romsdal" + newLine);
                writer.write("Rindal, Møre og Romsdal" + newLine);
                writer.write("Nesset, Møre og Romsdal" + newLine);
                writer.write("Rauma, Møre og Romsdal" + newLine);
                writer.write("Molde, Møre og Romsdal" + newLine);
                writer.write("Tingvoll, Møre og Romsdal" + newLine);
                writer.write("Halsa, Møre og Romsdal" + newLine);
                writer.write("Aure, Møre og Romsdal" + newLine);
                writer.write("Tustna, Møre og Romsdal" + newLine);
                writer.write("Smøla, Møre og Romsdal" + newLine);
                writer.write("Frei, Møre og Romsdal" + newLine);
                writer.write("Gjemnes, Møre og Romsdal" + newLine);
                writer.write("Kristiansund, Møre og Romsdal" + newLine);
                writer.write("Eide, Møre og Romsdal" + newLine);
                writer.write("Averøy, Møre og Romsdal" + newLine);
                writer.write("Fræna, Møre og Romsdal" + newLine);
                writer.write("Aukra, Møre og Romsdal" + newLine);
                writer.write("Norddal, Møre og Romsdal" + newLine);
                writer.write("Stranda, Møre og Romsdal" + newLine);
                writer.write("Stordal, Møre og Romsdal" + newLine);
                writer.write("Vestnes, Møre og Romsdal" + newLine);
                writer.write("Ørskog, Møre og Romsdal" + newLine);
                writer.write("Ørsta, Møre og Romsdal" + newLine);
                writer.write("Sykkylven, Møre og Romsdal" + newLine);
                writer.write("Volda, Møre og Romsdal" + newLine);
                writer.write("Vanylven, Møre og Romsdal" + newLine);
                writer.write("Sande, Møre og Romsdal" + newLine);
                writer.write("Skodje, Møre og Romsdal" + newLine);
                writer.write("Haram, Møre og Romsdal" + newLine);
                writer.write("Midsund, Møre og Romsdal" + newLine);
                writer.write("Sandøy, Møre og Romsdal" + newLine);
                writer.write("Ålesund, Møre og Romsdal" + newLine);
                writer.write("Sula, Møre og Romsdal" + newLine);
                writer.write("Giske, Møre og Romsdal" + newLine);
                writer.write("Hareid, Møre og Romsdal" + newLine);
                writer.write("Ulstein, Møre og Romsdal" + newLine);
                writer.write("Herøy, Møre og Romsdal" + newLine);
                writer.write("Røros, Sør-Trøndelag" + newLine);
                writer.write("Tydal, Sør-Trøndelag" + newLine);
                writer.write("Selbu, Sør-Trøndelag" + newLine);
                writer.write("Holtålen, Sør-Trøndelag" + newLine);
                writer.write("Midtre Gauldal, Sør-Trøndelag" + newLine);
                writer.write("Oppdal, Sør-Trøndelag" + newLine);
                writer.write("Rennebu, Sør-Trøndelag" + newLine);
                writer.write("Meldal, Sør-Trøndelag" + newLine);
                writer.write("Malvik, Sør-Trøndelag" + newLine);
                writer.write("Melhus, Sør-Trøndelag" + newLine);
                writer.write("Klæbu, Sør-Trøndelag" + newLine);
                writer.write("Trondheim, Sør-Trøndelag" + newLine);
                writer.write("Skaun, Sør-Trøndelag" + newLine);
                writer.write("Orkdal, Sør-Trøndelag" + newLine);
                writer.write("Hemne, Sør-Trøndelag" + newLine);
                writer.write("Snillfjord, Sør-Trøndelag" + newLine);
                writer.write("Agdenes, Sør-Trøndelag" + newLine);
                writer.write("Rissa, Sør-Trøndelag" + newLine);
                writer.write("Åfjord, Sør-Trøndelag" + newLine);
                writer.write("Roan, Sør-Trøndelag" + newLine);
                writer.write("Osen, Sør-Trøndelag" + newLine);
                writer.write("Hitra, Sør-Trøndelag" + newLine);
                writer.write("Frøya, Sør-Trøndelag" + newLine);
                writer.write("Ørland, Sør-Trøndelag" + newLine);
                writer.write("Bjugn, Sør-Trøndelag" + newLine);
                writer.write("Lierne, Nord-Trøndelag" + newLine);
                writer.write("Røyrvik, Nord-Trøndelag" + newLine);
                writer.write("Snåsa, Nord-Trøndelag" + newLine);
                writer.write("Verdal, Nord-Trøndelag" + newLine);
                writer.write("Meråker, Nord-Trøndelag" + newLine);
                writer.write("Stjørdal, Nord-Trøndelag" + newLine);
                writer.write("Frosta, Nord-Trøndelag" + newLine);
                writer.write("Levanger, Nord-Trøndelag" + newLine);
                writer.write("Namsskogan, Nord-Trøndelag" + newLine);
                writer.write("Grong, Nord-Trøndelag" + newLine);
                writer.write("Høylandet, Nord-Trøndelag" + newLine);
                writer.write("Overhalla, Nord-Trøndelag" + newLine);
                writer.write("Steinkjer, Nord-Trøndelag" + newLine);
                writer.write("Leksvik, Nord-Trøndelag" + newLine);
                writer.write("Mosvik, Nord-Trøndelag" + newLine);
                writer.write("Inderøy, Nord-Trøndelag" + newLine);
                writer.write("Verran, Nord-Trøndelag" + newLine);
                writer.write("Namdalseid, Nord-Trøndelag" + newLine);
                writer.write("Namsos, Nord-Trøndelag" + newLine);
                writer.write("Flatanger, Nord-Trøndelag" + newLine);
                writer.write("Fosnes, Nord-Trøndelag" + newLine);
                writer.write("Nærøy, Nord-Trøndelag" + newLine);
                writer.write("Vikna, Nord-Trøndelag" + newLine);
                writer.write("Leka, Nord-Trøndelag" + newLine);
                writer.write("Hattfjelldal, Nordland" + newLine);
                writer.write("Grane, Nordland" + newLine);
                writer.write("Bindal, Nordland" + newLine);
                writer.write("Brønnøy, Nordland" + newLine);
                writer.write("Sømna, Nordland" + newLine);
                writer.write("Vefsn, Nordland" + newLine);
                writer.write("Hemnes, Nordland" + newLine);
                writer.write("Rana, Nordland" + newLine);
                writer.write("Leirfjord, Nordland" + newLine);
                writer.write("Vevelstad, Nordland" + newLine);
                writer.write("Vega, Nordland" + newLine);
                writer.write("Alstahaug, Nordland" + newLine);
                writer.write("Herøy, Nordland" + newLine);
                writer.write("Dønna, Nordland" + newLine);
                writer.write("Nesna, Nordland" + newLine);
                writer.write("Lurøy, Nordland" + newLine);
                writer.write("Rødøy, Nordland" + newLine);
                writer.write("Træna, Nordland" + newLine);
                writer.write("Meløy, Nordland" + newLine);
                writer.write("Saltdal, Nordland" + newLine);
                writer.write("Beiarn, Nordland" + newLine);
                writer.write("Skjerstad, Nordland" + newLine);
                writer.write("Fauske, Nordland" + newLine);
                writer.write("Bodø, Nordland" + newLine);
                writer.write("Gildeskål, Nordland" + newLine);
                writer.write("Sørfold, Nordland" + newLine);
                writer.write("Steigen, Nordland" + newLine);
                writer.write("Hamarøy, Nordland" + newLine);
                writer.write("Tysfjord, Nordland" + newLine);
                writer.write("Ballangen, Nordland" + newLine);
                writer.write("Narvik, Nordland" + newLine);
                writer.write("Evenes, Nordland" + newLine);
                writer.write("Tjeldsund, Nordland" + newLine);
                writer.write("Lødingen, Nordland" + newLine);
                writer.write("Hadsel, Nordland" + newLine);
                writer.write("Sortland, Nordland" + newLine);
                writer.write("Bø, Nordland" + newLine);
                writer.write("Andøy, Nordland" + newLine);
                writer.write("Øksnes, Nordland" + newLine);
                writer.write("Vågan, Nordland" + newLine);
                writer.write("Vestvågøy, Nordland" + newLine);
                writer.write("Flakstad, Nordland" + newLine);
                writer.write("Moskenes, Nordland" + newLine);
                writer.write("Værøy, Nordland" + newLine);
                writer.write("Røst, Nordland" + newLine);
                writer.write("Kvænangen, Troms" + newLine);
                writer.write("Nordreisa, Troms" + newLine);
                writer.write("Skjervøy, Troms" + newLine);
                writer.write("Kåfjord, Troms" + newLine);
                writer.write("Storfjord, Troms" + newLine);
                writer.write("Målselv, Troms" + newLine);
                writer.write("Bardu, Troms" + newLine);
                writer.write("Lyngen, Troms" + newLine);
                writer.write("Tromsø, Troms" + newLine);
                writer.write("Karlsøy, Troms" + newLine);
                writer.write("Balsfjord, Troms" + newLine);
                writer.write("Lenvik, Troms" + newLine);
                writer.write("Berg, Troms" + newLine);
                writer.write("Torsken, Troms" + newLine);
                writer.write("Tranøy, Troms" + newLine);
                writer.write("Bjarkøy, Troms" + newLine);
                writer.write("Sørreisa, Troms" + newLine);
                writer.write("Dyrøy, Troms" + newLine);
                writer.write("Salangen, Troms" + newLine);
                writer.write("Levangen, Troms" + newLine);
                writer.write("Gratangen, Troms" + newLine);
                writer.write("Skånland, Troms" + newLine);
                writer.write("Kvæfjord, Troms" + newLine);
                writer.write("Harstad, Troms" + newLine);
                writer.write("Kautokeino, Finnmark" + newLine);
                writer.write("Karasjok, Finnmark" + newLine);
                writer.write("Alta, Finnmark" + newLine);
                writer.write("Loppa, Finnmark" + newLine);
                writer.write("Hasvik, Finnmark" + newLine);
                writer.write("Hammerfest, Finnmark" + newLine);
                writer.write("Kvalsund, Finnmark" + newLine);
                writer.write("Porsanger, Finnmark" + newLine);
                writer.write("Måsøy, Finnmark" + newLine);
                writer.write("Nordkapp, Finnmark" + newLine);
                writer.write("Lebesby, Finnmark" + newLine);
                writer.write("Gamvik, Finnmark" + newLine);
                writer.write("Tana, Finnmark" + newLine);
                writer.write("Berlevåg, Finnmark" + newLine);
                writer.write("Båtsfjord, Finnmark" + newLine);
                writer.write("Vardø, Finnmark" + newLine);
                writer.write("Vadsø, Finnmark" + newLine);
                writer.write("Nesseby, Finnmark" + newLine);
                writer.write("Sør-Varanger, Finnmark" + newLine);

                writer.close();
            }

            buf = new BufferedReader(new FileReader(file));
            while ((line = buf.readLine()) != null) {
                String l = line.trim();
                if(l.isEmpty())
                    continue;
                communities.add(l);
            }
            buf.close();

            ArrayAdapter<String> adapterCommunities = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, communities);
            adapterCommunities.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            etNextCommunity.setAdapter(adapterCommunities);

            // Load preferences
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            collector = prefs.getString("collector", "");
            collectorAddress = prefs.getString("collector_address", "");
            dataId = prefs.getString("data_id", "");
            String strSyncFrequency = prefs.getString("sync_frequency", "3000");
            String strSyncDistance = prefs.getString("sync_distance", "2");
            syncFrequency = Long.parseLong(strSyncFrequency);
            syncDistance = Float.parseFloat(strSyncDistance);
            prefs.registerOnSharedPreferenceChangeListener(preferenceListener);
            //Toast.makeText(this, "Sync freq: " + Long.toString(syncFrequency) + " Sync dist: " + Float.toString(syncDistance), Toast.LENGTH_SHORT).show();

            tvDataID.setText(dataId);
            nSatellites = 0;
            accuracy = 0f;

            // Initialize location manager
            locManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
            providerEnabled = locManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            if (!providerEnabled) {
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivity(intent);
            }

            Criteria criteria = new Criteria();
            locProvider = locManager.getBestProvider(criteria, false);
            if(locProvider == null) {
                Toast.makeText(this, "Ingen 'location provider' tilgjengelig", Toast.LENGTH_SHORT).show();
                exitApp();
                return;
            }

            locManager.addGpsStatusListener(gpsStatusListener);

            Location location = locManager.getLastKnownLocation(locProvider);
            if (location != null) {
                tvCurrProvider.setText("Provider: " + locProvider);
                onLocationChanged(location);
            }
        } catch(SecurityException ex) {
            Toast.makeText(SampleRegistrationActivity.this, ErrorString(ex.getMessage()), Toast.LENGTH_LONG).show();
        } catch(Exception ex) {
            Toast.makeText(SampleRegistrationActivity.this, ErrorString(ex.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_sample_registration, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private SharedPreferences.OnSharedPreferenceChangeListener preferenceListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

            try {
                if(key.equals("sync_frequency")) {
                    String strSyncFrequency = sharedPreferences.getString("sync_frequency", "3000");
                    syncFrequency = Long.parseLong(strSyncFrequency);
                    locManager.requestLocationUpdates(locProvider, syncFrequency, syncDistance, SampleRegistrationActivity.this);

                } else if(key.equals("sync_distance")) {
                    String strSyncDistance = sharedPreferences.getString("sync_distance", "2");
                    syncDistance = Float.parseFloat(strSyncDistance);
                    locManager.requestLocationUpdates(locProvider, syncFrequency, syncDistance, SampleRegistrationActivity.this);
                } else if(key.equals("data_id")) {
                    dataId = sharedPreferences.getString("data_id", "");
                    tvDataID.setText(dataId);
                }
            } catch(SecurityException ex) {
                Toast.makeText(SampleRegistrationActivity.this, ErrorString(ex.getMessage()), Toast.LENGTH_SHORT).show();
            }
        }
    };

    private View.OnClickListener btnBack_onClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            btnBack.setText(R.string.back);
            btnNextId.setText(R.string.store_next_sample);
            tvEditing.setText("");
            etNextSampleType.setText("");

            if(editIndex != -1) {
                editIndex = -1;
                tvNextID.setText(String.valueOf(nextId));
                return;
            }

            editIndex = -1;
            switcher.showPrevious();
        }
    };

    private View.OnClickListener btnEditSample_onClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            editIndex = -1;

            File file = new File (projDir, tvProjName.getText().toString() + ".txt");
            if(!file.exists())
                return;

            String line;
            BufferedReader buf = null;
            editSampleArray.clear();

            try {

                buf = new BufferedReader(new FileReader(file));
                buf.readLine();
                while ((line = buf.readLine()) != null) {
                    String l = line.trim();
                    if (l.isEmpty())
                        continue;
                    editSampleArray.add(l);
                }
                buf.close();

            } catch (Exception e) {
                e.printStackTrace();
                return;
            }

            String[] samples = new String[editSampleArray.size()];
            for(int i=0; i<editSampleArray.size(); i++)
            {
                String[] parts = editSampleArray.get(i).split("\\|", -1);
                samples[i] = parts[2] + " - " + parts[7];
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(SampleRegistrationActivity.this);
            builder.setTitle(R.string.select_sample_for_edit).setItems(samples, selectSampleListener);
            builder.show();
        }
    };

    DialogInterface.OnClickListener selectSampleListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {

            editIndex = which;
            String[] parts = editSampleArray.get(which).split("\\|", -1);

            if(parts.length < 22)
            {
                Toast.makeText(SampleRegistrationActivity.this, ErrorString("Feil antall elementer i loggfil"), Toast.LENGTH_LONG).show();
                return;
            }

            tvDataID.setText(parts[0]);
            tvNextID.setText(parts[1]);
            etNextSampleType.setText(parts[9]);
            etNextLocation.setText(parts[10]);
            etNextLocationType.setSelection(locationTypes.indexOf(parts[11]));
            etNextCommunity.setText(parts[12]);
            etNextAdjacentHardwoods.setItems(adjacentHardwoods, parts[13], new MultiSpinner.MultiSpinnerListener() {
                @Override
                public void onItemsSelected(boolean[] selected) {
                }
            });
            etNextGrass.setText(parts[14]);
            etNextHerbs.setText(parts[15]);
            etNextHeather.setText(parts[16]);
            etNextDensity.setSelection(densityList.indexOf(parts[17]));
            etNextReceiver.setText(parts[20]);
            etNextComment.setText(parts[21]);

            btnBack.setText(R.string.cancel);
            btnNextId.setText(R.string.update);
            tvEditing.setText(" (redigering...)");

            //Toast.makeText(SampleRegistrationActivity.this, "Prøve valgt: " + parts[1] + " - " + parts[9], Toast.LENGTH_LONG).show();

            AlertDialog.Builder yesNoDiag = new AlertDialog.Builder(SampleRegistrationActivity.this);
            yesNoDiag.setMessage("Vi du oppdatere koordinatene også?");
            yesNoDiag.setPositiveButton("Ja", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    modCoords = true;
                }
            }).setNegativeButton("Nei", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    modCoords = false;
                }
            });
            yesNoDiag.show();
        }
    };

    private View.OnClickListener btnNextID_onClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            try {
                String sampleType = etNextSampleType.getText().toString().trim();
                if(sampleType.length() < 1)
                {
                    Toast.makeText(SampleRegistrationActivity.this, ErrorString("Feltet for prøvetype er påkrevet"), Toast.LENGTH_LONG).show();
                    return;
                }

                float fValue = 0f;
                TimeZone tz = TimeZone.getTimeZone("UTC");
                DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
                df.setTimeZone(tz);
                String strDateISO = df.format(new Date());

                String projName = tvProjName.getText().toString();
                String currLat = tvCurrLat.getText().toString().trim();
                String currLon = tvCurrLon.getText().toString().trim();
                String aboveSea = tvCurrentAboveSeaLevel.getText().toString().trim();
                String dataID = tvDataID.getText().toString().trim();
                String sNextID = tvNextID.getText().toString().trim();
                String location = etNextLocation.getText().toString().trim();
                String locationType = etNextLocationType.getSelectedItem().toString().trim();
                String community = etNextCommunity.getText().toString().trim();
                String adjacentHardwoods = etNextAdjacentHardwoods.getItemsText();
                String grass = etNextGrass.getText().toString().trim();
                String herbs = etNextHerbs.getText().toString().trim();
                String heather = etNextHeather.getText().toString().trim();
                String density = etNextDensity.getSelectedItem().toString().trim();
                String receiver = etNextReceiver.getText().toString().trim();
                String sampleComment = etNextComment.getText().toString().trim();
                String nSats = String.valueOf(nSatellites);
                String nAcc = String.valueOf(accuracy);

                if(location.length() < 1)
                {
                    Toast.makeText(SampleRegistrationActivity.this, ErrorString("Feltet for stedsnavn er påkrevet"), Toast.LENGTH_LONG).show();
                    return;
                }

                if(locationType.length() < 1)
                {
                    Toast.makeText(SampleRegistrationActivity.this, ErrorString("Feltet for områdebeskrivelse er påkrevet"), Toast.LENGTH_LONG).show();
                    return;
                }

                if(density.length() < 1)
                {
                    Toast.makeText(SampleRegistrationActivity.this, ErrorString("Feltet for forekomst er påkrevet"), Toast.LENGTH_LONG).show();
                    return;
                }

                String line = dataID + "|" + sNextID + "|" + collector + "|" + collectorAddress + "|" + projName + "|"
                        + strDateISO + "|" + currLat + "|" + currLon + "|"  + aboveSea + "|" + sampleType + "|" + location + "|"
                        + locationType + "|" + community + "|" + adjacentHardwoods + "|" + grass + "|" + herbs + "|" + heather + "|" + density + "|"
                        + nSats + "|" + nAcc + "|" + receiver + "|" + sampleComment + newLine;

                if(editIndex == -1) {
                    File file = new File(projDir, tvProjName.getText().toString() + ".txt");
                    FileOutputStream out = new FileOutputStream(file, true);
                    out.write(line.getBytes());
                    out.flush();
                    out.close();
                    Toast.makeText(SampleRegistrationActivity.this, "ID " + dataID + " " + sNextID + " lagret som " + sampleType, Toast.LENGTH_LONG).show();
                    nextId++;
                }
                else {
                    String filename = tvProjName.getText().toString() + ".txt";
                    String newFilename = tvProjName.getText().toString() + "_new.txt";

                    File file = new File(projDir, filename);
                    File newFile = new File(projDir, newFilename);

                    int idx = 0;
                    String l;
                    BufferedReader rd = new BufferedReader(new FileReader(file));
                    BufferedWriter wr = new BufferedWriter(new FileWriter(newFile));
                    while ((l = rd.readLine()) != null) {
                        if(idx == editIndex + 1) {
                            if(!modCoords) {
                                String[] parts = l.split("\\|", -1);
                                String modLat = parts[6];
                                String modLon = parts[7];
                                String modAlt = parts[8];

                                line = dataID + "|" + sNextID + "|" + collector + "|" + collectorAddress + "|" + projName + "|"
                                        + strDateISO + "|" + modLat + "|" + modLon + "|"  + modAlt + "|" + sampleType + "|" + location + "|"
                                        + locationType + "|" + community + "|" + adjacentHardwoods + "|" + grass + "|" + herbs + "|" + heather + "|" + density + "|"
                                        + nSats + "|" + nAcc + "|" + receiver + "|" + sampleComment + newLine;
                            }

                            wr.write(line);
                        }
                        else {
                            wr.write(l + newLine);
                        }
                        idx++;
                    }
                    rd.close();
                    wr.close();

                    file.delete();
                    newFile.renameTo(file);

                    Toast.makeText(SampleRegistrationActivity.this, "ID " + dataID + " " + sNextID + " oppdatert", Toast.LENGTH_LONG).show();
                    editIndex = -1;
                    btnNextId.setText(R.string.store_next_sample);
                    btnBack.setText(R.string.back);
                    tvEditing.setText("");
                }

                tvNextID.setText(String.valueOf(nextId));
                etNextSampleType.setText("");
                svSamples.fullScroll(ScrollView.FOCUS_UP);

            } catch (Exception e) {
                Toast.makeText(SampleRegistrationActivity.this, ErrorString(e.getMessage()), Toast.LENGTH_LONG).show();
            }
        }
    };

    private AdapterView.OnItemClickListener lstProj_onItemClick = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            tvProjName.setText(String.valueOf(parent.getItemAtPosition(position)));

            if(dataId.trim().length() < 1)
            {
                Toast.makeText(SampleRegistrationActivity.this, ErrorString("Du må legge inn en 'Phone ID' under innstillinger"), Toast.LENGTH_LONG).show();
                return;
            }

            if(collector.trim().length() < 1)
            {
                Toast.makeText(SampleRegistrationActivity.this, ErrorString("Du må legge inn en 'collector' under innstillinger"), Toast.LENGTH_LONG).show();
                return;
            }

            if(collectorAddress.trim().length() < 1)
            {
                Toast.makeText(SampleRegistrationActivity.this, ErrorString("Du må legge inn en 'collector address' under innstillinger"), Toast.LENGTH_LONG).show();
                return;
            }

            try {

                LineNumberReader lnr = new LineNumberReader(new FileReader(new File(projDir, tvProjName.getText().toString() + ".txt")));
                lnr.skip(Long.MAX_VALUE);
                nextId = lnr.getLineNumber();
                lnr.close();

            } catch (Exception e) {
                Toast.makeText(SampleRegistrationActivity.this, ErrorString(e.getMessage()), Toast.LENGTH_LONG).show();
            }

            tvNextID.setText(String.valueOf(nextId));
            switcher.showNext();
        }
    };

    private EditText.OnKeyListener etNewProj_onKey = new EditText.OnKeyListener() {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            EditText et = (EditText) v;
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {

                String strNewProj = et.getText().toString().trim();
                if(strNewProj.length() < 1)
                {
                    Toast.makeText(SampleRegistrationActivity.this, ErrorString("Feltet for 'prosjektnavn' er påkrevet"), Toast.LENGTH_LONG).show();
                    return true;
                }

                for(int i=0; i<adapter.getCount(); i++) {
                    String s = (String)adapter.getItem(i);
                    if(s.equalsIgnoreCase(strNewProj)) {
                        Toast.makeText(SampleRegistrationActivity.this, ErrorString("Prosjektet finnes allerede"), Toast.LENGTH_LONG).show();
                        return true;
                    }
                }

                File file = new File (projDir, strNewProj + ".txt");
                try {
                    FileOutputStream out = new FileOutputStream(file);
                    String  strUID = UUID.randomUUID().toString() + newLine;
                    out.write(strUID.getBytes());
                    out.flush();
                    out.close();
                } catch (Exception e) {
                    Toast.makeText(SampleRegistrationActivity.this, ErrorString(e.getMessage()), Toast.LENGTH_LONG).show();
                }

                populateProjects();
                et.setText("");

                return true;
            }
            return false;
        }
    };

    GpsStatus.Listener gpsStatusListener = new GpsStatus.Listener() {
        @Override
        public void onGpsStatusChanged(int event) {
            if (event == GpsStatus.GPS_EVENT_SATELLITE_STATUS || event == GpsStatus.GPS_EVENT_FIRST_FIX) {
                GpsStatus status = locManager.getGpsStatus(null);
                Iterable<GpsSatellite> sats = status.getSatellites();
                nSatellites = 0;
                for (GpsSatellite sat : sats) {
                    if(sat.usedInFix())
                        nSatellites++;
                }
                tvCurrFix.setText("Sats: " + String.valueOf(nSatellites));
            }
        }
    };

    /* Request updates at startup */
    @Override
    protected void onResume() {
        super.onResume();
        if(!providerEnabled) {
            Toast.makeText(SampleRegistrationActivity.this, ErrorString("Ingen 'provider' aktiv"), Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            locManager.requestLocationUpdates(locProvider, syncFrequency, syncDistance, this);
        } catch(SecurityException ex) {
            Toast.makeText(SampleRegistrationActivity.this, ErrorString(ex.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    /* Remove the locationlistener updates when Activity is paused */
    @Override
    protected void onPause() {
        super.onPause();
        if(!providerEnabled) {
            Toast.makeText(SampleRegistrationActivity.this, ErrorString("No provider enabled"), Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            locManager.removeUpdates(this);
        } catch(SecurityException ex) {
            Toast.makeText(SampleRegistrationActivity.this, ErrorString(ex.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onLocationChanged(Location location) {

        if(location.hasAltitude()) {
            double alt = location.getAltitude();
            tvCurrentAboveSeaLevel.setText(String.valueOf(alt));
        }

        if(location.hasAccuracy()) {
            accuracy = location.getAccuracy();
            tvCurrAcc.setText("Acc: " + String.valueOf(accuracy) + "m");
        }

        Date now = new Date();
        tvCurrGPSDate.setText(DateFormat.getDateTimeInstance().format(now));

        double lat = (double) (location.getLatitude());
        tvCurrLat.setText(String.valueOf(lat));

        double lng = (double) (location.getLongitude());
        tvCurrLon.setText(String.valueOf(lng));
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onProviderEnabled(String provider) {
        tvCurrProvider.setText("Provider: " + provider);
    }

    @Override
    public void onProviderDisabled(String provider) {
        tvCurrProvider.setText("");
    }

    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    public File getProjectDir() {
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "sampleregistrationfungi/projects");
        file.mkdirs();
        return file;
    }

    public File getConfigDir() {
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "sampleregistrationfungi/config");
        file.mkdirs();
        return file;
    }

    public void populateProjects() {
        items.clear();
        File[] listOfFiles = projDir.listFiles();
        for (File file : listOfFiles) {
            if (file.isFile()) {
                int pos = file.getName().lastIndexOf(".");
                String baseName = pos > 0 ? file.getName().substring(0, pos) : file.getName();
                items.add(baseName);
            }
        }
        lstProj.setAdapter(adapter);
    }

    public void exitApp() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}
