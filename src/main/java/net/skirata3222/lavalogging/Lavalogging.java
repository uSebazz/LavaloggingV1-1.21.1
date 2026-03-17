package net.skirata3222.lavalogging;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleResourceReloadListener;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.skirata3222.lavalogging.mixin.BlockAccessor;
import net.skirata3222.lavalogging.util.BlockListParser;
import net.skirata3222.lavalogging.util.BlockListRegistry;
import net.skirata3222.lavalogging.util.Lavaloggable;

public class Lavalogging implements ModInitializer {

	public static final String MOD_ID = "lavalogging";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		System.out.println("LOADING LAVALOGGED NOW");

		// Server authoritative
		ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(
				new SimpleResourceReloadListener<Set<Identifier>>() {
					@Override
					public Identifier getFabricId() {
						return Identifier.of("lavalogging", "server_blocklist");
					}

					@Override
					public CompletableFuture<Set<Identifier>> load(ResourceManager manager, Profiler profiler,
							Executor executor) {
						return CompletableFuture.supplyAsync(
								() -> BlockListParser.parse(manager, "blocklists/lavalog_eligible.json"), executor);
					}

					@Override
					public CompletableFuture<Void> apply(Set<Identifier> data, ResourceManager manager,
							Profiler profiler, Executor executor) {
						BlockListRegistry.setServerList(data);
						return CompletableFuture.completedFuture(null);
					}
				});

		// Client fallback
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
				new SimpleResourceReloadListener<Set<Identifier>>() {
					@Override
					public Identifier getFabricId() {
						return Identifier.of("lavalogging", "client_blocklist");
					}

					@Override
					public CompletableFuture<Set<Identifier>> load(ResourceManager manager, Profiler profiler,
							Executor executor) {
						return CompletableFuture.supplyAsync(
								() -> BlockListParser.parse(manager, "blocklists/lavalog_eligible.json"), executor);
					}

					@Override
					public CompletableFuture<Void> apply(Set<Identifier> data, ResourceManager manager,
							Profiler profiler, Executor executor) {
						BlockListRegistry.setClientList(data);
						return CompletableFuture.completedFuture(null);
					}
				});

		fixDefaultStates();
	}

	private void fixDefaultStates() {
		for (Block block : Registries.BLOCK) {
			if (block instanceof Lavaloggable) {
				BlockState state = block.getDefaultState();
				if (state.contains(Lavaloggable.LAVALOGGED)) {
					((BlockAccessor) block).invokeSetDefaultState(
							state.with(Lavaloggable.LAVALOGGED, false)
									.with(Properties.WATERLOGGED, false));
				}
			}
		}
	}
}