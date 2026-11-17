package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import com.lorenz.dragonmace.world.DragonMaceOfflinePlayerData;
import com.lorenz.dragonmace.world.DragonMaceOfflinePlayerData.PurgeResult;
import com.lorenz.dragonmace.world.DragonMaceWorldData;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression tests for {@code /dm remove} on an online and on an offline holder.
 *
 * <p>The command itself needs a live server, so the removal is exercised through the three seams it
 * is built from: the pure removal decision, the world record that stores the holder, and the saved
 * player data that carries the aura. The file tests write and read real compressed NBT, so the
 * offline path is covered end to end apart from the command dispatch.
 */
class DragonMaceOfflineRemovalTest {
	@BeforeAll
	static void setUp() {
		TestBootstrap.boot();
	}

	// ------------------------------------------------------------ online owner

	@Test
	void removingAnOnlineOwnerClearsTheRecordedHolder() {
		UUID owner = UUID.randomUUID();
		DragonMaceWorldData data = worldDataWith(owner);

		assertEquals(DragonMaceCurse.RemoveResult.REMOVED,
				DragonMaceCurse.decideRemoval(data.getCurseOwner(), owner));

		data.setCurseOwner(null);

		assertNull(data.getCurseOwner(), "the holder must be cleared");
		assertFalse(DragonMaceCurse.isActive(null, owner));
	}

	@Test
	void anOnlineOwnerIsNoLongerActiveAfterRemoval() {
		UUID owner = UUID.randomUUID();
		DragonMaceWorldData data = worldDataWith(owner);

		assertTrue(DragonMaceCurse.shouldApplyOnJoin(data.getCurseOwner(), owner));

		data.setCurseOwner(null);

		assertFalse(DragonMaceCurse.shouldApplyOnJoin(data.getCurseOwner(), owner));
	}

	// ----------------------------------------------------------- offline owner

	@Test
	void removingAnOfflineOwnerPurgesTheSavedAura(@TempDir Path directory) throws Exception {
		UUID owner = UUID.randomUUID();
		Path file = writePlayerData(directory, owner, auraTag(), unrelatedKeys());

		assertEquals(PurgeResult.PURGED, DragonMaceOfflinePlayerData.purgeAura(file));

		CompoundTag saved = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
		assertFalse(saved.contains(DragonMaceOfflinePlayerData.ACTIVE_EFFECTS_KEY),
				"the aura list must be gone once it is empty");
	}

	@Test
	void offlinePurgeKeepsEveryUnrelatedKey(@TempDir Path directory) throws Exception {
		UUID owner = UUID.randomUUID();
		CompoundTag original = unrelatedKeys();
		Path file = writePlayerData(directory, owner, auraTag(), original);

		DragonMaceOfflinePlayerData.purgeAura(file);

		CompoundTag saved = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());

		assertEquals(original.getIntOr("XpLevel", -1), saved.getIntOr("XpLevel", -2));
		assertEquals(original.getStringOr("Dimension", ""), saved.getStringOr("Dimension", "?"));
		assertEquals(original.getFloatOr("Health", 0.0F), saved.getFloatOr("Health", -1.0F));
		assertEquals(original.getListOrEmpty("Inventory").size(), saved.getListOrEmpty("Inventory").size());
		assertEquals("minecraft:stick",
				saved.getListOrEmpty("Inventory").getCompoundOrEmpty(0).getStringOr("id", ""));
	}

	@Test
	void offlinePurgeKeepsOtherEffects(@TempDir Path directory) throws Exception {
		UUID owner = UUID.randomUUID();
		ListTag effects = new ListTag();
		effects.add(auraTag());
		effects.add(effectTag("minecraft:speed", 1, 600));
		Path file = writePlayerData(directory, owner, effects, unrelatedKeys());

		assertEquals(PurgeResult.PURGED, DragonMaceOfflinePlayerData.purgeAura(file));

		ListTag saved = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap())
				.getListOrEmpty(DragonMaceOfflinePlayerData.ACTIVE_EFFECTS_KEY);

		assertEquals(1, saved.size(), "only the aura may be removed");
		assertEquals("minecraft:speed", saved.getCompoundOrEmpty(0).getStringOr("id", ""));
	}

	@Test
	void aFiniteGlowingEffectIsNotTheAura(@TempDir Path directory) throws Exception {
		UUID owner = UUID.randomUUID();
		ListTag effects = new ListTag();
		effects.add(effectTag("minecraft:glowing", 0, 200));
		Path file = writePlayerData(directory, owner, effects, unrelatedKeys());
		byte[] before = Files.readAllBytes(file);

		assertEquals(PurgeResult.NOTHING_TO_PURGE, DragonMaceOfflinePlayerData.purgeAura(file));
		assertArrayEquals(before, Files.readAllBytes(file), "an untouched file must not be rewritten");
	}

	@Test
	void anAmplifiedGlowingEffectIsNotTheAura() {
		assertFalse(DragonMaceOfflinePlayerData.isAuraEffect(effectTag("minecraft:glowing", 1, -1)));
		assertTrue(DragonMaceOfflinePlayerData.isAuraEffect(effectTag("minecraft:glowing", 0, -1)));
	}

	@Test
	void aPlayerWithoutSavedDataIsReportedAsNoData(@TempDir Path directory) {
		Path missing = directory.resolve(UUID.randomUUID() + ".dat");

		assertEquals(PurgeResult.NO_DATA, DragonMaceOfflinePlayerData.purgeAura(missing));
		assertFalse(Files.exists(missing), "removal must never create saved data");
	}

	@Test
	void aNullFileIsReportedAsNoData() {
		assertEquals(PurgeResult.NO_DATA, DragonMaceOfflinePlayerData.purgeAura((Path) null));
	}

	@Test
	void aNullTagIsNotPurged() {
		assertFalse(DragonMaceOfflinePlayerData.purgeAura((CompoundTag) null));
	}

	// ------------------------------------------------- joining after removal

	@Test
	void anOfflineOwnerJoiningAfterRemovalDoesNotRegainTheCurse() {
		UUID owner = UUID.randomUUID();
		DragonMaceWorldData data = worldDataWith(owner);

		assertEquals(DragonMaceCurse.RemoveResult.REMOVED,
				DragonMaceCurse.decideRemoval(data.getCurseOwner(), owner));

		data.setCurseOwner(null);

		assertFalse(DragonMaceCurse.shouldApplyOnJoin(data.getCurseOwner(), owner),
				"a removed holder must not be re-cursed on join");
		assertFalse(DragonMaceCurse.shouldApplyOnJoin(data.getCurseOwner(), UUID.randomUUID()));
	}

	// ------------------------------------------------------- refused removals

	@Test
	void removingAPlayerWhoDoesNotOwnTheCurseIsRefused() {
		UUID owner = UUID.randomUUID();
		UUID other = UUID.randomUUID();

		assertEquals(DragonMaceCurse.RemoveResult.NOT_OWNER,
				DragonMaceCurse.decideRemoval(owner, other));
	}

	@Test
	void removingAnUnknownPlayerIsRefused() {
		assertEquals(DragonMaceCurse.RemoveResult.NOT_OWNER,
				DragonMaceCurse.decideRemoval(null, UUID.randomUUID()));
		assertEquals(DragonMaceCurse.RemoveResult.NO_TARGET,
				DragonMaceCurse.decideRemoval(UUID.randomUUID(), null));
		assertEquals(DragonMaceCurse.RemoveResult.NO_TARGET,
				DragonMaceCurse.decideRemoval(null, null));
	}

	@Test
	void anotherPlayersCurseIsUnaffected() {
		UUID owner = UUID.randomUUID();
		UUID other = UUID.randomUUID();
		DragonMaceWorldData data = worldDataWith(owner);

		assertEquals(DragonMaceCurse.RemoveResult.NOT_OWNER,
				DragonMaceCurse.decideRemoval(data.getCurseOwner(), other));
		assertEquals(owner, data.getCurseOwner(), "the active holder must be untouched");
		assertTrue(DragonMaceCurse.shouldApplyOnJoin(data.getCurseOwner(), owner));
	}

	// ------------------------------------------------------ no core is issued

	@Test
	void offlineRemovalDoesNotCreateAVeryHeavyCore(@TempDir Path directory) throws Exception {
		UUID owner = UUID.randomUUID();
		Path file = writePlayerData(directory, owner, auraTag(), unrelatedKeys());

		DragonMaceOfflinePlayerData.purgeAura(file);

		CompoundTag saved = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
		ListTag inventory = saved.getListOrEmpty("Inventory");

		assertEquals(1, inventory.size(), "the inventory must keep exactly its one stack");
		assertEquals("minecraft:stick", inventory.getCompoundOrEmpty(0).getStringOr("id", ""));
		assertFalse(saved.toString().contains("very_heavy_core"),
				"no Very Heavy Core marker may be written into the player data");
		assertFalse(saved.toString().contains("minecraft:paper"),
				"no paper stack may be written into the player data");
	}

	// ------------------------------------------------------------- utilities

	private static DragonMaceWorldData worldDataWith(UUID owner) {
		return new DragonMaceWorldData(false, Optional.empty(), 0, Optional.empty(), Optional.empty(),
				Optional.of(owner.toString()));
	}

	private static CompoundTag auraTag() {
		return effectTag(DragonMaceOfflinePlayerData.AURA_EFFECT_ID, 0,
				DragonMaceOfflinePlayerData.INFINITE_DURATION);
	}

	private static CompoundTag effectTag(String id, int amplifier, int duration) {
		CompoundTag effect = new CompoundTag();
		effect.putString("id", id);
		effect.putInt("amplifier", amplifier);
		effect.putInt("duration", duration);
		effect.putBoolean("ambient", false);
		effect.putBoolean("show_particles", false);
		effect.putBoolean("show_icon", false);
		return effect;
	}

	private static CompoundTag unrelatedKeys() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("XpLevel", 17);
		tag.putString("Dimension", "minecraft:overworld");
		tag.putFloat("Health", 20.0F);

		CompoundTag stack = new CompoundTag();
		stack.putString("id", "minecraft:stick");
		stack.putInt("count", 1);
		stack.putInt("Slot", 0);

		ListTag inventory = new ListTag();
		inventory.add(stack);
		tag.put("Inventory", inventory);
		return tag;
	}

	private static Path writePlayerData(Path directory, UUID owner, CompoundTag effects,
			CompoundTag unrelated) throws Exception {
		ListTag list = new ListTag();
		list.add(effects);
		return writePlayerData(directory, owner, list, unrelated);
	}

	private static Path writePlayerData(Path directory, UUID owner, ListTag effects,
			CompoundTag unrelated) throws Exception {
		CompoundTag tag = unrelated.copy();
		tag.put(DragonMaceOfflinePlayerData.ACTIVE_EFFECTS_KEY, effects);

		Path file = directory.resolve(owner + ".dat");
		NbtIo.writeCompressed(tag, file);
		assertNotNull(file);
		return file;
	}
}
