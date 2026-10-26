package com.wellsense.app;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(UsageTrackerPlugin.class);
        registerPlugin(HealthConnectPlugin.class); // must be before super.onCreate
        super.onCreate(savedInstanceState);
    }
}
