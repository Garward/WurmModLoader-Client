package com.garward.wurmmodloader.client.api.bytecode;

import javassist.CtClass;
import java.util.Collection;
import java.util.Collections;

/**
 * Interface for bytecode patches that modify Wurm client classes.
 *
 * <p>Bytecode patches are applied during client startup to inject hooks
 * into the Wurm client without modifying the source code. Each patch
 * targets a specific class and modifies its bytecode to fire events.
 *
 * <h2>Patch Lifecycle</h2>
 * <ol>
 *   <li>Patches are registered with {@code PatchRegistry}</li>
 *   <li>During client startup, {@code PatchManager} applies all patches</li>
 *   <li>Each patch's {@link #apply(CtClass)} method is called once</li>
 *   <li>Modified classes are loaded into the client</li>
 * </ol>
 *
 * <h2>Example Patch</h2>
 * <pre>{@code
 * public class ClientInitPatch implements BytecodePatch {
 *     @Override
 *     public String getTargetClassName() {
 *         return "com.wurmonline.client.WurmClientBase";
 *     }
 *
 *     @Override
 *     public void apply(CtClass ctClass) throws Exception {
 *         CtMethod method = ctClass.getDeclaredMethod("init");
 *         method.insertAfter(
 *             "com.garward.wurmmodloader.client.modloader.ProxyClientHook.fireClientInitEvent();"
 *         );
 *     }
 *
 *     @Override
 *     public Collection<String> getConflictKeys() {
 *         return Collections.singleton("client.lifecycle.init");
 *     }
 * }
 * }</pre>
 *
 * <h2>Important Rules</h2>
 * <ul>
 *   <li>Keep patches minimal - only inject hook calls</li>
 *   <li>Always call ProxyClientHook static methods (ending in "Event")</li>
 *   <li>Never put game logic in patches - use events instead</li>
 *   <li>Test patches thoroughly - bytecode errors crash the client</li>
 *   <li>Use fully qualified class names in insertBefore/insertAfter</li>
 *   <li>Declare conflict keys to prevent patches from breaking each other</li>
 * </ul>
 *
 * @since 0.1.0
 * @see PatchRegistry
 */
public interface BytecodePatch {

    /**
     * Returns the fully qualified name of the class to patch.
     *
     * <p>Example: {@code "com.wurmonline.client.WurmClientBase"}
     *
     * @return the target class name
     */
    String getTargetClassName();

    /**
     * Applies the bytecode modifications to the target class.
     *
     * <p>This method is called once during client startup with a Javassist
     * {@code CtClass} representing the target class. Use Javassist APIs to
     * modify the class bytecode.
     *
     * <p><b>Important:</b> Always use fully qualified class names when
     * injecting code. For example:
     * <pre>{@code
     * method.insertAfter("com.garward.wurmmodloader.client.modloader.ProxyClientHook.fireClientInitEvent();");
     * }</pre>
     *
     * @param ctClass the Javassist class to modify
     * @throws Exception if the patch fails to apply
     */
    void apply(CtClass ctClass) throws Exception;

    /**
     * Returns the priority of this patch.
     *
     * <p>Higher priority patches are applied first. Default is 100.
     * Use this when patches have dependencies on each other.
     *
     * @return the patch priority (higher = applied first)
     */
    default int getPriority() {
        return 100;
    }

    /**
     * Returns a human-readable description of this patch.
     *
     * <p>Used for logging and debugging. Should briefly describe what
     * the patch does.
     *
     * @return a description of the patch
     */
    default String getDescription() {
        return getClass().getSimpleName();
    }

    /**
     * Returns the conflict keys for this patch.
     *
     * <p>Conflict keys prevent multiple patches from modifying the same
     * code in incompatible ways. If two patches share a conflict key,
     * the patch manager will detect this and warn the user.
     *
     * <p><b>Conflict Key Naming Convention:</b>
     * <ul>
     *   <li>{@code client.lifecycle.init} - Client initialization</li>
     *   <li>{@code client.lifecycle.tick} - Main game loop tick</li>
     *   <li>{@code client.input.movement} - Movement input handling</li>
     *   <li>{@code client.network.position} - Position updates from server</li>
     *   <li>{@code client.render.frame} - Frame rendering</li>
     *   <li>{@code client.entity.npc} - NPC entity updates</li>
     *   <li>{@code client.combat.animation} - Combat animations</li>
     * </ul>
     *
     * <p><b>Example Usage:</b>
     * <pre>{@code
     * @Override
     * public Collection<String> getConflictKeys() {
     *     return Arrays.asList("client.input.movement", "client.input.keyboard");
     * }
     * }</pre>
     *
     * <p>Return an empty collection if this patch has no conflicts.
     *
     * @return collection of conflict key strings
     */
    default Collection<String> getConflictKeys() {
        return Collections.emptyList();
    }
}
