package com.garward.wurmmodloader.client.api.events.gui;

import com.garward.wurmmodloader.client.api.events.base.Event;

/**
 * Fired when a custom GUI component needs to render.
 *
 * <p>This event allows mods to render custom content in their GUI components
 * using the Wurm rendering queue. The event provides the rendering queue,
 * component bounds, and alpha value.
 *
 * <p>Use {@link com.garward.wurmmodloader.client.api.rendering.RendererHelper}
 * to perform rendering operations.
 *
 * <p>Example usage:
 * <pre>{@code
 * @SubscribeEvent
 * public void onRender(ComponentRenderEvent event) {
 *     // Render a textured quad
 *     RendererHelper.drawTexture(
 *         event.getQueue(),
 *         myTexture,
 *         event.getX(), event.getY(),
 *         event.getWidth(), event.getHeight()
 *     );
 * }
 * }</pre>
 *
 * @since 0.2.0
 */
public class ComponentRenderEvent extends Event {

    private final Object component; // The FlexComponent being rendered
    private final Object queue; // Queue (com.wurmonline.client.renderer.backend.Queue)
    private final int x, y; // Component position
    private final int width, height; // Component size
    private final float alpha; // Alpha transparency

    public ComponentRenderEvent(Object component, Object queue, int x, int y, int width, int height, float alpha) {
        super(false); // Not cancellable - rendering must happen

        this.component = component;
        this.queue = queue;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.alpha = alpha;
    }

    /**
     * Get the component being rendered.
     */
    public Object getComponent() {
        return component;
    }

    /**
     * Get the rendering queue (com.wurmonline.client.renderer.backend.Queue).
     */
    public Object getQueue() {
        return queue;
    }

    /**
     * Get component X position on screen.
     */
    public int getX() {
        return x;
    }

    /**
     * Get component Y position on screen.
     */
    public int getY() {
        return y;
    }

    /**
     * Get component width in pixels.
     */
    public int getWidth() {
        return width;
    }

    /**
     * Get component height in pixels.
     */
    public int getHeight() {
        return height;
    }

    /**
     * Get alpha transparency (0.0 - 1.0).
     */
    public float getAlpha() {
        return alpha;
    }

    @Override
    public String toString() {
        return "ComponentRender[x=" + x + ", y=" + y + ", w=" + width + ", h=" + height + ", alpha=" + alpha + "]";
    }
}
