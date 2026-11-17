package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import com.lorenz.dragonmace.command.DragonMaceCommands;
import com.lorenz.dragonmace.world.DragonMaceOfflinePlayerData;
import com.lorenz.dragonmace.world.DragonMaceOfflinePlayerData.DirectoryPurge;
import com.lorenz.dragonmace.world.DragonMaceWorldData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression tests for the global {@code /dm remove}.
 *
 * <p>The command needs a live server, so the global cleanup is exercised through the two seams it is
 * built from: the world record that stores the single holder, and the {@code playerdata} directory
 * purge. The directory tests write and read real compressed NBT, so multiple offline holders, a
 * malformed file and the "no file is created" rule are covered end to end.
 */
class DragonMaceGlobalRemovalTest {
	@BeforeAll
	static void setUp() {
		TestBootstrap.boot();
	}

	// ------------------------------------------------------- global owner state

	@Test
	void clearingTheOwnerLeavesNoHolder() {
		UUID owner = UUID.randomUUID();
		DragonMaceWorldData data = worldDataWith(owner);

		data.setCurseOwner(null);

		assertNull(data.getCurseOwner());
		assertFalse(DragonMaceCurse.isActive(null, owner));
	}

	@Test
	void aReloadedWorldHasNoHolder() {
		DragonMaceWorldData data = worldDataWith(UUID.randomUUID());
		data.setCurseOwner(null);

		DragonMaceWorldData reloaded = new DragonMaceWorldData(false, Optional.empty(), 0, Optional.empty(),
				Optional.empty(), Optional.empty());

		assertNull(reloaded.getCurseOwner(), "a restart must not resurrect the curse");
		assertFalse(DragonMaceCurse.shouldApplyOnJoin(reloaded.getCurseOwner(), UUID.randomUUID()));
	}

	// ------------------------------------------------------- multiple offline files

	@Test
	void everySavedPlayerFileIsPurged(@TempDir Path directory) throws Exception {
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		UUID third = UUID.randomUUID();
		writePlayerData(directory, first, true);
		writePlayerData(directory, second, true);
		writePlayerData(directory, third, false);

		DirectoryPurge purge = DragonMaceOfflinePlayerData.purgeDirectory(directory);

		assertEquals(2, purge.purged(), "both aura files must be purged");
		assertEquals(1, purge.alreadyClean());
		assertEquals(0, purge.failed());
		assertEquals(3, purge.cleaned());
		assertFalse(hasAura(directory.resolve(first + ".dat")));
		assertFalse(hasAura(directory.resolve(second + ".dat")));
	}

	@Test
	void unrelatedEffectsAndInventorySurviveTheDirectoryPurge(@TempDir Path directory) throws Exception {
		UUID owner = UUID.randomUUID();
		Path file = writePlayerData(directory, owner, true);

		DragonMaceOfflinePlayerData.purgeDirectory(directory);

		CompoundTag saved = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
		ListTag effects = saved.getListOrEmpty(DragonMaceOfflinePlayerData.ACTIVE_EFFECTS_KEY);

		assertEquals(1, effects.size(), "the unrelated effect must survive");
		assertEquals("minecraft:speed", effects.getCompoundOrEmpty(0).getStringOr("id", ""));
		assertEquals(17, saved.getIntOr("XpLevel", -1));
		assertEquals(1, saved.getListOrEmpty("Inventory").size());
		assertEquals("minecraft:stick",
				saved.getListOrEmpty("Inventory").getCompoundOrEmpty(0).getStringOr("id", ""));
	}

	@Test
	void aMalformedFileDoesNotStopTheOtherPlayers(@TempDir Path directory) throws Exception {
		UUID broken = UUID.randomUUID();
		UUID healthy = UUID.randomUUID();
		Files.write(directory.resolve(broken + ".dat"), new byte[] {1, 2, 3, 4, 5});
		writePlayerData(directory, healthy, true);

		DirectoryPurge purge = DragonMaceOfflinePlayerData.purgeDirectory(directory);

		assertEquals(1, purge.failed(), "the malformed file must be reported as failed");
		assertEquals(1, purge.purged(), "the healthy file must still be purged");
		assertFalse(hasAura(directory.resolve(healthy + ".dat")));
		assertEquals(5, Files.size(directory.resolve(broken + ".dat")),
				"a malformed file must be left exactly as it was");
	}

	@Test
	void aMissingDirectoryCleansNothingAndCreatesNothing(@TempDir Path directory) {
		Path missing = directory.resolve("playerdata");

		DirectoryPurge purge = DragonMaceOfflinePlayerData.purgeDirectory(missing);

		assertEquals(0, purge.cleaned());
		assertEquals(0, purge.failed());
		assertFalse(Files.exists(missing), "no directory may be created");
	}

	@Test
	void aNullDirectoryCleansNothing() {
		assertEquals(0, DragonMaceOfflinePlayerData.purgeDirectory(null).cleaned());
	}

	@Test
	void nonDatFilesAreIgnored(@TempDir Path directory) throws Exception {
		Files.writeString(directory.resolve("notes.txt"), "not player data");

		DirectoryPurge purge = DragonMaceOfflinePlayerData.purgeDirectory(directory);

		assertEquals(0, purge.cleaned());
		assertEquals(0, purge.failed());
		assertTrue(Files.exists(directory.resolve("notes.txt")));
	}

	// ------------------------------------------------------------- no core issued

	@Test
	void theDirectoryPurgeNeverAddsAnItem(@TempDir Path directory) throws Exception {
		UUID owner = UUID.randomUUID();
		Path file = writePlayerData(directory, owner, true);

		DragonMaceOfflinePlayerData.purgeDirectory(directory);

		CompoundTag saved = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());

		assertEquals(1, saved.getListOrEmpty("Inventory").size());
		assertFalse(saved.toString().contains("very_heavy_core"));
		assertFalse(saved.toString().contains("minecraft:paper"));
	}

	// --------------------------------------------------------------- join safety

	@Test
	void aPurgedPlayerDoesNotRegainTheCurseOnLogin() {
		DragonMaceWorldData data = worldDataWith(UUID.randomUUID());
		UUID removed = data.getCurseOwner();

		data.setCurseOwner(null);

		assertNotNull(removed);
		assertFalse(DragonMaceCurse.shouldApplyOnJoin(data.getCurseOwner(), removed));
	}

	// --------------------------------------------------------------- command tree

	@Test
	void removeRunsWithoutAPlayerArgument() {
		CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
		DragonMaceCommands.register(dispatcher);

		CommandNode<CommandSourceStack> remove = dispatcher.getRoot().getChild("dm").getChild("remove");

		assertNotNull(remove);
		assertNotNull(remove.getCommand(), "bare /dm remove must execute the global removal");
	}

	@Test
	void theOldPlayerSyntaxIsStillAccepted() {
		CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
		DragonMaceCommands.register(dispatcher);

		CommandNode<CommandSourceStack> remove = dispatcher.getRoot().getChild("dm").getChild("remove");

		assertNotNull(remove.getChild("player"), "/dm remove <player> must still parse");
		assertNotNull(remove.getChild("player").getCommand());
	}

	// ------------------------------------------------------------- utilities

	private static DragonMaceWorldData worldDataWith(UUID owner) {
		return new DragonMaceWorldData(false, Optional.empty(), 0, Optional.empty(), Optional.empty(),
				Optional.of(owner.toString()));
	}

	private static boolean hasAura(Path file) throws Exception {
		CompoundTag saved = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());

		for (int index = 0; index < saved.getListOrEmpty(
				DragonMaceOfflinePlayerData.ACTIVE_EFFECTS_KEY).size(); index++) {
			if (DragonMaceOfflinePlayerData.isAuraEffect(saved
					.getListOrEmpty(DragonMaceOfflinePlayerData.ACTIVE_EFFECTS_KEY)
					.getCompoundOrEmpty(index))) {
				return true;
			}
		}

		return false;
	}

	private static Path writePlayerData(Path directory, UUID owner, boolean withAura) throws Exception {
		CompoundTag tag = new CompoundTag();
		tag.putInt("XpLevel", 17);
		tag.putString("Dimension", "minecraft:overworld");

		CompoundTag stack = new CompoundTag();
		stack.putString("id", "minecraft:stick");
		stack.putInt("count", 1);
		stack.putInt("Slot", 0);

		ListTag inventory = new ListTag();
		inventory.add(stack);
		tag.put("Inventory", inventory);

		ListTag effects = new ListTag();

		if (withAura) {
			effects.add(effect("minecraft:glowing", 0, DragonMaceOfflinePlayerData.INFINITE_DURATION));
		}

		effects.add(effect("minecraft:speed", 1, 600));
		tag.put(DragonMaceOfflinePlayerData.ACTIVE_EFFECTS_KEY, effects);

		Path file = directory.resolve(owner + ".dat");
		NbtIo.writeCompressed(tag, file);
		return file;
	}

	private static CompoundTag effect(String id, int amplifier, int duration) {
		CompoundTag effect = new CompoundTag();
		effect.putString("id", id);
		effect.putInt("amplifier", amplifier);
		effect.putInt("duration", duration);
		return effect;
	}
}
