package net.trilleo.rhythmcraft;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.trilleo.rhythmcraft.screen.RhythmGameScreen;
import org.lwjgl.glfw.GLFW;

public class RhythmCraftClient implements ClientModInitializer {

    /** Press this key (default: R) to open the rhythm game stage. */
    public static KeyBinding openGameKey;

    @Override
    public void onInitializeClient() {
        openGameKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.rhythmcraft.open_game",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "category.rhythmcraft.gameplay"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGameKey.wasPressed()) {
                client.setScreen(new RhythmGameScreen());
            }
        });
    }
}
