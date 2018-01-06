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
import android.location.Location;
import android.os.BatteryManager;
import android.preference.PreferenceManager;
import android.util.Log;

import com.mapzen.android.lost.api.LocationListener;
import com.mapzen.android.lost.api.LocationRequest;
import com.mapzen.android.lost.api.LocationServices;
import com.mapzen.android.lost.api.LostApiClient;

public class PositionProvider implements LostApiClient.ConnectionCallbacks, LocationListener, ChargingManager.ChargingHandler {

    private static final String TAG = PositionProvider.class.getSimpleName();

    private static final int MINIMUM_INTERVAL = 1000;

    public interface PositionListener {
        void onPositionUpdate(Position position);
    }

    private final PositionListener listener;
    private ChargingManager chargingManager;

    private final Context context;
    private SharedPreferences preferences;
    private LostApiClient apiClient;

    private String deviceId;
    private long interval_battery;
    private long interval_charging;
    private long interval;
    private double distance;
    private double angle;
    private boolean distance_angle_charging;

    private boolean distance_angle_allowed;

    private Location lastLocation;

    public PositionProvider(Context context, PositionListener listener) {
        this.context = context;
        this.listener = listener;

        preferences = PreferenceManager.getDefaultSharedPreferences(context);

        deviceId = preferences.getString(MainFragment.KEY_DEVICE, "undefined");
        interval_battery = Long.parseLong(preferences.getString(MainFragment.KEY_INTERVAL, "600")) * 1000;
        interval_charging = Long.parseLong(preferences.getString(MainFragment.KEY_INTERVAL_CHARGING, "60")) * 1000;
        distance = Integer.parseInt(preferences.getString(MainFragment.KEY_DISTANCE, "0"));
        angle = Integer.parseInt(preferences.getString(MainFragment.KEY_ANGLE, "0"));
        distance_angle_charging = preferences.getBoolean(MainFragment.KEY_DISTANCE_ANGLE_CHARGING, false);

        if(interval_charging > 0 || distance_angle_charging) {
            chargingManager = new ChargingManager(context, this);
        }
    }

    @Override
    public void onChargingUpdate(boolean isCharging) {
        int chargingString = R.string.status_power_disconnected;
        if(isCharging) {
            chargingString = R.string.status_power_connected;
        }
        StatusActivity.addMessage(context.getString(chargingString));

        apiClient.disconnect();
        setupChargingVariables(isCharging);
        apiClient.connect();
    }


    private void setupChargingVariables(boolean isCharging){
        distance_angle_allowed = distance_angle_charging ? isCharging : true;
        interval = interval_charging > 0 && isCharging ? interval_charging : interval_battery;
    }

    public void startUpdates() {
        boolean isCharging = chargingManager != null ? chargingManager.isCharging() : false;
        setupChargingVariables(isCharging);
        if(chargingManager != null) {
            chargingManager.start();
        }

        apiClient = new LostApiClient.Builder(context).addConnectionCallbacks(this).build();
        apiClient.connect();
    }

    private int getPriority(String accuracy) {
        switch (accuracy) {
            case "high":
                return LocationRequest.PRIORITY_HIGH_ACCURACY;
            case "low":
                return LocationRequest.PRIORITY_LOW_POWER;
            default:
                return LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY;
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onConnected() {
        LocationRequest request = LocationRequest.create()
                .setPriority(getPriority(preferences.getString(MainFragment.KEY_ACCURACY, "medium")))
                .setInterval(distance_angle_allowed && (distance > 0 || angle > 0) ? MINIMUM_INTERVAL : interval);

        LocationServices.FusedLocationApi.requestLocationUpdates(apiClient, request, this);
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location != null && (lastLocation == null
                || location.getTime() - lastLocation.getTime() >= interval
                || distance_angle_allowed && distance > 0 && DistanceCalculator.distance(location.getLatitude(), location.getLongitude(), lastLocation.getLatitude(), lastLocation.getLongitude()) >= distance
                || distance_angle_allowed && angle > 0 && Math.abs(location.getBearing() - lastLocation.getBearing()) >= angle)) {
            Log.i(TAG, "location new");
            lastLocation = location;
            listener.onPositionUpdate(new Position(deviceId, location, getBatteryLevel(context)));
        } else {
            Log.i(TAG, location != null ? "location ignored" : "location nil");
        }
    }

    @Override
    public void onConnectionSuspended() {
        Log.i(TAG, "lost client suspended");
    }

    public void stopUpdates() {
        if(chargingManager != null) {
            chargingManager.stop();
        }

        LocationServices.FusedLocationApi.removeLocationUpdates(apiClient, this);

        apiClient.disconnect();
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

}
