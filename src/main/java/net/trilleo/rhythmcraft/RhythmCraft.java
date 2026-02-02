package net.trilleo.rhythmcraft;

import net.fabricmc.api.ModInitializer;

import net.trilleo.rhythmcraft.item.ModItems;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RhythmCraft implements ModInitializer {
	public static final String MOD_ID = "rhythmcraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModItems.registerModItems();
	}
}