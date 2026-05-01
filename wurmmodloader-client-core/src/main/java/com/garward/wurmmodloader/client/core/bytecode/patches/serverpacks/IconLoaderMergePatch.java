package com.garward.wurmmodloader.client.core.bytecode.patches.serverpacks;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;

import javassist.CtClass;
import javassist.CtMethod;

import java.util.Collection;
import java.util.Collections;

/**
 * Replaces {@code IconLoader.initIcons()} with a per-slot merge across every
 * pack that ships an iconsheet, then appends dynamic-icon sheets past the
 * vanilla seven.
 *
 * <p>Vanilla picks the first pack that claims a given iconsheet key and uses
 * that pack's PNG wholesale. Server-pack authors routinely ship overlay
 * sheets — most slots transparent, a handful of redrawn icons — expecting
 * the loader to layer them on top of the vanilla sheet. With first-match-wins,
 * an overlay pack at the front of the chain wipes every icon it didn't
 * touch.
 *
 * <p>For sheets 0–6 (the seven vanilla iconsheets) we run the
 * {@link com.garward.wurmmodloader.client.core.serverpacks.IconSheetMerger}:
 * lowest-priority pack first, each higher-priority pack painted over with
 * {@code SrcOver}. <strong>Do not bypass the merger here</strong> —
 * Ago-server overlay packs (iconzz, etc.) target these sheets and rely on
 * per-slot composite over vanilla.
 *
 * <p>For sheets 7+ we ask
 * {@link com.garward.wurmmodloader.client.core.serverpacks.ModIconAtlasStore
 * ModIconAtlasStore} for a pre-painted atlas built from the dynamic icon
 * registry. Total sheet count = {@code 7 + extraSheetCount()}.
 * {@code IconLoader.getIcon(id)} indexes {@code itemIconImages[id/240]}, so
 * once the array is sized correctly, ids ≥ 1680 just resolve.
 *
 * <p>If the registry hasn't synced yet (vanilla client, or pre-handshake),
 * {@code extraSheetCount()} returns 0 and behavior is identical to the old
 * 7-sheet patch.
 *
 * @since 0.4.1
 */
public class IconLoaderMergePatch implements BytecodePatch {

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.resources.textures.IconLoader";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        CtMethod initIcons = ctClass.getMethod("initIcons", "()V");
        // Build the array fully in a local, then publish in a single
        // assignment. The merger is slow (PNG round-trip x 7), so the gap
        // between "fresh array of nulls" and "all slots filled" is wide.
        // The render thread keeps calling IconLoader.getIcon() in parallel —
        // if it reads itemIconImages[0] while it's still null, drawImage
        // silently draws nothing, the empty BufferedImage becomes a GL
        // texture, gets cached, and the inventory-group icon stays invisible
        // even after the merge completes.
        //
        // Dynamic sheets (idx >= 7) come from ModIconAtlasStore. Slots that
        // reference an as-yet-undownloaded pack land transparent; the
        // registry-sync rebuild trigger flushes IconLoader.loadedImages and
        // calls initIcons again so they reload from the freshly-painted
        // atlas.
        initIcons.setBody(
            "{" +
            "    int vanillaCount = com.wurmonline.shared.constants.IconConstants.ICON_SHEET_FILE_NAMES.length;" +
            "    int extras = com.garward.wurmmodloader.client.core.serverpacks.ModIconAtlasStore.extraSheetCount();" +
            "    java.awt.Image[] sheets = new java.awt.Image[vanillaCount + extras];" +
            "    for (int i = 0; i < vanillaCount; i = i + 1) {" +
            "        java.awt.image.BufferedImage merged = com.garward.wurmmodloader.client.core.serverpacks.IconSheetMerger" +
            "            .loadMergedSheet(com.wurmonline.shared.constants.IconConstants.ICON_SHEET_FILE_NAMES[i]);" +
            "        if (merged == null) {" +
            "            throw com.wurmonline.client.GameCrashedException.forCrash(" +
            "                \"Could not load icon sheet \" + com.wurmonline.shared.constants.IconConstants.ICON_SHEET_FILE_NAMES[i]);" +
            "        }" +
            "        sheets[i] = merged;" +
            "    }" +
            "    for (int j = 0; j < extras; j = j + 1) {" +
            "        java.awt.image.BufferedImage atlas = com.garward.wurmmodloader.client.core.serverpacks.ModIconAtlasStore.sheet(j);" +
            "        sheets[vanillaCount + j] = atlas;" +
            "    }" +
            "    itemIconImages = sheets;" +
            "}"
        );
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public String getDescription() {
        return "IconLoader.initIcons() per-slot merge across pack chain";
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.resources.textures.iconloader.initicons");
    }
}
