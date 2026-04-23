package com.garward.wurmmodloader.client.core.bytecode.patches;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.Modifier;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

/**
 * Patches {@code RangeOption.set(int)} to detect FOV changes.
 *
 * <p>This patch is <b>surgical</b> - it contains ZERO logic. It only routes
 * to {@link com.garward.wurmmodloader.client.modloader.ProxyClientHook} which
 * contains all decision-making logic.</p>
 *
 * <h2>Architecture Compliance:</h2>
 * <ul>
 *   <li>✅ NO conditionals in patch</li>
 *   <li>✅ NO loops in patch</li>
 *   <li>✅ NO calculations in patch</li>
 *   <li>✅ Only inserts hook call</li>
 * </ul>
 *
 * <h2>Patch Target:</h2>
 * <pre>{@code
 * // Original: RangeOption.set(int)
 * public void set(int newValue) {
 *     this.value = Math.max(this.low, Math.min(this.high, newValue));
 * }
 *
 * // After patch:
 * public void set(int newValue) {
 *     int _wmlOldValue = this.value; // Capture old value BEFORE assignment
 *     this.value = Math.max(this.low, Math.min(this.high, newValue));
 *     ProxyClientHook.fireFOVChangedEventIfApplicable(this, _wmlOldValue, this.value);
 * }
 * }</pre>
 *
 * <h2>Event Flow:</h2>
 * <pre>
 * RangeOption.set(newValue)
 *   ↓
 * ProxyClientHook.fireFOVChangedEventIfApplicable(this, oldVal, newVal) [STATIC]
 *   ↓ (checks if this == Options.fovHorizontal and oldVal != newVal)
 * ClientHook.fireFOVChanged(oldVal, newVal) [INSTANCE]
 *   ↓
 * EventBus.post(new FOVChangedEvent(oldVal, newVal))
 *   ↓
 * @SubscribeEvent handlers in mods
 * </pre>
 *
 * @since 0.2.0
 */
public class FOVChangePatch implements BytecodePatch {

    private static final Logger logger = Logger.getLogger(FOVChangePatch.class.getName());

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.options.RangeOption";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        logger.info("[FOVChangePatch] Applying FOV change detection patch...");

        CtMethod setMethod = ctClass.getDeclaredMethod("set");

        String proxyClass = "com.garward.wurmmodloader.client.modloader.ProxyClientHook";

        // insertBefore/insertAfter are separate scopes, so a local can't bridge
        // them. Stash the pre-assignment value on a synthetic instance field.
        CtField stash = new CtField(CtClass.intType, "_wmlOldValue", ctClass);
        stash.setModifiers(Modifier.PRIVATE | Modifier.TRANSIENT);
        ctClass.addField(stash);

        setMethod.insertBefore("this._wmlOldValue = this.value;");

        setMethod.insertAfter(
            "try {" +
            "  " + proxyClass + ".fireFOVChangedEventIfApplicable(this, this._wmlOldValue, this.value);" +
            "} catch (Throwable t) { t.printStackTrace(); }"
        );

        logger.info("[FOVChangePatch] ✅ Successfully patched RangeOption.set() for FOV change detection");
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.options.fov");
    }

    @Override
    public String getDescription() {
        return "Detects FOV changes in RangeOption.set() - SURGICAL (no logic in patch)";
    }
}
