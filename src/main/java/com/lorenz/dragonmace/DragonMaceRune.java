package com.lorenz.dragonmace;

import java.util.Optional;

import net.minecraft.ChatFormatting;

/**
 * The five rune identities, shared by all three rune categories.
 *
 * <p>Each rune has one fixed colour and one cumulative kill requirement. The same name and colour are
 * used whether the rune is selected as a Kill, Attack or Aura rune, so a player never sees the same rune
 * rendered two different ways.
 *
 * <p>The unlock thresholds are cumulative: at {@code n} kills every rune whose requirement is at most
 * {@code n} is unlocked. Unlocks are permanent — {@link DragonMaceProgression} only ever raises a
 * player's kill count, so a rune can never become locked again.
 */
public enum DragonMaceRune {
	DRACONIC("draconic", "Draconic", ChatFormatting.LIGHT_PURPLE, 0),
	SOULFLAME("soulflame", "Soulflame", ChatFormatting.BLUE, 1),
	TEMPEST("tempest", "Tempest", ChatFormatting.YELLOW, 2),
	ABYSSAL("abyssal", "Abyssal", ChatFormatting.DARK_PURPLE, 3),
	WARDEN("warden", "Warden", ChatFormatting.AQUA, 4);

	/** The rune every player starts with, before any kill. */
	public static final DragonMaceRune DEFAULT = DRACONIC;

	private final String id;
	private final String displayName;
	private final ChatFormatting colour;
	private final int unlockKills;

	DragonMaceRune(String id, String displayName, ChatFormatting colour, int unlockKills) {
		this.id = id;
		this.displayName = displayName;
		this.colour = colour;
		this.unlockKills = unlockKills;
	}

	/** The stable lowercase id written to saved data. */
	public String id() {
		return this.id;
	}

	/** The name shown in the GUI and in the item lore. */
	public String displayName() {
		return this.displayName;
	}

	/** The rune's own colour, used for its name everywhere. */
	public ChatFormatting colour() {
		return this.colour;
	}

	/** The number of qualifying kills needed to unlock this rune. */
	public int unlockKills() {
		return this.unlockKills;
	}

	/** True when {@code kills} qualifying kills are enough to have unlocked this rune. */
	public boolean isUnlockedAt(int kills) {
		return kills >= this.unlockKills;
	}

	/** Resolves a saved id, falling back to {@link #DEFAULT} for anything unknown. */
	public static Optional<DragonMaceRune> byId(String id) {
		if (id == null) {
			return Optional.empty();
		}

		for (DragonMaceRune rune : values()) {
			if (rune.id.equals(id)) {
				return Optional.of(rune);
			}
		}

		return Optional.empty();
	}

	/** Resolves a saved id, or {@link #DEFAULT} when it is missing or unknown. */
	public static DragonMaceRune byIdOrDefault(String id) {
		return byId(id).orElse(DEFAULT);
	}
}
