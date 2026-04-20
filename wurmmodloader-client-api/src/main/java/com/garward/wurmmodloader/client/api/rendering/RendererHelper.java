package com.garward.wurmmodloader.client.api.rendering;

/**
 * Helper class for rendering operations in custom GUI components.
 *
 * <p>This class provides mod-safe access to Wurm's rendering system without
 * requiring direct access to package-private classes. All methods use reflection
 * internally to call the appropriate Wurm rendering methods.
 *
 * <p>Usage in ComponentRenderEvent:
 * <pre>{@code
 * @SubscribeEvent
 * public void onRender(ComponentRenderEvent event) {
 *     // Draw a textured rectangle
 *     RendererHelper.drawTexture(
 *         event.getQueue(),
 *         myTexture,
 *         event.getX(), event.getY(),
 *         256, 256
 *     );
 * }
 * }</pre>
 *
 * @since 0.2.0
 */
public class RendererHelper {

    private static Class<?> rendererClass;
    private static java.lang.reflect.Method texturedQuadMethod;
    private static java.lang.reflect.Method texturedQuadAlphaMethod;

    static {
        try {
            rendererClass = Class.forName("com.wurmonline.client.renderer.gui.Renderer");

            // Find texturedQuad method (basic version)
            texturedQuadMethod = findMethod(rendererClass, "texturedQuad",
                Object.class, // Queue
                Object.class, // Texture
                float.class, float.class, float.class, float.class, // RGBA
                float.class, float.class, // X, Y
                float.class, float.class  // Width, Height
            );

            // Find texturedQuad with alpha blend
            texturedQuadAlphaMethod = findMethod(rendererClass, "texturedQuadAlphaBlend",
                Object.class, // Queue
                Object.class, // Texture
                float.class, float.class, float.class, float.class, // RGBA
                float.class, float.class, // X, Y
                float.class, float.class, // Width, Height
                float.class, float.class, // Texture U, V start
                float.class, float.class  // Texture U, V scale
            );

        } catch (Exception e) {
            System.err.println("[RendererHelper] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Draw a textured rectangle.
     *
     * @param queue rendering queue (from ComponentRenderEvent)
     * @param texture ImageTexture object
     * @param x screen X position
     * @param y screen Y position
     * @param width rectangle width
     * @param height rectangle height
     */
    public static void drawTexture(Object queue, Object texture, float x, float y, float width, float height) {
        drawTexture(queue, texture, 1.0f, 1.0f, 1.0f, 1.0f, x, y, width, height);
    }

    /**
     * Draw a textured rectangle with RGBA tint.
     *
     * @param queue rendering queue
     * @param texture ImageTexture object
     * @param r red tint (0.0 - 1.0)
     * @param g green tint (0.0 - 1.0)
     * @param b blue tint (0.0 - 1.0)
     * @param a alpha transparency (0.0 - 1.0)
     * @param x screen X position
     * @param y screen Y position
     * @param width rectangle width
     * @param height rectangle height
     */
    public static void drawTexture(Object queue, Object texture,
                                   float r, float g, float b, float a,
                                   float x, float y, float width, float height) {
        try {
            if (texturedQuadMethod != null) {
                texturedQuadMethod.invoke(null, queue, texture, r, g, b, a, x, y, width, height);
            }
        } catch (Exception e) {
            System.err.println("[RendererHelper] drawTexture failed: " + e.getMessage());
        }
    }

    /**
     * Draw a textured rectangle with UV coordinates and alpha blending.
     *
     * @param queue rendering queue
     * @param texture ImageTexture object
     * @param r red tint (0.0 - 1.0)
     * @param g green tint (0.0 - 1.0)
     * @param b blue tint (0.0 - 1.0)
     * @param a alpha transparency (0.0 - 1.0)
     * @param x screen X position
     * @param y screen Y position
     * @param width rectangle width
     * @param height rectangle height
     * @param texU texture U coordinate start
     * @param texV texture V coordinate start
     * @param texUScale texture U coordinate scale
     * @param texVScale texture V coordinate scale
     */
    public static void drawTextureWithUV(Object queue, Object texture,
                                         float r, float g, float b, float a,
                                         float x, float y, float width, float height,
                                         float texU, float texV, float texUScale, float texVScale) {
        try {
            if (texturedQuadAlphaMethod != null) {
                texturedQuadAlphaMethod.invoke(null, queue, texture, r, g, b, a,
                    x, y, width, height, texU, texV, texUScale, texVScale);
            }
        } catch (Exception e) {
            System.err.println("[RendererHelper] drawTextureWithUV failed: " + e.getMessage());
        }
    }

    /**
     * Helper to find method by signature.
     */
    private static java.lang.reflect.Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        try {
            java.lang.reflect.Method method = clazz.getDeclaredMethod(name, paramTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            // Try to find by name and parameter count (less strict)
            for (java.lang.reflect.Method m : clazz.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == paramTypes.length) {
                    m.setAccessible(true);
                    return m;
                }
            }
            System.err.println("[RendererHelper] Method not found: " + name);
            return null;
        }
    }
}
