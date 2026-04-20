/**
 * Bytecode patches for the Wurm Unlimited client.
 *
 * <p>This package contains implementations of {@link com.garward.wurmmodloader.client.api.bytecode.BytecodePatch}
 * that modify Wurm client classes to inject event hooks.
 *
 * <h2>Available Patches</h2>
 * <ul>
 *   <li>{@link com.garward.wurmmodloader.client.core.bytecode.patches.ClientInitPatch} - Client initialization</li>
 *   <li>{@link com.garward.wurmmodloader.client.core.bytecode.patches.ClientTickPatch} - Game loop ticks</li>
 * </ul>
 *
 * <h2>Adding New Patches</h2>
 * <ol>
 *   <li>Implement {@link com.garward.wurmmodloader.client.api.bytecode.BytecodePatch}</li>
 *   <li>Register in {@link com.garward.wurmmodloader.client.core.bytecode.CorePatches}</li>
 *   <li>Add corresponding event to API if needed</li>
 * </ol>
 *
 * @since 0.1.0
 */
package com.garward.wurmmodloader.client.core.bytecode.patches;
