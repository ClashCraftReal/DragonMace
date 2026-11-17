# Dragon Mace — Project Status

Fabric mod for Minecraft 1.21.11. Server-side only: no client entrypoint, no registry entries, no
custom item classes. Every special item is a vanilla item carrying a persistent
`minecraft:custom_data` marker, so the mod works on a vanilla client.

Last updated: 2026-11-16.

## Build and test

| Command | Result |
| --- | --- |
| `./gradlew clean build` | BUILD SUCCESSFUL |
| `./gradlew test` | 554 tests, 0 failures, 0 errors, 0 skipped |
| `./gradlew runServer` | `Done (2.649s)!`, no mixin apply failure, no mod ERROR lines |

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

## Bug fixes — 2026-11-16

Three reported defects, each fixed at its root cause and locked down by
`DragonMaceBugRegressionTest`.

### 1. Very Heavy Core rendered as plain paper

- **Cause:** `minecraft:item_model` takes an **item definition** id, not a model id. The component
  carried `minecraft:block/heavy_core`, so the client looked for
  `assets/minecraft/items/block/heavy_core.json`, found nothing and fell back to the paper model.
- **Fix:** `DragonMaceItems.VERY_HEAVY_CORE_MODEL` is now `minecraft:heavy_core`. The vanilla
  definition `assets/minecraft/items/heavy_core.json` exists in the 1.21.11 jar and points at
  `minecraft:block/heavy_core`, so the item renders as a Heavy Core with no client asset and no
  registry entry.
- **Files:** `DragonMaceItems.java`.
- **Automated:** component value, "not a `block/` path", the referenced definition resolving to
  `minecraft:block/heavy_core`, and the component on a freshly built stack.
- **In-game only:** the rendered item in a client inventory.

### 2. Dragons Wrath did not convert maces obtained after the curse

- **Cause:** conversion ran only from `DragonMaceCurse.apply`, which is reached on activation,
  transfer, join and respawn. A mace that entered the holder's inventory afterwards (pickup, crafting
  result, shift-click, anvil) was never converted, because nothing observed the inventory write.
  `Inventory.add` also writes straight into the backing list instead of going through `setItem`, so a
  single hook could not cover both paths.
- **Fix:** `DragonMaceMaceConversion.convertSlot` converts one slot in place and
  `convertContainer` now loops over it. `InventoryMixin` gained two hooks: `setItem` at `RETURN`
  converts the written slot, and `add` at `RETURN` sweeps the inventory (the slot is not reported by
  `add`). Both run only for a `ServerPlayer` who actually holds the curse, and both are idempotent
  because a converted stack is no longer eligible, so no recursion and no double conversion.
- **Files:** `DragonMaceMaceConversion.java`, `mixin/InventoryMixin.java`.
- **Automated:** every eligible mace converted, non-maces untouched, count unchanged (no duplicate or
  deletion), damage preserved, unique Dragon Mace marker preserved, already-converted maces skipped,
  idempotency, null/out-of-range guards, single-slot conversion touching only its slot, and the two
  `Inventory` hook targets existing.
- **In-game only:** a mace picked up or crafted while holding the curse turning into a Draconic Mace
  in a live session.

### 3. Dragon Egg could not be placed in the crafting grid

- **Cause:** `DragonMaceProtection.isAllowedContainer` allowed only `Inventory`. Crafting slots are
  backed by `TransientCraftingContainer`, which implements `CraftingContainer` but not `Inventory`, so
  `blocksStorage` refused the egg in the grid and the Very Heavy Core recipe could never be filled.
- **Fix:** `CraftingContainer` is now allowed alongside `Inventory`. Chests, barrels, shulkers,
  hoppers, dispensers, droppers and ender chests are still refused, and the bundle, item-frame,
  falling-block and quick-move guards are unchanged.
- **Files:** `DragonMaceProtection.java`.
- **Automated:** crafting grid allowed, egg not blocked in a grid, egg still blocked in a 27-slot
  storage container, Dragon Mace also allowed in a grid, ordinary items unaffected, plus the existing
  exact nine-ingredient recipe tests.
- **In-game only:** placing the egg in the 3x3 grid and crafting the core in a live session.

## Delayed mace conversion and star-free name — 2026-11-17

- **Delayed conversion.** `DragonMaceCurse.apply` called `convertInventory` immediately, and `apply` runs
  from `transfer` at the head of `ServerPlayer.die`. The killer's mace therefore became Draconic before
  vanilla built the death message, so an ordinary-mace kill reported "Draconic Mace".
  `DragonMaceMaceConversion` now queues the conversion instead: `scheduleConversion` keys a
  `ConcurrentHashMap` by UUID with `putIfAbsent` (so repeated inventory writes cannot queue twice),
  `takeDue` releases each entry exactly once, and `tick` runs the due ones from
  `ServerTickEvents.END_SERVER_TICK`. A player who disconnected or lost the curse during the delay is
  skipped. `CONVERSION_DELAY_TICKS` is 1, which is enough to leave the death call and imperceptible in
  play. Reversion stays immediate, and `convertContainer`/`convertInventory` stay immediate for direct
  callers and tests.
- **Star-free name.** `draconicMaceName` dropped the gold `✪✪✪✪✪` run and the `DRACONIC_STARS`
  constant, leaving the light purple name, a space and the red tier glyph: `Draconic Mace ➎`. The tier
  glyph is not a star, so it is preserved.
- **Files:** `DragonMaceMaceConversion.java`, `DragonMaceCurse.java`, `DragonMace.java`,
  `DragonMaceItems.java`, `DragonMaceConversionTest.java`, `DragonMaceItemsTest.java`,
  `DragonMaceDeathMessageTest.java`, `PROJECT_STATUS.md`.
- **Automated:** 554 tests, 0 failures. New: `conversionIsDelayedByOneTick`,
  `schedulingTheSamePlayerTwiceQueuesOneConversion`, `aDueConversionIsReleasedExactlyOnce`,
  `schedulingWithoutAPlayerIsRefused`, `theCurseApplyPathSchedulesInsteadOfConvertingImmediately`,
  `theServerTickDrivesPendingConversions`, `theDraconicNameCarriesNoStars`.
- **Not verified:** no client was connected. No live player-versus-player kill was performed, so the
  death message was not observed in game.

## Kill rune effects wired into the kill pipeline — 2026-11-17

- **Root cause.** `DragonMaceRuneEffects.onKill` had **zero callers**. `DragonMaceProgression.registerKill`
  recorded the kill and returned the unlocked rune, but never invoked the effect, so all five Kill runes
  were dead code and no kill ever produced a particle or sound. `grep -rn "onKill" src/main/java/`
  returned only the declaration.
- **Fix.** `registerKill` now calls `DragonMaceRuneEffects.onKill(killer, victim,
  runes(server, killer.getUUID()).kill())` after `recordKill`, so the effect runs after the count is
  persisted and uses the killer's saved Kill rune rather than a default. `onKill` already re-checks the
  KILL toggle and requires a `ServerLevel`, so a disabled category still counts the kill but plays
  nothing, and the effect stays server-side.
- **Attribution unchanged.** `resolveKiller` still supplies the credited player, self-kills and
  uncredited deaths still return early, and `beginDeath`/`endDeath` still claim the victim so one death
  cannot double-count.
- **Files:** `DragonMaceProgression.java`, `DragonMaceRuneEffectsTest.java`, `PROJECT_STATUS.md`.
- **Automated:** 547 tests, 0 failures. `DragonMaceRuneEffectsTest` 19 tests, including
  `theKillPipelineInvokesTheKillEffect` and `theKillEffectUsesTheKillersSelectedKillRune`. Both were
  proven to fail with the call removed and pass with it restored.
- **Not verified:** no client was connected. No live player-versus-player kill was performed, so the
  particles and sound were never observed in game.

## Rune row layout, apply slots and per-category toggles — 2026-11-17

- **Rune row layout.** Rows 3–5 are now Kill (27), Aura (36) and Attack (45), each shaped
  `empty | info | five runes | toggle | apply`: info at 28/37/46, runes at 29–33/38–42/47–51, toggle at
  34/43/52 and apply at 35/44/53. The info icon is the second slot of the row, so it sits one index
  before the first rune; the explicit slot numbers in the brief are authoritative and are what the tests
  assert. Rows 0–2 are untouched.
- **Apply slots.** The rightmost slot of each rune row applies that row's rune through
  `DragonMaceEnchantmentMenu.applyRunes`, which stores only that one category and re-lores the holder's
  maces immediately. It reads no configuration and stamps no timestamp, so the 120-second cooldown can
  never block it; `canApplyRunes` gates on the unlock only.
- **Toggles.** `DragonMaceRuneToggles` is a three-boolean record stored in
  `DragonMaceProgressionData.Entry` under `toggles`, defaulting to all-on so an older save loads with
  everything enabled. `DragonMaceProgression.toggleCategory` flips one category, saves, and refreshes the
  holder's maces. The slot shows lime dye when enabled and gray dye when disabled, with the state in the
  name and tooltip. Turning a category off never touches its selected rune.
- **Effect gating.** `onKill`, `onAttack` and `tickAura` each return early when their own category is
  disabled, so one switch cannot affect the other two. The mace lore appends ` (Disabled)` to a disabled
  category's rune line.
- **Soulflame.** No Fire Aspect anywhere: `grep -rn "FIRE_ASPECT"` over `src/` returns nothing, and
  `DragonMaceEnchantments.configured` never emits it. Soulflame keeps its ignition
  (`ignitesTarget` → 80 fire ticks) and applies no potion.
- **Files:** `DragonMaceRuneToggles.java` (new), `DragonMaceEnchantmentLayout.java`,
  `DragonMaceEnchantmentMenu.java`, `DragonMaceRuneEffects.java`, `DragonMaceProgression.java`,
  `DragonMaceLoreContext.java`, `DragonMaceItems.java`, `world/DragonMaceProgressionData.java`,
  `DragonMaceRuneGuiTest.java`, `DragonMaceRuneEffectsTest.java`, `PROJECT_STATUS.md`.
- **Automated:** 543 tests, 0 failures. `DragonMaceRuneGuiTest` 35 tests, `DragonMaceRuneEffectsTest` 15.
- **Not verified:** no client was connected. The rendered 54-slot grid, a live rune apply, a live toggle
  click, and the disabled-effect gating in a running world were not exercised in game.

## Rune system, kill progression and visual effects — 2026-11-17

**Kill progression.** `DragonMaceProgressionData` is a `SavedData` in the overworld's
`DimensionDataStorage`, written to `data/dragon_mace_progression.dat` and keyed by player UUID. It holds
one record per player: a kill count and a three-field rune selection. Kill counts only ever increase, so
an unlock is permanent. A record is created only when a player earns a kill or saves a selection.

**Kill counting.** `ServerPlayerDeathMixin` registers the kill last, after the mace and curse transfers,
so the killer's mace has already been converted and the kill that grants the curse also counts. The
victim's UUID is claimed by `beginDeath` and released at `RETURN`, so a repeated death event in the same
tick cannot double-count. A self-kill, an uncredited death and a kill made with anything other than a
Draconic Mace are all ignored.

**Runes.** Five runes — Draconic (light purple, 0 kills), Soulflame (blue, 1), Tempest (yellow, 2),
Abyssal (dark purple, 3), Warden (aqua, 4) — in three independent categories: Kill, Attack and Aura. The
same name and colour are used in the GUI, the lore and the unlock announcement. A rune that is not
unlocked at the player's kill count is refused server-side on every click, and a stored selection is
sanitised against the stored kill count on read, so a hand-edited save cannot grant an unearned rune.

**Effects.** All server-side, all vanilla particles, nothing destructive.
`DragonMaceRuneEffects.onKill` bursts the Kill rune's particle on the victim. `onAttack` fires from
Fabric's `AttackEntityCallback` on the main hand only, re-checks the curse and the main hand, and applies
the Attack rune: Soulflame ignites, Tempest knocks back, Abyssal applies Darkness, Warden applies
Slowness. `tickAura` runs every ten ticks for the single curse holder and trails the Aura rune's particle
around them, recomputed from live state so the aura stops the moment the curse or the last Draconic Mace
is gone.

**GUI.** The menu is now 54 slots — the existing 27-slot enchantment section is unchanged in rows 0–2,
and rows 3–5 add one rune row per category at slots 27, 36 and 45. The client screen draws the vanilla
`generic_54.png` at six rows. Confirm saves the enchantment configuration and the rune selection
together, reconciles every online player's maces and starts the same 120-second global cooldown; Cancel
discards both. Every slot stays display-only.

**Lore.** The Draconic Mace lore is rebuilt from a `DragonMaceLoreContext` — live enchantment component,
rune selection and kill count — so the enchantment summary, the three rune lines and the kill counter can
never go stale. `refreshDraconicMace` rewrites them in place after a kill, after a confirmation and
whenever the curse is applied.

**Files:** `DragonMaceRune.java`, `DragonMaceRuneCategory.java`, `DragonMaceRuneSelection.java`,
`DragonMaceLoreContext.java`, `DragonMaceProgression.java`, `DragonMaceRuneEffects.java`,
`world/DragonMaceProgressionData.java` (all new); `DragonMaceItems.java`, `DragonMaceMaceConversion.java`,
`DragonMaceEnchantmentLayout.java`, `DragonMaceEnchantmentMenu.java`,
`client/DragonMaceEnchantmentScreen.java`, `DragonMaceAnnouncements.java`,
`mixin/ServerPlayerDeathMixin.java`, `DragonMace.java`, `PROJECT_STATUS.md`; tests
`DragonMaceRuneTest.java`, `DragonMaceProgressionTest.java`, `DragonMaceRuneGuiTest.java`,
`DragonMaceRuneEffectsTest.java` (new) plus three existing files updated to the new lore and layout.

**Automated:** 523 tests, 0 failures. New: 64 tests across the four new classes.

**Not verified:** no client was connected. The rendered 54-slot GUI, a live kill incrementing the
counter, an unlock announcement, the aura particles, an Attack rune firing on a real hit, and the
`data/dragon_mace_progression.dat` file being written and reloaded were not exercised in game.

## Shift + F gated at the earliest point — 2026-11-17

- **Root cause.** The mace check was the *last* term of both decisions. `isMenuShortcutDown` evaluated
  `keyShift.isDown() && openMenu.isDown()` before `holdsMace(client)`, and `shouldOpen` evaluated
  `!screenOpen && shiftDown` before `holdingMace`. The tick handler also called `shouldOpen` with the
  mace result computed inline, so the screen and Shift state were read for every held item. The
  behaviour was correct but the custom logic still ran for non-maces.
- **Fix.** The mace check is now the first statement in all three places. `isMenuShortcutDown` returns
  `false` before touching any key state, so `MinecraftKeybindMixin` never suppresses the vanilla
  offhand swap for a non-mace. `shouldOpen` short-circuits on `!holdingMace` before the screen and
  Shift checks. The tick handler computes `holdingMace` and `continue`s before calling `shouldOpen` at
  all, so no screen or key state is consulted and no command is sent.
- **Files:** `client/DragonMaceClient.java`, `DragonMaceClientShortcutTest.java`, `PROJECT_STATUS.md`.
- **Automated:** 459 tests, 0 failures. `DragonMaceClientShortcutTest` 11 tests, including a full
  truth table proving every non-mace combination bypasses the shortcut and that a mace opens the menu
  only with Shift held and no screen open.
- **Not verified:** no client was connected. The keybinding, the offhand swap and the redirect were not
  exercised in game.

## Shift + F restricted to maces — 2026-11-17

- **The gate.** `DragonMaceClient.shouldOpen` now also requires a mace in the main hand, and
  `isMenuShortcutDown` — the method `MinecraftKeybindMixin` reads to decide whether to suppress the
  vanilla offhand swap — requires it too. With anything else held, `isMenuShortcutDown` returns
  `false`, so the redirect leaves `keySwapOffhand` alone and Shift + F swaps the offhand exactly as
  vanilla does. Nothing else about the mixin changed.
- **Identity.** The check is `DragonMaceEnchantments.isMace`, which matches `minecraft:mace` and
  therefore covers both a vanilla mace and a Draconic mace, since the Draconic form is a mace stack
  with components. It rejects paper, swords, the Very Heavy Core and an empty hand.
- **Testability.** `shouldOpen` took a `Screen`; constructing a client `Screen` in a headless test JVM
  throws NPE from its constructor, so the parameter is now a plain `screenOpen` boolean and the caller
  passes `client.screen != null`. The decision no longer depends on a client class at all.
- **Files:** `client/DragonMaceClient.java`, `DragonMaceClientShortcutTest.java` (new),
  `TestBootstrap.java` (made public so the new test package can bootstrap), `PROJECT_STATUS.md`.
- **Automated:** 457 tests, 0 failures. `DragonMaceClientShortcutTest` 9 tests.
- **Not verified:** no client was connected. The keybinding, the offhand swap and the redirect were not
  exercised in game.

## Custom anvil system removed and totem gate fixed — 2026-11-17

- **Totem bug root cause.** `ContainerTransactionMixin.dragonmace$rejectTotemInsert` consulted
  `DragonMaceTotemLimit.blocksInsert` with no curse check at all. The `ServerPlayer` pattern variable
  was bound and then never used, so every player — including one who had withdrawn or transferred the
  curse away — had a totem click refused as soon as they carried two. `InventoryMixin`'s `add` guard
  and the shift-click guard both checked `DragonMaceCurse.isActive`; this third hook did not. The
  decision now goes through `blocksInsert(owner, player, carried, incoming)`, which returns `false`
  unless the player is the current owner, so the gate and the counting rule live in one place.
- **Custom anvil handling removed.** `AnvilMenuMixin` is deleted, `ANVIL_INPUTS`, `ANVIL_RESULTS`,
  `registerAnvilInput`, `isAnvilInput`, `registerAnvilResult` and `isAnvilResult` are gone from
  `DragonMaceProtection`, `isAllowedContainer` is back to `Inventory`/`CraftingContainer`/
  `ResultContainer`, and the anvil exceptions are removed from `SlotSetMixin` and `SlotMixin`. The
  mixin list is 28 entries and no anvil class ships in the jar. Vanilla anvil behaviour is untouched.
- **Consequence, stated plainly.** With the bypass gone, a protected stack placed into an anvil input
  is refused again by `blocksStorage`, because an anvil input is a plain `SimpleContainer`. Ordinary
  maces are not protected stacks, so they work normally. A Draconic Mace is a protected stack, so it
  is refused at the input slot. That is the direct result of removing the custom handling as asked,
  not a separate defect.
- **Files:** `mixin/AnvilMenuMixin.java` (deleted), `mixin/SlotMixin.java`, `mixin/SlotSetMixin.java`,
  `mixin/ContainerTransactionMixin.java`, `DragonMaceProtection.java`, `DragonMaceTotemLimit.java`,
  `dragon-mace.mixins.json`, `DragonMaceAnvilTest.java` (deleted), `DragonMaceHotfixTest.java`,
  `DragonMaceEnchantmentGuiTest.java`, `DragonMaceTotemLimitTest.java`, `PROJECT_STATUS.md`.
- **Automated:** 448 tests, 0 failures. `DragonMaceTotemLimitTest` 26 tests.
- **Not verified:** no client was connected. Moving totems without the curse, and a live third-totem
  drop, were not exercised in game.

## Fire immunity and Dragons Pride lifecycle — 2026-11-17

- **Dropped items burned.** `groundProtection` only covered the mace and the egg, so a dropped Very
  Heavy Core was an ordinary paper item entity and burned. The core is now included, matched by its
  custom identifier, so ordinary paper keeps vanilla behaviour. `ItemEntity.fireImmune()` is the gate
  `Entity.lavaHurt` consults before applying lava damage and setting the entity alight, so
  `ItemEntityMixin` now returns `true` there for a protected stack; invulnerability alone blocked the
  damage through `hurtServer` but still let the entity catch fire and play the lava hurt sound. Void
  destruction is a position check in `Entity.checkBelowWorld`, not damage, so it still applies.
- **Dragons Pride outlived the curse.** `InventoryMixin.dragonmace$reconcile` called
  `DragonMaceTotemLimit.enforce` on every `setItem` and successful `add` with no curse check, so the
  limit kept dropping totems after a withdrawal, a removal or a transfer away. `enforce` now returns
  early unless the player is the current owner, decided by the pure `appliesTo(owner, player)`. Nothing
  is dropped when the restriction lifts, and re-activating the curse enforces the same priority again.
- **Files:** `DragonMaceProtection.java`, `mixin/ItemEntityMixin.java`, `DragonMaceTotemLimit.java`,
  `DragonMaceFireImmunityTest.java` (new), `DragonMaceTotemLimitTest.java`,
  `DragonMaceVeryHeavyCoreProtectionTest.java`, `PROJECT_STATUS.md`.
- **Automated:** 454 tests, 0 failures. `DragonMaceFireImmunityTest` 7 tests,
  `DragonMaceTotemLimitTest` 20 tests.
- **Not verified:** no client was connected. A dropped egg or core in lava, and a live totem drop at
  the limit, were not exercised in game.

## Anvil result extraction and Dragons Pride drop priority — 2026-11-17

- **Anvil result could not be taken.** `SlotMixin` blocks `Slot.mayPickup` for any `ResultSlot` holding
  a marked Dragon Mace while the world already has one. The anvil's result slot is also a `ResultSlot`
  backed by a `ResultContainer`, so a renamed mace parked there could never be picked up — the
  duplication guard was firing on the anvil. `DragonMaceProtection` now keeps a weak registry of anvil
  result containers, `AnvilMenuMixin` registers slot 2, and `SlotMixin` skips the guard for a registered
  anvil result. The crafting guard is unchanged, so the unique-mace duplication protection still holds.
- **Dragons Pride drop priority.** `DragonMaceTotemLimit.enforce` restores the drop that the previous
  round removed, now with the required priority: the offhand totem is never touched, one further totem
  is kept from the lowest inventory slot that holds one, and every remaining totem is dropped with
  `Player.drop`. `planDrops` is the pure planner behind it. A `ThreadLocal` re-entry guard stops the
  slot writes from recursing through the inventory hooks that call `enforce`.
- **Enforcement points.** `DragonMaceCurse.apply` enforces when the curse lands, and
  `InventoryMixin.dragonmace$reconcile` enforces after every `setItem` and successful `add`, which
  covers pickups, shift-click, drag, number-key swaps and container transfers.
- **Files:** `DragonMaceTotemLimit.java`, `DragonMaceProtection.java`, `mixin/SlotMixin.java`,
  `mixin/AnvilMenuMixin.java`, `mixin/InventoryMixin.java`, `DragonMaceCurse.java`,
  `DragonMaceTotemLimitTest.java` (new), `DragonMaceAnvilTest.java`, `PROJECT_STATUS.md`.
- **Automated:** 443 tests, 0 failures. `DragonMaceTotemLimitTest` 16 tests, `DragonMaceAnvilTest`
  11 tests.
- **Not verified:** no client was connected. A real anvil take, and a live totem drop, were not
  exercised in game.

## Anvil, Dragons Pride and vanilla mace crafting hotfix — 2026-11-17

- **Anvil.** `SlotSetMixin` reverted any mace written into a container that was not a player inventory
  or a `ResultContainer`. The anvil's *input* slots are plain `SimpleContainer`s, so a Draconic Mace was
  stripped the instant it was placed in the anvil — that is why it could not be renamed. The inputs are
  now registered as anvil inputs and exempt from the revert. The `createResult` reconcile injection was
  removed entirely, so no anvil operation is blocked or rewritten; the ownership rule is still enforced
  by the inventory hooks when the result lands in a player's inventory.
- **Very Heavy Core protection.** Already covered by `isProtectedStack` matching
  `DragonMaceIdentity.isVeryHeavyCore`; unchanged this round.
- **Dragons Pride lore.** The line read "Totems stop working on you." and now reads
  "You may carry and use up to two Totems of Undying.", matching the real two-totem limit.
- **Dragons Pride enforcement.** `DragonMaceTotemLimit.enforce` dropped excess totems, which violated
  "never automatically drop". It is gone. Rejection now happens before anything moves:
  `doClick` HEAD refuses a `PICKUP`/`QUICK_CRAFT` that would exceed the limit, and `moveItemStackTo`
  HEAD refuses a shift-click into the player inventory. `quickMoveStack` is abstract on
  `AbstractContainerMenu` and cannot be injected — the first attempt failed at apply time with
  `Cannot read field "index" because "insnNode" is null`, so the guard moved to the concrete funnel.
  Number-key and offhand swaps move between two inventory slots and cannot raise the count, so they are
  left alone. `blocksQuickMove` skips the source stack by identity so internal moves are never refused.
- **Vanilla mace crafting.** `DragonMaceRecipes.apply` dropped `minecraft:mace` from the recipe map.
  It no longer does, so the vanilla recipe is restored and its ingredients are consumed by vanilla
  crafting. The unique Dragon Mace protection lives in item identity and ownership, not the recipe map.
  Recipe count at boot went from 1471 to 1472.
- **Files:** `mixin/AnvilMenuMixin.java`, `mixin/SlotSetMixin.java`, `mixin/ContainerTransactionMixin.java`,
  `mixin/RecipeManagerMixin.java`, `DragonMaceProtection.java`, `DragonMaceTotemLimit.java`,
  `DragonMaceCurse.java`, `DragonMaceRecipes.java`, `DragonMaceItems.java`,
  `DragonMaceHotfixTest.java` (new), `DragonMaceItemsTest.java`, `PROJECT_STATUS.md`.
- **Automated:** 431 tests, 0 failures. `DragonMaceHotfixTest` 17 tests.
- **Not verified:** no client was connected. A real anvil rename, a live totem-limit rejection, and
  crafting a vanilla mace in a crafting table were not exercised in game.

## Anvil acceptance, Very Heavy Core protection and warning removal — 2026-11-17

- **Anvil.** The input slots were already accepted and the constructor injection that registers them
  was verified in the exported transformed `AnvilMenu`: the handler runs at the tail of
  `(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V`
  and calls `registerAnvilInput` on both input containers. The remaining defect was client-side:
  `AnvilMenu.createResult` also runs on the client, where `level().getServer()` is null, so
  `isEligible` was always false and the client stripped enchantments the server kept. The client's
  predicted result therefore disagreed with the server's. `AnvilMenuMixin` now returns early when the
  server is null, leaving the client's result untouched; the server stays authoritative and syncs it.
- **Very Heavy Core protection.** `DragonMaceProtection.isProtectedStack` now also matches
  `DragonMaceIdentity.isVeryHeavyCore`, so the core inherits every existing guard: container writes,
  shelves, bundles, number-key and offhand swaps, shift-click quick-move, drag and baby-piglin pickup.
  No parallel system was added. `groundProtection` was narrowed to the mace and the egg explicitly, so
  adding the core to the storage rules does not silently make a dropped core invulnerable or immortal.
  Ordinary paper is unaffected because identity requires both the paper item and the custom identifier.
- **Warning removed.** `DragonMaceRespawn.reconcile` no longer logs on `PRESENT_NOT_FOUND`; the branch
  is an explicit no-op. The saved state is untouched, the unique-mace protection is unchanged, and
  genuine failures still log. The removed text was the only periodic warning; the tick path calls
  `reconcile` once at `RECONCILE_DELAY_TICKS`.
- **Files:** `mixin/AnvilMenuMixin.java`, `DragonMaceProtection.java`, `DragonMaceRespawn.java`,
  `DragonMaceAnvilTest.java` (new), `DragonMaceVeryHeavyCoreProtectionTest.java` (new),
  `PROJECT_STATUS.md`.
- **Automated:** 414 tests, 0 failures. `DragonMaceAnvilTest` 7 tests, `DragonMaceVeryHeavyCoreProtectionTest`
  10 tests.
- **Not verified:** no client was connected. A real anvil rename, the core being refused by a chest,
  shelf or bundle in a live session, and baby-piglin pickup were not exercised in game.

## Client fixes and updated Draconic Mace design — 2026-11-17

Four client-side defects were reported after the previous round. Each was reproduced against the
1.21.11 bytecode before being changed.

- **White GUI background.** The blit call passed an explicit tint colour through the eleven-argument
  overload. It now uses the same ten-argument overload vanilla `ContainerScreen.renderBg` uses, with no
  tint, so the chest panel renders exactly as a real chest. The texture itself was verified present and
  valid in the 1.21.11 jar: `assets/minecraft/textures/gui/container/generic_54.png`, 256×256, 4-bit
  palette, 33024 bytes of raw scanlines.
- **Missing tooltips.** `AbstractContainerScreen.renderContents` never calls `renderTooltip` — the only
  occurrence of that name in the class is its own declaration. The screen now calls
  `renderTooltip(graphics, mouseX, mouseY)` after `super.render`, so hovering a display stack shows the
  server-defined name and lore.
- **Anvil still refused maces.** The earlier fix exempted `ResultContainer`, which was the *output*
  slot. The *input* slots are backed by a plain `SimpleContainer` created by
  `ItemCombinerMenu.createContainer`, indistinguishable from a chest by type, so `blocksStorage`
  refused every mace and egg placed into an anvil. `AnvilMenuMixin` now registers both input containers
  in a weak set that `isAllowedContainer` accepts. The first attempt injected into `<init>` without a
  descriptor and Mixin bound it to the two-argument constructor, failing at apply time; the descriptor
  is now pinned to `(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V`.
- **Shift + F used the offhand.** The shortcut is bound to `F`, the same key vanilla binds to
  `keySwapOffhand`, and it only *checked* Shift. Both mappings fired on one press, so the offhand was
  swapped before the menu request was sent. `MinecraftKeybindMixin` redirects
  `KeyMapping.consumeClick` inside `handleKeybinds` and drains the swap binding without acting while
  the shortcut is held. Plain `F` is untouched.

**Updated Draconic Mace design.** The lore is now the 19-line design from the latest `/give` command:
gear score, enchantment summary, the Enchant Chooser ability block with its three description lines and
the 120s cooldown, the Smash ability block with its two description lines, the Draconic Rune line, the
converted-by line, Soulbound and the Mythic footer. Smash remains vanilla mace behaviour; no custom
smash was added.

**Files:** `client/DragonMaceEnchantmentScreen.java`, `client/DragonMaceClient.java`,
`mixin/client/MinecraftKeybindMixin.java` (new), `mixin/AnvilMenuMixin.java`,
`DragonMaceProtection.java`, `DragonMaceItems.java`, `dragon-mace.mixins.json` (new `client` array),
`DragonMaceConversionTest.java`, `DragonMaceEnchantmentGuiTest.java`, `DragonMaceItemsTest.java`,
`PROJECT_STATUS.md`.

**Automated:** 397 tests, 0 failures. New: a registered anvil input accepts protected stacks while an
unregistered plain container still refuses them, and three lore-section tests covering the Enchant
Chooser block, the Smash block and the converted-by line.

**Not verified:** no client was connected. The rendered chest background, the tooltip pass, a real
anvil interaction, the Shift + F keybinding and the client mixin's redirect are all unconfirmed
in-game. The server boots with every server mixin applied, and the client mixin class ships in the jar,
but `MinecraftKeybindMixin` has never been applied to a running client.

## Anvil usability, chest GUI and Confirm flow — 2026-11-16

**Anvil.** The anvil was broken by `SlotSetMixin`, not by `AnvilMenuMixin`. That hook reverts a
Draconic Mace written into any container that is not a player `Inventory`, and an anvil result slot is
backed by `ResultContainer` — so the moment the anvil produced a Draconic Mace result, the write
reverted it and the player received a stripped mace. `ResultContainer` is now exempt from the revert
and from `blocksStorage`: a result slot is staging, not storage, and the stack only sits there until it
is taken. `AnvilMenuMixin` still only reconciles the finished result, so renaming, repairing and
merging all work and nothing is blocked.

**GUI.** The screen now blits the vanilla chest background
(`textures/gui/container/generic_54.png`) cropped to three rows — 176×78 — instead of drawing a custom
panel. No custom texture is shipped. The 27 slots already used the chest grid geometry
(8 + 18·column, 18 + 18·row), so the layout is unchanged.

**Confirm.** `apply` now validates through `decideApply` (selection first, then the saved cooldown),
saves, reconciles every online player, restarts the 120-second cooldown, messages the player with the
applied pair, and closes the menu. A refused press reports the remaining cooldown and leaves the saved
configuration untouched. Cancel still closes without writing.

**Files:** `mixin/SlotSetMixin.java`, `DragonMaceProtection.java`, `DragonMaceEnchantmentMenu.java`,
`client/DragonMaceEnchantmentScreen.java`, `DragonMaceEnchantmentGuiTest.java`, `PROJECT_STATUS.md`.

**Automated:** six new tests — Confirm applying when legal, Confirm refused during the cooldown, an
illegal selection rejected before the cooldown is consulted, a refused Confirm leaving the
configuration untouched, an anvil result slot not treated as storage, and a chest still treated as
storage. 393 tests total, 0 failures.

**In-game only:** the rendered chest background, a real anvil rename or merge, a real Confirm click,
and the Shift + F keybinding.

## Enchantment GUI — 27-slot layout and Shift + F — 2026-11-16

**Layout.** `DragonMaceEnchantmentLayout` is a static table of the 27 slots, so tests assert it without
a menu or a player. Slots 0–8 are empty; 9 Barrier `Wind Burst 0`, 10–12 Wind Charge ×1/×2/×3 for
Wind Burst I/II/III; 13–14 empty; 15 Breach IV and 16 Density V as enchanted books carrying their real
stored level; 17 empty; 18–20 empty; 21 Lime Dye `Confirm`, 22 Knowledge Book `Info`, 23 Red Dye
`Cancel`; 24–26 empty. Every selection item carries a description, and the selected one is marked
`Selected` in green with a glint override.

**Interaction.** `DragonMaceEnchantmentMenu.clicked` never delegates to the vanilla handler, so no
slot can be taken, placed, swapped, shift-clicked, dragged or number-key moved. Each `DisplaySlot`
also refuses `mayPickup`/`mayPlace` and ignores `setByPlayer`, so a client-driven write cannot replace
the layout. `quickMoveStack` returns empty. Clicks are routed through `buttonIdFor` into
`clickMenuButton`, which the screen and the tests share.

**Access.** `/dm changeenchants` replaces the old `/dm enchantments` literal and requires the active
curse holder; the requirement is no longer gamemaster-only, because the holder is the only player who
may change the configuration. `isAuthorized` is re-checked on every click and by `stillValid`, so
losing the curse closes the menu.

**Shift + F.** `DragonMaceClient` registers a `KeyMapping` on F in the Gameplay category through
Fabric API's `KeyBindingHelper`, so it appears in Controls. The tick handler only fires while Shift is
held and no screen is open, and drains `consumeClick`, so one press sends at most one
`/dm changeenchants`. The server re-checks ownership, so the keybinding grants nothing on its own.
Plain F still swaps the offhand.

**Dependency.** `fabric-api` is now a `modImplementation` and a `depends` entry, because
`KeyBindingHelper` and `ClientTickEvents` come from it. This is the one new dependency and it exists
only for the keybinding.

**Files:** `DragonMaceEnchantmentLayout.java` (new), `DragonMaceEnchantmentMenu.java`,
`client/DragonMaceEnchantmentScreen.java`, `client/DragonMaceClient.java`,
`command/DragonMaceCommands.java`, `assets/dragon-mace/lang/en_us.json` (new), `build.gradle`,
`gradle.properties`, `fabric.mod.json`, `DragonMaceEnchantmentGuiTest.java` (new),
`DragonMaceGiveItemCommandTest.java`, `PROJECT_STATUS.md`.

**Automated:** `DragonMaceEnchantmentGuiTest`, 24 tests — 27 slots, empty top row, exact item per slot,
stack counts 1/2/3, every display name, every description, stored book levels IV and V, selection
marker and glint, slot geometry, Wind Burst 0–III mapping, non-selection buttons leaving the selection
alone, Breach/Density mutual exclusion, selection not touching the saved configuration, Confirm saving
and starting the cooldown, Cancel leaving it untouched, Info being hover-only, the cooldown being
global and surviving a reload, the 120-second length, immediate reconciliation, future maces receiving
the configuration, unauthorized access, every slot refusing pickup and placement, client writes being
ignored, and every action slot mapping to its button.

**In-game only:** the rendered 27-slot screen, a real slot click, the Shift + F keybinding in a client,
and the Controls entry.

## Curse-based enchantment ownership and the enchantment GUI — 2026-11-16

**The rule.** Every mace keeps enchantments only while it sits in the inventory of the player who
currently owns the Curse of the Dragons. Anywhere else — another player's inventory, a chest, a shelf,
a bundle, a hopper, the ground — every mace is stripped to an unenchanted mace. Only
`minecraft:mace` stacks are touched, so swords, bows, armour, tools and unrelated items keep theirs.

- **Enforcement.** `DragonMaceEnchantments.reconcile` is the single decision, derived from where the
  stack is rather than from a stored flag, so it cannot drift. It is called from the inventory hooks
  (`InventoryMixin` via `DragonMaceMaceConversion.reconcile`), from the container and slot hooks that
  already route through `revertLeaving`, from the curse transfer and removal paths, and from the anvil
  result. Every method is idempotent.
- **Curse transfer.** `DragonMaceCurse.transfer` saves the new owner, then `clear(victim)` reverts and
  strips the previous holder, then `apply(killer)` converts and applies the configuration to the new
  holder. Both happen inside the existing lock, before the death drops.
- **Withdrawal and removal.** `withdraw` and `removeAll` go through `clear`, which strips every mace.
  An offline holder has their saved `playerdata/<uuid>.dat` rewritten by
  `DragonMaceOfflinePlayerData.purgeCurse`, which purges the aura and removes the enchantment
  components from every mace stack in the saved inventory, atomically and without creating a file.
- **Global configuration.** `DragonMaceConfig` now also holds `windBurstLevel` (0–3),
  `mainEnchantment` (`breach` IV or `density` V) and `lastApplyEpochMillis`. Saving re-reads the file
  and replaces only those keys, so unrelated settings survive. `DragonMaceEnchantments.configured`
  turns the configuration into the enchantment component; Wind Burst 0 is omitted entirely.
- **GUI.** `/dm enchantments` (gamemasters) opens `DragonMaceEnchantmentMenu`. Wind Burst 0–III on the
  left, Breach IV and Density V on the right, Apply underneath. Selections stay in the menu until
  Apply. Apply saves the configuration, reconciles every online player immediately and stamps the
  120-second cooldown. The cooldown lives in the saved configuration, so it cannot be bypassed by
  reopening the menu, by another player or by a restart. No XP, material or currency cost.
- **Anvil.** The old guard that cleared the result is gone. `AnvilMenuMixin` now reconciles the result
  instead, so renaming, repairing and merging all work and nothing is blocked; a mace simply cannot
  leave an anvil enchanted when it is not in the holder's inventory.
- **Reversion.** A reverted mace now has no custom name at all and shows the vanilla `Mace` name. It
  keeps only the `draconic_reverted` marker, which is what identifies it for reconversion. The unique
  Stage 1 Dragon Mace keeps its own name.
- **Registry entry.** `DragonMaceMenus` registers `dragon_mace:enchantments` as a `MenuType`, from
  `BootstrapMixin` at the head of `BuiltInRegistries.bootStrap()`. This is the mod's only registry
  entry, so `environment` is now `*` and there is a client entrypoint.
- **Access widener.** `dragon-mace.accesswidener` opens the private `MenuType` constructor and its
  supplier, `MenuScreens.register` and its constructor type, and `ItemCombinerMenu.player`.
- **Files:** `DragonMaceConfig.java`, `DragonMaceEnchantments.java` (new),
  `DragonMaceEnchantmentMenu.java` (new), `DragonMaceMenus.java` (new),
  `client/DragonMaceEnchantmentScreen.java` (new), `client/DragonMaceClient.java` (new),
  `mixin/BootstrapMixin.java` (new), `mixin/AnvilMenuMixin.java`, `DragonMaceItems.java`,
  `DragonMaceIdentity.java`, `DragonMaceMaceConversion.java`, `DragonMaceCurse.java`,
  `world/DragonMaceOfflinePlayerData.java`, `command/DragonMaceCommands.java`,
  `dragon-mace.accesswidener` (new), `dragon-mace.mixins.json`, `fabric.mod.json`, `build.gradle`.
- **Automated:** `DragonMaceEnchantmentOwnershipTest`, 18 tests — defaults, Breach IV versus Density V
  mutual exclusion, Wind Burst clamping, the cooldown and its global nature, JSON round-trip, invalid
  configuration fallback, configured enchantments, holder keeps them, another player loses them, a
  dropped mace loses them, container maces lose them, unrelated items untouched, idempotency, damage
  and count preserved, replacement of other enchantments, and the mace-only guard.
- **In-game only:** the rendered GUI, a real anvil rename, a live curse transfer between two connected
  players, and the Apply button with its cooldown.

## Dropped mace rendering, renaming and reversion — 2026-11-16

- **Invisible dropped mace.** `revertDraconicMace` called `stack.remove(DataComponents.ITEM_MODEL)`.
  The mace prototype carries `minecraft:item_model=minecraft:mace`, so deleting the component left the
  stack with no model id at all and the client had nothing to resolve. Reversion now restores the
  item's own default model from `Items.MACE.components()` instead of deleting it. The Draconic design
  never set a custom model, so nothing custom is lost.
- **Renaming.** `isRevertedDraconicMace` was name-based, so an anvil rename replaced the name and the
  item stopped being recognised, which re-opened enchanting. Reversion now writes a
  `draconic_reverted` marker into `custom_data`, and identification accepts that marker or the
  canonical name. The marker survives a rename, so the anvil guard still refuses enchantments while
  renaming stays allowed.
- **Reversion keeps the current name.** It no longer overwrites the name with the canonical one, so a
  name chosen while reverted survives. The unique Stage 1 Dragon Mace still gets its own `Dragon Mace`
  name back, because its marker is not Draconic-specific.
- **Reconversion.** `createDraconicMaceStack` clears the reverted marker and always writes the
  canonical Draconic name, so a renamed mace that returns to the holder regains the full design.
- **Anvil decision.** `DragonMaceItems.addsEnchantments` now owns the rename-versus-enchant decision and
  `AnvilMenuMixin` calls it, so the rule is testable without a live menu.
- **Files:** `DragonMaceItems.java`, `DragonMaceIdentity.java`, `mixin/AnvilMenuMixin.java`,
  `DragonMaceConversionTest.java`, `DragonMaceItemsTest.java`.
- **Automated:** `DragonMaceConversionTest` grew to 59 tests — the restored mace model, a dropped mace
  matching a plain mace's model, the name surviving a drop, arbitrary renaming, a renamed mace staying
  reverted and unenchantable, reconversion of a renamed mace, the canonical name winning on
  reconversion, the marker being cleared, and the anvil rename-versus-enchant decisions.
- **In-game only:** the rendered dropped-item model in a client and an actual anvil rename.

## Draconic Mace kill attribution in death messages — 2026-11-16

- **Cause.** `ServerPlayer.die` builds the death message inside the method, after the head injections
  have already moved the curse. `CombatTracker.getDeathMessage` calls
  `DamageSource.getLocalizedDeathMessage`, which resolves the weapon by reading the killer's **live**
  main hand (`getMainHandItem`) and only uses the `.item` wording when that stack carries
  `CUSTOM_NAME`. Any change the transfer makes to that stack therefore changes the message.
- **Fix.** `DragonMaceDeathMessage` snapshots the killer's main-hand display name around the transfer
  and `DamageSourceMixin` replays it at the head of `getLocalizedDeathMessage`, rebuilding the message
  with the same `death.attack.<msgId>.item` key and the same three arguments vanilla uses.
- **Ordering.** `ServerPlayerDeathMixin` now runs one head injection that captures, transfers the mace,
  transfers the curse and captures again, so the order is fixed rather than left to mixin application
  order. The transfer timing is unchanged: still at the head of `die`, still before the death drops.
  The second capture sees the killer's mace after the curse converted it; the first survives a mace the
  transfer reverts. A later snapshot only replaces an earlier one when it ranks higher.
- **Scope.** Only a Draconic snapshot overrides the message, and only when the damage source is
  credited to the same player. Ordinary maces, other weapons and unrelated or indirect damage keep
  vanilla wording. The snapshot is dropped at the return of `die`, so nothing is retained.
- **Files:** `DragonMaceDeathMessage.java` (new), `mixin/DamageSourceMixin.java` (new),
  `mixin/ServerPlayerDeathMixin.java`, `dragon-mace.mixins.json` (29 mixins),
  `DragonMaceDeathMessageTest.java` (new).
- **Automated:** `DragonMaceDeathMessageTest`, 20 tests — Draconic and reverted ranking, ordinary and
  unrelated-named negatives, the override decision including the indirect-damage refusal, snapshot
  replacement rules, the vanilla key format, component equality with what vanilla builds, and the
  stars and tier glyph surviving into the message.
- **In-game only:** an actual player-versus-player kill and the broadcast message.

## Reverted Draconic Maces keep only their name — 2026-11-16

- **Reversion strips everything but the name.** `DragonMaceItems.revertDraconicMace` now removes lore,
  `ENCHANTMENTS`, `STORED_ENCHANTMENTS`, `ENCHANTABLE`, the glint override, `UNBREAKABLE`,
  `TOOLTIP_DISPLAY`, `ITEM_MODEL`, `RARITY` and the custom data marker, and keeps the exact
  `Draconic Mace ✪✪✪✪✪➎` name component. Damage and the mace item type survive. The unique Stage 1
  Dragon Mace keeps its own `Dragon Mace` name because its marker is not Draconic-specific.
- **Enchanting table.** `ENCHANTABLE` is removed, so `ItemStack.isEnchantable()` is false.
  `EnchantmentMenu.slotsChanged` gates on that method, so the table offers no enchantments and
  `clickMenuButton` refuses because every cost is 0. This is server-side state, not a client UI change.
- **Anvil.** `AnvilMenuMixin` runs at the tail of `createResult` and clears the result when it carries
  an enchantment level above what the input already had. That covers enchanted books, combining with
  another enchanted mace and every other merge path, while renaming and repair still work because
  neither raises a level. `DragonMaceIdentity.isRevertedDraconicMace` identifies the item by its exact
  name on a mace that is not currently Draconic, since no marker may remain.
- **Reconversion.** `createDraconicMaceStack` now restores `ENCHANTABLE` and seeds `ENCHANTMENTS` with
  `ItemEnchantments.EMPTY`, because `EnchantmentHelper.updateEnchantments` returns early when the
  component is absent. Without that seed a reverted mace could never regain Density V and Wind
  Burst III.
- **Files:** `DragonMaceItems.java`, `DragonMaceIdentity.java`, `mixin/AnvilMenuMixin.java` (new),
  `dragon-mace.mixins.json` (27 mixins), `DragonMaceConversionTest.java`, `DragonMaceItemsTest.java`.
- **Automated:** `DragonMaceConversionTest` grew to 46 tests — the name-only reversion, every removed
  component, `isEnchantable()` false on a reverted mace and true on an ordinary one, the name-based
  identification with ordinary, unrelated-named and live-Draconic negatives, damage preserved, no
  storage protection on a reverted mace, and full reconversion.
- **In-game only:** the enchanting table and anvil screens with a connected player.

## Dragons Wrath inventory membership and the Draconic Mace design — 2026-11-16

### Conversion is now tied to inventory membership

- **Cause.** Conversion was a one-way write triggered by `Inventory.setItem` and `Inventory.add`, and
  nothing observed a mace leaving. A converted mace therefore stayed Draconic in a chest, on the
  ground and after a curse removal, and a mace that never returned kept the design forever.
- **Fix.** `DragonMaceMaceConversion.reconcile` is the single entry point every inventory hook calls:
  it converts when the player holds the curse and reverts when they do not, so the two directions can
  never disagree. `revertLeaving` returns the reverted stack for the two leaving paths, and a
  per-thread `SWEEPING` guard stops the write-back from re-entering the sweep.
- **Leaving paths.** `SlotSetMixin` reverts through `@ModifyVariable` on `Slot.set` when the slot's
  container is not a player inventory, which covers chests, barrels, shulkers, hoppers, dispensers,
  shift-click, drag and drop, number-key swaps and plain slot clicks. `ItemEntityMixin` reverts through
  `@ModifyVariable` on `ItemEntity.setItem`, which covers every drop. Both replace the argument before
  the write, so nothing is lost or duplicated.
- **Staying paths.** A move between two inventory slots goes through `Inventory.setItem`, which
  reconciles and finds the stack already Draconic, so it is left alone.
- **Curse removal.** `clear` and the non-holder branches of `onPlayerJoin` and `onPlayerRespawn` call
  `revertInventory`, so a player who lost the curse while offline is cleaned on their next login.
- **Files:** `DragonMaceMaceConversion.java`, `mixin/InventoryMixin.java`, `mixin/SlotSetMixin.java`,
  `mixin/ItemEntityMixin.java`, `DragonMaceCurse.java`.

### The Draconic Mace design is restored

- **Cause.** The previous build replaced the design with a generic "Curse of the Dragons" lore block,
  a dark purple name and `Rarity.EPIC`, and it skipped the enchantments whenever the mace already had
  any.
- **Fix.** `DragonMaceItems.draconicMaceName` and `draconicMaceLore` reproduce the design command run
  for run: the light purple name, the space, five gold stars and the red `➎`; then the gear score, the
  bold gold `Wind Burst III, Density V`, `◆ Draconic Rune III`, `*Soulbound*` and the obfuscated
  `X Mythic Mace X` footer. `createDraconicMaceStack` also sets the forced glint, `UNBREAKABLE` and a
  `TOOLTIP_DISPLAY` hiding attribute modifiers, enchantments and unbreakable, and always writes the
  real Density V and Wind Burst III. `Rarity.EPIC` is gone because the design command sets no rarity.
- **Revert.** `revertDraconicMace` removes exactly those components, so a reverted mace keeps its
  damage and any data the conversion did not write, and regains the full design if it returns.
- **Files:** `DragonMaceItems.java`.
- **Automated:** `DragonMaceConversionTest`, 35 tests — conversion on entry, multiple maces, idempotency,
  damage and count preserved, a move between slots not reverting, drop and chest leaving paths, return
  and reconversion, five conversion/reversion cycles without duplication or loss, curse removal over
  three stacks, another player's items untouched, null and range guards, the exact name and its four
  runs, all five lore sections with their colours, bold, italic and obfuscated flags, the glint,
  unbreakable, the three hidden tooltip components, Density V and Wind Burst III, and the rune staying
  presentation-only.
- **In-game only:** the rendered tooltip and the item in a client inventory.

### Design-command discrepancy

The design command sets `"italic":false` on `*Soulbound*`, while the written brief asks for italic. The
command is declared authoritative, so the implementation follows the command and the line is dark gray
and not italic. Change one line in `DragonMaceItems.draconicMaceLore` if the italic reading is wanted.

## Global `/dm remove` and Dragon Egg transfer fixes — 2026-11-16

### `/dm remove` is now global

- **Cause of the old failure.** The command only cleared the one UUID recorded in `curse_owner`. An
  offline holder kept the aura in `playerdata/<uuid>.dat` until they logged in, and a targeted removal
  depended on the argument resolving to exactly that UUID, so any mismatch left the curse in place.
- **Fix.** `DragonMaceCurse.removeAll` clears and saves `curse_owner` first, then cleans every online
  player through the existing `clear` path (aura, tablist tag, existing Draconic Mace revert) and
  finally purges every saved player file through `DragonMaceOfflinePlayerData.purgeDirectory`. The
  owner is cleared before any per-player work, so a failed file cannot leave the curse active.
- **Command tree.** `/dm remove` now executes with no argument. `/dm remove <player>` is retained and
  also performs the global removal. Permissions and the other four subcommands are unchanged.
- **Counts.** The command reports cleaned (online + purged + already clean) and failed, and returns 0
  when any file failed.
- **Files:** `DragonMaceCurse.java`, `world/DragonMaceOfflinePlayerData.java`,
  `command/DragonMaceCommands.java`.
- **Automated:** `DragonMaceGlobalRemovalTest` — cleared owner, restart safety, multiple offline files,
  unrelated effects and inventory preserved, malformed file counted as failed while the healthy file is
  still purged, missing directory and non-`.dat` files ignored, no core or paper written, join after
  removal, and the command tree with and without the player argument.
- **In-game only:** running `/dm remove` on a live server with connected players.

### Dragon Egg transfer bypasses

- **Offhand-to-chest loss.** Vanilla's `SWAP` branch clears the source inventory slot
  (`Inventory.setItem(button, EMPTY)`) *before* it writes the destination slot. Cancelling only
  `Slot.set` therefore refused the destination after the source was already empty, which is the
  deletion. The click is now rejected at the head of `doClick` through
  `DragonMaceProtection.blocksSwapInto`, before anything is cleared, and a rejected click forces
  `sendAllDataToRemote` so the client's predicted swap cannot linger as a phantom loss.
- **Shelves.** `ShelfBlock.useItemOn` calls `ShelfBlockEntity.swapItemNoUpdate` directly; no menu slot
  and no `Container.setItem` is involved, so the slot guards never saw it. `ShelfBlockEntityMixin`
  returns the incoming stack unchanged, which the caller writes back into the player's selected slot,
  and `ListBackedContainerMixin` refuses `canPlaceItem`, `setItem` and `setItemNoUpdate` for automated
  insertion.
- **Bundles.** `BundleContentsMixin` refuses `tryInsert` and `tryTransfer`, the funnel both bundle click
  overrides use, returning 0 so nothing is inserted, consumed or rewritten.
- **Files:** `DragonMaceProtection.java`, `mixin/ContainerTransactionMixin.java`,
  `mixin/ShelfBlockEntityMixin.java` (new), `mixin/ListBackedContainerMixin.java` (new),
  `mixin/BundleContentsMixin.java` (new), `dragon-mace.mixins.json` (26 mixins).
- **Automated:** `DragonEggTransferTest` — offhand and all nine hotbar swaps into a chest rejected,
  player-inventory swaps allowed, carried egg rejected in a chest slot and allowed in the inventory,
  shift-click and drag rejected, shelf and bundle rejection with ordinary items still accepted, the
  crafting grid still accepting the egg, and source stacks unchanged after a rejected swap or
  quick-move.
- **In-game only:** every interaction above with a connected player; no client was connected.

## Offline `/dm remove` — 2026-11-16

`/dm remove <player>` now clears the curse whether the holder is online or offline. The command
syntax, the `GameProfileArgument` target and the gamemaster requirement are unchanged.

- **Holder resolution.** The target is resolved to a UUID through the game profile argument, so a
  previously known offline player is matched by UUID and never by username. A player who never joined
  has no saved data; removal is still refused unless their UUID is the recorded holder.
- **Global state first.** `DragonMaceCurse.remove` clears `curse_owner` in `data/dragon_mace.dat` and
  schedules the save before any per-player cleanup, so the curse cannot reactivate even if the rest of
  the cleanup fails.
- **Online holder.** The existing `clear` path runs unchanged: aura removed, tablist tag rebroadcast,
  and the existing Draconic Mace revert. No Very Heavy Core is created.
- **Offline holder.** `DragonMaceOfflinePlayerData` reads `playerdata/<uuid>.dat`, removes only the
  exact infinite amplifier-0 `minecraft:glowing` entry from `active_effects`, and rewrites the file
  through a sibling temporary file plus an atomic move. Every other key, including `Inventory`, is
  preserved, and a missing file is reported as `NO_DATA` and never created.
- **No reactivation.** `DragonMaceCurse.shouldApplyOnJoin` only reapplies the curse to the recorded
  holder, so a removed player who reconnects gets `clearStale` and a plain tablist name instead.
- **Per-player state that does not exist.** Dragons Pride is derived from curse ownership and Dragons
  Wrath conversion is item data, so neither has player NBT to purge; the aura is the only persistent
  per-player curse record. `/dm withdraw` is untouched and still returns a Very Heavy Core.
- **Files:** `DragonMaceCurse.java`, `world/DragonMaceOfflinePlayerData.java` (new),
  `command/DragonMaceCommands.java` (javadoc only).
- **Automated:** `DragonMaceOfflineRemovalTest`, 15 tests — online removal, offline removal with a real
  compressed NBT round-trip, unrelated keys and other effects preserved, finite and amplified glowing
  left alone, missing file reported without being created, join after removal, non-owner, unknown
  player, another player's curse untouched, and no core or paper written into the player data.
- **In-game only:** running `/dm remove` against a real offline holder and observing the aura gone on
  their next login.

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
  (`minecraft:heavy_core`). No client asset is shipped and no registry entry is needed.
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
   `minecraft:item_model = minecraft:heavy_core`, which names the vanilla item definition
   `assets/minecraft/items/heavy_core.json` (that file resolves to `minecraft:block/heavy_core`), but
   no client has rendered the item.

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
- [ ] `/dm remove` with no argument clears the curse for every player, online and offline.
- [ ] `/dm remove <offline player>` clears the saved owner and the aura in `playerdata/<uuid>.dat`.
- [ ] The offline holder logs in afterwards with no glowing aura and no tablist tag.
- [ ] `/dm remove` never hands out a Very Heavy Core.
- [ ] An egg in the offhand swapped into a chest stays in the offhand.
- [ ] An egg cannot be placed on a shelf, in a bundle, or in any storage container.
- [ ] The egg still goes into the 3x3 crafting grid.
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
- `DragonMaceBugRegressionTest` — one group per reported bug: the `minecraft:heavy_core` item
  definition and its resolution, inventory conversion of every eligible mace with data preservation
  and idempotency plus the two `Inventory` hook targets, and the Dragon Egg being allowed in a
  `CraftingContainer` while storage containers still refuse it.
- `DragonMaceOfflineRemovalTest` — online and offline removal, the saved-data aura purge with a real
  NBT round-trip, unrelated keys and other effects preserved, missing saved data, join after removal,
  non-owner and unknown-player refusals, another holder untouched, and no Very Heavy Core created.
