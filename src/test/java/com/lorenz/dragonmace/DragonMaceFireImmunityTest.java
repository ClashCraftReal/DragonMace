package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Fire and lava immunity for dropped Dragon Eggs and Very Heavy Cores.
 *
 * <p>{@link DragonMaceProtection#groundProtection} is the pure decision behind both the item entity
 * flags and {@code ItemEntity.fireImmune()}, so the whole rule is exercised without a live level.
 * Void destruction is a position check rather than damage, so it is deliberately not expressed here.
 */
class DragonMaceFireImmunityTest {
	@BeforeAll
	static void setUp() {
		TestBootstrap.boot();
	}

	private static ItemStack core() {
		return DragonMaceItems.createVeryHeavyCoreStack();
	}

	private static ItemStack dragonMace() {
		ItemStack stack = new ItemStack(Items.MACE);
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(DragonMaceIdentity.MARKER_KEY, true);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	@Test
	void aDroppedDragonEggIsInvulnerableAndNeverDespawns() {
		DragonMaceProtection.GroundProtection protection =
				DragonMaceProtection.groundProtection(new ItemStack(Items.DRAGON_EGG));

		assertTrue(protection.invulnerable(), "a dropped Dragon Egg must be immune to fire and lava");
		assertTrue(protection.unlimitedLifetime(), "a dropped Dragon Egg must never despawn");
	}

	@Test
	void aDroppedVeryHeavyCoreIsInvulnerableAndNeverDespawns() {
		DragonMaceProtection.GroundProtection protection = DragonMaceProtection.groundProtection(core());

		assertTrue(protection.invulnerable(), "a dropped Very Heavy Core must be immune to fire and lava");
		assertTrue(protection.unlimitedLifetime(), "a dropped Very Heavy Core must never despawn");
	}

	@Test
	void ordinaryPaperKeepsVanillaGroundBehaviour() {
		DragonMaceProtection.GroundProtection protection =
				DragonMaceProtection.groundProtection(new ItemStack(Items.PAPER));

		assertFalse(protection.invulnerable(), "ordinary paper must still burn");
		assertFalse(protection.unlimitedLifetime(), "ordinary paper must still despawn");
	}

	@Test
	void aDroppedDragonMaceStaysProtected() {
		DragonMaceProtection.GroundProtection protection =
				DragonMaceProtection.groundProtection(dragonMace());

		assertTrue(protection.invulnerable());
		assertTrue(protection.unlimitedLifetime());
	}

	@Test
	void fireImmunityFollowsTheGroundProtection() {
		assertTrue(DragonMaceProtection.isFireImmune(new ItemStack(Items.DRAGON_EGG)));
		assertTrue(DragonMaceProtection.isFireImmune(core()));
		assertTrue(DragonMaceProtection.isFireImmune(dragonMace()));
		assertFalse(DragonMaceProtection.isFireImmune(new ItemStack(Items.PAPER)));
		assertFalse(DragonMaceProtection.isFireImmune(new ItemStack(Items.MACE)));
	}

	@Test
	void anEmptyStackIsNotProtected() {
		assertFalse(DragonMaceProtection.isFireImmune(ItemStack.EMPTY));
		assertFalse(DragonMaceProtection.groundProtection(ItemStack.EMPTY).invulnerable());
	}

	@Test
	void aRenamedCoreIsStillRecognisedByItsIdentity() {
		ItemStack renamed = core();
		renamed.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
				net.minecraft.network.chat.Component.literal("Not A Core"));

		assertTrue(DragonMaceProtection.isFireImmune(renamed),
				"identity comes from the custom identifier, not the display name");
	}
}
