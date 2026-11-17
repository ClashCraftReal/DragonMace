package com.lorenz.dragonmace;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One player's three independent rune selections.
 *
 * <p>Immutable, so a pending GUI selection can be handed around without risking the saved one. The
 * record is what gets persisted per player and what the item lore is built from, which is why the GUI
 * and the mace can never drift apart.
 */
public record DragonMaceRuneSelection(DragonMaceRune kill, DragonMaceRune attack, DragonMaceRune aura) {
	/** Serialised as three optional id strings, so an older save still loads. */
	public static final Codec<DragonMaceRuneSelection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.optionalFieldOf("kill", DragonMaceRune.DEFAULT.id())
					.forGetter(selection -> selection.kill.id()),
			Codec.STRING.optionalFieldOf("attack", DragonMaceRune.DEFAULT.id())
					.forGetter(selection -> selection.attack.id()),
			Codec.STRING.optionalFieldOf("aura", DragonMaceRune.DEFAULT.id())
					.forGetter(selection -> selection.aura.id())
	).apply(instance, DragonMaceRuneSelection::fromIds));

	/** Every category on {@link DragonMaceRune#DEFAULT}, the state of a player with no kills. */
	public static DragonMaceRuneSelection defaults() {
		return new DragonMaceRuneSelection(DragonMaceRune.DEFAULT, DragonMaceRune.DEFAULT,
				DragonMaceRune.DEFAULT);
	}

	/** Rebuilds a selection from three saved ids, falling back per field. */
	public static DragonMaceRuneSelection fromIds(String kill, String attack, String aura) {
		return new DragonMaceRuneSelection(DragonMaceRune.byIdOrDefault(kill),
				DragonMaceRune.byIdOrDefault(attack), DragonMaceRune.byIdOrDefault(aura));
	}

	/** Normalises a possibly-null selection to the defaults. */
	public static DragonMaceRuneSelection orDefaults(DragonMaceRuneSelection selection) {
		return selection == null ? defaults() : selection;
	}

	public DragonMaceRuneSelection {
		kill = kill == null ? DragonMaceRune.DEFAULT : kill;
		attack = attack == null ? DragonMaceRune.DEFAULT : attack;
		aura = aura == null ? DragonMaceRune.DEFAULT : aura;
	}

	/** The rune selected for {@code category}. */
	public DragonMaceRune get(DragonMaceRuneCategory category) {
		return switch (category) {
			case KILL -> this.kill;
			case ATTACK -> this.attack;
			case AURA -> this.aura;
		};
	}

	/** The same selection with {@code category} switched to {@code rune}. */
	public DragonMaceRuneSelection with(DragonMaceRuneCategory category, DragonMaceRune rune) {
		DragonMaceRune value = rune == null ? DragonMaceRune.DEFAULT : rune;

		return switch (category) {
			case KILL -> new DragonMaceRuneSelection(value, this.attack, this.aura);
			case ATTACK -> new DragonMaceRuneSelection(this.kill, value, this.aura);
			case AURA -> new DragonMaceRuneSelection(this.kill, this.attack, value);
		};
	}

	/** True when every selected rune is unlocked at {@code kills}. */
	public boolean isFullyUnlockedAt(int kills) {
		for (DragonMaceRuneCategory category : DragonMaceRuneCategory.values()) {
			if (!get(category).isUnlockedAt(kills)) {
				return false;
			}
		}

		return true;
	}

	/**
	 * The same selection with every rune that is not unlocked at {@code kills} reset to the default.
	 *
	 * <p>Used when loading saved data, so a hand-edited or downgraded save can never leave a player
	 * running a rune they have not earned.
	 */
	public DragonMaceRuneSelection sanitisedAt(int kills) {
		DragonMaceRuneSelection result = this;

		for (DragonMaceRuneCategory category : DragonMaceRuneCategory.values()) {
			DragonMaceRune rune = result.get(category);

			if (!rune.isUnlockedAt(kills)) {
				result = result.with(category, DragonMaceRune.DEFAULT);
			}
		}

		return result;
	}
}
