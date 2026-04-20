package com.garward.wurmmodloader.client.core.bytecode.patches.gui;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CtClass;
import javassist.CtMethod;

import java.util.Collection;
import java.util.Collections;

/**
 * Patches WurmComponent mouse input methods to fire input events.
 *
 * <p>This patch allows mods to receive mouse input events for their custom
 * GUI components: clicks, drags, releases.
 *
 * <p>Patched methods:
 * <ul>
 *   <li>leftPressed - fires MouseClickEvent (button=0, pressed=true)</li>
 *   <li>rightPressed - fires MouseClickEvent (button=1, pressed=true)</li>
 *   <li>leftReleased - fires MouseClickEvent (button=0, pressed=false)</li>
 *   <li>rightReleased - fires MouseClickEvent (button=1, pressed=false)</li>
 *   <li>mouseDragged - fires MouseDragEvent</li>
 * </ul>
 *
 * @since 0.2.0
 */
public class WurmComponentInputPatch implements BytecodePatch {

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.renderer.gui.WurmComponent";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        CtClass[] mouseParams = new CtClass[] {
            CtClass.intType,  // xMouse
            CtClass.intType   // yMouse
        };

        CtClass[] pressedParams = new CtClass[] {
            CtClass.intType,  // xMouse
            CtClass.intType,  // yMouse
            CtClass.intType   // clickCount
        };

        // Hook leftPressed - NO LOGIC, just pass raw data + method name
        CtMethod leftPressed = ctClass.getDeclaredMethod("leftPressed", pressedParams);
        leftPressed.insertBefore(
            "{ " +
            "  try {" +
            "    com.garward.wurmmodloader.client.modloader.ProxyClientHook.fireMouseInputEvent(" +
            "      this, \"leftPressed\", $1, $2, $3" +
            "    );" +
            "  } catch (Throwable t) {" +
            "    t.printStackTrace();" +
            "  }" +
            "}"
        );

        // Hook rightPressed - NO LOGIC, just pass raw data + method name
        CtMethod rightPressed = ctClass.getDeclaredMethod("rightPressed", pressedParams);
        rightPressed.insertBefore(
            "{ " +
            "  try {" +
            "    com.garward.wurmmodloader.client.modloader.ProxyClientHook.fireMouseInputEvent(" +
            "      this, \"rightPressed\", $1, $2, $3" +
            "    );" +
            "  } catch (Throwable t) {" +
            "    t.printStackTrace();" +
            "  }" +
            "}"
        );

        // Hook leftReleased - NO LOGIC, just pass raw data + method name
        CtMethod leftReleased = ctClass.getDeclaredMethod("leftReleased", mouseParams);
        leftReleased.insertBefore(
            "{ " +
            "  try {" +
            "    com.garward.wurmmodloader.client.modloader.ProxyClientHook.fireMouseInputEvent(" +
            "      this, \"leftReleased\", $1, $2, 1" + // clickCount=1 for release
            "    );" +
            "  } catch (Throwable t) {" +
            "    t.printStackTrace();" +
            "  }" +
            "}"
        );

        // Hook rightReleased - NO LOGIC, just pass raw data + method name
        CtMethod rightReleased = ctClass.getDeclaredMethod("rightReleased", mouseParams);
        rightReleased.insertBefore(
            "{ " +
            "  try {" +
            "    com.garward.wurmmodloader.client.modloader.ProxyClientHook.fireMouseInputEvent(" +
            "      this, \"rightReleased\", $1, $2, 1" + // clickCount=1 for release
            "    );" +
            "  } catch (Throwable t) {" +
            "    t.printStackTrace();" +
            "  }" +
            "}"
        );

        // Hook mouseDragged - NO LOGIC, just pass raw data
        // Delta calculation happens in MouseInputEventLogic
        CtMethod mouseDragged = ctClass.getDeclaredMethod("mouseDragged", mouseParams);
        mouseDragged.insertBefore(
            "{ " +
            "  try {" +
            "    com.garward.wurmmodloader.client.modloader.ProxyClientHook.fireMouseDragRawEvent(" +
            "      this, $1, $2" +
            "    );" +
            "  } catch (Throwable t) {" +
            "    t.printStackTrace();" +
            "  }" +
            "}"
        );
    }

    @Override
    public int getPriority() {
        return 100;  // High priority
    }

    @Override
    public String getDescription() {
        return "GUI component input hook";
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.gui.component.input");
    }
}
