package kr.pyke.integration;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import kr.pyke.CheeseBridge;
import kr.pyke.type.PLATFORM;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import com.mojang.serialization.DataResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.core.HolderLookup;

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

    private static BridgeDataState fromEntries(List<TokenEntry> entries) {
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
    }

    private List<TokenEntry> toEntries() {
        List<TokenEntry> entries = new ArrayList<>();
        playerTokens.forEach((uuid, map) ->
            map.forEach((platform, info) ->
                entries.add(new TokenEntry(uuid.toString(), platform.name(), info.accessToken(), info.refreshToken()))
            )
        );

        return entries;
    }

    private static BridgeDataState fromTag(CompoundTag tag) {
        if (!tag.contains(NBT_KEY)) { return new BridgeDataState(); }

        DataResult<List<TokenEntry>> result = ENTRIES_CODEC.parse(NbtOps.INSTANCE, tag.get(NBT_KEY));
        return fromEntries(result.resultOrPartial(err -> CheeseBridge.LOGGER.error("[DataState] 로드 실패: {}", err)).orElse(List.of()));
    }

    private CompoundTag toTag(CompoundTag compoundTag) {
        DataResult<Tag> result = ENTRIES_CODEC.encodeStart(NbtOps.INSTANCE, toEntries());
        result.resultOrPartial(err -> CheeseBridge.LOGGER.error("[DataState] 저장 실패: {}", err))
            .ifPresent(encoded -> compoundTag.put(NBT_KEY, encoded));

        return compoundTag;
    }

    public static BridgeDataState load(CompoundTag tag, HolderLookup.Provider registries) { return fromTag(tag); }

    @Override
    public CompoundTag save(CompoundTag compoundTag, HolderLookup.Provider registries) { return toTag(compoundTag); }

    public TokenInfo getToken(UUID uuid, PLATFORM platform) {
        return playerTokens.getOrDefault(uuid, new HashMap<>()).get(platform);
    }

    public void setToken(UUID uuid, PLATFORM platform, TokenInfo tokenInfo) {
        playerTokens.computeIfAbsent(uuid, k -> new HashMap<>()).put(platform, tokenInfo);
        this.setDirty();
    }

    public static BridgeDataState getServerState(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(BridgeDataState::new, BridgeDataState::load, null), DATA_NAME);
    }
}
