package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DragonMaceTotemLimitTest {
	@BeforeAll
	static void setUp() {
		SharedConstants.tryDetectVersion();
		TestBootstrap.boot();
	}

	private static ItemStack totems(int count) {
		return new ItemStack(Items.TOTEM_OF_UNDYING, count);
	}

	@Test
	void limitIsTwo() {
		assertEquals(2, DragonMaceTotemLimit.MAX_TOTEMS);
	}

	@Test
	void onlyTotemsAreRecognised() {
		assertTrue(DragonMaceTotemLimit.isTotem(totems(1)));
		assertFalse(DragonMaceTotemLimit.isTotem(new ItemStack(Items.MACE)));
		assertFalse(DragonMaceTotemLimit.isTotem(ItemStack.EMPTY));
	}

	@Test
	void firstAndSecondTotemAreAllowed() {
		assertTrue(DragonMaceTotemLimit.allowsAdd(0, totems(1)));
		assertTrue(DragonMaceTotemLimit.allowsAdd(1, totems(1)));
	}

	@Test
	void thirdTotemIsRefused() {
		assertFalse(DragonMaceTotemLimit.allowsAdd(2, totems(1)));
	}

	@Test
	void aStackThatWouldOvershootIsRefused() {
		assertFalse(DragonMaceTotemLimit.allowsAdd(1, totems(2)));
	}

	@Test
	void nonTotemsAreAlwaysAllowed() {
		assertTrue(DragonMaceTotemLimit.allowsAdd(2, new ItemStack(Items.MACE)));
		assertTrue(DragonMaceTotemLimit.allowsAdd(99, new ItemStack(Items.STICK)));
	}

	@Test
	void excessIsZeroUpToTheLimit() {
		assertEquals(0, DragonMaceTotemLimit.excess(0));
		assertEquals(0, DragonMaceTotemLimit.excess(2));
	}

	@Test
	void excessCountsEverythingAboveTheLimit() {
		assertEquals(1, DragonMaceTotemLimit.excess(3));
		assertEquals(5, DragonMaceTotemLimit.excess(7));
	}
}
