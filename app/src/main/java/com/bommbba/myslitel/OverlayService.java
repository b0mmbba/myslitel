package com.bommbba.myslitel;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;

public class OverlayService extends Service {
    private static WeakReference<OverlayService> activeService;

    private WindowManager windowManager;
    private View overlayView;
    private TextView statusText;

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
        createOverlay();
    }

    private void createOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(24, 18, 24, 18);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xEE222222);
        bg.setCornerRadius(28);
        panel.setBackground(bg);

        TextView title = new TextView(this);
        title.setText("Мыслитель 0.4 — панель активна");
        title.setTextColor(Color.WHITE);
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));

        statusText = new TextView(this);
        statusText.setText("Можно нажать “Анализ” вручную или включить “Live” — комментарий примерно раз в 12 секунд.");
        statusText.setTextColor(0xFFDADADA);
        statusText.setTextSize(13);
        statusText.setPadding(0, 8, 0, 8);
        panel.addView(statusText, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout buttonsTop = new LinearLayout(this);
        buttonsTop.setOrientation(LinearLayout.HORIZONTAL);
        buttonsTop.setGravity(Gravity.END);

        Button analyzeButton = new Button(this);
        analyzeButton.setText("Анализ");
        analyzeButton.setOnClickListener(v -> MainActivity.analyzeScreenFromOverlay());
        buttonsTop.addView(analyzeButton, new LinearLayout.LayoutParams(0, -2, 1f));

        Button openButton = new Button(this);
        openButton.setText("Открыть");
        openButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });
        buttonsTop.addView(openButton, new LinearLayout.LayoutParams(0, -2, 1f));
        panel.addView(buttonsTop, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout buttonsLive = new LinearLayout(this);
        buttonsLive.setOrientation(LinearLayout.HORIZONTAL);
        buttonsLive.setGravity(Gravity.END);

        Button liveButton = new Button(this);
        liveButton.setText("Live");
        liveButton.setOnClickListener(v -> MainActivity.startLiveFromOverlay());
        buttonsLive.addView(liveButton, new LinearLayout.LayoutParams(0, -2, 1f));

        Button stopLiveButton = new Button(this);
        stopLiveButton.setText("Стоп live");
        stopLiveButton.setOnClickListener(v -> MainActivity.stopLiveFromOverlay());
        buttonsLive.addView(stopLiveButton, new LinearLayout.LayoutParams(0, -2, 1f));

        panel.addView(buttonsLive, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout buttonsBottom = new LinearLayout(this);
        buttonsBottom.setOrientation(LinearLayout.HORIZONTAL);
        buttonsBottom.setGravity(Gravity.END);

        Button stopCaptureButton = new Button(this);
        stopCaptureButton.setText("Стоп просмотр");
        stopCaptureButton.setOnClickListener(v -> MainActivity.stopScreenCaptureFromOverlay());
        buttonsBottom.addView(stopCaptureButton, new LinearLayout.LayoutParams(0, -2, 1f));

        Button hideButton = new Button(this);
        hideButton.setText("Скрыть");
        hideButton.setOnClickListener(v -> stopSelf());
        buttonsBottom.addView(hideButton, new LinearLayout.LayoutParams(0, -2, 1f));

        panel.addView(buttonsBottom, new LinearLayout.LayoutParams(-1, -2));

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.x = 0;
        params.y = 18;

        overlayView = panel;
        windowManager.addView(overlayView, params);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (windowManager != null && overlayView != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception ignored) {
            }
            overlayView = null;
        }
        if (activeService != null && activeService.get() == this) {
            activeService = null;
        }
    }
}
