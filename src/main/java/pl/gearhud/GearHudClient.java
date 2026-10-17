package pl.gearhud;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class GearHudClient implements ClientModInitializer {
    public static boolean enabled = true;
    /** true = panel pokazuje sie tylko w Netherze */
    public static boolean netherOnly = false;
    /** zasieg w kratkach */
    public static int range = 64;
    /** ile kart naraz */
    public static int maxCards = 4;

    private static KeyBinding toggleKey;
    private static KeyBinding modeKey;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.gearhud.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "category.gearhud"));
        modeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.gearhud.mode", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, "category.gearhud"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                enabled = !enabled;
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("GearHUD: " + (enabled ? "ON" : "OFF")), true);
                }
            }
            while (modeKey.wasPressed()) {
                netherOnly = !netherOnly;
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("GearHUD: " + (netherOnly ? "tylko Nether" : "zawsze")), true);
                }
            }
        });

        HudRenderCallback.EVENT.register(GearHudOverlay::render);
    }
}
