package com.example;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

/**
 * FocusAccessibilityService works in background inspecting screen content and packages.
 * It intercepts YouTube Shorts, Facebook Reels, Instagram Reels, and TikTok.
 * Uses high-performance native text querying to remain battery efficient.
 */
public class FocusAccessibilityService extends AccessibilityService {

    private StatsManager statsManager;
    private long lastBlockTime = 0; // Prevent spam triggers inside event bursts

    @Override
    public void onCreate() {
        super.onCreate();
        statsManager = StatsManager.getInstance(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || statsManager == null) {
            return;
        }

        // 1. Battery Saving Check: Skip immediately if blocker is completely disabled
        // and no study timer is ticking.
        boolean activeBlocker = statsManager.isGlobalBlockerEnabled() || statsManager.isTimerRunning();
        if (!activeBlocker) {
            return;
        }

        CharSequence pkgChar = event.getPackageName();
        if (pkgChar == null) {
            return;
        }

        String packageName = pkgChar.toString();

        // 2. High-speed Triage: Filter targeted social media packages
        boolean isYouTube = "com.google.android.youtube".equals(packageName);
        boolean isInstagram = "com.instagram.android".equals(packageName);
        boolean isFacebook = "com.facebook.katana".equals(packageName);
        boolean isTikTok = "com.zhiliaoapp.musically".equals(packageName);

        if (!isYouTube && !isInstagram && !isFacebook && !isTikTok) {
            // Unrelated application, bypass inspection instantly to keep system performant.
            return;
        }

        // 3. TikTok: Directly block the entire app immediately if TikTok blocking is toggled.
        if (isTikTok && statsManager.isBlockTiktokEnabled()) {
            triggerBlockerActions(packageName);
            return;
        }

        // 4. Inspect screen nodes for other apps (Shorts / Reels)
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) {
            return;
        }

        try {
            // Check switches relative to each app
            if (isYouTube && statsManager.isBlockYoutubeEnabled()) {
                if (containsBlockingKeywords(rootNode, "Shorts")) {
                    triggerBlockerActions(packageName);
                }
            } else if (isInstagram && statsManager.isBlockInstagramEnabled()) {
                if (containsBlockingKeywords(rootNode, "Reels") || 
                    containsBlockingKeywords(rootNode, "Suggested reels") ||
                    containsBlockingKeywords(rootNode, "Watch more reels")) {
                    triggerBlockerActions(packageName);
                }
            } else if (isFacebook && statsManager.isBlockFacebookEnabled()) {
                if (containsBlockingKeywords(rootNode, "Reels") || 
                    containsBlockingKeywords(rootNode, "Suggested reels") ||
                    containsBlockingKeywords(rootNode, "Watch more reels")) {
                    triggerBlockerActions(packageName);
                }
            }
        } finally {
            // Always recycle root node reference to avoid memory leaks!
            rootNode.recycle();
        }
    }

    /**
     * High performance search utilizing Android's built-in text-indexing engine.
     * Recursively traverses subnodes if built-in filters require secondary validation.
     */
    private boolean containsBlockingKeywords(AccessibilityNodeInfo root, String keyword) {
        if (root == null) return false;

        // Use native index search - extremely optimized compared to heavy manual child tree loops
        List<AccessibilityNodeInfo> matchingNodes = root.findAccessibilityNodeInfosByText(keyword);
        if (matchingNodes != null && !matchingNodes.isEmpty()) {
            for (AccessibilityNodeInfo node : matchingNodes) {
                // Double verify exact or partial text matches to prevent false positives
                CharSequence nodeText = node.getText();
                if (nodeText != null) {
                    String cleanText = nodeText.toString().toLowerCase();
                    String cleanKeyword = keyword.toLowerCase();
                    if (cleanText.contains(cleanKeyword)) {
                        node.recycle();
                        return true;
                    }
                }
                node.recycle();
            }
        }
        return false;
    }

    /**
     * Executes security block actions: Hitting BACK, reporting stats, and launching full-screen overlay alert.
     */
    private void triggerBlockerActions(String packageName) {
        long currentTime = System.currentTimeMillis();
        // Debounce block actions inside a 3-second window
        if (currentTime - lastBlockTime < 3000) {
            return;
        }
        lastBlockTime = currentTime;

        // Log stats
        statsManager.logReelBlocked(packageName);

        // Action 1: perform back press to close current window state
        performGlobalAction(GLOBAL_ACTION_BACK);

        // Action 2: Show overlay alert screen
        Intent overlayIntent = new Intent(this, OverlayService.class);
        overlayIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startService(overlayIntent);
    }

    @Override
    public void onInterrupt() {
        // Accessibility interrupts trigger cleanup if needed
    }
}
