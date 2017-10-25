package com.livingpages.cellinfoproto.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.support.v4.app.ActivityCompat;

import com.google.gson.Gson;

/**
 * Created by jlew on 10/4/2017.
 */

public class DeviceLocation {

    LocationManager locationManager;
    private Context mContext;

    public void setContext(Context mContext) {
       this.mContext = mContext;
    }

    public Location getLocation() {

        LocationManager locationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        String bestProvider = locationManager.getBestProvider(criteria, true);

        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
//            return void;
        }
        Location loc = locationManager.getLastKnownLocation(bestProvider);
        return loc;
    }

    /**
     * wrap the location in json
     * @param loc
     * @return
     */
    public String wrapLocationInJson(Location loc) {
        Gson gson = new Gson();
        String jsonLocation = "";
        // maybe send this to an api :)?
        jsonLocation = gson.toJson(loc);
        return jsonLocation;
    }

    /**
     * get speed MPH
     * @param loc
     * @return
     */
    public Float getSpeedMPH(Location loc) {
        Float speed = loc.getSpeed();
        Float converstion = (float) 2.237;
        Float mphFloat = speed * converstion;
        return mphFloat;
    }
}
