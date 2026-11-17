package com.linkrouter;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 카카오톡 인앱 브라우저가 뜨면 자동으로 ⋮ → "다른 브라우저로 열기"를 누른다.
 * 카카오톡(com.kakao.talk) 화면에서만 동작한다.
 */
public class KakaoSkipService extends AccessibilityService {

    static final String PREFS = "linkrouter";
    static final String KEY_LAST_CLASS = "last_kakao_class";

    private static final String OPEN_EXTERNAL_TEXT = "다른 브라우저로 열기";
    private static final String[] MORE_BUTTON_DESCS = {"더보기", "더 보기", "메뉴", "More options", "More"};
    private static final int MAX_TRIES = 15;          // 약 4.5초 동안 시도
    private static final long TRY_INTERVAL_MS = 300;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean inBrowser = false;
    private int tries = 0;
    private long lastMenuClick = 0;

    private final Runnable attempt = new Runnable() {
        @Override
        public void run() {
            if (!inBrowser) return;
            if (step()) {
                inBrowser = false;
                return;
            }
            if (++tries < MAX_TRIES) handler.postDelayed(this, TRY_INTERVAL_MS);
        }
    };

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        if (!"com.kakao.talk".contentEquals(event.getPackageName())) return;

        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && event.getClassName() != null) {
            String cls = event.getClassName().toString();
            // 팝업 메뉴 등은 무시하고, 화면(Activity) 전환일 때만 상태를 바꾼다
            if (cls.endsWith("Activity")) {
                saveLastClass(cls);
                String lower = cls.toLowerCase(Locale.ROOT);
                boolean browser = lower.contains("browser") || lower.contains("webview");
                if (browser && !inBrowser) {
                    inBrowser = true;
                    tries = 0;
                    handler.removeCallbacks(attempt);
                    handler.postDelayed(attempt, 150);
                } else if (!browser) {
                    inBrowser = false;
                    handler.removeCallbacks(attempt);
                }
            }
        }
    }

    /** 한 번 시도. "다른 브라우저로 열기"를 눌렀으면 true. */
    private boolean step() {
        List<AccessibilityNodeInfo> roots = roots();

        // 1) 메뉴가 이미 열려 있으면 항목 클릭
        for (AccessibilityNodeInfo root : roots) {
            List<AccessibilityNodeInfo> hits = root.findAccessibilityNodeInfosByText(OPEN_EXTERNAL_TEXT);
            if (hits != null) {
                for (AccessibilityNodeInfo n : hits) {
                    if (clickUp(n)) return true;
                }
            }
        }

        // 2) 아니면 ⋮(더보기) 버튼 클릭 — 너무 자주 누르지 않게
        long now = SystemClock.uptimeMillis();
        if (now - lastMenuClick > 900) {
            for (AccessibilityNodeInfo root : roots) {
                AccessibilityNodeInfo more = findByDesc(root);
                if (more != null && clickUp(more)) {
                    lastMenuClick = now;
                    break;
                }
            }
        }
        return false;
    }

    private List<AccessibilityNodeInfo> roots() {
        List<AccessibilityNodeInfo> list = new ArrayList<>();
        try {
            for (AccessibilityWindowInfo w : getWindows()) {
                AccessibilityNodeInfo r = w.getRoot();
                if (r != null && r.getPackageName() != null
                        && "com.kakao.talk".contentEquals(r.getPackageName())) {
                    list.add(r);
                }
            }
        } catch (Exception ignored) { }
        if (list.isEmpty()) {
            AccessibilityNodeInfo r = getRootInActiveWindow();
            if (r != null) list.add(r);
        }
        return list;
    }

    private AccessibilityNodeInfo findByDesc(AccessibilityNodeInfo node) {
        if (node == null) return null;
        CharSequence d = node.getContentDescription();
        if (d != null) {
            String desc = d.toString().trim();
            for (String key : MORE_BUTTON_DESCS) {
                if (desc.equalsIgnoreCase(key)) return node;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findByDesc(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }

    private boolean clickUp(AccessibilityNodeInfo n) {
        int depth = 0;
        while (n != null && depth < 6) {
            if (n.isClickable() && n.isEnabled()) {
                return n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
            n = n.getParent();
            depth++;
        }
        return false;
    }

    private void saveLastClass(String cls) {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        p.edit().putString(KEY_LAST_CLASS, cls).apply();
    }

    @Override
    public void onInterrupt() { }
}
