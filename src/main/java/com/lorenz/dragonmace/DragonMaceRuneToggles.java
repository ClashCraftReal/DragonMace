package com.lorenz.dragonmace;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One player's three independent rune on/off switches.
 *
 * <p>Separate from {@link DragonMaceRuneSelection} on purpose: a selection says <em>which</em> rune a
 * category runs, a toggle says <em>whether</em> that category runs at all. Turning a category off never
 * touches its selected rune, so switching it back on restores exactly the rune the player had chosen.
 *
 * <p>Every category defaults to on, which is what an older save without the field decodes to, so adding
 * the toggles cannot silently disable an existing player's runes.
 */
public record DragonMaceRuneToggles(boolean kill, boolean attack, boolean aura) {
	/** Serialised as three optional booleans, so an older save still loads with everything enabled. */
	public static final Codec<DragonMaceRuneToggles> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("kill", true).forGetter(DragonMaceRuneToggles::kill),
			Codec.BOOL.optionalFieldOf("attack", true).forGetter(DragonMaceRuneToggles::attack),
			Codec.BOOL.optionalFieldOf("aura", true).forGetter(DragonMaceRuneToggles::aura)
	).apply(instance, DragonMaceRuneToggles::new));

	/** Every category enabled, the state of a player who has never toggled anything. */
	public static DragonMaceRuneToggles defaults() {
		return new DragonMaceRuneToggles(true, true, true);
	}

	/** Normalises a possibly-null value to the defaults. */
	public static DragonMaceRuneToggles orDefaults(DragonMaceRuneToggles toggles) {
		return toggles == null ? defaults() : toggles;
	}

	/** Whether {@code category} is enabled. */
	public boolean get(DragonMaceRuneCategory category) {
		return switch (category) {
			case KILL -> this.kill;
			case ATTACK -> this.attack;
			case AURA -> this.aura;
		};
	}

	/** The same toggles with {@code category} set to {@code enabled}. */
	public DragonMaceRuneToggles with(DragonMaceRuneCategory category, boolean enabled) {
		return switch (category) {
			case KILL -> new DragonMaceRuneToggles(enabled, this.attack, this.aura);
			case ATTACK -> new DragonMaceRuneToggles(this.kill, enabled, this.aura);
			case AURA -> new DragonMaceRuneToggles(this.kill, this.attack, enabled);
		};
	}

	/** The same toggles with {@code category} flipped. */
	public DragonMaceRuneToggles toggled(DragonMaceRuneCategory category) {
		return with(category, !get(category));
	}

	/** True when every category is enabled. */
	public boolean allEnabled() {
		return this.kill && this.attack && this.aura;
	}
}
