package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import com.mojang.serialization.Lifecycle;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for the Draconic Mace enchantment GUI.
 *
 * <p>The layout, the display stacks, the selection decisions and the slot guards are exercised directly.
 * Constructing a live menu needs a connected player, so the menu's own click routing is covered through
 * the same static decisions it calls.
 */
class DragonMaceEnchantmentGuiTest {
	private static RegistryAccess registries;

	@BeforeAll
	static void setUp() {
		TestBootstrap.boot();
		registries = enchantmentRegistries();
	}

	// ------------------------------------------------------------- layout

	@Test
	void theGuiHasExactlyFiftyFourSlots() {
		assertEquals(54, DragonMaceEnchantmentLayout.SLOT_COUNT);
		assertEquals(54, DragonMaceEnchantmentLayout.build(registries, 0,
				DragonMaceConfig.MainEnchantment.BREACH).length);
	}

	@Test
	void theTopRowIsEmpty() {
		ItemStack[] items = DragonMaceEnchantmentLayout.build(registries, 3,
				DragonMaceConfig.MainEnchantment.DENSITY);

		for (int slot = 0; slot <= 8; slot++) {
			assertTrue(items[slot].isEmpty(), "slot " + slot + " must be empty");
		}
	}

	@Test
	void theSelectionAndActionItemsSitInTheirExactSlots() {
		ItemStack[] items = DragonMaceEnchantmentLayout.build(registries, 3,
				DragonMaceConfig.MainEnchantment.DENSITY);

		assertTrue(items[9].is(Items.BARRIER));
		assertTrue(items[10].is(Items.WIND_CHARGE));
		assertTrue(items[11].is(Items.WIND_CHARGE));
		assertTrue(items[12].is(Items.WIND_CHARGE));
		assertTrue(items[13].isEmpty());
		assertTrue(items[14].isEmpty());
		assertTrue(items[15].is(Items.ENCHANTED_BOOK));
		assertTrue(items[16].is(Items.ENCHANTED_BOOK));
		assertTrue(items[17].isEmpty());
		assertTrue(items[18].isEmpty());
		assertTrue(items[19].isEmpty());
		assertTrue(items[20].isEmpty());
		assertTrue(items[21].is(Items.LIME_DYE));
		assertTrue(items[22].is(Items.KNOWLEDGE_BOOK));
		assertTrue(items[23].is(Items.RED_DYE));
		assertTrue(items[24].isEmpty());
		assertTrue(items[25].isEmpty());
		assertTrue(items[26].isEmpty());
	}

	@Test
	void theWindChargesCarryTheirStackCounts() {
		ItemStack[] items = DragonMaceEnchantmentLayout.build(registries, 0,
				DragonMaceConfig.MainEnchantment.BREACH);

		assertEquals(1, items[10].getCount());
		assertEquals(2, items[11].getCount());
		assertEquals(3, items[12].getCount());
	}

	@Test
	void everySelectionItemCarriesItsDisplayName() {
		ItemStack[] items = DragonMaceEnchantmentLayout.build(registries, 0,
				DragonMaceConfig.MainEnchantment.BREACH);

		assertEquals("Wind Burst 0", name(items[9]));
		assertEquals("Wind Burst I", name(items[10]));
		assertEquals("Wind Burst II", name(items[11]));
		assertEquals("Wind Burst III", name(items[12]));
		assertEquals("Breach IV", name(items[15]));
		assertEquals("Density V", name(items[16]));
		assertEquals("Confirm", name(items[21]));
		assertEquals("Info", name(items[22]));
		assertEquals("Cancel", name(items[23]));
	}

	@Test
	void everySelectionItemCarriesADescription() {
		ItemStack[] items = DragonMaceEnchantmentLayout.build(registries, 0,
				DragonMaceConfig.MainEnchantment.BREACH);

		for (int slot : new int[] {9, 10, 11, 12, 15, 16, 21, 22, 23}) {
			ItemLore lore = items[slot].get(DataComponents.LORE);
			assertNotNull(lore, "slot " + slot + " must have lore");
			assertFalse(lore.lines().isEmpty(), "slot " + slot + " must have lore lines");
		}

		assertTrue(loreText(items[9]).contains("Wind Burst is disabled."));
		assertTrue(loreText(items[10]).contains("Wind Burst level I."));
		assertTrue(loreText(items[11]).contains("Wind Burst level II."));
		assertTrue(loreText(items[12]).contains("Wind Burst level III."));
		assertTrue(loreText(items[15]).contains("Breach reduces the effectiveness"));
		assertTrue(loreText(items[16]).contains("Density increases smash attack damage."));
	}

	@Test
	void theEnchantedBooksCarryTheirStoredEnchantmentLevels() {
		ItemStack[] items = DragonMaceEnchantmentLayout.build(registries, 0,
				DragonMaceConfig.MainEnchantment.BREACH);

		ItemEnchantments breach = items[15].get(DataComponents.STORED_ENCHANTMENTS);
		ItemEnchantments density = items[16].get(DataComponents.STORED_ENCHANTMENTS);

		assertNotNull(breach);
		assertNotNull(density);
		assertEquals(4, levelOf(breach, Enchantments.BREACH));
		assertEquals(5, levelOf(density, Enchantments.DENSITY));
	}

	@Test
	void theSelectedOptionIsMarkedAndGlints() {
		ItemStack[] items = DragonMaceEnchantmentLayout.build(registries, 2,
				DragonMaceConfig.MainEnchantment.DENSITY);

		assertTrue(loreText(items[11]).contains("Selected"));
		assertFalse(loreText(items[10]).contains("Selected"));
		assertEquals(Boolean.TRUE, items[11].get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
		assertNull(items[10].get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));

		assertTrue(loreText(items[16]).contains("Selected"));
		assertFalse(loreText(items[15]).contains("Selected"));
	}

	@Test
	void slotGeometryIsThreeRowsOfNine() {
		assertEquals(8, DragonMaceEnchantmentLayout.slotX(0));
		assertEquals(18, DragonMaceEnchantmentLayout.slotY(0));
		assertEquals(8 + 8 * 18, DragonMaceEnchantmentLayout.slotX(8));
		assertEquals(18, DragonMaceEnchantmentLayout.slotY(8));
		assertEquals(18 + 18, DragonMaceEnchantmentLayout.slotY(9));
		assertEquals(18 + 36, DragonMaceEnchantmentLayout.slotY(26));
	}

	// ---------------------------------------------------------- selection

	@Test
	void windBurstSelectionMapsEveryLevel() {
		assertEquals(0, DragonMaceEnchantmentMenu.windBurstAfter(
				DragonMaceEnchantmentMenu.BUTTON_WIND_BURST_0, 3));
		assertEquals(1, DragonMaceEnchantmentMenu.windBurstAfter(
				DragonMaceEnchantmentMenu.BUTTON_WIND_BURST_1, 3));
		assertEquals(2, DragonMaceEnchantmentMenu.windBurstAfter(
				DragonMaceEnchantmentMenu.BUTTON_WIND_BURST_2, 3));
		assertEquals(3, DragonMaceEnchantmentMenu.windBurstAfter(
				DragonMaceEnchantmentMenu.BUTTON_WIND_BURST_3, 0));
	}

	@Test
	void aNonSelectionButtonLeavesTheSelectionAlone() {
		assertEquals(2, DragonMaceEnchantmentMenu.windBurstAfter(
				DragonMaceEnchantmentMenu.BUTTON_APPLY, 2));
		assertEquals(DragonMaceConfig.MainEnchantment.DENSITY.ordinal(),
				DragonMaceEnchantmentMenu.mainAfter(DragonMaceEnchantmentMenu.BUTTON_INFO,
						DragonMaceConfig.MainEnchantment.DENSITY.ordinal()));
	}

	@Test
	void breachAndDensityAreMutuallyExclusive() {
		int density = DragonMaceConfig.MainEnchantment.DENSITY.ordinal();
		int breach = DragonMaceEnchantmentMenu.mainAfter(DragonMaceEnchantmentMenu.BUTTON_BREACH,
				density);

		assertEquals(DragonMaceConfig.MainEnchantment.BREACH.ordinal(), breach);

		int back = DragonMaceEnchantmentMenu.mainAfter(DragonMaceEnchantmentMenu.BUTTON_DENSITY, breach);
		assertEquals(density, back);
	}

	@Test
	void selectingDoesNotChangeTheSavedConfiguration() {
		DragonMaceConfig before = DragonMaceConfig.defaults();
		DragonMaceConfig.set(before);

		DragonMaceEnchantmentMenu.windBurstAfter(DragonMaceEnchantmentMenu.BUTTON_WIND_BURST_0,
				before.windBurstLevel());
		DragonMaceEnchantmentMenu.mainAfter(DragonMaceEnchantmentMenu.BUTTON_BREACH,
				before.mainEnchantment().ordinal());

		DragonMaceConfig after = DragonMaceConfig.get();
		assertEquals(before.windBurstLevel(), after.windBurstLevel());
		assertEquals(before.mainEnchantment(), after.mainEnchantment());
	}

	@Test
	void confirmSavesTheSelectionAndStartsTheCooldown() {
		DragonMaceConfig.set(DragonMaceConfig.defaults());
		long now = 1_000_000L;

		DragonMaceConfig next = DragonMaceConfig.get().withWindBurstLevel(1)
				.withMainEnchantment(DragonMaceConfig.MainEnchantment.BREACH)
				.withLastApply(now);

		DragonMaceConfig.set(next);

		DragonMaceConfig saved = DragonMaceConfig.get();
		assertEquals(1, saved.windBurstLevel());
		assertEquals(DragonMaceConfig.MainEnchantment.BREACH, saved.mainEnchantment());
		assertFalse(saved.canApply(now));
		assertTrue(saved.canApply(now + DragonMaceConfig.APPLY_COOLDOWN_MILLIS));
	}

	@Test
	void cancelLeavesTheConfigurationUntouched() {
		DragonMaceConfig.set(DragonMaceConfig.defaults());
		DragonMaceConfig before = DragonMaceConfig.get();

		// Cancel never writes: the pending selection is simply dropped with the menu.
		assertEquals(before.windBurstLevel(), DragonMaceConfig.get().windBurstLevel());
		assertEquals(before.mainEnchantment(), DragonMaceConfig.get().mainEnchantment());
		assertEquals(0L, DragonMaceConfig.get().lastApplyEpochMillis());
	}

	@Test
	void infoIsHoverOnlyAndNeverChangesTheConfiguration() {
		DragonMaceConfig.set(DragonMaceConfig.defaults());
		DragonMaceConfig before = DragonMaceConfig.get();

		assertEquals(DragonMaceEnchantmentMenu.BUTTON_INFO,
				buttonIdFor(DragonMaceEnchantmentLayout.SLOT_INFO));
		assertEquals(before.windBurstLevel(), DragonMaceConfig.get().windBurstLevel());
		assertEquals(before.lastApplyEpochMillis(), DragonMaceConfig.get().lastApplyEpochMillis());
	}

	// ----------------------------------------------------------- cooldown

	@Test
	void theCooldownIsGlobalAndSurvivesARestart() {
		long now = 5_000_000L;
		DragonMaceConfig applied = DragonMaceConfig.defaults().withLastApply(now);

		// A restart re-reads the file, so the cooldown cannot be bypassed.
		DragonMaceConfig reloaded = DragonMaceConfig.parse(DragonMaceConfig.defaultJson()
				.replace("\"lastApplyEpochMillis\": 0", "\"lastApplyEpochMillis\": " + now));

		assertEquals(now, reloaded.lastApplyEpochMillis());
		assertFalse(reloaded.canApply(now + 1_000L));
		assertEquals(DragonMaceConfig.APPLY_COOLDOWN_MILLIS - 1_000L,
				reloaded.remainingCooldownMillis(now + 1_000L));
		assertTrue(reloaded.canApply(now + DragonMaceConfig.APPLY_COOLDOWN_MILLIS));
		assertEquals(now, applied.lastApplyEpochMillis());
	}

	@Test
	void theCooldownIsTwoMinutes() {
		assertEquals(120_000L, DragonMaceConfig.APPLY_COOLDOWN_MILLIS);
	}

	// -------------------------------------------------------- integration

	@Test
	void confirmReconcilesEligibleMacesImmediately() {
		DragonMaceConfig config = DragonMaceConfig.defaults().withWindBurstLevel(2)
				.withMainEnchantment(DragonMaceConfig.MainEnchantment.BREACH);
		SimpleContainer inventory = new SimpleContainer(3);
		inventory.setItem(0, new ItemStack(Items.MACE));
		inventory.setItem(1, new ItemStack(Items.MACE));
		inventory.setItem(2, new ItemStack(Items.IRON_SWORD));

		int changed = DragonMaceEnchantments.reconcileContainer(inventory, true, registries, config);

		assertEquals(2, changed);
		assertEquals(2, levelOf(inventory.getItem(0).getEnchantments(), Enchantments.WIND_BURST));
		assertEquals(4, levelOf(inventory.getItem(0).getEnchantments(), Enchantments.BREACH));
		assertTrue(inventory.getItem(2).getEnchantments().isEmpty());
	}

	@Test
	void aFutureMaceReceivesTheActiveConfiguration() {
		DragonMaceConfig config = DragonMaceConfig.defaults().withWindBurstLevel(3)
				.withMainEnchantment(DragonMaceConfig.MainEnchantment.DENSITY);
		ItemStack fresh = new ItemStack(Items.MACE);

		assertTrue(DragonMaceEnchantments.reconcile(fresh, true, registries, config));
		assertEquals(3, levelOf(fresh.getEnchantments(), Enchantments.WIND_BURST));
		assertEquals(5, levelOf(fresh.getEnchantments(), Enchantments.DENSITY));
	}

	@Test
	void anUnauthorizedPlayerCannotUseTheMenu() {
		assertFalse(DragonMaceEnchantmentMenu.isAuthorized(null));
	}

	// ------------------------------------------------------------- guards

	@Test
	void everyDisplaySlotRefusesPickupAndPlacement() {
		SimpleContainer container = new SimpleContainer(DragonMaceEnchantmentLayout.SLOT_COUNT);
		container.setItem(21, new ItemStack(Items.LIME_DYE));

		for (int slot = 0; slot < DragonMaceEnchantmentLayout.SLOT_COUNT; slot++) {
			DragonMaceEnchantmentMenu.DisplaySlot display = new DragonMaceEnchantmentMenu.DisplaySlot(
					container, slot, 0, 0);

			assertFalse(display.mayPickup(null), "slot " + slot + " must refuse pickup");
			assertFalse(display.mayPlace(new ItemStack(Items.MACE)),
					"slot " + slot + " must refuse placement");
		}
	}

	@Test
	void aDisplaySlotIgnoresClientDrivenWrites() {
		SimpleContainer container = new SimpleContainer(DragonMaceEnchantmentLayout.SLOT_COUNT);
		container.setItem(21, new ItemStack(Items.LIME_DYE));
		DragonMaceEnchantmentMenu.DisplaySlot display = new DragonMaceEnchantmentMenu.DisplaySlot(
				container, 21, 0, 0);

		display.setByPlayer(new ItemStack(Items.DIAMOND));
		display.setByPlayer(new ItemStack(Items.DIAMOND), new ItemStack(Items.LIME_DYE));

		assertTrue(container.getItem(21).is(Items.LIME_DYE));
	}

	@Test
	void everyActionSlotMapsToItsButton() {
		assertEquals(DragonMaceEnchantmentMenu.BUTTON_WIND_BURST_0,
				buttonIdFor(DragonMaceEnchantmentLayout.SLOT_WIND_BURST_0));
		assertEquals(DragonMaceEnchantmentMenu.BUTTON_WIND_BURST_3,
				buttonIdFor(DragonMaceEnchantmentLayout.SLOT_WIND_BURST_3));
		assertEquals(DragonMaceEnchantmentMenu.BUTTON_BREACH,
				buttonIdFor(DragonMaceEnchantmentLayout.SLOT_BREACH));
		assertEquals(DragonMaceEnchantmentMenu.BUTTON_DENSITY,
				buttonIdFor(DragonMaceEnchantmentLayout.SLOT_DENSITY));
		assertEquals(DragonMaceEnchantmentMenu.BUTTON_APPLY,
				buttonIdFor(DragonMaceEnchantmentLayout.SLOT_CONFIRM));
		assertEquals(DragonMaceEnchantmentMenu.BUTTON_INFO,
				buttonIdFor(DragonMaceEnchantmentLayout.SLOT_INFO));
		assertEquals(DragonMaceEnchantmentMenu.BUTTON_CANCEL,
				buttonIdFor(DragonMaceEnchantmentLayout.SLOT_CANCEL));
		assertEquals(-1, buttonIdFor(0));
		assertEquals(-1, buttonIdFor(13));
	}

	// ------------------------------------------------------- confirmation

	@Test
	void confirmAppliesWhenTheSelectionIsLegalAndNoCooldownIsActive() {
		DragonMaceConfig config = DragonMaceConfig.defaults();

		assertEquals(DragonMaceEnchantmentMenu.ApplyResult.APPLIED,
				DragonMaceEnchantmentMenu.decideApply(config, 2,
						DragonMaceConfig.MainEnchantment.BREACH.ordinal(), 1_000_000L));
	}

	@Test
	void confirmIsRefusedWhileTheCooldownIsActive() {
		long now = 2_000_000L;
		DragonMaceConfig config = DragonMaceConfig.defaults().withLastApply(now);

		assertEquals(DragonMaceEnchantmentMenu.ApplyResult.COOLDOWN,
				DragonMaceEnchantmentMenu.decideApply(config, 2,
						DragonMaceConfig.MainEnchantment.DENSITY.ordinal(), now + 1_000L));
	}

	@Test
	void anIllegalSelectionIsRejectedBeforeTheCooldownIsChecked() {
		DragonMaceConfig config = DragonMaceConfig.defaults().withLastApply(9_000_000L);

		assertEquals(DragonMaceEnchantmentMenu.ApplyResult.INVALID,
				DragonMaceEnchantmentMenu.decideApply(config, 4, 0, 1_000L));
		assertEquals(DragonMaceEnchantmentMenu.ApplyResult.INVALID,
				DragonMaceEnchantmentMenu.decideApply(config, -1, 0, 1_000L));
		assertEquals(DragonMaceEnchantmentMenu.ApplyResult.INVALID,
				DragonMaceEnchantmentMenu.decideApply(config, 0, 2, 1_000L));
		assertEquals(DragonMaceEnchantmentMenu.ApplyResult.INVALID,
				DragonMaceEnchantmentMenu.decideApply(null, 0, 0, 1_000L));
	}

	@Test
	void aRefusedConfirmLeavesTheConfigurationUntouched() {
		long now = 3_000_000L;
		DragonMaceConfig before = DragonMaceConfig.defaults().withLastApply(now);
		DragonMaceConfig.set(before);

		assertEquals(DragonMaceEnchantmentMenu.ApplyResult.COOLDOWN,
				DragonMaceEnchantmentMenu.decideApply(DragonMaceConfig.get(), 0, 0, now + 1_000L));

		assertEquals(before.windBurstLevel(), DragonMaceConfig.get().windBurstLevel());
		assertEquals(before.mainEnchantment(), DragonMaceConfig.get().mainEnchantment());
		assertEquals(now, DragonMaceConfig.get().lastApplyEpochMillis());
	}

	// -------------------------------------------------------------- anvil

	@Test
	void anAnvilResultSlotIsNotTreatedAsStorage() {
		assertTrue(DragonMaceProtection.isAllowedContainer(new ResultContainer()));
		assertFalse(DragonMaceProtection.blocksStorage(new ResultContainer(),
				new ItemStack(Items.DRAGON_EGG)));
	}

	@Test
	void aChestIsStillTreatedAsStorage() {
		assertFalse(DragonMaceProtection.isAllowedContainer(new SimpleContainer(27)));
		assertTrue(DragonMaceProtection.blocksStorage(new SimpleContainer(27),
				new ItemStack(Items.DRAGON_EGG)));
	}

	@Test
	void aPlainContainerStillRefusesProtectedStacks() {
		SimpleContainer input = new SimpleContainer(1);

		assertTrue(DragonMaceProtection.blocksStorage(input, new ItemStack(Items.DRAGON_EGG)),
				"a plain container must still refuse a protected stack");
		assertFalse(DragonMaceProtection.isAllowedContainer(input));
	}

	// ------------------------------------------------------------ helpers

	private static int buttonIdFor(int slot) {
		return DragonMaceEnchantmentMenu.buttonIdFor(slot);
	}

	private static String name(ItemStack stack) {
		Component name = stack.get(DataComponents.CUSTOM_NAME);
		return name == null ? null : name.getString();
	}

	private static String loreText(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);

		if (lore == null) {
			return "";
		}

		StringBuilder builder = new StringBuilder();

		for (Component line : lore.lines()) {
			builder.append(line.getString()).append('\n');
		}

		return builder.toString();
	}

	private static int levelOf(ItemEnchantments enchantments, ResourceKey<Enchantment> key) {
		for (var entry : enchantments.entrySet()) {
			if (entry.getKey().is(key)) {
				return entry.getIntValue();
			}
		}

		return 0;
	}

	static RegistryAccess enchantmentRegistries() {
		MappedRegistry<Enchantment> registry = new MappedRegistry<>(Registries.ENCHANTMENT,
				Lifecycle.stable());
		HolderSet<Item> supported = HolderSet.direct(BuiltInRegistries.ITEM.wrapAsHolder(Items.MACE));

		register(registry, Enchantments.DENSITY, supported);
		register(registry, Enchantments.WIND_BURST, supported);
		register(registry, Enchantments.BREACH, supported);
		registry.freeze();

		return new TestRegistryAccess(registry);
	}

	private static void register(MappedRegistry<Enchantment> registry, ResourceKey<Enchantment> key,
			HolderSet<Item> supported) {
		Enchantment.EnchantmentDefinition definition = Enchantment.definition(supported, 5, 5,
				Enchantment.constantCost(1), Enchantment.constantCost(51), 4, EquipmentSlotGroup.MAINHAND);

		registry.register(key, new Enchantment(Component.literal(key.toString()), definition,
				HolderSet.empty(), net.minecraft.core.component.DataComponentMap.EMPTY),
				RegistrationInfo.BUILT_IN);
	}

	/** Minimal {@link RegistryAccess} exposing one registry. */
	private static final class TestRegistryAccess implements RegistryAccess {
		private final Registry<Enchantment> enchantments;

		private TestRegistryAccess(Registry<Enchantment> enchantments) {
			this.enchantments = enchantments;
		}

		@Override
		@SuppressWarnings("unchecked")
		public <E> Registry<E> lookupOrThrow(ResourceKey<? extends Registry<? extends E>> key) {
			if (Registries.ENCHANTMENT.equals(key)) {
				return (Registry<E>) this.enchantments;
			}

			throw new IllegalStateException("Unexpected registry " + key);
		}

		@Override
		@SuppressWarnings("unchecked")
		public <E> Optional<Registry<E>> lookup(ResourceKey<? extends Registry<? extends E>> key) {
			return Registries.ENCHANTMENT.equals(key) ? Optional.of((Registry<E>) this.enchantments)
					: Optional.empty();
		}

		@Override
		public Stream<RegistryEntry<?>> registries() {
			return Stream.empty();
		}

		@Override
		public Stream<ResourceKey<? extends Registry<?>>> listRegistryKeys() {
			return Stream.of(Registries.ENCHANTMENT);
		}
	}
}
