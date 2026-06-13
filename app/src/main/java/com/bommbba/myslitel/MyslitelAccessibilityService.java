package com.bommbba.myslitel;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.lang.ref.WeakReference;

public class MyslitelAccessibilityService extends AccessibilityService {
    private static WeakReference<MyslitelAccessibilityService> activeService;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        activeService = new WeakReference<>(this);
        OverlayService.updatePanelText("Управление телефоном включено.");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Нам не нужно читать события постоянно. Сервис нужен для жестов и ввода по команде пользователя.
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (activeService != null && activeService.get() == this) activeService = null;
    }

    public static boolean isReady() {
        return activeService != null && activeService.get() != null;
    }

    private static MyslitelAccessibilityService service() {
        return activeService == null ? null : activeService.get();
    }

    private int screenWidth() {
        return Math.max(1, getResources().getDisplayMetrics().widthPixels);
    }

    private int screenHeight() {
        return Math.max(1, getResources().getDisplayMetrics().heightPixels);
    }

    private int nx(int x1000) {
        int x = Math.max(0, Math.min(1000, x1000));
        return Math.round((x / 1000f) * screenWidth());
    }

    private int ny(int y1000) {
        int y = Math.max(0, Math.min(1000, y1000));
        return Math.round((y / 1000f) * screenHeight());
    }

    public static boolean tapNormalized(int x1000, int y1000) {
        MyslitelAccessibilityService s = service();
        if (s == null) return false;
        return s.gestureTap(s.nx(x1000), s.ny(y1000));
    }

    public static boolean swipeNormalized(int x1000, int y1000, int x2_1000, int y2_1000, int durationMs) {
        MyslitelAccessibilityService s = service();
        if (s == null) return false;
        return s.gestureSwipe(s.nx(x1000), s.ny(y1000), s.nx(x2_1000), s.ny(y2_1000), durationMs);
    }

    public static boolean scrollDown() {
        MyslitelAccessibilityService s = service();
        if (s == null) return false;
        // Палец идёт вверх, содержимое списка уходит вниз.
        return s.gestureSwipe(s.nx(500), s.ny(760), s.nx(500), s.ny(280), 520);
    }

    public static boolean scrollUp() {
        MyslitelAccessibilityService s = service();
        if (s == null) return false;
        // Палец идёт вниз, содержимое списка возвращается вверх.
        return s.gestureSwipe(s.nx(500), s.ny(280), s.nx(500), s.ny(760), 520);
    }

    public static boolean back() {
        MyslitelAccessibilityService s = service();
        return s != null && s.performGlobalAction(GLOBAL_ACTION_BACK);
    }

    public static boolean home() {
        MyslitelAccessibilityService s = service();
        return s != null && s.performGlobalAction(GLOBAL_ACTION_HOME);
    }

    public static boolean typeText(String text) {
        MyslitelAccessibilityService s = service();
        if (s == null || text == null) return false;
        AccessibilityNodeInfo root = s.getRootInActiveWindow();
        if (root == null) return false;
        AccessibilityNodeInfo node = s.findFocusedEditable(root);
        if (node == null) return false;
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    private AccessibilityNodeInfo findFocusedEditable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isFocused() && node.isEditable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findFocusedEditable(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }

    private boolean gestureTap(int x, int y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, 70);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        return dispatchGesture(gesture, null, null);
    }

    private boolean gestureSwipe(int x1, int y1, int x2, int y2, int durationMs) {
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        int duration = Math.max(120, Math.min(1500, durationMs));
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, duration);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        return dispatchGesture(gesture, null, null);
    }
}
