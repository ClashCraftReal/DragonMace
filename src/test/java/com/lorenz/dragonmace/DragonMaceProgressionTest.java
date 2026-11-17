package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import com.lorenz.dragonmace.world.DragonMaceProgressionData;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Kill progression, rune persistence and the lore context.
 *
 * <p>Exercises the real {@link DragonMaceProgressionData} codec, so a save that would not survive a
 * restart fails here rather than in game.
 */
class DragonMaceProgressionTest {
	@BeforeAll
	static void setUp() {
		TestBootstrap.boot();
	}

	@Test
	void aFreshPlayerHasNoRecordAndNoKills() {
		DragonMaceProgressionData data = new DragonMaceProgressionData();
		UUID player = UUID.randomUUID();

		assertNull(data.entry(player));
		assertEquals(0, data.kills(player));
		assertEquals(DragonMaceRuneSelection.defaults(), data.runes(player));
		assertEquals(0, data.size());
	}

	@Test
	void aKillRaisesTheCountAndCreatesTheRecord() {
		DragonMaceProgressionData data = new DragonMaceProgressionData();
		UUID player = UUID.randomUUID();

		assertEquals(1, data.recordKill(player));
		assertEquals(2, data.recordKill(player));
		assertEquals(2, data.kills(player));
		assertEquals(1, data.size());
	}

	@Test
	void killsAreTrackedPerPlayer() {
		DragonMaceProgressionData data = new DragonMaceProgressionData();
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();

		data.recordKill(first);
		data.recordKill(first);
		data.recordKill(second);

		assertEquals(2, data.kills(first));
		assertEquals(1, data.kills(second));
	}

	@Test
	void aNullPlayerIsIgnored() {
		DragonMaceProgressionData data = new DragonMaceProgressionData();

		assertEquals(0, data.recordKill(null));
		assertEquals(0, data.kills(null));
		assertFalse(data.setRunes(null, DragonMaceRuneSelection.defaults()));
		assertEquals(0, data.size());
	}

	@Test
	void anUnlockedSelectionIsStored() {
		DragonMaceProgressionData data = new DragonMaceProgressionData();
		UUID player = UUID.randomUUID();
		data.recordKill(player);
		DragonMaceRuneSelection selection = DragonMaceRuneSelection.defaults()
				.with(DragonMaceRuneCategory.KILL, DragonMaceRune.SOULFLAME);

		assertTrue(data.setRunes(player, selection));
		assertEquals(selection, data.runes(player));
	}

	@Test
	void aLockedSelectionIsSanitisedOnSave() {
		DragonMaceProgressionData data = new DragonMaceProgressionData();
		UUID player = UUID.randomUUID();
		DragonMaceRuneSelection locked = DragonMaceRuneSelection.defaults()
				.with(DragonMaceRuneCategory.AURA, DragonMaceRune.WARDEN);

		assertTrue(data.setRunes(player, locked));
		assertSame(DragonMaceRune.DRACONIC, data.runes(player).aura(),
				"a rune the player has not unlocked must not be stored");
	}

	@Test
	void savingTheSameSelectionTwiceIsANoOp() {
		DragonMaceProgressionData data = new DragonMaceProgressionData();
		UUID player = UUID.randomUUID();
		DragonMaceRuneSelection selection = DragonMaceRuneSelection.defaults()
				.with(DragonMaceRuneCategory.ATTACK, DragonMaceRune.DRACONIC);

		assertTrue(data.setRunes(player, selection));
		assertFalse(data.setRunes(player, selection));
	}

	@Test
	void aStoredSelectionIsSanitisedAgainstTheStoredKillCount() {
		// A hand-edited save: Warden selected with only one kill.
		UUID player = UUID.randomUUID();
		DragonMaceProgressionData data = new DragonMaceProgressionData(java.util.Map.of(
				player.toString(),
				new DragonMaceProgressionData.Entry(1, new DragonMaceRuneSelection(
						DragonMaceRune.WARDEN, DragonMaceRune.DRACONIC, DragonMaceRune.DRACONIC))));

		assertSame(DragonMaceRune.DRACONIC, data.runes(player).kill());
	}

	@Test
	void theDataSurvivesACodecRoundTrip() {
		DragonMaceProgressionData data = new DragonMaceProgressionData();
		UUID player = UUID.randomUUID();

		for (int i = 0; i < 4; i++) {
			data.recordKill(player);
		}

		data.setRunes(player, new DragonMaceRuneSelection(DragonMaceRune.SOULFLAME,
				DragonMaceRune.TEMPEST, DragonMaceRune.WARDEN));

		var encoded = DragonMaceProgressionData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
		DragonMaceProgressionData decoded = DragonMaceProgressionData.CODEC
				.parse(JsonOps.INSTANCE, encoded).getOrThrow();

		assertEquals(4, decoded.kills(player));
		assertEquals(new DragonMaceRuneSelection(DragonMaceRune.SOULFLAME, DragonMaceRune.TEMPEST,
				DragonMaceRune.WARDEN), decoded.runes(player));
	}

	@Test
	void anEmptyDataEncodesAndDecodes() {
		var encoded = DragonMaceProgressionData.CODEC
				.encodeStart(JsonOps.INSTANCE, new DragonMaceProgressionData()).getOrThrow();
		DragonMaceProgressionData decoded = DragonMaceProgressionData.CODEC
				.parse(JsonOps.INSTANCE, encoded).getOrThrow();

		assertEquals(0, decoded.size());
	}

	@Test
	void theEnchantmentSummaryFollowsTheConfiguredLevels() {
		assertEquals("Wind Burst III, Density V",
				DragonMaceLoreContext.summaryOf(3, 5, 0));
		assertEquals("Breach IV", DragonMaceLoreContext.summaryOf(0, 0, 4));
		assertEquals("Wind Burst I, Breach IV",
				DragonMaceLoreContext.summaryOf(1, 0, 4));
		assertEquals("", DragonMaceLoreContext.summaryOf(0, 0, 0));
	}

	@Test
	void theLoreContextCarriesTheSelectionAndKills() {
		DragonMaceRuneSelection selection = DragonMaceRuneSelection.defaults()
				.with(DragonMaceRuneCategory.AURA, DragonMaceRune.TEMPEST);
		DragonMaceLoreContext context = new DragonMaceLoreContext(null, "Density V", selection, 7);

		assertEquals(7, context.kills());
		assertSame(DragonMaceRune.TEMPEST, context.runes().aura());
		assertEquals("Density V", context.enchantmentSummary());
	}

	@Test
	void aNullLoreContextFallsBackToTheDefaults() {
		DragonMaceLoreContext context = new DragonMaceLoreContext(null, null, null, -5);

		assertEquals(0, context.kills());
		assertEquals("", context.enchantmentSummary());
		assertEquals(DragonMaceRuneSelection.defaults(), context.runes());
		assertNotNull(context.enchantments());
	}

	@Test
	void theLoreRendersTheSelectedRuneNames() {
		DragonMaceLoreContext context = new DragonMaceLoreContext(null, "Density V",
				new DragonMaceRuneSelection(DragonMaceRune.SOULFLAME, DragonMaceRune.TEMPEST,
						DragonMaceRune.WARDEN),
				4);
		var lore = DragonMaceItems.draconicMaceLore(context);

		assertEquals("\u25C6 Kill Rune: Soulflame", lore.get(14).getString());
		assertEquals("\u25C6 Attack Rune: Tempest", lore.get(15).getString());
		assertEquals("\u25C6 Aura Rune: Warden", lore.get(16).getString());
	}

	@Test
	void theLoreRendersTheKillCounter() {
		DragonMaceLoreContext context = new DragonMaceLoreContext(null, "Density V",
				DragonMaceRuneSelection.defaults(), 12);
		var lore = DragonMaceItems.draconicMaceLore(context);

		assertEquals("Kills: 12", lore.get(18).getString());
	}

	@Test
	void theLoreRendersTheEnchantmentSummary() {
		DragonMaceLoreContext context = new DragonMaceLoreContext(null, "Wind Burst II, Breach IV",
				DragonMaceRuneSelection.defaults(), 0);

		assertEquals("Wind Burst II, Breach IV",
				DragonMaceItems.draconicMaceLore(context).get(2).getString());
	}

	@Test
	void anEmptySummaryRendersAsNoEnchantments() {
		DragonMaceLoreContext context = new DragonMaceLoreContext(null, "",
				DragonMaceRuneSelection.defaults(), 0);

		assertEquals("No enchantments", DragonMaceItems.draconicMaceLore(context).get(2).getString());
	}
}
