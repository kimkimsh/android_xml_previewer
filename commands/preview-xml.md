---
description: Open the Android XML layout preview viewer in the browser
allowed-tools: Bash(xdg-open:*), mcp__axp-server__open_viewer, mcp__axp-server__render_layout
argument-hint: "[optional: path to a specific res/layout/*.xml file to pre-render]"
---

Ensure the AXP preview server is running, then open the viewer at `http://localhost:7321`.

If `$ARGUMENTS` contains a specific layout file path:
1. Call `mcp__axp-server__render_layout(xml_path=$ARGUMENTS)` to pre-render before the browser opens
2. Then call `mcp__axp-server__open_viewer` and report the URL

Otherwise, just call `mcp__axp-server__open_viewer` and report the URL.
