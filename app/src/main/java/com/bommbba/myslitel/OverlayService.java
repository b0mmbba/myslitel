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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;

public class OverlayService extends Service {
    private static WeakReference<OverlayService> activeService;

    private WindowManager windowManager;
    private View overlayView;
    private TextView statusText;
    private EditText quickInput;

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
        panel.setPadding(18, 14, 18, 14);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF0222222);
        bg.setCornerRadius(24);
        panel.setBackground(bg);

        TextView title = new TextView(this);
        title.setText("Мыслитель 0.5 — панель активна");
        title.setTextColor(Color.WHITE);
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));

        statusText = new TextView(this);
        statusText.setText("Можно писать сообщение, нажимать “Анализ” или включить “Live”. Режимы: Комментатор/Навигатор/Учитель/Антиошибка/Тихий.");
        statusText.setTextColor(0xFFDADADA);
        statusText.setTextSize(12);
        statusText.setPadding(0, 6, 0, 6);
        panel.addView(statusText, new LinearLayout.LayoutParams(-1, -2));

        quickInput = new EditText(this);
        quickInput.setHint("Напиши Мыслителю… например: запомни, что я настраиваю приложение");
        quickInput.setSingleLine(false);
        quickInput.setMinLines(1);
        quickInput.setMaxLines(2);
        quickInput.setTextColor(Color.WHITE);
        quickInput.setHintTextColor(0xFFBBBBBB);
        quickInput.setTextSize(13);
        panel.addView(quickInput, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout messageRow = new LinearLayout(this);
        messageRow.setOrientation(LinearLayout.HORIZONTAL);

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
        messageRow.addView(sendButton, new LinearLayout.LayoutParams(0, -2, 1f));

        Button analyzeButton = smallButton("Анализ");
        analyzeButton.setOnClickListener(v -> MainActivity.analyzeScreenFromOverlay());
        messageRow.addView(analyzeButton, new LinearLayout.LayoutParams(0, -2, 1f));

        Button openButton = smallButton("Открыть");
        openButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });
        messageRow.addView(openButton, new LinearLayout.LayoutParams(0, -2, 1f));
        panel.addView(messageRow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);

        Button commentatorButton = smallButton("Комм.");
        commentatorButton.setOnClickListener(v -> MainActivity.setModeFromOverlay("Комментатор"));
        modeRow.addView(commentatorButton, new LinearLayout.LayoutParams(0, -2, 1f));

        Button navigatorButton = smallButton("Навиг.");
        navigatorButton.setOnClickListener(v -> MainActivity.setModeFromOverlay("Навигатор"));
        modeRow.addView(navigatorButton, new LinearLayout.LayoutParams(0, -2, 1f));

        Button teacherButton = smallButton("Учитель");
        teacherButton.setOnClickListener(v -> MainActivity.setModeFromOverlay("Учитель"));
        modeRow.addView(teacherButton, new LinearLayout.LayoutParams(0, -2, 1f));

        Button antiErrorButton = smallButton("Ошибка");
        antiErrorButton.setOnClickListener(v -> MainActivity.setModeFromOverlay("Антиошибка"));
        modeRow.addView(antiErrorButton, new LinearLayout.LayoutParams(0, -2, 1f));

        Button quietButton = smallButton("Тихо");
        quietButton.setOnClickListener(v -> MainActivity.setModeFromOverlay("Тихий"));
        modeRow.addView(quietButton, new LinearLayout.LayoutParams(0, -2, 1f));
        panel.addView(modeRow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout liveRow = new LinearLayout(this);
        liveRow.setOrientation(LinearLayout.HORIZONTAL);

        Button liveButton = smallButton("Live");
        liveButton.setOnClickListener(v -> MainActivity.startLiveFromOverlay());
        liveRow.addView(liveButton, new LinearLayout.LayoutParams(0, -2, 1f));

        Button stopLiveButton = smallButton("Стоп live");
        stopLiveButton.setOnClickListener(v -> MainActivity.stopLiveFromOverlay());
        liveRow.addView(stopLiveButton, new LinearLayout.LayoutParams(0, -2, 1f));

        Button stopCaptureButton = smallButton("Стоп просмотр");
        stopCaptureButton.setOnClickListener(v -> MainActivity.stopScreenCaptureFromOverlay());
        liveRow.addView(stopCaptureButton, new LinearLayout.LayoutParams(0, -2, 1f));

        Button hideButton = smallButton("Скрыть");
        hideButton.setOnClickListener(v -> stopSelf());
        liveRow.addView(hideButton, new LinearLayout.LayoutParams(0, -2, 1f));
        panel.addView(liveRow, new LinearLayout.LayoutParams(-1, -2));

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.x = 0;
        params.y = 8;

        overlayView = panel;
        windowManager.addView(overlayView, params);
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
