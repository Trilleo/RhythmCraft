package net.trilleo.rhythmcraft.item;

import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.trilleo.rhythmcraft.RhythmCraft;

import java.util.function.Function;

public class ModItems {

    // Items

    public static final Item RHYTHM_TUNER = registerItem("rhythm_tuner", Item::new);

    // Registry

    private static Item registerItem(String name, Function<Item.Settings, Item> function) {
        return Registry.register(Registries.ITEM, Identifier.of(RhythmCraft.MOD_ID, name),
                function.apply(new Item.Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(RhythmCraft.MOD_ID, name)))));
    }

    public static void registerModItems() {

        RhythmCraft.LOGGER.info("[RhythmCraft] Registering ModItems...");
    }
}
