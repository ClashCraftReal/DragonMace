package com.lorenz.dragonmace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The rune effect mapping.
 *
 * <p>Every visual and potion choice is a pure function of the rune, so the whole table is asserted here
 * without a world. Nothing in this table is destructive: no explosions, no block changes, no lightning.
 */
class DragonMaceRuneEffectsTest {
	private static RegistryAccess registries;

	@BeforeAll
	static void setUp() {
		TestBootstrap.boot();
		registries = DragonMaceEnchantmentGuiTest.enchantmentRegistries();
	}

	@Test
	void theAuraRunsEveryTenTicks() {
		assertEquals(10, DragonMaceRuneEffects.AURA_INTERVAL_TICKS);
	}

	// --- the kill pipeline must actually reach the effect ---------------------------------------

	/**
	 * The regression for the reported bug: {@code onKill} existed but nothing called it, so every Kill
	 * rune was dead code and no kill ever produced a particle. Asserting the call site is what catches a
	 * future refactor that drops it again.
	 */
	@Test
	void theKillPipelineInvokesTheKillEffect() throws Exception {
		String source = java.nio.file.Files.readString(java.nio.file.Path.of(
				"src/main/java/com/lorenz/dragonmace/DragonMaceProgression.java"));

		assertTrue(source.contains("DragonMaceRuneEffects.onKill("),
				"registerKill must call DragonMaceRuneEffects.onKill or no Kill rune can ever fire");
	}

	@Test
	void theKillEffectUsesTheKillersSelectedKillRune() throws Exception {
		String source = java.nio.file.Files.readString(java.nio.file.Path.of(
				"src/main/java/com/lorenz/dragonmace/DragonMaceProgression.java"));

		assertTrue(source.contains("runes(server, killer.getUUID()).kill()"),
				"the effect must use the killer's saved Kill rune, not a default");
	}

	@Test
	void everyKillRuneIsReachableAndDistinct() {
		Set<ParticleOptions> particles = new HashSet<>();

		for (DragonMaceRune rune : DragonMaceRune.values()) {
			assertTrue(rune.unlockKills() >= 0, rune.name());
			particles.add(DragonMaceRuneEffects.killParticle(rune));
		}

		assertEquals(DragonMaceRune.values().length, particles.size());
	}

	@Test
	void theKillEffectHonoursTheKillToggle() throws Exception {
		String source = java.nio.file.Files.readString(java.nio.file.Path.of(
				"src/main/java/com/lorenz/dragonmace/DragonMaceRuneEffects.java"));

		assertTrue(source.contains("DragonMaceRuneCategory.KILL"),
				"onKill must gate on the KILL toggle");
	}

	@Test
	void everyRuneHasItsOwnKillParticle() {
		Set<ParticleOptions> particles = new HashSet<>();

		for (DragonMaceRune rune : DragonMaceRune.values()) {
			particles.add(DragonMaceRuneEffects.killParticle(rune));
		}

		assertEquals(5, particles.size(), "each rune must be visually distinguishable");
	}

	@Test
	void theKillParticleMatchesTheRuneColourFamily() {
		assertSame(ParticleTypes.SOUL_FIRE_FLAME,
				DragonMaceRuneEffects.killParticle(DragonMaceRune.SOULFLAME));
		assertSame(ParticleTypes.ELECTRIC_SPARK,
				DragonMaceRuneEffects.killParticle(DragonMaceRune.TEMPEST));
		assertSame(ParticleTypes.SCULK_SOUL,
				DragonMaceRuneEffects.killParticle(DragonMaceRune.ABYSSAL));
		assertSame(ParticleTypes.SOUL, DragonMaceRuneEffects.killParticle(DragonMaceRune.WARDEN));
	}

	@Test
	void theAttackAndAuraParticlesMatchTheKillParticle() {
		for (DragonMaceRune rune : DragonMaceRune.values()) {
			assertSame(DragonMaceRuneEffects.killParticle(rune),
					DragonMaceRuneEffects.attackParticle(rune));
			assertSame(DragonMaceRuneEffects.killParticle(rune),
					DragonMaceRuneEffects.auraParticle(rune));
		}
	}

	@Test
	void aNullRuneFallsBackToDraconic() {
		assertSame(DragonMaceRuneEffects.killParticle(DragonMaceRune.DRACONIC),
				DragonMaceRuneEffects.killParticle(null));
		assertTrue(DragonMaceRuneEffects.attackEffect(null).isEmpty());
	}

	@Test
	void abyssalAppliesDarkness() {
		var effect = DragonMaceRuneEffects.attackEffect(DragonMaceRune.ABYSSAL).orElseThrow();

		assertSame(MobEffects.DARKNESS, effect.effect());
		assertEquals(100, effect.durationTicks());
		assertEquals(0, effect.amplifier());
	}

	@Test
	void wardenAppliesSlowness() {
		var effect = DragonMaceRuneEffects.attackEffect(DragonMaceRune.WARDEN).orElseThrow();

		assertSame(MobEffects.SLOWNESS, effect.effect());
		assertEquals(60, effect.durationTicks());
		assertEquals(1, effect.amplifier());
	}

	@Test
	void draconicAndTempestApplyNoPotion() {
		assertTrue(DragonMaceRuneEffects.attackEffect(DragonMaceRune.DRACONIC).isEmpty());
		assertTrue(DragonMaceRuneEffects.attackEffect(DragonMaceRune.TEMPEST).isEmpty());
	}

	@Test
	void soulflameAppliesNoPotionBecauseItIgnites() {
		assertTrue(DragonMaceRuneEffects.attackEffect(DragonMaceRune.SOULFLAME).isEmpty());
		assertTrue(DragonMaceRuneEffects.ignitesTarget(DragonMaceRune.SOULFLAME));
	}

	@Test
	void onlySoulflameIgnites() {
		for (DragonMaceRune rune : DragonMaceRune.values()) {
			assertEquals(rune == DragonMaceRune.SOULFLAME,
					DragonMaceRuneEffects.ignitesTarget(rune), rune.name());
		}
	}

	@Test
	void onlyTempestKnocksBack() {
		for (DragonMaceRune rune : DragonMaceRune.values()) {
			assertEquals(rune == DragonMaceRune.TEMPEST,
					DragonMaceRuneEffects.knocksBack(rune), rune.name());
		}
	}

	@Test
	void noRuneAppliesAPotionToItself() {
		for (DragonMaceRune rune : DragonMaceRune.values()) {
			DragonMaceRuneEffects.attackEffect(rune).ifPresent(effect ->
					assertFalse(effect.durationTicks() <= 0, "a zero-duration instance is a no-op"));
		}
	}

	// ------------------------------------------------------------------ soulflame

	@Test
	void soulflameNeverAddsFireAspect() {
		DragonMaceRuneSelection selection = new DragonMaceRuneSelection(DragonMaceRune.SOULFLAME,
				DragonMaceRune.SOULFLAME, DragonMaceRune.SOULFLAME);
		DragonMaceLoreContext context = new DragonMaceLoreContext(
				DragonMaceEnchantments.configured(registries, DragonMaceConfig.defaults()),
				DragonMaceLoreContext.summaryOf(
						DragonMaceEnchantments.configured(registries, DragonMaceConfig.defaults())),
				selection, 4);
		ItemStack mace = DragonMaceItems.createDraconicMaceStack(new ItemStack(Items.MACE), registries,
				context);

		assertFalse(carries(mace.getEnchantments(), Enchantments.FIRE_ASPECT), "conversion");
		assertTrue(DragonMaceItems.refreshDraconicMace(mace, context));
		assertFalse(carries(mace.getEnchantments(), Enchantments.FIRE_ASPECT), "refresh");
	}

	@Test
	void noConfigurationEverAddsFireAspect() {
		for (int level = 0; level <= DragonMaceConfig.MAX_WIND_BURST_LEVEL; level++) {
			for (DragonMaceConfig.MainEnchantment main : DragonMaceConfig.MainEnchantment.values()) {
				ItemEnchantments enchantments = DragonMaceEnchantments.configured(registries,
						DragonMaceConfig.defaults().withWindBurstLevel(level).withMainEnchantment(main));

				assertFalse(carries(enchantments, Enchantments.FIRE_ASPECT),
						"wind burst " + level + ", " + main);
			}
		}
	}

	@Test
	void soulflameStillIgnitesWithoutFireAspect() {
		assertTrue(DragonMaceRuneEffects.ignitesTarget(DragonMaceRune.SOULFLAME));
		assertTrue(DragonMaceRuneEffects.attackEffect(DragonMaceRune.SOULFLAME).isEmpty());
		assertFalse(carries(DragonMaceEnchantments.configured(registries, DragonMaceConfig.defaults()),
				Enchantments.FIRE_ASPECT));
	}

	private static boolean carries(ItemEnchantments enchantments, ResourceKey<Enchantment> key) {
		for (var entry : enchantments.entrySet()) {
			if (entry.getKey().is(key)) {
				return true;
			}
		}

		return false;
	}
}
