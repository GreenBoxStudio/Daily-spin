package Greenbox.org.daily_case;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persists last spin timestamps across server restarts.
 */
public class LastSpinStorage extends SavedData {
    private static final String DATA_NAME = Daily_case.MODID + "_last_spin";
    private final Map<UUID, Long> lastSpins = new HashMap<>();

    public static LastSpinStorage get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(LastSpinStorage::load, LastSpinStorage::new, DATA_NAME);
    }

    public long getLastSpin(UUID uuid) {
        return lastSpins.getOrDefault(uuid, 0L);
    }

    public void setLastSpin(UUID uuid, long ts) {
        lastSpins.put(uuid, ts);
        setDirty();
    }

    public static LastSpinStorage load(CompoundTag tag) {
        LastSpinStorage storage = new LastSpinStorage();
        CompoundTag data = tag.getCompound("spins");
        for (String key : data.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                storage.lastSpins.put(uuid, data.getLong(key));
            } catch (IllegalArgumentException ignored) {}
        }
        return storage;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag data = new CompoundTag();
        for (Map.Entry<UUID, Long> e : lastSpins.entrySet()) {
            data.put(e.getKey().toString(), LongTag.valueOf(e.getValue()));
        }
        tag.put("spins", data);
        return tag;
    }
}
