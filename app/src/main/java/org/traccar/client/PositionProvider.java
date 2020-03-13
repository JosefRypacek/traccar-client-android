/*
<<<<<<< HEAD
 * Copyright 2013 - 2018 Anton Tananaev (anton.tananaev@gmail.com), Josef Rypacek (j.rypacek@gmail.com)
=======
 * Copyright 2013 - 2019 Anton Tananaev (anton@traccar.org)
>>>>>>> upstream/master
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

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.BatteryManager;
import android.preference.PreferenceManager;
import android.util.Log;

public abstract class PositionProvider implements ChargingManager.ChargingHandler {

    private static final String TAG = PositionProvider.class.getSimpleName();

    protected static final int MINIMUM_INTERVAL = 1000;

    public interface PositionListener {
        void onPositionUpdate(Position position);
        void onPositionError(Throwable error);
    }

    protected final PositionListener listener;

    protected ChargingManager chargingManager;
    protected TemperatureManager temperatureManager;

    protected final Context context;
    protected SharedPreferences preferences;

    protected String deviceId;
    protected long interval;
    protected double distance;
    protected double angle;

    protected long interval_battery;
    protected long interval_charging;
    protected boolean distance_angle_charging;
    protected boolean power_as_ignition;
    protected boolean temperatureMonitoring;
    protected boolean distance_angle_allowed;

    protected Location lastLocation;

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
        power_as_ignition = preferences.getBoolean(MainFragment.KEY_POWER_AS_IGNITION, false);
        temperatureMonitoring = preferences.getBoolean(MainFragment.KEY_TEMPERATURE_MONITORING, false);

        if (interval_charging > 0 || distance_angle_charging || power_as_ignition) {
            chargingManager = new ChargingManager(context, this);
        }
        if(temperatureMonitoring) {
            temperatureManager = new TemperatureManager(context);
        }
    }


    protected void setupChargingVariables(boolean isCharging) {
        distance_angle_allowed = distance_angle_charging ? isCharging : true;
        interval = interval_charging > 0 && isCharging ? interval_charging : interval_battery;
        if (power_as_ignition) {
            lastLocation = null; // Clear lastLocation to send update ASAP
        }
    }

    public abstract void startUpdates();

    public abstract void stopUpdates();

    public abstract void requestSingleLocation();

    protected void processLocation(Location location) {
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

    protected static double getBatteryLevel(Context context) {
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
