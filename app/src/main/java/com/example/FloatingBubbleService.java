package com.example;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * FloatingBubbleService provides a sleek, draggable floating pill widget displaying the 
 * active Pomodoro timer state countdown.
 * Highly responsive user control with delta motion calculation.
 */
public class FloatingBubbleService extends Service {

    private WindowManager windowManager;
    private View bubbleView;
    private WindowManager.LayoutParams params;
    private TextView timerText;
    private final Handler handler = new Handler();
    private StatsManager statsManager;

    private final Runnable timerUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (statsManager != null && statsManager.isTimerRunning()) {
                long timeLeft = statsManager.getTimerEndTime() - System.currentTimeMillis();
                if (timeLeft > 0) {
                    int minutes = (int) (timeLeft / 60000);
                    int seconds = (int) ((timeLeft % 60000) / 1000);
                    String timeStr = String.format("%02d:%02d", minutes, seconds);
                    if (timerText != null) {
                        timerText.setText(timeStr);
                    }
                    handler.postDelayed(this, 1000);
                } else {
                    statsManager.setTimerRunning(false);
                    if (timerText != null) {
                        timerText.setText("Done!");
                    }
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            stopSelf();
                        }
                    }, 3000);
                }
            } else {
                stopSelf();
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        statsManager = StatsManager.getInstance(this);
        showBubble();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handler.removeCallbacks(timerUpdateRunnable);
        handler.post(timerUpdateRunnable);
        return START_STICKY;
    }

    private void showBubble() {
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        bubbleView = inflater.inflate(R.layout.floating_bubble, null);
        timerText = bubbleView.findViewById(R.id.bubble_text);

        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 200;

        // Custom drag handler
        bubbleView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(bubbleView, params);
                        return true;

                    case MotionEvent.ACTION_UP:
                        // If tapped, open MainActivity
                        float deltaX = event.getRawX() - initialTouchX;
                        float deltaY = event.getRawY() - initialTouchY;
                        if (Math.abs(deltaX) < 10 && Math.abs(deltaY) < 10) {
                            Intent intent = new Intent(FloatingBubbleService.this, MainActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }
                        return true;
                }
                return false;
            }
        });

        try {
            windowManager.addView(bubbleView, params);
        } catch (Exception e) {
            e.printStackTrace();
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(timerUpdateRunnable);
        if (windowManager != null && bubbleView != null) {
            try {
                windowManager.removeView(bubbleView);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
