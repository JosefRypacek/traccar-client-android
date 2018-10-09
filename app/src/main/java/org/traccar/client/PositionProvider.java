/*
 * Copyright 2013 - 2018 Anton Tananaev (anton.tananaev@gmail.com), Josef Rypacek (j.rypacek@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.client;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;


public class PositionProvider implements LocationListener, ChargingManager.ChargingHandler {

    private static final String TAG = PositionProvider.class.getSimpleName();

    private static final int MINIMUM_INTERVAL = 1000;

    public interface PositionListener {
        void onPositionUpdate(Position position);
    }

    private final PositionListener listener;
    private ChargingManager chargingManager;
    private TemperatureManager temperatureManager;

    private final Context context;
    private SharedPreferences preferences;
    private LocationManager locationManager;

    private String deviceId;
    private long interval_battery;
    private long interval_charging;
    private long interval;
    private double distance;
    private double angle;
    private boolean distance_angle_charging;
    private boolean power_as_ignition;
    private boolean temperatureMonitoring;

    private boolean distance_angle_allowed;

    private Location lastLocation;

    public PositionProvider(Context context, PositionListener listener) {
        this.context = context;
        this.listener = listener;

        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        preferences = PreferenceManager.getDefaultSharedPreferences(context);

        deviceId = preferences.getString(MainFragment.KEY_DEVICE, "undefined");
        interval_battery = Long.parseLong(preferences.getString(MainFragment.KEY_INTERVAL, "600")) * 1000;
        interval_charging = Long.parseLong(preferences.getString(MainFragment.KEY_INTERVAL_CHARGING, "60")) * 1000;
        distance = Integer.parseInt(preferences.getString(MainFragment.KEY_DISTANCE, "0"));
        angle = Integer.parseInt(preferences.getString(MainFragment.KEY_ANGLE, "0"));
        distance_angle_charging = preferences.getBoolean(MainFragment.KEY_DISTANCE_ANGLE_CHARGING, false);
        power_as_ignition = preferences.getBoolean(MainFragment.KEY_POWER_AS_IGNITION, false);
        temperatureMonitoring = preferences.getBoolean(MainFragment.KEY_TEMPERATURE_MONITORING, false);

        if (interval_charging > 0 || distance_angle_charging || power_as_ignition) {
            chargingManager = new ChargingManager(context, this);
        }
        if(temperatureMonitoring) {
            temperatureManager = new TemperatureManager(context);
        }
    }

    @Override
    public void onChargingUpdate(boolean isCharging) {
        int chargingString = R.string.status_power_disconnected;
        if (isCharging) {
            chargingString = R.string.status_power_connected;
        }
        StatusActivity.addMessage(context.getString(chargingString));

//        LocationServices.FusedLocationApi.removeLocationUpdates(apiClient, this);
//        setupChargingVariables(isCharging);
//        onConnected();

//        can use this way????
        stopUpdates();
        startUpdates();
    }


    private void setupChargingVariables(boolean isCharging) {
        distance_angle_allowed = distance_angle_charging ? isCharging : true;
        interval = interval_charging > 0 && isCharging ? interval_charging : interval_battery;
        if (power_as_ignition) {
            lastLocation = null; // Clear lastLocation to send update ASAP
        }
    }

    @SuppressLint("MissingPermission")
    public void startUpdates() {
        boolean isCharging = chargingManager != null ? chargingManager.isCharging() : false;
        setupChargingVariables(isCharging);
        if (chargingManager != null) {
            chargingManager.start();
        }
        if(temperatureManager != null) {
            temperatureManager.start();
        }

        try {
            locationManager.requestLocationUpdates(
                    distance_angle_allowed && (distance > 0 || angle > 0) ? MINIMUM_INTERVAL : interval, 0,
                    getCriteria(preferences.getString(MainFragment.KEY_ACCURACY, "medium")),
                    this, Looper.myLooper());
        } catch (RuntimeException e) {
            Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public static Criteria getCriteria(String accuracy) {
        Criteria criteria = new Criteria();
        switch (accuracy) {
            case "high":
                criteria.setHorizontalAccuracy(Criteria.ACCURACY_HIGH);
                criteria.setPowerRequirement(Criteria.POWER_HIGH);
                break;
            case "low":
                criteria.setHorizontalAccuracy(Criteria.ACCURACY_LOW);
                criteria.setPowerRequirement(Criteria.POWER_LOW);
                break;
            default:
                criteria.setHorizontalAccuracy(Criteria.ACCURACY_MEDIUM);
                criteria.setPowerRequirement(Criteria.POWER_MEDIUM);
                break;
        }
        return criteria;
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location != null && (lastLocation == null
                || location.getTime() - lastLocation.getTime() >= interval
                || distance_angle_allowed && distance > 0 && location.distanceTo(lastLocation) >= distance
                || distance_angle_allowed && angle > 0 && Math.abs(location.getBearing() - lastLocation.getBearing()) >= angle)) {
            Log.i(TAG, "location new");
            lastLocation = location;
            listener.onPositionUpdate(new Position(deviceId, location, getBatteryLevel(context), getIgnitionStatus(), getTemperature()));
        } else {
            Log.i(TAG, location != null ? "location ignored" : "location nil");
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    public void stopUpdates() {
        if (chargingManager != null) {
            chargingManager.stop();
        }
        if(temperatureManager != null) {
            temperatureManager.stop();
        }

        locationManager.removeUpdates(this);
    }

    public static double getBatteryLevel(Context context) {
        Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryIntent != null) {
            int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 1);
            return (level * 100.0) / scale;
        }
        return 0;
    }

    private int getIgnitionStatus() {
        if (!power_as_ignition) {
            return -1;
        }
        return chargingManager.isCharging() ? 1 : 0;
    }

    private float getTemperature() {
        if (!temperatureMonitoring) {
            return Float.NaN;
        }
        return temperatureManager.getLastTemperature();
    }

}
