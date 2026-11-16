package com.lorenz.dragonmace;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Persistent ownership state of the unique Dragon Mace.
 *
 * <p>Transitions are explicit and one-way per edge; anything not listed in {@link #ALLOWED} is
 * rejected so a stale or duplicated event can never move the world into an inconsistent state.
 */
public enum DragonMaceOwnership {
	/** No Dragon Mace has ever been crafted in this world. */
	UNCRAFTED("uncrafted"),
	/** The unique Dragon Mace exists somewhere: an item entity, an inventory or a container. */
	PRESENT("present"),
	/** The Dragon Mace was genuinely destroyed and is waiting to be recovered. */
	MISSING("missing"),
	/** An administrator removed the mace on purpose; automatic recovery is disabled. */
	DELETED("deleted");

	private static final Map<DragonMaceOwnership, Set<DragonMaceOwnership>> ALLOWED = Map.of(
			UNCRAFTED, EnumSet.of(PRESENT),
			PRESENT, EnumSet.of(MISSING, DELETED),
			MISSING, EnumSet.of(PRESENT, DELETED),
			// PRESENT is the administrative spawn; UNCRAFTED is the administrative reset that hands the
			// world back to crafting after a deletion that actually removed the mace.
			DELETED, EnumSet.of(PRESENT, UNCRAFTED));

	private final String id;

	DragonMaceOwnership(String id) {
		this.id = id;
	}

	public String id() {
		return this.id;
	}

	public boolean canTransitionTo(DragonMaceOwnership target) {
		return ALLOWED.get(this).contains(target);
	}

	public static Optional<DragonMaceOwnership> byId(String id) {
		for (DragonMaceOwnership state : values()) {
			if (state.id.equals(id)) {
				return Optional.of(state);
			}
		}

		return Optional.empty();
	}
}
