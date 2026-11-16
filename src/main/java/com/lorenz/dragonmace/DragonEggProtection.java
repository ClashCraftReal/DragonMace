package com.lorenz.dragonmace;

import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Pure decision logic for the Dragon Egg duplication protections.
 *
 * <p>Kept free of world state so every rule can be unit tested directly; the mixins only supply the
 * live block states and bounds predicates.
 */
public final class DragonEggProtection {
	/** Number of random destination attempts vanilla makes when a dragon egg teleports. */
	public static final int TELEPORT_ATTEMPTS = 1000;

	private DragonEggProtection() {
	}

	/**
	 * True when a falling dragon egg has landed on a position that already holds a dragon egg.
	 *
	 * <p>Vanilla cannot place the block there, so it discards the falling entity and drops a dragon
	 * egg item while the existing block stays in the world, turning one egg into a block plus an
	 * item.
	 */
	public static boolean isDuplicateLanding(BlockState landingState, BlockState fallingState) {
		return fallingState.is(Blocks.DRAGON_EGG) && landingState.is(Blocks.DRAGON_EGG);
	}

	/**
	 * True when a teleport would write the egg onto its own position.
	 *
	 * <p>Vanilla sets the block at the destination and then removes the origin block, so a zero
	 * offset sets and immediately removes the same position, destroying the egg.
	 */
	public static boolean isSelfTeleport(BlockPos origin, BlockPos destination) {
		return origin.equals(destination);
	}

	/**
	 * Picks a teleport destination using vanilla's distribution and constraints.
	 *
	 * @return the destination, or {@code null} when no valid position was found
	 */
	public static BlockPos findTeleportDestination(BlockPos origin, RandomSource random,
			Predicate<BlockPos> isAir, Predicate<BlockPos> isWithinBounds) {
		for (int attempt = 0; attempt < TELEPORT_ATTEMPTS; attempt++) {
			BlockPos candidate = origin.offset(
					random.nextInt(16) - random.nextInt(16),
					random.nextInt(8) - random.nextInt(8),
					random.nextInt(16) - random.nextInt(16));

			if (isAir.test(candidate) && isWithinBounds.test(candidate)) {
				return candidate;
			}
		}

		return null;
	}
}
