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
        boolean blockDetected = false;

        // Try checking event source first
        AccessibilityNodeInfo sourceNode = event.getSource();
        if (sourceNode != null) {
            try {
                if (isYouTube && statsManager.isBlockYoutubeEnabled()) {
                    blockDetected = checkYouTubeShorts(sourceNode);
                } else if (isInstagram && statsManager.isBlockInstagramEnabled()) {
                    blockDetected = checkInstagramReels(sourceNode);
                } else if (isFacebook && statsManager.isBlockFacebookEnabled()) {
                    blockDetected = checkFacebookReels(sourceNode);
                }
            } finally {
                sourceNode.recycle();
            }
        }

        // If not detected in source, try root in active window
        if (!blockDetected) {
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode != null) {
                try {
                    if (isYouTube && statsManager.isBlockYoutubeEnabled()) {
                        blockDetected = checkYouTubeShorts(rootNode);
                    } else if (isInstagram && statsManager.isBlockInstagramEnabled()) {
                        blockDetected = checkInstagramReels(rootNode);
                    } else if (isFacebook && statsManager.isBlockFacebookEnabled()) {
                        blockDetected = checkFacebookReels(rootNode);
                    }
                } finally {
                    rootNode.recycle();
                }
            }
        }

        if (blockDetected) {
            triggerBlockerActions(packageName);
        }
    }

    /**
     * High performance recursive scan of YouTube layout for Shorts active player structures.
     */
    private boolean checkYouTubeShorts(AccessibilityNodeInfo node) {
        if (node == null) return false;

        String id = node.getViewIdResourceName();
        if (id != null) {
            String cleanId = id.toLowerCase();
            // Bypass home menu bottom bar / search controls to prevent homepage false detections
            if (cleanId.contains("pivot_bar") || cleanId.contains("tab_bar") || cleanId.contains("navigation") || cleanId.contains("search")) {
                return false;
            }

            // Target active Shorts player elements directly
            if (cleanId.contains("shorts_player") || 
                cleanId.contains("shorts_container") || 
                cleanId.contains("reel_player") || 
                cleanId.contains("reel_container") || 
                cleanId.contains("shorts_video") ||
                cleanId.contains("shorts_surface") ||
                cleanId.contains("shorts-player") ||
                cleanId.contains("watch_shorts") ||
                cleanId.contains("reel_viewer") ||
                cleanId.contains("panels_container_layout")) {
                return true;
            }
        }

        // Check text heuristics for "Shorts" or similar
        CharSequence txt = node.getText();
        if (txt != null) {
            String cleanText = txt.toString().toLowerCase();
            if (cleanText.equals("shorts") || cleanText.contains("shorts viewer")) {
                if (id == null || (!id.contains("pivot_bar") && !id.contains("tab") && !id.contains("menu"))) {
                    return true;
                }
            }
        }

        // Check content descriptions
        CharSequence desc = node.getContentDescription();
        if (desc != null) {
            String cleanDesc = desc.toString().toLowerCase();
            if (cleanDesc.equals("shorts") || cleanDesc.contains("shorts player") || cleanDesc.contains("shorts viewer") || cleanDesc.contains("shorts video")) {
                if (id == null || (!id.contains("pivot_bar") && !id.contains("tab") && !id.contains("menu"))) {
                    return true;
                }
            }
        }

        // Search children recursively
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                if (checkYouTubeShorts(child)) {
                    child.recycle();
                    return true;
                }
                child.recycle();
            }
        }
        return false;
    }

    /**
     * High performance recursive scan of Instagram layout for active Reel/Clip containers.
     */
    private boolean checkInstagramReels(AccessibilityNodeInfo node) {
        if (node == null) return false;

        String id = node.getViewIdResourceName();
        if (id != null) {
            String cleanId = id.toLowerCase();
            if (cleanId.contains("tab_bar") || cleanId.contains("tabbar") || cleanId.contains("navigation")) {
                return false;
            }

            if (cleanId.contains("clips_video_container") || 
                cleanId.contains("clips_viewer") || 
                cleanId.contains("reel_viewer") || 
                cleanId.contains("clips_video") ||
                cleanId.contains("clips_viewer_container") ||
                cleanId.contains("clips_item_layout") ||
                cleanId.contains("reel_container") ||
                cleanId.contains("reels_viewer") ||
                cleanId.contains("video_container")) {
                return true;
            }
        }

        CharSequence desc = node.getContentDescription();
        if (desc != null) {
            String cleanDesc = desc.toString().toLowerCase();
            if (cleanDesc.equals("reels") || cleanDesc.contains("reels viewer") || cleanDesc.contains("clips viewer") || cleanDesc.contains("watch reels")) {
                if (id == null || (!id.contains("tab") && !id.contains("navigation"))) {
                    return true;
                }
            }
        }

        CharSequence txt = node.getText();
        if (txt != null) {
            String cleanText = txt.toString().toLowerCase();
            if (cleanText.equals("reels") || cleanText.contains("reels viewer") || cleanText.contains("watch reels")) {
                if (id == null || (!id.contains("tab") && !id.contains("navigation"))) {
                    return true;
                }
            }
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                if (checkInstagramReels(child)) {
                    child.recycle();
                    return true;
                }
                child.recycle();
            }
        }
        return false;
    }

    /**
     * High performance recursive scan of Facebook layouts for active Reel viewer structures.
     */
    private boolean checkFacebookReels(AccessibilityNodeInfo node) {
        if (node == null) return false;

        String id = node.getViewIdResourceName();
        if (id != null) {
            String cleanId = id.toLowerCase();
            if (cleanId.contains("tab_bar") || cleanId.contains("navigation")) {
                return false;
            }

            if (cleanId.contains("fb_shorts") || 
                cleanId.contains("reels_viewer") || 
                cleanId.contains("fb_reels") || 
                cleanId.contains("reel_viewer") ||
                cleanId.contains("reels_root") ||
                cleanId.contains("reels_container") ||
                cleanId.contains("fb_shorts_viewer") ||
                cleanId.contains("fb_shorts_player")) {
                return true;
            }
        }

        CharSequence desc = node.getContentDescription();
        if (desc != null) {
            String cleanDesc = desc.toString().toLowerCase();
            if (cleanDesc.contains("reels viewer") || cleanDesc.contains("clips viewer") || cleanDesc.contains("fb shorts player") || cleanDesc.contains("facebook reels")) {
                if (id == null || (!id.contains("tab") && !id.contains("navigation"))) {
                    return true;
                }
            }
        }

        CharSequence txt = node.getText();
        if (txt != null) {
            String cleanText = txt.toString().toLowerCase();
            if (cleanText.contains("reels viewer") || cleanText.contains("fb shorts player") || cleanText.contains("facebook reels")) {
                if (id == null || (!id.contains("tab") && !id.contains("navigation"))) {
                    return true;
                }
            }
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                if (checkFacebookReels(child)) {
                    child.recycle();
                    return true;
                }
                child.recycle();
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
