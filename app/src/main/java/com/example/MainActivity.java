package com.example;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import java.util.List;

/**
 * MainActivity manages the FocusVault dashboard, toggle controls, 
 * device permissions, and local stats display.
 * Designed using traditional Android Views and Java for full AIDE compatibility.
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQ_CODE_STRICT_DISABLE = 101;
    private static final int REQ_CODE_BLOCKER_DISABLE = 102;
    private static final int REQ_CODE_DEVICE_ADMIN = 103;

    private TextView txtShieldTitle;
    private TextView txtShieldDesc;
    private TextView txtStatBlocked;
    private TextView txtStatSaved;
    private Button btnToggleAccessibility;
    private Button btnLaunchTimer;
    private Button btnToggleAdmin;
    private TextView txtAdminStatus;

    private TextView txtMostDistracting;
    private TextView txtCountYoutube;
    private TextView txtCountInstagram;
    private TextView txtCountTiktok;
    private TextView txtCountFacebook;

    private android.widget.ProgressBar progressYoutube;
    private android.widget.ProgressBar progressInstagram;
    private android.widget.ProgressBar progressTiktok;
    private android.widget.ProgressBar progressFacebook;

    private TextView btnResetStats;

    private SwitchCompat switchGlobalBlocker;
    private SwitchCompat switchStrictMode;
    private SwitchCompat switchBlockTiktok;
    private SwitchCompat switchBlockYoutube;
    private SwitchCompat switchBlockInstagram;
    private SwitchCompat switchBlockFacebook;

    private StatsManager statsManager;
    private DevicePolicyManager devicePolicyManager;
    private ComponentName adminComponent;

    // Temporary trackers to prevent toggle-loops when prompting password check
    private boolean isRevertingToggle = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statsManager = StatsManager.getInstance(this);
        devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, FocusDeviceAdminReceiver.class);

        initializeViews();
        setupListeners();
    }

    private void initializeViews() {
        txtShieldTitle = findViewById(R.id.txt_shield_title);
        txtShieldDesc = findViewById(R.id.txt_shield_desc);
        txtStatBlocked = findViewById(R.id.txt_stat_blocked);
        txtStatSaved = findViewById(R.id.txt_stat_saved);
        
        btnToggleAccessibility = findViewById(R.id.btn_toggle_accessibility);
        btnLaunchTimer = findViewById(R.id.btn_launch_timer);
        btnToggleAdmin = findViewById(R.id.btn_toggle_admin);
        txtAdminStatus = findViewById(R.id.txt_admin_status);

        txtMostDistracting = findViewById(R.id.txt_most_distracting);
        txtCountYoutube = findViewById(R.id.txt_count_youtube);
        txtCountInstagram = findViewById(R.id.txt_count_instagram);
        txtCountTiktok = findViewById(R.id.txt_count_tiktok);
        txtCountFacebook = findViewById(R.id.txt_count_facebook);

        progressYoutube = findViewById(R.id.progress_youtube);
        progressInstagram = findViewById(R.id.progress_instagram);
        progressTiktok = findViewById(R.id.progress_tiktok);
        progressFacebook = findViewById(R.id.progress_facebook);

        btnResetStats = findViewById(R.id.btn_reset_stats);

        switchGlobalBlocker = findViewById(R.id.switch_global_blocker);
        switchStrictMode = findViewById(R.id.switch_strict_mode);
        switchBlockTiktok = findViewById(R.id.switch_block_tiktok);
        switchBlockYoutube = findViewById(R.id.switch_block_youtube);
        switchBlockInstagram = findViewById(R.id.switch_block_instagram);
        switchBlockFacebook = findViewById(R.id.switch_block_facebook);

        // Load active configurations
        switchGlobalBlocker.setChecked(statsManager.isGlobalBlockerEnabled());
        switchStrictMode.setChecked(statsManager.isStrictModeEnabled());
        switchBlockTiktok.setChecked(statsManager.isBlockTiktokEnabled());
        switchBlockYoutube.setChecked(statsManager.isBlockYoutubeEnabled());
        switchBlockInstagram.setChecked(statsManager.isBlockInstagramEnabled());
        switchBlockFacebook.setChecked(statsManager.isBlockFacebookEnabled());
    }

    private void setupListeners() {
        // Toggle helper
        btnToggleAccessibility.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
                Toast.makeText(MainActivity.this, "Locate 'FocusVault' in the downloads lists and turn on its shield.", Toast.LENGTH_LONG).show();
            }
        });

        // Launch timer page
        btnLaunchTimer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent timerIntent = new Intent(MainActivity.this, TimerActivity.class);
                startActivity(timerIntent);
            }
        });

        // Reset statistics
        btnResetStats.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                statsManager.resetStats();
                updateStats();
                Toast.makeText(MainActivity.this, "Distraction statistics have been reset.", Toast.LENGTH_SHORT).show();
            }
        });

        // Toggle Device Admin security
        btnToggleAdmin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (devicePolicyManager.isAdminActive(adminComponent)) {
                    // Deactivate admin (check strict mode first)
                    if (statsManager.isStrictModeEnabled()) {
                        Intent pinIntent = new Intent(MainActivity.this, PinActivity.class);
                        pinIntent.putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_VERIFY);
                        startActivityForResult(pinIntent, REQ_CODE_DEVICE_ADMIN);
                    } else {
                        devicePolicyManager.removeActiveAdmin(adminComponent);
                        updateSecurityViews();
                    }
                } else {
                    // Activate Device Admin
                    Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                    intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
                    intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Securing FocusVault prevents impulse uninstallations or self-sabotage.");
                    startActivity(intent);
                }
            }
        });

        // Global Blocker Switch with Strict PIN Lock Check
        switchGlobalBlocker.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isRevertingToggle) return;

                if (isChecked) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(MainActivity.this)) {
                        Toast.makeText(MainActivity.this, "Overlay permission is vital to show shields. Please enable it.", Toast.LENGTH_LONG).show();
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                        startActivity(intent);
                        
                        isRevertingToggle = true;
                        switchGlobalBlocker.setChecked(false);
                        isRevertingToggle = false;
                        return;
                    }
                }

                if (!isChecked && statsManager.isStrictModeEnabled()) {
                    // Impulsive bypass block! Prompt passcode validation
                    isRevertingToggle = true;
                    switchGlobalBlocker.setChecked(true); // temporary lock
                    isRevertingToggle = false;

                    Intent intent = new Intent(MainActivity.this, PinActivity.class);
                    intent.putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_VERIFY);
                    startActivityForResult(intent, REQ_CODE_BLOCKER_DISABLE);
                } else {
                    statsManager.setGlobalBlockerEnabled(isChecked);
                }
            }
        });

        // Strict Mode protection change listener
        switchStrictMode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isRevertingToggle) return;

                if (isChecked) {
                    // Force the user to configure a passcode first 
                    Intent intent = new Intent(MainActivity.this, PinActivity.class);
                    intent.putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_SETUP);
                    startActivityForResult(intent, REQ_CODE_STRICT_DISABLE);
                } else {
                    // Turning strict mode off under active security requires authentication
                    isRevertingToggle = true;
                    switchStrictMode.setChecked(true);
                    isRevertingToggle = false;

                    Intent intent = new Intent(MainActivity.this, PinActivity.class);
                    intent.putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_VERIFY);
                    startActivityForResult(intent, REQ_CODE_STRICT_DISABLE);
                }
            }
        });

        // Block lists switches listeners
        switchBlockTiktok.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                statsManager.setBlockTiktokEnabled(isChecked);
            }
        });

        switchBlockYoutube.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                statsManager.setBlockYoutubeEnabled(isChecked);
            }
        });

        switchBlockInstagram.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                statsManager.setBlockInstagramEnabled(isChecked);
            }
        });

        switchBlockFacebook.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                statsManager.setBlockFacebookEnabled(isChecked);
            }
        });
    }

    private void updateSecurityViews() {
        if (devicePolicyManager.isAdminActive(adminComponent)) {
            txtAdminStatus.setText("Device Administrator protection is SECURED");
            txtAdminStatus.setTextColor(getResources().getColor(R.color.accent_green));
            btnToggleAdmin.setText("Deactivate Admin Shield");
        } else {
            txtAdminStatus.setText("Anti-Uninstall protection is currently vulnerable");
            txtAdminStatus.setTextColor(getResources().getColor(R.color.accent_red));
            btnToggleAdmin.setText("Secure Device Admin");
        }
    }

    private void updateAccessibilityStatus() {
        boolean running = isAccessibilityServiceEnabled(this, FocusAccessibilityService.class);
        if (running) {
            txtShieldTitle.setText(R.string.status_shield_active);
            txtShieldTitle.setTextColor(getResources().getColor(R.color.accent_green));
            txtShieldDesc.setText("Addiction barrier is fully functional.");
            btnToggleAccessibility.setText("Configure Shield");
            // Highlight icon badge block if on
            findViewById(R.id.status_icon_badge).setBackgroundTintList(getResources().getColorStateList(R.color.accent_green_glow));
        } else {
            txtShieldTitle.setText(R.string.status_shield_inactive);
            txtShieldTitle.setTextColor(getResources().getColor(R.color.accent_red));
            txtShieldDesc.setText("Accessibility service needs configuration permission");
            btnToggleAccessibility.setText("Give Accessibility Access");
            findViewById(R.id.status_icon_badge).setBackgroundTintList(getResources().getColorStateList(R.color.accent_red_glow));
        }
    }

    private void updateStats() {
        int blockedToday = statsManager.getBlockedCountToday();
        txtStatBlocked.setText(String.valueOf(blockedToday));
        txtStatSaved.setText(statsManager.getTimeSavedMinutesToday() + "m");

        String mostDistracted = statsManager.getMostDistractingApp();
        txtMostDistracting.setText(mostDistracted);

        int ytCount = statsManager.getYoutubeBlockedCount();
        int igCount = statsManager.getInstagramBlockedCount();
        int ttCount = statsManager.getTiktokBlockedCount();
        int fbCount = statsManager.getFacebookBlockedCount();

        txtCountYoutube.setText(ytCount + " blocks");
        txtCountInstagram.setText(igCount + " blocks");
        txtCountTiktok.setText(ttCount + " blocks");
        txtCountFacebook.setText(fbCount + " blocks");

        // Scale max dynamically based on largest count or minimum 5 to maintain fine visibility proportions
        int maxVal = Math.max(5, Math.max(ytCount, Math.max(igCount, Math.max(ttCount, fbCount))));
        progressYoutube.setMax(maxVal);
        progressInstagram.setMax(maxVal);
        progressTiktok.setMax(maxVal);
        progressFacebook.setMax(maxVal);

        progressYoutube.setProgress(ytCount);
        progressInstagram.setProgress(igCount);
        progressTiktok.setProgress(ttCount);
        progressFacebook.setProgress(fbCount);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAccessibilityStatus();
        updateSecurityViews();
        updateStats();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_CODE_STRICT_DISABLE) {
            if (resultCode == RESULT_OK) {
                // If checking ON (completed SETUP PIN)
                if (!statsManager.isStrictModeEnabled()) {
                    statsManager.setStrictModeEnabled(true);
                    isRevertingToggle = true;
                    switchStrictMode.setChecked(true);
                    isRevertingToggle = false;
                } else {
                    // Verified existing PIN successfully, we can turn strict mode OFF
                    statsManager.setStrictModeEnabled(false);
                    isRevertingToggle = true;
                    switchStrictMode.setChecked(false);
                    isRevertingToggle = false;
                }
            } else {
                Toast.makeText(this, "Verification canceled! Settings maintained.", Toast.LENGTH_SHORT).show();
                isRevertingToggle = true;
                switchStrictMode.setChecked(statsManager.isStrictModeEnabled());
                isRevertingToggle = false;
            }
        } 
        
        else if (requestCode == REQ_CODE_BLOCKER_DISABLE) {
            if (resultCode == RESULT_OK) {
                statsManager.setGlobalBlockerEnabled(false);
                isRevertingToggle = true;
                switchGlobalBlocker.setChecked(false);
                isRevertingToggle = false;
            } else {
                Toast.makeText(this, "Verification failed! Focus Shield remains locked.", Toast.LENGTH_SHORT).show();
                isRevertingToggle = true;
                switchGlobalBlocker.setChecked(true);
                isRevertingToggle = false;
            }
        }

        else if (requestCode == REQ_CODE_DEVICE_ADMIN) {
            if (resultCode == RESULT_OK) {
                devicePolicyManager.removeActiveAdmin(adminComponent);
                updateSecurityViews();
                Toast.makeText(this, "Admin Shield disabled.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Utility method to check if our background accessibility service is currently active.
     */
    private static boolean isAccessibilityServiceEnabled(Context context, Class<? extends AccessibilityService> serviceClass) {
        String prefString = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (prefString != null) {
            ComponentName expectedComponent = new ComponentName(context, serviceClass);
            String[] services = prefString.split(":");
            for (String service : services) {
                ComponentName enabledComponent = ComponentName.unflattenFromString(service);
                if (enabledComponent != null && enabledComponent.equals(expectedComponent)) {
                    return true;
                }
            }
        }
        return false;
    }
}
