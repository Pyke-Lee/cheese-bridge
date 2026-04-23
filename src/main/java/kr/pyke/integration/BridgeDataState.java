package kr.pyke.integration;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import kr.pyke.CheeseBridge;
import kr.pyke.util.PLATFORM;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

public class BridgeDataState extends SavedData {
    private static final String DATA_NAME = "cheese_bridge";
    private static final String NBT_KEY = "entries";

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

    private static final Codec<List<TokenEntry>> ENTRIES_CODEC = TokenEntry.CODEC.listOf();

    private final Map<UUID, Map<PLATFORM, TokenInfo>> playerTokens = new HashMap<>();

    public BridgeDataState() { }

    public static BridgeDataState load(CompoundTag tag) {
        BridgeDataState state = new BridgeDataState();
        if (!tag.contains(NBT_KEY)) { return state; }

        DataResult<List<TokenEntry>> result = ENTRIES_CODEC.parse(NbtOps.INSTANCE, tag.get(NBT_KEY));
        List<TokenEntry> entries = result.resultOrPartial(err -> CheeseBridge.LOGGER.error("[DataState] 로드 실패: {}", err)).orElse(List.of());

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
    }

    @Override
    public CompoundTag save(CompoundTag compoundTag) {
        List<TokenEntry> entries = new ArrayList<>();
        playerTokens.forEach((uuid, map) ->
            map.forEach((platform, info) ->
                entries.add(new TokenEntry(uuid.toString(), platform.name(), info.accessToken(), info.refreshToken()))
            )
        );

        DataResult<Tag> result = ENTRIES_CODEC.encodeStart(NbtOps.INSTANCE, entries);
        result.resultOrPartial(err -> CheeseBridge.LOGGER.error("[DataState] 저장 실패: {}", err))
            .ifPresent(encoded -> compoundTag.put(NBT_KEY, encoded));

        return compoundTag;
    }

    public TokenInfo getToken(UUID uuid, PLATFORM platform) {
        return playerTokens.getOrDefault(uuid, new HashMap<>()).get(platform);
    }

    public void setToken(UUID uuid, PLATFORM platform, TokenInfo tokenInfo) {
        playerTokens.computeIfAbsent(uuid, k -> new HashMap<>()).put(platform, tokenInfo);
        this.setDirty();
    }

    public static BridgeDataState getServerState(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(BridgeDataState::load, BridgeDataState::new, DATA_NAME);
    }
}
