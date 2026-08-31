# OpenShulk (1.21.x) — with anti-dupe

Right-click a shulker box in your main hand or off hand to open its contents
in a virtual inventory, without placing the block down.

## What causes the dupe this replaces

Most "open shulker in hand" plugins track *which slot* the shulker was held
in, then write the edited contents back into "whatever is in that slot" when
the GUI closes. If you move the shulker to a different slot mid-edit (most
commonly: hover the open GUI and press a hotbar number key, or press F to
swap offhand), the plugin can't tell the shulker moved — it just writes the
new contents into that slot again, duplicating everything you removed.

## How this version prevents it

- Every shulker gets a random UUID stamped into its item NBT the first time
  it's opened.
- While the GUI is open, the player's own inventory is fully locked (no
  clicks or drags in it), which blocks ordinary slot-swapping.
- The hotbar-number-key swap and the offhand-swap key are specifically
  checked too, since Bukkit fires those as events on the *top* inventory
  even though they mutate the *bottom* (player) inventory — a common blind
  spot in naive implementations, and very likely the exact glitch you saw.
- Dropping the shulker while it's open is blocked.
- On close (and on player quit, as a backstop), the plugin re-reads the item
  from the recorded hand slot and checks the stamped UUID still matches
  before writing anything back. If it doesn't match, it refuses to write —
  no new contents get created, so there's nothing to duplicate.
- Nesting another shulker box or a bundle inside the open one is blocked,
  matching vanilla behavior for real shulker boxes.

## Building

You'll need Java 21 and Maven installed locally (this sandbox has no
network access to download dependencies, so I couldn't compile it here —
you'll want to build it on your own machine or CI):

```bash
mvn clean package
```

The output jar will be at `target/OpenShulk-1.21.x.jar`. Drop it in your
server's `plugins/` folder.

Built against the Paper API (`paper-api:1.21.1-R0.1-SNAPSHOT`), which is
compatible with Spigot at runtime for a plugin this size (no Paper-only API
is used beyond what Spigot also implements).

## Known trade-off

While a shulker is open, the player's whole inventory is locked (you can't
click around in it). This is deliberate — it's the simplest guarantee against
slot-swap dupes. If you want a version that only locks the *specific* shulker
slot and leaves the rest of the inventory usable, that's doable but needs a
bit more nuance (has to also block hotbar-key swaps, offhand swaps, and
shift-clicks that would move the shulker specifically) — let me know and I
can build that variant instead.
