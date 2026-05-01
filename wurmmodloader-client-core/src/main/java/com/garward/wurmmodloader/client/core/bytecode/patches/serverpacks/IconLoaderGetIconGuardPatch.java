package com.garward.wurmmodloader.client.core.bytecode.patches.serverpacks;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;

import javassist.CtClass;
import javassist.CtMethod;

import java.util.Collection;
import java.util.Collections;

/**
 * Defensive guard on {@code IconLoader.getIcon(Short)} — redirects any
 * out-of-bounds sheet request to vanilla sheet 0, slot 0, instead of
 * letting the renderer hit {@code itemIconImages[sheet]} with a stale
 * length and crash with {@link ArrayIndexOutOfBoundsException}.
 *
 * <p>The {@link IconLoaderMergePatch} sizes the icon-sheet array to
 * {@code 7 + extraSheetCount()}. {@code extraSheetCount()} is zero until the
 * server's {@code com.garward.icons} registry-sync packet lands. If the GUI
 * asks for an icon id whose sheet index falls past the current array length
 * — typical on early init, when the server registry is empty, or on a
 * vanilla server that never speaks the channel — we'd previously crash.
 *
 * <p>This patch makes the unknown-icon case render visibly (whatever sits
 * in vanilla slot 0) and never throw. Logging is deliberately omitted from
 * the hot path; the registry-sync trigger is the right place to emit
 * diagnostics, not the per-frame fetch.
 */
public class IconLoaderGetIconGuardPatch implements BytecodePatch {

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.resources.textures.IconLoader";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        CtMethod getIcon = ctClass.getMethod("getIcon", "(Ljava/lang/Short;)Lcom/wurmonline/client/resources/textures/ImageTexture;");
        // The guard only rewrites $1 in pathological cases; in the common
        // path (in-bounds, non-null sheet) we fall straight through to
        // vanilla code with zero overhead beyond two array reads.
        getIcon.insertBefore(
            "{" +
            "    int __sheet = $1.intValue() / 240;" +
            "    if (itemIconImages == null" +
            "            || __sheet < 0" +
            "            || __sheet >= itemIconImages.length" +
            "            || itemIconImages[__sheet] == null) {" +
            "        $1 = Short.valueOf((short) 0);" +
            "    }" +
            "}"
        );
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public String getDescription() {
        return "IconLoader.getIcon() OOB-sheet guard: redirect to slot 0 instead of AOOBE";
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.resources.textures.iconloader.geticon.guard");
    }
}
