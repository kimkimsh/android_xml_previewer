# Third-Party Notices

> 본 프로젝트 **android-xml-previewer**(이하 "본 소프트웨어")는 다음의 제3자 오픈소스 컴포넌트를 번들하거나 사용합니다.
> 모든 고지는 Apache License 2.0 및 각 라이선스의 요구사항을 충족합니다.

---

## 1. 번들되는(재배포되는) 컴포넌트

본 소프트웨어는 설치 시 사용자의 디스크에 함께 배포되는 다음 JAR/바이너리/리소스를 포함합니다.

### 1.1 layoutlib (Android Open Source Project) — 4 artifact 번들

- **위치**: `server/libs/layoutlib-dist/android-34/`
- **배포 소스 (canonical, 08 §7.5 정명화)**: Google's Maven 저장소
  `https://dl.google.com/android/maven2/com/android/tools/layoutlib/`
- **배포 소스 (대체)**: AOSP `out/soong/layoutlib_runner` 빌드 산출물
- **라이선스**: Apache License, Version 2.0 (모두)
- **변경 여부**: 본 소프트웨어는 layoutlib 바이너리/리소스를 **수정 없이** 번들합니다 (META-INF 정리 제외).
- **원 라이선스 사본**: `docs/licenses/LICENSE-APACHE-2.0.txt`
- **이전 plan 가정 폐기**: W1D1 README 의 "Paparazzi prebuilts 경로 (`libs/layoutlib/android-34/`)" 가정은
  Paparazzi 2.x 에서 in-tree prebuilts 제거로 무효화됨. 08 §7.5 가 canonical 갱신 (handoff §4 UNVERIFIED #1 해소).

#### 1.1.a com.android.tools.layoutlib:layoutlib:14.0.11 (JVM Bridge JAR, 49M)

- **다운로드 URL**: `https://dl.google.com/android/maven2/com/android/tools/layoutlib/layoutlib/14.0.11/layoutlib-14.0.11.jar`
- **로컬 경로**: `server/libs/layoutlib-dist/android-34/layoutlib-14.0.11.jar`
- **포함 클래스**: `com.android.layoutlib.bridge.Bridge` + Android framework 클래스 전체 (`android.*`)
- **sha256**: `9a8ab05c9617ec18a3bd8546ef9d56b4ea6271208ade27710b0baf564fe4dd59`

#### 1.1.b com.android.tools.layoutlib:layoutlib-runtime:14.0.11:linux (네이티브 + 폰트/icu/키보드, 74M)

- **다운로드 URL**: `https://dl.google.com/android/maven2/com/android/tools/layoutlib/layoutlib-runtime/14.0.11/layoutlib-runtime-14.0.11-linux.jar`
- **언팩 후 로컬 경로**: `server/libs/layoutlib-dist/android-34/data/{fonts,icu,keyboards,linux/lib64}/` + `data/build.prop` (jar 루트의 build.prop 을 data/ 로 이동) + `licenses/`
- **OS classifier**: linux (Linux-first 정책, 02 §3). macOS/Windows 는 v1 미지원.
- **sha256**: `3811fac81ad279ac1b7c998d199ff62cace4a36b10fc259b80eb4da40f8b0aea`

#### 1.1.c com.android.tools.layoutlib:layoutlib-resources:14.0.11 (framework resources, 32M)

- **다운로드 URL**: `https://dl.google.com/android/maven2/com/android/tools/layoutlib/layoutlib-resources/14.0.11/layoutlib-resources-14.0.11.jar`
- **언팩 후 로컬 경로**: `server/libs/layoutlib-dist/android-34/data/{res,overlays,resources*.bin}`
- **포함**: 445 framework res 디렉토리 + DisplayCutout/NavigationBar overlays + locale-specific 컴파일된 string table
- **sha256**: `e9aa042241eda69a1a5b030b628a4afa01f77720884c847489b367e785bd9259`

#### 1.1.d com.android.tools.layoutlib:layoutlib-api:31.13.2 (API surface, 120K)

- **다운로드 URL**: `https://dl.google.com/android/maven2/com/android/tools/layoutlib/layoutlib-api/31.13.2/layoutlib-api-31.13.2.jar`
- **로컬 경로**: `server/libs/layoutlib-dist/android-34/layoutlib-api-31.13.2.jar`
- **포함**: Bridge parent 클래스 (`com.android.ide.common.rendering.api.Bridge`), `ILayoutLog` 인터페이스 등 layoutlib 의 public API 표면 — Bridge 클래스 로드에 필수.
- **버전 매핑**: 31.13.2 = `androidTools` 좌표 (Paparazzi pin 동기화), AGP 8.13.x 와 정합. layoutlib 14.x 의 컴파일타임 API 참조.
- **sha256**: `d06bc650247632a4a4e6596b87312019f45e900267c5476c47a5bfa6e3fd3132`

#### 1.1.e 고지 요구 (4 artifact 공통)

- (1) 본 파일을 소스 배포 및 바이너리 배포(zip/tar/플러그인 마켓플레이스 패키지)에 포함
- (2) 레포의 `LICENSE` 파일(본 프로젝트의 Apache-2.0)과 병행 고지
- (3) 변경이 없으므로 `NOTICE` 수정 의무 없음. 단, runtime jar 의 `licenses/` 디렉토리(원 NOTICE/ICU/Linux 라이선스)는 그대로 유지.

### 1.2 Android SDK Framework Stub (android.jar)

- **경로**: 사용자 시스템의 `$ANDROID_HOME/platforms/android-34/android.jar`
- **재배포 여부**: **번들하지 않음**. 사용자가 Android SDK를 별도로 설치해야 하며 본 소프트웨어는 로컬 사본만 참조합니다.
- **라이선스**: Android Software Development Kit License Agreement (사용자-SDK 간 계약)
- **영향**: 본 소프트웨어 라이선스에 영향을 주지 않음 (재배포가 아니므로).

### 1.3 폰트 팩 (Noto Sans / Noto Sans CJK / Noto Color Emoji / Roboto)

- **위치**: `server/libs/fonts/`
- **라이선스**:
  - Noto Sans / Noto Sans CJK / Noto Color Emoji: SIL Open Font License 1.1
  - Roboto: Apache License 2.0
- **번들 이유**: 07 §2.4 — 결정 cache key에 폰트 해시 포함, deterministic 렌더 보장. 한국어 primary persona 대응.
- **고지**: `docs/licenses/OFL-1.1.txt` (Noto), `docs/licenses/LICENSE-APACHE-2.0.txt` (Roboto).

### 1.4 harness APK (자체 빌드)

- **위치**: `server/libs/harness.apk` (Week 3에 빌드됨)
- **저자권**: 본 프로젝트
- **라이선스**: Apache License 2.0 (본 프로젝트와 동일)
- **의존**: AndroidX Core / AppCompat — 실행 시 사용자 앱의 AndroidX에 의해 `ChildFirstDexClassLoader`로 오버라이드되므로 버전 독립적이지만, 사용된 AndroidX 고지는 본 문서 §2에 기록.

---

## 2. 실행 시 의존하는(비-재배포) 컴포넌트

본 소프트웨어 빌드/실행 중에만 의존하고, 재배포 바이너리에는 포함되지 않는 라이브러리.

- **Kotlin Stdlib / Coroutines** — Apache 2.0 (JetBrains)
- **Ktor** — Apache 2.0 (JetBrains)
- **kotlinx.serialization** — Apache 2.0 (JetBrains)
- **dexlib2** (DEX preflight, 07 §3.4) — BSD 3-Clause (JesusFreke/smali)
- **aapt2** — Android SDK 구성요소 (사용자 SDK 경로에서 호출)
- **AndroidX (harness에서만 빌드 타임 의존)** — Apache 2.0

---

## 3. Apache 2.0 전체 사본

`docs/licenses/LICENSE-APACHE-2.0.txt` 에 전체 텍스트 포함.

---

## 4. 고지 정책 (요약)

- 본 `THIRD_PARTY_NOTICES.md`는 배포 패키지의 루트에 **반드시** 포함된다.
- 플러그인 마켓플레이스 메타데이터 페이지에 본 파일로의 링크 포함.
- layoutlib-dist 번들의 원 `NOTICE` 파일이 존재하면 `server/libs/layoutlib-dist/android-34/NOTICE`로 그대로 유지한다.

---

## 5. 변경 로그

| 일자 | 변경 | 작성자 |
|------|------|--------|
| 2026-04-22 | 초안 — W1D1 Task #1, 08 §3.3 반영 | 실행 세션 |
| 2026-04-23 | §1.1 정명화 (Paparazzi prebuilts → Google Maven 3-artifact) + sha256 기록 — W1D3-R2, 08 §7.5 | W1D2 세션 |
| 2026-04-23 | §1.1.d 추가 — layoutlib-api 31.13.2 (Bridge parent 클래스). 3-artifact → 4-artifact (W1D4-R3 발견, 08 §7.5 갱신) | W1D2 세션 |
