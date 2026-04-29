---
name: preview-android-layout
description: |
  Use this skill when the user edits an Android XML layout file (res/layout/*.xml,
  res/drawable/*.xml, res/values/*.xml, AndroidManifest.xml) and wants a live visual
  preview in the browser. Also use when the user asks to "render layout", "preview
  XML", "see the layout", "show me this layout", or mentions an Android XML file path.

  Do NOT use for generic Android questions, code review, or non-XML files. The
  plugin's filesystem watcher handles automatic re-rendering — this skill is only
  for opening or focusing the browser viewer on-demand.

  <example>
  user: "activity_main.xml 어떻게 보일지 보고 싶어"
  assistant: "[invoke preview-android-layout skill to open the viewer and focus on activity_main.xml]"
  <commentary>User explicitly asks for a layout preview.</commentary>
  </example>

  <example>
  user: "이 Button 의 margin 을 늘려줘"
  assistant: "[edit the XML, then the filesystem watcher auto-renders — no skill call needed]"
  <commentary>Auto-render via watcher; skill is only for opening the viewer.</commentary>
  </example>

  <example>
  user: "이 레이아웃이 태블릿에서 어떻게 보일지 비교해줘"
  assistant: "[invoke preview-android-layout skill, pass a request to multi-device grid rendering]"
  <commentary>Multi-device comparison is a plugin feature.</commentary>
  </example>
version: 0.1.0
allowed-tools:
  - Read
  - Bash(axprev:*, xdg-open:*)
  - mcp__axp-server__open_viewer
  - mcp__axp-server__render_layout
---

# Preview Android Layout

When invoked:

1. Check whether the `axp-server` MCP tool is available (`mcp__axp-server__*`).
2. Call `mcp__axp-server__open_viewer` to ensure the HTTP+SSE server is running on
   `localhost:7321` and receive the viewer URL.
3. If the user named a specific XML file (e.g. "activity_main.xml"):
   - Resolve the path relative to the project root (look for `res/layout/*.xml`)
   - Call `mcp__axp-server__render_layout(xml_path=<path>)` to pre-render before the
     browser tab opens
4. If a browser is not already open, suggest `xdg-open <url>`. Otherwise just report
   the URL so the user can focus their existing tab.

**Do NOT** call `render_layout` repeatedly just because an edit happened — the
filesystem watcher inside the server already re-renders on save. Only call it when
the user explicitly requests a one-off render or when `open_viewer` is first called.

If the MCP server is unavailable (not running, socket error), report the error
directly and suggest the user run `axprev serve` from the project root as a
fallback.
