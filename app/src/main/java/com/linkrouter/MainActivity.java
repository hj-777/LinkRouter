package com.linkrouter;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {

    private TextView debug;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView info = new TextView(this);
        info.setTextSize(16);
        info.setText("링크 라우터\n\n"
                + "1. [접근성 설정 열기] → 설치된 앱 → '카톡 인앱브라우저 건너뛰기' 켜기\n"
                + "   (켜지지 않으면: 설정 → 애플리케이션 → 링크 라우터 → 오른쪽 위 ⋮ → '제한된 설정 허용' 후 다시)\n\n"
                + "2. [기본 브라우저 설정 열기] → '링크 라우터' 선택\n"
                + "   유튜브·GitHub 링크는 해당 앱으로, 나머지는 크롬으로 열려요.\n"
                + "   (그냥 크롬으로만 열고 싶으면 크롬을 기본 브라우저로 두세요)\n");
        root.addView(info);

        root.addView(button("접근성 설정 열기", Settings.ACTION_ACCESSIBILITY_SETTINGS), wide());
        root.addView(button("기본 브라우저 설정 열기", Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS), wide());

        Button test = new Button(this);
        test.setText("테스트: 유튜브 링크 라우팅");
        test.setOnClickListener(v -> {
            Intent i = new Intent(this, RouterActivity.class);
            i.setAction(Intent.ACTION_VIEW);
            i.setData(Uri.parse("https://www.youtube.com/watch?v=jNQXAC9IVRw"));
            startActivity(i);
        });
        root.addView(test, wide());

        debug = new TextView(this);
        debug.setTextSize(12);
        debug.setPadding(0, pad, 0, 0);
        root.addView(debug);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String last = getSharedPreferences(KakaoSkipService.PREFS, MODE_PRIVATE)
                .getString(KakaoSkipService.KEY_LAST_CLASS, "(아직 없음)");
        debug.setText("문제 해결용 — 마지막으로 본 카톡 화면:\n" + last);
    }

    private Button button(String label, String action) {
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(v -> {
            try {
                startActivity(new Intent(action));
            } catch (ActivityNotFoundException e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });
        return b;
    }

    private LinearLayout.LayoutParams wide() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
