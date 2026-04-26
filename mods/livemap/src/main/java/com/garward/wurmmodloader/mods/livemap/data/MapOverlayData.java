package com.garward.wurmmodloader.mods.livemap.data;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed snapshot of {@code /livemap/api/data}.
 *
 * <p>Intentionally regex-based rather than pulling in a JSON library: the payload
 * is a tiny flat object with four arrays (players / villages / altars / towers)
 * of scalar fields, and we run this on a 15-second poll. Adding Gson/Jackson
 * to the client classpath for this is overkill.
 */
public final class MapOverlayData {

    public final List<Player> players;
    public final List<Village> villages;
    public final List<Altar> altars;
    public final List<Tower> towers;

    private MapOverlayData(List<Player> p, List<Village> v, List<Altar> a, List<Tower> t) {
        this.players = p; this.villages = v; this.altars = a; this.towers = t;
    }

    public static final MapOverlayData EMPTY =
            new MapOverlayData(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

    public static MapOverlayData parse(String json) {
        if (json == null || json.isEmpty()) return EMPTY;
        return new MapOverlayData(
                parsePlayers (extractArray(json, "players")),
                parseVillages(extractArray(json, "villages")),
                parseAltars  (extractArray(json, "altars")),
                parseTowers  (extractArray(json, "towers"))
        );
    }

    private static String extractArray(String json, String key) {
        int keyIdx = json.indexOf("\"" + key + "\"");
        if (keyIdx < 0) return "";
        int open = json.indexOf('[', keyIdx);
        if (open < 0) return "";
        int depth = 0;
        for (int i = open; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) return json.substring(open + 1, i);
            }
        }
        return "";
    }

    private static List<String> splitObjects(String arrBody) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        int start = -1;
        for (int i = 0; i < arrBody.length(); i++) {
            char c = arrBody.charAt(i);
            if (c == '{') { if (depth == 0) start = i; depth++; }
            else if (c == '}') { depth--; if (depth == 0 && start >= 0) { out.add(arrBody.substring(start, i + 1)); start = -1; } }
        }
        return out;
    }

    private static String str(String obj, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(obj);
        if (!m.find()) return "";
        return m.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static int intField(String obj, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)").matcher(obj);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static float floatField(String obj, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").matcher(obj);
        return m.find() ? Float.parseFloat(m.group(1)) : 0f;
    }

    private static boolean boolField(String obj, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(true|false)").matcher(obj);
        return m.find() && "true".equals(m.group(1));
    }

    private static List<Player> parsePlayers(String body) {
        List<Player> out = new ArrayList<>();
        for (String obj : splitObjects(body)) {
            out.add(new Player(str(obj, "name"), intField(obj, "x"), intField(obj, "y"),
                    boolField(obj, "surface"), (byte) intField(obj, "kingdom")));
        }
        return out;
    }

    private static List<Village> parseVillages(String body) {
        List<Village> out = new ArrayList<>();
        for (String obj : splitObjects(body)) {
            int[] borders = intArrayField(obj, "borders");
            int sx = borders.length > 0 ? borders[0] : intField(obj, "startX");
            int sy = borders.length > 1 ? borders[1] : intField(obj, "startY");
            int ex = borders.length > 2 ? borders[2] : intField(obj, "endX");
            int ey = borders.length > 3 ? borders[3] : intField(obj, "endY");
            // Server's villages.json emits the token as plain x/y; fall back
            // to legacy tokenX/Y or deed center for older servers.
            int tx = intField(obj, "x");
            int ty = intField(obj, "y");
            if (tx <= 0) tx = intField(obj, "tokenX");
            if (ty <= 0) ty = intField(obj, "tokenY");
            if (tx <= 0) tx = (sx + ex) / 2;
            if (ty <= 0) ty = (sy + ey) / 2;
            out.add(new Village(
                    str(obj, "name"), sx, sy, ex, ey,
                    str(obj, "type"), str(obj, "mayor"), str(obj, "motto"),
                    intField(obj, "citizenCount"),
                    boolField(obj, "permanent"),
                    tx, ty));
        }
        return out;
    }

    private static int[] intArrayField(String obj, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\[([^\\]]*)\\]").matcher(obj);
        if (!m.find()) return new int[0];
        String body = m.group(1).trim();
        if (body.isEmpty()) return new int[0];
        String[] parts = body.split(",");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { out[i] = Integer.parseInt(parts[i].trim()); } catch (NumberFormatException ignored) {}
        }
        return out;
    }

    private static List<Altar> parseAltars(String body) {
        List<Altar> out = new ArrayList<>();
        for (String obj : splitObjects(body)) {
            out.add(new Altar(str(obj, "name"), intField(obj, "x"), intField(obj, "y"), str(obj, "type")));
        }
        return out;
    }

    private static List<Tower> parseTowers(String body) {
        List<Tower> out = new ArrayList<>();
        for (String obj : splitObjects(body)) {
            out.add(new Tower(str(obj, "name"), intField(obj, "x"), intField(obj, "y"),
                    (byte) intField(obj, "kingdom"), floatField(obj, "dmg")));
        }
        return out;
    }

    public static final class Player {
        public final String name; public final int x, y; public final boolean surface; public final byte kingdom;
        public Player(String name, int x, int y, boolean surface, byte kingdom) {
            this.name = name; this.x = x; this.y = y; this.surface = surface; this.kingdom = kingdom;
        }
    }

    public static final class Village {
        public final String name, type, mayor, motto;
        public final int startX, startY, endX, endY, citizens, tokenX, tokenY;
        public final boolean permanent;
        public Village(String name, int sx, int sy, int ex, int ey, String type, String mayor,
                       String motto, int citizens, boolean permanent, int tokenX, int tokenY) {
            this.name = name; this.startX = sx; this.startY = sy; this.endX = ex; this.endY = ey;
            this.type = type; this.mayor = mayor; this.motto = motto; this.citizens = citizens;
            this.permanent = permanent; this.tokenX = tokenX; this.tokenY = tokenY;
        }
    }

    public static final class Altar {
        public final String name, type; public final int x, y;
        public Altar(String name, int x, int y, String type) {
            this.name = name; this.x = x; this.y = y; this.type = type;
        }
    }

    public static final class Tower {
        public final String name; public final int x, y; public final byte kingdom; public final float damage;
        public Tower(String name, int x, int y, byte kingdom, float damage) {
            this.name = name; this.x = x; this.y = y; this.kingdom = kingdom; this.damage = damage;
        }
    }
}
