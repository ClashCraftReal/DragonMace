package com.lorenz.dragonmace;

import java.util.ArrayList;
import java.util.List;

import com.lorenz.dragonmace.world.DragonMaceWorldData;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndPodiumFeature;

/**
 * Destruction detection, recovery and administrative removal for the unique Dragon Mace.
 *
 * <p>The persistent state machine is the authority on whether the mace exists. Loaded-entity scans
 * are only used as a duplicate guard before spawning, never as the trigger for a recovery, so an
 * unloaded chunk or an offline player can never be mistaken for a destroyed mace.
 */
public final class DragonMaceRespawn {
	/** Ticks to wait before the one-shot startup reconciliation, so spawn chunks are loaded. */
	private static final int RECONCILE_DELAY_TICKS = 100;

	private static MinecraftServer reconciledServer;
	private static int ticksSinceStart;

	private DragonMaceRespawn() {
	}

	/**
	 * Classifies a permanent removal reason plus the stack at removal time.
	 *
	 * <p>Pickup discards the item entity only after {@code Inventory.add} emptied the stack (vanilla
	 * restores the count afterwards), so an empty stack identifies a pickup. This helper is kept for
	 * the removal-reason rules; the live void hook deliberately uses the more specific
	 * {@code Entity.onBelowWorld()} event, not a generic discard reason.
	 */
	public static boolean isDestruction(RemovalReason reason, ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}

		return reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED;
	}

	/** Pure position resolution, kept separate so both configurations can be unit tested. */
	public static BlockPos computeSpawnPosition(DragonMaceConfig.RespawnLocation location,
			BlockPos fountainColumn, int fountainSurfaceY, BlockPos worldSpawnColumn, int worldSpawnSurfaceY) {
		return switch (location) {
			case END_FOUNTAIN -> new BlockPos(fountainColumn.getX(), fountainSurfaceY + 1, fountainColumn.getZ());
			case WORLD_SPAWN -> new BlockPos(worldSpawnColumn.getX(), worldSpawnSurfaceY + 1, worldSpawnColumn.getZ());
		};
	}

	public static boolean anyDragonMaceExists(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				// Removed entities can still be returned while the entity list is being processed. The
				// void hook marks the mace MISSING at the head of onBelowWorld, before discard(), so a
				// removed entity must never be mistaken for a surviving mace during the same tick.
				if (entity.isRemoved()) {
					continue;
				}

				if (entity instanceof ItemEntity item && DragonMaceIdentity.isDragonMace(item.getItem())) {
					return true;
				}

				if (entity instanceof ItemFrame frame && DragonMaceIdentity.isDragonMace(frame.getItem())) {
					return true;
				}
			}
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (inventoryContainsDragonMace(player.getInventory())) {
				return true;
			}
		}

		return false;
	}

	public static boolean inventoryContainsDragonMace(Inventory inventory) {
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (DragonMaceIdentity.isDragonMace(inventory.getItem(slot))) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Spawns the mace at the configured location.
	 *
	 * <p>The entity is inserted before the state is written, so a failed insertion can never leave the
	 * world claiming {@link DragonMaceOwnership#PRESENT} without a mace. The duplicate scan still runs
	 * first, so an existing mace always wins over a stale {@code MISSING}/{@code DELETED} state.
	 *
	 * <p>The old owner is cleared only after the insertion and the PRESENT transition succeeded, so a
	 * respawned mace is unowned until a player actually picks it up.
	 *
	 * @return {@code true} only when a new item entity was actually added and the state moved to PRESENT
	 */
	public static boolean spawn(MinecraftServer server) {
		DragonMaceWorldData data = DragonMaceWorldData.get(server);

		if (data.getState() == DragonMaceOwnership.PRESENT) {
			return false;
		}

		// Resolved first because it force-loads and fully loads the target chunk; the duplicate scan
		// below can then see a mace that is sitting at the respawn location in an otherwise unloaded
		// chunk. Only the chunk that is actually used is forced.
		SpawnTarget target = resolveTarget(server);

		if (target == null) {
			return false;
		}

		// A mace that still exists somewhere wins over a stale MISSING/DELETED state.
		if (anyDragonMaceExists(server)) {
			if (data.markPresent()) {
				DragonMaceWorldData.save(server);
			}

			releaseTemporaryForce(target);
			return false;
		}

		ItemStack stack = DragonMaceRecipes.createDragonMaceStack();
		ItemEntity entity = new ItemEntity(target.level(),
				target.pos().getX() + 0.5D, target.pos().getY() + 0.5D, target.pos().getZ() + 0.5D, stack);
		// Both flags are also applied by ItemEntityMixin, but applying them explicitly keeps the
		// administrative path independent of the mixin order. The item still has pickup delay 0.
		DragonMaceProtection.protectItemEntity(entity);

		boolean added = target.level().addFreshEntity(entity);

		// addFreshEntity() returns the real entity-manager insertion result. Do not use a proximity or
		// visibility lookup here: a correctly added entity can be outside the entity-ticking range.
		if (!added || entity.isRemoved()) {
			if (!entity.isRemoved()) {
				entity.setRemoved(RemovalReason.UNLOADED_TO_CHUNK);
			}

			releaseTemporaryForce(target);
			DragonMace.LOGGER.warn("Could not add the Dragon Mace entity at {}; the saved state was left unchanged.",
					target.pos());
			return false;
		}

		if (!commitSpawn(data, true)) {
			// The state machine refused the transition, so the new entity must not survive as a duplicate.
			// UNLOADED_TO_CHUNK is used because it is the one removal reason that never marks the mace MISSING.
			entity.setRemoved(RemovalReason.UNLOADED_TO_CHUNK);
			releaseTemporaryForce(target);
			DragonMace.LOGGER.warn("The Dragon Mace state refused the spawn transition; removed the new entity again.");
			return false;
		}

		data.incrementRespawnCount();
		data.setLastDimension(target.level().dimension().identifier().toString());
		DragonMaceWorldData.save(server);
		DragonMaceAnnouncements.respawned(server, target.locationDescription());

		DragonMace.LOGGER.info("Dragon Mace respawned at {} in {} (respawn #{})",
				target.pos(), target.level().dimension().identifier(), data.getRespawnCount());
		return true;
	}

	/**
	 * Applies the state change for a spawn whose entity insertion already succeeded.
	 *
	 * <p>The owner is cleared in the same transition so a mace summoned by an operator has no player
	 * owner until the first successful pickup.
	 *
	 * @param inserted whether the item entity was actually added to the level
	 * @return {@code true} when the world is now PRESENT because of this spawn
	 */
	public static boolean commitSpawn(DragonMaceWorldData data, boolean inserted) {
		if (!inserted) {
			return false;
		}

		if (!data.markPresent()) {
			return false;
		}

		data.setOwner(null);
		return true;
	}

	/** What {@code /spawndragonmace} should do for a saved state plus the loaded-world scan. */
	public enum SpawnDecision {
		/** Nothing exists and nothing is saved as present: spawn a new mace. */
		SPAWN,
		/** The state is PRESENT and a mace is loaded: refuse, the mace already exists. */
		ALREADY_PRESENT,
		/** A mace is loaded but the state is not PRESENT: adopt it instead of spawning a second one. */
		FOUND_EXISTING,
		/** The state is PRESENT but no mace is loaded: refuse, it may be in an unloaded chunk. */
		STATE_SAYS_PRESENT_BUT_NOT_FOUND
	}

	public static SpawnDecision decideSpawn(DragonMaceOwnership state, boolean found) {
		if (state == DragonMaceOwnership.PRESENT) {
			return found ? SpawnDecision.ALREADY_PRESENT : SpawnDecision.STATE_SAYS_PRESENT_BUT_NOT_FOUND;
		}

		return found ? SpawnDecision.FOUND_EXISTING : SpawnDecision.SPAWN;
	}

	/** What the one-shot startup reconciliation should do. */
	public enum Reconcile {
		/** Saved state and loaded world agree, nothing to do. */
		NONE,
		/** A mace is loaded but the world is still UNCRAFTED: adopt it. */
		ADOPT_EXISTING,
		/** The world is PRESENT but no mace is loaded: warn, never demote. */
		PRESENT_NOT_FOUND
	}

	/**
	 * Decides the startup reconciliation.
	 *
	 * <p>The scan can only see loaded chunks, so a missing mace is never treated as proof that the mace
	 * is gone: the state is only ever promoted, never demoted.
	 */
	public static Reconcile reconcileDecision(DragonMaceOwnership state, boolean found) {
		if (state == DragonMaceOwnership.UNCRAFTED && found) {
			return Reconcile.ADOPT_EXISTING;
		}

		if (state == DragonMaceOwnership.PRESENT && !found) {
			return Reconcile.PRESENT_NOT_FOUND;
		}

		return Reconcile.NONE;
	}

	/** What an automatic tick may do for a MISSING mace. It may never create a new entity. */
	public enum MissingAction {
		/** The saved state is not MISSING. */
		NONE,
		/** A mace is loaded, so the stale MISSING state is repaired instead of duplicating it. */
		ADOPT_EXISTING,
		/** No mace is loaded: wait for an operator to use the administrative spawn command. */
		AWAIT_OPERATOR
	}

	/**
	 * Decides the automatic MISSING-state handling.
	 *
	 * <p>{@link MissingAction#AWAIT_OPERATOR} is intentionally a no-op: a destroyed mace is never
	 * respawned automatically. Only {@code /spawndragonmace} may create it again.
	 */
	public static MissingAction missingAction(DragonMaceOwnership state, boolean found) {
		if (state != DragonMaceOwnership.MISSING) {
			return MissingAction.NONE;
		}

		return found ? MissingAction.ADOPT_EXISTING : MissingAction.AWAIT_OPERATOR;
	}

	/**
	 * Records a genuine void destruction.
	 *
	 * <p>Kept separate from the live entity so the state transition and its save round trip can be
	 * tested without a server.
	 */
	public static boolean markVoidDestroyed(DragonMaceWorldData data) {
		return data != null && data.markMissing();
	}

	/**
	 * Live void handling, called from {@code Entity.onBelowWorld()} before vanilla discards the entity.
	 *
	 * <p>This is the actual 1.21.11 void path: {@code Entity.checkBelowWorld()} calls
	 * {@code onBelowWorld()}, which discards the entity. Ordinary pickups, chunk unloads, dimension
	 * changes and merges never pass through this method, so they cannot mark the mace MISSING.
	 */
	public static boolean handleVoidDestruction(ItemEntity entity) {
		if (entity == null || !DragonMaceIdentity.isDragonMace(entity.getItem())) {
			return false;
		}

		MinecraftServer server = entity.level().getServer();

		if (server == null) {
			return false;
		}

		DragonMaceWorldData data = DragonMaceWorldData.get(server);

		if (!markVoidDestroyed(data)) {
			return false;
		}

		DragonMaceWorldData.save(server);
		DragonMaceAnnouncements.lostToVoid(server);
		DragonMace.LOGGER.info("The Dragon Mace fell into the void; saved state is MISSING and manual recovery is required.");
		return true;
	}

	/**
	 * Runs once per server tick.
	 *
	 * <p>A MISSING mace with no loaded entity is left alone so an operator must explicitly summon it.
	 * A MISSING mace that is still loaded is adopted to repair a stale state; that is not a respawn.
	 */
	public static void tick(MinecraftServer server) {
		DragonMaceWorldData data = DragonMaceWorldData.get(server);

		if (reconciledServer != server) {
			reconciledServer = server;
			ticksSinceStart = 0;
		} else {
			ticksSinceStart++;
		}

		// Chunks are still loading during the first ticks, so the scan waits for the world to settle.
		if (ticksSinceStart == RECONCILE_DELAY_TICKS) {
			reconcile(server, data);
		}

		// The world scan is only needed for a MISSING state; every other state stays a cheap no-op.
		MissingAction action = data.getState() == DragonMaceOwnership.MISSING
				? missingAction(data.getState(), anyDragonMaceExists(server))
				: MissingAction.NONE;

		switch (action) {
			case ADOPT_EXISTING -> {
				if (data.markPresent()) {
					DragonMaceWorldData.save(server);
					DragonMace.LOGGER.info("Dragon Mace still exists; cleared the stale MISSING state.");
				}
			}
			case AWAIT_OPERATOR -> {
				// Deliberately no-op: void destruction requires /spawndragonmace.
			}
			case NONE -> {
			}
		}
	}

	static void reconcile(MinecraftServer server, DragonMaceWorldData data) {
		switch (reconcileDecision(data.getState(), anyDragonMaceExists(server))) {
			case ADOPT_EXISTING -> {
				if (data.markPresent()) {
					DragonMaceWorldData.save(server);
					DragonMace.LOGGER.info("A Dragon Mace was found in the world; saved state reconciled to PRESENT.");
				}
			}
			case PRESENT_NOT_FOUND -> {
				// Deliberately silent. A PRESENT state with no loaded mace is the normal case for a mace
				// sitting in an unloaded chunk or an offline player's inventory, so it is not an error and
				// must not spam the console. The saved state is left untouched and the unique-mace
				// protection is unaffected; only genuine failures are logged.
			}
			case NONE -> {
			}
		}
	}

	/** Removes every marked mace from loaded item entities, item frames and online inventories. */
	public static int deleteAll(MinecraftServer server) {
		int removed = 0;

		for (ServerLevel level : server.getAllLevels()) {
			List<Entity> marked = new ArrayList<>();

			for (Entity entity : level.getAllEntities()) {
				if (entity instanceof ItemEntity item && DragonMaceIdentity.isDragonMace(item.getItem())) {
					marked.add(entity);
				} else if (entity instanceof ItemFrame frame && DragonMaceIdentity.isDragonMace(frame.getItem())) {
					marked.add(entity);
				}
			}

			// Collected first so the entity list is not mutated while it is being iterated.
			for (Entity entity : marked) {
				if (entity instanceof ItemFrame frame) {
					frame.setItem(ItemStack.EMPTY);
				} else {
					entity.discard();
				}

				removed++;
			}
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Inventory inventory = player.getInventory();

			for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
				if (DragonMaceIdentity.isDragonMace(inventory.getItem(slot))) {
					inventory.setItem(slot, ItemStack.EMPTY);
					removed++;
				}
			}
		}

		return removed;
	}

	/**
	 * Applies the administrative reset for a deletion that already ran.
	 *
	 * <p>The reset is deliberately conditional: it only happens when the removal actually took at least
	 * one marked stack out of the world. A deletion that found nothing (the mace sits in an unloaded
	 * chunk, an offline player's inventory or an unscanned container) leaves the state {@code DELETED},
	 * so crafting stays blocked and no second mace can be created next to the old one.
	 *
	 * @param data    the world data whose state was already moved to {@code DELETED}
	 * @param removed number of stacks {@link #deleteAll} actually removed
	 * @return {@code true} when the world is {@code UNCRAFTED} again and may craft a new mace
	 */
	public static boolean completeDeletion(DragonMaceWorldData data, int removed) {
		if (removed <= 0) {
			return false;
		}

		return data.resetToUncrafted();
	}

	/**
	 * Removes marked maces from containers in already-loaded chunks around a centre.
	 *
	 * <p>Bounded on purpose: only chunks that are currently loaded are visited, so this never forces
	 * chunk generation or walks the whole world. Storage in unloaded chunks is out of reach and is
	 * documented as a manual step.
	 */
	public static int deleteInLoadedContainers(ServerLevel level, BlockPos center, int radiusBlocks) {
		int removed = 0;
		int minChunkX = (center.getX() - radiusBlocks) >> 4;
		int maxChunkX = (center.getX() + radiusBlocks) >> 4;
		int minChunkZ = (center.getZ() - radiusBlocks) >> 4;
		int maxChunkZ = (center.getZ() + radiusBlocks) >> 4;

		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);

				if (chunk == null) {
					continue;
				}

				for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
					if (!(blockEntity instanceof Container container)) {
						continue;
					}

					for (int slot = 0; slot < container.getContainerSize(); slot++) {
						if (DragonMaceIdentity.isDragonMace(container.getItem(slot))) {
							container.setItem(slot, ItemStack.EMPTY);
							removed++;
						}
					}
				}
			}
		}

		return removed;
	}

	/**
	 * Outcome of a bounded recovery pass.
	 *
	 * @param recovered  stacks moved into the destination inventory
	 * @param leftBehind stacks that stayed where they were because the destination was full
	 */
	public record Recovery(int recovered, int leftBehind) {
		public int total() {
			return this.recovered + this.leftBehind;
		}

		public Recovery plus(Recovery other) {
			return new Recovery(this.recovered + other.recovered, this.leftBehind + other.leftBehind);
		}
	}

	/**
	 * Moves marked maces out of containers in already-loaded chunks into {@code target}.
	 *
	 * <p>Same bound as {@link #deleteInLoadedContainers}: only loaded chunks are visited, so no chunk
	 * is generated and the whole world is never walked. A stack that does not fit stays in its
	 * container instead of being deleted, so a failed recovery can never destroy the mace.
	 */
	public static Recovery recoverFromLoadedContainers(ServerLevel level, BlockPos center, int radiusBlocks,
			Inventory target) {
		int recovered = 0;
		int leftBehind = 0;
		int minChunkX = (center.getX() - radiusBlocks) >> 4;
		int maxChunkX = (center.getX() + radiusBlocks) >> 4;
		int minChunkZ = (center.getZ() - radiusBlocks) >> 4;
		int maxChunkZ = (center.getZ() + radiusBlocks) >> 4;

		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);

				if (chunk == null) {
					continue;
				}

				for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
					if (!(blockEntity instanceof Container container)) {
						continue;
					}

					for (int slot = 0; slot < container.getContainerSize(); slot++) {
						ItemStack stack = container.getItem(slot);

						if (!DragonMaceIdentity.isDragonMace(stack)) {
							continue;
						}

						ItemStack moving = stack.copy();
						target.add(moving);

						if (moving.isEmpty()) {
							container.setItem(slot, ItemStack.EMPTY);
							recovered++;
						} else {
							// Keep whatever did not fit so a full inventory cannot delete the mace.
							container.setItem(slot, moving);
							leftBehind++;
						}
					}
				}
			}
		}

		return new Recovery(recovered, leftBehind);
	}

	/**
	 * Moves marked maces out of the ender chests of online players.
	 *
	 * <p>An ender chest is a per-player {@code PlayerEnderChestContainer}, not a block entity, so it
	 * is unreachable from any chunk scan. Only online players can be inspected; an offline player's
	 * ender chest stays untouched until they log in and run the command again.
	 */
	public static Recovery recoverFromEnderChests(MinecraftServer server, Inventory target) {
		int recovered = 0;
		int leftBehind = 0;

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Container enderChest = player.getEnderChestInventory();

			for (int slot = 0; slot < enderChest.getContainerSize(); slot++) {
				ItemStack stack = enderChest.getItem(slot);

				if (!DragonMaceIdentity.isDragonMace(stack)) {
					continue;
				}

				ItemStack moving = stack.copy();
				target.add(moving);

				if (moving.isEmpty()) {
					enderChest.setItem(slot, ItemStack.EMPTY);
					recovered++;
				} else {
					enderChest.setItem(slot, moving);
					leftBehind++;
				}
			}
		}

		return new Recovery(recovered, leftBehind);
	}

	/** Moves marked maces out of item frames within {@code radiusBlocks} of {@code center}. */
	public static Recovery recoverFromItemFrames(ServerLevel level, BlockPos center, int radiusBlocks,
			Inventory target) {
		int recovered = 0;
		int leftBehind = 0;
		double radiusSquared = (double) radiusBlocks * radiusBlocks;

		for (Entity entity : level.getAllEntities()) {
			if (!(entity instanceof ItemFrame frame) || frame.blockPosition().distSqr(center) > radiusSquared) {
				continue;
			}

			ItemStack stack = frame.getItem();

			if (!DragonMaceIdentity.isDragonMace(stack)) {
				continue;
			}

			ItemStack moving = stack.copy();
			target.add(moving);

			if (moving.isEmpty()) {
				frame.setItem(ItemStack.EMPTY);
				recovered++;
			} else {
				frame.setItem(moving);
				leftBehind++;
			}
		}

		return new Recovery(recovered, leftBehind);
	}

	private record SpawnTarget(ServerLevel level, BlockPos pos, DragonMaceConfig.RespawnLocation location,
			boolean newlyForced) {
		String locationDescription() {
			return this.location == DragonMaceConfig.RespawnLocation.END_FOUNTAIN
					? "the End Fountain"
					: "the world spawn";
		}
	}

	private static SpawnTarget resolveTarget(MinecraftServer server) {
		DragonMaceConfig.RespawnLocation location = DragonMaceConfig.get().respawnLocation();
		ServerLevel overworld = server.overworld();
		ServerLevel end = server.getLevel(Level.END);

		if (location == DragonMaceConfig.RespawnLocation.END_FOUNTAIN && end == null) {
			DragonMace.LOGGER.warn("The End is not loaded; falling back to the overworld spawn.");
			location = DragonMaceConfig.RespawnLocation.WORLD_SPAWN;
		}

		// EndPodiumFeature.getLocation resolves the fountain column (0, 0); its Y comes from the heightmap.
		BlockPos fountainColumn = EndPodiumFeature.getLocation(BlockPos.ZERO);
		BlockPos worldSpawnColumn = overworld.getRespawnData().pos();

		// The target chunk must be fully loaded before the heightmap is read and before the entity is
		// added. Level.getHeight() silently returns minY for an unloaded chunk, which used to place the
		// mace underneath the End and let it fall into the void immediately. Only the chunk that is
		// actually used is forced; unrelated chunks are never loaded.
		ServerLevel targetLevel = location == DragonMaceConfig.RespawnLocation.END_FOUNTAIN ? end : overworld;
		BlockPos targetColumn = location == DragonMaceConfig.RespawnLocation.END_FOUNTAIN
				? fountainColumn
				: worldSpawnColumn;

		if (targetLevel == null) {
			DragonMace.LOGGER.warn("The Dragon Mace respawn level is not available.");
			return null;
		}

		int chunkX = targetColumn.getX() >> 4;
		int chunkZ = targetColumn.getZ() >> 4;
		boolean newlyForced = targetLevel.setChunkForced(chunkX, chunkZ, true);
		// setChunkForced() only calls getChunk() when the ticket was newly added. Loading explicitly
		// also covers a chunk that was already in the forced set but not yet loaded, and guarantees the
		// heightmap is populated before it is read.
		targetLevel.getChunk(chunkX, chunkZ);

		int fountainSurfaceY = 0;
		int worldSpawnSurfaceY = 0;

		if (location == DragonMaceConfig.RespawnLocation.END_FOUNTAIN) {
			fountainSurfaceY = targetLevel.getHeight(Heightmap.Types.MOTION_BLOCKING,
					fountainColumn.getX(), fountainColumn.getZ());
		} else {
			worldSpawnSurfaceY = targetLevel.getHeight(Heightmap.Types.MOTION_BLOCKING,
					worldSpawnColumn.getX(), worldSpawnColumn.getZ());
		}

		BlockPos pos = computeSpawnPosition(location, fountainColumn, fountainSurfaceY,
				worldSpawnColumn, worldSpawnSurfaceY);

		if (targetLevel.isOutsideBuildHeight(pos) || pos.getY() <= targetLevel.getMinY()) {
			if (newlyForced) {
				targetLevel.setChunkForced(chunkX, chunkZ, false);
			}

			DragonMace.LOGGER.warn("Resolved Dragon Mace respawn position {} is not usable.", pos);
			return null;
		}

		return new SpawnTarget(targetLevel, pos, location, newlyForced);
	}

	/** Releases a force ticket that this spawn added, but only when the spawn did not commit. */
	private static void releaseTemporaryForce(SpawnTarget target) {
		if (target.newlyForced()) {
			target.level().setChunkForced(target.pos().getX() >> 4, target.pos().getZ() >> 4, false);
		}
	}
}
