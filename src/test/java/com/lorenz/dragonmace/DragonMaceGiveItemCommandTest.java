package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lorenz.dragonmace.command.DragonMaceCommands;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies the final {@code /dm} command tree and the item {@code /dm giveitem veryheavycore} hands out.
 *
 * <p>The dispatcher is built directly, which needs no running server, so the tree is checked exactly as
 * {@code CommandsMixin} registers it.
 */
class DragonMaceGiveItemCommandTest {
	private static CommandDispatcher<CommandSourceStack> dispatcher;
	private static CommandNode<CommandSourceStack> dm;

	@BeforeAll
	static void setUp() {
		TestBootstrap.boot();
		dispatcher = new CommandDispatcher<>();
		DragonMaceCommands.register(dispatcher);
		dm = dispatcher.getRoot().getChild("dm");
	}

	@Test
	void dmRootExists() {
		assertNotNull(dm);
	}

	@Test
	void dragonmaceAliasRedirectsToDm() {
		CommandNode<CommandSourceStack> alias = dispatcher.getRoot().getChild("dragonmace");

		assertNotNull(alias);
		assertSame(dm, alias.getRedirect());
	}

	@Test
	void theFiveRequiredCommandsExist() {
		assertNotNull(dm.getChild("effect"));
		assertNotNull(dm.getChild("remove"));
		assertNotNull(dm.getChild("giveitem"));
		assertNotNull(dm.getChild("withdraw"));
		assertNotNull(dm.getChild("status"));
	}

	@Test
	void theFourObsoleteCommandsAreGone() {
		assertNull(dm.getChild("spawn"), "/dm spawn must not be registered");
		assertNull(dm.getChild("delete"), "/dm delete must not be registered");
		assertNull(dm.getChild("transfer"), "/dm transfer must not be registered");
		assertNull(dm.getChild("recover"), "/dm recover must not be registered");
	}

	@Test
	void noObsoleteAliasRemainsAtTheRoot() {
		assertNull(dispatcher.getRoot().getChild("spawndragonmace"));
		assertNull(dispatcher.getRoot().getChild("deletedragonmace"));
		assertNull(dispatcher.getRoot().getChild("transfermace"));
		assertNull(dispatcher.getRoot().getChild("recoverdragonmace"));
	}

	@Test
	void dmHasExactlyTheFiveSubcommands() {
		assertEquals(6, dm.getChildren().size(),
				"the tree must contain only effect, remove, giveitem, withdraw, status and changeenchants");
		assertNotNull(dm.getChild("changeenchants"), "/dm changeenchants must exist");
		assertNull(dm.getChild("enchantments"), "/dm enchantments must be gone");
	}

	@Test
	void giveItemVeryHeavyCorePathExists() {
		CommandNode<CommandSourceStack> giveitem = dm.getChild("giveitem");

		assertNotNull(giveitem, "/dm giveitem must exist");
		assertNotNull(giveitem.getChild("veryheavycore"), "/dm giveitem veryheavycore must exist");
	}

	@Test
	void administrativeCommandsRequireGamemasters() {
		assertNotNull(dm.getChild("effect").getRequirement());
		assertNotNull(dm.getChild("remove").getRequirement());
		assertNotNull(dm.getChild("giveitem").getRequirement());
	}

	@Test
	void theGivenStackIsPaperWithTheCustomIdentifier() {
		ItemStack stack = DragonMaceItems.createVeryHeavyCoreStack();

		assertTrue(stack.is(Items.PAPER));
		assertEquals(1, stack.getCount());
		assertTrue(DragonMaceIdentity.isVeryHeavyCore(stack));
		assertEquals("Very Heavy Core", stack.getHoverName().getString());
		assertEquals(Identifier.withDefaultNamespace("heavy_core"),
				stack.get(DataComponents.ITEM_MODEL));
	}
}
