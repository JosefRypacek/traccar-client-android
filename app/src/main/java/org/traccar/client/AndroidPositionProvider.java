/*
 * Copyright 2019 Anton Tananaev (anton@traccar.org)
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
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;

public class AndroidPositionProvider extends PositionProvider implements LocationListener {

    private LocationManager locationManager;
    private String provider;

    public AndroidPositionProvider(Context context, PositionListener listener) {
        super(context, listener);
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        provider = getProvider(preferences.getString(MainFragment.KEY_ACCURACY, "medium"));
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
                    provider, distance_angle_allowed && (distance > 0 || angle > 0) ? MINIMUM_INTERVAL : interval, 0, this);
        } catch (RuntimeException e) {
            listener.onPositionError(e);
        }
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

    @SuppressLint("MissingPermission")
    public void requestSingleLocation() {
        try {
            Location location = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
            if (location != null) {
                listener.onPositionUpdate(new Position(deviceId, location, getBatteryLevel(context)));
            } else {
                locationManager.requestSingleUpdate(provider, new LocationListener() {
                    @Override
                    public void onLocationChanged(Location location) {
                        listener.onPositionUpdate(new Position(deviceId, location, getBatteryLevel(context)));
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
                }, Looper.myLooper());
            }
        } catch (RuntimeException e) {
            listener.onPositionError(e);
        }
    }

    private static String getProvider(String accuracy) {
        switch (accuracy) {
            case "high":
                return LocationManager.GPS_PROVIDER;
            case "low":
                return LocationManager.PASSIVE_PROVIDER;
            default:
                return LocationManager.NETWORK_PROVIDER;
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        processLocation(location);
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

    @Override
    public void onChargingUpdate(boolean isCharging) {
        int chargingString = R.string.status_power_disconnected;
        if (isCharging) {
            chargingString = R.string.status_power_connected;
        }
        StatusActivity.addMessage(context.getString(chargingString));

        stopUpdates();
        startUpdates();
    }

}
