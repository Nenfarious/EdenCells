/*     */ package dev.lsdmc.gui;
/*     */
/*     */ import java.util.HashMap;
/*     */ import java.util.Map;
/*     */ import java.util.Stack;
/*     */ import java.util.UUID;
/*     */ import org.bukkit.entity.Player;
/*     */
/*     */
/*     */
/*     */
/*     */
/*     */
/*     */ public class NavigationManager
        /*     */ {
    /*  16 */   private final Map<UUID, Stack<NavPage>> navHistory = new HashMap<>(); public static final String PAGE_MAIN = "MAIN"; public static final String PAGE_MY_CELLS = "MY_CELLS"; public static final String PAGE_CELL_MEMBERS = "CELL_MEMBERS"; public static final String PAGE_REGION_MEMBERS = "REGION_MEMBERS"; public static final String PAGE_CELL_INFO = "CELL_INFO";
    /*     */   public static final String PAGE_RENT_CELL = "RENT_CELL";
    /*     */   public static final String PAGE_DOOR_INFO = "DOOR_INFO";
    /*     */   public static final String PAGE_ADMIN = "ADMIN";
    /*     */   public static final String PAGE_RESET = "RESET";
    /*     */   public static final String PAGE_DOOR_ADMIN = "DOOR_ADMIN";
    /*     */
    /*     */   public static class NavPage { private final String type;
        /*     */
        /*     */     public NavPage(String type, String data) {
            /*  26 */       this.type = type;
            /*  27 */       this.data = data;
            /*     */     }
        /*     */     private final String data;
        /*     */     public NavPage(String type) {
            /*  31 */       this(type, "");
            /*     */     }
        /*     */
        /*     */     public String getType() {
            /*  35 */       return this.type;
            /*     */     }
        /*     */
        /*     */     public String getData() {
            /*  39 */       return this.data;
            /*     */     }
        /*     */
        /*     */
        /*     */     public boolean equals(Object obj) {
            /*  44 */       if (!(obj instanceof NavPage)) return false;
            /*  45 */       NavPage other = (NavPage)obj;
            /*  46 */       return (this.type.equals(other.type) && this.data.equals(other.data));
            /*     */     }
        /*     */
        /*     */
        /*     */     public int hashCode() {
            /*  51 */       return this.type.hashCode() * 31 + this.data.hashCode();
            /*     */     } }
    /*     */
    /*     */
    /*     */
    /*     */
    /*     */
    /*     */   public void pushPage(Player player, String pageType, String pageData) {
        /*  59 */     NavPage page = new NavPage(pageType, pageData);
        /*  60 */     pushPage(player, page);
        /*     */   }
    /*     */
    /*     */
    /*     */
    /*     */
    /*     */   public void pushPage(Player player, NavPage page) {
        /*  67 */     UUID playerId = player.getUniqueId();
        /*     */
        /*     */
        /*  70 */     Stack<NavPage> history = this.navHistory.computeIfAbsent(playerId, k -> new Stack<>());
        /*     */
        /*     */
        /*  73 */     if (!history.isEmpty()) {
            /*  74 */       NavPage current = history.peek();
            /*  75 */       if (current.equals(page)) {
                /*     */         return;
                /*     */       }
            /*     */     }
        /*     */
        /*     */
        /*  81 */     history.push(page);
        /*     */
        /*     */
        /*  84 */     while (history.size() > 10) {
            /*  85 */       history.remove(0);
            /*     */     }
        /*     */   }
    /*     */
    /*     */
    /*     */
    /*     */
    /*     */   public void pushPage(Player player, String pageType) {
        /*  93 */     pushPage(player, pageType, "");
        /*     */   }
    /*     */
    /*     */
    /*     */
    /*     */
    /*     */   public NavPage popPage(Player player) {
        /* 100 */     UUID playerId = player.getUniqueId();
        /* 101 */     Stack<NavPage> history = this.navHistory.get(playerId);
        /*     */
        /* 103 */     if (history == null || history.isEmpty()) {
            /* 104 */       return new NavPage("MAIN");
            /*     */     }
        /*     */
        /* 107 */     return history.pop();
        /*     */   }
    /*     */
    /*     */
    /*     */
    /*     */
    /*     */   public NavPage peekPage(Player player) {
        /* 114 */     UUID playerId = player.getUniqueId();
        /* 115 */     Stack<NavPage> history = this.navHistory.get(playerId);
        /*     */
        /* 117 */     if (history == null || history.isEmpty()) {
            /* 118 */       return new NavPage("MAIN");
            /*     */     }
        /*     */
        /* 121 */     return history.peek();
        /*     */   }
    /*     */
    /*     */
    /*     */
    /*     */
    /*     */   public NavPage getPreviousPage(Player player) {
        /* 128 */     UUID playerId = player.getUniqueId();
        /* 129 */     Stack<NavPage> history = this.navHistory.get(playerId);
        /*     */
        /* 131 */     if (history == null || history.size() <= 1) {
            /* 132 */       return new NavPage("MAIN");
            /*     */     }
        /*     */
        /*     */
        /* 136 */     NavPage current = history.pop();
        /* 137 */     NavPage previous = history.peek();
        /*     */
        /*     */
        /* 140 */     history.push(current);
        /*     */
        /* 142 */     return previous;
        /*     */   }
    /*     */
    /*     */
    /*     */
    /*     */
    /*     */   public NavPage navigateBack(Player player) {
        /* 149 */     NavPage currentPage = popPage(player);
        /* 150 */     NavPage previousPage = popPage(player);
        /*     */
        /*     */
        /* 153 */     pushPage(player, previousPage);
        /*     */
        /* 155 */     return previousPage;
        /*     */   }
    /*     */
    /*     */
    /*     */
    /*     */
    /*     */   public void clearHistory(Player player) {
        /* 162 */     this.navHistory.remove(player.getUniqueId());
        /*     */   }
    /*     */
    /*     */
    /*     */
    /*     */
    /*     */   public void clearAllHistory() {
        /* 169 */     this.navHistory.clear();
        /*     */   }
    /*     */ }


/* Location:              C:\Users\purpt\OneDrive\Desktop\EdenCells-1.0-SNAPSHOT.jar!\dev\lsdmc\gui\NavigationManager.class
 * Java compiler version: 21 (65.0)
 * JD-Core Version:       1.1.3
 */