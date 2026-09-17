# Hitboxes

Util > Hitboxes is a passive, default-off feature. Right-click its feature header
in `/kung` to expand the settings. `Add Hitbox` opens a focused search field with
seven visible results and a scroll bar. All registered entity types are available,
including projectiles, items, players and Ender Dragons. Names are English; search
also accepts registry IDs, ignores case and treats underscores like spaces.
Already selected types are excluded. Mouse wheel or Up/Down navigates; click or
Enter adds one type and closes the popup. Escape/Cancel changes nothing.

Selected types appear directly below Add Hitbox. Each row has a color swatch and
a red `-` removal button. The swatch opens a hue/saturation wheel, brightness
slider, preview and editable `#RRGGBB` field. Save/Enter applies the color;
Escape/Cancel discards edits. Invalid hex input cannot be saved. The wheel texture
is generated once per popup and released on close. Search and color popups block
input to the underlying menu and resize with it.

The Ender Dragon row expands into two independent switches: **Overall Box** for
the large parent bounding box, and **Body Part Boxes** for the smaller head,
body, wing and tail boxes. Both default on to preserve existing selections.
Either or both may be disabled; the color applies to both groups. These settings
persist even if the dragon type is removed and added again. Other entity types
keep their usual single box.

The same settings catalog supplies the optional OneConfig switch and action
buttons. Add/Edit opens the complete Kung editor, returning to its updated list
after selection. Closing that editor returns to OneConfig. Its native row catalog
refreshes outside the native screen, as the existing per-file audio controls do.

Selections and opaque RGB colors are stored in the `hitboxes` category of the
existing `config/kung/kung.json`. Disabling retains selections; older configs start
with an empty list. Invalid IDs/null entries are removed on load. Valid unknown
IDs are retained for temporarily absent mods and produce no boxes until present.

`HitboxesFeature` extracts interpolated bounding boxes from loaded entities once
per rendered frame through Fabric's extraction event. It applies Minecraft's
entity render-distance check and omits the first-person camera entity. Selected
Ender Dragons use the independent overall/part switches. Immutable box/color snapshots
belong to that frame; the draw event uses Minecraft's depth-tested line renderer
and flushes its line buffer. It changes no entity dimensions or server behavior
and does not toggle vanilla F3+B. Vanilla debug boxes may also appear if enabled.

`HitboxesTest` covers legacy defaults, normalization, persistence, duplicate adds,
dynamic menu rows, independent swatch/removal targets, the full registry search,
hex validation, wheel colors and independent dragon switches across save/reload
and removal/re-addition. The shared OneConfig tree supports the nested switches.
Live checks remain for all four dragon-box combinations, search/scroll/keyboard
interaction, popup placement at different GUI scales, native-menu return, color
rendering and moving/projectile/dragon hitboxes in-world.
