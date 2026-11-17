package com.lorenz.dragonmace;

/**
 * The three independent rune categories.
 *
 * <p>Each category holds its own selection, so a player may run Draconic as their Kill rune, Soulflame
 * as their Attack rune and Warden as their Aura rune at the same time. The category also carries the
 * lore label used on the Draconic Mace, so the item and the GUI can never disagree about the wording.
 */
public enum DragonMaceRuneCategory {
	KILL("Kill Rune"),
	ATTACK("Attack Rune"),
	AURA("Aura Rune");

	private final String label;

	DragonMaceRuneCategory(String label) {
		this.label = label;
	}

	/** The label shown in the lore, e.g. {@code Kill Rune}. */
	public String label() {
		return this.label;
	}

	/** The full lore prefix, e.g. {@code Kill Rune: }. */
	public String loreLabel() {
		return this.label + ": ";
	}
}
