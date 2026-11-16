# Dragon Mace — Project Status

Fabric mod for Minecraft 1.21.11. Server-side only: no client entrypoint, no registry entries, no
custom item classes. Every special item is a vanilla item carrying a persistent
`minecraft:custom_data` marker, so the mod works on a vanilla client.

Last updated: 2026-11-16.

## Build and test

| Command | Result |
| --- | --- |
| `./gradlew clean build` | BUILD SUCCESSFUL |
| `./gradlew test` | 203 tests, 0 failures, 0 errors, 0 skipped |
| `./gradlew runServer` | `Done (2.356s)!`, 0 ERROR lines, `ItemUseMixin` applied to `Item` |

`runServer` needs a free world directory and port, because another server process holds the
`run/world` session lock and port 25565 on this machine. Verified with:

```
./gradlew runServer --args="--universe <tmp>/dm-universe --world mixincheck --port 25566 --nogui"
```

With `-Dmixin.debug.verbose=true`, the log confirmed all 24 mixins applied, including
`ServerPlayerDeathMixin`, `ServerPlayerRespawnMixin` and `ServerPlayerTabListMixin` into
`ServerPlayer`, plus `MobPickupMixin` into `Mob`, `InventoryMixin` into `Inventory`,
`PlayerListMixin` into `PlayerList`, `ItemUseMixin` into `Item` and `BlockItemUseOnMixin` into
`BlockItem`.

The build requires a **Java 25** runtime. Fabric Loom 1.19.4 refuses to configure on Java 21
(`Dependency requires at least JVM runtime version 25`). Bytecode target stays `release = 21`, and
`dragon-mace.mixins.json` keeps `compatibilityLevel: JAVA_21`.

## Stage 1 — unique Dragon Mace (complete, unchanged)

- `DragonMaceRecipes` drops the vanilla mace recipe and adds `dragon-mace:dragon_mace`.
- `DragonMaceWorldData` is the persisted state machine (`UNCRAFTED` / `PRESENT` / `MISSING`).
- `DragonMaceCrafting`, `DragonMacePickup`, `DragonMaceTransfer`, `DragonMaceRespawn`,
  `DragonMaceDeathTransfer`, `DragonMaceProtection`, `DragonMaceOwnership` implement the lifecycle.
- Container, bundle, hopper, dispenser, item frame and falling-block guards all route through
  `DragonMaceProtection.isProtectedStack`.

## Stage 2 — Very Heavy Core, Draconic Mace and Dragon effect update (this update)

### Very Heavy Core

- Material is `minecraft:paper`, stamped with the custom identifier `dragon_mace:very_heavy_core` in
  `minecraft:custom_data` (`very_heavy_core` boolean plus `very_heavy_core_id` string). No item
  registry entry is registered, so the mod stays server-side only.
- Identity requires **both** the paper item type and the identifier, so ordinary paper can never be
  identified as a Very Heavy Core and a renamed paper stack cannot be promoted by its display name.
- Appearance reuses the vanilla Heavy Core model through the synced `minecraft:item_model` component
  (`minecraft:block/heavy_core`). No client asset is shipped and no registry entry is needed.
- Name `Very Heavy Core` in gold and the 19-line lore are written onto the stack, so every stack
  carries them. The lore keeps the `∞` glyph and the intentional `LEGNDARY CONSUMABLE` spelling.
- Paper is not a `BlockItem`, so the item cannot be placed and never reaches block interactions.
- Right-click consumption runs from `ItemUseMixin` at the head of `Item.use`, the single entry point
  for a right-click in the air, which also covers a click on a block.
- Identity survives dropping, picking up, container storage, transfer and server restarts, because
  every component lives on the stack itself.
- Recipe `dragon-mace:very_heavy_core`, exact 3x3 layout:

  | Conduit | Dragon Egg | Nether Star |
  | --- | --- | --- |
  | Creeper Head | Heavy Core | Piglin Head |
  | Wayfinder Armor Trim Template | Trident | Silence Armor Trim Template |

  All nine slots are real ingredients, so vanilla crafting consumes every one of them, including the
  Dragon Egg. The result is the paper core.
- No one-core-per-world restriction. A new core can be crafted whenever the previous one was
  consumed, withdrawn or destroyed.
- Crafting announces `[Dragon Mace] A Very Heavy Core has been crafted!` from
  `ResultSlot.checkTakeAchievements`, which covers both the click and the shift-click take paths
  exactly once per craft.

### Draconic Mace

- `minecraft:mace` + `draconic_mace` marker, layered on top of the existing `dragon_mace` marker, so
  the Stage 1 uniqueness system is untouched.
- Preserved: custom item name `Dragon Mace`, dark purple styling, bold lore footer, existing
  textures/models/assets (the item is still a vanilla mace, so no new assets are needed), `EPIC`
  rarity.
- Placeholder enchantments: Density V and Wind Burst III as real vanilla enchantments.
- `Draconic Rune III` has **no vanilla enchantment**. Its level is stored in custom data under
  `draconic_rune` for the future enchantment system to read; it is not a functioning enchantment
  today. This is the one part of the requested enchantment set that is not implemented.
- A mace that already carries enchantments is left alone, so a player's own enchantments are never
  overwritten. A `draconic_placeholder_enchantments` flag records whether this mod added them, so a
  withdrawal removes exactly what was added.
- Damage, a pre-existing `dragon_mace` marker and all other components survive conversion and revert.

### Recipe-book discovery and JEI

- `DragonMaceRecipeUnlock` unlocks `dragon-mace:very_heavy_core` through
  `ServerPlayer.awardRecipesByKey`, the vanilla path that adds the recipe to `ServerRecipeBook`,
  pushes the recipe display to the client and fires `RECIPE_UNLOCKED`. The known set is persisted in
  the player's saved data, so the unlock is permanent for that player and survives reconnects and
  restarts.
- Trigger points: `InventoryMixin` at the `RETURN` of `Inventory.add` when the insertion succeeded and
  the stack is a Dragon Egg (covers pickups, crafting results and slot-less inserts), and
  `PlayerListMixin` at the tail of `placeNewPlayer` for a player who already owned an egg.
- The recipe is a normal `ShapedRecipe`, so it carries a vanilla recipe display and appears in the
  crafting recipe book once unlocked. JEI reads the same synced recipe map, so it displays the recipe
  under the same discovery rule.
- A datapack advancement at `data/dragon-mace/advancement/very_heavy_core.json` was implemented first
  and then removed: the server loaded exactly 1584 advancements, which is the vanilla file count, so
  the mod's `data/` pack was not applied in this environment. The code path above does not depend on
  datapack loading.

### Curse of the Dragons

- `curse_owner` UUID added to the `DragonMaceWorldData` codec, so the curse survives restarts,
  reconnects, dimension changes and name changes.
- Exactly one holder, enforced by `DragonMaceCurse.decideActivation` under a lock. The owner is
  persisted before any effect is applied.
- Activation consumes one Very Heavy Core only after the curse was actually written.
- Death transfers the curse to the credited killer, reusing `DragonMaceDeathTransfer.resolveKiller`
  so the mace transfer and the curse transfer always agree. A death without a credited player killer
  keeps the curse on the victim.
- Announcements: `[Dragon Mace] <player> has consumed a Very Heavy Core!` and
  `[Dragon Mace] The Curse of the Dragons has transferred from <victim> to <killer>!`

### Dragons Aura

- Infinite `minecraft:glowing` effect, applied with `ambient=false, visible=false, showIcon=false`.
- Re-applied on join (`PlayerList.placeNewPlayer`) and respawn (`ServerPlayer.restoreFrom`), because
  vanilla clears effects on death.
- `clearStale` only removes the exact infinite amplifier-0 instance, so a beacon or spectral-arrow
  glow is left alone.

### Tablist tag

- `[Curse of the Dragons]` in purple with gold brackets, followed by the player's name.
- Implemented by overriding `ServerPlayer.getTabListDisplayName()` in `ServerPlayerTabListMixin`. In
  1.21.11 that method is a plain `return null` stub and
  `ClientboundPlayerInfoUpdatePacket.Entry` reads its display name from exactly that method, so this
  is the single point covering both the initial tab list population and every later
  `UPDATE_DISPLAY_NAME` broadcast.
- The game profile, username, chat name and scoreboard identity are never modified.

### Dragons Pride — totem limit

- Maximum two Totems of Undying across main inventory, hotbar and offhand.
- Two enforcement layers: `InventoryMixin` refuses an `Inventory.add` that would exceed the limit
  (pickups and slot-less inserts), and `ContainerTransactionMixin` enforces at the tail of
  `doClick` (shift-click, number-key swaps, drags). Excess totems are dropped, never deleted.
- The two permitted totems keep working normally; nothing touches the totem's own behaviour.

### Dragon Egg protections

- `DragonMaceProtection.isProtectedStack` now covers the Dragon Egg as well as the Dragon Mace, which
  automatically applies the existing slot, shift-click, drag, bundle, hopper, dispenser and item
  frame guards.
- `MobPickupMixin` stops baby piglins from picking the egg up through `Mob.pickUpItem`, which is not
  a container and bypassed every other guard.
- The egg is identified by its vanilla item, never by a display name, so a renamed egg is still
  protected.

### Commands

`/dm` with `/dragonmace` as an alias. Exactly five subcommands.

| Command | Permission | Notes |
| --- | --- | --- |
| `/dm effect <player>` | gamemasters | grants the curse, respecting the single-owner rule |
| `/dm remove <player>` | gamemasters | clears the curse; works on offline players via `GameProfileArgument` |
| `/dm giveitem veryheavycore` | gamemasters | gives the paper core; never activates the curse |
| `/dm withdraw` | everyone | active holder trades the curse for one Very Heavy Core |
| `/dm status` | everyone | shows the current holder |

`/dm spawn`, `/dm delete`, `/dm transfer` and `/dm recover` were removed completely: registrations,
handlers, argument definitions and tab completions are gone, and no aliases were left behind. The
older `/spawndragonmace`, `/deletedragonmace`, `/transfermace` and `/recoverdragonmace` literals remain
absent.

## Known limitations and open items

1. **Purple glow outline is not implemented.** The outline colour of a glowing entity is chosen by
   the client from the entity's scoreboard team colour, not from the effect. A server-side-only mod
   cannot tint it purple without rewriting the player's scoreboard identity, which the design
   explicitly forbids. The aura is a real infinite glowing effect; only the colour is vanilla yellow.
2. **`Draconic Rune III` is a data marker, not an enchantment.** No vanilla enchantment exists and
   adding one needs a registry entry, which would break the server-side-only design. See above.
3. **Core lore contradicts the totem rule.** The lore says `Totems stop working on you`, while the
   requirement is that the holder keeps two working totems. The lore was reproduced verbatim as
   instructed; the behaviour follows the requirement. One of the two texts should be changed.
4. **In-game behaviour is not yet exercised.** The server boots cleanly and `ItemUseMixin` applies,
   but no player has logged in, so the curse activation, transfer, totem limit, tablist tag, container
   guards, recipe-book unlock, `/dm giveitem` and the right-click consumption have not been observed in
   a live session. The manual testing checklist below covers them.
5. **`/dm effect` requires the target to be online**, because the effects have to be written to a live
   player. `/dm remove` works offline.
6. **JEI is not installed in this project**, so JEI display could not be verified directly. JEI reads
   the recipe map that the server syncs, and the Very Heavy Core is a normal `ShapedRecipe` in that
   map, so it is expected to display; this remains unverified.
7. **Recipe-book unlock is verified by code path, not by a live player.** `awardRecipesByKey` is the
   vanilla unlock path and the recipe key resolves to the registered recipe, but no client has opened
   the recipe book to confirm the entry appears.
8. **Heavy Core appearance is verified by component, not by a rendered client.** The stack carries
   `minecraft:item_model = minecraft:block/heavy_core`, which the client resolves to the vanilla model,
   but no client has rendered the item.

## Manual testing checklist

Run on a dedicated server with two operator accounts and one plain account.

### Very Heavy Core

- [ ] Craft the exact 3x3 layout: Conduit / Dragon Egg / Nether Star, Creeper Head / Heavy Core /
      Piglin Head, Wayfinder Template / Trident / Silence Template. The result is `Very Heavy Core`
      in gold with the full 19-line lore.
- [ ] Confirm all nine ingredients are consumed, including the Dragon Egg.
- [ ] Confirm the recipe does not craft with any ingredient swapped or a slot left empty.
- [ ] Confirm the result is `minecraft:paper` carrying `dragon_mace:very_heavy_core` and renders with
      the vanilla Heavy Core model.
- [ ] Confirm the item cannot be placed as a block and does not trigger block interactions.
- [ ] Confirm ordinary `minecraft:paper` right-click does nothing and never activates the curse.
- [ ] Obtain a Dragon Egg for the first time; the Very Heavy Core recipe appears in the crafting
      recipe book and stays unlocked after reconnecting and restarting.
- [ ] With JEI installed, confirm the Very Heavy Core recipe is listed and shows the Dragon Egg.
- [ ] `/dm giveitem veryheavycore` gives exactly one paper core with the identifier, name and lore,
      and does not activate the curse or change the curse owner.
- [ ] Every online player sees `[Dragon Mace] A Very Heavy Core has been crafted!` exactly once.
- [ ] Shift-click the result out of the crafting grid; the announcement still fires exactly once.
- [ ] Restart the server and confirm the recipe still works.

### Consumption and single-owner rule

- [ ] Right-click the core in the air; it is consumed and the curse is granted.
- [ ] Right-click the core against a block; it is consumed and no heavy core block is placed.
- [ ] With the curse active, a second player right-clicking a core is refused and keeps the core.
- [ ] `/dm effect <second player>` is refused while the curse is held.
- [ ] Double right-click does not consume two cores.

### Draconic Mace

- [ ] Every mace in the holder's inventory becomes `Dragon Mace`, dark purple, `EPIC`, with Density V
      and Wind Burst III.
- [ ] Drop a converted mace, pick it up, reconnect, restart; it stays converted.
- [ ] A mace that already had enchantments keeps them and gains no placeholders.
- [ ] The vanilla Smash ability still works.

### Aura and tablist

- [ ] The holder glows permanently with no effect icon in the HUD.
- [ ] The holder shows `[Curse of the Dragons]` in the tab list for every online player.
- [ ] Reconnect and restart; the tag and the glow return.
- [ ] A player without the curse never shows the tag.

### Totem limit

- [ ] Pick up a third Totem of Undying; it is refused or dropped, never deleted.
- [ ] Shift-click a third totem into the inventory; the excess is dropped.
- [ ] The two permitted totems still save the holder from death.

### Dragon Egg

- [ ] The egg cannot be placed into a chest, barrel, shulker box, hopper, dispenser, dropper, bundle,
      shelf or item frame.
- [ ] A baby piglin standing on the egg never picks it up.
- [ ] A player can still pick the egg up and drop it, and can still place it as a block.

### Death and transfer

- [ ] Die to a mob or to the void; the curse stays on the holder and returns after respawn.
- [ ] Die to another player's melee; the curse moves to the killer with the announcement.
- [ ] Die to another player's arrow; the curse moves to the shooter.
- [ ] The killer's maces are converted and their totem count is capped at two.
- [ ] The victim loses the tag and the glow immediately.

### Withdraw and remove

- [ ] `/dm withdraw` as the holder returns exactly one core and clears every effect.
- [ ] Withdraw with a full inventory; the core is dropped, not lost.
- [ ] The withdrawn core is consumable by another player.
- [ ] `/dm remove <player>` clears the curse and frees the slot.
- [ ] `/dm remove <offline player>` clears the saved owner.
- [ ] `/dm status` reports the holder, or that the curse is inactive.

### Commands

- [ ] `/dm` and `/dragonmace` both work and tab-complete their subcommands.
- [ ] `/dm` offers exactly `effect`, `remove`, `giveitem`, `withdraw` and `status`.
- [ ] `/dm spawn`, `/dm delete`, `/dm transfer` and `/dm recover` are not recognised.
- [ ] A non-operator cannot run `/dm effect`, `/dm remove` or `/dm giveitem`.
- [ ] A non-operator holder can run `/dm withdraw` and `/dm status`.

## Test coverage added by this update

- `DragonMaceCurseTest` — activation decisions, curse owner codec round-trip, malformed UUID handling.
- `DragonMaceTotemLimitTest` — limit constant, totem recognition, allow/refuse decisions, excess.
- `DragonMaceItemsTest` — core and mace identity, exact lore lines, conversion and revert, damage and
  marker preservation, egg protection, paper material, custom identifier, Heavy Core model, and the
  ordinary-paper and renamed-paper negatives.
- `DragonMaceCurseAnnouncementTest` — exact announcement strings and the tablist tag text.
- `DragonMaceRecipesTest` — extended for the Very Heavy Core recipe, idempotency, the exact nine
  ingredient positions, no Netherite Ingot in any slot, matching only the specified layout, and
  assembling the dedicated item.
- `DragonMaceRecipeUnlockTest` — the unlock key resolves to the registered recipe and the null-player
  guards hold.
- `DragonMaceGiveItemCommandTest` — the five required commands exist, the four obsolete ones and their
  old aliases are absent, the tree has exactly five children, gamemaster requirements hold, and the
  given stack is paper with the identifier, name and Heavy Core model.
