package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DragonEggProtectionTest {
	@BeforeAll
	static void setUp() {
		SharedConstants.tryDetectVersion();
		TestBootstrap.boot();
	}

	private static BlockState air() {
		return Blocks.AIR.defaultBlockState();
	}

	private static BlockState egg() {
		return Blocks.DRAGON_EGG.defaultBlockState();
	}

	@Test
	void fallingEggOnExistingEggIsADuplicate() {
		assertTrue(DragonEggProtection.isDuplicateLanding(egg(), egg()));
	}

	@Test
	void fallingEggOnAirOrStoneIsNotADuplicate() {
		assertFalse(DragonEggProtection.isDuplicateLanding(air(), egg()));
		assertFalse(DragonEggProtection.isDuplicateLanding(Blocks.STONE.defaultBlockState(), egg()));
	}

	@Test
	void otherFallingBlocksAreNeverTreatedAsEggDuplicates() {
		BlockState sand = Blocks.SAND.defaultBlockState();

		assertFalse(DragonEggProtection.isDuplicateLanding(egg(), sand));
		assertFalse(DragonEggProtection.isDuplicateLanding(air(), sand));
	}

	@Test
	void zeroOffsetTeleportIsDetected() {
		BlockPos origin = new BlockPos(10, 64, -3);

		assertTrue(DragonEggProtection.isSelfTeleport(origin, new BlockPos(10, 64, -3)));
		assertFalse(DragonEggProtection.isSelfTeleport(origin, new BlockPos(11, 64, -3)));
	}

	@Test
	void destinationIsFoundWhenAirIsAvailable() {
		BlockPos origin = BlockPos.ZERO;
		BlockPos destination = DragonEggProtection.findTeleportDestination(
				origin, RandomSource.create(42L), pos -> true, pos -> true);

		assertNotEquals(null, destination);
		assertFalse(DragonEggProtection.isSelfTeleport(origin, destination));
	}

	@Test
	void destinationStaysWithinVanillaOffsetRange() {
		BlockPos origin = new BlockPos(100, 70, 100);

		for (long seed = 0; seed < 50; seed++) {
			BlockPos destination = DragonEggProtection.findTeleportDestination(
					origin, RandomSource.create(seed), pos -> true, pos -> true);

			assertTrue(Math.abs(destination.getX() - origin.getX()) <= 15);
			assertTrue(Math.abs(destination.getY() - origin.getY()) <= 7);
			assertTrue(Math.abs(destination.getZ() - origin.getZ()) <= 15);
		}
	}

	@Test
	void noDestinationWhenEveryPositionIsBlocked() {
		assertNull(DragonEggProtection.findTeleportDestination(
				BlockPos.ZERO, RandomSource.create(7L), pos -> false, pos -> true));
	}

	@Test
	void noDestinationWhenEveryPositionIsOutOfBounds() {
		assertNull(DragonEggProtection.findTeleportDestination(
				BlockPos.ZERO, RandomSource.create(7L), pos -> true, pos -> false));
	}

	@Test
	void destinationRespectsBothPredicates() {
		BlockPos origin = BlockPos.ZERO;
		Set<BlockPos> allowed = Set.of(new BlockPos(1, 0, 0));

		BlockPos destination = DragonEggProtection.findTeleportDestination(
				origin, RandomSource.create(3L), allowed::contains, pos -> true);

		assertEquals(new BlockPos(1, 0, 0), destination);
	}
}
