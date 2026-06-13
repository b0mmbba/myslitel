package com.bommbba.myslitel;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String PREFS = "myslitel_prefs";
    private static final String KEY_OPENAI = "openai_api_key";
    private static final String KEY_MODE = "work_mode";
    private static final String KEY_SESSION_CONTEXT = "session_context";
    private static final String KEY_BUDGET_USD = "budget_usd";
    private static final String KEY_ESTIMATED_SPEND_USD = "estimated_spend_usd";
    private static final String KEY_INPUT_PRICE_PER_M = "input_price_per_m";
    private static final String KEY_OUTPUT_PRICE_PER_M = "output_price_per_m";
    private static final String MODEL = "gpt-5.5";
    private static final int REQUEST_MEDIA_PROJECTION = 2303;
    private static final long LIVE_INTERVAL_MS = 12000L;
    private static final long AUTOPILOT_INTERVAL_MS = 3400L;
    private static final int AUTOPILOT_MAX_STEPS = 18;

    private static final String[] MODES = new String[]{"Комментатор", "Навигатор", "Учитель", "Антиошибка", "Тихий"};
    private static WeakReference<MainActivity> activeActivity;

    private EditText apiKeyInput;
    private EditText contextInput;
    private EditText messageInput;
    private EditText budgetInput;
    private EditText inputPriceInput;
    private EditText outputPriceInput;
    private TextView chatLog;
    private TextView modeDescriptionText;
    private TextView balanceView;
    private Button askButton;
    private Button analyzeButton;
    private Button liveStartButton;
    private Button liveStopButton;
    private Button autoStepButton;
    private Button autopilotStartButton;
    private Button autopilotStopButton;
    private Spinner modeSpinner;

    private MediaProjectionManager projectionManager;
    private SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean liveModeEnabled = false;
    private boolean analysisRunning = false;
    private boolean controlRunning = false;
    private boolean autopilotEnabled = false;
    private int liveTickCount = 0;
    private int autopilotStepCount = 0;
    private String autopilotTask = "";
    private String lastControlSummary = "";
    private int controlErrorStreak = 0;
    private String liveContext = "";
    private String selectedMode = "Комментатор";
    private String sessionContext = "";
    private double budgetUsd = 10.0;
    private double estimatedSpendUsd = 0.0;
    private double inputPricePerM = 5.0;
    private double outputPricePerM = 30.0;

    public static void analyzeScreenFromOverlay() {
        MainActivity activity = getActiveActivity();
        if (activity == null) {
            OverlayService.updatePanelText("Открой приложение “Мыслитель”, нажми “Разрешить просмотр экрана”, затем снова попробуй анализ.");
            return;
        }
        activity.runOnUiThread(activity::analyzeCurrentScreen);
    }

    public static void askFromOverlay(String text) {
        MainActivity activity = getActiveActivity();
        if (activity == null) {
            OverlayService.updatePanelText("Открой Мыслитель один раз, чтобы панель связалась с приложением.");
            return;
        }
        activity.runOnUiThread(() -> activity.handleOverlayQuestion(text));
    }

    public static void setModeFromOverlay(String mode) {
        MainActivity activity = getActiveActivity();
        if (activity == null) {
            OverlayService.updatePanelText("Режим “" + mode + "” выбран. Открой Мыслитель, если режим не сохранился.");
            return;
        }
        activity.runOnUiThread(() -> activity.setMode(mode));
    }

    public static void startLiveFromOverlay() {
        MainActivity activity = getActiveActivity();
        if (activity == null) {
            OverlayService.updatePanelText("Открой Мыслитель один раз, выдай просмотр экрана, потом запускай Live.");
            return;
        }
        activity.runOnUiThread(activity::startLiveMode);
    }

    public static void stopLiveFromOverlay() {
        MainActivity activity = getActiveActivity();
        if (activity == null) {
            OverlayService.updatePanelText("Live остановлен, если приложение ещё активно. Для уверенности открой Мыслитель.");
            return;
        }
        activity.runOnUiThread(activity::stopLiveMode);
    }

    public static void autoStepFromOverlay(String task) {
        MainActivity activity = getActiveActivity();
        if (activity == null) {
            OverlayService.updatePanelText("Открой Мыслитель, включи просмотр экрана и управление, потом снова нажми Автошаг.");
            return;
        }
        activity.runOnUiThread(() -> activity.runControlStep(task, false));
    }

    public static void startAutopilotFromOverlay(String task) {
        MainActivity activity = getActiveActivity();
        if (activity == null) {
            OverlayService.updatePanelText("Открой Мыслитель, включи просмотр экрана и управление, потом запускай Авто.");
            return;
        }
        activity.runOnUiThread(() -> activity.startAutopilot(task));
    }

    public static void stopAutopilotFromOverlay() {
        MainActivity activity = getActiveActivity();
        if (activity == null) {
            OverlayService.updatePanelText("Автопилот остановлен, если приложение активно.");
            return;
        }
        activity.runOnUiThread(activity::stopAutopilot);
    }

    public static void stopScreenCaptureFromOverlay() {
        MainActivity activity = getActiveActivity();
        if (activity == null) {
            OverlayService.updatePanelText("Открой Мыслитель и нажми “Остановить просмотр экрана”.");
            return;
        }
        activity.runOnUiThread(activity::stopScreenCapture);
    }

    public static void notifyScreenCaptureReady(boolean ready, String message) {
        MainActivity activity = getActiveActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> activity.appendLog(message));
        }
    }

    private static MainActivity getActiveActivity() {
        return activeActivity == null ? null : activeActivity.get();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activeActivity = new WeakReference<>(this);
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        selectedMode = prefs.getString(KEY_MODE, "Комментатор");
        sessionContext = prefs.getString(KEY_SESSION_CONTEXT, "");
        budgetUsd = parseDoubleSafe(prefs.getString(KEY_BUDGET_USD, "10"), 10.0);
        estimatedSpendUsd = Double.longBitsToDouble(prefs.getLong(KEY_ESTIMATED_SPEND_USD, Double.doubleToLongBits(0.0)));
        inputPricePerM = parseDoubleSafe(prefs.getString(KEY_INPUT_PRICE_PER_M, "5"), 5.0);
        outputPricePerM = parseDoubleSafe(prefs.getString(KEY_OUTPUT_PRICE_PER_M, "30"), 30.0);

        ScrollView pageScroll = new ScrollView(this);
        pageScroll.setFillViewport(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 28, 28, 28);
        root.setBackgroundColor(0xFFF7F7F7);
        pageScroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = new TextView(this);
        title.setText("Мыслитель 0.7.2.1");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText("Седьмой прототип 0.7.3: автопилот после каждого действия ждёт, делает новый скрин и продолжает. Добавлен tap_sequence для игр и шахмат, чтобы выполнять два тапа подряд.");
        subtitle.setTextSize(14);
        subtitle.setPadding(0, 8, 0, 18);
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));

        TextView modelInfo = new TextView(this);
        modelInfo.setText("Модель в этой сборке: " + MODEL + ". Режимы — это разные инструкции к одной модели, а не разные модели.");
        modelInfo.setTextSize(13);
        modelInfo.setPadding(0, 0, 0, 12);
        root.addView(modelInfo, new LinearLayout.LayoutParams(-1, -2));

        apiKeyInput = new EditText(this);
        apiKeyInput.setHint("OpenAI API key: sk-...");
        apiKeyInput.setSingleLine(true);
        apiKeyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        apiKeyInput.setText(prefs.getString(KEY_OPENAI, ""));
        root.addView(apiKeyInput, new LinearLayout.LayoutParams(-1, -2));

        Button saveKeyButton = new Button(this);
        saveKeyButton.setText("Сохранить API ключ на телефоне");
        saveKeyButton.setOnClickListener(v -> {
            prefs.edit().putString(KEY_OPENAI, apiKeyInput.getText().toString().trim()).apply();
            appendLog("Система: API ключ сохранён локально в приложении. Для публичной версии так делать нельзя — нужен сервер-прокси.");
        });
        root.addView(saveKeyButton, new LinearLayout.LayoutParams(-1, -2));

        TextView modeTitle = new TextView(this);
        modeTitle.setText("Режим работы:");
        modeTitle.setTextSize(15);
        modeTitle.setTypeface(Typeface.DEFAULT_BOLD);
        modeTitle.setPadding(0, 14, 0, 4);
        root.addView(modeTitle, new LinearLayout.LayoutParams(-1, -2));

        modeSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, MODES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modeSpinner.setAdapter(adapter);
        modeSpinner.setSelection(indexOfMode(selectedMode));
        modeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                setMode(MODES[position]);
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        root.addView(modeSpinner, new LinearLayout.LayoutParams(-1, -2));

        modeDescriptionText = new TextView(this);
        modeDescriptionText.setText(modeLongDescription(selectedMode));
        modeDescriptionText.setTextSize(12);
        modeDescriptionText.setPadding(0, 6, 0, 10);
        root.addView(modeDescriptionText, new LinearLayout.LayoutParams(-1, -2));

        contextInput = new EditText(this);
        contextInput.setHint("Контекст/цель: например, помогай мне разобраться в настройках Android");
        contextInput.setMinLines(2);
        contextInput.setGravity(Gravity.TOP);
        contextInput.setText(sessionContext);
        root.addView(contextInput, new LinearLayout.LayoutParams(-1, -2));

        Button saveContextButton = new Button(this);
        saveContextButton.setText("Сохранить контекст сессии");
        saveContextButton.setOnClickListener(v -> saveContextFromField());
        root.addView(saveContextButton, new LinearLayout.LayoutParams(-1, -2));

        Button clearContextButton = new Button(this);
        clearContextButton.setText("Очистить контекст сессии");
        clearContextButton.setOnClickListener(v -> {
            sessionContext = "";
            liveContext = "";
            contextInput.setText("");
            prefs.edit().putString(KEY_SESSION_CONTEXT, "").apply();
            appendLog("Система: контекст сессии очищен.");
            OverlayService.updatePanelText("Контекст сессии очищен.");
        });
        root.addView(clearContextButton, new LinearLayout.LayoutParams(-1, -2));

        TextView apiBudgetTitle = new TextView(this);
        apiBudgetTitle.setText("API бюджет и примерный расход:");
        apiBudgetTitle.setTextSize(15);
        apiBudgetTitle.setTypeface(Typeface.DEFAULT_BOLD);
        apiBudgetTitle.setPadding(0, 18, 0, 4);
        root.addView(apiBudgetTitle, new LinearLayout.LayoutParams(-1, -2));

        balanceView = new TextView(this);
        balanceView.setTextSize(13);
        balanceView.setPadding(0, 0, 0, 8);
        root.addView(balanceView, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout budgetRow = new LinearLayout(this);
        budgetRow.setOrientation(LinearLayout.HORIZONTAL);
        budgetInput = new EditText(this);
        budgetInput.setHint("Бюджет, $ например 10");
        budgetInput.setSingleLine(true);
        budgetInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        budgetInput.setText(formatMoneyPlain(budgetUsd));
        budgetRow.addView(budgetInput, new LinearLayout.LayoutParams(0, -2, 1f));
        Button saveBudgetButton = new Button(this);
        saveBudgetButton.setText("Сохранить бюджет");
        saveBudgetButton.setOnClickListener(v -> saveBudgetSettings());
        budgetRow.addView(saveBudgetButton, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(budgetRow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout priceRow = new LinearLayout(this);
        priceRow.setOrientation(LinearLayout.HORIZONTAL);
        inputPriceInput = new EditText(this);
        inputPriceInput.setHint("input $/1M");
        inputPriceInput.setSingleLine(true);
        inputPriceInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        inputPriceInput.setText(formatMoneyPlain(inputPricePerM));
        priceRow.addView(inputPriceInput, new LinearLayout.LayoutParams(0, -2, 1f));
        outputPriceInput = new EditText(this);
        outputPriceInput.setHint("output $/1M");
        outputPriceInput.setSingleLine(true);
        outputPriceInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        outputPriceInput.setText(formatMoneyPlain(outputPricePerM));
        priceRow.addView(outputPriceInput, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(priceRow, new LinearLayout.LayoutParams(-1, -2));

        Button resetUsageButton = new Button(this);
        resetUsageButton.setText("Сбросить локальный счётчик расхода");
        resetUsageButton.setOnClickListener(v -> resetUsageEstimate());
        root.addView(resetUsageButton, new LinearLayout.LayoutParams(-1, -2));

        TextView budgetNote = new TextView(this);
        budgetNote.setText("Это локальная оценка по токенам из ответов API, а не официальный баланс OpenAI. Реальный баланс смотри в Billing на Platform.");
        budgetNote.setTextSize(12);
        root.addView(budgetNote, new LinearLayout.LayoutParams(-1, -2));
        updateBalanceView();

        TextView overlayTitle = new TextView(this);
        overlayTitle.setText("Панель поверх экрана:");
        overlayTitle.setTextSize(15);
        overlayTitle.setTypeface(Typeface.DEFAULT_BOLD);
        overlayTitle.setPadding(0, 18, 0, 4);
        root.addView(overlayTitle, new LinearLayout.LayoutParams(-1, -2));

        Button permissionButton = new Button(this);
        permissionButton.setText("1. Разрешить панель поверх экрана");
        permissionButton.setOnClickListener(v -> requestOverlayPermission());
        root.addView(permissionButton, new LinearLayout.LayoutParams(-1, -2));

        Button startOverlayButton = new Button(this);
        startOverlayButton.setText("2. Включить нижнюю панель");
        startOverlayButton.setOnClickListener(v -> startOverlayPanel());
        root.addView(startOverlayButton, new LinearLayout.LayoutParams(-1, -2));

        Button stopOverlayButton = new Button(this);
        stopOverlayButton.setText("Скрыть нижнюю панель");
        stopOverlayButton.setOnClickListener(v -> {
            stopService(new Intent(this, OverlayService.class));
            appendLog("Система: нижняя панель скрыта.");
        });
        root.addView(stopOverlayButton, new LinearLayout.LayoutParams(-1, -2));

        TextView screenTitle = new TextView(this);
        screenTitle.setText("Просмотр экрана:");
        screenTitle.setTextSize(15);
        screenTitle.setTypeface(Typeface.DEFAULT_BOLD);
        screenTitle.setPadding(0, 18, 0, 4);
        root.addView(screenTitle, new LinearLayout.LayoutParams(-1, -2));

        Button screenButton = new Button(this);
        screenButton.setText("3. Разрешить / заново запросить просмотр экрана");
        screenButton.setOnClickListener(v -> requestScreenCapturePermission());
        root.addView(screenButton, new LinearLayout.LayoutParams(-1, -2));

        analyzeButton = new Button(this);
        analyzeButton.setText("4. Анализировать текущий экран");
        analyzeButton.setOnClickListener(v -> analyzeCurrentScreen());
        root.addView(analyzeButton, new LinearLayout.LayoutParams(-1, -2));

        Button stopScreenButton = new Button(this);
        stopScreenButton.setText("Остановить просмотр экрана");
        stopScreenButton.setOnClickListener(v -> stopScreenCapture());
        root.addView(stopScreenButton, new LinearLayout.LayoutParams(-1, -2));

        Button resetScreenButton = new Button(this);
        resetScreenButton.setText("Сбросить просмотр и запросить разрешение заново");
        resetScreenButton.setOnClickListener(v -> {
            stopScreenCapture();
            mainHandler.postDelayed(this::requestScreenCapturePermission, 450);
        });
        root.addView(resetScreenButton, new LinearLayout.LayoutParams(-1, -2));

        liveStartButton = new Button(this);
        liveStartButton.setText("5. Запустить live-комментарии (каждые 12 сек)");
        liveStartButton.setOnClickListener(v -> startLiveMode());
        root.addView(liveStartButton, new LinearLayout.LayoutParams(-1, -2));

        liveStopButton = new Button(this);
        liveStopButton.setText("Остановить live-комментарии");
        liveStopButton.setOnClickListener(v -> stopLiveMode());
        liveStopButton.setEnabled(false);
        root.addView(liveStopButton, new LinearLayout.LayoutParams(-1, -2));

        TextView controlTitle = new TextView(this);
        controlTitle.setText("Управление телефоном:");
        controlTitle.setTextSize(15);
        controlTitle.setTypeface(Typeface.DEFAULT_BOLD);
        controlTitle.setPadding(0, 18, 0, 4);
        root.addView(controlTitle, new LinearLayout.LayoutParams(-1, -2));

        TextView controlNote = new TextView(this);
        controlNote.setText("Работает через специальное разрешение Android Accessibility. Для безопасности команда должна быть записана в поле сообщения. Опасные действия модель должна блокировать.");
        controlNote.setTextSize(12);
        root.addView(controlNote, new LinearLayout.LayoutParams(-1, -2));

        Button accessibilityButton = new Button(this);
        accessibilityButton.setText("6. Разрешить управление телефоном");
        accessibilityButton.setOnClickListener(v -> requestAccessibilityPermission());
        root.addView(accessibilityButton, new LinearLayout.LayoutParams(-1, -2));

        autoStepButton = new Button(this);
        autoStepButton.setText("7. Автошаг: ИИ выполнит 1 действие");
        autoStepButton.setOnClickListener(v -> runControlStep(null, false));
        root.addView(autoStepButton, new LinearLayout.LayoutParams(-1, -2));

        autopilotStartButton = new Button(this);
        autopilotStartButton.setText("Запустить автопилот осторожно");
        autopilotStartButton.setOnClickListener(v -> startAutopilot(null));
        root.addView(autopilotStartButton, new LinearLayout.LayoutParams(-1, -2));

        autopilotStopButton = new Button(this);
        autopilotStopButton.setText("Остановить автопилот");
        autopilotStopButton.setEnabled(false);
        autopilotStopButton.setOnClickListener(v -> stopAutopilot());
        root.addView(autopilotStopButton, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scrollView = new ScrollView(this);
        chatLog = new TextView(this);
        chatLog.setTextSize(15);
        chatLog.setText("Мыслитель: Напиши вопрос и нажми “Спросить”.\n");
        chatLog.setPadding(16, 16, 16, 16);
        scrollView.addView(chatLog);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, dp(220));
        scrollParams.setMargins(0, 16, 0, 16);
        root.addView(scrollView, scrollParams);

        messageInput = new EditText(this);
        messageInput.setHint("Например: запомни, что я настраиваю разрешения для приложения");
        messageInput.setMinLines(2);
        messageInput.setGravity(Gravity.TOP);
        root.addView(messageInput, new LinearLayout.LayoutParams(-1, -2));

        askButton = new Button(this);
        askButton.setText("Спросить Мыслителя");
        askButton.setOnClickListener(v -> askOpenAI());
        root.addView(askButton, new LinearLayout.LayoutParams(-1, -2));

        TextView warning = new TextView(this);
        warning.setText("Важно: 0.7 умеет пробовать управлять телефоном только после отдельного разрешения Accessibility. Держи кнопку “Остановить автопилот” доступной, не запускай на банках, платежах, паролях, 2FA и личных переписках. Панель можно двигать перетаскиванием верхней/пустой области, но кнопок ↑/↓ на ней больше нет.");
        warning.setTextSize(12);
        warning.setPadding(0, 12, 0, 0);
        root.addView(warning, new LinearLayout.LayoutParams(-1, -2));

        setContentView(pageScroll);
        OverlayService.updatePanelText("Режим: " + selectedMode + ". Модель: " + MODEL + ". Контекст: " + compactContextForUi());
    }


    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (activeActivity != null && activeActivity.get() == this) activeActivity = null;
    }

    private int indexOfMode(String mode) {
        for (int i = 0; i < MODES.length; i++) if (MODES[i].equals(mode)) return i;
        return 0;
    }

    private void setMode(String mode) {
        selectedMode = mode;
        if (prefs != null) prefs.edit().putString(KEY_MODE, mode).apply();
        if (modeDescriptionText != null) modeDescriptionText.setText(modeLongDescription(mode));
        OverlayService.updatePanelText("Режим: " + selectedMode + ". " + modeShortHint(mode));
    }

    private String modeShortHint(String mode) {
        if ("Навигатор".equals(mode)) return "Буду подсказывать следующий шаг.";
        if ("Учитель".equals(mode)) return "Буду объяснять происходящее простыми словами.";
        if ("Антиошибка".equals(mode)) return "Буду искать ошибки, риски и странные места.";
        if ("Тихий".equals(mode)) return "Буду писать только важное.";
        return "Буду кратко комментировать экран.";
    }

    private String modeLongDescription(String mode) {
        if ("Навигатор".equals(mode)) return "Навигатор: ищет следующий конкретный шаг. Формат: что нажать/куда перейти/что проверить. Минимум описаний.";
        if ("Учитель".equals(mode)) return "Учитель: объясняет смысл происходящего на экране простыми словами. Подходит для обучения и разбора интерфейсов.";
        if ("Антиошибка".equals(mode)) return "Антиошибка: ищет ошибки, предупреждения, опасные действия, неверные настройки и предлагает безопасное исправление.";
        if ("Тихий".equals(mode)) return "Тихий: молчит или пишет “Без изменений”, если ничего важного. Подходит для долгого live, чтобы не спамить.";
        return "Комментатор: коротко описывает, что происходит на экране, и даёт один полезный совет. Это базовый режим.";
    }

    private String modeInstruction() {
        if ("Навигатор".equals(selectedMode)) return "Ты в режиме Навигатор: не просто описывай экран, а дай следующий конкретный шаг. Формат: 1) что нажать/куда перейти; 2) зачем. Максимум 2 короткие строки.";
        if ("Учитель".equals(selectedMode)) return "Ты в режиме Учитель: объясняй происходящее простым языком, как наставник. Не командуй, а помогай понять смысл интерфейса и действий.";
        if ("Антиошибка".equals(selectedMode)) return "Ты в режиме Антиошибка: приоритет — заметить ошибки, предупреждения, риски, неправильные действия, приватные данные и странные места. Дай короткое безопасное исправление.";
        if ("Тихий".equals(selectedMode)) return "Ты в режиме Тихий: отвечай только когда есть новое или важное. Если экран похож на предыдущий и нет риска/ошибки, ответь ровно: “Без изменений”.";
        return "Ты в режиме Комментатор: кратко скажи, что происходит на экране, и добавь один полезный совет. Не растягивай ответ.";
    }

    private void saveContextFromField() {
        sessionContext = contextInput.getText().toString().trim();
        prefs.edit().putString(KEY_SESSION_CONTEXT, sessionContext).apply();
        appendLog("Система: контекст сессии сохранён: " + (sessionContext.isEmpty() ? "пусто" : sessionContext));
        OverlayService.updatePanelText("Контекст сохранён: " + compactContextForUi());
    }

    private String compactContextForUi() {
        if (sessionContext == null || sessionContext.trim().isEmpty()) return "пока пустой";
        return trimForContext(sessionContext, 120);
    }

    private double parseDoubleSafe(String text, double fallback) {
        try {
            if (text == null) return fallback;
            String normalized = text.trim().replace(',', '.');
            if (normalized.isEmpty()) return fallback;
            return Double.parseDouble(normalized);
        } catch (Exception e) {
            return fallback;
        }
    }

    private String formatMoneyPlain(double value) {
        if (Math.abs(value - Math.round(value)) < 0.0001) return String.valueOf((long) Math.round(value));
        return String.format(java.util.Locale.US, "%.4f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private String formatUsd(double value) {
        return String.format(java.util.Locale.US, "$%.4f", Math.max(0.0, value));
    }

    private void updateBalanceView() {
        if (balanceView == null) return;
        double remaining = Math.max(0.0, budgetUsd - estimatedSpendUsd);
        balanceView.setText("Модель: " + MODEL + " | Бюджет: " + formatUsd(budgetUsd) + " | Примерно потрачено: " + formatUsd(estimatedSpendUsd) + " | Осталось по локальной оценке: " + formatUsd(remaining));
    }

    private void readBudgetSettingsFromFields() {
        budgetUsd = parseDoubleSafe(budgetInput == null ? null : budgetInput.getText().toString(), budgetUsd);
        inputPricePerM = parseDoubleSafe(inputPriceInput == null ? null : inputPriceInput.getText().toString(), inputPricePerM);
        outputPricePerM = parseDoubleSafe(outputPriceInput == null ? null : outputPriceInput.getText().toString(), outputPricePerM);
        prefs.edit()
                .putString(KEY_BUDGET_USD, formatMoneyPlain(budgetUsd))
                .putString(KEY_INPUT_PRICE_PER_M, formatMoneyPlain(inputPricePerM))
                .putString(KEY_OUTPUT_PRICE_PER_M, formatMoneyPlain(outputPricePerM))
                .apply();
        updateBalanceView();
    }

    private void saveBudgetSettings() {
        readBudgetSettingsFromFields();
        appendLog("Система: настройки локального бюджета сохранены. Это оценка, не официальный баланс OpenAI.");
    }

    private void resetUsageEstimate() {
        estimatedSpendUsd = 0.0;
        prefs.edit().putLong(KEY_ESTIMATED_SPEND_USD, Double.doubleToLongBits(estimatedSpendUsd)).apply();
        updateBalanceView();
        appendLog("Система: локальный счётчик расхода сброшен.");
        OverlayService.updatePanelText("Локальный счётчик расхода API сброшен.");
    }

    private void recordUsageFromResponse(JSONObject json) {
        try {
            JSONObject usage = json.optJSONObject("usage");
            if (usage == null) return;
            int inputTokens = usage.optInt("input_tokens", 0);
            int outputTokens = usage.optInt("output_tokens", 0);
            if (inputTokens <= 0 && outputTokens <= 0) return;
            double cost = (inputTokens / 1000000.0) * inputPricePerM + (outputTokens / 1000000.0) * outputPricePerM;
            estimatedSpendUsd += cost;
            prefs.edit().putLong(KEY_ESTIMATED_SPEND_USD, Double.doubleToLongBits(estimatedSpendUsd)).apply();
            runOnUiThread(() -> {
                updateBalanceView();
                appendLog("Система: примерный расход этого запроса: " + formatUsd(cost) + " (input " + inputTokens + ", output " + outputTokens + ").");
            });
        } catch (Exception ignored) {}
    }

    private void requestAccessibilityPermission() {
        appendLog("Система: открою настройки специальных возможностей. Найди “Мыслитель — управление телефоном” и включи. После этого вернись назад.");
        OverlayService.updatePanelText("Включи управление в специальных возможностях Android.");
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
    }

    private boolean isControlReady() {
        return MyslitelAccessibilityService.isReady();
    }

    private String currentTaskFromFieldOrDefault(String override) {
        String task = override == null ? "" : override.trim();
        if (task.isEmpty() && messageInput != null) task = messageInput.getText().toString().trim();
        if (task.isEmpty()) task = sessionContext == null ? "" : sessionContext.trim();
        return task;
    }

    private void startAutopilot(String taskOverride) {
        String task = currentTaskFromFieldOrDefault(taskOverride);
        if (task.isEmpty()) {
            appendLog("Система: сначала напиши задачу для автопилота. Например: “открой настройки Wi‑Fi” или “пролистай вниз до нужного пункта”.");
            OverlayService.updatePanelText("Напиши задачу для автопилота в чате панели.");
            return;
        }
        if (!ScreenCaptureService.isReady()) {
            appendLog("Система: сначала разреши просмотр экрана.");
            OverlayService.updatePanelText("Сначала разреши просмотр экрана.");
            return;
        }
        if (!isControlReady()) {
            appendLog("Система: сначала включи управление телефоном в специальных возможностях.");
            OverlayService.updatePanelText("Сначала включи Accessibility-управление.");
            requestAccessibilityPermission();
            return;
        }
        autopilotEnabled = true;
        autopilotStepCount = 0;
        controlErrorStreak = 0;
        lastControlSummary = "";
        autopilotTask = task;
        if (autopilotStartButton != null) autopilotStartButton.setEnabled(false);
        if (autopilotStopButton != null) autopilotStopButton.setEnabled(true);
        appendLog("Система: автопилот запущен. Задача: " + autopilotTask);
        OverlayService.updatePanelText("Автопилот запущен: " + trimForContext(autopilotTask, 90));
        scheduleNextAutopilotStep(1200);
    }

    private void stopAutopilot() {
        autopilotEnabled = false;
        controlErrorStreak = 0;
        if (autopilotStartButton != null) autopilotStartButton.setEnabled(true);
        if (autopilotStopButton != null) autopilotStopButton.setEnabled(false);
        appendLog("Система: автопилот остановлен.");
        OverlayService.updatePanelText("Автопилот остановлен.");
    }

    private void scheduleNextAutopilotStep(long delayMs) {
        mainHandler.postDelayed(() -> {
            if (autopilotEnabled) runControlStep(autopilotTask, true);
        }, delayMs);
    }

    private void runControlStep(String taskOverride, boolean autopilot) {
        String apiKey = apiKeyInput.getText().toString().trim();
        if (apiKey.isEmpty()) {
            appendLog("Система: сначала вставь OpenAI API key.");
            OverlayService.updatePanelText("Сначала сохрани API key.");
            if (autopilot) stopAutopilot();
            return;
        }
        if (!ScreenCaptureService.isReady()) {
            appendLog("Система: сначала разреши просмотр экрана.");
            OverlayService.updatePanelText("Сначала разреши просмотр экрана.");
            if (autopilot) stopAutopilot();
            return;
        }
        if (!isControlReady()) {
            appendLog("Система: сначала включи управление телефоном в специальных возможностях Android.");
            OverlayService.updatePanelText("Сначала включи Accessibility-управление.");
            if (autopilot) stopAutopilot();
            requestAccessibilityPermission();
            return;
        }
        if (controlRunning || analysisRunning) {
            if (autopilot) scheduleNextAutopilotStep(2500);
            else appendLog("Система: уже идёт анализ/действие. Подожди пару секунд.");
            return;
        }
        saveContextFromField();
        readBudgetSettingsFromFields();
        String task = currentTaskFromFieldOrDefault(taskOverride);
        if (task.isEmpty()) {
            appendLog("Система: напиши задачу для управления. Без задачи ИИ не будет нажимать сам.");
            OverlayService.updatePanelText("Напиши задачу: что надо сделать на телефоне.");
            if (autopilot) stopAutopilot();
            return;
        }
        if (!autopilot && messageInput != null && taskOverride == null) messageInput.setText("");

        if (autopilot && autopilotStepCount >= AUTOPILOT_MAX_STEPS) {
            appendLog("Система: автопилот остановлен по лимиту шагов. Если нужно продолжить — снова нажми “Авто”.");
            OverlayService.updatePanelText("Авто остановлен по лимиту шагов.");
            stopAutopilot();
            return;
        }

        String directApp = detectOpenAppTask(task);
        boolean alreadyTriedDirectOpen = directApp != null && lastControlSummary.toLowerCase(java.util.Locale.ROOT).contains(directApp.toLowerCase(java.util.Locale.ROOT));
        if (directApp != null && !alreadyTriedDirectOpen) {
            boolean packageOpen = openInstalledAppByLabel(directApp);
            if (packageOpen) {
                String result = "Открываю приложение: " + directApp + ".";
                appendLog("Система: приложение открыто напрямую через список установленных приложений: " + directApp);
                OverlayService.updatePanelText(result);
                rememberAnswer(result, false);
                lastControlSummary = result;
                if (autopilot) scheduleNextAutopilotStep(AUTOPILOT_INTERVAL_MS);
                return;
            }
            if (isControlReady()) {
                boolean directOk = MyslitelAccessibilityService.openAppByLabel(directApp);
                if (directOk) {
                    String result = "Открываю приложение: " + directApp + ".";
                    appendLog("Система: прямое действие выполнено по видимому ярлыку: open_app " + directApp);
                    OverlayService.updatePanelText(result);
                    rememberAnswer(result, false);
                    lastControlSummary = result;
                    if (autopilot) scheduleNextAutopilotStep(AUTOPILOT_INTERVAL_MS);
                    return;
                }
            }
        }

        controlRunning = true;
        if (autoStepButton != null) autoStepButton.setEnabled(false);
        if (askButton != null) askButton.setEnabled(false);
        if (autopilot) {
            autopilotStepCount++;
            appendLog("Система: автопилот, шаг #" + autopilotStepCount + "… Задача: " + task);
            OverlayService.updatePanelText("Автопилот шаг #" + autopilotStepCount + ": делаю новый скрин…");
        } else {
            appendLog("Система: автошаг… Задача: " + task);
            OverlayService.updatePanelText("Автошаг: делаю скрин и выбираю действие…");
        }

        String finalTask = task;
        executor.execute(() -> {
            try {
                // Небольшая пауза перед скрином: Android/лаунчер/игра должны успеть отрисовать экран после прошлого действия.
                Thread.sleep(autopilot ? 1200 : 650);
                Bitmap screenshot = ScreenCaptureService.acquireScreenshotBitmap();
                if (screenshot == null) throw new Exception("не удалось получить кадр для управления.");
                String dataUrl = bitmapToJpegDataUrl(screenshot);
                screenshot.recycle();

                String jsonText = null;
                JSONObject command = null;
                Exception parseError = null;
                for (int attempt = 1; attempt <= 2; attempt++) {
                    try {
                        jsonText = callControlApiWithImage(apiKey, finalTask, dataUrl, autopilot, attempt);
                        command = extractCommandJson(jsonText);
                        break;
                    } catch (Exception ex) {
                        parseError = ex;
                    }
                }
                if (command == null) throw parseError == null ? new Exception("модель не вернула команду") : parseError;

                controlErrorStreak = 0;
                String say = command.optString("say", "Действие получено.").trim();
                String action = command.optString("action", "none").trim().toLowerCase(java.util.Locale.US);
                String risk = command.optString("risk", "safe").trim().toLowerCase(java.util.Locale.US);
                boolean done = command.optBoolean("done", false);

                String finalJsonText = jsonText;
                runOnUiThread(() -> appendLog("Мыслитель управление: " + finalJsonText));

                if (!"safe".equals(risk)) {
                    runOnUiThread(() -> {
                        appendLog("Система: действие не выполнено, потому что risk=" + risk + ".");
                        OverlayService.updatePanelText(say.isEmpty() ? "Действие заблокировано." : say);
                    });
                    if (autopilot) runOnUiThread(this::stopAutopilot);
                    return;
                }

                boolean ok = executeAccessibilityCommand(command);
                runOnUiThread(() -> {
                    String result = say.isEmpty() ? (ok ? "Действие выполнено." : "Не удалось выполнить действие.") : say;
                    appendLog("Система: " + (ok ? "выполнено" : "не выполнено") + ": " + action);
                    OverlayService.updatePanelText(result);
                    rememberAnswer(result, false);
                    lastControlSummary = "Шаг " + autopilotStepCount + ": action=" + action + ", ok=" + ok + ", say=" + result;
                    if (done) {
                        appendLog("Система: модель считает задачу завершённой.");
                        if (autopilot) stopAutopilot();
                    }
                });
            } catch (Exception e) {
                final boolean fallbackChess = tryFallbackChessMove(finalTask);
                runOnUiThread(() -> {
                    if (fallbackChess) {
                        controlErrorStreak = 0;
                        String result = "Модель не дала JSON, поэтому сделал тестовый шахматный тап: e2 → e4. Дальше сделаю новый скрин.";
                        appendLog("Система: " + result);
                        OverlayService.updatePanelText(result);
                        lastControlSummary = "fallback_chess_e2e4 ok=true";
                        return;
                    }
                    controlErrorStreak++;
                    String msg = e.getMessage() == null ? "пустой ответ" : e.getMessage();
                    appendLog("Ошибка управления: " + msg);
                    if (autopilot) {
                        OverlayService.updatePanelText("Не получил команду. Повторяю после нового скрина…");
                        if (controlErrorStreak >= 5) stopAutopilot();
                    } else {
                        OverlayService.updatePanelText("Не получил команду. Нажми Автошаг ещё раз.");
                    }
                });
            } finally {
                runOnUiThread(() -> {
                    controlRunning = false;
                    if (autoStepButton != null) autoStepButton.setEnabled(true);
                    if (askButton != null) askButton.setEnabled(true);
                    if (autopilot && autopilotEnabled) scheduleNextAutopilotStep(AUTOPILOT_INTERVAL_MS);
                });
            }
        });
    }

    private String detectOpenAppTask(String task) {
        if (task == null) return null;
        String lower = task.trim().toLowerCase(java.util.Locale.ROOT);
        if (lower.isEmpty()) return null;
        boolean openIntent = lower.contains("открой") || lower.contains("запусти") || lower.contains("включи") || lower.contains("найди и открой");
        if (!openIntent) return null;
        if (lower.contains("шахмат")) return "Шахматы";
        String[] keys = new String[]{"открой", "запусти", "включи"};
        for (String key : keys) {
            int idx = lower.indexOf(key);
            if (idx >= 0) {
                String rest = task.substring(Math.min(task.length(), idx + key.length())).trim();
                rest = rest.replaceAll("(?i)^(приложение|апп|app)\\s+", "").trim();
                if (!rest.isEmpty() && rest.length() <= 40) return rest;
            }
        }
        return null;
    }

    private boolean openInstalledAppByLabel(String appName) {
        if (appName == null || appName.trim().isEmpty()) return false;
        String needle = appName.trim().toLowerCase(java.util.Locale.ROOT);
        try {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(0);
            ApplicationInfo best = null;
            for (ApplicationInfo info : apps) {
                Intent launchIntent = pm.getLaunchIntentForPackage(info.packageName);
                if (launchIntent == null) continue;
                CharSequence labelSeq = pm.getApplicationLabel(info);
                String label = labelSeq == null ? "" : labelSeq.toString().trim().toLowerCase(java.util.Locale.ROOT);
                if (label.isEmpty()) continue;
                if (label.equals(needle)) {
                    best = info;
                    break;
                }
                if (best == null && (label.contains(needle) || needle.contains(label))) best = info;
            }
            if (best == null) return false;
            Intent launch = pm.getLaunchIntentForPackage(best.packageName);
            if (launch == null) return false;
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
            return true;
        } catch (Exception e) {
            appendLog("Система: не удалось открыть приложение напрямую: " + e.getMessage());
            return false;
        }
    }

    private String callControlApiWithImage(String apiKey, String task, String imageDataUrl, boolean autopilot, int attempt) throws Exception {
        String controlPrompt = "" +
                "Ты управляешь Android-телефоном пользователя через приложение Мыслитель. " +
                "У тебя есть текущий скриншот. Верни строго один JSON без markdown и без пояснений вокруг. " +
                "Координаты x/y/x2/y2 указывай в нормализованной системе 0..1000, где 0,0 — левый верх экрана. " +
                "Доступные action: open_app, tap, tap_sequence, swipe, scroll_down, scroll_up, type, back, home, none. " +
                "Если задача — открыть приложение, и ярлык/название приложения видно на экране, предпочитай action open_app с полем app, например {\"action\":\"open_app\",\"app\":\"Шахматы\",\"risk\":\"safe\",\"done\":false}. " +
                "Если нужное приложение не видно на текущей странице лаунчера, используй scroll_down или scroll_up. " +
                "tap используй только если ты уверен в координатах нужной кнопки/иконки. Для игр и шахмат чаще используй tap_sequence: points=[{\"x\":563,\"y\":716},{\"x\":563,\"y\":604}], чтобы сначала выбрать фигуру, потом клетку. " +
                "type используй только если на экране уже активно текстовое поле. " +
                "Для scroll_down будет свайп вверх, чтобы список пошёл вниз; для scroll_up будет свайп вниз. " +
                "Если видишь банк, оплату, пароль, 2FA, личную переписку, удаление данных, покупку или другое опасное действие — верни action none и risk blocked. " +
                "Если не уверен — action none. " +
                "После каждого действия приложение само подождёт и пришлёт новый скриншот, поэтому выбирай только один следующий шаг. " +
                "Если на экране шахматы и задача — играть/выиграть/сделать ход, выбери безопасный легальный ход и верни tap_sequence из двух тапов: первая точка — фигура, вторая — клетка назначения. Если фигура уже выбрана, можно вернуть один tap по клетке назначения. После каждого действия приложение само подождёт и пришлёт новый скрин, поэтому не пытайся делать всю задачу сразу. Если задача явно завершена на текущем экране — поставь done true и action none. Иначе done false. " +
                "Формат: {\"say\":\"коротко по-русски что делаю\",\"action\":\"open_app|tap|tap_sequence|swipe|scroll_down|scroll_up|type|back|home|none\",\"app\":\"\",\"x\":500,\"y\":500,\"x2\":500,\"y2\":300,\"points\":[{\"x\":500,\"y\":700},{\"x\":500,\"y\":500}],\"text\":\"\",\"risk\":\"safe|blocked\",\"done\":false}";

        String userText = "Режим: " + selectedMode + "\n" +
                "Инструкция режима: " + modeInstruction() + "\n" +
                "Контекст пользователя: " + (sessionContext.isEmpty() ? "пока нет" : sessionContext) + "\n" +
                "Предыдущий контекст: " + (liveContext.isEmpty() ? "пока нет" : liveContext) + "\n" +
                "Предыдущее действие управления: " + (lastControlSummary.isEmpty() ? "пока нет" : lastControlSummary) + "\n" +
                "Попытка запроса JSON: " + attempt + " из 2. Если это повторная попытка, верни именно JSON, даже если действие none.\n" +
                "Задача пользователя: " + task + "\n" +
                "Это " + (autopilot ? "новый шаг автопилота после паузы и нового скрина" : "один автошаг") + ". Смотри только на текущий скрин и выбери одно безопасное действие. Для шахмат/игр управляй реальными тапами по экрану.";

        JSONArray input = new JSONArray();
        input.put(new JSONObject()
                .put("role", "system")
                .put("content", new JSONArray().put(new JSONObject()
                        .put("type", "input_text")
                        .put("text", controlPrompt))));
        input.put(new JSONObject()
                .put("role", "user")
                .put("content", new JSONArray()
                        .put(new JSONObject().put("type", "input_text").put("text", userText))
                        .put(new JSONObject().put("type", "input_image").put("image_url", imageDataUrl))));

        JSONObject payload = new JSONObject()
                .put("model", MODEL)
                .put("input", input)
                .put("max_output_tokens", 520);
        return postToResponsesApi(apiKey, payload);
    }

    private JSONObject extractCommandJson(String text) throws Exception {
        if (text == null) throw new Exception("пустой ответ модели");
        String t = text.trim();
        if (t.startsWith("```")) {
            t = t.replace("```json", "").replace("```", "").trim();
        }
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        if (start < 0 || end <= start) throw new Exception("модель не вернула JSON: " + trimForContext(text, 300));
        return new JSONObject(t.substring(start, end + 1));
    }

    private boolean executeTapSequence(JSONObject command) {
        try {
            JSONArray points = command.optJSONArray("points");
            if (points == null || points.length() == 0) {
                int x = command.optInt("x", 500);
                int y = command.optInt("y", 500);
                return MyslitelAccessibilityService.tapNormalized(x, y);
            }
            int[][] xy = new int[Math.min(points.length(), 4)][2];
            for (int i = 0; i < xy.length; i++) {
                JSONObject p = points.optJSONObject(i);
                if (p == null) return false;
                xy[i][0] = p.optInt("x", 500);
                xy[i][1] = p.optInt("y", 500);
            }
            return MyslitelAccessibilityService.tapSequenceNormalized(xy, 420);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean tryFallbackChessMove(String task) {
        try {
            if (task == null) return false;
            String lower = task.toLowerCase(java.util.Locale.ROOT);
            boolean chessTask = lower.contains("шахмат") || lower.contains("сделай ход") || lower.contains("играй");
            boolean justOpen = lower.contains("открой") || lower.contains("найди");
            if (!chessTask || justOpen) return false;
            // Аварийный первый ход для теста управления в шахматах: e2 -> e4.
            // Нормализованные координаты подходят для текущего портретного экрана, где доска занимает почти всю ширину.
            int[][] e2e4 = new int[][]{{563, 716}, {563, 604}};
            return MyslitelAccessibilityService.tapSequenceNormalized(e2e4, 420);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean executeAccessibilityCommand(JSONObject command) {
        String action = command.optString("action", "none").trim().toLowerCase(java.util.Locale.US);
        if ("none".equals(action)) return true;
        if ("open_app".equals(action)) return MyslitelAccessibilityService.openAppByLabel(command.optString("app", ""));
        if ("tap".equals(action)) return MyslitelAccessibilityService.tapNormalized(command.optInt("x", 500), command.optInt("y", 500));
        if ("tap_sequence".equals(action)) return executeTapSequence(command);
        if ("swipe".equals(action)) return MyslitelAccessibilityService.swipeNormalized(command.optInt("x", 500), command.optInt("y", 750), command.optInt("x2", 500), command.optInt("y2", 250), 450);
        if ("scroll_down".equals(action)) return MyslitelAccessibilityService.scrollDown();
        if ("scroll_up".equals(action)) return MyslitelAccessibilityService.scrollUp();
        if ("type".equals(action)) return MyslitelAccessibilityService.typeText(command.optString("text", ""));
        if ("back".equals(action)) return MyslitelAccessibilityService.back();
        if ("home".equals(action)) return MyslitelAccessibilityService.home();
        return false;
    }

    private void requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            appendLog("Система: разрешение уже включено. Теперь нажми “Включить нижнюю панель”.");
            return;
        }
        appendLog("Система: открою настройки. Включи разрешение “Показывать поверх других приложений” для Мыслителя, потом вернись назад.");
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void startOverlayPanel() {
        if (!Settings.canDrawOverlays(this)) {
            appendLog("Система: сначала нужно разрешение “поверх других приложений”. Нажми кнопку 1.");
            requestOverlayPermission();
            return;
        }
        startService(new Intent(this, OverlayService.class));
        appendLog("Система: нижняя панель включена. В 0.7 на панели только чат и кнопки: Спросить, Анализ, Live/Стоп, Автошаг/Авто/Стоп.");
    }

    private void requestScreenCapturePermission() {
        if (projectionManager == null) {
            appendLog("Система: MediaProjection недоступен на этом устройстве.");
            return;
        }
        if (liveModeEnabled) stopLiveMode();
        Intent stopIntent = new Intent(this, ScreenCaptureService.class);
        stopIntent.setAction(ScreenCaptureService.ACTION_STOP);
        try { startService(stopIntent); } catch (Exception ignored) {}
        appendLog("Система: сейчас запрошу просмотр экрана заново. Если до этого нажал “Запретить”, это должно дать новый чистый запрос.");
        OverlayService.updatePanelText("Запрашиваю просмотр экрана заново…");
        mainHandler.postDelayed(() -> startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION), 250);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode != RESULT_OK || data == null) {
                appendLog("Система: просмотр экрана не разрешён. Чтобы попробовать снова, нажми “3. Разрешить / заново запросить просмотр экрана”.");
                OverlayService.updatePanelText("Просмотр не разрешён. Открой приложение и запроси заново.");
                Intent stopIntent = new Intent(this, ScreenCaptureService.class);
                stopIntent.setAction(ScreenCaptureService.ACTION_STOP);
                try { startService(stopIntent); } catch (Exception ignored) {}
                return;
            }
            Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
            serviceIntent.setAction(ScreenCaptureService.ACTION_START);
            serviceIntent.putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode);
            serviceIntent.putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data);
            startForegroundService(serviceIntent);
        }
    }

    private void stopScreenCapture() {
        if (liveModeEnabled) stopLiveMode();
        Intent intent = new Intent(this, ScreenCaptureService.class);
        intent.setAction(ScreenCaptureService.ACTION_STOP);
        startService(intent);
        appendLog("Система: просмотр экрана остановлен. Теперь обычные скриншоты должны работать как раньше.");
        OverlayService.updatePanelText("Просмотр экрана остановлен. Для анализа снова выдай разрешение.");
    }

    private void analyzeCurrentScreen() {
        analyzeCurrentScreenWithTask(null, false);
    }

    private void handleOverlayQuestion(String text) {
        String prompt = text == null ? "" : text.trim();
        if (prompt.isEmpty()) {
            OverlayService.updatePanelText("Напиши сообщение в поле панели.");
            return;
        }
        if (prompt.toLowerCase().startsWith("запомни")) {
            sessionContext = mergeContext(sessionContext, prompt);
            if (contextInput != null) contextInput.setText(sessionContext);
            prefs.edit().putString(KEY_SESSION_CONTEXT, sessionContext).apply();
            appendLog("Ты с панели: " + prompt);
            appendLog("Система: добавил это в контекст сессии.");
            OverlayService.updatePanelText("Запомнил в контексте: " + trimForContext(prompt, 120));
            return;
        }
        if (ScreenCaptureService.isReady()) {
            analyzeCurrentScreenWithTask(prompt, false);
        } else {
            askOpenAIWithPrompt(prompt, true);
        }
    }

    private void startLiveMode() {
        if (liveModeEnabled) {
            appendLog("Система: live уже включён.");
            return;
        }
        if (!ScreenCaptureService.isReady()) {
            appendLog("Система: сначала нажми “Разрешить просмотр экрана”.");
            OverlayService.updatePanelText("Сначала выдай разрешение просмотра экрана.");
            return;
        }
        liveModeEnabled = true;
        liveTickCount = 0;
        if (liveStartButton != null) liveStartButton.setEnabled(false);
        if (liveStopButton != null) liveStopButton.setEnabled(true);
        saveContextFromField();
        appendLog("Система: live-комментарии включены. Режим: " + selectedMode + ". Интервал примерно 12 секунд.");
        OverlayService.updatePanelText("Live включён. Режим: " + selectedMode + ".");
        analyzeCurrentScreenInternal(true, null);
    }

    private void stopLiveMode() {
        liveModeEnabled = false;
        if (liveStartButton != null) liveStartButton.setEnabled(true);
        if (liveStopButton != null) liveStopButton.setEnabled(false);
        appendLog("Система: live-комментарии остановлены. Просмотр экрана может оставаться включённым для ручного анализа.");
        OverlayService.updatePanelText("Live остановлен. Можно писать в поле, нажать “Анализ” или “Стоп просмотр”.");
    }

    private void scheduleNextLiveTick(long delayMs) {
        mainHandler.postDelayed(() -> {
            if (liveModeEnabled) analyzeCurrentScreenInternal(true, null);
        }, delayMs);
    }

    private void analyzeCurrentScreenWithTask(String task, boolean liveMode) {
        analyzeCurrentScreenInternal(liveMode, task);
    }

    private void analyzeCurrentScreenInternal(boolean liveMode, String taskOverride) {
        String apiKey = apiKeyInput.getText().toString().trim();
        if (apiKey.isEmpty()) {
            appendLog("Система: сначала вставь OpenAI API key.");
            OverlayService.updatePanelText("Сначала сохрани API key в приложении.");
            if (liveMode) stopLiveMode();
            return;
        }
        if (!ScreenCaptureService.isReady()) {
            appendLog("Система: сначала нажми “Разрешить просмотр экрана” и выбери весь экран или нужное приложение.");
            OverlayService.updatePanelText("Сначала открой Мыслитель и нажми “Разрешить просмотр экрана”.");
            if (liveMode) stopLiveMode();
            return;
        }
        if (analysisRunning) {
            if (liveMode) scheduleNextLiveTick(3000);
            else appendLog("Система: уже идёт анализ. Подожди пару секунд.");
            return;
        }

        saveContextFromField();
        readBudgetSettingsFromFields();

        String userTask = taskOverride == null ? messageInput.getText().toString().trim() : taskOverride.trim();
        if (userTask.isEmpty()) {
            userTask = liveMode
                    ? "Ты в live-режиме. Следи за происходящим на экране и давай короткий полезный комментарий."
                    : "Коротко прокомментируй, что сейчас происходит на экране, и дай один полезный совет.";
        }

        String finalUserTask;
        String logPrefix;
        if (liveMode) {
            liveTickCount++;
            finalUserTask = buildScreenTask(userTask, true);
            logPrefix = "Live #" + liveTickCount + ": ";
            appendLog("Система: live-анализ кадра #" + liveTickCount + "…");
            OverlayService.updatePanelText("Live #" + liveTickCount + ": смотрю экран…");
        } else {
            finalUserTask = buildScreenTask(userTask, false);
            logPrefix = "Мыслитель: ";
            appendLog("Ты: " + userTask + "\nСистема: анализ текущего экрана…");
            OverlayService.updatePanelText("Смотрю экран с учётом сообщения…");
        }

        analysisRunning = true;
        if (analyzeButton != null) analyzeButton.setEnabled(false);
        if (askButton != null) askButton.setEnabled(false);

        executor.execute(() -> {
            try {
                Thread.sleep(500);
                Bitmap screenshot = ScreenCaptureService.acquireScreenshotBitmap();
                if (screenshot == null) throw new Exception("не удалось получить кадр. Попробуй ещё раз через секунду.");
                String dataUrl = bitmapToJpegDataUrl(screenshot);
                screenshot.recycle();

                String answer = callResponsesApiWithImage(apiKey, finalUserTask, dataUrl);
                runOnUiThread(() -> {
                    appendLog(logPrefix + answer);
                    OverlayService.updatePanelText(answer);
                    rememberAnswer(answer, liveMode);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    String error = "Ошибка анализа экрана: " + e.getMessage();
                    appendLog(error);
                    OverlayService.updatePanelText(liveMode ? "Live ошибка: " + e.getMessage() : "Ошибка анализа: " + e.getMessage());
                });
            } finally {
                runOnUiThread(() -> {
                    analysisRunning = false;
                    if (analyzeButton != null) analyzeButton.setEnabled(true);
                    if (askButton != null) askButton.setEnabled(true);
                    if (liveMode && liveModeEnabled) scheduleNextLiveTick(LIVE_INTERVAL_MS);
                });
            }
        });
    }

    private String buildScreenTask(String userTask, boolean liveMode) {
        StringBuilder sb = new StringBuilder();
        sb.append("Режим работы: ").append(selectedMode).append(".\n");
        sb.append(modeInstruction()).append("\n\n");
        sb.append("Долгосрочный контекст/цель пользователя: ").append(sessionContext.isEmpty() ? "пока нет" : sessionContext).append("\n");
        sb.append("Краткий контекст предыдущих наблюдений: ").append(liveContext.isEmpty() ? "пока нет" : liveContext).append("\n\n");
        sb.append("Текущая просьба пользователя: ").append(userTask).append("\n\n");
        if (liveMode) {
            sb.append("Ответь на русском в 1-2 короткие строки. Скажи только новое/важное и следующий полезный шаг. Не повторяйся, если экран почти не изменился.");
        } else {
            sb.append("Ответь на русском коротко и по делу. Если пользователь просит запомнить что-то, явно подтверди, что это будет учтено в контексте сессии.");
        }
        return sb.toString();
    }

    private void askOpenAI() {
        String prompt = messageInput.getText().toString().trim();
        askOpenAIWithPrompt(prompt, false);
    }

    private void askOpenAIWithPrompt(String prompt, boolean fromOverlay) {
        String apiKey = apiKeyInput.getText().toString().trim();
        if (apiKey.isEmpty()) {
            appendLog("Система: сначала вставь OpenAI API key.");
            OverlayService.updatePanelText("Сначала сохрани API key.");
            return;
        }
        if (prompt == null || prompt.trim().isEmpty()) {
            appendLog("Система: напиши вопрос.");
            OverlayService.updatePanelText("Напиши сообщение.");
            return;
        }

        prompt = prompt.trim();
        saveContextFromField();
        readBudgetSettingsFromFields();
        if (!fromOverlay) messageInput.setText("");
        appendLog((fromOverlay ? "Ты с панели: " : "Ты: ") + prompt);
        askButton.setEnabled(false);
        askButton.setText("Думаю...");
        OverlayService.updatePanelText("Думаю над сообщением…");

        String finalPrompt = prompt;
        executor.execute(() -> {
            try {
                String answer = callResponsesApi(apiKey, finalPrompt);
                runOnUiThread(() -> {
                    appendLog("Мыслитель: " + answer);
                    OverlayService.updatePanelText(answer);
                    rememberAnswer(answer, false);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    appendLog("Ошибка: " + e.getMessage());
                    OverlayService.updatePanelText("Ошибка: " + e.getMessage());
                });
            } finally {
                runOnUiThread(() -> {
                    askButton.setEnabled(true);
                    askButton.setText("Спросить Мыслителя");
                });
            }
        });
    }

    private void rememberAnswer(String answer, boolean fromLive) {
        String clean = trimForContext(answer, 700);
        liveContext = clean;
        if (answer.toLowerCase().contains("запом")) {
            sessionContext = mergeContext(sessionContext, answer);
            if (contextInput != null) contextInput.setText(sessionContext);
            prefs.edit().putString(KEY_SESSION_CONTEXT, sessionContext).apply();
        }
        if (!fromLive && !clean.isEmpty()) {
            liveContext = trimForContext((liveContext + " | " + clean), 900);
        }
    }

    private String mergeContext(String oldContext, String newFact) {
        String base = oldContext == null ? "" : oldContext.trim();
        String addition = newFact == null ? "" : newFact.trim();
        if (addition.isEmpty()) return base;
        String merged = base.isEmpty() ? addition : base + "\n- " + addition;
        return trimForContext(merged, 1200);
    }

    private String trimForContext(String text, int maxChars) {
        if (text == null) return "";
        String clean = text.replace('\n', ' ').trim();
        if (clean.length() <= maxChars) return clean;
        return clean.substring(clean.length() - maxChars);
    }

    private String bitmapToJpegDataUrl(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 65, baos);
        String base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
        return "data:image/jpeg;base64," + base64;
    }

    private String callResponsesApi(String apiKey, String userPrompt) throws Exception {
        JSONArray input = new JSONArray();
        input.put(new JSONObject()
                .put("role", "system")
                .put("content", new JSONArray().put(new JSONObject()
                        .put("type", "input_text")
                        .put("text", "Ты — приложение Мыслитель. Отвечай на русском. " + modeInstruction() + " Долгосрочный контекст пользователя: " + (sessionContext.isEmpty() ? "пока нет" : sessionContext) + ". Краткий контекст последних наблюдений: " + (liveContext.isEmpty() ? "пока нет" : liveContext) + "."))));
        input.put(new JSONObject()
                .put("role", "user")
                .put("content", new JSONArray().put(new JSONObject()
                        .put("type", "input_text")
                        .put("text", userPrompt))));

        JSONObject payload = new JSONObject()
                .put("model", MODEL)
                .put("input", input)
                .put("max_output_tokens", 450);

        return postToResponsesApi(apiKey, payload);
    }

    private String callResponsesApiWithImage(String apiKey, String userTask, String imageDataUrl) throws Exception {
        JSONArray input = new JSONArray();
        input.put(new JSONObject()
                .put("role", "system")
                .put("content", new JSONArray().put(new JSONObject()
                        .put("type", "input_text")
                        .put("text", "Ты — экранный AI-комментатор Android-приложения Мыслитель. Не управляй телефоном, только комментируй, объясняй и советуй. Всегда учитывай режим работы и контекст. Не проси доступы, если они уже выданы."))));
        input.put(new JSONObject()
                .put("role", "user")
                .put("content", new JSONArray()
                        .put(new JSONObject()
                                .put("type", "input_text")
                                .put("text", userTask))
                        .put(new JSONObject()
                                .put("type", "input_image")
                                .put("image_url", imageDataUrl))));

        JSONObject payload = new JSONObject()
                .put("model", MODEL)
                .put("input", input)
                .put("max_output_tokens", 220);

        return postToResponsesApi(apiKey, payload);
    }

    private String postToResponsesApi(String apiKey, JSONObject payload) throws Exception {
        URL url = new URL("https://api.openai.com/v1/responses");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(90000);
        connection.setDoOutput(true);

        byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = connection.getOutputStream()) {
            os.write(bytes);
        }

        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        String response = readAll(stream);
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code + ": " + response);

        JSONObject json = new JSONObject(response);
        recordUsageFromResponse(json);
        String outputText = json.optString("output_text", "").trim();
        if (!outputText.isEmpty()) return outputText;
        return extractTextFallback(json);
    }

    private String extractTextFallback(JSONObject json) {
        try {
            JSONArray output = json.optJSONArray("output");
            if (output == null) return "Ответ пришёл, но приложение не смогло его прочитать.";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < output.length(); i++) {
                JSONObject item = output.optJSONObject(i);
                if (item == null) continue;
                JSONArray content = item.optJSONArray("content");
                if (content == null) continue;
                for (int j = 0; j < content.length(); j++) {
                    JSONObject c = content.optJSONObject(j);
                    if (c != null) {
                        String text = c.optString("text", "");
                        if (!text.isEmpty()) sb.append(text).append("\n");
                    }
                }
            }
            String result = sb.toString().trim();
            return result.isEmpty() ? "Пустой ответ." : result;
        } catch (Exception e) {
            return "Ошибка чтения ответа: " + e.getMessage();
        }
    }

    private String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    private void appendLog(String text) {
        if (chatLog != null) chatLog.append("\n" + text + "\n");
    }
}
