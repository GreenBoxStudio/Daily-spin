package Greenbox.org.daily_case;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Loads loot and case definitions from config/daily_case/*.json with sane defaults.
 */
public class LootConfigLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger("daily_case:loot");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get().resolve("daily_case");
    private static final Path ITEMS_JSON = CONFIG_DIR.resolve("items.json");
    private static final Path CASES_JSON = CONFIG_DIR.resolve("case.json");

    private static final LootConfigLoader INSTANCE = new LootConfigLoader();

    public static LootConfigLoader get() {
        return INSTANCE;
    }

    private final Map<String, CaseDefinition> cases = new HashMap<>();
    private final List<LootEntry> entries = new ArrayList<>();

    private LootConfigLoader() {
        try {
            Files.createDirectories(CONFIG_DIR);
        } catch (IOException e) {
            LOGGER.error("Could not create config directory {}", CONFIG_DIR, e);
        }
        loadItems();
        loadCases();
    }

    private void loadItems() {
        List<LootEntry> loaded = readJson(ITEMS_JSON, new TypeToken<List<LootEntry>>() {}.getType());
        if (loaded == null || loaded.isEmpty()) {
            loaded = defaultItems();
            writeJson(ITEMS_JSON, loaded);
        }
        entries.clear();
        entries.addAll(loaded);
        LOGGER.info("Loaded {} loot entries", entries.size());
    }

    private void loadCases() {
        Map<String, CaseDefinition> loaded = readJson(CASES_JSON, new TypeToken<Map<String, CaseDefinition>>() {}.getType());
        if (loaded == null || loaded.isEmpty()) {
            loaded = defaultCases();
            writeJson(CASES_JSON, loaded);
        }
        cases.clear();
        cases.putAll(loaded);
        LOGGER.info("Loaded {} cases", cases.size());
    }

    private <T> T readJson(Path path, Type type) {
        if (!Files.exists(path)) return null;
        try (Reader reader = Files.newBufferedReader(path)) {
            return GSON.fromJson(reader, type);
        } catch (Exception e) {
            LOGGER.warn("Failed to read {}: {}", path, e.getMessage());
            return null;
        }
    }

    private void writeJson(Path path, Object data) {
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(data, writer);
        } catch (Exception e) {
            LOGGER.warn("Failed to write default config {}: {}", path, e.getMessage());
        }
    }

    private List<LootEntry> defaultItems() {
        List<LootEntry> list = new ArrayList<>();
        list.add(new LootEntry("minecraft:iron_ingot", "common", 50, 1, 3));
        list.add(new LootEntry("minecraft:gold_ingot", "common", 35, 1, 3));
        list.add(new LootEntry("minecraft:emerald", "uncommon", 20, 1, 2));
        list.add(new LootEntry("minecraft:diamond", "rare", 10, 1, 1));
        list.add(new LootEntry("minecraft:netherite_scrap", "epic", 5, 1, 1));
        list.add(new LootEntry("minecraft:totem_of_undying", "epic", 3, 1, 1));
        list.add(new LootEntry("minecraft:elytra", "legendary", 1, 1, 1));
        list.add(new LootEntry("minecraft:beacon", "legendary", 1, 1, 1));
        list.add(new LootEntry("minecraft:enchanted_golden_apple", "legendary", 1, 1, 1));
        return list;
    }

    private Map<String, CaseDefinition> defaultCases() {
        Map<String, CaseDefinition> map = new HashMap<>();
        CaseDefinition daily = new CaseDefinition();
        daily.id = "daily";
        daily.cooldownHours = 24;
        daily.priceEmeralds = 0;
        daily.shopItemId = "minecraft:chest";
        daily.availableInShop = false;
        daily.rarityChances = new HashMap<>();
        daily.rarityChances.put("legendary", 0.01);
        daily.rarityChances.put("epic", 0.04);
        daily.rarityChances.put("rare", 0.15);
        daily.rarityChances.put("uncommon", 0.3);
        daily.rarityChances.put("common", 0.5);
        map.put("daily", daily);

        CaseDefinition premium = new CaseDefinition();
        premium.id = "premium";
        premium.cooldownHours = 0;
        premium.priceEmeralds = 25;
        premium.shopItemId = "minecraft:ender_chest";
        premium.availableInShop = true;
        premium.rarityChances = new HashMap<>();
        premium.rarityChances.put("legendary", 0.05);
        premium.rarityChances.put("epic", 0.10);
        premium.rarityChances.put("rare", 0.20);
        premium.rarityChances.put("uncommon", 0.25);
        premium.rarityChances.put("common", 0.40);
        map.put("premium", premium);
        return map;
    }

    public CaseDefinition getCase(String name) {
        return cases.getOrDefault(name, defaultCases().get("daily"));
    }

    public Map<String, CaseDefinition> getCasesOrdered() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(cases));
    }

    public Optional<Item> resolveItem(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return Optional.empty();
        return Optional.ofNullable(ForgeRegistries.ITEMS.getValue(rl));
    }

    public Optional<ItemStackWithWeight> pickItem(String rarity) {
        List<ItemStackWithWeight> pool = buildPool(rarity);
        if (pool.isEmpty()) return Optional.empty();
        int total = pool.stream().mapToInt(ItemStackWithWeight::weight).sum();
        int roll = ThreadLocalRandom.current().nextInt(total) + 1;
        int acc = 0;
        for (ItemStackWithWeight entry : pool) {
            acc += entry.weight();
            if (roll <= acc) return Optional.of(entry);
        }
        return Optional.of(pool.get(pool.size() - 1));
    }

    public Optional<ItemStackWithWeight> pickItemFromCase(String rarity, CaseDefinition caseDef) {
        List<ItemStackWithWeight> pool = buildPoolFromCase(rarity, caseDef);
        if (pool.isEmpty()) return Optional.empty();
        int total = pool.stream().mapToInt(ItemStackWithWeight::weight).sum();
        int roll = ThreadLocalRandom.current().nextInt(total) + 1;
        int acc = 0;
        for (ItemStackWithWeight entry : pool) {
            acc += entry.weight();
            if (roll <= acc) return Optional.of(entry);
        }
        return Optional.of(pool.get(pool.size() - 1));
    }

    private List<ItemStackWithWeight> buildPool(String rarity) {
        List<ItemStackWithWeight> pool = new ArrayList<>();
        for (LootEntry e : entries) {
            if (!Objects.equals(e.rarity, rarity)) continue;
            ResourceLocation rl = ResourceLocation.tryParse(e.id);
            if (rl == null) continue;
            Item item = ForgeRegistries.ITEMS.getValue(rl);
            if (item == null) continue;
            int count = ThreadLocalRandom.current().nextInt(e.minCount, e.maxCount + 1);
            pool.add(new ItemStackWithWeight(item, count, Math.max(1, e.weight), e.enchantments));
        }
        return pool;
    }

    private List<ItemStackWithWeight> buildPoolFromCase(String rarity, CaseDefinition caseDef) {
        List<ItemStackWithWeight> pool = new ArrayList<>();
        List<LootEntry> sourceEntries = (caseDef.items != null && !caseDef.items.isEmpty())
            ? caseDef.items
            : entries;

        for (LootEntry e : sourceEntries) {
            if (!Objects.equals(e.rarity, rarity)) continue;
            ResourceLocation rl = ResourceLocation.tryParse(e.id);
            if (rl == null) continue;
            Item item = ForgeRegistries.ITEMS.getValue(rl);
            if (item == null) continue;
            int count = ThreadLocalRandom.current().nextInt(e.minCount, e.maxCount + 1);
            pool.add(new ItemStackWithWeight(item, count, Math.max(1, e.weight), e.enchantments));
        }
        return pool;
    }

    public String rollRarity(CaseDefinition def) {
        double roll = ThreadLocalRandom.current().nextDouble();
        double acc = 0;
        for (Map.Entry<String, Double> entry : def.rarityChances.entrySet()) {
            acc += entry.getValue();
            if (roll <= acc) return entry.getKey();
        }
        return def.rarityChances.keySet().stream().findFirst().orElse("common");
    }

    // Data classes
    public static class LootEntry {
        public String id;
        public String rarity;
        public int weight;
        public int minCount;
        public int maxCount;
        public List<String> enchantments = new ArrayList<>();

        public LootEntry() {}
        public LootEntry(String id, String rarity, int weight, int minCount, int maxCount) {
            this.id = id;
            this.rarity = rarity;
            this.weight = weight;
            this.minCount = minCount;
            this.maxCount = maxCount;
        }

        public ChatFormatting getRarityColor() {
            return switch (rarity.toLowerCase()) {
                case "legendary" -> ChatFormatting.GOLD;
                case "epic" -> ChatFormatting.LIGHT_PURPLE;
                case "rare" -> ChatFormatting.BLUE;
                case "uncommon" -> ChatFormatting.GREEN;
                default -> ChatFormatting.WHITE;
            };
        }
    }

    public static class CaseDefinition {
         public String id = "";
         public Map<String, Double> rarityChances = new HashMap<>();
         public int cooldownHours = 24;
         public int priceEmeralds = 0;
         public String priceItemId = ""; // z.B. "minecraft:diamond" für Item-Preis
         public int priceItemCount = 0;
         public String shopItemId = "minecraft:chest";
         public boolean availableInShop = true;
         public List<LootEntry> items = new ArrayList<>();
    }

    public record ItemStackWithWeight(Item item, int count, int weight, List<String> enchantments) {}
}
