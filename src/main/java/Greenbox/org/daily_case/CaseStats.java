package Greenbox.org.daily_case;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks player statistics for case openings
 */
public class CaseStats {
    private static final String STATS_KEY = "daily_case:stats";
    private static final String CASES_OPENED_KEY = "cases_opened";
    private static final String ITEMS_WON_KEY = "items_won";
    private static final String TOTAL_LEGENDARY_KEY = "total_legendary";
    private static final String TOTAL_EPIC_KEY = "total_epic";
    private static final String TOTAL_RARE_KEY = "total_rare";

    public static void recordCaseOpened(Player player, String caseId) {
        CompoundTag stats = getStats(player);
        stats.putInt(CASES_OPENED_KEY + "_" + caseId, stats.getInt(CASES_OPENED_KEY + "_" + caseId) + 1);
        stats.putInt(CASES_OPENED_KEY, stats.getInt(CASES_OPENED_KEY) + 1);
        saveStats(player, stats);
    }

    public static void recordItemWon(Player player, String itemId, String rarity) {
        CompoundTag stats = getStats(player);
        stats.putInt(ITEMS_WON_KEY + "_" + itemId, stats.getInt(ITEMS_WON_KEY + "_" + itemId) + 1);

        // Track by rarity
        String rarityKey = switch (rarity.toLowerCase()) {
            case "legendary" -> TOTAL_LEGENDARY_KEY;
            case "epic" -> TOTAL_EPIC_KEY;
            case "rare" -> TOTAL_RARE_KEY;
            default -> null;
        };
        if (rarityKey != null) {
            stats.putInt(rarityKey, stats.getInt(rarityKey) + 1);
        }

        saveStats(player, stats);
    }

    public static int getTotalCasesOpened(Player player) {
        return getStats(player).getInt(CASES_OPENED_KEY);
    }

    public static int getCasesOpenedByType(Player player, String caseId) {
        return getStats(player).getInt(CASES_OPENED_KEY + "_" + caseId);
    }

    public static int getItemsWon(Player player, String itemId) {
        return getStats(player).getInt(ITEMS_WON_KEY + "_" + itemId);
    }

    public static int getLegendaryCount(Player player) {
        return getStats(player).getInt(TOTAL_LEGENDARY_KEY);
    }

    public static int getEpicCount(Player player) {
        return getStats(player).getInt(TOTAL_EPIC_KEY);
    }

    public static int getRareCount(Player player) {
        return getStats(player).getInt(TOTAL_RARE_KEY);
    }

    private static CompoundTag getStats(Player player) {
        CompoundTag persisted = player.getPersistentData();
        CompoundTag data = persisted.getCompound(Player.PERSISTED_NBT_TAG);
        if (!data.contains(STATS_KEY)) {
            data.put(STATS_KEY, new CompoundTag());
        }
        return data.getCompound(STATS_KEY);
    }

    private static void saveStats(Player player, CompoundTag stats) {
        CompoundTag persisted = player.getPersistentData();
        CompoundTag data = persisted.getCompound(Player.PERSISTED_NBT_TAG);
        data.put(STATS_KEY, stats);
        persisted.put(Player.PERSISTED_NBT_TAG, data);
    }
}
