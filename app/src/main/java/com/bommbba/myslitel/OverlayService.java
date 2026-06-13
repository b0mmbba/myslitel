package com.bommbba.myslitel;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
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
        service.statusText.post(() -> service.statusText.setText(text));
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
        button.setTextSize(11);
        button.setAllCaps(false);
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
        panel.setPadding(18, 12, 18, 12);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF0222222);
        bg.setCornerRadius(24);
        panel.setBackground(bg);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView dragHandle = new TextView(this);
        dragHandle.setText("↕ Мыслитель 0.6.2 — тяни эту строку или жми ↑/↓");
        dragHandle.setTextColor(Color.WHITE);
        dragHandle.setTextSize(13);
        dragHandle.setTypeface(Typeface.DEFAULT_BOLD);
        dragHandle.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(dragHandle, new LinearLayout.LayoutParams(0, -2, 1f));

        Button upButton = smallButton("↑");
        upButton.setOnClickListener(v -> movePanelBy(-90));
        header.addView(upButton, new LinearLayout.LayoutParams(-2, -2));

        Button downButton = smallButton("↓");
        downButton.setOnClickListener(v -> movePanelBy(90));
        header.addView(downButton, new LinearLayout.LayoutParams(-2, -2));

        panel.addView(header, new LinearLayout.LayoutParams(-1, -2));

        statusText = new TextView(this);
        statusText.setText("Напиши сообщение, нажми “Анализ” или включи “Live”. Режимы и остальные кнопки теперь в основном приложении.");
        statusText.setTextColor(0xFFDADADA);
        statusText.setTextSize(12);
        statusText.setPadding(0, 6, 0, 6);
        panel.addView(statusText, new LinearLayout.LayoutParams(-1, -2));

        quickInput = new EditText(this);
        quickInput.setHint("Напиши Мыслителю… например: запомни, что я тестирую live");
        quickInput.setSingleLine(false);
        quickInput.setMinLines(1);
        quickInput.setMaxLines(2);
        quickInput.setTextColor(Color.WHITE);
        quickInput.setHintTextColor(0xFFBBBBBB);
        quickInput.setTextSize(13);
        panel.addView(quickInput, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        Button sendButton = smallButton("Спросить");
        sendButton.setOnClickListener(v -> {
            String text = quickInput.getText().toString().trim();
            if (text.isEmpty()) {
                updatePanelText("Напиши сообщение в поле выше.");
                return;
            }
            quickInput.setText("");
            updatePanelText("Отправляю сообщение с учётом текущего экрана…");
            MainActivity.askFromOverlay(text);
        });
        row.addView(sendButton, new LinearLayout.LayoutParams(0, -2, 1f));

        Button analyzeButton = smallButton("Анализ");
        analyzeButton.setOnClickListener(v -> MainActivity.analyzeScreenFromOverlay());
        row.addView(analyzeButton, new LinearLayout.LayoutParams(0, -2, 1f));

        Button liveButton = smallButton("Live");
        liveButton.setOnClickListener(v -> MainActivity.startLiveFromOverlay());
        row.addView(liveButton, new LinearLayout.LayoutParams(0, -2, 1f));

        Button stopLiveButton = smallButton("Стоп live");
        stopLiveButton.setOnClickListener(v -> MainActivity.stopLiveFromOverlay());
        row.addView(stopLiveButton, new LinearLayout.LayoutParams(0, -2, 1f));

        panel.addView(row, new LinearLayout.LayoutParams(-1, -2));

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
                        if (Math.abs(dy) > 4) moved = true;
                        setPanelTopY(startY + dy, false);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        prefs.edit().putInt(KEY_OVERLAY_TOP_Y, overlayParams.y).apply();
                        if (moved) updatePanelText("Положение панели сохранено. Можно тянуть верхнюю строку или жать ↑/↓.");
                        return true;
                }
                return false;
            }
        };
        dragHandle.setOnTouchListener(dragListener);
        header.setOnTouchListener(dragListener);

        overlayView = panel;
        windowManager.addView(overlayView, overlayParams);

        overlayView.post(() -> {
            int saved = prefs.getInt(KEY_OVERLAY_TOP_Y, -1);
            if (saved < 0) {
                int bottomY = getMaxPanelTopY();
                setPanelTopY(bottomY, true);
            } else {
                setPanelTopY(saved, true);
            }
        });
    }

    private int getMaxPanelTopY() {
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int panelHeight = overlayView == null ? 260 : Math.max(1, overlayView.getHeight());
        return Math.max(0, screenHeight - panelHeight - 12);
    }

    private void setPanelTopY(int y, boolean save) {
        if (overlayParams == null || windowManager == null || overlayView == null) return;
        int clamped = Math.max(0, Math.min(y, getMaxPanelTopY()));
        overlayParams.y = clamped;
        try { windowManager.updateViewLayout(overlayView, overlayParams); } catch (Exception ignored) {}
        if (save) prefs.edit().putInt(KEY_OVERLAY_TOP_Y, overlayParams.y).apply();
    }

    private void movePanelBy(int deltaY) {
        setPanelTopY(overlayParams == null ? 0 : overlayParams.y + deltaY, true);
        updatePanelText("Положение панели изменено кнопками ↑/↓. Верхнюю строку тоже можно тянуть пальцем.");
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
