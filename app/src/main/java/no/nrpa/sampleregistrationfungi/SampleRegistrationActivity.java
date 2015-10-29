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
// CONTRIBUTORS AND COPYRIGHT HOLDERS (c) 2015:
// Dag Robøle (dag D0T robole AT gmail D0T com)

package no.nrpa.sampleregistrationfungi;

import android.Manifest;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
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
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

import no.nrpa.sampleregistrationfungi.R;

public class SampleRegistrationActivity extends AppCompatActivity implements LocationListener {

    private ViewSwitcher switcher;
    private ListView lstProj;
    private ArrayList<String> items;
    private ListAdapter adapter;
    private LocationManager locManager;
    private String locProvider;
    private boolean providerEnabled;
    private TextView tvProjName, tvCurrProvider, tvCurrFix, tvCurrAcc, tvCurrGPSDate, tvCurrLat, tvCurrLon, tvDataID, tvNextID;
    private EditText etStation, etMeasurementValue, etNextComment;
    private AutoCompleteTextView etNextSampleType, etMeasurementUnit;
    private File projDir, cfgDir;
    private int nextId;
    private String dataId;
    private long syncFrequency;
    private float syncDistance;
    private int nSatellites;
    private float accuracy;

    private Spanned ErrorString(String s) {
        return Html.fromHtml("<font color='#ff8888' ><b>" + s + "</b></font>");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sample_registration);

        try {
            //getWindow().getDecorView().setBackgroundColor(Color.parseColor("#fdf6e3"));

            ActionBar actionBar = getSupportActionBar();
            if(actionBar != null) {
                actionBar.setDisplayShowHomeEnabled(true);
                actionBar.setLogo(R.mipmap.ic_launcher);
                actionBar.setDisplayUseLogoEnabled(true);
            }

            if(!isExternalStorageWritable())
            {
                Toast.makeText(this, "Access to external storage denied", Toast.LENGTH_SHORT).show();
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

            Button btnBack = (Button) findViewById(R.id.btnBack);
            btnBack.setOnClickListener(btnBack_onClick);

            Button btnNextID = (Button) findViewById(R.id.btnNextId);
            btnNextID.setOnClickListener(btnNextID_onClick);

            tvProjName = (TextView) findViewById(R.id.tvProjName);
            tvCurrProvider = (TextView) findViewById(R.id.tvCurrentProvider);
            tvCurrFix = (TextView) findViewById(R.id.tvCurrentFix);
            tvCurrAcc = (TextView) findViewById(R.id.tvCurrentAcc);
            tvCurrGPSDate = (TextView) findViewById(R.id.tvLastDate);
            tvCurrLat = (TextView) findViewById(R.id.tvCurrentLatitude);
            tvCurrLon = (TextView) findViewById(R.id.tvCurrentLongitude);
            tvDataID = (TextView)findViewById(R.id.tvDataId);
            tvNextID = (TextView)findViewById(R.id.tvNextId);
            etNextSampleType = (AutoCompleteTextView)findViewById(R.id.etNextSampleType);
            etStation = (EditText)findViewById(R.id.etStation);
            etMeasurementValue = (EditText)findViewById(R.id.etMeasurementValue);
            etMeasurementUnit = (AutoCompleteTextView)findViewById(R.id.etMeasurementUnit);
            etNextComment = (EditText)findViewById(R.id.etNextComment);

            ArrayList<String> sampleTypes = new ArrayList<String>();
            File file = new File (cfgDir, "sample-types.txt");
            if(file.exists()) {
                String line;
                BufferedReader buf = new BufferedReader(new FileReader(file));
                while ((line = buf.readLine()) != null) {
                    sampleTypes.add(line);
                }
                buf.close();

                ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, sampleTypes);
                etNextSampleType.setAdapter(adapter);
            }

            ArrayList<String> units = new ArrayList<String>();
            file = new File (cfgDir, "units.txt");
            if(file.exists()) {
                String line;
                BufferedReader buf = new BufferedReader(new FileReader(file));
                while ((line = buf.readLine()) != null) {
                    units.add(line);
                }
                buf.close();

                ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, units);
                etMeasurementUnit.setAdapter(adapter);
            }

            // Load preferences
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
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
                Toast.makeText(this, "No location provider available", Toast.LENGTH_SHORT).show();
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
            switcher.showPrevious();
        }
    };

    private View.OnClickListener btnNextID_onClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            try {
                String sampleType = etNextSampleType.getText().toString().trim();
                if(sampleType.length() < 1)
                {
                    Toast.makeText(SampleRegistrationActivity.this, ErrorString("Field 'Sample type' is required"), Toast.LENGTH_LONG).show();
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
                String dataID = tvDataID.getText().toString().trim();
                String nextID = tvNextID.getText().toString().trim();
                String station = etStation.getText().toString().trim();
                String value = etMeasurementValue.getText().toString().trim();
                String unit = etMeasurementUnit.getText().toString().trim();
                if(value.length() > 0) {
                    try {
                        fValue = Float.parseFloat(value);
                    } catch (NumberFormatException ex) {
                        Toast.makeText(SampleRegistrationActivity.this, ErrorString("Invalid value format"), Toast.LENGTH_LONG).show();
                        return;
                    }

                    if(unit.length() < 1) {
                        Toast.makeText(SampleRegistrationActivity.this, ErrorString("Missing unit"), Toast.LENGTH_LONG).show();
                        return;
                    }
                }
                String sampleComment = etNextComment.getText().toString().trim();
                String nSats = String.valueOf(nSatellites);
                String nAcc = String.valueOf(accuracy);

                String line = dataID + "|" + projName + "|" + nextID + "|" + strDateISO + "|" + currLat + "|" + currLon + "|" + station + "|" + sampleType + "|" + value + "|" + unit + "|" + nSats + "|" + nAcc + "|" + sampleComment + "\n";

                File file = new File (projDir, tvProjName.getText().toString() + ".txt");
                FileOutputStream out = new FileOutputStream(file, true);
                out.write(line.getBytes());
                out.flush();
                out.close();

                Toast.makeText(SampleRegistrationActivity.this, "ID " + dataID + " " + nextID + " stored as " + sampleType, Toast.LENGTH_LONG).show();

                nextId++;
                tvNextID.setText(String.valueOf(nextId));

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
                Toast.makeText(SampleRegistrationActivity.this, ErrorString("You must add a Phone ID under settings"), Toast.LENGTH_LONG).show();
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
                    Toast.makeText(SampleRegistrationActivity.this, ErrorString("Field 'Project name' is required"), Toast.LENGTH_LONG).show();
                    return true;
                }

                for(int i=0; i<adapter.getCount(); i++) {
                    String s = (String)adapter.getItem(i);
                    if(s.equalsIgnoreCase(strNewProj)) {
                        Toast.makeText(SampleRegistrationActivity.this, ErrorString("Project already exists"), Toast.LENGTH_LONG).show();
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
            Toast.makeText(SampleRegistrationActivity.this, ErrorString("No provider enabled"), Toast.LENGTH_SHORT).show();
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

        if(location.getAccuracy() > 150f)
            return;

        accuracy = location.getAccuracy();
        tvCurrAcc.setText("Acc: " + String.valueOf(accuracy) + "m");

        Date now = new Date();
        double lat = (double) (location.getLatitude());
        double lng = (double) (location.getLongitude());

        tvCurrGPSDate.setText(DateFormat.getDateTimeInstance().format(now));
        tvCurrLat.setText(String.valueOf(lat));
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
        lstProj.setAdapter(adapter); // FIXME: probably overkill
    }

    public void exitApp() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}
