package dev.lsdmc.edencells.gui;

import dev.lsdmc.edencells.EdenCells;
import dev.lsdmc.edencells.gui.holder.CellGuiHolder;
import dev.lsdmc.edencells.utils.MessageUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import net.alex9849.arm.regions.Region;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public class MemberSelectionGUI implements Listener {
  private final EdenCells plugin;
  
  private final NamespacedKey regionIdKey;
  
  private final NamespacedKey actionKey;
  
  private final NamespacedKey playerUuidKey;
  
  private final NamespacedKey pageKey;
  
  public MemberSelectionGUI(EdenCells plugin) {
    this.plugin = plugin;
    this.regionIdKey = new NamespacedKey((Plugin)plugin, "region_id");
    this.actionKey = new NamespacedKey((Plugin)plugin, "action");
    this.playerUuidKey = new NamespacedKey((Plugin)plugin, "player_uuid");
    this.pageKey = new NamespacedKey((Plugin)plugin, "page");
    Bukkit.getPluginManager().registerEvents(this, (Plugin)plugin);
  }
  
  public void openAddMemberGUI(Player player, Region region, int page) {
    String regionId = region.getRegion().getId();
    List<Player> onlinePlayers = getEligiblePlayersForAddition(region, player);
    int totalPages = Math.max(1, (onlinePlayers.size() + 35) / 36);
    page = Math.max(0, Math.min(page, totalPages - 1));
    CellGuiHolder holder = new CellGuiHolder("ADD_MEMBER");
    Inventory inv = Bukkit.createInventory((InventoryHolder)holder, 54, 
        Component.text("Add Member to " + regionId).color(TextColor.color(10040012)));
    holder.setInventory(inv);
    ItemStack infoItem = new ItemStack(Material.EMERALD);
    ItemMeta infoMeta = infoItem.getItemMeta();
    infoMeta.displayName(Component.text("Add Member").color((TextColor)NamedTextColor.GREEN));
    List<Component> infoLore = new ArrayList<>();
    infoLore.add(Component.text("Region: " + regionId).color((TextColor)NamedTextColor.YELLOW));
    infoLore.add(Component.text("Current members: " + region.getRegion().getMembers().size()).color((TextColor)NamedTextColor.GRAY));
    infoLore.add(Component.text("Available slots: " + (region.getMaxMembers() >= 0 ? (region.getMaxMembers() - region.getRegion().getMembers().size()) : "Unlimited")).color(
          (region.getMaxMembers() >= 0 && region.getMaxMembers() - region.getRegion().getMembers().size() > 0) ? (TextColor)NamedTextColor.GREEN : (region.getMaxMembers() >= 0 ? (TextColor)NamedTextColor.GRAY : (TextColor)NamedTextColor.RED)));
    double cost = this.plugin.getMemberAddCost();
    if (cost > 0.0D) {
      Economy economy = this.plugin.getEconomy();
      if (economy != null) {
        double balance = economy.getBalance((OfflinePlayer)player);
        infoLore.add(Component.text("Cost: $" + String.format("%.2f", new Object[] { Double.valueOf(cost) })).color(
              (balance >= cost) ? (TextColor)NamedTextColor.GREEN : (TextColor)NamedTextColor.RED));
      } 
    } 
    infoLore.add(Component.empty());
    infoLore.add(Component.text("Click a player to add them").color((TextColor)NamedTextColor.WHITE));
    infoMeta.lore(infoLore);
    infoItem.setItemMeta(infoMeta);
    inv.setItem(4, infoItem);
    int startIndex = page * 36;
    int endIndex = Math.min(startIndex + 36, onlinePlayers.size());
    if (onlinePlayers.isEmpty()) {
      ItemStack noPlayers = new ItemStack(Material.BARRIER);
      ItemMeta noMeta = noPlayers.getItemMeta();
      noMeta.displayName(Component.text("No Players Available").color((TextColor)NamedTextColor.RED));
      noMeta.lore(Arrays.asList(new TextComponent[] { (TextComponent)Component.text("No online players can be added").color((TextColor)NamedTextColor.GRAY), 
              (TextComponent)Component.text("Players must be online and not").color((TextColor)NamedTextColor.GRAY), 
              (TextComponent)Component.text("already members of this cell").color((TextColor)NamedTextColor.GRAY) }));
      noPlayers.setItemMeta(noMeta);
      inv.setItem(22, noPlayers);
    } else {
      for (int i = startIndex; i < endIndex; i++) {
        Player targetPlayer = onlinePlayers.get(i);
        ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta)playerHead.getItemMeta();
        skullMeta.setOwningPlayer((OfflinePlayer)targetPlayer);
        skullMeta.displayName(Component.text(targetPlayer.getName()).color((TextColor)NamedTextColor.GREEN));
        List<Component> playerLore = new ArrayList<>();
        playerLore.add(Component.text("Status: Online").color((TextColor)NamedTextColor.GREEN));
        playerLore.add(Component.empty());
        playerLore.add(Component.text("Click to add as member").color((TextColor)NamedTextColor.YELLOW));
        skullMeta.lore(playerLore);
        skullMeta.getPersistentDataContainer().set(this.regionIdKey, PersistentDataType.STRING, regionId);
        skullMeta.getPersistentDataContainer().set(this.actionKey, PersistentDataType.STRING, "ADD_MEMBER_CONFIRM");
        skullMeta.getPersistentDataContainer().set(this.playerUuidKey, PersistentDataType.STRING, targetPlayer.getUniqueId().toString());
        playerHead.setItemMeta((ItemMeta)skullMeta);
        inv.setItem(9 + i - startIndex, playerHead);
      } 
    } 
    addNavigationControls(inv, page, totalPages, regionId, "ADD_MEMBER");
    ItemStack backButton = new ItemStack(Material.ARROW);
    ItemMeta backMeta = backButton.getItemMeta();
    backMeta.displayName(Component.text("Back to Member Management").color((TextColor)NamedTextColor.RED));
    backMeta.getPersistentDataContainer().set(this.regionIdKey, PersistentDataType.STRING, regionId);
    backMeta.getPersistentDataContainer().set(this.actionKey, PersistentDataType.STRING, "BACK_MEMBER_MANAGEMENT");
    backButton.setItemMeta(backMeta);
    inv.setItem(49, backButton);
    player.openInventory(inv);
  }
  
  public void openRemoveMemberGUI(Player player, Region region, int page) {
    String regionId = region.getRegion().getId();
    List<UUID> memberUuids = new ArrayList<>(region.getRegion().getMembers());
    int totalPages = Math.max(1, (memberUuids.size() + 35) / 36);
    page = Math.max(0, Math.min(page, totalPages - 1));
    CellGuiHolder holder = new CellGuiHolder("REMOVE_MEMBER");
    Inventory inv = Bukkit.createInventory((InventoryHolder)holder, 54, 
        Component.text("Remove Member from " + regionId).color(TextColor.color(10040012)));
    holder.setInventory(inv);
    ItemStack infoItem = new ItemStack(Material.REDSTONE);
    ItemMeta infoMeta = infoItem.getItemMeta();
    infoMeta.displayName(Component.text("Remove Member").color((TextColor)NamedTextColor.RED));
    List<Component> infoLore = new ArrayList<>();
    infoLore.add(Component.text("Region: " + regionId).color((TextColor)NamedTextColor.YELLOW));
    infoLore.add(Component.text("Current members: " + memberUuids.size()).color((TextColor)NamedTextColor.GRAY));
    double cost = this.plugin.getMemberRemoveCost();
    if (cost > 0.0D) {
      Economy economy = this.plugin.getEconomy();
      if (economy != null) {
        double balance = economy.getBalance((OfflinePlayer)player);
        infoLore.add(Component.text("Cost: $" + String.format("%.2f", new Object[] { Double.valueOf(cost) })).color(
              (balance >= cost) ? (TextColor)NamedTextColor.GREEN : (TextColor)NamedTextColor.RED));
      } 
    } 
    infoLore.add(Component.empty());
    infoLore.add(Component.text("Click a member to remove them").color((TextColor)NamedTextColor.WHITE));
    infoMeta.lore(infoLore);
    infoItem.setItemMeta(infoMeta);
    inv.setItem(4, infoItem);
    int startIndex = page * 36;
    int endIndex = Math.min(startIndex + 36, memberUuids.size());
    if (memberUuids.isEmpty()) {
      ItemStack noMembers = new ItemStack(Material.BARRIER);
      ItemMeta noMeta = noMembers.getItemMeta();
      noMeta.displayName(Component.text("No Members").color((TextColor)NamedTextColor.YELLOW));
      noMeta.lore(Arrays.asList(new TextComponent[] { (TextComponent)Component.text("This cell has no members").color((TextColor)NamedTextColor.GRAY), 
              (TextComponent)Component.text("to remove").color((TextColor)NamedTextColor.GRAY) }));
      noMembers.setItemMeta(noMeta);
      inv.setItem(22, noMembers);
    } else {
      for (int i = startIndex; i < endIndex; i++) {
        UUID memberUuid = memberUuids.get(i);
        OfflinePlayer member = Bukkit.getOfflinePlayer(memberUuid);
        String memberName = (member.getName() != null) ? member.getName() : "Unknown";
        ItemStack memberHead = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta)memberHead.getItemMeta();
        skullMeta.setOwningPlayer(member);
        skullMeta.displayName(Component.text(memberName).color((TextColor)NamedTextColor.RED));
        List<Component> memberLore = new ArrayList<>();
        memberLore.add(Component.text("Status: " + (member.isOnline() ? "Online" : "Offline"))
            .color(member.isOnline() ? (TextColor)NamedTextColor.GREEN : (TextColor)NamedTextColor.GRAY));
        if (member.getLastPlayed() > 0L)
          memberLore.add(Component.text("Last seen: " + formatTime(member.getLastPlayed()))
              .color((TextColor)NamedTextColor.GRAY)); 
        memberLore.add(Component.empty());
        memberLore.add(Component.text("Click to remove from cell").color((TextColor)NamedTextColor.YELLOW));
        skullMeta.lore(memberLore);
        skullMeta.getPersistentDataContainer().set(this.regionIdKey, PersistentDataType.STRING, regionId);
        skullMeta.getPersistentDataContainer().set(this.actionKey, PersistentDataType.STRING, "REMOVE_MEMBER_CONFIRM");
        skullMeta.getPersistentDataContainer().set(this.playerUuidKey, PersistentDataType.STRING, memberUuid.toString());
        memberHead.setItemMeta((ItemMeta)skullMeta);
        inv.setItem(9 + i - startIndex, memberHead);
      } 
    } 
    addNavigationControls(inv, page, totalPages, regionId, "REMOVE_MEMBER");
    ItemStack backButton = new ItemStack(Material.ARROW);
    ItemMeta backMeta = backButton.getItemMeta();
    backMeta.displayName(Component.text("Back to Member Management").color((TextColor)NamedTextColor.RED));
    backMeta.getPersistentDataContainer().set(this.regionIdKey, PersistentDataType.STRING, regionId);
    backMeta.getPersistentDataContainer().set(this.actionKey, PersistentDataType.STRING, "BACK_MEMBER_MANAGEMENT");
    backButton.setItemMeta(backMeta);
    inv.setItem(49, backButton);
    player.openInventory(inv);
  }
  
  private void addNavigationControls(Inventory inv, int currentPage, int totalPages, String regionId, String action) {
    if (totalPages <= 1)
      return; 
    if (currentPage > 0) {
      ItemStack prevPage = new ItemStack(Material.ARROW);
      ItemMeta prevMeta = prevPage.getItemMeta();
      prevMeta.displayName(Component.text("Previous Page").color((TextColor)NamedTextColor.YELLOW));
      prevMeta.getPersistentDataContainer().set(this.regionIdKey, PersistentDataType.STRING, regionId);
      prevMeta.getPersistentDataContainer().set(this.actionKey, PersistentDataType.STRING, action + "_PAGE");
      prevMeta.getPersistentDataContainer().set(this.pageKey, PersistentDataType.INTEGER, Integer.valueOf(currentPage - 1));
      prevPage.setItemMeta(prevMeta);
      inv.setItem(45, prevPage);
    } 
    ItemStack pageIndicator = new ItemStack(Material.PAPER);
    ItemMeta pageMeta = pageIndicator.getItemMeta();
    pageMeta.displayName(Component.text("Page " + currentPage + 1 + "/" + totalPages).color((TextColor)NamedTextColor.WHITE));
    pageIndicator.setItemMeta(pageMeta);
    inv.setItem(49, pageIndicator);
    if (currentPage < totalPages - 1) {
      ItemStack nextPage = new ItemStack(Material.ARROW);
      ItemMeta nextMeta = nextPage.getItemMeta();
      nextMeta.displayName(Component.text("Next Page").color((TextColor)NamedTextColor.YELLOW));
      nextMeta.getPersistentDataContainer().set(this.regionIdKey, PersistentDataType.STRING, regionId);
      nextMeta.getPersistentDataContainer().set(this.actionKey, PersistentDataType.STRING, action + "_PAGE");
      nextMeta.getPersistentDataContainer().set(this.pageKey, PersistentDataType.INTEGER, Integer.valueOf(currentPage + 1));
      nextPage.setItemMeta(nextMeta);
      inv.setItem(53, nextPage);
    } 
  }
  
  private List<Player> getEligiblePlayersForAddition(Region region, Player requester) {
    return (List<Player>)Bukkit.getOnlinePlayers().stream()
      .filter(p -> !p.equals(requester))
      .filter(p -> !region.getRegion().hasMember(p.getUniqueId()))
      .filter(p -> !region.getRegion().hasOwner(p.getUniqueId()))
      .collect(Collectors.toList());
  }
  
  private String formatTime(long timestamp) {
    long now = System.currentTimeMillis();
    long diff = now - timestamp;
    if (diff < 60000L)
      return "Just now"; 
    if (diff < 3600000L)
      return "" + diff / 60000L + " minutes ago"; 
    if (diff < 86400000L)
      return "" + diff / 3600000L + " hours ago"; 
    return "" + diff / 86400000L + " days ago";
  }
  
  @EventHandler(priority = EventPriority.HIGH)
  public void onInventoryClick(InventoryClickEvent event) {
    Player player;
    CellGuiHolder holder;
    String playerUuidStr, memberUuidStr;
    int page;
    Region region;
    HumanEntity humanEntity = event.getWhoClicked();
    if (humanEntity instanceof Player) {
      player = (Player)humanEntity;
    } else {
      return;
    } 
    InventoryHolder inventoryHolder = event.getInventory().getHolder();
    if (inventoryHolder instanceof CellGuiHolder) {
      holder = (CellGuiHolder)inventoryHolder;
    } else {
      return;
    } 
    String menuType = holder.getPageKey();
    if (!menuType.equals("ADD_MEMBER") && !menuType.equals("REMOVE_MEMBER"))
      return; 
    event.setCancelled(true);
    ItemStack clickedItem = event.getCurrentItem();
    if (clickedItem == null || clickedItem.getType().isAir())
      return; 
    ItemMeta meta = clickedItem.getItemMeta();
    if (meta == null)
      return; 
    String action = (String)meta.getPersistentDataContainer().getOrDefault(this.actionKey, PersistentDataType.STRING, "");
    String regionId = (String)meta.getPersistentDataContainer().getOrDefault(this.regionIdKey, PersistentDataType.STRING, "");
    switch (action) {
      case "ADD_MEMBER_CONFIRM":
        playerUuidStr = (String)meta.getPersistentDataContainer().getOrDefault(this.playerUuidKey, PersistentDataType.STRING, "");
        if (!playerUuidStr.isEmpty() && !regionId.isEmpty()) {
          player.closeInventory();
          handleAddMember(player, regionId, UUID.fromString(playerUuidStr));
        } 
        break;
      case "REMOVE_MEMBER_CONFIRM":
        memberUuidStr = (String)meta.getPersistentDataContainer().getOrDefault(this.playerUuidKey, PersistentDataType.STRING, "");
        if (!memberUuidStr.isEmpty() && !regionId.isEmpty()) {
          player.closeInventory();
          handleRemoveMember(player, regionId, UUID.fromString(memberUuidStr));
        } 
        break;
      case "ADD_MEMBER_PAGE":
      case "REMOVE_MEMBER_PAGE":
        page = ((Integer)meta.getPersistentDataContainer().getOrDefault(this.pageKey, PersistentDataType.INTEGER, Integer.valueOf(0))).intValue();
        region = this.plugin.findRegionById(regionId);
        if (region != null) {
          if (action.startsWith("ADD_MEMBER")) {
            openAddMemberGUI(player, region, page);
            break;
          } 
          openRemoveMemberGUI(player, region, page);
        } 
        break;
      case "BACK_MEMBER_MANAGEMENT":
        if (!regionId.isEmpty()) {
          Region backRegion = this.plugin.findRegionById(regionId);
          if (backRegion != null) {
            player.closeInventory();
            this.plugin.getGuiManager().openCellManagementGUI(player, backRegion);
          } 
        } 
        break;
    } 
  }
  
  private void handleAddMember(Player player, String regionId, UUID targetUuid) {
    Region region = this.plugin.findRegionById(regionId);
    if (region == null) {
      player.sendMessage(MessageUtils.error("Region not found!"));
      return;
    } 
    OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
    String targetName = (target.getName() != null) ? target.getName() : "Unknown";
    if (region.getRegion().hasMember(targetUuid)) {
      player.sendMessage(MessageUtils.error(targetName + " is already a member!"));
      this.plugin.getGuiManager().openCellManagementGUI(player, region);
      return;
    } 
    int maxMembers = region.getMaxMembers();
    if (maxMembers >= 0 && region.getRegion().getMembers().size() >= maxMembers) {
      player.sendMessage(MessageUtils.error("Member limit of " + maxMembers + " reached!"));
      this.plugin.getGuiManager().openCellManagementGUI(player, region);
      return;
    } 
    double cost = this.plugin.getMemberAddCost();
    if (cost > 0.0D) {
      Economy economy = this.plugin.getEconomy();
      if (economy == null || !economy.has((OfflinePlayer)player, cost)) {
        player.sendMessage(MessageUtils.error("Insufficient funds. Cost: $" + String.format("%.2f", new Object[] { Double.valueOf(cost) })));
        this.plugin.getGuiManager().openCellManagementGUI(player, region);
        return;
      } 
      if (!economy.withdrawPlayer((OfflinePlayer)player, cost).transactionSuccess()) {
        player.sendMessage(MessageUtils.error("Payment failed!"));
        this.plugin.getGuiManager().openCellManagementGUI(player, region);
        return;
      } 
      player.sendMessage(MessageUtils.warning("$" + String.format("%.2f", new Object[] { Double.valueOf(cost) }) + " withdrawn."));
    } 
    region.getRegion().addMember(targetUuid);
    try {
      region.queueSave();
    } catch (Exception e) {
      this.plugin.getLogger().warning("Failed to save region after adding member: " + e.getMessage());
    } 
    player.sendMessage(MessageUtils.success("Added " + targetName + " to cell " + regionId + "!"));
    if (target.isOnline()) {
      Player targetPlayer = (Player)target;
      targetPlayer.sendMessage(MessageUtils.success("You have been added to cell " + regionId + " by " + player.getName()));
    } 
    this.plugin.getGuiManager().openCellManagementGUI(player, region);
  }
  
  private void handleRemoveMember(Player player, String regionId, UUID memberUuid) {
    Region region = this.plugin.findRegionById(regionId);
    if (region == null) {
      player.sendMessage(MessageUtils.error("Region not found!"));
      return;
    } 
    OfflinePlayer member = Bukkit.getOfflinePlayer(memberUuid);
    String memberName = (member.getName() != null) ? member.getName() : "Unknown";
    if (!region.getRegion().hasMember(memberUuid)) {
      player.sendMessage(MessageUtils.error(memberName + " is not a member!"));
      this.plugin.getGuiManager().openCellManagementGUI(player, region);
      return;
    } 
    double cost = this.plugin.getMemberRemoveCost();
    if (cost > 0.0D) {
      Economy economy = this.plugin.getEconomy();
      if (economy == null || !economy.has((OfflinePlayer)player, cost)) {
        player.sendMessage(MessageUtils.error("Insufficient funds. Cost: $" + String.format("%.2f", new Object[] { Double.valueOf(cost) })));
        this.plugin.getGuiManager().openCellManagementGUI(player, region);
        return;
      } 
      if (!economy.withdrawPlayer((OfflinePlayer)player, cost).transactionSuccess()) {
        player.sendMessage(MessageUtils.error("Payment failed!"));
        this.plugin.getGuiManager().openCellManagementGUI(player, region);
        return;
      } 
      player.sendMessage(MessageUtils.warning("$" + String.format("%.2f", new Object[] { Double.valueOf(cost) }) + " withdrawn."));
    } 
    region.getRegion().removeMember(memberUuid);
    try {
      region.queueSave();
    } catch (Exception e) {
      this.plugin.getLogger().warning("Failed to save region after removing member: " + e.getMessage());
    } 
    player.sendMessage(MessageUtils.success("Removed " + memberName + " from cell " + regionId + "!"));
    if (member.isOnline()) {
      Player memberPlayer = (Player)member;
      memberPlayer.sendMessage(MessageUtils.warning("You have been removed from cell " + regionId + " by " + player.getName()));
    } 
    this.plugin.getGuiManager().openCellManagementGUI(player, region);
  }
}


/* Location:              C:\Users\purpt\OneDrive\Desktop\EdenCells-1.0-SNAPSHOT (1).jar!\dev\lsdmc\gui\MemberSelectionGUI.class
 * Java compiler version: 21 (65.0)
 * JD-Core Version:       1.1.3
 */