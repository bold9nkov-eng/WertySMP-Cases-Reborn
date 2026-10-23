package ru.wertysmp.cases;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class WertyCases extends JavaPlugin implements Listener, TabCompleter {
    private NamespacedKey caseKey;
    private File dataFile;
    private YamlConfiguration data;

    @Override public void onEnable() {
        saveDefaultConfig();
        caseKey = new NamespacedKey(this, "case_type");
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("wcases")).setTabCompleter(this);
        getLogger().info("WertySMP-Cases 2.1.0 включён.");
        Bukkit.getScheduler().runTaskTimer(this, this::deliverAllOnline, 20L, 100L);
    }

    @Override public void onDisable() { saveData(); }

    private void saveData() {
        try { if (!getDataFolder().exists()) getDataFolder().mkdirs(); data.save(dataFile); }
        catch (IOException e) { getLogger().severe("Не удалось сохранить data.yml: " + e.getMessage()); }
    }


    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("wertysmpcases.admin")) {
                options.add("give");
                options.add("reload");
            }
            if (sender instanceof Player) options.add("daily");
            return filterOptions(options, args[0]);
        }
        if (args[0].equalsIgnoreCase("give") && sender.hasPermission("wertysmpcases.admin")) {
            if (args.length == 2) {
                for (Player online : Bukkit.getOnlinePlayers()) options.add(online.getName());
                return filterOptions(options, args[1]);
            }
            if (args.length == 3) {
                options.addAll(List.of("ordinary", "epic", "legendary"));
                return filterOptions(options, args[2]);
            }
            if (args.length == 4) {
                options.addAll(List.of("1", "5", "10", "16", "32", "64"));
                return filterOptions(options, args[3]);
            }
        }
        return Collections.emptyList();
    }

    private List<String> filterOptions(List<String> options, String typed) {
        String prefix = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
    private ItemStack named(Material material, int amount, String name, List<String> lore) {
        ItemStack item = new ItemStack(material, Math.max(1, Math.min(amount, material.getMaxStackSize())));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) meta.setDisplayName(color(name));
            if (lore != null) meta.setLore(lore.stream().map(this::color).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack caseItem(String type) {
        ConfigurationSection section = getConfig().getConfigurationSection("cases." + type);
        Material mat = Material.CHEST;
        String name = "&fКейс";
        List<String> lore = List.of("&7ПКМ, держа кейс в руке, чтобы открыть");
        if (section != null) {
            Material configured = Material.matchMaterial(section.getString("material", "CHEST"));
            if (configured != null && configured.isItem()) mat = configured;
            name = section.getString("name", name);
            lore = section.getStringList("lore");
        }
        ItemStack item = named(mat, 1, name, lore);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.getPersistentDataContainer().set(caseKey, PersistentDataType.STRING, type); item.setItemMeta(meta); }
        return item;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { sender.sendMessage(color("&e/wcases give <игрок> <ordinary|epic|legendary> [количество]")); sender.sendMessage(color("&e/wcases daily &7— ежедневные награды")); sender.sendMessage(color("&e/wcases reload")); return true; }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("wertysmpcases.admin")) { sender.sendMessage(color("&cНедостаточно прав.")); return true; }
            reloadConfig(); sender.sendMessage(color("&aКонфигурация перезагружена.")); return true;
        }
        if (args[0].equalsIgnoreCase("daily")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Эта команда доступна игроку."); return true; }
            openDaily(p); return true;
        }
        if (args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("wertysmpcases.admin")) { sender.sendMessage(color("&cНедостаточно прав.")); return true; }
            if (args.length < 3) { sender.sendMessage(color("&c/wcases give <игрок> <ordinary|epic|legendary> [количество]")); return true; }
            Player target = Bukkit.getPlayerExact(args[1]);
            String type = args[2].toLowerCase(Locale.ROOT);
            if (target == null) { sender.sendMessage(color("&cИгрок должен быть онлайн.")); return true; }
            if (!Set.of("ordinary", "epic", "legendary").contains(type)) { sender.sendMessage(color("&cНеизвестный кейс.")); return true; }
            int amount = 1;
            if (args.length >= 4) try { amount = Math.max(1, Math.min(64, Integer.parseInt(args[3]))); } catch (NumberFormatException ex) { sender.sendMessage(color("&cКоличество должно быть числом.")); return true; }
            for (int i = 0; i < amount; i++) queueReward(target.getUniqueId(), caseItem(type));
            deliver(target);
            sender.sendMessage(color("&aВыдано кейсов: " + amount + " игроку " + target.getName()));
            return true;
        }
        sender.sendMessage(color("&cНеизвестная команда. Используй /wcases")); return true;
    }

    @EventHandler public void onCaseUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack hand = event.getItem();
        if (hand == null || !hand.hasItemMeta()) return;
        String type = hand.getItemMeta().getPersistentDataContainer().get(caseKey, PersistentDataType.STRING);
        if (type == null || !Set.of("ordinary", "epic", "legendary").contains(type)) return;
        event.setCancelled(true);
        Player p = event.getPlayer();
        // Persist the reward transaction before consuming the case.
        List<ItemStack> rewards = rollRewards(type);
        for (ItemStack reward : rewards) queueReward(p.getUniqueId(), reward);
        saveData();
        if (hand.getAmount() <= 1) p.getInventory().setItemInMainHand(null); else hand.setAmount(hand.getAmount() - 1);
        p.updateInventory();
        deliver(p);
        p.sendMessage(color("&aКейс открыт! &7Наград: &f" + rewards.size()));
    }

    private List<ItemStack> rollRewards(String type) {
        List<ItemStack> out = new ArrayList<>();
        ConfigurationSection section = getConfig().getConfigurationSection("rewards." + type);
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection r = section.getConfigurationSection(id);
                if (r == null) continue;
                double chance = Math.max(0, Math.min(100, r.getDouble("chance", 0)));
                if (Math.random() * 100 >= chance) continue;
                Material mat = Material.matchMaterial(r.getString("material", "EMERALD"));
                if (mat == null || !mat.isItem()) continue;
                int min = Math.max(1, r.getInt("min", 1));
                int max = Math.max(min, r.getInt("max", min));
                int amount = min + new Random().nextInt(Math.min(100000, max - min + 1));
                out.add(named(mat, amount, r.getString("display", null), null));
            }
        }
        if (out.isEmpty()) {
            ConfigurationSection min = getConfig().getConfigurationSection("settings.minimum-reward");
            Material mat = Material.EMERALD; int amount = 1; String name = "&aНаграда";
            if (min != null) { Material m = Material.matchMaterial(min.getString("material", "EMERALD")); if (m != null && m.isItem()) mat = m; amount = min.getInt("amount", 1); name = min.getString("name", name); }
            out.add(named(mat, amount, name, null));
        }
        return out;
    }

    private void queueReward(UUID uuid, ItemStack item) {
        List<String> encoded = data.getStringList("pending." + uuid);
        encoded = new ArrayList<>(encoded);
        encoded.add(Base64.getEncoder().encodeToString(item.serializeAsBytes()));
        data.set("pending." + uuid, encoded);
        saveData();
    }

    private void deliverAllOnline() { for (Player p : Bukkit.getOnlinePlayers()) deliver(p); }
    private void deliver(Player p) {
        String path = "pending." + p.getUniqueId();
        List<String> encoded = new ArrayList<>(data.getStringList(path));
        if (encoded.isEmpty()) return;
        List<ItemStack> pending = new ArrayList<>();
        try {
            for (String s : encoded) pending.add(ItemStack.deserializeBytes(Base64.getDecoder().decode(s)));
        } catch (Exception ex) { getLogger().severe("Ошибка чтения ожидающих наград для " + p.getName() + ": " + ex.getMessage()); return; }
        // Keep rewards queued if there is not enough space for the whole transaction.
        ItemStack[] simulated = p.getInventory().getStorageContents().clone();
        for (ItemStack reward : pending) {
            Map<Integer, ItemStack> leftover = simulateAdd(simulated, reward.clone());
            if (!leftover.isEmpty()) { p.sendMessage(color("&eИнвентарь заполнен. Освободи место — награды будут выданы автоматически.")); return; }
        }
        // Queue is removed before delivery to avoid repeating a reward after a restart.
        data.set(path, null); saveData();
        for (ItemStack reward : pending) {
            Map<Integer, ItemStack> leftover = p.getInventory().addItem(reward);
            if (!leftover.isEmpty()) {
                for (ItemStack item : leftover.values()) queueReward(p.getUniqueId(), item);
            }
        }
        p.updateInventory();
    }

    private Map<Integer, ItemStack> simulateAdd(ItemStack[] storage, ItemStack incoming) {
        Map<Integer, ItemStack> leftover = new HashMap<>();
        for (int i = 0; i < storage.length && incoming.getAmount() > 0; i++) {
            ItemStack cur = storage[i];
            if (cur != null && cur.isSimilar(incoming) && cur.getAmount() < cur.getMaxStackSize()) {
                int moved = Math.min(incoming.getAmount(), cur.getMaxStackSize() - cur.getAmount());
                cur.setAmount(cur.getAmount() + moved); incoming.setAmount(incoming.getAmount() - moved);
            }
        }
        for (int i = 0; i < storage.length && incoming.getAmount() > 0; i++) if (storage[i] == null || storage[i].getType().isAir()) {
            int moved = Math.min(incoming.getAmount(), incoming.getMaxStackSize());
            storage[i] = incoming.clone(); storage[i].setAmount(moved); incoming.setAmount(incoming.getAmount() - moved);
        }
        if (incoming.getAmount() > 0) leftover.put(0, incoming);
        return leftover;
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { Bukkit.getScheduler().runTask(this, () -> deliver(event.getPlayer())); }

    private static final class DailyHolder implements InventoryHolder {
        final UUID uuid;
        DailyHolder(UUID uuid) { this.uuid = uuid; }
        @Override public Inventory getInventory() { return null; }
    }

    private LocalDate today() {
        try { return LocalDate.now(ZoneId.of(getConfig().getString("settings.timezone", "Europe/Moscow"))); }
        catch (Exception e) { return LocalDate.now(ZoneId.of("UTC")); }
    }

    private void openDaily(Player p) {
        if (!getConfig().getBoolean("daily.enabled", true)) { p.sendMessage(color("&cЕжедневные награды отключены.")); return; }
        LocalDate now = today();
        String base = "daily." + p.getUniqueId() + ".";
        String first = data.getString(base + "first");
        if (first == null) { first = now.toString(); data.set(base + "first", first); saveData(); }
        LocalDate start;
        try { start = LocalDate.parse(first); } catch (Exception e) { start = now; data.set(base + "first", now.toString()); }
        int day = (int) (Math.floorMod(ChronoUnit.DAYS.between(start, now), 15) + 1);
        String claimed = data.getString(base + "claimed");
        Inventory inv = Bukkit.createInventory(new DailyHolder(p.getUniqueId()), 27, color("&8Ежедневные награды"));
        for (int d = 1; d <= 15; d++) {
            ConfigurationSection r = getConfig().getConfigurationSection("daily.rewards." + d);
            Material mat = Material.CHEST; int amount = 1;
            if (r != null) { Material m = Material.matchMaterial(r.getString("material", "CHEST")); if (m != null && m.isItem()) mat = m; amount = r.getInt("amount", 1); }
            String label = d == day ? "&aДень " + d + " — нажми, чтобы забрать" : d < day ? "&7День " + d : "&8День " + d;
            if (r == null) label = "&cДень " + d + " — награда не настроена";
            ItemStack icon = named(mat, amount, label, List.of("&7Награда доступна только в свой календарный день"));
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) { meta.getPersistentDataContainer().set(new NamespacedKey(this, "daily_day"), PersistentDataType.INTEGER, d); icon.setItemMeta(meta); }
            inv.setItem(d - 1, icon);
        }
        if (now.toString().equals(claimed)) inv.setItem(22, named(Material.LIME_STAINED_GLASS_PANE, 1, "&aСегодня уже забрано", List.of("&7Возвращайся завтра")));
        else inv.setItem(22, named(Material.CLOCK, 1, "&eСегодня: день " + day, List.of("&7Пропущенные дни нельзя забрать задним числом")));
        p.openInventory(inv);
    }

    @EventHandler public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof DailyHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p) || !p.getUniqueId().equals(holder.uuid)) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        Integer dayClicked = clicked.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(this, "daily_day"), PersistentDataType.INTEGER);
        if (dayClicked == null) return;
        LocalDate now = today();
        String base = "daily." + p.getUniqueId() + ".";
        String first = data.getString(base + "first", now.toString());
        int day = (int) (Math.floorMod(ChronoUnit.DAYS.between(LocalDate.parse(first), now), 15) + 1);
        if (dayClicked != day) { p.sendMessage(color("&cСегодня доступен только день " + day + ".")); return; }
        if (now.toString().equals(data.getString(base + "claimed"))) { p.sendMessage(color("&eТы уже забрал награду сегодня.")); return; }
        ConfigurationSection r = getConfig().getConfigurationSection("daily.rewards." + day);
        if (r == null) { p.sendMessage(color("&cНаграда для этого дня не настроена.")); return; }
        Material mat = Material.matchMaterial(r.getString("material", "BREAD"));
        if (mat == null || !mat.isItem()) { p.sendMessage(color("&cВ конфиге указана неизвестная награда.")); return; }
        ItemStack reward = new ItemStack(mat, Math.max(1, Math.min(r.getInt("amount", 1), 64)));
        // Record the claim and durable pending reward before attempting delivery.
        queueReward(p.getUniqueId(), reward);
        data.set(base + "claimed", now.toString());
        saveData();
        deliver(p);
        p.closeInventory();
        p.sendMessage(color("&aНаграда за день " + day + " добавлена в очередь выдачи."));
    }
}
