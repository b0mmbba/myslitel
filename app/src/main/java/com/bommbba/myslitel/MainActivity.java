package com.bommbba.myslitel;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String PREFS = "myslitel_prefs";
    private static final String KEY_OPENAI = "openai_api_key";
    private static final String MODEL = "gpt-5.5";

    private EditText apiKeyInput;
    private EditText messageInput;
    private TextView chatLog;
    private Button askButton;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 28, 28, 28);
        root.setBackgroundColor(0xFFF7F7F7);

        TextView title = new TextView(this);
        title.setText("Мыслитель 0.1");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText("Первый прототип: чат с ИИ. Потом добавим нижнюю панель и просмотр экрана.");
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
        messageInput.setHint("Например: помоги придумать экран live-комментариев");
        messageInput.setMinLines(2);
        messageInput.setGravity(Gravity.TOP);
        root.addView(messageInput, new LinearLayout.LayoutParams(-1, -2));

        askButton = new Button(this);
        askButton.setText("Спросить Мыслителя");
        askButton.setOnClickListener(v -> askOpenAI());
        root.addView(askButton, new LinearLayout.LayoutParams(-1, -2));

        TextView warning = new TextView(this);
        warning.setText("Важно: в этой версии нет просмотра экрана и нет управления телефоном. Это безопасная база для проверки API.");
        warning.setTextSize(12);
        warning.setPadding(0, 12, 0, 0);
        root.addView(warning, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
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
        URL url = new URL("https://api.openai.com/v1/responses");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(60000);
        connection.setDoOutput(true);

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
}
