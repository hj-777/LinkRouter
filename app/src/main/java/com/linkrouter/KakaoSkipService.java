package com.linkrouter;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 카카오톡 인앱 브라우저(InAppBrowserActivity)가 뜨면
 * 자동으로 ⋮ → "다른 브라우저로 열기"를 누른다. 카카오톡 화면에서만 동작.
 */
public class KakaoSkipService extends AccessibilityService {

    static final String PREFS = "linkrouter";
    static final String KEY_LAST_CLASS = "last_kakao_class";
    static final String KEY_DUMP = "last_dump";

    private static final String KAKAO = "com.kakao.talk";
    private static final String[] MORE_KEYWORDS = {"더보기", "더 보기", "메뉴", "옵션", "more", "menu", "option"};
    private static final int MAX_TRIES = 20;          // 약 6초
    private static final long TRY_INTERVAL_MS = 300;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean inBrowser = false;
    private int tries = 0;
    private long lastMenuClick = 0;
    private int menuClicks = 0;

    private final Runnable attempt = new Runnable() {
        @Override
        public void run() {
            if (!inBrowser) return;
            if (step()) {
                inBrowser = false;
                return;
            }
            if (++tries < MAX_TRIES) {
                handler.postDelayed(this, TRY_INTERVAL_MS);
            } else {
                saveDump(); // 실패 시 화면 구조 기록
                inBrowser = false;
            }
        }
    };

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        if (!KAKAO.contentEquals(event.getPackageName())) return;

        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && event.getClassName() != null) {
            String cls = event.getClassName().toString();
            if (cls.endsWith("Activity")) {
                saveLastClass(cls);
                String lower = cls.toLowerCase(Locale.ROOT);
                boolean browser = lower.contains("browser") || lower.contains("webview");
                if (browser && !inBrowser) {
                    inBrowser = true;
                    tries = 0;
                    menuClicks = 0;
                    lastMenuClick = 0;
                    handler.removeCallbacks(attempt);
                    handler.postDelayed(attempt, 250);
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

        // 1) 메뉴가 열려 있으면 "다른 브라우저…열기" 항목 클릭
        for (AccessibilityNodeInfo root : roots) {
            AccessibilityNodeInfo item = findMenuItem(root);
            if (item != null && clickUp(item)) return true;
        }

        // 2) ⋮ 버튼 클릭 (너무 자주/많이 누르지 않게)
        long now = SystemClock.uptimeMillis();
        if (now - lastMenuClick > 1000 && menuClicks < 4) {
            for (AccessibilityNodeInfo root : roots) {
                AccessibilityNodeInfo more = findMoreButton(root, menuClicks % 2 == 1);
                if (more != null && clickUp(more)) {
                    lastMenuClick = now;
                    menuClicks++;
                    break;
                }
            }
        }
        return false;
    }

    /* ---------- 메뉴 항목 찾기 ---------- */

    private AccessibilityNodeInfo findMenuItem(AccessibilityNodeInfo node) {
        if (node == null || isWebView(node)) return null;
        String t = label(node);
        if (t.contains("브라우저") && t.contains("열기")) return node;
        if (t.contains("browser") && t.contains("open")) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo f = findMenuItem(node.getChild(i));
            if (f != null) return f;
        }
        return null;
    }

    /* ---------- ⋮ 버튼 찾기 ---------- */

    private AccessibilityNodeInfo findMoreButton(AccessibilityNodeInfo root, boolean positionOnly) {
        List<AccessibilityNodeInfo> clickables = new ArrayList<>();
        collectClickables(root, clickables);

        // (a) 이름/ID에 '더보기/메뉴/more' 가 들어간 버튼 (짝수 번째 시도)
        if (!positionOnly) for (AccessibilityNodeInfo n : clickables) {
            String l = label(n) + " " + viewId(n);
            for (String k : MORE_KEYWORDS) {
                if (l.contains(k)) return n;
            }
        }

        // (b) 화면 아래쪽 15% 안에서 가장 오른쪽 버튼 (= 하단 툴바의 ⋮)
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int bottomZone = (int) (dm.heightPixels * 0.85);
        AccessibilityNodeInfo best = null;
        int bestRight = -1;
        Rect r = new Rect();
        for (AccessibilityNodeInfo n : clickables) {
            n.getBoundsInScreen(r);
            if (r.width() <= 0 || r.height() <= 0) continue;
            if (r.width() > dm.widthPixels / 2) continue; // 툴바 전체 같은 큰 영역 제외
            if (r.centerY() < bottomZone) continue;
            if (r.right > bestRight) {
                bestRight = r.right;
                best = n;
            }
        }
        return best;
    }

    private void collectClickables(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> out) {
        if (node == null || isWebView(node)) return; // 웹페이지 안쪽은 건드리지 않음
        if (node.isClickable() && node.isVisibleToUser()) out.add(node);
        for (int i = 0; i < node.getChildCount(); i++) collectClickables(node.getChild(i), out);
    }

    /* ---------- 공통 ---------- */

    private List<AccessibilityNodeInfo> roots() {
        List<AccessibilityNodeInfo> list = new ArrayList<>();
        try {
            for (AccessibilityWindowInfo w : getWindows()) {
                AccessibilityNodeInfo r = w.getRoot();
                if (r != null && r.getPackageName() != null
                        && KAKAO.contentEquals(r.getPackageName())) {
                    list.add(0, r); // 위에 떠 있는 창(팝업 메뉴)이 보통 나중에 나오므로 앞으로
                }
            }
        } catch (Exception ignored) { }
        if (list.isEmpty()) {
            AccessibilityNodeInfo r = getRootInActiveWindow();
            if (r != null) list.add(r);
        }
        return list;
    }

    private static boolean isWebView(AccessibilityNodeInfo n) {
        CharSequence c = n.getClassName();
        return c != null && c.toString().contains("WebView");
    }

    private static String label(AccessibilityNodeInfo n) {
        StringBuilder sb = new StringBuilder();
        if (n.getText() != null) sb.append(n.getText());
        sb.append(' ');
        if (n.getContentDescription() != null) sb.append(n.getContentDescription());
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private static String viewId(AccessibilityNodeInfo n) {
        String id = n.getViewIdResourceName();
        return id == null ? "" : id.toLowerCase(Locale.ROOT);
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
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_LAST_CLASS, cls).apply();
    }

    /** 문제 해결용: 카톡 창의 버튼 목록을 기록 */
    private void saveDump() {
        StringBuilder sb = new StringBuilder();
        Rect r = new Rect();
        int w = 0;
        for (AccessibilityNodeInfo root : roots()) {
            sb.append("[window ").append(w++).append("]\n");
            List<AccessibilityNodeInfo> cl = new ArrayList<>();
            collectClickables(root, cl);
            for (AccessibilityNodeInfo n : cl) {
                n.getBoundsInScreen(r);
                sb.append("- ").append(n.getClassName())
                        .append(" | id=").append(n.getViewIdResourceName())
                        .append(" | ").append(label(n).trim())
                        .append(" | ").append(r.toShortString()).append('\n');
            }
        }
        sb.append("menuClicks=").append(menuClicks);
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        p.edit().putString(KEY_DUMP, sb.toString()).apply();
    }

    @Override
    public void onInterrupt() { }
}
