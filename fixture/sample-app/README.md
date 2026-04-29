# fixture/sample-app

W1D1 Task #6 — 플러그인 개발/테스트 용 미니 Android 앱.
08 §5 item 6 canonical 요구사항:

- `app/src/main/res/layout/activity_basic.xml` (LinearLayout + TextView + Button, Material theme)
- `app/src/main/res/layout/activity_custom_view.xml` (com.fixture.DummyView)
- `app/src/main/java/com/fixture/DummyView.kt` (placeholder class)
- `./gradlew :app:assembleDebug` 로 APK 가 생성되어야 함

## 빌드 요구사항 (런타임)

- Android SDK (API 34 이상) — `$ANDROID_HOME` 설정
- JVM 17+
- 인터넷 연결 (Gradle 이 AndroidX dependency 를 mavenCentral/google()에서 다운로드)

Week 1 Day 1 작성 시점에 로컬 환경에 Android SDK 미설치되어 실제 빌드는 추후 CI 에서 검증.
fixture/sample-app 디렉토리 구조 + source 파일만 canonical 로 확정.

## 플러그인 통합 용도

이 fixture 는 다음 목적에 사용됨:

1. **W1D4 L1 spike** — `activity_basic.xml` 이 layoutlib 의 첫 렌더 대상
2. **W3D14 L3 harness 테스트** — `activity_custom_view.xml` 이 L1 실패 → L3 에스컬레이션 경로 검증
3. **W6 acceptance** — golden corpus 13 프로젝트와 별도로, 플러그인 자체의 self-test 용
