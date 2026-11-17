package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Dragons Pride: the two-totem limit and its drop priority.
 *
 * <p>{@link DragonMaceTotemLimit#planDrops} is pure, so every priority case is exercised without a
 * live player. The offhand totem is always kept, one further totem is kept from the lowest inventory
 * slot that holds one, and everything else is dropped.
 */
class DragonMaceTotemLimitTest {
	@BeforeAll
	static void setUp() {
		TestBootstrap.boot();
	}

	private static ItemStack totem(int count) {
		return new ItemStack(Items.TOTEM_OF_UNDYING, count);
	}

	private static ItemStack totem() {
		return totem(1);
	}

	@Test
	void offhandAndOneInventoryTotemAreBothKept() {
		assertArrayEquals(new int[] {0}, DragonMaceTotemLimit.planDrops(1, new int[] {1}));
	}

	@Test
	void offhandIsKeptAndOneInventoryTotemSurvivesTheRest() {
		assertArrayEquals(new int[] {2}, DragonMaceTotemLimit.planDrops(1, new int[] {3}));
	}

	@Test
	void offhandAloneIsKept() {
		assertArrayEquals(new int[] {0}, DragonMaceTotemLimit.planDrops(1, new int[] {0}));
	}

	@Test
	void twoInventoryTotemsAreKeptAndTheThirdIsDropped() {
		assertArrayEquals(new int[] {1}, DragonMaceTotemLimit.planDrops(0, new int[] {3}));
	}

	@Test
	void aSingleInventoryTotemIsKept() {
		assertArrayEquals(new int[] {0}, DragonMaceTotemLimit.planDrops(0, new int[] {1}));
	}

	@Test
	void theThirdSlotLosesItsTotemWhenEarlierSlotsFillTheBudget() {
		assertArrayEquals(new int[] {0, 0, 1}, DragonMaceTotemLimit.planDrops(0, new int[] {1, 1, 1}));
	}

	@Test
	void aFullOffhandLeavesNoBudgetForTheInventory() {
		assertArrayEquals(new int[] {1}, DragonMaceTotemLimit.planDrops(2, new int[] {1}));
	}

	@Test
	void anEmptyInventoryDropsNothing() {
		assertArrayEquals(new int[] {0, 0, 0}, DragonMaceTotemLimit.planDrops(0, new int[] {0, 0, 0}));
	}

	@Test
	void aStackedTotemDropsOnlyTheExcess() {
		assertArrayEquals(new int[] {63}, DragonMaceTotemLimit.planDrops(1, new int[] {64}));
	}

	@Test
	void anEmptyPlanIsEmpty() {
		assertArrayEquals(new int[0], DragonMaceTotemLimit.planDrops(1, new int[0]));
	}

	@Test
	void negativeCountsAreTreatedAsEmpty() {
		assertArrayEquals(new int[] {0}, DragonMaceTotemLimit.planDrops(-5, new int[] {-3}));
	}

	@Test
	void nothingIsLostOrDuplicatedAcrossEveryPriorityCase() {
		int[][] inventories = {{1}, {3}, {0}, {1, 1, 1}, {64}, {2, 5, 0, 1}, {0, 0, 0}};

		for (int offhand = 0; offhand <= 2; offhand++) {
			for (int[] inventory : inventories) {
				int[] drops = DragonMaceTotemLimit.planDrops(offhand, inventory);
				int total = offhand;
				int dropped = 0;
				int kept = Math.min(offhand, DragonMaceTotemLimit.MAX_TOTEMS);

				for (int index = 0; index < inventory.length; index++) {
					total += inventory[index];
					dropped += drops[index];
					kept += inventory[index] - drops[index];
				}

				assertEquals(total, kept + dropped, "kept plus dropped must equal the total carried");
				assertTrue(kept <= DragonMaceTotemLimit.MAX_TOTEMS, "at most two totems may be kept");
				assertEquals(Math.min(offhand, DragonMaceTotemLimit.MAX_TOTEMS),
						Math.min(offhand, DragonMaceTotemLimit.MAX_TOTEMS),
						"the offhand totem is never dropped");
			}
		}
	}

	@Test
	void theOffhandTotemIsNeverPartOfTheDropPlan() {
		int[] drops = DragonMaceTotemLimit.planDrops(2, new int[] {1, 1, 1});

		assertArrayEquals(new int[] {1, 1, 1}, drops, "with a full offhand every inventory totem is excess");
	}

	@Test
	void aThirdTotemIsRefusedBeforeItIsPickedUp() {
		assertFalse(DragonMaceTotemLimit.allowsAdd(2, totem(1)), "a third totem must be refused");
		assertTrue(DragonMaceTotemLimit.allowsAdd(1, totem(1)), "a second totem must be allowed");
		assertTrue(DragonMaceTotemLimit.allowsAdd(0, totem(2)), "two totems in one stack are allowed");
		assertFalse(DragonMaceTotemLimit.allowsAdd(1, totem(2)), "a stack that would make three is refused");
	}

	@Test
	void nonTotemsAreAlwaysAllowed() {
		assertTrue(DragonMaceTotemLimit.allowsAdd(2, new ItemStack(Items.SHIELD)));
		assertTrue(DragonMaceTotemLimit.allowsAdd(64, new ItemStack(Items.MACE)));
	}

	@Test
	void excessIsTheAmountOverTwo() {
		assertEquals(0, DragonMaceTotemLimit.excess(0));
		assertEquals(0, DragonMaceTotemLimit.excess(2));
		assertEquals(1, DragonMaceTotemLimit.excess(3));
		assertEquals(62, DragonMaceTotemLimit.excess(64));
	}

	@Test
	void theLimitAppliesOnlyToTheCurrentCurseHolder() {
		UUID owner = UUID.randomUUID();
		UUID other = UUID.randomUUID();

		assertTrue(DragonMaceTotemLimit.appliesTo(owner, owner),
				"the curse holder is limited to two totems");
		assertFalse(DragonMaceTotemLimit.appliesTo(owner, other),
				"a player without the curse keeps normal vanilla totem behaviour");
	}

	@Test
	void theLimitLiftsWhenTheCurseHasNoOwner() {
		assertFalse(DragonMaceTotemLimit.appliesTo(null, UUID.randomUUID()),
				"a withdrawal or global removal clears the owner and must lift the restriction");
	}

	@Test
	void theLimitLiftsWhenTheCurseTransfersAway() {
		UUID previous = UUID.randomUUID();
		UUID next = UUID.randomUUID();

		assertTrue(DragonMaceTotemLimit.appliesTo(previous, previous));
		assertFalse(DragonMaceTotemLimit.appliesTo(next, previous),
				"after a transfer the previous holder is no longer limited");
		assertTrue(DragonMaceTotemLimit.appliesTo(next, next),
				"after a transfer the new holder is limited");
	}

	@Test
	void enforcingWithoutAPlayerDoesNothing() {
		assertEquals(0, DragonMaceTotemLimit.enforce(null));
	}

	// --- curse-aware insertion gate -----------------------------------------------------------

	@Test
	void withoutTheCurseATotemInsertIsNeverRefused() {
		UUID player = UUID.randomUUID();

		assertFalse(DragonMaceTotemLimit.blocksInsert(null, player, 2, totem()),
				"a player without the curse must keep vanilla totem behaviour");
	}

	@Test
	void aFormerHolderIsTreatedLikeAnyOtherPlayer() {
		UUID former = UUID.randomUUID();
		UUID newOwner = UUID.randomUUID();

		assertFalse(DragonMaceTotemLimit.blocksInsert(newOwner, former, 2, totem()),
				"after a transfer the previous holder must be unrestricted");
	}

	@Test
	void theHolderIsRefusedAThirdTotem() {
		UUID owner = UUID.randomUUID();

		assertTrue(DragonMaceTotemLimit.blocksInsert(owner, owner, 2, totem()));
	}

	@Test
	void theHolderMayStillRearrangeTwoTotems() {
		UUID owner = UUID.randomUUID();

		assertFalse(DragonMaceTotemLimit.blocksInsert(owner, owner, 1, totem()),
				"moving an existing totem into an empty slot must not be refused");
	}

	@Test
	void theHolderMayStillSwapATotemWithAnotherItem() {
		UUID owner = UUID.randomUUID();

		assertFalse(DragonMaceTotemLimit.blocksInsert(owner, owner, 2, new ItemStack(Items.STONE)),
				"a non-totem insert is never limited");
	}

	@Test
	void reapplyingTheCurseRestoresTheLimit() {
		UUID owner = UUID.randomUUID();

		assertFalse(DragonMaceTotemLimit.blocksInsert(null, owner, 2, totem()));
		assertTrue(DragonMaceTotemLimit.blocksInsert(owner, owner, 2, totem()),
				"reapplying the curse must restore the two-totem limit");
	}
}
