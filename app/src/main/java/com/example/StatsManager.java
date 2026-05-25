package com.example;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persistence manager for FocusVault stats and settings.
 * Designed to compile cleanly in AIDE on a mobile device (strictly using SharedPreferences).
 */
public class StatsManager {

    private static final String PREF_NAME = "focus_vault_settings";
    
    // Preference Keys
    private static final String KEY_GLOBAL_BLOCKER = "global_block_enabled";
    private static final String KEY_STRICT_MODE = "strict_mode_enabled";
    private static final String KEY_BLOCK_TIKTOK = "block_tiktok";
    private static final String KEY_BLOCK_YOUTUBE = "block_youtube";
    private static final String KEY_BLOCK_INSTAGRAM = "block_instagram";
    private static final String KEY_BLOCK_FACEBOOK = "block_facebook";
    
    // Stats Keys
    private static final String KEY_BLOCKED_COUNT = "blocked_today_count";
    private static final String KEY_TIME_SAVED = "minutes_saved_today";
    private static final String KEY_MOST_BLOCKED_APP = "most_blocked_app";
    
    // Security Keys
    private static final String KEY_SECURITY_PIN = "security_pin_code";
    
    // Social counters
    private static final String COUNT_YT = "count_youtube_shorts";
    private static final String COUNT_IG = "count_instagram_reels";
    private static final String COUNT_FB = "count_facebook_reels";
    private static final String COUNT_TT = "count_tiktok_reels";

    // Timer state management keys
    private static final String KEY_TIMER_RUNNING = "timer_active_state";
    private static final String KEY_TIMER_DURATION = "timer_total_duration";
    private static final String KEY_TIMER_END_TIME = "timer_end_timestamp";

    private final SharedPreferences prefs;
    private static StatsManager instance;

    private StatsManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized StatsManager getInstance(Context context) {
        if (instance == null) {
            instance = new StatsManager(context);
        }
        return instance;
    }

    // --- Core Protection Switches ---
    
    public boolean isGlobalBlockerEnabled() {
        return prefs.getBoolean(KEY_GLOBAL_BLOCKER, false);
    }

    public void setGlobalBlockerEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_GLOBAL_BLOCKER, enabled).apply();
    }

    public boolean isStrictModeEnabled() {
        return prefs.getBoolean(KEY_STRICT_MODE, false);
    }

    public void setStrictModeEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_STRICT_MODE, enabled).apply();
    }

    public boolean isBlockTiktokEnabled() {
        return prefs.getBoolean(KEY_BLOCK_TIKTOK, true);
    }

    public void setBlockTiktokEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_BLOCK_TIKTOK, enabled).apply();
    }

    public boolean isBlockYoutubeEnabled() {
        return prefs.getBoolean(KEY_BLOCK_YOUTUBE, true);
    }

    public void setBlockYoutubeEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_BLOCK_YOUTUBE, enabled).apply();
    }

    public boolean isBlockInstagramEnabled() {
        return prefs.getBoolean(KEY_BLOCK_INSTAGRAM, true);
    }

    public void setBlockInstagramEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_BLOCK_INSTAGRAM, enabled).apply();
    }

    public boolean isBlockFacebookEnabled() {
        return prefs.getBoolean(KEY_BLOCK_FACEBOOK, true);
    }

    public void setBlockFacebookEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_BLOCK_FACEBOOK, enabled).apply();
    }

    // --- Logging & Intercept Statistics ---
    
    public int getBlockedCountToday() {
        return prefs.getInt(KEY_BLOCKED_COUNT, 0);
    }

    public int getTimeSavedMinutesToday() {
        return prefs.getInt(KEY_TIME_SAVED, 0);
    }

    public String getMostDistractingApp() {
        return prefs.getString(KEY_MOST_BLOCKED_APP, "None");
    }

    /**
     * Increments the block counter and estimates saved study time (2 minutes per scroll block).
     * Calculates the most distracting app dynamically.
     */
    public synchronized void logReelBlocked(String packageName) {
        int count = getBlockedCountToday() + 1;
        int timeSaved = getTimeSavedMinutesToday() + 2; // Estimate 2 minutes saved per scroll hook
        
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(KEY_BLOCKED_COUNT, count);
        editor.putInt(KEY_TIME_SAVED, timeSaved);

        // Update specific app counters
        if ("com.google.android.youtube".equals(packageName)) {
            int ytVal = prefs.getInt(COUNT_YT, 0) + 1;
            editor.putInt(COUNT_YT, ytVal);
        } else if ("com.instagram.android".equals(packageName)) {
            int igVal = prefs.getInt(COUNT_IG, 0) + 1;
            editor.putInt(COUNT_IG, igVal);
        } else if ("com.facebook.katana".equals(packageName)) {
            int fbVal = prefs.getInt(COUNT_FB, 0) + 1;
            editor.putInt(COUNT_FB, fbVal);
        } else if ("com.zhiliaoapp.musically".equals(packageName)) {
            int ttVal = prefs.getInt(COUNT_TT, 0) + 1;
            editor.putInt(COUNT_TT, ttVal);
        }

        // Determine most blocked app
        int yt = prefs.getInt(COUNT_YT, 0);
        int ig = prefs.getInt(COUNT_IG, 0);
        int fb = prefs.getInt(COUNT_FB, 0);
        int tt = prefs.getInt(COUNT_TT, 0);

        String winner = "None";
        int max = 0;
        if (yt > max) { max = yt; winner = "YouTube Shorts"; }
        if (ig > max) { max = ig; winner = "Instagram Reels"; }
        if (fb > max) { max = fb; winner = "Facebook Reels"; }
        if (tt > max) { max = tt; winner = "TikTok"; }

        editor.putString(KEY_MOST_BLOCKED_APP, winner);
        editor.apply();
    }

    public int getYoutubeBlockedCount() {
        return prefs.getInt(COUNT_YT, 0);
    }

    public int getInstagramBlockedCount() {
        return prefs.getInt(COUNT_IG, 0);
    }

    public int getFacebookBlockedCount() {
        return prefs.getInt(COUNT_FB, 0);
    }

    public int getTiktokBlockedCount() {
        return prefs.getInt(COUNT_TT, 0);
    }

    public void resetStats() {
        prefs.edit()
            .putInt(KEY_BLOCKED_COUNT, 0)
            .putInt(KEY_TIME_SAVED, 0)
            .putInt(COUNT_YT, 0)
            .putInt(COUNT_IG, 0)
            .putInt(COUNT_FB, 0)
            .putInt(COUNT_TT, 0)
            .putString(KEY_MOST_BLOCKED_APP, "None")
            .apply();
    }

    // --- Pin Lock Passcode ---
    
    public String getSecurityPin() {
        return prefs.getString(KEY_SECURITY_PIN, "1234");
    }

    public void setSecurityPin(String pin) {
        prefs.edit().putString(KEY_SECURITY_PIN, pin).apply();
    }

    // --- Study Countdown Session Keys ---

    public boolean isTimerRunning() {
        return prefs.getBoolean(KEY_TIMER_RUNNING, false);
    }

    public void setTimerRunning(boolean isRunning) {
        prefs.edit().putBoolean(KEY_TIMER_RUNNING, isRunning).apply();
    }

    public long getTimerEndTime() {
        return prefs.getLong(KEY_TIMER_END_TIME, 0);
    }

    public void setTimerEndTime(long endTime) {
        prefs.edit().putLong(KEY_TIMER_END_TIME, endTime).apply();
    }

    public int getTimerDurationSeconds() {
        return prefs.getInt(KEY_TIMER_DURATION, 1500); // Defaults to 25 minutes
    }

    public void setTimerDurationSeconds(int seconds) {
        prefs.edit().putInt(KEY_TIMER_DURATION, seconds).apply();
    }
}
