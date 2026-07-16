package kr.pyke.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import kr.pyke.CheeseBridge;
import kr.pyke.client.gui.hud.DebugHud;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class ModKeyBindings {
    private static final KeyMapping.Category generalCategory = KeyMapping.Category.register(CheeseBridge.id("general"));

    public static KeyMapping debugKey;

    private ModKeyBindings() { }

    public static void register() {
        debugKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.cheese-bridge.debug.toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F9,
            generalCategory
        ));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            while(debugKey.consumeClick()) {
                DebugHud.toggle();
            }
        });
    }
}
