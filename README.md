# Myslitel / Мыслитель

**Myslitel** is an experimental Android assistant app that can inspect the phone screen, show a compact floating control panel, and perform simple user-requested actions such as taps, swipes, Back/Home navigation, app opening, and grid-based screen interaction.

> This is an MVP-stage project. Use it carefully: the app requests screen-capture access and an Android Accessibility service.

## Features

- Current-screen analysis using screenshots.
- Floating overlay panel on top of other apps.
- Work modes: commentator, navigator, teacher, anti-mistake, and quiet mode.
- Phone control through Accessibility: tap, swipe, back, home, open app.
- A1-J20 coordinate grid for more accurate tapping when Android Accessibility cannot detect a UI element.
- Live mode and autopilot-style step execution.
- Local settings storage: API key, mode, session context, and estimated budget.

## Key Files

| File | Purpose |
| --- | --- |
| `app/src/main/java/com/bommbba/myslitel/MainActivity.java` | Main screen, settings, model requests, screen analysis, and autopilot logic. |
| `app/src/main/java/com/bommbba/myslitel/MyslitelAccessibilityService.java` | Performs taps, swipes, Back/Home actions, and text-based UI element search. |
| `app/src/main/java/com/bommbba/myslitel/OverlayService.java` | Creates the floating panel with quick actions and text input. |
| `app/src/main/java/com/bommbba/myslitel/ScreenCaptureService.java` | Handles screen-capture permission and screenshot acquisition. |
| `app/src/main/AndroidManifest.xml` | Declares permissions, the main Activity, and app services. |
| `.github/workflows/android-debug-apk.yml` | Builds a debug APK with GitHub Actions. |
| `NEXT_STEPS.md` | Short list of upcoming checks and ideas. |

## Android Permissions

The app uses permissions for:

- internet access;
- drawing an overlay above other apps;
- foreground service execution;
- screen capture through MediaProjection;
- Accessibility-powered gestures and navigation.

Before publishing or installing, keep in mind that these permissions provide a high level of device access. Do not commit private API keys or secrets to the repository.

## Build

The project uses Gradle and the Android Gradle Plugin.

```bash
./gradlew assembleDebug
```

After a successful build, the debug APK is usually available in:

```text
app/build/outputs/apk/debug/
```

## Status

Current version: **MVP 0.8**.

Next checks are listed in `NEXT_STEPS.md`: chess, auto moves, alarm-clock flows, tap accuracy, and a possible two-level grid.

---

# Мыслитель

**Мыслитель** — экспериментальное Android-приложение-помощник, которое анализирует экран телефона, показывает компактную плавающую панель и может выполнять простые действия по команде пользователя: нажимать элементы, делать свайпы, возвращаться назад, открывать приложения и работать по координатной сетке.

> Проект находится в стадии MVP. Используйте осторожно: приложение запрашивает доступ к просмотру экрана и Accessibility-сервису.

## Возможности

- Анализ текущего экрана через скриншот.
- Плавающая панель поверх других приложений.
- Режимы работы: комментатор, навигатор, учитель, антиошибка и тихий режим.
- Управление телефоном через Accessibility: tap, swipe, back, home, open app.
- Координатная сетка A1-J20 для более точных нажатий там, где Android Accessibility не видит кнопку.
- Live-режим и автопилот с пошаговым выполнением задачи.
- Локальное хранение настроек: API-ключ, режим, контекст сессии и примерный бюджет.

## Основные файлы

| Файл | Что делает |
| --- | --- |
| `app/src/main/java/com/bommbba/myslitel/MainActivity.java` | Главный экран, настройки, отправка запросов к модели, анализ экрана и логика автопилота. |
| `app/src/main/java/com/bommbba/myslitel/MyslitelAccessibilityService.java` | Выполняет нажатия, свайпы, кнопки Back/Home и поиск элементов по тексту. |
| `app/src/main/java/com/bommbba/myslitel/OverlayService.java` | Создает плавающую панель с быстрыми кнопками и полем ввода. |
| `app/src/main/java/com/bommbba/myslitel/ScreenCaptureService.java` | Получает разрешение на запись экрана и делает скриншоты для анализа. |
| `app/src/main/AndroidManifest.xml` | Описывает разрешения, Activity и сервисы приложения. |
| `.github/workflows/android-debug-apk.yml` | Собирает debug APK через GitHub Actions. |
| `NEXT_STEPS.md` | Короткий список следующих проверок и идей. |

## Разрешения Android

Приложение использует разрешения для:

- доступа в интернет;
- отображения панели поверх других окон;
- foreground-сервиса;
- захвата экрана через MediaProjection;
- Accessibility-сервиса для жестов и навигации.

Перед публикацией или установкой важно понимать, что такие разрешения дают приложению высокий уровень доступа к устройству. Не храните секретные ключи в публичном коде.

## Сборка

Проект использует Gradle и Android Gradle Plugin.

```bash
./gradlew assembleDebug
```

После успешной сборки debug APK обычно находится в:

```text
app/build/outputs/apk/debug/
```

## Статус

Текущая версия: **MVP 0.8**.

Ближайшие проверки описаны в `NEXT_STEPS.md`: шахматы, автоходы, будильник, точность нажатий и возможная двухуровневая сетка.
