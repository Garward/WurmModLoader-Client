# Client UI Layout API

A small, opinionated layout layer on top of Wurm's GUI primitives. Lets a mod
build a sidebar of bordered icon buttons (or any structured panel) without
guessing pixel sizes or fighting Wurm's internal sizing rules.

Package: `com.garward.wurmmodloader.client.api.gui`

## TL;DR

```java
ModBorderPanel main = new ModBorderPanel("Live Map");
ModStackPanel sidebar = new ModStackPanel("Sidebar", ArrayDirection.VERTICAL)
        .setBackgroundPainted(true)
        .setPadding(Insets.uniform(4))
        .setGap(4);
sidebar.setInitialSize(48, 620, false);

sidebar.addChild(new ModImageButton(MyMod.class,
        "/com/example/icons/zoom_in.png", "Zoom in", view::zoomIn));
sidebar.addChild(new ModImageButton(MyMod.class,
        "/com/example/icons/zoom_out.png", "Zoom out", view::zoomOut));

main.setRegion(view, BorderRegion.CENTER);
main.setRegion(sidebar, BorderRegion.EAST);
```

That's it: a vertical sidebar with Wurm's wood-panel background, three native
bordered icon buttons sized as squares matching the sidebar width, evenly
spaced with 4px gaps. No pixel constants for button sizes.

## Why this exists

Wurm's `WurmArrayPanel` shrinks to the sum of its children's preferred sizes
and stretches the cross axis without per-child control. `WurmBorderPanel` only
splits into N/E/S/W/CENTER without alignment within each region. Worse,
`FlexComponent.setLocation` silently overrides any width/height you pass if
the child has `FIXED_WIDTH` / `FIXED_HEIGHT` flags set — which is almost every
button by default. The result is buttons that ignore your layout and end up
stretched, squished, or invisible.

The layout API works with these constraints instead of around them:

- Containers do their own layout (no reliance on `WurmArrayPanel`'s sizing).
- Children declare intent via `LayoutHints` instead of fixed pixel sizes.
- Widgets like `ModImageButton` clear `sizeFlags` so the layout's sizing
  actually sticks.

## Concepts

### `Alignment`

Per-axis alignment for a child within its slot:

- `START` — top / left
- `CENTER` — middle
- `END` — bottom / right
- `FILL` — stretch to slot extent on that axis (default for both axes)

### `Insets`

Immutable per-edge spacing in pixels. Build with:

- `Insets.uniform(4)` — 4 on every edge
- `Insets.symmetric(vertical, horizontal)`
- `Insets.of(top, right, bottom, left)`

Used as container padding (inside the panel) and child margin (outside the
child).

### `LayoutHints`

Per-child layout instructions. Mutable, fluent. Defaults give "fill the slot
in both axes," which is what you want for full-width contents.

| Field | Default | Effect |
|---|---|---|
| `alignX`, `alignY` | `FILL`, `FILL` | Per-axis alignment within the slot |
| `aspectRatio` | `0` (off) | If `> 0`, derive main-axis size from cross-axis size. `1f` = square. |
| `weight` | `0` (off) | If `> 0`, gets a share of remaining main-axis space proportional to weight. |
| `preferredWidth` / `preferredHeight` | `-1` (unset) | Fixed size on that axis when alignment isn't FILL. |
| `margin` | `Insets.ZERO` | Outer spacing around the child. |

Resolution per axis:

1. If `weight > 0` → child gets a share of remaining main-axis space after
   fixed children are sized.
2. Else if a `preferred` size is set on that axis → that wins.
3. Else if `aspectRatio > 0` → main-axis size derived from cross-axis size.
4. Else the child's natural component size is used.

Cross-axis: `Alignment.FILL` (default) stretches; any other alignment uses the
preferred or aspect-derived size and pins it.

### `LayoutHints.Provider`

Widgets that have natural layout intent implement this and return their
default hints. `ModStackPanel.addChild(child)` will pick those up
automatically. Saves callers from writing `aspectRatio(1f)` for every icon
button. Override by passing explicit hints to
`addChild(child, customHints)`.

`ModImageButton` provides `aspectRatio(1f).align(CENTER, START)` so dropped
into a vertical sidebar it auto-sizes as a square the width of the sidebar.

## Containers

### `ModStackPanel`

Self-laying-out vertical/horizontal stack. The replacement for
`ModArrayPanel` + `ModPanel` when you want real layout control.

```java
ModStackPanel sidebar = new ModStackPanel("Sidebar", ArrayDirection.VERTICAL)
        .setBackgroundPainted(true)            // Wurm wood-panel fill
        .setPadding(Insets.uniform(4))         // inside the panel
        .setGap(4);                            // between children
sidebar.setInitialSize(48, 620, false);

sidebar.addChild(button1);                     // uses Provider hints (square)
sidebar.addChild(button2);
sidebar.addChild(spacer, new LayoutHints().weight(1f));  // pushes next to bottom
sidebar.addChild(footerButton);
```

Behavior:

- Pass 1 sizes fixed children (preferred / aspect / natural).
- Pass 2 distributes remaining main-axis space among `weight > 0` children.
- Cross-axis sizing follows each child's alignment + hints.
- Children added in order; vertical stacks render top-to-bottom.
- `setBackgroundPainted(true)` paints Wurm's standard wood-panel texture
  (the same `backgroundTexture2` that vanilla windows use) under the children.

### `ModBorderPanel`

Unchanged from before — five regions (NORTH/SOUTH/EAST/WEST/CENTER). Use it
as your top-level layout (e.g. map in CENTER, sidebar in EAST), then put a
`ModStackPanel` inside any region that needs structured contents.

## Widgets

### `ModImageButton`

Icon-faced button on top of Wurm's `WButton`, so you get the native bordered
button frame for free.

```java
new ModImageButton(MyMod.class, "/com/example/icons/zoom_in.png",
                   "Zoom in", view::zoomIn);   // size from layout
new ModImageButton(MyMod.class, "/com/example/icons/zoom_in.png",
                   "Zoom in", view::zoomIn, 56);   // legacy fixed-square mode
```

Constructor parameters:

- `resourceAnchor` — a class from your mod JAR (used as the classloader
  origin for the resource lookup).
- `resourcePath` — classpath-absolute path to the icon (e.g.
  `/com/example/icons/foo.png`). PNG, or anything `ImageIO` reads.
- `hoverText` — tooltip; pass `null` for none.
- `onClick` — `Runnable` invoked on click.
- `size` *(optional)* — when `> 0`, fixes the button to a square of that size
  (legacy mode). Pass `0` (or use the 4-arg ctor) when adding to a
  `ModStackPanel`; the layout decides the size.

Behavior:

- Native Wurm button frame via `super.renderComponent` (matches every other
  vanilla button).
- Icon is drawn inset by 4px inside the frame, centered, scaled to fit.
- **Auto alpha-bbox**: at load time the icon's non-transparent bounding box is
  computed and used as the UV region. A poorly-centered source PNG (icon
  drawn off-center within its 256×256 canvas) renders centered anyway.
- Aspect-correct: non-square icons render proportional inside the inset
  square instead of being stretched.
- Texture cache keyed by `(anchor class name) + (path)` — many buttons of the
  same icon pay one GL upload.
- Tinted darker while pressed.

### `ModButton`

Plain text button with a `Runnable` callback. Unchanged. Use this when you
don't need an icon.

## Worked example: a sidebar with a spacer

Three buttons at the top, one pinned to the bottom:

```java
ModStackPanel sidebar = new ModStackPanel("Sidebar", ArrayDirection.VERTICAL)
        .setBackgroundPainted(true)
        .setPadding(Insets.uniform(4))
        .setGap(4);
sidebar.setInitialSize(48, 620, false);

sidebar.addChild(zoomIn);     // Provider hints: square, fills width
sidebar.addChild(zoomOut);
sidebar.addChild(centerOn);

// Invisible spacer that consumes all remaining vertical space:
sidebar.addChild(new ModComponent("spacer") {}, new LayoutHints().weight(1f));

sidebar.addChild(settings);   // pinned to the bottom
```

## Authoring icon assets

A few practical notes from getting `ModImageButton` to look right:

- **Use a transparent background.** PNG with an alpha channel; the auto
  alpha-bbox depends on this.
- **Stroke weight matters at small sizes.** A 4px stroke on a 256×256 source
  becomes invisible at 28×28 even with nearest-neighbor filtering. Aim for
  strokes that survive a ~9× downscale; ~14–20px on a 256px canvas is safe.
- **Centering doesn't matter for aspect-correct rendering.** The auto bbox
  re-centers off-center icons. But if you draw an icon flush against the top
  edge of the canvas, the bbox-derived aspect will be tall+thin, so the icon
  renders narrower than expected. Leave a few pixels of true transparent
  padding around the icon if you want it to render proportional.
- **Match Wurm's palette.** Black or near-black icons read well over the
  wood-panel background and the native button frame. Bright/low-contrast
  colors compete with the frame and feel out of place.

## Migrating from `ModArrayPanel` + `ModPanel`

Before:

```java
ModArrayPanel<WButton> buttons =
        new ModArrayPanel<>("Buttons", ArrayDirection.VERTICAL);
buttons.setInitialSize(BUTTON_PANEL_WIDTH, MAP_HEIGHT, false);
buttons.addComponent(new ModImageButton(anchor, "/.../zoom_in.png",
        "Zoom in", view::zoomIn, ICON_BUTTON_SIZE));   // fixed size
buttons.addComponent(...);

ModPanel sidebar = new ModPanel("Sidebar", buttons);
sidebar.setBackgroundPainted(true);
sidebar.setInitialSize(BUTTON_PANEL_WIDTH, MAP_HEIGHT, false);

borderPanel.setRegion(sidebar, BorderRegion.EAST);
```

After:

```java
ModStackPanel sidebar = new ModStackPanel("Sidebar", ArrayDirection.VERTICAL)
        .setBackgroundPainted(true)
        .setPadding(Insets.uniform(4))
        .setGap(4);
sidebar.setInitialSize(BUTTON_PANEL_WIDTH, MAP_HEIGHT, false);

sidebar.addChild(new ModImageButton(anchor, "/.../zoom_in.png",
        "Zoom in", view::zoomIn));     // layout sizes it
sidebar.addChild(...);

borderPanel.setRegion(sidebar, BorderRegion.EAST);
```

You drop the wrapper, drop the `ICON_BUTTON_SIZE` constant, and gain real
spacing and padding control.
