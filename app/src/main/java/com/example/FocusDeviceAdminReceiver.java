package com.example;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/**
 * Custom Device Admin receiver to provide Anti-Uninstall capabilities.
 * When activated, it restricts spontaneous uninstallation of FocusVault.
 * Designed to compile cleanly in mobile phone AIDE editors.
 */
public class FocusDeviceAdminReceiver extends DeviceAdminReceiver {

    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        Toast.makeText(context, "Anti-Uninstall Protection Shield Active!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        // Show strict warning when trying to disable Device Admin
        StatsManager stats = StatsManager.getInstance(context);
        if (stats.isStrictModeEnabled()) {
            return "Strict Focus Mode is currently engaged. Disabling this permissions will violate your study promises!";
        }
        return "Are you sure you want to disable Anti-Uninstall protection? This allows immediate uninstallation.";
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        Toast.makeText(context, "Anti-Uninstall Protection Shield Offline!", Toast.LENGTH_SHORT).show();
    }
}
