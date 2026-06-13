package com.bommbba.myslitel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

public class ScreenCaptureService extends Service {
    public static final String ACTION_START = "com.bommbba.myslitel.START_SCREEN_CAPTURE";
    public static final String ACTION_STOP = "com.bommbba.myslitel.STOP_SCREEN_CAPTURE";
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";

    private static final String CHANNEL_ID = "myslitel_screen_capture";
    private static final int NOTIFICATION_ID = 3031;
    private static WeakReference<ScreenCaptureService> activeService;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private MediaProjection mediaProjection;
    private ImageReader imageReader;
    private VirtualDisplay virtualDisplay;
    private int captureWidth;
    private int captureHeight;
    private int captureDensity;
    private boolean captureReady = false;

    public static boolean isReady() {
        ScreenCaptureService service = activeService == null ? null : activeService.get();
        return service != null && service.captureReady && service.imageReader != null;
    }

    public static Bitmap acquireScreenshotBitmap() {
        ScreenCaptureService service = activeService == null ? null : activeService.get();
        if (service == null || !service.captureReady || service.imageReader == null) return null;
        return service.acquireLatestBitmapInternal();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        activeService = new WeakReference<>(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        if (ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_START.equals(intent.getAction())) {
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            Intent resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);
            startCaptureForeground(resultCode, resultData);
            return START_STICKY;
        }

        return START_NOT_STICKY;
    }

    private void startCaptureForeground(int resultCode, Intent resultData) {
        if (resultCode == 0 || resultData == null) {
            OverlayService.updatePanelText("Не получил разрешение просмотра экрана. Открой Мыслитель и нажми кнопку 3 ещё раз.");
            stopSelf();
            return;
        }

        try {
            createNotificationChannel();
            Notification notification = buildNotification();
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }

            releaseScreenCapture();

            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            if (manager == null) throw new Exception("MediaProjectionManager недоступен");

            mediaProjection = manager.getMediaProjection(resultCode, resultData);
            if (mediaProjection == null) throw new Exception("MediaProjection не запустился");

            mediaProjection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    mainHandler.post(() -> {
                        releaseScreenCaptureOnlyViews();
                        captureReady = false;
                        OverlayService.updatePanelText("Просмотр экрана остановлен. Для анализа снова выдай разрешение.");
                    });
                }
            }, mainHandler);

            startCapturePipeline();
            captureReady = true;
            OverlayService.updatePanelText("Просмотр разрешён. Открой нужный экран и нажми “Анализ”.");
            MainActivity.notifyScreenCaptureReady(true, "Система: просмотр экрана разрешён. Сверни приложение, открой нужный экран и нажми “Анализ” на нижней панели.");
        } catch (Exception e) {
            captureReady = false;
            OverlayService.updatePanelText("Ошибка запуска просмотра: " + e.getMessage());
            MainActivity.notifyScreenCaptureReady(false, "Система: не удалось запустить просмотр экрана: " + e.getMessage());
            stopSelf();
        }
    }

    private void startCapturePipeline() throws Exception {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) throw new Exception("WindowManager недоступен");

        wm.getDefaultDisplay().getRealMetrics(metrics);

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
        if (virtualDisplay == null) throw new Exception("VirtualDisplay не создан");
    }

    private Bitmap acquireLatestBitmapInternal() {
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

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Мыслитель — просмотр экрана",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Нужно для ручного анализа экрана в Мыслителе");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setContentTitle("Мыслитель анализирует экран")
                .setContentText("Разрешение активно. Анализ запускается только вручную по кнопке.")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true)
                .build();
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        releaseScreenCapture();
        if (activeService != null && activeService.get() == this) {
            activeService = null;
        }
    }
}
