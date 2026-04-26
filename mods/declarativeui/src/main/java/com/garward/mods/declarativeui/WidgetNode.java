package com.garward.mods.declarativeui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parsed widget tree node. Opaque to the ModComm listener — the interpreter
 * walks this and instantiates real HUD components in {@link WindowBuilder}.
 */
final class WidgetNode {
    final String type;
    final Map<String, String> props;
    final List<WidgetNode> children;

    WidgetNode(String type) {
        this.type = type;
        this.props = new LinkedHashMap<>();
        this.children = new ArrayList<>();
    }

    String prop(String key, String fallback) {
        String v = props.get(key);
        return v == null ? fallback : v;
    }

    int propInt(String key, int fallback) {
        String v = props.get(key);
        if (v == null) return fallback;
        try { return Integer.parseInt(v); } catch (NumberFormatException e) { return fallback; }
    }
}
