package kr.pyke.type;

import kr.pyke.CheeseBridge;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public enum MESSAGE_TYPE {
    PERSONAL,
    SERVER,
    BROADCAST;

    public static MESSAGE_TYPE fromString(String value) {
        if (value == null) { return PERSONAL; }

        return switch (value.toLowerCase()) {
            case "server" -> SERVER;
            case "broadcast" -> BROADCAST;
            default -> PERSONAL;
        };
    }

    public void send(ServerPlayer player, String message) {
        MinecraftServer server = CheeseBridge.getServer(player);
        if (server == null) { return; }
        Component component = Component.literal("§6[SYSTEM]§r ").append(message).withStyle(ChatFormatting.WHITE);

        switch (this) {
            case SERVER -> server.getPlayerList().broadcastSystemMessage(component, false);
            case BROADCAST -> {
                server.getPlayerList().broadcastSystemMessage(Component.empty(), false);
                server.getPlayerList().broadcastSystemMessage(component, false);
                server.getPlayerList().broadcastSystemMessage(Component.empty(), false);
            }
            default -> player.sendSystemMessage(component, false);
        }
    }

    public static void send(MESSAGE_TYPE type, ServerPlayer player, String message) {
        MinecraftServer server = CheeseBridge.getServer(player);
        if (server == null) { return; }
        Component component = Component.literal("§6[SYSTEM]§r ").append(message).withStyle(ChatFormatting.WHITE);

        switch (type) {
            case SERVER -> server.getPlayerList().broadcastSystemMessage(component, false);
            case BROADCAST -> {
                server.getPlayerList().broadcastSystemMessage(Component.empty(), false);
                server.getPlayerList().broadcastSystemMessage(component, false);
                server.getPlayerList().broadcastSystemMessage(Component.empty(), false);
            }
            default -> player.sendSystemMessage(component, false);
        }
    }
}