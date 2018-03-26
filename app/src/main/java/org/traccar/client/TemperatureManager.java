/*
 * Copyright 2018 Josef Rypacek (j.rypacek@gmail.com)
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
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;

public class TemperatureManager implements SensorEventListener {

    private static final String TAG = TemperatureManager.class.getSimpleName();

    private Context context;

    private SensorManager mSensorManager;
    private Sensor mTemperature;

    private float lastTemperature = Float.NaN;


    public TemperatureManager(Context context) {
        this.context = context;

        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mTemperature = mSensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);

        if(mTemperature == null) {
            StatusActivity.addMessage("Ambient temperature sensor is missing! Using battery temperature.");
        }else{
            StatusActivity.addMessage("Ambient temperature sensor is available!");
        }
    }

    public void start() {
        if(mTemperature != null) {
            mSensorManager.registerListener(this, mTemperature, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    public void stop() {
        if(mTemperature != null) {
            mSensorManager.unregisterListener(this);
        }
    }

    public float getLastTemperature(){
        if(mTemperature == null){
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, filter);
            return batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10f;
        }
        return lastTemperature;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        lastTemperature = event.values[0];
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}
