package com.wellsense.app;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/** Reads phone usage (app foreground time, app opens, night use) via Android UsageStats. */
@CapacitorPlugin(name = "UsageTracker")
public class UsageTrackerPlugin extends Plugin {

    private boolean granted() {
        AppOpsManager a = (AppOpsManager) getContext().getSystemService(Context.APP_OPS_SERVICE);
        int m = a.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getContext().getPackageName());
        return m == AppOpsManager.MODE_ALLOWED;
    }

    @PluginMethod
    public void hasAccess(PluginCall call) {
        JSObject o = new JSObject();
        o.put("granted", granted());
        call.resolve(o);
    }

    @PluginMethod
    public void openSettings(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    /** Returns {foregroundMillis, appOpenCount} between start and end. */
    private long[] calc(long start, long end) {
        if (end <= start) return new long[]{0, 0};
        UsageStatsManager u = (UsageStatsManager) getContext().getSystemService(Context.USAGE_STATS_SERVICE);
        UsageEvents ev = u.queryEvents(start, end);
        UsageEvents.Event x = new UsageEvents.Event();
        Map<String, Long> open = new HashMap<>();
        long total = 0, opens = 0;
        String self = getContext().getPackageName();
        while (ev.hasNextEvent()) {
            ev.getNextEvent(x);
            String p = x.getPackageName();
            if (p.equals(self)) continue; // ignore WellSense itself
            int t = x.getEventType();
            if (t == UsageEvents.Event.ACTIVITY_RESUMED) { open.put(p, x.getTimeStamp()); opens++; }
            else if (t == UsageEvents.Event.ACTIVITY_PAUSED) {
                Long st = open.remove(p);
                if (st != null) total += x.getTimeStamp() - st;
            }
        }
        for (Long st : open.values()) total += Math.max(0, end - st);
        return new long[]{total, opens};
    }

    @PluginMethod
    public void getToday(PluginCall call) {
        if (!granted()) { call.reject("Usage access not granted"); return; }
        long now = System.currentTimeMillis();
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        long midnight = c.getTimeInMillis();
        long[] day = calc(midnight, now);
        // Night window: yesterday 23:00 -> today 05:00 (capped at now)
        long[] night = calc(midnight - 3600000L, Math.min(now, midnight + 5 * 3600000L));
        double hours = Math.max(1.0, (now - midnight) / 3600000.0);
        JSObject o = new JSObject();
        o.put("screen", Math.round(day[0] / 360000.0) / 10.0); // hours, 1 decimal
        o.put("switches", Math.round(day[1] / hours));         // app opens per hour
        o.put("night", Math.round(night[0] / 60000.0));        // minutes
        call.resolve(o);
    }
}
