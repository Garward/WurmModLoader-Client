package com.garward.mods.declarativeui;

/**
 * Wire protocol for the {@code com.garward.ui} declarative UI channel.
 *
 * <p>Frames are binary, written via {@code PacketWriter} / read via
 * {@code PacketReader} (both {@link java.io.DataOutputStream} / {@link java.io.DataInputStream}-
 * based, so integers are big-endian and strings use modified UTF-8).
 *
 * <p>Server → client ops:
 * <pre>
 *   0x01 MOUNT     : UTF windowId, UTF title, int width, int height, TREE
 *   0x02 UNMOUNT   : UTF windowId
 *   0x03 BIND      : UTF windowId, short n, [UTF key, UTF value]*
 *   0x05 SHOW      : UTF windowId
 *   0x06 HIDE      : UTF windowId
 * </pre>
 *
 * <p>Client → server ops:
 * <pre>
 *   0x10 ACTION    : UTF windowId, UTF actionId, UTF payload
 * </pre>
 *
 * <p>TREE is recursive:
 * <pre>
 *   UTF widgetType,
 *   short propCount, [UTF key, UTF value]*,
 *   short childCount, TREE[]
 * </pre>
 *
 * <p>Widget types (v0): {@code Label}, {@code Button}, {@code StackPanel},
 * {@code Spacer}. Unknown types are logged and skipped — they never crash the
 * HUD.
 *
 * <p>Common props:
 * <ul>
 *   <li>{@code Label}: {@code text}, {@code bind}, {@code color} ("r,g,b,a")</li>
 *   <li>{@code Button}: {@code label}, {@code action}, {@code hover}</li>
 *   <li>{@code StackPanel}: {@code direction} ("vertical"|"horizontal"),
 *       {@code gap} (int), {@code padding} (int)</li>
 *   <li>{@code Spacer}: {@code width}, {@code height} (ints)</li>
 * </ul>
 */
public final class UiProtocol {

    public static final String CHANNEL = "com.garward.ui";

    // Server → client
    public static final byte OP_MOUNT    = 0x01;
    public static final byte OP_UNMOUNT  = 0x02;
    public static final byte OP_BIND     = 0x03;
    public static final byte OP_SHOW     = 0x05;
    public static final byte OP_HIDE     = 0x06;

    // Client → server
    public static final byte OP_ACTION   = 0x10;

    // Widget types
    public static final String W_LABEL      = "Label";
    public static final String W_BUTTON     = "Button";
    public static final String W_STACK      = "StackPanel";
    public static final String W_SPACER     = "Spacer";
    public static final String W_CANVAS     = "Canvas";
    public static final String W_EDGE       = "Edge";
    public static final String W_IMAGE      = "Image";

    // Hard caps — reject obviously-malformed trees without crashing the HUD.
    // MAX_WIDGETS lifted from 256 to 4096 to fit large skill-tree layouts
    // (PoE-tier ~1300 nodes + edges; 4096 leaves headroom).
    public static final int MAX_TREE_DEPTH = 16;
    public static final int MAX_WIDGETS_PER_WINDOW = 4096;

    private UiProtocol() {}
}
