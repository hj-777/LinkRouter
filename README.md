# 링크 라우터

카톡 인앱 브라우저에서 '다른 브라우저로 열기'를 누르면, 도메인별로 알맞은 앱(유튜브, GitHub 등)으로 링크를 넘겨주는 작은 안드로이드 앱.

- 규칙 수정: `app/src/main/java/com/linkrouter/RouterActivity.java`의 `RULES`
- 빌드: GitHub에 올리면 Actions 탭에서 자동 빌드 → Artifacts에서 `LinkRouter-apk` 다운로드
- 로컬 빌드: Android Studio로 폴더 열기 → Build > Build APK(s)
