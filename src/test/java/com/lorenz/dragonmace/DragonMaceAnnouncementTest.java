package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pure message tests for the server-wide announcements. The live methods only add the broadcast
 * audience and styling, so the wording and the one-message-per-operation contract can be checked
 * here without a running server.
 */
class DragonMaceAnnouncementTest {
	@Test
	void craftingAnnouncementNamesTheCrafter() {
		assertEquals("\uD83D\uDC09 THE DRAGON MACE HAS BEEN FORGED! Alice has crafted the Dragon Mace!",
				DragonMaceAnnouncements.craftedMessage("Alice"));
	}

	@Test
	void firstPickupAnnouncementNamesTheNewOwner() {
		assertEquals("\uD83D\uDC09 THE DRAGON MACE HAS BEEN CLAIMED! Alice has claimed the Dragon Mace!",
				DragonMaceAnnouncements.claimedMessage("Alice"));
	}

	@Test
	void ownershipChangeAnnouncementNamesTheNewWielder() {
		assertEquals("\u2694\uFE0F THE DRAGON MACE CHANGES HANDS! Bob is now the wielder of the Dragon Mace!",
				DragonMaceAnnouncements.changedHandsMessage("Bob"));
	}

	@Test
	void combatAnnouncementNamesBothPlayers() {
		assertEquals("\u2694\uFE0F THE DRAGON MACE HAS BEEN CLAIMED IN COMBAT! Killer has defeated Owner "
						+ "and claimed the Dragon Mace!",
				DragonMaceAnnouncements.combatClaimedMessage("Killer", "Owner"));
	}

	@Test
	void respawnAnnouncementNamesTheFountainAndDoesNotNameAnOwner() {
		assertEquals("\uD83D\uDC09 THE DRAGON MACE HAS RETURNED! The Dragon Mace has been summoned at "
						+ "the End Fountain!",
				DragonMaceAnnouncements.respawnedMessage("the End Fountain"));
	}

	@Test
	void deletionAnnouncementIsOperatorNeutral() {
		assertEquals("\uD83D\uDC09 THE DRAGON MACE HAS BEEN SEALED! An operator has removed the Dragon Mace "
						+ "from the world.",
				DragonMaceAnnouncements.sealedMessage());
	}

	@Test
	void voidAnnouncementExplainsThatAnOperatorMustSummonItAgain() {
		assertEquals("\uD83D\uDC09 THE DRAGON MACE HAS BEEN LOST! The Dragon Mace has fallen into the void and "
						+ "been destroyed. An operator must summon it again.",
				DragonMaceAnnouncements.lostToVoidMessage());
	}

	@Test
	void everyAnnouncementUsesTheSameDragonMacePrefix() {
		assertTrue(DragonMaceAnnouncements.craftedMessage("A").startsWith("\uD83D\uDC09 THE DRAGON MACE"));
		assertTrue(DragonMaceAnnouncements.claimedMessage("A").startsWith("\uD83D\uDC09 THE DRAGON MACE"));
		assertTrue(DragonMaceAnnouncements.changedHandsMessage("A").startsWith("\u2694\uFE0F THE DRAGON MACE"));
		assertTrue(DragonMaceAnnouncements.combatClaimedMessage("A", "B")
				.startsWith("\u2694\uFE0F THE DRAGON MACE"));
		assertTrue(DragonMaceAnnouncements.respawnedMessage("the End Fountain")
				.startsWith("\uD83D\uDC09 THE DRAGON MACE"));
		assertTrue(DragonMaceAnnouncements.sealedMessage().startsWith("\uD83D\uDC09 THE DRAGON MACE"));
		assertTrue(DragonMaceAnnouncements.lostToVoidMessage().startsWith("\uD83D\uDC09 THE DRAGON MACE"));
	}
}
