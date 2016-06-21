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
import android.graphics.Color;
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
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
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

    private ViewSwitcher switcher;
    private Button btnBack, btnNextId, btnEditSample;
    private ListView lstProj;
    private ArrayList<String> items;
    private ListAdapter adapter;
    private LocationManager locManager;
    private String locProvider;
    private boolean providerEnabled;
    private TextView tvProjName, tvCurrProvider, tvCurrFix, tvCurrAcc, tvCurrGPSDate, tvCurrLat, tvCurrLon, tvCurrentAboveSeaLevel, tvDataID, tvNextID;
    private EditText etNextGrass, etNextHerbs, etNextHeather, etNextReceiver, etNextComment, etNextLocation;
    private AutoCompleteTextView etNextSampleType, etNextCommunity;
    private Spinner etNextLocationType, etNextDensity;
    private MultiSpinner etNextAdjacentHardwoods;
    private File projDir, cfgDir;
    private int nextId;
    private String collector, collectorAddress, dataId;
    private long syncFrequency;
    private float syncDistance;
    private int nSatellites;
    private float accuracy;

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

            int cliWidth = winWidth - 32;
            btnBack = (Button)findViewById(R.id.btnBack);
            btnBack.setWidth(cliWidth / 3);
            btnBack.setOnClickListener(btnBack_onClick);

            btnEditSample = (Button)findViewById(R.id.btnEditSample);
            btnEditSample.setWidth(cliWidth / 3);
            btnEditSample.setOnClickListener(btnEditSample_onClick);

            btnNextId = (Button)findViewById(R.id.btnNextId);
            btnNextId.setWidth(cliWidth / 3);
            btnNextId.setOnClickListener(btnNextID_onClick);

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

            tvProjName = (TextView) findViewById(R.id.tvProjName);
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
                writer.write("Steinsopp\n");
                writer.write("Fluesopp\n");
                writer.write("Kantarell\n");
                writer.write("Blåbær\n");
                writer.write("Jordbær\n");
                writer.write("Bringebær\n");
                writer.write("Tyttebær\n");
                writer.write("Annet\n");
                writer.close();
            }

            String line;
            BufferedReader buf = new BufferedReader(new FileReader(file));
            while ((line = buf.readLine()) != null) {
                sampleTypes.add(line);
            }
            buf.close();

            ArrayAdapter<String> adapterSampleTypes = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, sampleTypes);
            etNextSampleType.setAdapter(adapterSampleTypes);

            file = new File (cfgDir, "location_types.txt");
            if(!file.exists()) {
                file.createNewFile();
                BufferedWriter writer = new BufferedWriter(new FileWriter(file));
                writer.write("\n");
                writer.write("Skogsbeite\n");
                writer.write("Fjellbeite\n");
                writer.write("Kysthei\n");
                writer.write("Myr\n");
                writer.write("Barskog\n");
                writer.write("Løvskog\n");
                writer.write("Fjellskog\n");
                writer.write("Snaufjellet\n");
                writer.write("Annet\n");
                writer.close();
            }

            buf = new BufferedReader(new FileReader(file));
            while ((line = buf.readLine()) != null) {
                locationTypes.add(line);
            }
            buf.close();

            ArrayAdapter<String> adapterLocationTypes = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, locationTypes);
            adapterLocationTypes.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            etNextLocationType.setAdapter(adapterLocationTypes);

            file = new File (cfgDir, "adjacent_hardwoods.txt");
            if(!file.exists()) {
                file.createNewFile();
                BufferedWriter writer = new BufferedWriter(new FileWriter(file));
                writer.write("Gran\n");
                writer.write("Furu\n");
                writer.write("Bjørk\n");
                writer.write("Selje\n");
                writer.write("Osp\n");
                writer.write("Rogn\n");
                writer.write("Or\n");
                writer.write("Vier\n");
                writer.write("Einer\n");
                writer.write("Annet\n");
                writer.close();
            }

            buf = new BufferedReader(new FileReader(file));
            while ((line = buf.readLine()) != null) {
                adjacentHardwoods.add(line);
            }
            buf.close();

            etNextAdjacentHardwoods.setItems(adjacentHardwoods, "", new MultiSpinner.MultiSpinnerListener() {
                @Override
                public void onItemsSelected(boolean[] selected) {
                }
            });

            densityList.add("");
            densityList.add("Lite");
            densityList.add("Middels");
            densityList.add("Mye");

            ArrayAdapter<String> adapterDensity = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, densityList);
            adapterDensity.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            etNextDensity.setAdapter(adapterDensity);

            ArrayList<String> communities = new ArrayList<String>();
            file = new File (cfgDir, "communities.txt");
            if(file.exists()) {
                buf = new BufferedReader(new FileReader(file));
                while ((line = buf.readLine()) != null) {
                    communities.add(line);
                }
                buf.close();
            }

            ArrayAdapter<String> adapterCommunities = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, communities);
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
            editIndex = -1;
            switcher.showPrevious();
        }
    };

    private View.OnClickListener btnEditSample_onClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            editIndex = -1;

            // TODO
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
                    editSampleArray.add(line);
                    //String[] parts = line.split("\\|");
                    //editSampleArray.add(parts[1] + " " + parts[9]);
                }

                buf.close();
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }

            String[] samples = new String[editSampleArray.size()];
            for(int i=0; i<editSampleArray.size(); i++)
            {
                String[] parts = editSampleArray.get(i).split("\\|");
                samples[i] = parts[1] + " - " + parts[9];
            }
            //samples = editSampleArray.toArray(samples);

            AlertDialog.Builder builder = new AlertDialog.Builder(SampleRegistrationActivity.this);
            builder.setTitle(R.string.select_sample).setItems(samples, selectSampleListener);
            builder.show();
        }
    };

    DialogInterface.OnClickListener selectSampleListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {

            editIndex = which + 1;
            String[] parts = editSampleArray.get(which).split("\\|");

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
            etNextComment.setText(parts[20]);

            Toast.makeText(SampleRegistrationActivity.this, "Prøve valgt: " + editIndex + ": " + parts[1] + " - " + parts[9], Toast.LENGTH_LONG).show();
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
                String nextID = tvNextID.getText().toString().trim();
                String location = etNextLocation.getText().toString().trim();
                String locationType = etNextLocationType.getSelectedItem().toString().trim();
                String community = etNextCommunity.getText().toString().trim();
                String adjacentHardwoods = "";
                List<String> items = etNextAdjacentHardwoods.getItems();
                boolean[] selected = etNextAdjacentHardwoods.getSelected();
                for(int i=0; i<items.size(); i++)
                    if(selected[i])
                        adjacentHardwoods += items.get(i).trim() + ",";
                if(adjacentHardwoods.length() > 0 && adjacentHardwoods.endsWith(","))
                    adjacentHardwoods = adjacentHardwoods.substring(0, adjacentHardwoods.length() - 1);

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

                String line = dataID + "|" + nextID + "|" + collector + "|" + collectorAddress + "|" + projName + "|"
                        + strDateISO + "|" + currLat + "|" + currLon + "|"  + aboveSea + "|" + sampleType + "|" + location + "|"
                        + locationType + "|" + community + "|" + adjacentHardwoods + "|" + grass + "|" + herbs + "|" + heather + "|" + density + "|"
                        + nSats + "|" + nAcc + "|" + receiver + "|" + sampleComment + "\n";

                if(editIndex == -1) {
                    File file = new File(projDir, tvProjName.getText().toString() + ".txt");
                    FileOutputStream out = new FileOutputStream(file, true);
                    out.write(line.getBytes());
                    out.flush();
                    out.close();

                    Toast.makeText(SampleRegistrationActivity.this, "ID " + dataID + " " + nextID + " lagret som " + sampleType, Toast.LENGTH_LONG).show();
                }
                else {
                    // TODO
                    // Update line "editIndex"

                    Toast.makeText(SampleRegistrationActivity.this, "ID " + dataID + " " + nextID + " oppdatert", Toast.LENGTH_LONG).show();
                }

                Toast.makeText(SampleRegistrationActivity.this, "ID " + dataID + " " + nextID + " lagret som " + sampleType, Toast.LENGTH_LONG).show();

                editIndex = -1;
                nextId++;
                tvNextID.setText(String.valueOf(nextId));

                etNextSampleType.setText("");

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
                    String  strUID = UUID.randomUUID().toString() + "\n";
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
