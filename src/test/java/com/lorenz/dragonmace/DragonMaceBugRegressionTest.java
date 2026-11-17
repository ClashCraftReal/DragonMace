package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for the three reported bugs.
 *
 * <p>Each test names the bug it locks down, so a future change that reintroduces the defect fails
 * here instead of in game.
 */
class DragonMaceBugRegressionTest {
	@BeforeAll
	static void setUp() {
		TestBootstrap.boot();
	}

	// ---------------------------------------------------------------- bug 1

	/**
	 * Bug 1: the {@code item_model} component must name an item definition, not a block model.
	 *
	 * <p>{@code minecraft:block/heavy_core} resolves to {@code assets/minecraft/items/block/heavy_core
	 * .json}, which does not exist, so the client fell back to the plain paper model.
	 */
	@Test
	void bug1ItemModelNamesTheItemDefinitionNotTheBlockModel() {
		assertEquals(Identifier.withDefaultNamespace("heavy_core"),
				DragonMaceItems.VERY_HEAVY_CORE_MODEL);
		assertEquals("minecraft:heavy_core", DragonMaceItems.VERY_HEAVY_CORE_MODEL.toString());
	}

	@Test
	void bug1ItemModelIsNotABlockModelPath() {
		assertFalse(DragonMaceItems.VERY_HEAVY_CORE_MODEL.getPath().startsWith("block/"),
				"the component takes an item definition id, not a block model id");
	}

	/** The definition the component points at must actually ship in the vanilla jar. */
	@Test
	void bug1TheReferencedItemDefinitionExists() throws Exception {
		String path = "/assets/minecraft/items/" + DragonMaceItems.VERY_HEAVY_CORE_MODEL.getPath()
				+ ".json";

		try (InputStream stream = DragonMaceBugRegressionTest.class.getResourceAsStream(path)) {
			assertNotNull(stream, "the item definition must exist on the classpath at " + path);
			String json = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
			assertTrue(json.contains("minecraft:block/heavy_core"),
					"the definition must resolve to the vanilla heavy core model");
		}
	}

	@Test
	void bug1TheStackCarriesTheItemModelComponent() {
		ItemStack core = DragonMaceItems.createVeryHeavyCoreStack();

		assertEquals(Identifier.withDefaultNamespace("heavy_core"),
				core.get(DataComponents.ITEM_MODEL));
	}

	// ---------------------------------------------------------------- bug 2

	/**
	 * Bug 2: Dragons Wrath must convert every eligible mace in the holder's inventory.
	 *
	 * <p>Driven through {@code convertContainer} against a real {@link SimpleContainer}, so the
	 * identification, iteration and in-place replacement are all exercised.
	 */
	@Test
	void bug2EveryEligibleMaceIsConverted() {
		SimpleContainer container = new SimpleContainer(4);
		container.setItem(0, new ItemStack(Items.MACE));
		container.setItem(1, new ItemStack(Items.MACE));
		container.setItem(2, new ItemStack(Items.STICK));
		container.setItem(3, new ItemStack(Items.MACE));

		int converted = DragonMaceMaceConversion.convertContainer(container, null);

		assertEquals(3, converted, "all three maces must be converted");
		assertTrue(DragonMaceIdentity.isDraconicMace(container.getItem(0)));
		assertTrue(DragonMaceIdentity.isDraconicMace(container.getItem(1)));
		assertTrue(DragonMaceIdentity.isDraconicMace(container.getItem(3)));
	}

	@Test
	void bug2NonMacesAreLeftUntouched() {
		SimpleContainer container = new SimpleContainer(3);
		container.setItem(0, new ItemStack(Items.STICK));
		container.setItem(1, new ItemStack(Items.PAPER));
		container.setItem(2, ItemStack.EMPTY);

		assertEquals(0, DragonMaceMaceConversion.convertContainer(container, null));
		assertTrue(container.getItem(0).is(Items.STICK));
		assertTrue(container.getItem(1).is(Items.PAPER));
		assertTrue(container.getItem(2).isEmpty());
	}

	@Test
	void bug2ConversionDoesNotDuplicateOrDeleteItems() {
		SimpleContainer container = new SimpleContainer(2);
		container.setItem(0, new ItemStack(Items.MACE));
		container.setItem(1, new ItemStack(Items.MACE));

		DragonMaceMaceConversion.convertContainer(container, null);

		assertEquals(2, container.countItem(Items.MACE),
				"the mace count must be unchanged, only the identity differs");
		assertFalse(container.getItem(0).isEmpty());
		assertFalse(container.getItem(1).isEmpty());
	}

	@Test
	void bug2ConversionPreservesDamage() {
		SimpleContainer container = new SimpleContainer(1);
		ItemStack mace = new ItemStack(Items.MACE);
		mace.set(DataComponents.DAMAGE, 33);
		container.setItem(0, mace);

		DragonMaceMaceConversion.convertContainer(container, null);

		assertEquals(33, container.getItem(0).get(DataComponents.DAMAGE));
	}

	@Test
	void bug2ConversionPreservesTheUniqueDragonMaceMarker() {
		SimpleContainer container = new SimpleContainer(1);
		container.setItem(0, DragonMaceRecipes.createDragonMaceStack());

		assertEquals(1, DragonMaceMaceConversion.convertContainer(container, null));

		ItemStack result = container.getItem(0);
		assertTrue(DragonMaceIdentity.isDragonMace(result), "the unique mace marker must survive");
		assertTrue(DragonMaceIdentity.isDraconicMace(result));
	}

	@Test
	void bug2AlreadyConvertedMacesAreNotConvertedTwice() {
		SimpleContainer container = new SimpleContainer(1);
		container.setItem(0, DragonMaceItems.createDraconicMaceStack(new ItemStack(Items.MACE), null));

		assertEquals(0, DragonMaceMaceConversion.convertContainer(container, null));
	}

	@Test
	void bug2ConversionIsIdempotent() {
		SimpleContainer container = new SimpleContainer(1);
		container.setItem(0, new ItemStack(Items.MACE));

		assertEquals(1, DragonMaceMaceConversion.convertContainer(container, null));
		assertEquals(0, DragonMaceMaceConversion.convertContainer(container, null));
		assertTrue(DragonMaceIdentity.isDraconicMace(container.getItem(0)));
	}

	@Test
	void bug2NullContainerIsRefused() {
		assertEquals(0, DragonMaceMaceConversion.convertContainer(null, null));
	}

	/**
	 * Bug 2 timing: a mace that lands in the inventory after the curse was applied is converted by
	 * the single-slot hook, which is what {@code Inventory.setItem} calls.
	 */
	@Test
	void bug2SingleSlotConversionTouchesOnlyThatSlot() {
		SimpleContainer container = new SimpleContainer(3);
		container.setItem(0, new ItemStack(Items.MACE));
		container.setItem(1, new ItemStack(Items.MACE));

		assertTrue(DragonMaceMaceConversion.convertSlot(container, 1, null));

		assertFalse(DragonMaceIdentity.isDraconicMace(container.getItem(0)),
				"the untouched slot must stay a plain mace");
		assertTrue(DragonMaceIdentity.isDraconicMace(container.getItem(1)));
	}

	@Test
	void bug2SingleSlotConversionIgnoresEmptyAndOutOfRangeSlots() {
		SimpleContainer container = new SimpleContainer(1);

		assertFalse(DragonMaceMaceConversion.convertSlot(container, 0, null));
		assertFalse(DragonMaceMaceConversion.convertSlot(container, -1, null));
		assertFalse(DragonMaceMaceConversion.convertSlot(container, 1, null));
		assertFalse(DragonMaceMaceConversion.convertSlot(null, 0, null));
	}

	/** The two inventory entry points the conversion hooks are injected into must exist. */
	@Test
	void bug2InventoryHookTargetsExist() throws Exception {
		assertNotNull(Inventory.class.getMethod("setItem", int.class, ItemStack.class));
		assertNotNull(Inventory.class.getMethod("add", ItemStack.class));
	}

	// ---------------------------------------------------------------- bug 3

	/**
	 * Bug 3: the Dragon Egg must be allowed in a crafting grid.
	 *
	 * <p>Crafting slots are backed by a {@code TransientCraftingContainer}, which is a
	 * {@link CraftingContainer} but not an {@code Inventory}, so the storage guard refused it.
	 */
	@Test
	void bug3CraftingGridIsAnAllowedContainer() {
		assertTrue(DragonMaceProtection.isAllowedContainer(new TestCraftingContainer()),
				"a crafting grid must accept the Dragon Egg");
	}

	@Test
	void bug3DragonEggIsNotBlockedFromACraftingGrid() {
		assertFalse(DragonMaceProtection.blocksStorage(new TestCraftingContainer(),
				new ItemStack(Items.DRAGON_EGG)));
	}

	@Test
	void bug3DragonEggIsStillBlockedFromRealStorage() {
		assertTrue(DragonMaceProtection.blocksStorage(new SimpleContainer(27),
				new ItemStack(Items.DRAGON_EGG)),
				"chests and other storage must still refuse the egg");
	}

	@Test
	void bug3DragonMaceIsAlsoAllowedInACraftingGrid() {
		assertFalse(DragonMaceProtection.blocksStorage(new TestCraftingContainer(),
				DragonMaceRecipes.createDragonMaceStack()));
	}

	@Test
	void bug3OrdinaryItemsAreUnaffected() {
		assertFalse(DragonMaceProtection.blocksStorage(new SimpleContainer(27),
				new ItemStack(Items.STICK)));
	}

	/** Minimal {@link CraftingContainer} so the guard can be tested without a live menu. */
	private static final class TestCraftingContainer implements CraftingContainer {
		private final SimpleContainer delegate = new SimpleContainer(9);

		@Override
		public int getWidth() {
			return 3;
		}

		@Override
		public int getHeight() {
			return 3;
		}

		@Override
		public List<ItemStack> getItems() {
			return List.of();
		}

		@Override
		public int getContainerSize() {
			return this.delegate.getContainerSize();
		}

		@Override
		public boolean isEmpty() {
			return this.delegate.isEmpty();
		}

		@Override
		public ItemStack getItem(int slot) {
			return this.delegate.getItem(slot);
		}

		@Override
		public ItemStack removeItem(int slot, int amount) {
			return this.delegate.removeItem(slot, amount);
		}

		@Override
		public ItemStack removeItemNoUpdate(int slot) {
			return this.delegate.removeItemNoUpdate(slot);
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			this.delegate.setItem(slot, stack);
		}

		@Override
		public void setChanged() {
			this.delegate.setChanged();
		}

		@Override
		public boolean stillValid(net.minecraft.world.entity.player.Player player) {
			return true;
		}

		@Override
		public void clearContent() {
			this.delegate.clearContent();
		}

		@Override
		public void fillStackedContents(net.minecraft.world.entity.player.StackedItemContents contents) {
		}
	}
}
