// axp viewer — Week 1 D5 minimal SSE 클라이언트.
// 기능: <img> 초기 로드 + EventSource 구독 + render_complete 시 cache-busting refresh.
// Week 4 D16-17 에서 10-event taxonomy + state machine 으로 확장.

(function () {
  // ---- 상수 (CLAUDE.md "no magic strings/numbers") -------------------------
  var DEFAULT_LAYOUT       = "activity_basic.xml";
  var PREVIEW_PATH         = "/preview";
  var EVENTS_PATH          = "/api/events";
  var EVENT_RENDER_DONE    = "render_complete";
  var ELEMENT_IMG_ID       = "previewImg";
  var ELEMENT_LAYOUT_ID    = "layoutPath";
  var ELEMENT_SSE_STATE_ID = "sseState";
  var ELEMENT_SSE_LAST_ID  = "sseLastEvent";
  var ELEMENT_STATUS_ID    = "statusBar";
  var QUERY_PARAM_LAYOUT   = "layout";
  var QUERY_PARAM_VERSION  = "v";

  // ---- DOM 핸들 -------------------------------------------------------------
  var imgEl       = document.getElementById(ELEMENT_IMG_ID);
  var layoutEl    = document.getElementById(ELEMENT_LAYOUT_ID);
  var sseStateEl  = document.getElementById(ELEMENT_SSE_STATE_ID);
  var sseLastEl   = document.getElementById(ELEMENT_SSE_LAST_ID);
  var statusEl    = document.getElementById(ELEMENT_STATUS_ID);

  // 로컬에서 ?layout=xxx 쿼리 지원 (W5 의 multi-layout selector 전 단계).
  var params         = new URLSearchParams(window.location.search);
  var currentLayout  = params.get(QUERY_PARAM_LAYOUT) || DEFAULT_LAYOUT;
  layoutEl.textContent = currentLayout;

  function buildPreviewUrl(layout, version) {
    var qs = new URLSearchParams();
    qs.set(QUERY_PARAM_LAYOUT, layout);
    if (version) {
      qs.set(QUERY_PARAM_VERSION, version);
    }
    return PREVIEW_PATH + "?" + qs.toString();
  }

  function setStatus(msg) {
    statusEl.textContent = msg;
  }

  function refreshImage(version) {
    imgEl.src = buildPreviewUrl(currentLayout, version || String(Date.now()));
  }

  // ---- 초기 PNG 로드 (SSE 와 무관 — 페이지 진입 즉시 반영) ----------------
  refreshImage("init");
  setStatus("initial /preview requested");

  // ---- SSE 구독 -------------------------------------------------------------
  if (!("EventSource" in window)) {
    sseStateEl.textContent = "EventSource unsupported";
    setStatus("브라우저가 SSE 미지원 — 수동 새로고침 모드");
    return;
  }

  var es = new EventSource(EVENTS_PATH);
  sseStateEl.textContent = "connecting";

  es.onopen = function () {
    sseStateEl.textContent = "open";
    setStatus("SSE connected");
  };

  es.onerror = function (e) {
    sseStateEl.textContent = "error / reconnecting";
    setStatus("SSE error — 브라우저가 자동 재연결 시도 중");
  };

  // 명시적 event 이름 핸들러 — 서버가 event: render_complete 라벨 사용.
  es.addEventListener(EVENT_RENDER_DONE, function (ev) {
    try {
      var payload = JSON.parse(ev.data);
      var version = payload && payload.renderId ? payload.renderId : ev.lastEventId;
      sseLastEl.textContent =
        EVENT_RENDER_DONE + " #" + (payload.id || "?") +
        " (" + (payload.durationMs || 0) + " ms)";
      setStatus("render_complete received — refreshing image v=" + version);
      refreshImage(version);
    } catch (err) {
      setStatus("event parse error: " + err.message);
    }
  });

  // generic onmessage — event 라벨 없이 data 만 보내는 fallback (W4 의 sse keep-alive 등).
  es.onmessage = function (ev) {
    if (!ev.data) return;
    setStatus("generic SSE frame: " + ev.data.slice(0, 80));
  };
})();
