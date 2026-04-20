package com.garward.wurmmodloader.client.core.bytecode.patches.gui;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CtClass;
import javassist.CtMethod;

import java.util.Collection;
import java.util.Collections;

/**
 * Patches WurmComponent.renderComponent() to fire ComponentRenderEvent.
 *
 * <p>This patch allows mods to receive rendering events for their custom
 * GUI components without needing to extend WurmComponent or be in the
 * com.wurmonline.client.renderer.gui package.
 *
 * <p>The event provides:
 * <ul>
 *   <li>Queue - rendering queue</li>
 *   <li>Component bounds (x, y, width, height)</li>
 *   <li>Alpha - transparency value</li>
 * </ul>
 *
 * @since 0.2.0
 */
public class WurmComponentRenderPatch implements BytecodePatch {

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.renderer.gui.WurmComponent";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        // Hook the renderComponent(Queue, float) method
        CtMethod method = ctClass.getDeclaredMethod("renderComponent",
            new CtClass[] {
                ctClass.getClassPool().get("com.wurmonline.client.renderer.backend.Queue"),
                CtClass.floatType
            });

        // Fire event AFTER component's own rendering
        // Pass: this (component), queue ($1), x, y, width, height, alpha ($2)
        method.insertAfter(
            "{ " +
            "  try {" +
            "    com.garward.wurmmodloader.client.modloader.ProxyClientHook.fireComponentRenderEvent(" +
            "      this, $1, this.x, this.y, this.width, this.height, $2" +
            "    );" +
            "  } catch (Throwable t) {" +
            "    t.printStackTrace();" +
            "  }" +
            "}"
        );
    }

    @Override
    public int getPriority() {
        return 100;  // High priority - fundamental GUI feature
    }

    @Override
    public String getDescription() {
        return "GUI component rendering hook";
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.gui.component.render");
    }
}
