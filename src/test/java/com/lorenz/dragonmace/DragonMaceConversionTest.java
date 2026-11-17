package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import com.mojang.serialization.Lifecycle;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for Dragons Wrath inventory membership and the Draconic Mace design.
 *
 * <p>The conversion is exercised against real {@link SimpleContainer}s, which is the same
 * {@code Container} contract a player inventory implements, so the slot-level behaviour is covered
 * without a live player. The presentation tests read the components back off a built stack.
 */
class DragonMaceConversionTest {
	private static RegistryAccess registries;

	@BeforeAll
	static void setUp() {
		TestBootstrap.boot();
		registries = enchantmentRegistries();
	}

	/**
	 * A registry access carrying just the two enchantments the design uses.
	 *
	 * <p>Enchantments are a datapack registry in 1.21.11, so they are not in {@code BuiltInRegistries}
	 * and a unit test has no datapack to load. Registering the two real keys with real
	 * {@link Enchantment} instances is enough for the component to be written and read back.
	 */
	private static RegistryAccess enchantmentRegistries() {
		MappedRegistry<Enchantment> registry = new MappedRegistry<>(Registries.ENCHANTMENT, Lifecycle.stable());
		HolderSet<Item> supported = HolderSet.direct(BuiltInRegistries.ITEM.wrapAsHolder(Items.MACE));

		register(registry, Enchantments.DENSITY, supported);
		register(registry, Enchantments.WIND_BURST, supported);
		registry.freeze();

		return new TestRegistryAccess(registry);
	}

	private static void register(MappedRegistry<Enchantment> registry, ResourceKey<Enchantment> key,
			HolderSet<Item> supported) {
		Enchantment.EnchantmentDefinition definition = Enchantment.definition(supported, 5, 5,
				Enchantment.constantCost(1), Enchantment.constantCost(51), 4, EquipmentSlotGroup.MAINHAND);

		registry.register(key, new Enchantment(Component.literal(key.toString()), definition,
				HolderSet.empty(), DataComponentMap.EMPTY), RegistrationInfo.BUILT_IN);
	}

	/** Minimal {@link RegistryAccess} exposing one registry. */
	private static final class TestRegistryAccess implements RegistryAccess {
		private final Registry<Enchantment> enchantments;

		private TestRegistryAccess(Registry<Enchantment> enchantments) {
			this.enchantments = enchantments;
		}

		@Override
		@SuppressWarnings("unchecked")
		public <E> Optional<Registry<E>> lookup(ResourceKey<? extends Registry<? extends E>> key) {
			if (Registries.ENCHANTMENT.equals(key)) {
				return Optional.of((Registry<E>) this.enchantments);
			}

			return Optional.empty();
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

	/** Builds an enchantment component holding one enchantment at one level. */
	private static ItemEnchantments enchantments(ResourceKey<Enchantment> key, int level) {
		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		mutable.set(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key), level);
		return mutable.toImmutable();
	}

	private static ItemStack mace() {		return new ItemStack(Items.MACE);
	}

	private static ItemStack draconic() {
		return DragonMaceItems.createDraconicMaceStack(mace(), registries);
	}

	// ------------------------------------------------------- conversion on entry

	@Test
	void aNormalMaceEnteringTheInventoryIsConverted() {
		SimpleContainer inventory = new SimpleContainer(36);
		inventory.setItem(0, mace());

		assertEquals(1, DragonMaceMaceConversion.convertContainer(inventory, registries));
		assertTrue(DragonMaceIdentity.isDraconicMace(inventory.getItem(0)));
	}

	@Test
	void multipleMacesInTheSameInventoryAreAllConverted() {
		SimpleContainer inventory = new SimpleContainer(36);
		inventory.setItem(0, mace());
		inventory.setItem(5, mace());
		inventory.setItem(35, mace());
		inventory.setItem(7, new ItemStack(Items.STICK));

		assertEquals(3, DragonMaceMaceConversion.convertContainer(inventory, registries));
		assertTrue(DragonMaceIdentity.isDraconicMace(inventory.getItem(0)));
		assertTrue(DragonMaceIdentity.isDraconicMace(inventory.getItem(5)));
		assertTrue(DragonMaceIdentity.isDraconicMace(inventory.getItem(35)));
		assertTrue(inventory.getItem(7).is(Items.STICK), "unrelated items must be untouched");
	}

	@Test
	void anAlreadyDraconicMaceIsNotConvertedTwice() {
		SimpleContainer inventory = new SimpleContainer(36);
		inventory.setItem(0, draconic());

		assertEquals(0, DragonMaceMaceConversion.convertContainer(inventory, registries));
		assertTrue(DragonMaceIdentity.isDraconicMace(inventory.getItem(0)));
	}

	@Test
	void conversionIsIdempotentAcrossRepeatedSweeps() {
		SimpleContainer inventory = new SimpleContainer(36);
		inventory.setItem(0, mace());

		assertEquals(1, DragonMaceMaceConversion.convertContainer(inventory, registries));
		assertEquals(0, DragonMaceMaceConversion.convertContainer(inventory, registries));
		assertEquals(1, inventory.getItem(0).getCount());
	}

	@Test
	void conversionPreservesDamageAndCount() {
		ItemStack damaged = mace();
		damaged.set(DataComponents.DAMAGE, 33);
		damaged.setCount(1);

		SimpleContainer inventory = new SimpleContainer(36);
		inventory.setItem(0, damaged);

		DragonMaceMaceConversion.convertContainer(inventory, registries);

		assertEquals(33, inventory.getItem(0).get(DataComponents.DAMAGE));
		assertEquals(1, inventory.getItem(0).getCount());
	}

	// ---------------------------------------------------- staying in the inventory

	@Test
	void aMaceMovedBetweenInventorySlotsStaysDraconic() {
		SimpleContainer inventory = new SimpleContainer(36);
		inventory.setItem(0, mace());
		DragonMaceMaceConversion.convertContainer(inventory, registries);

		ItemStack moved = inventory.getItem(0);
		inventory.setItem(0, ItemStack.EMPTY);
		inventory.setItem(20, moved);

		assertEquals(0, DragonMaceMaceConversion.convertContainer(inventory, registries),
				"a move inside the inventory must not reconvert");
		assertTrue(DragonMaceIdentity.isDraconicMace(inventory.getItem(20)),
				"the mace must stay Draconic while it is still in the inventory");
	}

	// ------------------------------------------------------- reversion on leaving

	@Test
	void aDraconicMaceLeavingThroughADropIsReverted() {
		ItemStack leaving = DragonMaceMaceConversion.revertLeaving(draconic());

		assertTrue(leaving.is(Items.MACE));
		assertFalse(DragonMaceIdentity.isDraconicMace(leaving));
		assertNull(leaving.get(DataComponents.LORE));
		assertNull(leaving.get(DataComponents.CUSTOM_NAME),
				"a reverted mace carries no custom name at all");
		assertEquals("Mace", leaving.getHoverName().getString());
	}

	@Test
	void aNormalMaceLeavingIsReturnedUnchanged() {
		ItemStack normal = mace();

		assertSame(normal, DragonMaceMaceConversion.revertLeaving(normal));
	}

	@Test
	void aDraconicMaceTransferredIntoAChestIsReverted() {
		SimpleContainer chest = new SimpleContainer(27);
		chest.setItem(3, DragonMaceMaceConversion.revertLeaving(draconic()));

		assertFalse(DragonMaceIdentity.isDraconicMace(chest.getItem(3)));
		assertEquals(1, chest.getItem(3).getCount(), "the transfer must not lose the item");
	}

	@Test
	void aMaceReturningToTheHolderIsConvertedAgain() {
		ItemStack returned = DragonMaceMaceConversion.revertLeaving(draconic());

		SimpleContainer inventory = new SimpleContainer(36);
		inventory.setItem(0, returned);

		assertEquals(1, DragonMaceMaceConversion.convertContainer(inventory, registries));
		assertTrue(DragonMaceIdentity.isDraconicMace(inventory.getItem(0)));
	}

	@Test
	void repeatedConversionAndReversionNeverDuplicatesOrLoses() {
		ItemStack stack = mace();

		for (int cycle = 0; cycle < 5; cycle++) {
			stack = DragonMaceItems.createDraconicMaceStack(stack, registries);
			assertEquals(1, stack.getCount());
			stack = DragonMaceItems.revertDraconicMace(stack);
			assertEquals(1, stack.getCount());
			assertTrue(stack.is(Items.MACE));
		}

		assertFalse(DragonMaceIdentity.isDraconicMace(stack));
		assertTrue(DragonMaceIdentity.isRevertedDraconicMace(stack),
				"the reverted marker is the only custom data a reverted mace keeps");
	}

	// ------------------------------------------------------------ curse removal

	@Test
	void curseRemovalRevertsEveryDraconicMaceInTheInventory() {
		SimpleContainer inventory = new SimpleContainer(36);
		inventory.setItem(0, mace());
		inventory.setItem(9, mace());
		inventory.setItem(18, mace());
		DragonMaceMaceConversion.convertContainer(inventory, registries);

		assertEquals(3, DragonMaceMaceConversion.revertContainer(inventory));

		for (int slot : new int[] {0, 9, 18}) {
			assertFalse(DragonMaceIdentity.isDraconicMace(inventory.getItem(slot)));
			assertEquals(1, inventory.getItem(slot).getCount());
		}
	}

	@Test
	void reversionLeavesAnotherPlayersItemsAlone() {
		SimpleContainer otherInventory = new SimpleContainer(36);
		otherInventory.setItem(0, mace());
		otherInventory.setItem(1, new ItemStack(Items.STICK));

		assertEquals(0, DragonMaceMaceConversion.revertContainer(otherInventory));
		assertTrue(otherInventory.getItem(0).is(Items.MACE));
		assertFalse(DragonMaceIdentity.isDraconicMace(otherInventory.getItem(0)));
		assertTrue(otherInventory.getItem(1).is(Items.STICK));
	}

	@Test
	void reversionGuardsRejectNullAndOutOfRange() {
		SimpleContainer inventory = new SimpleContainer(1);

		assertFalse(DragonMaceMaceConversion.revertSlot(null, 0));
		assertFalse(DragonMaceMaceConversion.revertSlot(inventory, -1));
		assertFalse(DragonMaceMaceConversion.revertSlot(inventory, 1));
		assertEquals(0, DragonMaceMaceConversion.revertContainer(null));
	}

	// ------------------------------------------------------------ name and lore

	@Test
	void theDraconicNameIsExact() {
		assertEquals("Draconic Mace", draconic().getHoverName().getString());
	}

	@Test
	void theDraconicNameCarriesNoDecoration() {
		String name = DragonMaceItems.draconicMaceName().getString();

		assertEquals("Draconic Mace", name, "the displayed name must be exactly the name");
		assertFalse(name.contains("\u272A"), "the decorative stars must be gone: " + name);
		assertFalse(name.contains("\u278E"), "the tier glyph must be gone: " + name);
		assertFalse(name.endsWith(" "), "the separator space must be gone: " + name);
	}

	@Test
	void theDraconicNameRunsCarryTheExactStyles() {
		List<Component> runs = DragonMaceItems.draconicMaceName().getSiblings();

		assertTrue(runs.isEmpty(), "the name is a single run, not a compound");
		assertEquals("Draconic Mace", DragonMaceItems.draconicMaceName().getString());
		assertEquals(ChatFormatting.LIGHT_PURPLE.getColor(),
				DragonMaceItems.draconicMaceName().getStyle().getColor().getValue());
		assertFalse(Boolean.TRUE.equals(DragonMaceItems.draconicMaceName().getStyle().isItalic()),
				"the name must not be italic");
	}

	// ------------------------------------------------------------ delayed conversion

	@Test
	void conversionIsDelayedByOneTick() {
		assertEquals(1, DragonMaceMaceConversion.CONVERSION_DELAY_TICKS);
		assertEquals(101, DragonMaceMaceConversion.dueTick(100));
		assertFalse(DragonMaceMaceConversion.isDue(101, 100), "the same tick must not convert");
		assertTrue(DragonMaceMaceConversion.isDue(101, 101), "the next tick must convert");
		assertTrue(DragonMaceMaceConversion.isDue(101, 102));
	}

	@Test
	void schedulingTheSamePlayerTwiceQueuesOneConversion() {
		DragonMaceMaceConversion.clearPending();
		UUID player = UUID.randomUUID();

		assertTrue(DragonMaceMaceConversion.schedule(player, 100));
		assertFalse(DragonMaceMaceConversion.schedule(player, 100), "a second schedule must be refused");
		assertFalse(DragonMaceMaceConversion.schedule(player, 200), "a later schedule must be refused too");
		assertEquals(1, DragonMaceMaceConversion.pendingConversions());
		assertEquals(101, DragonMaceMaceConversion.dueTick(100));

		DragonMaceMaceConversion.clearPending();
	}

	@Test
	void aDueConversionIsReleasedExactlyOnce() {
		DragonMaceMaceConversion.clearPending();
		UUID player = UUID.randomUUID();
		DragonMaceMaceConversion.schedule(player, 100);

		assertTrue(DragonMaceMaceConversion.takeDue(100).isEmpty(), "not due yet");
		assertEquals(List.of(player), DragonMaceMaceConversion.takeDue(101));
		assertEquals(0, DragonMaceMaceConversion.pendingConversions());
		assertTrue(DragonMaceMaceConversion.takeDue(101).isEmpty(), "released only once");

		DragonMaceMaceConversion.clearPending();
	}

	@Test
	void schedulingWithoutAPlayerIsRefused() {
		assertFalse(DragonMaceMaceConversion.schedule(null, 100));
		assertFalse(DragonMaceMaceConversion.scheduleConversion(null, null));
	}

	@Test
	void theCurseApplyPathSchedulesInsteadOfConvertingImmediately() throws Exception {
		String source = java.nio.file.Files.readString(java.nio.file.Path.of(
				"src/main/java/com/lorenz/dragonmace/DragonMaceCurse.java"));

		assertTrue(source.contains("DragonMaceMaceConversion.scheduleConversion(server, player)"),
				"apply must defer the conversion or a kill's death message shows the Draconic Mace");
		assertFalse(source.contains("DragonMaceMaceConversion.convertInventory("),
				"apply must not convert immediately");
	}

	@Test
	void theServerTickDrivesPendingConversions() throws Exception {
		String source = java.nio.file.Files.readString(java.nio.file.Path.of(
				"src/main/java/com/lorenz/dragonmace/DragonMace.java"));

		assertTrue(source.contains("DragonMaceMaceConversion::tick"),
				"a queued conversion must be driven by the server tick or it never runs");
	}

	@Test
	void theLoreHasTwentyThreeLines() {
		List<Component> lore = DragonMaceItems.draconicMaceLore();

		assertEquals(23, lore.size());
		assertEquals("", lore.get(1).getString());
		assertEquals("", lore.get(3).getString());
		assertEquals("", lore.get(9).getString());
		assertEquals("", lore.get(13).getString());
		assertEquals("", lore.get(17).getString());
		assertEquals("", lore.get(19).getString());
		assertEquals(23, draconic().get(DataComponents.LORE).lines().size());
	}

	@Test
	void theGearScoreLineHasThreeColouredRuns() {
		List<Component> runs = DragonMaceItems.draconicMaceLore().get(0).getSiblings();

		assertEquals(3, runs.size());
		assertEquals("Gear Score: ", runs.get(0).getString());
		assertEquals("9999 ", runs.get(1).getString());
		assertEquals("(9999)", runs.get(2).getString());
	}

	@Test
	void theEnchantmentLineIsBoldGold() {
		Component line = DragonMaceItems.draconicMaceLore().get(2);

		assertEquals("Wind Burst III, Density V", line.getString());
		assertTrue(Boolean.TRUE.equals(line.getStyle().isBold()));
		assertFalse(Boolean.TRUE.equals(line.getStyle().isItalic()));
	}

	@Test
	void theRuneLinesKeepTheirGlyphAndAreNotItalic() {
		List<Component> lore = DragonMaceItems.draconicMaceLore();

		assertEquals("\u25C6 Kill Rune: Draconic", lore.get(14).getString());
		assertEquals("\u25C6 Attack Rune: Draconic", lore.get(15).getString());
		assertEquals("\u25C6 Aura Rune: Draconic", lore.get(16).getString());
		assertFalse(Boolean.TRUE.equals(lore.get(14).getStyle().isItalic()));
		assertFalse(Boolean.TRUE.equals(lore.get(15).getStyle().isItalic()));
		assertFalse(Boolean.TRUE.equals(lore.get(16).getStyle().isItalic()));
	}

	@Test
	void theSoulboundLineIsDarkGray() {
		Component line = DragonMaceItems.draconicMaceLore().get(21);

		assertEquals("*Soulbound*", line.getString());
		assertFalse(Boolean.TRUE.equals(line.getStyle().isItalic()));
	}

	@Test
	void theMythicFooterObfuscatesBothXs() {
		List<Component> runs = DragonMaceItems.draconicMaceLore().get(22).getSiblings();

		assertEquals(3, runs.size());
		assertEquals("X", runs.get(0).getString());
		assertEquals(" Mythic Mace ", runs.get(1).getString());
		assertEquals("X", runs.get(2).getString());
		assertTrue(Boolean.TRUE.equals(runs.get(0).getStyle().isObfuscated()));
		assertTrue(Boolean.TRUE.equals(runs.get(2).getStyle().isObfuscated()));
		assertTrue(Boolean.TRUE.equals(runs.get(1).getStyle().isBold()));
		assertEquals("X Mythic Mace X", DragonMaceItems.draconicMaceLore().get(22).getString());
	}

	// --------------------------------------------------------- item properties

	@Test
	void theDraconicMaceForcesTheGlint() {
		assertEquals(Boolean.TRUE, draconic().get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
	}

	@Test
	void theDraconicMaceIsUnbreakable() {
		assertNotNull(draconic().get(DataComponents.UNBREAKABLE));
	}

	@Test
	void theDraconicMaceHidesTheThreeTooltipComponents() {
		TooltipDisplay display = draconic().get(DataComponents.TOOLTIP_DISPLAY);

		assertNotNull(display);
		assertFalse(display.shows(DataComponents.ATTRIBUTE_MODIFIERS));
		assertFalse(display.shows(DataComponents.ENCHANTMENTS));
		assertFalse(display.shows(DataComponents.UNBREAKABLE));
		assertTrue(display.shows(DataComponents.CUSTOM_NAME));
	}

	@Test
	void theDraconicMaceCarriesDensityFiveAndWindBurstThree() {
		ItemStack stack = draconic();
		Holder<Enchantment> density = registries.lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(Enchantments.DENSITY);
		Holder<Enchantment> windBurst = registries.lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(Enchantments.WIND_BURST);

		assertEquals(5, stack.get(DataComponents.ENCHANTMENTS).getLevel(density));
		assertEquals(3, stack.get(DataComponents.ENCHANTMENTS).getLevel(windBurst));
		assertEquals(2, stack.get(DataComponents.ENCHANTMENTS).keySet().size());
	}

	@Test
	void theDraconicRuneStaysPresentationOnly() {
		ItemStack stack = draconic();

		assertEquals(3, DragonMaceIdentity.copyTag(stack)
				.getIntOr(DragonMaceIdentity.DRACONIC_RUNE_KEY, -1));
		assertEquals(2, stack.get(DataComponents.ENCHANTMENTS).keySet().size(),
				"the rune must not be registered as an enchantment");
	}

	// -------------------------------------------------------------- reversion

	@Test
	void reversionRemovesEveryDesignComponent() {
		ItemStack reverted = DragonMaceItems.revertDraconicMace(draconic());

		assertNull(reverted.get(DataComponents.LORE));
		assertNull(reverted.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
		assertNull(reverted.get(DataComponents.UNBREAKABLE));
		assertNull(reverted.get(DataComponents.TOOLTIP_DISPLAY));
		assertNull(reverted.get(DataComponents.ENCHANTMENTS));
		assertNull(reverted.get(DataComponents.RARITY));
		assertNull(reverted.get(DataComponents.CUSTOM_NAME), "the custom name must be gone");
		assertEquals("Mace", reverted.getHoverName().getString(), "only the vanilla name may remain");
	}

	@Test
	void reversionKeepsLegitimateOriginalData() {
		ItemStack original = mace();
		original.set(DataComponents.DAMAGE, 12);

		ItemStack reverted = DragonMaceItems.revertDraconicMace(
				DragonMaceItems.createDraconicMaceStack(original, registries));

		assertEquals(12, reverted.get(DataComponents.DAMAGE));
		assertTrue(reverted.is(Items.MACE));
	}

	@Test
	void aRevertedMaceRegainsTheFullDesignWhenItReturns() {
		ItemStack reverted = DragonMaceItems.revertDraconicMace(draconic());
		ItemStack again = DragonMaceItems.createDraconicMaceStack(reverted, registries);

		assertEquals("Draconic Mace", again.getHoverName().getString());
		assertEquals(23, again.get(DataComponents.LORE).lines().size());
		assertEquals(Boolean.TRUE, again.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
		assertNotNull(again.get(DataComponents.UNBREAKABLE));
	}

	@Test
	void theLoreComponentMatchesTheBuilder() {
		ItemLore lore = draconic().get(DataComponents.LORE);

		assertEquals(DragonMaceItems.draconicMaceLore(), lore.lines());
	}

	@Test
	void theNameStyleIsNotItalicOnEveryRun() {
		for (Component run : DragonMaceItems.draconicMaceName().getSiblings()) {
			assertFalse(Boolean.TRUE.equals(run.getStyle().isItalic()),
					"run '" + run.getString() + "' must not be italic");
		}
	}

	@Test
	void theGearScoreRunsUseTheSpecifiedColours() {
		List<Component> runs = DragonMaceItems.draconicMaceLore().get(0).getSiblings();

		assertEquals(ChatFormatting.GRAY.getColor(), runs.get(0).getStyle().getColor().getValue());
		assertEquals(ChatFormatting.LIGHT_PURPLE.getColor(), runs.get(1).getStyle().getColor().getValue());
		assertEquals(ChatFormatting.DARK_AQUA.getColor(), runs.get(2).getStyle().getColor().getValue());
	}

	@Test
	void theTierRunIsGone() {
		List<Component> runs = DragonMaceItems.draconicMaceName().getSiblings();

		assertTrue(runs.isEmpty(), "there is no tier run left to colour");
	}

	@Test
	void theEnchantChooserSectionIsPresent() {
		List<Component> lore = DragonMaceItems.draconicMaceLore();

		assertEquals("Ability: Enchant Chooser SNEAK OFFHAND", lore.get(4).getString());
		assertEquals("Allows the user to enchant their mace at will,", lore.get(5).getString());
		assertEquals("using dragon powers.", lore.get(6).getString());
		assertEquals("Opens a GUI to choose enchantments and runes.", lore.get(7).getString());
		assertEquals("Cooldown: 120s", lore.get(8).getString());
	}

	@Test
	void theSmashSectionIsPresent() {
		List<Component> lore = DragonMaceItems.draconicMaceLore();

		assertEquals("Ability: Smash LEFT CLICK", lore.get(10).getString());
		assertEquals("While falling over 1.5 blocks, activate a smash attack.",
				lore.get(11).getString());
		assertEquals("Deals more damage the higher the fall.", lore.get(12).getString());
	}

	@Test
	void theConvertedLinePrecedesSoulbound() {
		List<Component> lore = DragonMaceItems.draconicMaceLore();

		assertEquals("*Converted by Curse of the Dragons*", lore.get(20).getString());
		assertEquals(ChatFormatting.DARK_GRAY.getColor(),
				lore.get(20).getStyle().getColor().getValue());
		assertEquals("*Soulbound*", lore.get(21).getString());
	}

	@Test
	void theSoulboundColourIsDarkGray() {
		Style style = DragonMaceItems.draconicMaceLore().get(21).getStyle();

		assertEquals(ChatFormatting.DARK_GRAY.getColor(), style.getColor().getValue());
	}

	// ------------------------------------------- reverted mace keeps only the name

	@Test
	void aRevertedMaceKeepsOnlyItsName() {
		ItemStack reverted = DragonMaceItems.revertDraconicMace(draconic());

		assertTrue(reverted.is(Items.MACE), "the item must stay a mace");
		assertEquals(1, reverted.getCount(), "the item must not be duplicated or deleted");
		assertNull(reverted.get(DataComponents.CUSTOM_NAME),
				"a reverted mace carries no custom name at all");
		assertEquals("Mace", reverted.getHoverName().getString());
		assertNull(reverted.get(DataComponents.LORE));
		assertNull(reverted.get(DataComponents.ENCHANTMENTS));
		assertNull(reverted.get(DataComponents.STORED_ENCHANTMENTS));
		assertNull(reverted.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
		assertNull(reverted.get(DataComponents.UNBREAKABLE));
		assertNull(reverted.get(DataComponents.TOOLTIP_DISPLAY));
		assertEquals(Items.MACE.components().get(DataComponents.ITEM_MODEL),
				reverted.get(DataComponents.ITEM_MODEL),
				"the vanilla mace model must be restored, not deleted");
		assertNull(reverted.get(DataComponents.RARITY));
	}

	@Test
	void aRevertedMaceIsNoLongerEnchantable() {
		ItemStack reverted = DragonMaceItems.revertDraconicMace(draconic());

		assertNull(reverted.get(DataComponents.ENCHANTABLE),
				"the enchanting table gates on this component");
		assertFalse(reverted.isEnchantable(), "the server must refuse the enchanting table");
	}

	@Test
	void anOrdinaryMaceIsStillEnchantable() {
		assertTrue(mace().isEnchantable(), "ordinary maces must keep working");
	}

	@Test
	void aRevertedMaceIsRecognisedByItsNameAlone() {
		ItemStack reverted = DragonMaceItems.revertDraconicMace(draconic());

		assertTrue(DragonMaceIdentity.isRevertedDraconicMace(reverted));
		assertFalse(DragonMaceIdentity.isDraconicMace(reverted));
	}

	@Test
	void anOrdinaryMaceIsNotARevertedDraconicMace() {
		assertFalse(DragonMaceIdentity.isRevertedDraconicMace(mace()));
		assertFalse(DragonMaceIdentity.isRevertedDraconicMace(ItemStack.EMPTY));
	}

	@Test
	void anUnrelatedNamedMaceIsNotARevertedDraconicMace() {
		ItemStack named = mace();
		named.set(DataComponents.CUSTOM_NAME, Component.literal("My Mace"));

		assertFalse(DragonMaceIdentity.isRevertedDraconicMace(named),
				"an unrelated named mace must not be restricted");
	}

	@Test
	void aLiveDraconicMaceIsNotTreatedAsReverted() {
		assertFalse(DragonMaceIdentity.isRevertedDraconicMace(draconic()));
	}

	@Test
	void aRevertedMaceStillCarriesItsDamage() {
		ItemStack damaged = mace();
		damaged.set(DataComponents.DAMAGE, 21);

		ItemStack reverted = DragonMaceItems.revertDraconicMace(
				DragonMaceItems.createDraconicMaceStack(damaged, registries));

		assertEquals(21, reverted.get(DataComponents.DAMAGE));
		assertTrue(reverted.is(Items.MACE));
	}

	@Test
	void aRevertedMaceIsNotProtectedFromStorage() {
		ItemStack reverted = DragonMaceItems.revertDraconicMace(draconic());

		assertFalse(DragonMaceProtection.isProtectedStack(reverted),
				"a reverted mace must move into chests, shelves and bundles normally");
		assertFalse(DragonMaceProtection.blocksShelfInsert(reverted));
		assertFalse(DragonMaceProtection.blocksBundleInsert(reverted));
	}

	@Test
	void aRevertedMaceRegainsTheDesignWhenItReturns() {
		ItemStack reverted = DragonMaceItems.revertDraconicMace(draconic());
		ItemStack again = DragonMaceItems.createDraconicMaceStack(reverted, registries);

		assertTrue(DragonMaceIdentity.isDraconicMace(again));
		assertEquals(23, again.get(DataComponents.LORE).lines().size());
		assertNotNull(again.get(DataComponents.ENCHANTMENTS));
		assertNotNull(again.get(DataComponents.ENCHANTABLE));
	}

	@Test
	void theUniqueDragonMaceKeepsItsOwnNameOnRevert() {
		ItemStack unique = DragonMaceRecipes.createDragonMaceStack();
		ItemStack reverted = DragonMaceItems.revertDraconicMace(
				DragonMaceItems.createDraconicMaceStack(unique, registries));

		assertTrue(DragonMaceIdentity.isDragonMace(reverted));
		assertEquals(DragonMaceRecipes.DRAGON_MACE_NAME, reverted.getHoverName().getString());
		assertNull(reverted.get(DataComponents.ENCHANTMENTS));
	}

	// ------------------------------------------- dropped mace keeps a usable model

	@Test
	void aRevertedMaceKeepsTheVanillaMaceItemModel() {
		ItemStack reverted = DragonMaceItems.revertDraconicMace(draconic());

		assertEquals(Items.MACE.components().get(DataComponents.ITEM_MODEL),
				reverted.get(DataComponents.ITEM_MODEL),
				"a stack without item_model has no model to resolve and renders invisible");
		assertNotNull(reverted.get(DataComponents.ITEM_MODEL));
	}

	@Test
	void aDroppedMaceCarriesTheSameModelAsAPlainMace() {
		ItemStack dropped = DragonMaceMaceConversion.revertLeaving(draconic());

		assertEquals(new ItemStack(Items.MACE).get(DataComponents.ITEM_MODEL),
				dropped.get(DataComponents.ITEM_MODEL));
		assertTrue(dropped.is(Items.MACE));
	}

	@Test
	void aDroppedMaceKeepsItsCustomName() {
		ItemStack dropped = DragonMaceMaceConversion.revertLeaving(draconic());

		assertNull(dropped.get(DataComponents.CUSTOM_NAME),
				"a dropped mace is an ordinary mace and carries no custom name");
		assertEquals("Mace", dropped.getHoverName().getString());
	}

	// --------------------------------------------------- renaming a reverted mace

	@Test
	void aRevertedMaceCanBeRenamedToAnArbitraryName() {
		ItemStack renamed = DragonMaceItems.revertDraconicMace(draconic());
		renamed.set(DataComponents.CUSTOM_NAME, Component.literal("My Mace"));

		assertEquals("My Mace", renamed.getHoverName().getString());
		assertTrue(renamed.is(Items.MACE));
	}

	@Test
	void aRenamedMaceStaysRevertedAndUnenchantable() {
		ItemStack renamed = DragonMaceItems.revertDraconicMace(draconic());
		renamed.set(DataComponents.CUSTOM_NAME, Component.literal("My Mace"));

		assertTrue(DragonMaceIdentity.isRevertedDraconicMace(renamed),
				"the marker must survive a rename that replaces the name");
		assertFalse(renamed.isEnchantable());
		assertNull(renamed.get(DataComponents.ENCHANTABLE));
	}

	@Test
	void aRenamedMaceReconvertsWhenItReturnsToTheHolder() {
		ItemStack renamed = DragonMaceItems.revertDraconicMace(draconic());
		renamed.set(DataComponents.CUSTOM_NAME, Component.literal("My Mace"));

		SimpleContainer inventory = new SimpleContainer(1);
		inventory.setItem(0, renamed);

		assertEquals(1, DragonMaceMaceConversion.convertContainer(inventory, registries));
		assertTrue(DragonMaceIdentity.isDraconicMace(inventory.getItem(0)));
	}

	@Test
	void reconversionRestoresTheCanonicalName() {
		ItemStack renamed = DragonMaceItems.revertDraconicMace(draconic());
		renamed.set(DataComponents.CUSTOM_NAME, Component.literal("My Mace"));

		ItemStack again = DragonMaceItems.createDraconicMaceStack(renamed, registries);

		assertEquals(DragonMaceItems.draconicMaceName(), again.get(DataComponents.CUSTOM_NAME),
				"the canonical Draconic name must win over any chosen name");
		assertEquals("Draconic Mace",
				again.getHoverName().getString());
	}

	@Test
	void reconversionClearsTheRevertedMarker() {
		ItemStack again = DragonMaceItems.createDraconicMaceStack(
				DragonMaceItems.revertDraconicMace(draconic()), registries);

		assertFalse(DragonMaceIdentity.isRevertedDraconicMace(again));
		assertTrue(DragonMaceIdentity.isDraconicMace(again));
	}

	@Test
	void anOrdinaryMaceIsStillRenamableAndEnchantable() {
		ItemStack ordinary = mace();
		ordinary.set(DataComponents.CUSTOM_NAME, Component.literal("My Mace"));

		assertFalse(DragonMaceIdentity.isRevertedDraconicMace(ordinary));
		assertTrue(ordinary.isEnchantable(), "ordinary maces must keep working");
	}

	// ------------------------------------------------------- anvil decision

	@Test
	void anAnvilRenameIsNotTreatedAsEnchanting() {
		ItemStack input = DragonMaceItems.revertDraconicMace(draconic());
		ItemStack result = input.copy();
		result.set(DataComponents.CUSTOM_NAME, Component.literal("My Mace"));

		assertFalse(DragonMaceItems.addsEnchantments(input, result),
				"a pure rename must be allowed");
	}

	@Test
	void anAnvilEnchantedBookIsRefused() {
		ItemStack input = DragonMaceItems.revertDraconicMace(draconic());
		ItemStack result = input.copy();
		result.set(DataComponents.ENCHANTMENTS, enchantments(Enchantments.DENSITY, 5));

		assertTrue(DragonMaceItems.addsEnchantments(input, result),
				"adding a level must be refused");
	}

	@Test
	void anAnvilMergeThatRaisesALevelIsRefused() {
		ItemStack input = DragonMaceItems.revertDraconicMace(draconic());
		ItemStack result = input.copy();
		result.set(DataComponents.ENCHANTMENTS, enchantments(Enchantments.WIND_BURST, 3));

		assertTrue(DragonMaceItems.addsEnchantments(input, result));
	}

	@Test
	void anAnvilOperationOnAnOrdinaryMaceIsNotRefused() {
		ItemStack input = mace();
		ItemStack result = input.copy();
		result.set(DataComponents.ENCHANTMENTS, enchantments(Enchantments.DENSITY, 5));

		assertFalse(DragonMaceIdentity.isRevertedDraconicMace(input),
				"the guard must not apply to ordinary maces");
	}
}
