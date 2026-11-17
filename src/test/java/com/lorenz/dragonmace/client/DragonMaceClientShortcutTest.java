package com.lorenz.dragonmace.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lorenz.dragonmace.DragonMaceEnchantments;
import com.lorenz.dragonmace.DragonMaceItems;
import com.lorenz.dragonmace.TestBootstrap;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Shift + F only opens the enchantment menu while a mace is held.
 *
 * <p>{@link DragonMaceClient#shouldOpen} is the pure decision the tick handler consults, and
 * {@link DragonMaceEnchantments#isMace} is the gate {@code isMenuShortcutDown} uses to decide whether
 * the vanilla offhand swap may be suppressed. Together they cover both halves of the rule: the menu
 * opens for a mace, and every other held item keeps vanilla offhand swapping.
 */
class DragonMaceClientShortcutTest {
	@BeforeAll
	static void setUp() {
		TestBootstrap.boot();
	}

	private static ItemStack vanillaMace() {
		return new ItemStack(Items.MACE);
	}

	private static ItemStack draconicMace() {
		return DragonMaceItems.createDraconicMaceStack(new ItemStack(Items.MACE), null);
	}

	// --- the shortcut fires for maces ---------------------------------------------------------

	@Test
	void shiftFWhileHoldingAVanillaMaceOpensTheMenu() {
		assertTrue(DragonMaceClient.shouldOpen(false, true, DragonMaceEnchantments.isMace(vanillaMace())),
				"a vanilla mace must open the enchantment menu");
	}

	@Test
	void shiftFWhileHoldingADraconicMaceOpensTheMenu() {
		assertTrue(DragonMaceClient.shouldOpen(false, true, DragonMaceEnchantments.isMace(draconicMace())),
				"a Draconic mace must open the enchantment menu");
	}

	// --- every other held item keeps vanilla offhand swapping ---------------------------------

	@Test
	void shiftFWhileHoldingPaperDoesNotOpenTheMenu() {
		assertFalse(DragonMaceClient.shouldOpen(false, true,
				DragonMaceEnchantments.isMace(new ItemStack(Items.PAPER))),
				"paper must keep vanilla offhand swapping");
	}

	@Test
	void shiftFWhileHoldingASwordDoesNotOpenTheMenu() {
		assertFalse(DragonMaceClient.shouldOpen(false, true,
				DragonMaceEnchantments.isMace(new ItemStack(Items.DIAMOND_SWORD))),
				"a sword must keep vanilla offhand swapping");
	}

	@Test
	void shiftFWithAnEmptyHandDoesNotOpenTheMenu() {
		assertFalse(DragonMaceClient.shouldOpen(false, true, DragonMaceEnchantments.isMace(ItemStack.EMPTY)),
				"an empty hand must keep vanilla offhand swapping");
	}

	@Test
	void theVeryHeavyCoreDoesNotOpenTheMenu() {
		assertFalse(DragonMaceClient.shouldOpen(false, true,
				DragonMaceEnchantments.isMace(DragonMaceItems.createVeryHeavyCoreStack())),
				"the Very Heavy Core is not a mace");
	}

	// --- the offhand swap is only suppressed for maces ----------------------------------------

	@Test
	void theOffhandSwapIsSuppressedOnlyForMaces() {
		assertTrue(DragonMaceEnchantments.isMace(vanillaMace()));
		assertTrue(DragonMaceEnchantments.isMace(draconicMace()));
		assertFalse(DragonMaceEnchantments.isMace(new ItemStack(Items.PAPER)));
		assertFalse(DragonMaceEnchantments.isMace(new ItemStack(Items.DIAMOND_SWORD)));
		assertFalse(DragonMaceEnchantments.isMace(ItemStack.EMPTY));
	}

	// --- context gates ------------------------------------------------------------------------

	@Test
	void anOpenScreenBlocksTheShortcut() {
		assertFalse(DragonMaceClient.shouldOpen(true, true, true),
				"the shortcut must not fire from inside an inventory screen");
	}

	@Test
	void shiftMustBeHeld() {
		assertFalse(DragonMaceClient.shouldOpen(false, false, true),
				"plain F must stay the vanilla offhand swap");
	}

	// --- the mace check short-circuits everything else ----------------------------------------

	@Test
	void everyNonMaceCombinationBypassesTheShortcut() {
		for (boolean screenOpen : new boolean[] {false, true}) {
			for (boolean shiftDown : new boolean[] {false, true}) {
				assertFalse(DragonMaceClient.shouldOpen(screenOpen, shiftDown, false),
						"a non-mace hand must bypass the shortcut for screenOpen=" + screenOpen
								+ " shiftDown=" + shiftDown);
			}
		}
	}

	@Test
	void onlyAMaceWithShiftAndNoScreenOpensTheMenu() {
		for (boolean screenOpen : new boolean[] {false, true}) {
			for (boolean shiftDown : new boolean[] {false, true}) {
				boolean expected = !screenOpen && shiftDown;

				assertEquals(expected, DragonMaceClient.shouldOpen(screenOpen, shiftDown, true),
						"screenOpen=" + screenOpen + " shiftDown=" + shiftDown);
			}
		}
	}
}
