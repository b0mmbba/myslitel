package com.bommbba.myslitel;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String PREFS = "myslitel_prefs";
    private static final String KEY_OPENAI = "openai_api_key";
    private static final String MODEL = "gpt-5.5";
    private static final int REQUEST_MEDIA_PROJECTION = 2303;

    private static WeakReference<MainActivity> activeActivity;

    private EditText apiKeyInput;
    private EditText messageInput;
    private TextView chatLog;
    private Button askButton;
    private Button screenButton;
    private Button analyzeButton;

    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private ImageReader imageReader;
    private VirtualDisplay virtualDisplay;
    private int captureWidth;
    private int captureHeight;
    private int captureDensity;
    private boolean captureReady = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static void analyzeScreenFromOverlay() {
        MainActivity activity = activeActivity == null ? null : activeActivity.get();
        if (activity == null) {
            OverlayService.updatePanelText("Открой приложение “Мыслитель”, нажми “Разрешить просмотр экрана”, затем снова попробуй анализ.");
            return;
        }
        activity.runOnUiThread(activity::analyzeCurrentScreen);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activeActivity = new WeakReference<>(this);
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 28, 28, 28);
        root.setBackgroundColor(0xFFF7F7F7);

        TextView title = new TextView(this);
        title.setText("Мыслитель 0.3");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText("Третий прототип: чат с ИИ + нижняя панель + ручной анализ текущего экрана.");
        subtitle.setTextSize(14);
        subtitle.setPadding(0, 8, 0, 20);
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));

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

        screenButton = new Button(this);
        screenButton.setText("3. Разрешить просмотр экрана");
        screenButton.setOnClickListener(v -> requestScreenCapturePermission());
        root.addView(screenButton, new LinearLayout.LayoutParams(-1, -2));

        analyzeButton = new Button(this);
        analyzeButton.setText("4. Анализировать текущий экран");
        analyzeButton.setOnClickListener(v -> analyzeCurrentScreen());
        root.addView(analyzeButton, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scrollView = new ScrollView(this);
        chatLog = new TextView(this);
        chatLog.setTextSize(15);
        chatLog.setText("Мыслитель: Напиши вопрос и нажми “Спросить”.\n");
        chatLog.setPadding(16, 16, 16, 16);
        scrollView.addView(chatLog);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        scrollParams.setMargins(0, 16, 0, 16);
        root.addView(scrollView, scrollParams);

        messageInput = new EditText(this);
        messageInput.setHint("Например: прокомментируй то, что видно на экране");
        messageInput.setMinLines(2);
        messageInput.setGravity(Gravity.TOP);
        root.addView(messageInput, new LinearLayout.LayoutParams(-1, -2));

        askButton = new Button(this);
        askButton.setText("Спросить Мыслителя");
        askButton.setOnClickListener(v -> askOpenAI());
        root.addView(askButton, new LinearLayout.LayoutParams(-1, -2));

        TextView warning = new TextView(this);
        warning.setText("Важно: в 0.3 нет автопилота и управления телефоном. Анализ экрана запускается вручную: нажал кнопку — отправился один сжатый кадр.");
        warning.setTextSize(12);
        warning.setPadding(0, 12, 0, 0);
        root.addView(warning, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseScreenCapture();
        if (activeActivity != null && activeActivity.get() == this) {
            activeActivity = null;
        }
    }

    private void requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            appendLog("Система: разрешение уже включено. Теперь нажми “Включить нижнюю панель”.");
            return;
        }
        appendLog("Система: открою настройки. Включи разрешение “Показывать поверх других приложений” для Мыслителя, потом вернись назад.");
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())
        );
        startActivity(intent);
    }

    private void startOverlayPanel() {
        if (!Settings.canDrawOverlays(this)) {
            appendLog("Система: сначала нужно разрешение “поверх других приложений”. Нажми кнопку 1.");
            requestOverlayPermission();
            return;
        }
        startService(new Intent(this, OverlayService.class));
        appendLog("Система: нижняя панель включена. В 0.3 на панели есть кнопка “Анализ”.");
    }

    private void requestScreenCapturePermission() {
        if (projectionManager == null) {
            appendLog("Система: MediaProjection недоступен на этом устройстве.");
            return;
        }
        appendLog("Система: сейчас Android спросит разрешение на запись/трансляцию экрана. Это нужно для ручного анализа кадра.");
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_MEDIA_PROJECTION) return;

        if (resultCode != RESULT_OK || data == null) {
            appendLog("Система: разрешение просмотра экрана не выдано.");
            OverlayService.updatePanelText("Просмотр экрана не разрешён. Открой Мыслитель и нажми кнопку 3.");
            return;
        }

        releaseScreenCapture();
        mediaProjection = projectionManager.getMediaProjection(resultCode, data);
        if (mediaProjection == null) {
            appendLog("Система: не получилось запустить просмотр экрана.");
            return;
        }
        mediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                mainHandler.post(() -> {
                    releaseScreenCaptureOnlyViews();
                    captureReady = false;
                    appendLog("Система: Android остановил просмотр экрана.");
                    OverlayService.updatePanelText("Просмотр экрана остановлен. Для анализа снова выдай разрешение.");
                });
            }
        }, mainHandler);

        try {
            startCapturePipeline();
            captureReady = true;
            appendLog("Система: просмотр экрана разрешён. Сверни приложение, открой нужный экран и нажми “Анализ” на нижней панели.");
            OverlayService.updatePanelText("Просмотр разрешён. Открой нужный экран и нажми “Анализ”.");
        } catch (Exception e) {
            appendLog("Система: не удалось подготовить захват экрана: " + e.getMessage());
            OverlayService.updatePanelText("Не удалось подготовить просмотр экрана: " + e.getMessage());
        }
    }

    private void startCapturePipeline() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        captureWidth = Math.max(1, metrics.widthPixels);
        captureHeight = Math.max(1, metrics.heightPixels);
        captureDensity = metrics.densityDpi;

        imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "myslitel-screen",
                captureWidth,
                captureHeight,
                captureDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                mainHandler
        );
    }

    private void analyzeCurrentScreen() {
        String apiKey = apiKeyInput.getText().toString().trim();
        if (apiKey.isEmpty()) {
            appendLog("Система: сначала вставь OpenAI API key.");
            OverlayService.updatePanelText("Сначала сохрани API key в приложении.");
            return;
        }
        if (!captureReady || imageReader == null) {
            appendLog("Система: сначала нажми “Разрешить просмотр экрана”.");
            OverlayService.updatePanelText("Сначала открой Мыслитель и нажми “Разрешить просмотр экрана”.");
            return;
        }

        String userTask = messageInput.getText().toString().trim();
        if (userTask.isEmpty()) {
            userTask = "Коротко прокомментируй, что сейчас происходит на экране, и дай один полезный совет.";
        }

        appendLog("Ты: анализ текущего экрана. Задача: " + userTask);
        OverlayService.updatePanelText("Смотрю экран…");
        analyzeButton.setEnabled(false);
        askButton.setEnabled(false);

        String finalUserTask = userTask;
        executor.execute(() -> {
            try {
                Thread.sleep(500);
                Bitmap screenshot = acquireScreenshotBitmap();
                if (screenshot == null) {
                    throw new Exception("не удалось получить кадр. Попробуй ещё раз через секунду.");
                }
                String dataUrl = bitmapToJpegDataUrl(screenshot);
                screenshot.recycle();

                String answer = callResponsesApiWithImage(apiKey, finalUserTask, dataUrl);
                runOnUiThread(() -> {
                    appendLog("Мыслитель: " + answer);
                    OverlayService.updatePanelText(answer);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    appendLog("Ошибка анализа экрана: " + e.getMessage());
                    OverlayService.updatePanelText("Ошибка анализа: " + e.getMessage());
                });
            } finally {
                runOnUiThread(() -> {
                    analyzeButton.setEnabled(true);
                    askButton.setEnabled(true);
                });
            }
        });
    }

    private Bitmap acquireScreenshotBitmap() {
        Image image = null;
        try {
            image = imageReader.acquireLatestImage();
            if (image == null) {
                return null;
            }

            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * captureWidth;

            Bitmap paddedBitmap = Bitmap.createBitmap(
                    captureWidth + rowPadding / pixelStride,
                    captureHeight,
                    Bitmap.Config.ARGB_8888
            );
            paddedBitmap.copyPixelsFromBuffer(buffer);
            Bitmap cropped = Bitmap.createBitmap(paddedBitmap, 0, 0, captureWidth, captureHeight);
            paddedBitmap.recycle();

            int maxWidth = 720;
            if (cropped.getWidth() > maxWidth) {
                float ratio = maxWidth / (float) cropped.getWidth();
                int newHeight = Math.max(1, Math.round(cropped.getHeight() * ratio));
                Bitmap scaled = Bitmap.createScaledBitmap(cropped, maxWidth, newHeight, true);
                cropped.recycle();
                return scaled;
            }
            return cropped;
        } catch (Exception e) {
            return null;
        } finally {
            if (image != null) image.close();
        }
    }

    private String bitmapToJpegDataUrl(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 65, baos);
        String base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
        return "data:image/jpeg;base64," + base64;
    }

    private void askOpenAI() {
        String apiKey = apiKeyInput.getText().toString().trim();
        String prompt = messageInput.getText().toString().trim();

        if (apiKey.isEmpty()) {
            appendLog("Система: сначала вставь OpenAI API key.");
            return;
        }
        if (prompt.isEmpty()) {
            appendLog("Система: напиши вопрос.");
            return;
        }

        messageInput.setText("");
        appendLog("Ты: " + prompt);
        askButton.setEnabled(false);
        askButton.setText("Думаю...");

        executor.execute(() -> {
            try {
                String answer = callResponsesApi(apiKey, prompt);
                runOnUiThread(() -> appendLog("Мыслитель: " + answer));
            } catch (Exception e) {
                runOnUiThread(() -> appendLog("Ошибка: " + e.getMessage()));
            } finally {
                runOnUiThread(() -> {
                    askButton.setEnabled(true);
                    askButton.setText("Спросить Мыслителя");
                });
            }
        });
    }

    private String callResponsesApi(String apiKey, String userPrompt) throws Exception {
        JSONArray input = new JSONArray();
        input.put(new JSONObject()
                .put("role", "system")
                .put("content", new JSONArray().put(new JSONObject()
                        .put("type", "input_text")
                        .put("text", "Ты — приложение Мыслитель: коротко, понятно и по делу помогаешь пользователю. Отвечай на русском. Для будущей версии помни: продукт должен стать экранным AI-комментатором Android."))));
        input.put(new JSONObject()
                .put("role", "user")
                .put("content", new JSONArray().put(new JSONObject()
                        .put("type", "input_text")
                        .put("text", userPrompt))));

        JSONObject payload = new JSONObject()
                .put("model", MODEL)
                .put("input", input)
                .put("max_output_tokens", 600);

        return postToResponsesApi(apiKey, payload);
    }

    private String callResponsesApiWithImage(String apiKey, String userTask, String imageDataUrl) throws Exception {
        JSONArray input = new JSONArray();
        input.put(new JSONObject()
                .put("role", "system")
                .put("content", new JSONArray().put(new JSONObject()
                        .put("type", "input_text")
                        .put("text", "Ты — экранный AI-комментатор Android-приложения Мыслитель. Пользователь вручную отправил один кадр экрана. Отвечай на русском, коротко: 1) что видно/что происходит, 2) один полезный совет. Не проси доступы и не утверждай, что управляешь телефоном."))));
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
        if (code < 200 || code >= 300) {
            throw new Exception("HTTP " + code + ": " + response);
        }

        JSONObject json = new JSONObject(response);
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
        chatLog.append("\n" + text + "\n");
    }

    private void releaseScreenCapture() {
        releaseScreenCaptureOnlyViews();
        if (mediaProjection != null) {
            try { mediaProjection.stop(); } catch (Exception ignored) {}
            mediaProjection = null;
        }
        captureReady = false;
    }

    private void releaseScreenCaptureOnlyViews() {
        if (virtualDisplay != null) {
            try { virtualDisplay.release(); } catch (Exception ignored) {}
            virtualDisplay = null;
        }
        if (imageReader != null) {
            try { imageReader.close(); } catch (Exception ignored) {}
            imageReader = null;
        }
    }
}
