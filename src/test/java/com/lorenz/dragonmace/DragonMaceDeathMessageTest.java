package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for Draconic Mace kill attribution in the death message.
 *
 * <p>The capture and override decisions are pure, so they are exercised directly. Building the message
 * is compared against the component vanilla itself would build, which pins both the translation key and
 * the three arguments.
 */
class DragonMaceDeathMessageTest {
	@BeforeAll
	static void setUp() {
		TestBootstrap.boot();
	}

	private static ItemStack mace() {
		return new ItemStack(Items.MACE);
	}

	private static ItemStack draconic() {
		return DragonMaceItems.createDraconicMaceStack(mace(), null);
	}

	private static ItemStack reverted() {
		return DragonMaceItems.revertDraconicMace(draconic());
	}

	// ------------------------------------------------------------- ranking

	@Test
	void aDraconicMaceRanksAsDraconic() {
		assertEquals(DragonMaceDeathMessage.RANK_DRACONIC, DragonMaceDeathMessage.rankOf(draconic()));
	}

	@Test
	void aRevertedDraconicMaceStillRanksAsDraconic() {
		assertEquals(DragonMaceDeathMessage.RANK_DRACONIC, DragonMaceDeathMessage.rankOf(reverted()),
				"a mace reverted during the transfer must still be attributed");
	}

	@Test
	void anOrdinaryMaceRanksAsNone() {
		assertEquals(DragonMaceDeathMessage.RANK_NONE, DragonMaceDeathMessage.rankOf(mace()));
	}

	@Test
	void anUnrelatedNamedItemRanksAsNamed() {
		ItemStack named = mace();
		named.set(DataComponents.CUSTOM_NAME, Component.literal("My Mace"));

		assertEquals(DragonMaceDeathMessage.RANK_NAMED, DragonMaceDeathMessage.rankOf(named));
	}

	@Test
	void anEmptyStackRanksAsNone() {
		assertEquals(DragonMaceDeathMessage.RANK_NONE, DragonMaceDeathMessage.rankOf(ItemStack.EMPTY));
		assertEquals(DragonMaceDeathMessage.RANK_NONE, DragonMaceDeathMessage.rankOf(null));
	}

	// ------------------------------------------------------------ override

	@Test
	void aDraconicKillOverridesTheMessage() {
		assertTrue(DragonMaceDeathMessage.shouldOverride(DragonMaceDeathMessage.RANK_DRACONIC, true));
	}

	@Test
	void anOrdinaryMaceKeepsTheVanillaMessage() {
		assertFalse(DragonMaceDeathMessage.shouldOverride(DragonMaceDeathMessage.RANK_NONE, true));
	}

	@Test
	void anotherNamedWeaponKeepsTheVanillaMessage() {
		assertFalse(DragonMaceDeathMessage.shouldOverride(DragonMaceDeathMessage.RANK_NAMED, true),
				"only a Draconic Mace may replace vanilla wording");
	}

	@Test
	void anUnrelatedDamageSourceIsNotOverridden() {
		assertFalse(DragonMaceDeathMessage.shouldOverride(DragonMaceDeathMessage.RANK_DRACONIC, false),
				"an indirect kill must not be attributed to the Draconic Mace");
	}

	// ------------------------------------------------------------- capture

	@Test
	void aHigherRankSnapshotReplacesALowerOne() {
		assertTrue(DragonMaceDeathMessage.shouldReplace(DragonMaceDeathMessage.RANK_NONE,
				DragonMaceDeathMessage.RANK_DRACONIC));
		assertTrue(DragonMaceDeathMessage.shouldReplace(DragonMaceDeathMessage.RANK_NAMED,
				DragonMaceDeathMessage.RANK_DRACONIC));
	}

	@Test
	void aLowerRankSnapshotNeverReplacesAHigherOne() {
		assertFalse(DragonMaceDeathMessage.shouldReplace(DragonMaceDeathMessage.RANK_DRACONIC,
				DragonMaceDeathMessage.RANK_NAMED),
				"a mace reverted during the transfer must keep its Draconic name");
		assertFalse(DragonMaceDeathMessage.shouldReplace(DragonMaceDeathMessage.RANK_DRACONIC,
				DragonMaceDeathMessage.RANK_DRACONIC));
	}

	@Test
	void captureIgnoresNullArguments() {
		DragonMaceDeathMessage.clearAll();
		DragonMaceDeathMessage.capture(null, null);

		assertTrue(DragonMaceDeathMessage.weaponFor(null, null).isEmpty());
	}

	@Test
	void anUnknownVictimHasNoCapturedWeapon() {
		DragonMaceDeathMessage.clearAll();

		assertTrue(DragonMaceDeathMessage.weaponFor(null, null).isEmpty());
	}

	@Test
	void clearingIsIdempotent() {
		DragonMaceDeathMessage.clear(null);
		DragonMaceDeathMessage.clearAll();
		DragonMaceDeathMessage.clearAll();
	}

	// ------------------------------------------------------------- message

	@Test
	void theMessageUsesTheVanillaItemKey() {
		assertEquals("death.attack.player.item", DragonMaceDeathMessage.deathMessageKey("player"));
		assertEquals("death.attack.mace_smash.item", DragonMaceDeathMessage.deathMessageKey("mace_smash"));
	}

	@Test
	void theMessageMatchesWhatVanillaWouldBuild() {
		Component victim = Component.literal("Victim");
		Component killer = Component.literal("Killer");
		Component weapon = DragonMaceItems.draconicMaceName();

		Component built = DragonMaceDeathMessage.buildMessage("player", victim, killer, weapon);
		Component vanilla = Component.translatable("death.attack.player.item", victim, killer, weapon);

		assertEquals(vanilla, built);
	}

	@Test
	void theMessageCarriesTheDraconicName() {
		Component built = DragonMaceDeathMessage.buildMessage("player",
				Component.literal("Victim"), Component.literal("Killer"),
				DragonMaceItems.draconicMaceName());

		assertTrue(built.getString().contains("Draconic Mace"),
				"the message must name the Draconic Mace");
	}

	@Test
	void theMessageKeepsTheCustomFormatting() {
		Component built = DragonMaceDeathMessage.buildMessage("player",
				Component.literal("Victim"), Component.literal("Killer"),
				DragonMaceItems.draconicMaceName());

		assertFalse(built.getString().contains("\u272A"),
				"the decorative stars must not survive into the message");
		assertFalse(built.getString().contains("\u278E"),
				"the tier glyph must not survive into the message");
	}

	@Test
	void anOrdinaryMaceNameIsNotTheDraconicName() {
		Component ordinary = mace().getDisplayName();

		assertFalse(ordinary.equals(DragonMaceItems.draconicMaceName()));
		assertEquals("[Mace]", ordinary.getString(),
				"getDisplayName wraps the name in brackets, exactly as vanilla does");
	}

	@Test
	void weaponForReturnsEmptyWithoutASnapshot() {
		DragonMaceDeathMessage.clearAll();

		assertEquals(Optional.empty(), DragonMaceDeathMessage.weaponFor(null, null));
	}
}
