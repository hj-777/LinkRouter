package com.linkrouter;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RouterActivity extends Activity {

    /*
     * 도메인 → 앱 패키지 규칙. 위에서부터 먼저 맞는 것이 적용됩니다.
     * "youtube.com" 은 youtube.com, www.youtube.com, m.youtube.com 모두 매칭.
     * 새 규칙을 추가하려면 여기에 한 줄 넣고 다시 빌드하면 됩니다.
     */
    static final String[][] RULES = {
            {"music.youtube.com", "com.google.android.apps.youtube.music"},
            {"youtube.com",       "com.google.android.youtube"},
            {"youtu.be",          "com.google.android.youtube"},
            {"github.com",        "com.github.android"},
            {"instagram.com",     "com.instagram.android"},
            {"x.com",             "com.twitter.android"},
            {"twitter.com",       "com.twitter.android"},
            {"reddit.com",        "com.reddit.frontpage"},
            {"open.spotify.com",  "com.spotify.music"},
            {"maps.google.com",   "com.google.android.apps.maps"},
            {"maps.app.goo.gl",   "com.google.android.apps.maps"},
    };

    /* 규칙에 없는 링크는 이 순서대로 설치된 브라우저에서 엽니다. */
    static final String[] BROWSERS = {
            "com.android.chrome",
            "com.sec.android.app.sbrowser",
            "com.naver.whale",
            "org.mozilla.firefox",
            "com.microsoft.emmx",
    };

    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Uri uri = extractUri(getIntent());
        if (uri == null) {
            Toast.makeText(this, "링크를 찾지 못했어요", Toast.LENGTH_SHORT).show();
        } else {
            route(uri);
        }
        finish();
    }

    private Uri extractUri(Intent intent) {
        if (intent == null) return null;
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            return intent.getData();
        }
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text == null) return null;
            Matcher m = URL_PATTERN.matcher(text);
            if (m.find()) return Uri.parse(m.group());
        }
        return null;
    }

    private void route(Uri uri) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);

        // 1) 도메인 규칙에 맞는 앱
        for (String[] rule : RULES) {
            String domain = rule[0];
            if (host.equals(domain) || host.endsWith("." + domain)) {
                if (tryOpen(uri, rule[1])) return;
                break; // 앱이 없으면 브라우저로
            }
        }

        // 2) 설치된 브라우저
        for (String browser : BROWSERS) {
            if (tryOpen(uri, browser)) return;
        }

        // 3) 그래도 없으면 선택 창 (이 앱 자신은 제외)
        Intent view = new Intent(Intent.ACTION_VIEW, uri);
        Intent chooser = Intent.createChooser(view, "열 앱 선택");
        chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS,
                new ComponentName[]{new ComponentName(this, RouterActivity.class)});
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(chooser);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "열 수 있는 앱이 없어요", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean tryOpen(Uri uri, String pkg) {
        Intent i = new Intent(Intent.ACTION_VIEW, uri);
        i.addCategory(Intent.CATEGORY_BROWSABLE);
        i.setPackage(pkg);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }
}
