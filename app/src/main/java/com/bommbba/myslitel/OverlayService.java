package com.bommbba.myslitel;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;

public class OverlayService extends Service {
    private static final String PREFS = "myslitel_prefs";
    private static final String KEY_OVERLAY_TOP_Y = "overlay_top_y";
    private static WeakReference<OverlayService> activeService;

    private WindowManager windowManager;
    private WindowManager.LayoutParams overlayParams;
    private View overlayView;
    private TextView statusText;
    private EditText quickInput;
    private SharedPreferences prefs;

    public static void updatePanelText(String text) {
        OverlayService service = activeService == null ? null : activeService.get();
        if (service == null || service.statusText == null) return;
        service.statusText.post(() -> service.statusText.setText(text == null ? "" : text));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        activeService = new WeakReference<>(this);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        createOverlay();
    }

    private Button smallButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(10);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(4, 4, 4, 4);
        return button;
    }

    private void createOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(12, 8, 12, 8);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF0222222);
        bg.setCornerRadius(22);
        panel.setBackground(bg);

        statusText = new TextView(this);
        statusText.setText("");
        statusText.setTextColor(0xFFEDEDED);
        statusText.setTextSize(12);
        statusText.setMinLines(1);
        statusText.setMaxLines(3);
        statusText.setPadding(0, 0, 0, 4);
        panel.addView(statusText, new LinearLayout.LayoutParams(-1, -2));

        quickInput = new EditText(this);
        quickInput.setHint("Напиши задачу или вопрос…");
        quickInput.setSingleLine(false);
        quickInput.setMinLines(1);
        quickInput.setMaxLines(2);
        quickInput.setTextColor(Color.WHITE);
        quickInput.setHintTextColor(0xFFBBBBBB);
        quickInput.setTextSize(13);
        panel.addView(quickInput, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);

        Button sendButton = smallButton("Спросить");
        sendButton.setOnClickListener(v -> {
            String text = quickInput.getText().toString().trim();
            if (text.isEmpty()) {
                updatePanelText("Напиши сообщение.");
                return;
            }
            quickInput.setText("");
            updatePanelText("Думаю…");
            MainActivity.askFromOverlay(text);
        });
        row1.addView(sendButton, new LinearLayout.LayoutParams(0, -2, 1f));

        Button analyzeButton = smallButton("Анализ");
        analyzeButton.setOnClickListener(v -> MainActivity.analyzeScreenFromOverlay());
        row1.addView(analyzeButton, new LinearLayout.LayoutParams(0, -2, 1f));

        Button liveButton = smallButton("Live");
        liveButton.setOnClickListener(v -> MainActivity.startLiveFromOverlay());
        row1.addView(liveButton, new LinearLayout.LayoutParams(0, -2, 1f));

        Button stopLiveButton = smallButton("Стоп");
        stopLiveButton.setOnClickListener(v -> MainActivity.stopLiveFromOverlay());
        row1.addView(stopLiveButton, new LinearLayout.LayoutParams(0, -2, 1f));

        panel.addView(row1, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);

        Button autoStepButton = smallButton("Автошаг");
        autoStepButton.setOnClickListener(v -> {
            String task = quickInput.getText().toString().trim();
            if (!task.isEmpty()) quickInput.setText("");
            updatePanelText("Автошаг…");
            MainActivity.autoStepFromOverlay(task);
        });
        row2.addView(autoStepButton, new LinearLayout.LayoutParams(0, -2, 1f));

        Button autoButton = smallButton("Авто");
        autoButton.setOnClickListener(v -> {
            String task = quickInput.getText().toString().trim();
            if (!task.isEmpty()) quickInput.setText("");
            updatePanelText("Авто…");
            MainActivity.startAutopilotFromOverlay(task);
        });
        row2.addView(autoButton, new LinearLayout.LayoutParams(0, -2, 1f));

        Button stopAutoButton = smallButton("Стоп авто");
        stopAutoButton.setOnClickListener(v -> MainActivity.stopAutopilotFromOverlay());
        row2.addView(stopAutoButton, new LinearLayout.LayoutParams(0, -2, 1f));

        Button hideButton = smallButton("Скрыть");
        hideButton.setOnClickListener(v -> stopSelf());
        row2.addView(hideButton, new LinearLayout.LayoutParams(0, -2, 1f));

        panel.addView(row2, new LinearLayout.LayoutParams(-1, -2));

        overlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        overlayParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        overlayParams.x = 0;
        overlayParams.y = Math.max(0, prefs.getInt(KEY_OVERLAY_TOP_Y, 0));

        View.OnTouchListener dragListener = new View.OnTouchListener() {
            private int startY;
            private float startRawY;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (overlayParams == null || windowManager == null || overlayView == null) return false;
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startY = overlayParams.y;
                        startRawY = event.getRawY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dy = Math.round(event.getRawY() - startRawY);
                        if (Math.abs(dy) > 6) moved = true;
                        setPanelTopY(startY + dy, false);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        prefs.edit().putInt(KEY_OVERLAY_TOP_Y, overlayParams.y).apply();
                        return moved;
                }
                return false;
            }
        };
        statusText.setOnTouchListener(dragListener);
        panel.setOnTouchListener(dragListener);

        overlayView = panel;
        windowManager.addView(overlayView, overlayParams);

        overlayView.post(() -> {
            int saved = prefs.getInt(KEY_OVERLAY_TOP_Y, -1);
            if (saved < 0) setPanelTopY(getMaxPanelTopY(), true);
            else setPanelTopY(saved, true);
        });
    }

    private int getMaxPanelTopY() {
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int panelHeight = overlayView == null ? 250 : Math.max(1, overlayView.getHeight());
        return Math.max(0, screenHeight - panelHeight - 12);
    }

    private void setPanelTopY(int y, boolean save) {
        if (overlayParams == null || windowManager == null || overlayView == null) return;
        int clamped = Math.max(0, Math.min(y, getMaxPanelTopY()));
        overlayParams.y = clamped;
        try { windowManager.updateViewLayout(overlayView, overlayParams); } catch (Exception ignored) {}
        if (save) prefs.edit().putInt(KEY_OVERLAY_TOP_Y, overlayParams.y).apply();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (windowManager != null && overlayView != null) {
            try { windowManager.removeView(overlayView); } catch (Exception ignored) {}
            overlayView = null;
        }
        if (activeService != null && activeService.get() == this) activeService = null;
    }
}
