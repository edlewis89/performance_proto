package com.livingpages.cellinfoproto;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.telephony.CellInfo;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;
import com.livingpages.cellinfoproto.location.DeviceLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    ListView listView ;

    // switch off by default
    private Boolean suppress_reporting = false;

    private static final String TEST_DRIVE_MODE = "TestDriveMode";
    private static final String AUTOMATIC_MODE = "AutomaticMode";

//    private String reporting_url ="https://livingpages.com/lpapi-android/v0/cellinfo";
    private String reporting_url ="http://ec2-54-213-12-229.us-west-2.compute.amazonaws.com/api/v1/upload";

    // default mode
    private String reporting_mode_setting = TEST_DRIVE_MODE;

    private Boolean reporting_mode = false;
    // default one seconf poll rate
    private Integer polling_rate_seconds = 1;
    private Integer poll_count = 0;

    private DeviceLocation deviceLocation = new DeviceLocation();
//    private ServiceStateEntity _serviceStateEntity = new ServiceStateEntity();

    static final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 0;

    private long tLastPoll = 0;

    private TelephonyManager tManager;

    private AlertDialog mDialog;

    private LocationListener locationListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        int PERMISSION_ALL = 1;

        // put all needed request permissions here
        String[] PERMISSIONS = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.READ_PHONE_STATE};

        if(!hasPermissions(this, PERMISSIONS)){
           ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
        }

        deviceLocation.setContext(this);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        final Switch master_poll_switch = (Switch) findViewById(R.id.polling_switch1);
        // switch off by default
        master_poll_switch.setChecked(false);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                master_poll_switch.setChecked(true);
                suppress_reporting = master_poll_switch.isChecked();
                Snackbar.make(view, "Reporting Off", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        master_poll_switch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
              String switch_label = "Reporting On";
              suppress_reporting = master_poll_switch.isChecked();
              if (suppress_reporting) {
                  switch_label = "Reporting Off";
              }
              Snackbar.make(view, switch_label, Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            }
        });

        final Switch reporting_mode_switch = (Switch) findViewById(R.id.reporting_mode_switch);

        // switch off by default
        reporting_mode_switch.setChecked(false);

        reporting_mode_switch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {String reporting_mode_label = "Test Drive Mode On";
                reporting_mode_setting = TEST_DRIVE_MODE;

                reporting_mode = reporting_mode_switch.isChecked();
                if (reporting_mode) {
                    reporting_mode_setting = AUTOMATIC_MODE;
                    reporting_mode_label = "Automatic Mode On";
                }
                Snackbar.make(view, reporting_mode_label, Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        // the possible polling intervals in seconds
        Integer[] items = new Integer[]{1, 15, 60};

        final Spinner poll_rate = (Spinner) findViewById(R.id.polling_rate_spinner);
        ArrayAdapter<Integer> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        poll_rate.setAdapter(adapter);

        poll_rate.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                polling_rate_seconds = (Integer)poll_rate.getSelectedItem();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                //Another interface callback
            }
        });

        // register the phone state listener
//        _registerServiceStateListeners(this);

//        https://www.sitepoint.com/phone-callbacks-in-android-using-telephonymanager/
        this.tManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        // TODO: understand servce state events better
//        this.tManager.listen(new PhoneCallback(), PhoneStateListener.LISTEN_CALL_STATE
//                | PhoneStateListener.LISTEN_DATA_ACTIVITY
//                | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE );

        // start the main loop
        _startLocationPolling();

        _doLoop();
    }

    private void _doLoop() {

        CountDownTimer countDownTimer = new CountDownTimer(60000, 1000) {
            public void onTick(long millisUntilFinished) {

                // TODO find a way to put all here? or at least drive test mode?
                // do something every second here
                String _do = "loop";
            }

            public void onFinish() {
                this.start(); // restart again.
            }

        }.start();

    }

    private void _startLocationPolling() {

        final LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        final TextView device_speed_text = (TextView) findViewById(R.id.device_speed);

        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }

        } else {

            this.locationListener = new LocationListener() {

                @Override
                public void onLocationChanged(Location location) {

                    Boolean report = false;

                    // check master switch
                    if (!suppress_reporting) {
                        if (Objects.equals(reporting_mode_setting, TEST_DRIVE_MODE)) {
                            if (!_suppressTestDriveModePolling()) {
                                // poll
                                report = true;
                            }
                        }

                        if (Objects.equals(reporting_mode_setting, AUTOMATIC_MODE)) {
                            if (!_suppressAutomaticModePolling(location)) {
                                report = true;
                            }
                        }
                    }

                    // TODO: test location accuracy?
                    // If this location does not have a horizontal accuracy, then 0.0 is returned. All locations generated by the LocationManager include horizontal accuracy

                    if (report) {
                        _pollDeviceinfo();
                    }

                    // get MPH
//                    Float speed = location.getSpeed();
//                    Float converstion = (float) 2.237;
                    Float mphFloat = deviceLocation.getSpeedMPH(location);

                    // one decimal
                    String mphS = String.format("%.1f", mphFloat);
                    // no decimals for MPH label
//                    String mphS = String.format("%.0f", mphF);
                    device_speed_text.setText(mphS);

                    String jsonLocation = deviceLocation.wrapLocationInJson(location);

                    // this stops Location listening to give chance to reset
                    //locationManager.removeUpdates(this);
                }

                public void onStatusChanged(String provider, int status, Bundle extras) {}

                public void onProviderEnabled(String provider) {}

                public void onProviderDisabled(String provider) {}
            };

            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            List<String> lProviders = locationManager.getProviders(false);
            for(int i=0; i<lProviders.size(); i++){
                Log.d("LocationActivity", lProviders.get(i));
            }

            // this returned "passive" on Lisas phone so need to look into why
            String bestProvider = locationManager.getBestProvider(criteria, true);
//            String bestProvider = GPS_PROVIDER;

            locationManager.requestLocationUpdates(bestProvider, 1000, 0, this.locationListener);
        }
    }

    /**
     * logic for Automatic Mode
     a method to help us determine if we should be polling
     */
    private Boolean _suppressAutomaticModePolling(Location location) {
        Boolean suppress = false;

        long tNow = System.currentTimeMillis();
        long tDelta = tNow - tLastPoll;

        // default
        // when stationary, report every 60 seconds
        // TODO: convert to mph
        int reporting_interval = 60 * 1000;
        Float speed = deviceLocation.getSpeedMPH(location);

        // 15 seconds from above zero to 5 mph
        if (speed > 0 && speed < 5) {
           reporting_interval = 15 * 1000;
        }

        // every 5 seconds from 5 to 15 mph
        if ((speed > 5) && (speed < 20)) {
           reporting_interval = 5 * 1000;
        }

        // every second above 20 mph
        if (speed > 20) {
           reporting_interval = 1000;
        }

        if (tDelta < reporting_interval) {
           suppress = true;
        }

//        double elapsedSeconds = tDelta / 1000.0;

        return suppress;
    }

    /**
     * logic for Test Drive Mode
    a method to help us determine if we should be polling
     */
    private Boolean _suppressTestDriveModePolling() {
        Boolean suppress = false;

        long tNow = System.currentTimeMillis();
        long tDelta = tNow - tLastPoll;

        // convert to micro seconds
        if (tDelta < (polling_rate_seconds * 1000)) {
            suppress = true;
        }

//        double elapsedSeconds = tDelta / 1000.0;

        return suppress;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    private void _pollDeviceinfo() {

//        _getServiceStateInfo();

        final TextView poll_count_text = (TextView) findViewById(R.id.poll_count_text);
        final TextView device_speed_text = (TextView) findViewById(R.id.device_speed);

                        /* do what you need to do */
        String cellInfoJson = _pollAllCellInfo();
        String device_id = _getDeviceId();

        String line1number = getLine1Number();

        Location loc = deviceLocation.getLocation();
        Float speed = loc.getSpeed();
        String jsonLocation = deviceLocation.wrapLocationInJson(loc);

        _postDeviceInfo(device_id, line1number, cellInfoJson, jsonLocation);

        // increment poll count
        poll_count++;

        // add poll count to text field
        String count_str = String.valueOf(poll_count);
        poll_count_text.setText(count_str);
        device_speed_text.setText(speed.toString());

        // log the time
        tLastPoll = System.currentTimeMillis();
    }

    private String _pollAllCellInfo() {

        listView = (ListView) findViewById(R.id.mainListView);

        String jsonCellInfo = "";
        Gson gson = new Gson();

        List<CellInfo> cellInfo = this.tManager.getAllCellInfo();

        // check in case network is off
        if (cellInfo != null) {
            ArrayAdapter<String> listAdapter = new ArrayAdapter(this, R.layout.cell_info_row, (List) cellInfo);
            listView.setAdapter(listAdapter);

            // maybe send this to an api :)?
            jsonCellInfo = gson.toJson(cellInfo);
            return jsonCellInfo;
        } else {
            return "[]";
        }
    }

    private String _getDeviceId() {
//        Log.d("ID", "Device ID : " + this.tManager.getDeviceId());
        return this.tManager.getDeviceId();
    }

    private String getLine1Number() {
        return this.tManager.getLine1Number();
    }

    /**
     * for getting ServiceState from device
     */
    private void _getServiceStateInfo() {
        ServiceState sState = new ServiceState();
//        String service_state = sState.toString();
    }

    private void _postDeviceInfo(final String device_id, final String line1number, final String cellInfoJson, final String jsonLocation) {

        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(this);

        // Request a string response from the provided URL.

        StringRequest stringRequest = new StringRequest(Request.Method.POST, reporting_url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {

//                        String resp = response.substring(0,500);
                        // Display the first 500 characters of the response string.
//                        mTextView.setText("Response is: "+ response.substring(0,500));
                    }
                }, new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
//                mTextView.setText("That didn't work!");
            }
        })
        {
            @Override
            protected Map<String, String> getParams()
            {
                Map<String, String>  params = new HashMap<>();
                params.put("device_id", device_id);
                params.put("line1number", line1number);
                params.put("cellinfo", cellInfoJson);
                params.put("location", jsonLocation);
                params.put("ping", "{}");

                return params;
            }
        };

        // Add the request to the RequestQueue.
        queue.add(stringRequest);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
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
            return true;
        }

        if (id == R.id.action_about) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.about_dialog_title);
            builder.setMessage(R.string.about_dialog_text);
            builder.setCancelable(false);
            builder.setPositiveButton(R.string.ok_label,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {

                        }
                    }
            );

            mDialog = builder.show();
        }

        return super.onOptionsItemSelected(item);
    }

    public static boolean hasPermissions(Context context, String... permissions) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

}