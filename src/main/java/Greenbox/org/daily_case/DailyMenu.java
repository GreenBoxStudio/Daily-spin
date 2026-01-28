package Greenbox.org.daily_case;

import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.SimpleContainer;
import net.minecraft.ChatFormatting;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashMap;

public class DailyMenu extends AbstractContainerMenu {
    private static final int ROWS = 3;
    private static final int BUTTON_SLOT_CLAIM = 13;
    private static final int BUTTON_SLOT_SHOP = 15;
    private static final int BUTTON_SLOT_BACK = 18; // unten links für Zurück im Shop
    private static final int SPIN_ROW_START = 9; // second row in the 3x9 grid
    private static final int SPIN_ROW_LEN = 9;
    private static final String LAST_SPIN_KEY = "daily_case:last_spin";

    private static final int SPIN_TOTAL_STEPS = 25; // ultra schneller Spin
    private static final int SPIN_MIN_INTERVAL_TICKS = 1;
    private static final int SPIN_MAX_INTERVAL_TICKS = 5; // minimale Abbremsung

    private final SimpleContainer buttons = new SimpleContainer(ROWS * 9);
    private final Player player;

    private boolean spinning = false;
    private int spinStep = 0;
    private int spinTickAccumulator = 0;
    private final List<ItemStack> spinFrames = new ArrayList<>();
    private ItemStack pendingReward = ItemStack.EMPTY;
    private int revealTicks = 0;
    private LootConfigLoader.CaseDefinition activeCase = LootConfigLoader.get().getCase("daily");
    private final Map<Integer, String> shopSlots = new HashMap<>();
    private boolean shopView = false;
    private boolean previewMode = false;
    private String previewCaseId = "";
    private String rewardRarity = "common";

    public DailyMenu(int windowId, Inventory playerInventory) {
        this(windowId, playerInventory, playerInventory.player);
    }

    public DailyMenu(int windowId, Inventory playerInventory, Player player) {
        super(Daily_case.DAILY_MENU.get(), windowId);
        this.player = player;

        // Button grid (3 rows x 9 columns)
        for (int slot = 0; slot < buttons.getContainerSize(); slot++) {
            int x = 8 + (slot % 9) * 18;
            int y = 18 + (slot / 9) * 18;
            addSlot(new DisplaySlot(buttons, slot, x, y));
        }

        addPlayerInventory(playerInventory, 84);
        refreshButtons();
    }

    private void addPlayerInventory(Inventory inv, int startY) {
        // Player inventory
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, startY + row * 18));
            }
        }
        // Hotbar
        for (int col = 0; col < 9; ++col) {
            addSlot(new Slot(inv, col, 8 + col * 18, startY + 58));
        }
    }

    public int getContainerRows() {
        return ROWS;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY; // disable shift-clicking into the menu
    }

    private void refreshButtons() {
        fillBackground();
        shopSlots.clear();

        if (previewMode) {
            // Preview wird von showPreview() gehandhabt
            return;
        }

        if (shopView) {
            // Shop-Ansicht: Zurück-Button unten links, Cases füllen das gesamte Grid
            ItemStack back = namedItem(Items.RED_CONCRETE, tr("ui.daily_case.back"), List.of(tr("ui.daily_case.back.tooltip")));
            buttons.setItem(BUTTON_SLOT_BACK, back);
            buttons.setItem(BUTTON_SLOT_CLAIM, ItemStack.EMPTY);
            buttons.setItem(BUTTON_SLOT_SHOP, ItemStack.EMPTY);

            // Alle verfügbaren Slots für Cases (außer Zurück-Button Position)
            // Von rechts nach links, oben nach unten füllen
            int[] availableSlots = {
                // Erste Reihe (rechts nach links)
                8, 7, 6, 5, 4, 3, 2, 1, 0,
                // Zweite Reihe (rechts nach links)
                17, 16, 15, 14, 13, 12, 11, 10, 9,
                // Dritte Reihe (rechts nach links, außer Slot 18 = Zurück)
                26, 25, 24, 23, 22, 21, 20, 19
            };

            int slotIndex = 0;
            for (Map.Entry<String, LootConfigLoader.CaseDefinition> entry : LootConfigLoader.get().getCasesOrdered().entrySet()) {
                LootConfigLoader.CaseDefinition def = entry.getValue();
                if ("daily".equals(def.id)) continue;
                if (!def.availableInShop) continue;
                if (slotIndex >= availableSlots.length) break;

                int slot = availableSlots[slotIndex];
                ItemStack icon = LootConfigLoader.get().resolveItem(def.shopItemId).map(ItemStack::new).orElseGet(() -> new ItemStack(Items.CHEST));
                String title = tr("ui.daily_case.case_title", def.id);
                List<String> loreLines = new ArrayList<>();
                if (def.priceItemId != null && !def.priceItemId.isEmpty() && def.priceItemCount > 0) {
                    loreLines.add(tr("ui.daily_case.price.item", def.priceItemCount, friendlyItemName(def.priceItemId)));
                } else if (def.priceEmeralds > 0) {
                    loreLines.add(tr("ui.daily_case.price.emerald", def.priceEmeralds));
                } else {
                    loreLines.add(tr("ui.daily_case.price.free"));
                }
                loreLines.add(" ");
                loreLines.add(tr("ui.daily_case.possible_items"));
                loreLines.add("§6" + tr("ui.daily_case.rarity.legendary", formatPercent(def.rarityChances.getOrDefault("legendary", 0.0))));
                loreLines.add("§d" + tr("ui.daily_case.rarity.epic", formatPercent(def.rarityChances.getOrDefault("epic", 0.0))));
                loreLines.add("§9" + tr("ui.daily_case.rarity.rare", formatPercent(def.rarityChances.getOrDefault("rare", 0.0))));
                loreLines.add("§2" + tr("ui.daily_case.rarity.uncommon", formatPercent(def.rarityChances.getOrDefault("uncommon", 0.0))));
                loreLines.add("§7" + tr("ui.daily_case.rarity.common", formatPercent(def.rarityChances.getOrDefault("common", 0.0))));

                ItemStack shopStack = namedItem(icon.getItem(), title, loreLines);
                buttons.setItem(slot, shopStack);
                shopSlots.put(slot, def.id);
                slotIndex++;
            }
        } else {
            if (spinning) {
                ItemStack arrowTop = namedItem(Items.SPECTRAL_ARROW, tr("ui.daily_case.target.down"), List.of(tr("ui.daily_case.target")));
                ItemStack arrowBottom = namedItem(Items.SPECTRAL_ARROW, tr("ui.daily_case.target.up"), List.of(tr("ui.daily_case.target")));
                buttons.setItem(4, arrowTop);
                buttons.setItem(22, arrowBottom);
            }

            ItemStack spinStack;
            if (spinning) {
                spinStack = namedItem(Items.GOLD_BLOCK, tr("ui.daily_case.rolling"), List.of(tr("ui.daily_case.rolling.desc")));
            } else if (canSpin()) {
                spinStack = namedItem(Items.EMERALD_BLOCK, tr("ui.daily_case.spin"), List.of(tr("ui.daily_case.spin.desc")));
            } else {
                long remaining = getRemainingMillis();
                spinStack = namedItem(Items.BARRIER, tr("ui.daily_case.cooldown"), List.of(tr("ui.daily_case.cooldown.desc", formatDuration(remaining))));
            }
            buttons.setItem(BUTTON_SLOT_CLAIM, spinStack);

            ItemStack shopIcon = namedItem(Items.CHEST, tr("ui.daily_case.shop"), List.of(tr("ui.daily_case.shop.desc")));
            buttons.setItem(BUTTON_SLOT_SHOP, shopIcon);
        }

        buttons.setChanged();
        broadcastChanges();
    }

    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        boolean isRightClick = clickType == ClickType.PICKUP && dragType == 1;
        boolean isLeftClick = clickType == ClickType.PICKUP && dragType == 0;

        if (slotId >= 0 && slotId < buttons.getContainerSize()) {
            if (!player.level().isClientSide) {
                // Rechtsklick = Preview
                if (isRightClick) {
                    if (shopView && shopSlots.containsKey(slotId)) {
                        showPreview(shopSlots.get(slotId));
                        return;
                    } else if (!shopView && !previewMode && slotId == BUTTON_SLOT_CLAIM && !spinning) {
                        showPreview("daily");
                        return;
                    }
                }

                // Linksklick = Normal
                if (isLeftClick) {
                    if (previewMode) {
                        previewMode = false;
                        previewCaseId = "";
                        refreshButtons();
                        return;
                    }

                    if (shopView && shopSlots.containsKey(slotId)) {
                        tryPurchaseAndSpin(shopSlots.get(slotId));
                    } else {
                        handleButton(slotId);
                    }
                }
            }
            return; // ignore client-side interaction for buttons
        }
        super.clicked(slotId, dragType, clickType, player);
    }

    private void handleButton(int slotId) {
        if (spinning || revealTicks > 0) return;

        if (previewMode) {
            if (slotId == BUTTON_SLOT_BACK) {
                previewMode = false;
                previewCaseId = "";
                refreshButtons();
            }
            return;
        }

        if (shopView) {
            if (slotId == BUTTON_SLOT_BACK) {
                shopView = false;
                refreshButtons();
            }
            return;
        }
        if (slotId == BUTTON_SLOT_CLAIM) {
            if (!canSpin()) {
                long remaining = getRemainingMillis();
                player.displayClientMessage(Component.translatable("ui.daily_case.msg.cooldown", formatDuration(remaining)), true);
                return;
            }
            activeCase = LootConfigLoader.get().getCase("daily");
            startSpin();
        } else if (slotId == BUTTON_SLOT_SHOP) {
            shopView = true;
            refreshButtons();
        }
    }

    private void tryPurchaseAndSpin(String caseName) {
        LootConfigLoader.CaseDefinition def = LootConfigLoader.get().getCase(caseName);

        // Check custom item price first
        if (def.priceItemId != null && !def.priceItemId.isEmpty() && def.priceItemCount > 0) {
            Optional<Item> priceItem = LootConfigLoader.get().resolveItem(def.priceItemId);
            if (priceItem.isPresent()) {
                int invCount = countItem(priceItem.get());
                if (invCount < def.priceItemCount) {
                    player.displayClientMessage(Component.translatable("ui.daily_case.msg.not_enough", def.priceItemCount, friendlyItemName(def.priceItemId)), true);
                    return;
                }
                removeItems(priceItem.get(), def.priceItemCount);
            } else {
                player.displayClientMessage(Component.translatable("ui.daily_case.msg.item_not_found", def.priceItemId), true);
                return;
            }
        } else if (def.priceEmeralds > 0) {
            // Fallback to emerald price
            int invCount = countItem(Items.EMERALD);
            if (invCount < def.priceEmeralds) {
                player.displayClientMessage(Component.translatable("ui.daily_case.msg.not_enough_emeralds", def.priceEmeralds), true);
                return;
            }
            removeItems(Items.EMERALD, def.priceEmeralds);
        }

        activeCase = def;
        shopView = false;
        startSpin();
    }

    private int countItem(Item item) {
         int count = 0;
         for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
             ItemStack s = player.getInventory().getItem(i);
             if (!s.isEmpty() && s.is(item)) count += s.getCount();
         }
         return count;
     }

     private void removeItems(Item item, int amount) {
         int remaining = amount;
         for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
             ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && s.is(item)) {
                 int take = Math.min(remaining, s.getCount());
                 s.shrink(take);
                 remaining -= take;
             }
         }
     }

    private void showPreview(String caseId) {
        previewMode = true;
        previewCaseId = caseId;
        LootConfigLoader.CaseDefinition caseDef = LootConfigLoader.get().getCase(caseId);

        fillBackground();

        // Zurück-Button unten links
        ItemStack back = namedItem(Items.RED_CONCRETE, tr("ui.daily_case.back"), List.of(tr("ui.daily_case.back.tooltip")));
        buttons.setItem(BUTTON_SLOT_BACK, back);

        // Case Info oben mittig
        ItemStack caseIcon = LootConfigLoader.get().resolveItem(caseDef.shopItemId)
            .map(ItemStack::new)
            .orElseGet(() -> new ItemStack(Items.CHEST));
        List<String> caseInfo = new ArrayList<>();
        caseInfo.add(tr("ui.daily_case.possible_items"));
        caseInfo.add("§6" + tr("ui.daily_case.rarity.legendary", String.format("%.1f%%", caseDef.rarityChances.getOrDefault("legendary", 0.0) * 100)));
        caseInfo.add("§d" + tr("ui.daily_case.rarity.epic", String.format("%.1f%%", caseDef.rarityChances.getOrDefault("epic", 0.0) * 100)));
        caseInfo.add("§9" + tr("ui.daily_case.rarity.rare", String.format("%.1f%%", caseDef.rarityChances.getOrDefault("rare", 0.0) * 100)));
        caseInfo.add("§2" + tr("ui.daily_case.rarity.uncommon", String.format("%.1f%%", caseDef.rarityChances.getOrDefault("uncommon", 0.0) * 100)));
        caseInfo.add("§7" + tr("ui.daily_case.rarity.common", String.format("%.1f%%", caseDef.rarityChances.getOrDefault("common", 0.0) * 100)));
        buttons.setItem(4, namedItem(caseIcon.getItem(), tr("ui.daily_case.case_title", caseId.toUpperCase()), caseInfo));

        // Items nach Rarität sortiert anzeigen
        Map<String, List<LootConfigLoader.LootEntry>> itemsByRarity = new HashMap<>();
        itemsByRarity.put("legendary", new ArrayList<>());
        itemsByRarity.put("epic", new ArrayList<>());
        itemsByRarity.put("rare", new ArrayList<>());
        itemsByRarity.put("uncommon", new ArrayList<>());
        itemsByRarity.put("common", new ArrayList<>());

        for (LootConfigLoader.LootEntry item : caseDef.items) {
            itemsByRarity.computeIfAbsent(item.rarity, k -> new ArrayList<>()).add(item);
        }

        // Slots für Items
        int[] displaySlots = {
            9, 10, 11, 12, 13, 14, 15, 16, 17,  // Zweite Reihe
            19, 20, 21, 22, 23, 24, 25, 26      // Dritte Reihe (außer Zurück-Button)
        };

        int slotIndex = 0;
        String[] rarityOrder = {"legendary", "epic", "rare", "uncommon", "common"};

        for (String rarity : rarityOrder) {
            List<LootConfigLoader.LootEntry> items = itemsByRarity.get(rarity);
            if (items == null || items.isEmpty()) continue;

            for (LootConfigLoader.LootEntry item : items) {
                if (slotIndex >= displaySlots.length) break;

                Optional<Item> resolvedItem = LootConfigLoader.get().resolveItem(item.id);
                if (resolvedItem.isPresent()) {
                    ItemStack display = new ItemStack(resolvedItem.get(), item.maxCount);
                    applyEnchantments(display, item.enchantments);

                    ChatFormatting rarityColor = switch (rarity.toLowerCase()) {
                        case "legendary" -> ChatFormatting.GOLD;
                        case "epic" -> ChatFormatting.LIGHT_PURPLE;
                        case "rare" -> ChatFormatting.BLUE;
                        case "uncommon" -> ChatFormatting.GREEN;
                        default -> ChatFormatting.WHITE;
                    };

                    List<String> lore = new ArrayList<>();
                    lore.add(tr("ui.daily_case.preview.rarity", rarityColor + rarity.toUpperCase()));
                    lore.add(tr("ui.daily_case.preview.count", item.minCount, item.maxCount));
                    lore.add(tr("ui.daily_case.preview.weight", item.weight));

                    ItemStack displayWithLore = namedItem(
                        resolvedItem.get(),
                        rarityColor + display.getHoverName().getString(),
                        lore
                    );
                    displayWithLore.setCount(item.maxCount);
                    applyEnchantments(displayWithLore, item.enchantments);

                    buttons.setItem(displaySlots[slotIndex], displayWithLore);
                    slotIndex++;
                }
            }
        }

        buttons.setChanged();
        broadcastChanges();
    }

    private void fillBackground() {
        ItemStack filler = namedItem(Items.GRAY_STAINED_GLASS_PANE, "", List.of());
        for (int i = 0; i < buttons.getContainerSize(); i++) {
            buttons.setItem(i, filler.copy());
        }
    }

    private void startSpin() {
        spinning = true;
        spinStep = 0;
        spinTickAccumulator = 0;
        revealTicks = 0;
        pendingReward = rollReward();
        playStartEffects();

        // create frames with random items, ensure the reward appears near the end
        List<Item> pool = rewardPool();
        spinFrames.clear();
        int frameCount = 32;
        for (int i = 0; i < frameCount - 1; i++) {
            Item randomItem = pool.get(player.getRandom().nextInt(pool.size()));
            spinFrames.add(new ItemStack(randomItem));
        }
        spinFrames.add(pendingReward.copy());

        refreshButtons(); // sicherstellen, dass Shop-Items ausgeblendet sind während des Spins
    }

    public void serverTick() {
        if (!spinning) {
            if (revealTicks > 0) {
                revealTicks--;
                if (revealTicks == 0) {
                    refreshButtons();
                }
            }
            return;
        }
        spinTickAccumulator++;
        int interval = currentSpinInterval();
        if (spinTickAccumulator < interval) return;
        spinTickAccumulator = 0;

        int offset = spinStep % spinFrames.size();
        for (int i = 0; i < SPIN_ROW_LEN; i++) {
            ItemStack frame = spinFrames.get((offset + i) % spinFrames.size());
            buttons.setItem(SPIN_ROW_START + i, frame.copy());
        }
        buttons.setChanged();
        broadcastChanges();
        playRollingSound();

        spinStep++;
        if (spinStep >= SPIN_TOTAL_STEPS) {
            finishSpin();
        }
    }

    private int currentSpinInterval() {
        // Quadratisches Easing: bleibt länger schnell, nur sanftes Abbremsen am Ende
        float progress = spinStep / (float) Math.max(1, SPIN_TOTAL_STEPS);
        float eased = progress * progress;
        return SPIN_MIN_INTERVAL_TICKS + Math.round((SPIN_MAX_INTERVAL_TICKS - SPIN_MIN_INTERVAL_TICKS) * eased);
    }

    private void finishSpin() {
        spinning = false;
        // place the reward in the center slot and grant it
        buttons.setItem(BUTTON_SLOT_CLAIM, pendingReward.copy());
        boolean added = player.getInventory().add(pendingReward.copy());
        if (!added) {
            player.drop(pendingReward.copy(), false);
        }
        setLastSpin(System.currentTimeMillis());

        // Record stats
        CaseStats.recordCaseOpened(player, activeCase.id);
        CaseStats.recordItemWon(player, pendingReward.getItem().toString(), rewardRarity);

        // Colored win message based on rarity
        ChatFormatting rarityColor = switch (rewardRarity.toLowerCase()) {
            case "legendary" -> ChatFormatting.GOLD;
            case "epic" -> ChatFormatting.LIGHT_PURPLE;
            case "rare" -> ChatFormatting.BLUE;
            case "uncommon" -> ChatFormatting.GREEN;
            default -> ChatFormatting.WHITE;
        };
        player.sendSystemMessage(
            Component.translatable("ui.daily_case.msg.won", pendingReward.getHoverName(), rewardRarity.toUpperCase()).withStyle(rarityColor)
        );

         playWinEffects();

        // Extra effects for legendary/epic
        if ("legendary".equalsIgnoreCase(rewardRarity)) {
            playLegendaryEffects();
        } else if ("epic".equalsIgnoreCase(rewardRarity)) {
            playEpicEffects();
        }

         pendingReward = ItemStack.EMPTY;
         revealTicks = 40; // ~2s bei 20 TPS
     }

    private void playStartEffects() {
        if (player.level() instanceof ServerLevel level) {
            level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.5, player.getZ(), 8, 0.2, 0.3, 0.2, 0.01);
        }
    }

    private void playRollingSound() {
        if (player.level() instanceof ServerLevel level) {
            float pitch = 0.8f + player.getRandom().nextFloat() * 0.2f;
            level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.5f, pitch);
        }
    }

    private void playWinEffects() {
         if (player.level() instanceof ServerLevel level) {
             level.playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
             level.sendParticles(ParticleTypes.FIREWORK, player.getX(), player.getY() + 1.5, player.getZ(), 12, 0.3, 0.4, 0.3, 0.01);
         }
     }

    private void playLegendaryEffects() {
        if (player.level() instanceof ServerLevel level) {
            // Extra sounds and particles for legendary
            level.playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.7f, 1.5f);
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.5, player.getZ(), 30, 0.5, 0.5, 0.5, 0.1);
            level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 2, player.getZ(), 20, 0.3, 0.5, 0.3, 0.05);
        }
    }

    private void playEpicEffects() {
        if (player.level() instanceof ServerLevel level) {
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f);
            level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.5, player.getZ(), 20, 0.4, 0.4, 0.4, 0.05);
        }
    }

    private boolean canSpin() {
        return getRemainingMillis() <= 0;
    }

    private long getRemainingMillis() {
        long last = getLastSpin();
        if (last <= 0) return 0;
        long cooldown = Math.max(1, Config.dailyCooldownHours) * 60L * 60L * 1000L;
        long elapsed = System.currentTimeMillis() - last;
        return Math.max(0, cooldown - elapsed);
    }

    private ServerLevel overworld() {
        return player.getServer() != null ? player.getServer().overworld() : (player.level() instanceof ServerLevel sl ? sl : null);
    }

    private long getLastSpin() {
        ServerLevel ow = overworld();
        if (ow != null) {
            long stored = LastSpinStorage.get(ow).getLastSpin(player.getUUID());
            if (stored > 0) return stored;
        }
        CompoundTag persisted = player.getPersistentData();
        CompoundTag data = persisted.getCompound(Player.PERSISTED_NBT_TAG);
        return data.getLong(LAST_SPIN_KEY);
    }

    private void setLastSpin(long timestamp) {
        // Immer auch in Player-NBT schreiben als Backup
        CompoundTag persisted = player.getPersistentData();
        CompoundTag data = persisted.getCompound(Player.PERSISTED_NBT_TAG);
        data.putLong(LAST_SPIN_KEY, timestamp);
        persisted.put(Player.PERSISTED_NBT_TAG, data);

        ServerLevel ow = overworld();
        if (ow != null) {
            LastSpinStorage.get(ow).setLastSpin(player.getUUID(), timestamp);
        }
    }

    private ItemStack rollReward() {
        rewardRarity = LootConfigLoader.get().rollRarity(activeCase);
        Optional<LootConfigLoader.ItemStackWithWeight> chosen = LootConfigLoader.get().pickItemFromCase(rewardRarity, activeCase);
        if (chosen.isEmpty()) {
            return new ItemStack(Items.DIRT);
        }
        ItemStack stack = new ItemStack(chosen.get().item(), chosen.get().count());
        applyEnchantments(stack, chosen.get().enchantments());
        return stack;
    }

    private void applyEnchantments(ItemStack stack, List<String> enchantments) {
        if (enchantments == null) return;
        for (String entry : enchantments) {
            if (entry == null || entry.isEmpty()) continue;
            String[] parts = entry.split(":");
            if (parts.length < 2) continue;
            String id = parts.length >= 2 ? parts[0] + ":" + parts[1] : parts[0];
            int level = 1;
            if (parts.length >= 3) {
                try {
                    level = Math.max(1, Integer.parseInt(parts[2]));
                } catch (NumberFormatException ignored) { }
            }
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null) continue;
            Enchantment ench = ForgeRegistries.ENCHANTMENTS.getValue(rl);
            if (ench != null) {
                stack.enchant(ench, level);
            }
        }
    }

    private static ItemStack namedItem(Item item, String name, List<String> loreLines) {
        ItemStack stack = new ItemStack(item);
        if (!name.isEmpty()) {
            stack.setHoverName(Component.literal(name).withStyle(ChatFormatting.GREEN));
        }
        if (!loreLines.isEmpty()) {
            ListTag lore = new ListTag();
            for (String line : loreLines) {
                lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(line).withStyle(ChatFormatting.GRAY))));
            }
            CompoundTag display = stack.getOrCreateTagElement("display");
            display.put("Lore", lore);
        }
        return stack;
    }

    private static String formatDuration(long millis) {
        Duration d = Duration.ofMillis(Math.max(0, millis));
        long hours = d.toHours();
        long minutes = d.minusHours(hours).toMinutes();
        long seconds = d.minusHours(hours).minusMinutes(minutes).toSeconds();
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }

    private static String formatPercent(double v) {
        return String.format("%.1f%%", v * 100);
    }

    private static class DisplaySlot extends Slot {
        public DisplaySlot(SimpleContainer container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    private List<Item> rewardPool() {
        List<Item> pool = new ArrayList<>();
        for (Map.Entry<String, Double> chance : activeCase.rarityChances.entrySet()) {
            LootConfigLoader.get().pickItemFromCase(chance.getKey(), activeCase)
                .ifPresent(i -> pool.add(i.item()));
        }
        if (pool.isEmpty()) {
            pool.add(Items.DIRT);
        }
        return pool;
    }

    private String friendlyItemName(String itemId) {
        return LootConfigLoader.get().resolveItem(itemId)
            .map(it -> new ItemStack(it).getHoverName().getString())
            .orElse(itemId.replace("minecraft:", ""));
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }
}
