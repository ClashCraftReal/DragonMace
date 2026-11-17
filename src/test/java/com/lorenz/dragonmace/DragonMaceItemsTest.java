package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DragonMaceItemsTest {
	@BeforeAll
	static void setUp() {
		SharedConstants.tryDetectVersion();
		TestBootstrap.boot();
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

	@Test
	void veryHeavyCoreIsPaperWithTheCustomIdentifier() {
		ItemStack core = DragonMaceItems.createVeryHeavyCoreStack();

		assertTrue(core.is(Items.PAPER), "the material must be minecraft:paper");
		assertEquals("dragon_mace:very_heavy_core", DragonMaceItems.VERY_HEAVY_CORE_ID.toString());
		assertEquals("Very Heavy Core", core.getHoverName().getString());
		assertTrue(DragonMaceIdentity.isVeryHeavyCore(core));
	}

	@Test
	void veryHeavyCoreUsesTheVanillaHeavyCoreModel() {
		ItemStack core = DragonMaceItems.createVeryHeavyCoreStack();

		assertEquals(Identifier.withDefaultNamespace("heavy_core"),
				core.get(DataComponents.ITEM_MODEL));
	}

	@Test
	void ordinaryPaperIsNeverAVeryHeavyCore() {
		assertFalse(DragonMaceIdentity.isVeryHeavyCore(new ItemStack(Items.PAPER)));
		assertFalse(DragonMaceIdentity.isVeryHeavyCore(ItemStack.EMPTY));
	}

	@Test
	void paperWithOnlyTheMarkerIsNotAVeryHeavyCore() {
		ItemStack paper = new ItemStack(Items.PAPER);
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(DragonMaceIdentity.VERY_HEAVY_CORE_KEY, true);
		paper.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

		assertFalse(DragonMaceIdentity.isVeryHeavyCore(paper),
				"the custom identifier must also be present");
	}

	@Test
	void paperWithOnlyTheIdentifierIsNotAVeryHeavyCore() {
		ItemStack paper = new ItemStack(Items.PAPER);
		CompoundTag tag = new CompoundTag();
		tag.putString(DragonMaceIdentity.VERY_HEAVY_CORE_ID_KEY, "dragon_mace:very_heavy_core");
		paper.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

		assertFalse(DragonMaceIdentity.isVeryHeavyCore(paper),
				"the marker must also be present");
	}

	@Test
	void aRenamedPaperStackIsNotAVeryHeavyCore() {
		ItemStack paper = new ItemStack(Items.PAPER);
		paper.set(DataComponents.CUSTOM_NAME, Component.literal("Very Heavy Core"));

		assertFalse(DragonMaceIdentity.isVeryHeavyCore(paper),
				"identity must come from custom data, not from the display name");
	}

	@Test
	void veryHeavyCoreIsNotABlockItemSoItCannotBePlaced() {
		assertFalse(DragonMaceItems.createVeryHeavyCoreStack().getItem() instanceof BlockItem);
	}

	@Test
	void veryHeavyCoreIdentitySurvivesACopy() {
		ItemStack core = DragonMaceItems.createVeryHeavyCoreStack();
		ItemStack copy = core.copy();

		assertTrue(DragonMaceIdentity.isVeryHeavyCore(copy),
				"identity must survive dropping, storing and transferring");
		assertEquals(core.get(DataComponents.ITEM_MODEL), copy.get(DataComponents.ITEM_MODEL));
		assertEquals(core.getHoverName().getString(), copy.getHoverName().getString());
	}

	@Test
	void applyingTheCoreIdentityTwiceIsANoOp() {
		ItemStack core = new ItemStack(Items.PAPER);

		assertTrue(DragonMaceItems.applyVeryHeavyCoreIdentity(core));
		assertFalse(DragonMaceItems.applyVeryHeavyCoreIdentity(core));
	}

	@Test
	void veryHeavyCoreLoreMatchesTheDesignLineForLine() {
		List<Component> lore = DragonMaceItems.veryHeavyCoreLore();

		assertEquals(19, lore.size());
		assertEquals("Consumable", lore.get(0).getString());
		assertEquals("", lore.get(1).getString());
		assertEquals("Curse of the Dragons (\u221E):", lore.get(4).getString());
		assertEquals("Dragons Wrath: Convert maces in your inventory to the Draconic Mace",
				lore.get(7).getString());
		assertEquals("Dragons Aura: Gain permanent glowing and a dragon Aura.", lore.get(8).getString());
		assertEquals("Dragons Pride: You may carry and use up to two Totems of Undying.",
				lore.get(9).getString());
		assertEquals("Curse transfers to killer on death.", lore.get(10).getString());
		assertEquals("Right-click to consume", lore.get(16).getString());
		assertEquals("LEGNDARY CONSUMABLE", lore.get(18).getString());
	}

	@Test
	void veryHeavyCoreLoreIsAppliedToTheStack() {
		String text = loreText(DragonMaceItems.createVeryHeavyCoreStack());

		assertTrue(text.contains("LEGNDARY CONSUMABLE"), "the intentional spelling must be preserved");
		assertTrue(text.contains("\u221E"), "the infinity glyph must be preserved");
		assertTrue(text.contains("Right-click to consume"));
	}

	@Test
	void draconicMaceKeepsThePreservedNameAndGainsEpicRarity() {
		ItemStack mace = new ItemStack(Items.MACE);
		ItemStack draconic = DragonMaceItems.createDraconicMaceStack(mace, null);

		assertTrue(draconic.is(Items.MACE));
		assertTrue(DragonMaceIdentity.isDraconicMace(draconic));
		assertEquals("Draconic Mace", draconic.getHoverName().getString());
		assertEquals(23, DragonMaceItems.draconicMaceLore().size());
	}

	@Test
	void draconicMacePreservesDamage() {
		ItemStack mace = new ItemStack(Items.MACE);
		mace.set(DataComponents.DAMAGE, 42);

		ItemStack draconic = DragonMaceItems.createDraconicMaceStack(mace, null);

		assertEquals(42, draconic.get(DataComponents.DAMAGE));
	}

	@Test
	void draconicMaceKeepsAnExistingDragonMaceMarker() {
		ItemStack unique = DragonMaceRecipes.createDragonMaceStack();
		ItemStack draconic = DragonMaceItems.createDraconicMaceStack(unique, null);

		assertTrue(DragonMaceIdentity.isDragonMace(draconic), "the unique mace marker must survive");
		assertTrue(DragonMaceIdentity.isDraconicMace(draconic));
	}

	@Test
	void revertingADraconicMaceRestoresAPlainMace() {
		ItemStack draconic = DragonMaceItems.createDraconicMaceStack(new ItemStack(Items.MACE), null);
		ItemStack reverted = DragonMaceItems.revertDraconicMace(draconic);

		assertTrue(reverted.is(Items.MACE));
		assertFalse(DragonMaceIdentity.isDraconicMace(reverted));
		assertNull(reverted.get(DataComponents.LORE));
		assertNull(reverted.get(DataComponents.RARITY));
		assertTrue(DragonMaceIdentity.isRevertedDraconicMace(reverted));
		assertNull(reverted.get(DataComponents.CUSTOM_NAME));
		assertEquals("Mace", reverted.getHoverName().getString(),
				"the reverted mace must use the vanilla name");
	}

	@Test
	void revertingKeepsTheUniqueDragonMaceIntact() {
		ItemStack unique = DragonMaceRecipes.createDragonMaceStack();
		ItemStack draconic = DragonMaceItems.createDraconicMaceStack(unique, null);
		ItemStack reverted = DragonMaceItems.revertDraconicMace(draconic);

		assertTrue(DragonMaceIdentity.isDragonMace(reverted), "the unique mace must not be destroyed");
		assertFalse(DragonMaceIdentity.isDraconicMace(reverted));
		assertEquals(DragonMaceRecipes.DRAGON_MACE_NAME, reverted.getHoverName().getString());
	}

	@Test
	void revertingPreservesDamage() {
		ItemStack mace = new ItemStack(Items.MACE);
		mace.set(DataComponents.DAMAGE, 17);

		ItemStack reverted = DragonMaceItems.revertDraconicMace(
				DragonMaceItems.createDraconicMaceStack(mace, null));

		assertEquals(17, reverted.get(DataComponents.DAMAGE));
	}

	@Test
	void dragonEggIsRecognisedByItem() {
		assertTrue(DragonMaceIdentity.isDragonEgg(new ItemStack(Items.DRAGON_EGG)));
		assertFalse(DragonMaceIdentity.isDragonEgg(new ItemStack(Items.MACE)));
		assertFalse(DragonMaceIdentity.isDragonEgg(ItemStack.EMPTY));
	}

	@Test
	void dragonEggIsProtectedLikeTheMace() {
		assertTrue(DragonMaceProtection.isProtectedStack(new ItemStack(Items.DRAGON_EGG)));
		assertTrue(DragonMaceProtection.isProtectedStack(DragonMaceRecipes.createDragonMaceStack()));
		assertFalse(DragonMaceProtection.isProtectedStack(new ItemStack(Items.MACE)));
	}

	@Test
	void placeholderEnchantmentsAreSkippedWithoutRegistries() {
		ItemStack draconic = DragonMaceItems.createDraconicMaceStack(new ItemStack(Items.MACE), null);

		assertNotNull(draconic);
		assertFalse(DragonMaceItems.applyPlaceholderEnchantments(draconic, null));
	}
}
