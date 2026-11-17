package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The 54-slot rune section of the enchantment menu.
 *
 * <p>Asserts the fixed layout, the per-rune colour and state, the slot-to-button mapping and the
 * server-side unlock gate, all without a menu or a player.
 */
class DragonMaceRuneGuiTest {
	private static RegistryAccess registries;

	@BeforeAll
	static void setUp() {
		TestBootstrap.boot();
		registries = enchantmentRegistries();
	}

	// ------------------------------------------------------------------ layout

	@Test
	void theRuneRowsStartAtTwentySevenThirtySixAndFortyFive() {
		assertEquals(27, DragonMaceEnchantmentLayout.runeRowStart(DragonMaceRuneCategory.KILL));
		assertEquals(36, DragonMaceEnchantmentLayout.runeRowStart(DragonMaceRuneCategory.AURA));
		assertEquals(45, DragonMaceEnchantmentLayout.runeRowStart(DragonMaceRuneCategory.ATTACK));
	}

	@Test
	void everyRuneHasItsOwnSlot() {
		for (DragonMaceRuneCategory category : DragonMaceRuneCategory.values()) {
			for (DragonMaceRune rune : DragonMaceRune.values()) {
				int slot = DragonMaceEnchantmentLayout.slotForRune(category, rune);

				assertSame(category, DragonMaceEnchantmentLayout.categoryOf(slot));
				assertSame(rune, DragonMaceEnchantmentLayout.runeOf(slot));
			}
		}
	}

	@Test
	void theRuneRowsHoldExactlyFiveRunesEach() {
		assertEquals(5, DragonMaceRune.values().length);
		assertTrue(DragonMaceEnchantmentLayout.isRuneSlot(29));
		assertTrue(DragonMaceEnchantmentLayout.isRuneSlot(33));
		assertFalse(DragonMaceEnchantmentLayout.isRuneSlot(34));
		assertTrue(DragonMaceEnchantmentLayout.isRuneSlot(38));
		assertFalse(DragonMaceEnchantmentLayout.isRuneSlot(43));
		assertTrue(DragonMaceEnchantmentLayout.isRuneSlot(47));
		assertFalse(DragonMaceEnchantmentLayout.isRuneSlot(52));
	}

	@Test
	void theInfoIconSitsExactlyTwoSlotsBeforeTheFirstRune() {
		for (DragonMaceRuneCategory category : DragonMaceRuneCategory.values()) {
			int info = DragonMaceEnchantmentLayout.runeInfoSlot(category);
			int first = DragonMaceEnchantmentLayout.slotForRune(category, DragonMaceRune.values()[0]);

			// The info icon is the second slot of the row and the runes start on the third, so it is
			// one index before the first rune. The explicit slot numbers below are authoritative.
			assertEquals(first - 1, info, category.name());
			assertSame(category, DragonMaceEnchantmentLayout.categoryOfInfo(info));
			assertFalse(DragonMaceEnchantmentLayout.isRuneSlot(info));
			assertFalse(DragonMaceEnchantmentLayout.isRuneApplySlot(info));
			assertFalse(DragonMaceEnchantmentLayout.isRuneToggleSlot(info));
		}

		assertEquals(28, DragonMaceEnchantmentLayout.runeInfoSlot(DragonMaceRuneCategory.KILL));
		assertEquals(37, DragonMaceEnchantmentLayout.runeInfoSlot(DragonMaceRuneCategory.AURA));
		assertEquals(46, DragonMaceEnchantmentLayout.runeInfoSlot(DragonMaceRuneCategory.ATTACK));
	}

	@Test
	void theToggleSitsExactlyOneSlotBeforeTheApplySlot() {
		for (DragonMaceRuneCategory category : DragonMaceRuneCategory.values()) {
			int toggle = DragonMaceEnchantmentLayout.runeToggleSlot(category);
			int apply = DragonMaceEnchantmentLayout.runeApplySlot(category);

			assertEquals(apply - 1, toggle, category.name());
			assertEquals(DragonMaceEnchantmentLayout.runeRowStart(category) + 8, apply);
			assertSame(category, DragonMaceEnchantmentLayout.categoryOfToggle(toggle));
			assertSame(category, DragonMaceEnchantmentLayout.categoryOfApply(apply));
			assertNull(DragonMaceEnchantmentLayout.categoryOfApply(toggle));
			assertNull(DragonMaceEnchantmentLayout.categoryOfToggle(apply));
			assertFalse(DragonMaceEnchantmentLayout.isRuneSlot(toggle));
			assertFalse(DragonMaceEnchantmentLayout.isRuneSlot(apply));
		}

		assertEquals(34, DragonMaceEnchantmentLayout.runeToggleSlot(DragonMaceRuneCategory.KILL));
		assertEquals(35, DragonMaceEnchantmentLayout.runeApplySlot(DragonMaceRuneCategory.KILL));
		assertEquals(43, DragonMaceEnchantmentLayout.runeToggleSlot(DragonMaceRuneCategory.AURA));
		assertEquals(44, DragonMaceEnchantmentLayout.runeApplySlot(DragonMaceRuneCategory.AURA));
		assertEquals(52, DragonMaceEnchantmentLayout.runeToggleSlot(DragonMaceRuneCategory.ATTACK));
		assertEquals(53, DragonMaceEnchantmentLayout.runeApplySlot(DragonMaceRuneCategory.ATTACK));
	}

	@Test
	void theUnusedRuneRowSlotsAreEmpty() {
		ItemStack[] items = DragonMaceEnchantmentLayout.build(registries, 3,
				DragonMaceConfig.MainEnchantment.DENSITY, DragonMaceRuneSelection.defaults(), 4);

		for (int slot : new int[] {27, 36, 45}) {
			assertTrue(items[slot].isEmpty(), "slot " + slot + " must be empty");
		}
	}

	@Test
	void everyToggleSlotIsFilledAndShowsItsState() {
		DragonMaceRuneToggles toggles = new DragonMaceRuneToggles(true, false, true);
		ItemStack[] items = DragonMaceEnchantmentLayout.build(registries, 0,
				DragonMaceConfig.MainEnchantment.BREACH, DragonMaceRuneSelection.defaults(), 4, toggles);

		assertTrue(items[34].is(Items.LIME_DYE));
		assertEquals("Kill Rune: Enabled", items[34].get(DataComponents.CUSTOM_NAME).getString());
		assertTrue(items[43].is(Items.LIME_DYE));
		assertEquals("Aura Rune: Enabled", items[43].get(DataComponents.CUSTOM_NAME).getString());
		assertTrue(items[52].is(Items.GRAY_DYE));
		assertEquals("Attack Rune: Disabled", items[52].get(DataComponents.CUSTOM_NAME).getString());
	}

	@Test
	void everyInfoIconIsFilled() {
		ItemStack[] items = DragonMaceEnchantmentLayout.build(registries, 0,
				DragonMaceConfig.MainEnchantment.BREACH, DragonMaceRuneSelection.defaults(), 4);

		for (DragonMaceRuneCategory category : DragonMaceRuneCategory.values()) {
			ItemStack stack = items[DragonMaceEnchantmentLayout.runeInfoSlot(category)];

			assertTrue(stack.is(Items.KNOWLEDGE_BOOK), category.name());
			assertEquals(category.label() + " Info",
					stack.get(DataComponents.CUSTOM_NAME).getString());
			assertTrue(stack.get(DataComponents.LORE).lines().stream()
					.map(net.minecraft.network.chat.Component::getString)
					.anyMatch(line -> line.equals(DragonMaceEnchantmentLayout.categoryDescription(category))),
					category.name());
		}
	}

	@Test
	void everyRuneSlotIsFilled() {
		ItemStack[] items = DragonMaceEnchantmentLayout.build(registries, 0,
				DragonMaceConfig.MainEnchantment.BREACH, DragonMaceRuneSelection.defaults(), 4);

		for (DragonMaceRuneCategory category : DragonMaceRuneCategory.values()) {
			for (DragonMaceRune rune : DragonMaceRune.values()) {
				int slot = DragonMaceEnchantmentLayout.slotForRune(category, rune);
				assertFalse(items[slot].isEmpty(), "slot " + slot + " must hold a rune");
			}
		}
	}

	@Test
	void theWholeGridMatchesTheSpecifiedLayout() {
		ItemStack[] items = DragonMaceEnchantmentLayout.build(registries, 3,
				DragonMaceConfig.MainEnchantment.DENSITY, DragonMaceRuneSelection.defaults(), 4);
		Set<Integer> filled = Set.of(9, 10, 11, 12, 15, 16, 21, 22, 23,
				28, 29, 30, 31, 32, 33, 34,
				37, 38, 39, 40, 41, 42, 43,
				46, 47, 48, 49, 50, 51, 52);

		for (int slot = 0; slot < DragonMaceEnchantmentLayout.SLOT_COUNT; slot++) {
			assertEquals(filled.contains(slot), !items[slot].isEmpty(), "slot " + slot);
		}
	}

	// ------------------------------------------------------------------ presentation

	@Test
	void everyRuneNameCarriesItsOwnColour() {
		ItemStack[] items = DragonMaceEnchantmentLayout.build(registries, 0,
				DragonMaceConfig.MainEnchantment.BREACH, DragonMaceRuneSelection.defaults(), 4);

		for (DragonMaceRune rune : DragonMaceRune.values()) {
			ItemStack stack = items[DragonMaceEnchantmentLayout.slotForRune(
					DragonMaceRuneCategory.KILL, rune)];
			var name = stack.get(DataComponents.CUSTOM_NAME);

			assertEquals(rune.displayName(), name.getString());
			assertEquals(rune.colour().getColor(), name.getStyle().getColor().getValue());
		}
	}

	@Test
	void aLockedRuneShowsItsRequirement() {
		ItemStack[] items = DragonMaceEnchantmentLayout.build(registries, 0,
				DragonMaceConfig.MainEnchantment.BREACH, DragonMaceRuneSelection.defaults(), 0);
		ItemStack warden = items[DragonMaceEnchantmentLayout.slotForRune(
				DragonMaceRuneCategory.AURA, DragonMaceRune.WARDEN)];
		List<String> lore = warden.get(DataComponents.LORE).lines().stream()
				.map(net.minecraft.network.chat.Component::getString).toList();

		assertTrue(lore.contains("Locked"), lore.toString());
		assertTrue(lore.contains("Requires 4 kills."), lore.toString());
		assertNull(warden.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE),
				"a locked rune must not glint");
	}

	@Test
	void anUnlockedRuneShowsUnlocked() {
		ItemStack[] items = DragonMaceEnchantmentLayout.build(registries, 0,
				DragonMaceConfig.MainEnchantment.BREACH, DragonMaceRuneSelection.defaults(), 4);
		ItemStack warden = items[DragonMaceEnchantmentLayout.slotForRune(
				DragonMaceRuneCategory.AURA, DragonMaceRune.WARDEN)];
		List<String> lore = warden.get(DataComponents.LORE).lines().stream()
				.map(net.minecraft.network.chat.Component::getString).toList();

		assertTrue(lore.contains("Unlocked"), lore.toString());
		assertTrue(lore.contains("Click to select"), lore.toString());
	}

	@Test
	void theSelectedRuneGlints() {
		DragonMaceRuneSelection selection = DragonMaceRuneSelection.defaults()
				.with(DragonMaceRuneCategory.KILL, DragonMaceRune.SOULFLAME);
		ItemStack[] items = DragonMaceEnchantmentLayout.build(registries, 0,
				DragonMaceConfig.MainEnchantment.BREACH, selection, 4);
		ItemStack selected = items[DragonMaceEnchantmentLayout.slotForRune(
				DragonMaceRuneCategory.KILL, DragonMaceRune.SOULFLAME)];

		assertEquals(Boolean.TRUE, selected.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
	}

	@Test
	void theConfirmLoreListsTheSelectedRunes() {
		DragonMaceRuneSelection selection = new DragonMaceRuneSelection(DragonMaceRune.SOULFLAME,
				DragonMaceRune.TEMPEST, DragonMaceRune.WARDEN);
		ItemStack[] items = DragonMaceEnchantmentLayout.build(registries, 0,
				DragonMaceConfig.MainEnchantment.BREACH, selection, 4);
		List<String> lore = items[DragonMaceEnchantmentLayout.SLOT_CONFIRM]
				.get(DataComponents.LORE).lines().stream()
				.map(net.minecraft.network.chat.Component::getString).toList();

		assertTrue(lore.contains("Runes: Soulflame / Tempest / Warden."), lore.toString());
	}

	// ------------------------------------------------------------------ buttons

	@Test
	void everyRuneSlotMapsToItsOwnButton() {
		for (DragonMaceRuneCategory category : DragonMaceRuneCategory.values()) {
			for (DragonMaceRune rune : DragonMaceRune.values()) {
				int slot = DragonMaceEnchantmentLayout.slotForRune(category, rune);
				int button = DragonMaceEnchantmentMenu.buttonIdFor(slot);

				assertSame(category, DragonMaceEnchantmentMenu.categoryForButton(button));
				assertSame(rune, DragonMaceEnchantmentMenu.runeForButton(button));
			}
		}
	}

	@Test
	void theRuneButtonsDoNotCollideWithTheEnchantmentButtons() {
		for (DragonMaceRuneCategory category : DragonMaceRuneCategory.values()) {
			for (DragonMaceRune rune : DragonMaceRune.values()) {
				assertTrue(DragonMaceEnchantmentMenu.runeButton(category, rune)
						>= DragonMaceEnchantmentMenu.BUTTON_RUNE_BASE);
			}
		}

		assertNull(DragonMaceEnchantmentMenu.categoryForButton(
				DragonMaceEnchantmentMenu.BUTTON_APPLY));
		assertNull(DragonMaceEnchantmentMenu.runeForButton(-1));
	}

	@Test
	void aLockedRuneButtonIsRefused() {
		assertSame(DragonMaceRune.DRACONIC, DragonMaceEnchantmentMenu.runeAfter(
				DragonMaceEnchantmentMenu.runeButton(DragonMaceRuneCategory.AURA,
						DragonMaceRune.WARDEN),
				DragonMaceRune.DRACONIC, 3));
	}

	@Test
	void anUnlockedRuneButtonIsAccepted() {
		assertSame(DragonMaceRune.WARDEN, DragonMaceEnchantmentMenu.runeAfter(
				DragonMaceEnchantmentMenu.runeButton(DragonMaceRuneCategory.AURA,
						DragonMaceRune.WARDEN),
				DragonMaceRune.DRACONIC, 4));
	}

	@Test
	void aNonRuneButtonLeavesTheSelectionAlone() {
		assertSame(DragonMaceRune.TEMPEST, DragonMaceEnchantmentMenu.runeAfter(
				DragonMaceEnchantmentMenu.BUTTON_APPLY, DragonMaceRune.TEMPEST, 9));
	}

	// ------------------------------------------------------------------ confirm

	@Test
	void confirmIsRefusedWhenARuneIsLocked() {
		DragonMaceRuneSelection locked = DragonMaceRuneSelection.defaults()
				.with(DragonMaceRuneCategory.KILL, DragonMaceRune.WARDEN);

		assertEquals(DragonMaceEnchantmentMenu.ApplyResult.INVALID,
				DragonMaceEnchantmentMenu.decideApply(DragonMaceConfig.defaults(), 3,
						DragonMaceConfig.MainEnchantment.DENSITY.ordinal(), locked, 0, 0L));
	}

	@Test
	void confirmIsRefusedDuringTheCooldown() {
		DragonMaceConfig config = DragonMaceConfig.defaults().withLastApply(1_000L);

		assertEquals(DragonMaceEnchantmentMenu.ApplyResult.COOLDOWN,
				DragonMaceEnchantmentMenu.decideApply(config, 3,
						DragonMaceConfig.MainEnchantment.DENSITY.ordinal(),
						DragonMaceRuneSelection.defaults(), 4, 2_000L));
	}

	@Test
	void confirmIsAcceptedWithUnlockedRunesAndNoCooldown() {
		DragonMaceRuneSelection selection = new DragonMaceRuneSelection(DragonMaceRune.SOULFLAME,
				DragonMaceRune.TEMPEST, DragonMaceRune.WARDEN);

		assertEquals(DragonMaceEnchantmentMenu.ApplyResult.APPLIED,
				DragonMaceEnchantmentMenu.decideApply(DragonMaceConfig.defaults(), 3,
						DragonMaceConfig.MainEnchantment.DENSITY.ordinal(), selection, 4,
						1_000_000L));
	}

	// ------------------------------------------------------------------ apply slots

	@Test
	void everyApplySlotMapsToItsCategoryButton() {
		for (DragonMaceRuneCategory category : DragonMaceRuneCategory.values()) {
			int button = DragonMaceEnchantmentMenu.runeApplyButton(category);
			int apply = DragonMaceEnchantmentLayout.runeApplySlot(category);

			assertEquals(DragonMaceEnchantmentMenu.BUTTON_RUNE_APPLY_BASE + category.ordinal(), button);
			assertSame(category, DragonMaceEnchantmentMenu.runeApplyCategory(button));
			assertEquals(button, DragonMaceEnchantmentMenu.buttonIdFor(apply), "slot " + apply);
		}

		assertNull(DragonMaceEnchantmentMenu.runeApplyCategory(DragonMaceEnchantmentMenu.BUTTON_APPLY));
		assertNull(DragonMaceEnchantmentMenu.runeApplyCategory(-1));
	}

	@Test
	void everyToggleSlotMapsToItsCategoryButton() {
		for (DragonMaceRuneCategory category : DragonMaceRuneCategory.values()) {
			int button = DragonMaceEnchantmentMenu.runeToggleButton(category);
			int toggle = DragonMaceEnchantmentLayout.runeToggleSlot(category);

			assertEquals(DragonMaceEnchantmentMenu.BUTTON_RUNE_TOGGLE_BASE + category.ordinal(), button);
			assertSame(category, DragonMaceEnchantmentMenu.runeToggleCategory(button));
			assertEquals(button, DragonMaceEnchantmentMenu.buttonIdFor(toggle), "slot " + toggle);
		}

		assertNull(DragonMaceEnchantmentMenu.runeToggleCategory(DragonMaceEnchantmentMenu.BUTTON_APPLY));
		assertNull(DragonMaceEnchantmentMenu.runeToggleCategory(-1));
	}

	@Test
	void applyingARowKeepsTheOtherRowsSaved() {
		DragonMaceRuneSelection saved = DragonMaceRuneSelection.defaults();
		DragonMaceRuneSelection pending = new DragonMaceRuneSelection(DragonMaceRune.SOULFLAME,
				DragonMaceRune.TEMPEST, DragonMaceRune.WARDEN);

		DragonMaceRuneSelection applied = DragonMaceEnchantmentMenu.runeSelectionAfterApply(saved,
				pending, DragonMaceRuneCategory.ATTACK);

		assertSame(DragonMaceRune.DRACONIC, applied.kill());
		assertSame(DragonMaceRune.TEMPEST, applied.attack());
		assertSame(DragonMaceRune.DRACONIC, applied.aura());
	}

	@Test
	void runeApplicationIgnoresTheEnchantmentCooldown() {
		long now = 4_000_000L;
		DragonMaceConfig cooling = DragonMaceConfig.defaults().withLastApply(now);
		DragonMaceRuneSelection selection = new DragonMaceRuneSelection(DragonMaceRune.SOULFLAME,
				DragonMaceRune.TEMPEST, DragonMaceRune.WARDEN);

		assertEquals(DragonMaceEnchantmentMenu.ApplyResult.COOLDOWN,
				DragonMaceEnchantmentMenu.decideApply(cooling, 3,
						DragonMaceConfig.MainEnchantment.DENSITY.ordinal(), selection, 4, now + 1_000L));
		assertTrue(DragonMaceEnchantmentMenu.canApplyRunes(selection, 4));
	}

	@Test
	void runeApplicationIsStillGatedByTheUnlock() {
		DragonMaceRuneSelection locked = DragonMaceRuneSelection.defaults()
				.with(DragonMaceRuneCategory.AURA, DragonMaceRune.WARDEN);

		assertFalse(DragonMaceEnchantmentMenu.canApplyRunes(locked, 3));
		assertTrue(DragonMaceEnchantmentMenu.canApplyRunes(locked, 4));
	}

	@Test
	void aRefreshedMaceKeepsTheSelectedRunes() {
		DragonMaceRuneSelection selection = new DragonMaceRuneSelection(DragonMaceRune.SOULFLAME,
				DragonMaceRune.TEMPEST, DragonMaceRune.WARDEN);
		DragonMaceLoreContext context = new DragonMaceLoreContext(
				DragonMaceEnchantments.configured(registries, DragonMaceConfig.defaults()),
				DragonMaceLoreContext.summaryOf(
						DragonMaceEnchantments.configured(registries, DragonMaceConfig.defaults())),
				selection, 4);
		ItemStack mace = DragonMaceItems.createDraconicMaceStack(new ItemStack(Items.MACE), registries,
				context);

		assertTrue(DragonMaceItems.refreshDraconicMace(mace, context));

		List<String> lore = mace.get(DataComponents.LORE).lines().stream()
				.map(net.minecraft.network.chat.Component::getString).toList();

		assertTrue(lore.contains(DragonMaceItems.DRACONIC_RUNE_GLYPH + " Kill Rune: Soulflame"),
				lore.toString());
		assertTrue(lore.contains(DragonMaceItems.DRACONIC_RUNE_GLYPH + " Attack Rune: Tempest"),
				lore.toString());
		assertTrue(lore.contains(DragonMaceItems.DRACONIC_RUNE_GLYPH + " Aura Rune: Warden"),
				lore.toString());
	}

	// ------------------------------------------------------------------ toggles

	@Test
	void everyCategoryDefaultsToEnabled() {
		DragonMaceRuneToggles toggles = DragonMaceRuneToggles.defaults();

		for (DragonMaceRuneCategory category : DragonMaceRuneCategory.values()) {
			assertTrue(toggles.get(category), category.name());
		}

		assertTrue(toggles.allEnabled());
	}

	@Test
	void togglingOneCategoryLeavesTheOtherTwoAlone() {
		DragonMaceRuneToggles off = DragonMaceRuneToggles.defaults()
				.toggled(DragonMaceRuneCategory.ATTACK);

		assertTrue(off.get(DragonMaceRuneCategory.KILL));
		assertFalse(off.get(DragonMaceRuneCategory.ATTACK));
		assertTrue(off.get(DragonMaceRuneCategory.AURA));

		DragonMaceRuneToggles back = off.toggled(DragonMaceRuneCategory.ATTACK);
		assertTrue(back.allEnabled());
	}

	@Test
	void togglingNeverChangesTheSelectedRune() {
		DragonMaceRuneSelection selection = new DragonMaceRuneSelection(DragonMaceRune.SOULFLAME,
				DragonMaceRune.TEMPEST, DragonMaceRune.WARDEN);
		DragonMaceRuneToggles off = DragonMaceRuneToggles.defaults()
				.with(DragonMaceRuneCategory.KILL, false);

		assertSame(DragonMaceRune.SOULFLAME, selection.get(DragonMaceRuneCategory.KILL));
		assertFalse(off.get(DragonMaceRuneCategory.KILL));
	}

	@Test
	void theTogglesSurviveACodecRoundTrip() {
		DragonMaceRuneToggles toggles = new DragonMaceRuneToggles(false, true, false);
		var encoded = DragonMaceRuneToggles.CODEC
				.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, toggles).getOrThrow();
		DragonMaceRuneToggles decoded = DragonMaceRuneToggles.CODEC
				.parse(com.mojang.serialization.JsonOps.INSTANCE, encoded).getOrThrow();

		assertEquals(toggles, decoded);
	}

	@Test
	void anOlderSaveWithoutTogglesLoadsEverythingEnabled() {
		var json = new com.google.gson.JsonObject();
		DragonMaceRuneToggles decoded = DragonMaceRuneToggles.CODEC
				.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();

		assertTrue(decoded.allEnabled());
	}

	@Test
	void theLoreMarksADisabledCategory() {
		DragonMaceRuneToggles toggles = new DragonMaceRuneToggles(true, true, false);
		DragonMaceLoreContext context = new DragonMaceLoreContext(
				DragonMaceEnchantments.configured(registries, DragonMaceConfig.defaults()),
				"", DragonMaceRuneSelection.defaults(), 4, toggles);
		ItemStack mace = DragonMaceItems.createDraconicMaceStack(new ItemStack(Items.MACE), registries,
				context);

		List<String> lore = mace.get(DataComponents.LORE).lines().stream()
				.map(net.minecraft.network.chat.Component::getString).toList();

		assertTrue(lore.stream().anyMatch(line -> line.endsWith("(Disabled)")), lore.toString());
		assertTrue(lore.stream().filter(line -> line.contains("Rune:")).count() == 3, lore.toString());
		assertTrue(lore.stream().filter(line -> line.endsWith("(Disabled)")).count() == 1, lore.toString());
	}

	// ------------------------------------------------------------------ helpers

	private static RegistryAccess enchantmentRegistries() {
		return DragonMaceEnchantmentGuiTest.enchantmentRegistries();
	}
}
