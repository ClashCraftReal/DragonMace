package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The rune identities, their unlock thresholds and the per-player selection.
 *
 * <p>Every rule here is pure, so no bootstrap or world is needed.
 */
class DragonMaceRuneTest {
	@Test
	void thereAreExactlyFiveRunes() {
		assertEquals(5, DragonMaceRune.values().length);
	}

	@Test
	void everyRuneHasADistinctColour() {
		long distinct = java.util.Arrays.stream(DragonMaceRune.values())
				.map(DragonMaceRune::colour)
				.distinct()
				.count();

		assertEquals(5, distinct, "each rune must be visually distinguishable");
	}

	@Test
	void theUnlockThresholdsAreZeroToOneToFour() {
		assertEquals(0, DragonMaceRune.DRACONIC.unlockKills());
		assertEquals(1, DragonMaceRune.SOULFLAME.unlockKills());
		assertEquals(2, DragonMaceRune.TEMPEST.unlockKills());
		assertEquals(3, DragonMaceRune.ABYSSAL.unlockKills());
		assertEquals(4, DragonMaceRune.WARDEN.unlockKills());
	}

	@Test
	void onlyDraconicIsUnlockedAtZeroKills() {
		assertEquals(List.of(DragonMaceRune.DRACONIC), DragonMaceProgression.unlockedAt(0));
	}

	@Test
	void everyRuneIsUnlockedAtFourKills() {
		assertEquals(5, DragonMaceProgression.unlockedAt(4).size());
	}

	@Test
	void unlocksAreCumulative() {
		assertEquals(List.of(DragonMaceRune.DRACONIC, DragonMaceRune.SOULFLAME),
				DragonMaceProgression.unlockedAt(1));
		assertEquals(3, DragonMaceProgression.unlockedAt(2).size());
		assertEquals(4, DragonMaceProgression.unlockedAt(3).size());
	}

	@Test
	void aLockedRuneCannotBeSelected() {
		assertFalse(DragonMaceProgression.canSelect(DragonMaceRune.WARDEN, 3));
		assertTrue(DragonMaceProgression.canSelect(DragonMaceRune.WARDEN, 4));
		assertFalse(DragonMaceProgression.canSelect(null, 99));
	}

	@Test
	void theNextUnlockIsTheLowestCrossedThreshold() {
		assertEquals(DragonMaceRune.SOULFLAME,
				DragonMaceProgression.nextUnlock(0, 1).orElseThrow());
		assertEquals(DragonMaceRune.TEMPEST,
				DragonMaceProgression.nextUnlock(1, 2).orElseThrow());
		// A jump of several kills still announces one rune, never skipping the lowest.
		assertEquals(DragonMaceRune.SOULFLAME,
				DragonMaceProgression.nextUnlock(0, 4).orElseThrow());
	}

	@Test
	void nothingIsUnlockedWhenNoThresholdIsCrossed() {
		assertTrue(DragonMaceProgression.nextUnlock(2, 2).isEmpty());
		assertTrue(DragonMaceProgression.nextUnlock(4, 9).isEmpty());
	}

	@Test
	void theDefaultSelectionIsDraconicEverywhere() {
		DragonMaceRuneSelection defaults = DragonMaceRuneSelection.defaults();

		for (DragonMaceRuneCategory category : DragonMaceRuneCategory.values()) {
			assertSame(DragonMaceRune.DRACONIC, defaults.get(category));
		}
	}

	@Test
	void eachCategoryHoldsItsOwnSelection() {
		DragonMaceRuneSelection selection = DragonMaceRuneSelection.defaults()
				.with(DragonMaceRuneCategory.KILL, DragonMaceRune.SOULFLAME)
				.with(DragonMaceRuneCategory.ATTACK, DragonMaceRune.TEMPEST)
				.with(DragonMaceRuneCategory.AURA, DragonMaceRune.WARDEN);

		assertSame(DragonMaceRune.SOULFLAME, selection.get(DragonMaceRuneCategory.KILL));
		assertSame(DragonMaceRune.TEMPEST, selection.get(DragonMaceRuneCategory.ATTACK));
		assertSame(DragonMaceRune.WARDEN, selection.get(DragonMaceRuneCategory.AURA));
	}

	@Test
	void aSelectionIsIllegalWhenAnyRuneIsLocked() {
		DragonMaceRuneSelection selection = DragonMaceRuneSelection.defaults()
				.with(DragonMaceRuneCategory.AURA, DragonMaceRune.WARDEN);

		assertFalse(DragonMaceProgression.isSelectionLegal(selection, 3));
		assertTrue(DragonMaceProgression.isSelectionLegal(selection, 4));
	}

	@Test
	void aLockedRuneIsResetWhenTheSelectionIsSanitised() {
		DragonMaceRuneSelection selection = new DragonMaceRuneSelection(DragonMaceRune.WARDEN,
				DragonMaceRune.ABYSSAL, DragonMaceRune.TEMPEST);
		DragonMaceRuneSelection sanitised = selection.sanitisedAt(1);

		assertSame(DragonMaceRune.DRACONIC, sanitised.kill());
		assertSame(DragonMaceRune.DRACONIC, sanitised.attack());
		assertSame(DragonMaceRune.DRACONIC, sanitised.aura());
	}

	@Test
	void aSanitisedSelectionKeepsUnlockedRunes() {
		DragonMaceRuneSelection selection = DragonMaceRuneSelection.defaults()
				.with(DragonMaceRuneCategory.KILL, DragonMaceRune.SOULFLAME);

		assertEquals(selection, selection.sanitisedAt(4));
	}

	@Test
	void anUnknownIdFallsBackToDraconic() {
		assertSame(DragonMaceRune.DRACONIC, DragonMaceRune.byIdOrDefault("nope"));
		assertSame(DragonMaceRune.DRACONIC, DragonMaceRune.byIdOrDefault(null));
		assertSame(DragonMaceRune.WARDEN, DragonMaceRune.byIdOrDefault("warden"));
	}

	@Test
	void everyRuneRoundTripsThroughItsId() {
		for (DragonMaceRune rune : DragonMaceRune.values()) {
			assertSame(rune, DragonMaceRune.byId(rune.id()).orElseThrow());
		}
	}

	@Test
	void theCategoryLabelsMatchTheLore() {
		assertEquals("Kill Rune", DragonMaceRuneCategory.KILL.label());
		assertEquals("Kill Rune: ", DragonMaceRuneCategory.KILL.loreLabel());
		assertEquals("Attack Rune: ", DragonMaceRuneCategory.ATTACK.loreLabel());
		assertEquals("Aura Rune: ", DragonMaceRuneCategory.AURA.loreLabel());
	}
}
