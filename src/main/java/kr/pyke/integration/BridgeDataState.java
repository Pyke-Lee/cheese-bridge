package kr.pyke.integration;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import kr.pyke.type.PLATFORM;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

public class BridgeDataState extends SavedData {
    public record TokenInfo(String accessToken, String refreshToken) {
        public static final Codec<TokenInfo> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("access").forGetter(TokenInfo::accessToken),
            Codec.STRING.fieldOf("refresh").forGetter(TokenInfo::refreshToken)
        ).apply(instance, TokenInfo::new));
    }

    private record TokenEntry(String uuid, String platform, String accessToken, String refreshToken) {
        public static final Codec<TokenEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("uuid").forGetter(TokenEntry::uuid),
            Codec.STRING.fieldOf("platform").forGetter(TokenEntry::platform),
            Codec.STRING.fieldOf("access").forGetter(TokenEntry::accessToken),
            Codec.STRING.fieldOf("refresh").forGetter(TokenEntry::refreshToken)
        ).apply(instance, TokenEntry::new));
    }

    public static final Codec<BridgeDataState> CODEC = TokenEntry.CODEC.listOf().xmap(
        entries -> {
            BridgeDataState state = new BridgeDataState();
            for (TokenEntry entry : entries) {
                try {
                    UUID uuid = UUID.fromString(entry.uuid());
                    PLATFORM platform = PLATFORM.valueOf(entry.platform());
                    state.playerTokens
                        .computeIfAbsent(uuid, k -> new HashMap<>())
                        .put(platform, new TokenInfo(entry.accessToken(), entry.refreshToken()));
                }
                catch (IllegalArgumentException ignored) { }
            }
            return state;
        },
        state -> {
            List<TokenEntry> entries = new ArrayList<>();
            state.playerTokens.forEach((uuid, map) ->
                map.forEach((platform, info) ->
                    entries.add(new TokenEntry(uuid.toString(), platform.name(), info.accessToken(), info.refreshToken()))
                )
            );
            return entries;
        }
    );

    public static final SavedDataType<BridgeDataState> TYPE = new SavedDataType<>("cheese_bridge", BridgeDataState::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<UUID, Map<PLATFORM, TokenInfo>> playerTokens = new HashMap<>();

    public BridgeDataState() { }

    public TokenInfo getToken(UUID uuid, PLATFORM platform) {
        return playerTokens.getOrDefault(uuid, new HashMap<>()).get(platform);
    }

    public void setToken(UUID uuid, PLATFORM platform, TokenInfo tokenInfo) {
        playerTokens.computeIfAbsent(uuid, k -> new HashMap<>()).put(platform, tokenInfo);
        this.setDirty();
    }

    public static BridgeDataState getServerState(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }
}