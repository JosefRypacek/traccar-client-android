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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;

public class ChargingManager extends BroadcastReceiver {

    private static final String TAG = ChargingManager.class.getSimpleName();

    private Context context;
    private ChargingHandler handler;
    private BatteryManager batteryManager;

    public ChargingManager(Context context, ChargingHandler handler) {
        this.context = context;
        this.handler = handler;
        batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
    }

    public interface ChargingHandler {
        void onChargingUpdate(boolean isCharging);
    }

    public boolean isCharging(){
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, filter);
        int pluggedStatus = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        return pluggedStatus > 0;
    }

    public void start() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        context.registerReceiver(this, filter);
    }

    public void stop() {
        context.unregisterReceiver(this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        boolean isCharging = false;
        if (intent.getAction().equals(Intent.ACTION_POWER_CONNECTED)){
            isCharging = true;
        }

        if (handler != null) {
            Log.i(TAG, "power " + (isCharging ? "connected" : "disconnected"));
            handler.onChargingUpdate(isCharging);
        }
    }

}
