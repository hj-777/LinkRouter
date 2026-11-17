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
import android.widget.TextView;

public class MainActivity extends Activity {

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
                + "1. 아래 버튼 → '브라우저 앱'을 '링크 라우터'로 바꾸세요.\n\n"
                + "2. 카톡에서 링크를 누른 뒤 오른쪽 아래 ⋮ → '다른 브라우저로 열기'를 누르면 "
                + "유튜브·GitHub 등은 해당 앱으로, 나머지는 크롬(없으면 삼성 인터넷)으로 열려요.\n\n"
                + "3. 인앱 브라우저의 '공유'에서 '링크 라우터'를 골라도 돼요.");
        root.addView(info);

        Button settings = new Button(this);
        settings.setText("기본 브라우저 설정 열기");
        settings.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
            } catch (ActivityNotFoundException e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });
        root.addView(settings, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button test = new Button(this);
        test.setText("테스트: 유튜브 링크 열기");
        test.setOnClickListener(v -> {
            Intent i = new Intent(this, RouterActivity.class);
            i.setAction(Intent.ACTION_VIEW);
            i.setData(Uri.parse("https://www.youtube.com/watch?v=jNQXAC9IVRw"));
            startActivity(i);
        });
        root.addView(test, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }
}
