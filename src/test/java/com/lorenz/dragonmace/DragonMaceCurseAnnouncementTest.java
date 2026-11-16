package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DragonMaceCurseAnnouncementTest {
	@Test
	void coreCraftedMessageIsExact() {
		assertEquals("[Dragon Mace] A Very Heavy Core has been crafted!",
				DragonMaceAnnouncements.coreCraftedMessage());
	}

	@Test
	void coreConsumedMessageIsExact() {
		assertEquals("[Dragon Mace] Steve has consumed a Very Heavy Core!",
				DragonMaceAnnouncements.coreConsumedMessage("Steve"));
	}

	@Test
	void curseTransferredMessageIsExact() {
		assertEquals("[Dragon Mace] The Curse of the Dragons has transferred from Steve to Alex!",
				DragonMaceAnnouncements.curseTransferredMessage("Steve", "Alex"));
	}

	@Test
	void everyCurseMessageUsesTheRequiredPrefix() {
		assertTrue(DragonMaceAnnouncements.coreCraftedMessage().startsWith("[Dragon Mace] "));
		assertTrue(DragonMaceAnnouncements.coreConsumedMessage("Steve").startsWith("[Dragon Mace] "));
		assertTrue(DragonMaceAnnouncements.curseTransferredMessage("Steve", "Alex")
				.startsWith("[Dragon Mace] "));
	}

	@Test
	void tabListTagIsTheRequiredText() {
		assertEquals("[Curse of the Dragons]", DragonMaceTabList.TAG_TEXT);
	}
}
