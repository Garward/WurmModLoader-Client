package com.garward.wurmmodloader.client.declarativeui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parsed widget tree node, internal to the client-side declarative UI service.
 * The wire decoder fills these in; {@link WindowBuilder} walks them to build
 * real HUD components. Distinct from
 * {@code com.garward.wurmmodloader.modsupport.declarativeui.WidgetNode} on the
 * server (the public builder API).
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

    double propDouble(String key, double fallback) {
        String v = props.get(key);
        if (v == null) return fallback;
        try { return Double.parseDouble(v); } catch (NumberFormatException e) { return fallback; }
    }
}
