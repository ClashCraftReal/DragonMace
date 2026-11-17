package com.lorenz.dragonmace;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/**
 * One-time test bootstrap.
 *
 * <p>The mod registers no items, so bootstrapping the vanilla registries is all that is needed. It
 * runs once per test JVM because {@code Bootstrap.bootStrap()} is not idempotent.
 */
public final class TestBootstrap {
	private static boolean ready;

	private TestBootstrap() {
	}

	public static synchronized void boot() {
		if (ready) {
			return;
		}

		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		ready = true;
	}
}
