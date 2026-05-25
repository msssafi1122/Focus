package com.example;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

/**
 * TimerActivity handles Pomodoro and deep study sessions.
 * Interlocks with FloatingBubbleService to display live counts on-top of other screens.
 * Designed using basic Android SDK classes suitable for AIDE compiles.
 */
public class TimerActivity extends AppCompatActivity {

    private TextView txtCount;
    private TextView txtState;
    private Button btnToggle;
    private Button btnStop;
    
    private CardView cardPreset25;
    private CardView cardPreset50;

    private StatsManager statsManager;
    private CountDownTimer countDownTimer;
    private int selectedDurationSeconds = 1500; // Default 25 min

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_timer);

        statsManager = StatsManager.getInstance(this);

        txtCount = findViewById(R.id.txt_countdown_time);
        txtState = findViewById(R.id.txt_countdown_state);
        btnToggle = findViewById(R.id.btn_timer_toggle);
        btnStop = findViewById(R.id.btn_timer_stop);
        
        cardPreset25 = findViewById(R.id.card_preset_25);
        cardPreset50 = findViewById(R.id.card_preset_50);

        findViewById(R.id.btn_timer_back_arrow).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        setupPresets();
        setupControls();
        restoreTimerSessionState();
    }

    private void setupPresets() {
        cardPreset25.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!statsManager.isTimerRunning()) {
                    selectedDurationSeconds = 1500; // 25 Min
                    updateTimeDisplay(selectedDurationSeconds);
                    Toast.makeText(TimerActivity.this, "Preset: 25 Min Pomodoro selected", Toast.LENGTH_SHORT).show();
                }
            }
        });

        cardPreset50.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!statsManager.isTimerRunning()) {
                    selectedDurationSeconds = 3000; // 50 Min
                    updateTimeDisplay(selectedDurationSeconds);
                    Toast.makeText(TimerActivity.this, "Preset: 50 Min Deep Study selected", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void setupControls() {
        btnToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (statsManager.isTimerRunning()) {
                    pauseTimer();
                } else {
                    startTimer();
                }
            }
        });

        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopTimer();
            }
        });
    }

    private void startTimer() {
        // Overlay permission check if required to start Bubble HUD
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Enable Draw On-Top permissions for study widgets", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            startActivity(intent);
            return;
        }

        long endTime = System.currentTimeMillis() + (selectedDurationSeconds * 1000L);
        statsManager.setTimerEndTime(endTime);
        statsManager.setTimerDurationSeconds(selectedDurationSeconds);
        statsManager.setTimerRunning(true);

        // Turn on the blocker shield automatically when starting a study deck session
        statsManager.setGlobalBlockerEnabled(true);

        btnToggle.setText("Pause Session");
        txtState.setText("LOCK ENGAGED");
        txtState.setTextColor(getResources().getColor(R.color.accent_green));

        runTickEngine(selectedDurationSeconds * 1000L);

        // Fire study bubble HUD HUD overlay service 
        Intent bubbleIntent = new Intent(this, FloatingBubbleService.class);
        startService(bubbleIntent);
    }

    private void pauseTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        statsManager.setTimerRunning(false);
        btnToggle.setText("Resume Study");
        txtState.setText("PAUSED");
    }

    private void stopTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        statsManager.setTimerRunning(false);
        statsManager.setTimerEndTime(0);
        
        btnToggle.setText("Start Study Session");
        txtState.setText("READY");
        txtState.setTextColor(getResources().getColor(R.color.accent_green));
        
        selectedDurationSeconds = 1500;
        updateTimeDisplay(selectedDurationSeconds);

        stopService(new Intent(this, FloatingBubbleService.class));
    }

    private void runTickEngine(long millisInFuture) {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        countDownTimer = new CountDownTimer(millisInFuture, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                selectedDurationSeconds = (int) (millisUntilFinished / 1000);
                updateTimeDisplay(selectedDurationSeconds);
            }

            @Override
            public void onFinish() {
                stopTimer();
                Toast.makeText(TimerActivity.this, "🎉 Study session complete! Great job maintaining focus!", Toast.LENGTH_LONG).show();
            }
        }.start();
    }

    private void restoreTimerSessionState() {
        if (statsManager.isTimerRunning()) {
            long timeLeft = statsManager.getTimerEndTime() - System.currentTimeMillis();
            if (timeLeft > 0) {
                btnToggle.setText("Pause Session");
                txtState.setText("STUDY SCRAMBLE ACTIVE");
                txtState.setTextColor(getResources().getColor(R.color.accent_red));
                runTickEngine(timeLeft);
            } else {
                stopTimer();
            }
        } else {
            updateTimeDisplay(selectedDurationSeconds);
        }
    }

    private void updateTimeDisplay(int totalSeconds) {
        int mins = totalSeconds / 60;
        int secs = totalSeconds % 60;
        txtCount.setText(String.format("%02d:%02d", mins, secs));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (statsManager.isTimerRunning()) {
            restoreTimerSessionState();
        }
    }
}
