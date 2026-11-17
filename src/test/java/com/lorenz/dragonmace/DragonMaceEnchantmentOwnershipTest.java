package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.stream.Stream;

import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import com.mojang.serialization.Lifecycle;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for curse-based enchantment ownership and the global configuration.
 *
 * <p>The ownership rule is exercised against real {@link SimpleContainer}s, which is the same
 * {@code Container} contract a player inventory implements, so the slot-level behaviour is covered
 * without a live player. The configuration tests cover the selectable values, the mutual exclusion and
 * the server-wide cooldown.
 */
class DragonMaceEnchantmentOwnershipTest {
	private static RegistryAccess registries;

	@BeforeAll
	static void setUp() {
		TestBootstrap.boot();
		registries = enchantmentRegistries();
	}

	// ------------------------------------------------------- configuration

	@Test
	void theDefaultConfigurationIsDensityFiveAndWindBurstThree() {
		DragonMaceConfig config = DragonMaceConfig.defaults();

		assertEquals(5, config.mainEnchantment().level());
		assertEquals(DragonMaceConfig.MainEnchantment.DENSITY, config.mainEnchantment());
		assertEquals(3, config.windBurstLevel());
	}

	@Test
	void breachIsLevelFourAndDensityIsLevelFive() {
		assertEquals(4, DragonMaceConfig.MainEnchantment.BREACH.level());
		assertEquals(5, DragonMaceConfig.MainEnchantment.DENSITY.level());
	}

	@Test
	void theMainEnchantmentIsMutuallyExclusive() {
		DragonMaceConfig config = DragonMaceConfig.defaults()
				.withMainEnchantment(DragonMaceConfig.MainEnchantment.BREACH);

		assertEquals(DragonMaceConfig.MainEnchantment.BREACH, config.mainEnchantment());

		DragonMaceConfig back = config.withMainEnchantment(DragonMaceConfig.MainEnchantment.DENSITY);

		assertEquals(DragonMaceConfig.MainEnchantment.DENSITY, back.mainEnchantment());
		assertNotEquals(config.mainEnchantment(), back.mainEnchantment());
	}

	@Test
	void windBurstIsClampedToZeroThroughThree() {
		assertEquals(0, DragonMaceConfig.clampWindBurst(-5));
		assertEquals(0, DragonMaceConfig.clampWindBurst(0));
		assertEquals(3, DragonMaceConfig.clampWindBurst(3));
		assertEquals(3, DragonMaceConfig.clampWindBurst(99));
	}

	@Test
	void theCooldownBlocksUntilItElapses() {
		long now = 1_000_000L;
		DragonMaceConfig config = DragonMaceConfig.defaults().withLastApply(now);

		assertFalse(config.canApply(now));
		assertEquals(DragonMaceConfig.APPLY_COOLDOWN_MILLIS, config.remainingCooldownMillis(now));
		assertFalse(config.canApply(now + DragonMaceConfig.APPLY_COOLDOWN_MILLIS - 1));
		assertTrue(config.canApply(now + DragonMaceConfig.APPLY_COOLDOWN_MILLIS));
	}

	@Test
	void theCooldownIsGlobalBecauseItLivesInTheConfiguration() {
		long now = 2_000_000L;
		DragonMaceConfig saved = DragonMaceConfig.defaults().withLastApply(now);

		// A second menu, or another player, reads the same saved timestamp.
		assertFalse(saved.canApply(now + 1_000L));
		assertEquals(saved.remainingCooldownMillis(now + 1_000L),
				DragonMaceConfig.defaults().withLastApply(now).remainingCooldownMillis(now + 1_000L));
	}

	@Test
	void theConfigurationRoundTripsThroughJson() {
		DragonMaceConfig config = new DragonMaceConfig(DragonMaceConfig.RespawnLocation.WORLD_SPAWN, 1,
				DragonMaceConfig.MainEnchantment.BREACH, 12345L);
		String json = "{\"respawnLocation\":\"world_spawn\",\"windBurstLevel\":1,"
				+ "\"mainEnchantment\":\"breach\",\"lastApplyEpochMillis\":12345}";

		DragonMaceConfig parsed = DragonMaceConfig.parse(json);

		assertEquals(config.respawnLocation(), parsed.respawnLocation());
		assertEquals(config.windBurstLevel(), parsed.windBurstLevel());
		assertEquals(config.mainEnchantment(), parsed.mainEnchantment());
		assertEquals(config.lastApplyEpochMillis(), parsed.lastApplyEpochMillis());
	}

	@Test
	void anInvalidConfigurationFallsBackToDefaults() {
		DragonMaceConfig parsed = DragonMaceConfig.parse(
				"{\"windBurstLevel\":99,\"mainEnchantment\":\"sharpness\"}");

		assertEquals(3, parsed.windBurstLevel());
		assertEquals(DragonMaceConfig.MainEnchantment.DENSITY, parsed.mainEnchantment());
	}

	// ---------------------------------------------------------- ownership

	@Test
	void theConfiguredEnchantmentsFollowTheConfiguration() {
		ItemEnchantments density = DragonMaceEnchantments.configured(registries,
				DragonMaceConfig.defaults());
		ItemEnchantments breach = DragonMaceEnchantments.configured(registries,
				DragonMaceConfig.defaults().withMainEnchantment(DragonMaceConfig.MainEnchantment.BREACH));
		ItemEnchantments noWind = DragonMaceEnchantments.configured(registries,
				DragonMaceConfig.defaults().withWindBurstLevel(0));

		assertEquals(2, density.size(), "density and wind burst");
		assertEquals(2, breach.size());
		assertEquals(1, noWind.size(), "wind burst 0 must be omitted entirely");
	}

	@Test
	void aHoldersMaceReceivesTheConfiguredEnchantments() {
		ItemStack mace = new ItemStack(Items.MACE);

		assertTrue(DragonMaceEnchantments.reconcile(mace, true, registries,
				DragonMaceConfig.defaults()));
		assertEquals(DragonMaceEnchantments.configured(registries, DragonMaceConfig.defaults()),
				mace.getEnchantments());
	}

	@Test
	void anotherPlayersMaceLosesEveryEnchantment() {
		ItemStack mace = new ItemStack(Items.MACE);
		mace.set(DataComponents.ENCHANTMENTS, enchantments(Enchantments.DENSITY, 5));

		assertTrue(DragonMaceEnchantments.reconcile(mace, false, registries,
				DragonMaceConfig.defaults()));
		assertTrue(mace.getEnchantments().isEmpty());
	}

	@Test
	void aDroppedMaceLosesEveryEnchantment() {
		ItemStack dropped = DragonMaceMaceConversion.revertLeaving(draconic());

		assertTrue(dropped.getEnchantments().isEmpty());
		assertEquals("Mace", dropped.getHoverName().getString());
	}

	@Test
	void macesInAContainerLoseEveryEnchantment() {
		SimpleContainer chest = new SimpleContainer(3);
		ItemStack first = new ItemStack(Items.MACE);
		first.set(DataComponents.ENCHANTMENTS, enchantments(Enchantments.DENSITY, 5));
		ItemStack second = new ItemStack(Items.MACE);
		second.set(DataComponents.ENCHANTMENTS, enchantments(Enchantments.WIND_BURST, 3));
		chest.setItem(0, first);
		chest.setItem(2, second);

		assertEquals(2, DragonMaceEnchantments.reconcileContainer(chest, false, registries,
				DragonMaceConfig.defaults()));
		assertTrue(chest.getItem(0).getEnchantments().isEmpty());
		assertTrue(chest.getItem(2).getEnchantments().isEmpty());
	}

	@Test
	void unrelatedItemsKeepTheirEnchantments() {
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		sword.set(DataComponents.ENCHANTMENTS, enchantments(Enchantments.DENSITY, 5));

		assertFalse(DragonMaceEnchantments.reconcile(sword, false, registries,
				DragonMaceConfig.defaults()));
		assertEquals(1, sword.getEnchantments().size(), "only maces are governed");
	}

	@Test
	void reconciliationIsIdempotent() {
		SimpleContainer inventory = new SimpleContainer(1);
		inventory.setItem(0, new ItemStack(Items.MACE));

		assertEquals(1, DragonMaceEnchantments.reconcileContainer(inventory, true, registries,
				DragonMaceConfig.defaults()));
		assertEquals(0, DragonMaceEnchantments.reconcileContainer(inventory, true, registries,
				DragonMaceConfig.defaults()));
		assertEquals(1, DragonMaceEnchantments.reconcileContainer(inventory, false, registries,
				DragonMaceConfig.defaults()));
		assertEquals(0, DragonMaceEnchantments.reconcileContainer(inventory, false, registries,
				DragonMaceConfig.defaults()));
	}

	@Test
	void strippingKeepsDamageAndCount() {
		ItemStack mace = new ItemStack(Items.MACE, 1);
		mace.set(DataComponents.DAMAGE, 42);
		mace.set(DataComponents.ENCHANTMENTS, enchantments(Enchantments.DENSITY, 5));

		DragonMaceEnchantments.strip(mace);

		assertEquals(42, mace.getOrDefault(DataComponents.DAMAGE, 0));
		assertEquals(1, mace.getCount());
		assertTrue(mace.getEnchantments().isEmpty());
	}

	@Test
	void applyingReplacesEveryOtherEnchantment() {
		ItemStack mace = new ItemStack(Items.MACE);
		mace.set(DataComponents.ENCHANTMENTS, enchantments(Enchantments.WIND_BURST, 1));

		DragonMaceEnchantments.applyConfigured(mace, registries, DragonMaceConfig.defaults());

		assertEquals(DragonMaceEnchantments.configured(registries, DragonMaceConfig.defaults()),
				mace.getEnchantments());
	}

	@Test
	void aNonMaceIsNeverTouched() {
		assertFalse(DragonMaceEnchantments.isMace(new ItemStack(Items.DIAMOND_SWORD)));
		assertFalse(DragonMaceEnchantments.isMace(ItemStack.EMPTY));
		assertFalse(DragonMaceEnchantments.isMace(null));
		assertTrue(DragonMaceEnchantments.isMace(new ItemStack(Items.MACE)));
	}

	// ------------------------------------------------------------- helpers

	private static ItemStack draconic() {
		return DragonMaceItems.createDraconicMaceStack(new ItemStack(Items.MACE), registries);
	}

	private static ItemEnchantments enchantments(ResourceKey<Enchantment> key, int level) {
		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		mutable.set(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key), level);
		return mutable.toImmutable();
	}

	private static RegistryAccess enchantmentRegistries() {
		MappedRegistry<Enchantment> registry = new MappedRegistry<>(Registries.ENCHANTMENT,
				Lifecycle.stable());
		HolderSet<Item> supported = HolderSet.direct(BuiltInRegistries.ITEM.wrapAsHolder(Items.MACE));

		register(registry, Enchantments.DENSITY, supported);
		register(registry, Enchantments.WIND_BURST, supported);
		register(registry, Enchantments.BREACH, supported);
		registry.freeze();

		return new TestRegistryAccess(registry);
	}

	private static void register(MappedRegistry<Enchantment> registry, ResourceKey<Enchantment> key,
			HolderSet<Item> supported) {
		Enchantment.EnchantmentDefinition definition = Enchantment.definition(supported, 5, 5,
				Enchantment.constantCost(1), Enchantment.constantCost(51), 4, EquipmentSlotGroup.MAINHAND);

		registry.register(key, new Enchantment(Component.literal(key.toString()), definition,
				HolderSet.empty(), DataComponentMap.EMPTY), RegistrationInfo.BUILT_IN);
	}

	/** Minimal {@link RegistryAccess} exposing one registry. */
	private static final class TestRegistryAccess implements RegistryAccess {
		private final Registry<Enchantment> enchantments;

		private TestRegistryAccess(Registry<Enchantment> enchantments) {
			this.enchantments = enchantments;
		}

		@Override
		@SuppressWarnings("unchecked")
		public <E> Registry<E> lookupOrThrow(ResourceKey<? extends Registry<? extends E>> key) {
			if (Registries.ENCHANTMENT.equals(key)) {
				return (Registry<E>) this.enchantments;
			}

			throw new IllegalStateException("Unexpected registry " + key);
		}

		@Override
		@SuppressWarnings("unchecked")
		public <E> Optional<Registry<E>> lookup(ResourceKey<? extends Registry<? extends E>> key) {
			if (Registries.ENCHANTMENT.equals(key)) {
				return Optional.of((Registry<E>) this.enchantments);
			}

			return Optional.empty();
		}

		@Override
		public Stream<RegistryEntry<?>> registries() {
			return Stream.empty();
		}

		@Override
		public Stream<ResourceKey<? extends Registry<?>>> listRegistryKeys() {
			return Stream.of(Registries.ENCHANTMENT);
		}
	}
}
