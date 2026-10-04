package com.example.kaleidoscope.winemerchant.trade;

import com.example.kaleidoscope.winemerchant.KaleidoscopeWineMerchant;
import com.example.kaleidoscope.winemerchant.config.WineGroups;
import com.example.kaleidoscope.winemerchant.config.WineMerchantConfig;
import com.example.kaleidoscope.winemerchant.registry.ModProfessions;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.BasicItemListing;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 酒商的交易表构建器。
 *
 * <h2>原版 / NeoForge 的交易表机制(务必先读)</h2>
 * <pre>
 *   世界加载时(NeoForge VillagerTradingManager.postVillagerEvents):
 *     ① 把 VANILLA_TRADES[profession] 复制成可变列表(5 个等级)
 *     ② 触发 VillagerTradesEvent —— 各模组往列表里 add 自己的条目
 *     ③ VillagerTrades.TRADES.put(profession, 新数组)   ← 交易由此固化
 *
 *   之后:村民认领职业 / 失业再就业 → Villager.updateTrades()
 *         它只从 VillagerTrades.TRADES 里**重新抽样**,不会重新触发事件。
 * </pre>
 * 结论:改了配置想让已有村民换交易,<b>光让村民失业再就业没用</b> ——
 * 必须把新条目重新写进 {@code VillagerTrades.TRADES}。
 * 这也是 {@code /wmc refresh} 做的事(见 {@link #rebuildIntoVanillaRegistry()})。
 *
 * <p>{@code VillagerTrades.TRADES} 是
 * {@code public static final Map<...>} —— 引用不可变但 Map 本身可变,
 * NeoForge 自己就是靠 {@code TRADES.put(...)} 写入的,所以这不是"反射黑科技"。
 *
 * <h2>构建流程</h2>
 * <ul>
 *   <li>{@link #rebuildPools()} —— 按当前配置<span>重建本模组自己的条目缓存</span>,
 *       不碰原版注册表。世界加载与 {@code /wmc refresh} 都会调用。</li>
 *   <li>{@link #onVillagerTrades} —— 世界加载时,把缓存 add 进事件列表;
 *       NeoForge 随后会把它写进 {@code TRADES}。</li>
 *   <li>{@link #rebuildIntoVanillaRegistry()} —— {@code /wmc refresh} 用:
 *       重建缓存后,绕过事件,直接把结果写进 {@code TRADES}。</li>
 * </ul>
 */
public final class WineMerchantTrades {

    private static final int BUY_MAX_USES = 16;
    private static final int SELL_MAX_USES = 12;
    /** 出售鸡尾酒价格的随机源(固定种子 → 同一次加载内价格序列可复现)。 */
    private static final RandomSource RANDOM = RandomSource.create(20261002L);

    /** 全部村民等级 */
    private static final int[] ALL_LEVELS = {1, 2, 3, 4, 5};
    /** 鸡尾酒出售挂靠的等级 */
    private static final int[] COCKTAIL_LEVELS = {4, 5};

    // ──────────────────── 条目缓存(可重建) ────────────────────

    /** 等级 → 本模组交易条目。不包含原版条目。 */
    private static final Map<Integer, List<VillagerTrades.ItemListing>> CACHE = new HashMap<>();

    private static int cacheCount;

    public static synchronized int cacheCount() {
        return cacheCount;
    }

    private WineMerchantTrades() {
    }

    private static void addTo(int level, VillagerTrades.ItemListing listing) {
        CACHE.computeIfAbsent(level, k -> new ArrayList<>()).add(listing);
    }

    /**
     * 按当前配置重建本模组的条目缓存。不修改任何原版数据。
     *
     * @return 条目总数;抛异常时返回 -1
     */
    public static synchronized int rebuildPools() {
        try {
            CACHE.clear();
            KIND_COUNTS.clear();
            WineMerchantConfig cfg = WineMerchantConfig.CONFIG;
            Set<String> blacklist = new LinkedHashSet<>(cfg.blacklist());
            boolean tierEnabled = cfg.isTierEnabled();

            int bought = buildItemBuys(cfg, blacklist, tierEnabled);
            int sold = buildCocktailSales(cfg, blacklist);

            cacheCount = bought + sold;
            return cacheCount;
        } catch (Throwable t) {
            KaleidoscopeWineMerchant.LOGGER.error("[WineMerchant] 重建交易条目缓存失败", t);
            return -1;
        }
    }

    // ──────────────────── 世界加载:走正常事件 ────────────────────

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != ModProfessions.WINE_MERCHANT.get()) {
            return;
        }
        // ⚠ 本事件触发于 ReloadableServerResources.updateRegistryTags 阶段,
        //   比 ModConfig.Type.SERVER 的加载更早;此时 ModConfigSpec 还没就绪,
        //   所以 WineMerchantConfig 会直接解析 TOML 文件(见 ConfigMirror),
        //   拿到的才是玩家的真实设置。
        int rebuilt = rebuildPools();
        if (rebuilt < 0) {
            return;
        }
        for (Map.Entry<Integer, List<VillagerTrades.ItemListing>> e : CACHE.entrySet()) {
            List<VillagerTrades.ItemListing> dest = event.getTrades().get(e.getKey());
            if (dest != null) {
                dest.addAll(e.getValue());
            }
        }
        KaleidoscopeWineMerchant.LOGGER.info(
                "[WineMerchant] 交易已生成:本模组 {} 条,分布于 {} 个等级", rebuilt, CACHE.size());
    }

    // ──────────────────── /wmc refresh:直接写回原版注册表 ────────────────────

    /** NeoForge 存放"原版条目"的字段 —— 重建 TRADES 时需要它作为基底。 */
    private static final String VANILLA_TRADES_FIELD =
            "net.neoforged.neoforge.common.VillagerTradingManager";
    private static Field vanillaTradesField;

    /**
     * 按当前配置重建交易表,并**直接写回** {@code VillagerTrades.TRADES}。
     *
     * <p>这是 {@code /wmc refresh} 的核心。因为 {@code VillagerTrades.TRADES} 只在世界
     * 加载时被 NeoForge 写一次,之后村民重新抽取交易读的都是它 —— 不写回它,
     * 配置改动永远不会生效。
     *
     * <p>做法与 NeoForge 的 {@code postVillagerEvents} 一致:
     * <pre>
     *   原版条目(反射读 NeoForge 的 VANILLA_TRADES)
     *     + 本模组按新配置重建的条目
     *     → VillagerTrades.TRADES.put(酒商职业, 结果)
     * </pre>
     *
     * @return 本模组写入的条目数;失败返回 -1
     */
    public static synchronized int rebuildIntoVanillaRegistry() {
        // ★ 先重读配置文件。
        //   ConfigMirror 是"启动时读一次并缓存"的内存快照,而 /wmc price 之类的指令
        //   只更新了 ModConfigSpec 与磁盘上的 TOML,没有刷新这个镜像 ——
        //   不重读的话,重建出来的交易表仍然携带**旧价格**。
        //   (症状:指令提示"价格已改",交易列表也确实重建了,但价格没变。)
        WineMerchantConfig.refreshMirror();

        int mine = rebuildPools();
        if (mine < 0) {
            return -1;
        }

        VillagerProfession prof = ModProfessions.WINE_MERCHANT.get();
        Int2ObjectMap<List<VillagerTrades.ItemListing>> merged = new Int2ObjectOpenHashMap<>();
        for (int level = 1; level <= 5; level++) {
            merged.put(level, NonNullList.create());
        }

        // ① 原版基底(本模组职业在 1.21.1 没有原版交易,但保留这段以与 NeoForge 行为一致)
        int vanilla = appendVanillaBase(prof, merged);
        if (vanilla < 0) {
            KaleidoscopeWineMerchant.LOGGER.warn(
                    "[WineMerchant] 未能读取原版交易基底,本次 refresh 已跳过"
                            + "(不改动 VillagerTrades.TRADES,配置改动需重启生效)");
            return -1;
        }

        // ② 本模组的新条目
        for (Map.Entry<Integer, List<VillagerTrades.ItemListing>> e : CACHE.entrySet()) {
            List<VillagerTrades.ItemListing> dest = merged.get((int) e.getKey());
            if (dest != null) {
                dest.addAll(e.getValue());
            }
        }

        // ③ 写回原版注册表
        Int2ObjectMap<VillagerTrades.ItemListing[]> asArrays = new Int2ObjectOpenHashMap<>();
        merged.int2ObjectEntrySet().forEach(e ->
                asArrays.put(e.getIntKey(), e.getValue().toArray(new VillagerTrades.ItemListing[0])));
        VillagerTrades.TRADES.put(prof, asArrays);

        // ④ 打印明细 —— 让"某个物品到底在不在池子里"可以直接从日志确认。
        //    每个村民只展示 10 条交易,光看交易界面无法判断某物品是"被移除了"
        //    还是"本来就没抽到",所以这里有必要的可见性。
        KaleidoscopeWineMerchant.LOGGER.info(
                "[WineMerchant] 已写回交易表:原版基底 {} 条 + 本模组 {} 条", vanilla, mine);
        KaleidoscopeWineMerchant.LOGGER.info("[WineMerchant] 本次池子构成:{}", describeCounts());
        return mine;
    }

    /** 各类别在本轮构建中的条目数(由构建过程直接统计,不做反向推断)。 */
    private static final Map<Kind, Integer> KIND_COUNTS = new java.util.EnumMap<>(Kind.class);

    private static void countKind(Kind kind, int n) {
        KIND_COUNTS.merge(kind, n, Integer::sum);
    }

    /** 供日志展示:本轮各方向各有多少条目。 */
    private static String describeCounts() {
        return String.format(
                "酒类收购 %d 条 / 饮料收购 %d 条 / 鸡尾酒收购 %d 条 / 鸡尾酒出售 %d 条",
                KIND_COUNTS.getOrDefault(Kind.WINE, 0),
                KIND_COUNTS.getOrDefault(Kind.DRINK, 0),
                KIND_COUNTS.getOrDefault(Kind.COCKTAIL, 0),
                KIND_COUNTS.getOrDefault(Kind.SELL, 0));
    }

    /**
     * 把该职业的原版交易条目加进 {@code merged}。
     *
     * <p>NeoForge 的 {@code VANILLA_TRADES} 是包私有字段,这里用反射读取。
     * 读不到时返回 -1,调用方会放弃本次重建(而不是写一个丢掉原版交易的表)。
     */
    @SuppressWarnings("unchecked")
    private static int appendVanillaBase(VillagerProfession prof,
                                         Int2ObjectMap<List<VillagerTrades.ItemListing>> merged) {
        try {
            if (vanillaTradesField == null) {
                Class<?> cls = Class.forName(VANILLA_TRADES_FIELD);
                Field f = cls.getDeclaredField("VANILLA_TRADES");
                f.setAccessible(true);
                vanillaTradesField = f;
            }
            Map<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> vanilla =
                    (Map<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>>)
                            vanillaTradesField.get(null);
            if (vanilla == null) {
                return -1;
            }
            Int2ObjectMap<VillagerTrades.ItemListing[]> forProf = vanilla.get(prof);
            if (forProf == null) {
                return 0; // 该职业没有原版交易(本模组的正常情况)
            }
            int n = 0;
            for (Int2ObjectMap.Entry<VillagerTrades.ItemListing[]> e : forProf.int2ObjectEntrySet()) {
                List<VillagerTrades.ItemListing> dest = merged.get(e.getIntKey());
                if (dest != null) {
                    for (VillagerTrades.ItemListing l : e.getValue()) {
                        dest.add(l);
                        n++;
                    }
                }
            }
            return n;
        } catch (Throwable t) {
            KaleidoscopeWineMerchant.LOGGER.warn(
                    "[WineMerchant] 反射读取 VANILLA_TRADES 失败:{}", t.toString());
            return -1;
        }
    }

    // ──────────────────── 村民收购(酒类 + 饮料 + 鸡尾酒)────────────────────

    private static int buildItemBuys(WineMerchantConfig cfg,
                                     Set<String> blacklist, boolean tierEnabled) {
        int added = 0;

        Set<String> t1 = new LinkedHashSet<>(cfg.tier(1));
        Set<String> t2 = new LinkedHashSet<>(cfg.tier(2));
        Set<String> t3 = new LinkedHashSet<>(cfg.tier(3));

        for (WineGroups.Item item : WineGroups.WINES) {
            if (blacklist.contains(item.id()) || !cfg.wineEnabled(item.id())) {
                continue;
            }
            int[] levels;
            int baseline;
            if (tierEnabled) {
                if (t1.contains(item.id())) {
                    levels = new int[]{1, 2};
                    baseline = 1;
                } else if (t2.contains(item.id())) {
                    levels = new int[]{3, 4};
                    baseline = 3;
                } else if (t3.contains(item.id())) {
                    levels = new int[]{5};
                    baseline = 5;
                } else {
                    continue; // 开启分档后,不在任何档位的酒不收购
                }
            } else {
                levels = ALL_LEVELS;
                baseline = 1;
            }
            added += addBuy(item.id(), cfg, levels, baseline, Kind.WINE);
        }

        for (WineGroups.Item item : WineGroups.DRINKS) {
            if (blacklist.contains(item.id()) || !cfg.drinkEnabled(item.id())) {
                continue;
            }
            added += addBuy(item.id(), cfg, ALL_LEVELS, 1, Kind.DRINK);
        }

        for (WineGroups.Item item : WineGroups.COCKTAILS) {
            if (blacklist.contains(item.id()) || !cfg.cocktailBuy(item.id())) {
                continue;
            }
            added += addBuy(item.id(), cfg, ALL_LEVELS, 1, Kind.COCKTAIL);
        }
        return added;
    }

    /** 物品所属大类(决定用哪张价格表) */
    private enum Kind { WINE, COCKTAIL, DRINK, SELL }

    /** 为一种物品在指定村民等级上生成「收购」交易,写进缓存。 */
    private static int addBuy(String id, WineMerchantConfig cfg,
                              int[] levels, int baselineLevel, Kind kind) {
        ItemStack stack = resolve(id);
        if (stack.isEmpty()) {
            KaleidoscopeWineMerchant.LOGGER.warn("[WineMerchant] 物品不存在,已跳过:{}", id);
            return 0;
        }
        WineGroups.Item def = WineGroups.byId(id);
        boolean flat = def != null && !def.hasQuality();

        // ── 醋的批量模式:凑够 N 瓶换 1 个绿宝石,不看品质 ──
        if (def != null && "kaleidoscope_tavern:vinegar".equals(id) && cfg.vinegarBulk()) {
            BasicItemListing listing = new BasicItemListing(
                    stack.copyWithCount(cfg.vinegarBulkBottles()),
                    new ItemStack(Items.EMERALD, 1), BUY_MAX_USES, 2, 0.0F);
            int n = 0;
            for (int level : levels) {
                addTo(level, listing);
                n++;
            }
            return n;
        }

        // 价目表随产出携带 —— 服务端与客户端读到的必然是同一份数据,不会算出不同价格。
        //
        // 注意:标记必须打在 <b>存储的 result</b> 上,因为 MerchantOffer.assemble()
        // 只是返回存储 result 的一个副本 —— Mixin 靠读这个副本来判断"这是本模组的交易"。
        var perQuality = new ArrayList<Integer>(BrewQuality.MAX_LEVEL);
        for (int q = 1; q <= BrewQuality.MAX_LEVEL; q++) {
            int price = switch (kind) {
                case WINE -> cfg.priceWine(id, q);
                case COCKTAIL -> cfg.priceCocktail(id, q);
                case DRINK -> cfg.priceDrink(id, q);
                // SELL 只是"村民出售鸡尾酒"的计数标记,不走收购价分支
                case SELL -> throw new IllegalStateException("SELL 不应进入收购价分支");
            };
            perQuality.add(Math.max(0, price));
        }
        ItemStack result = ModDataComponents.mark(new ItemStack(Items.EMERALD, 1),
                new ModDataComponents.QualityPricedWine(id, perQuality, baselineLevel, flat));

        int n = 0;
        for (int level : levels) {
            // result 数量保持 1:交易列表显示"1 个绿宝石"作为最低保证,
            // 实际数量由 Mixin 按放入物品的品质算出(只会 ≥ 1)。
            addTo(level, new BasicItemListing(stack.copyWithCount(1), result.copy(),
                    BUY_MAX_USES, 2, 0.0F));
            n++;
        }
        countKind(kind, n);
        return n;
    }

    // ──────────────────── 村民出售鸡尾酒 ────────────────────

    /** 玩家花绿宝石买鸡尾酒(默认方向,逐个物品开关)。 */
    private static int buildCocktailSales(WineMerchantConfig cfg, Set<String> blacklist) {
        int min = cfg.cocktailPriceMin();
        int max = Math.max(min, cfg.cocktailPriceMax());

        int added = 0;
        KIND_COUNTS.remove(Kind.SELL);
        for (WineGroups.Item item : WineGroups.COCKTAILS) {
            if (blacklist.contains(item.id()) || !cfg.cocktailSell(item.id())) {
                continue;
            }
            ItemStack stack = resolve(item.id());
            if (stack.isEmpty()) {
                continue;
            }
            int price = max > min ? min + RANDOM.nextInt(max - min + 1) : min;
            // priceMultiplier = 0:冻结需求/声望浮动,保证价格落在配置区间内
            for (int level : COCKTAIL_LEVELS) {
                addTo(level, new BasicItemListing(
                        new ItemStack(Items.EMERALD, Math.max(1, price)),
                        stack.copy(), SELL_MAX_USES, 4, 0.0F));
                added++;
            }
        }
        countKind(Kind.SELL, added);
        return added;
    }

    // ──────────────────── 工具方法 ────────────────────

    /** 按注册 ID 解析物品(优先物品,其次方块物品形式)。 */
    private static ItemStack resolve(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return ItemStack.EMPTY;
        }
        if (BuiltInRegistries.ITEM.containsKey(rl)) {
            return new ItemStack(BuiltInRegistries.ITEM.get(rl), 1);
        }
        if (BuiltInRegistries.BLOCK.containsKey(rl)) {
            Item blockItem = BuiltInRegistries.BLOCK.get(rl).asItem();
            if (blockItem != Items.AIR) {
                return new ItemStack(blockItem, 1);
            }
        }
        return ItemStack.EMPTY;
    }

    /** 供外部查看物品清单。 */
    public static List<String> allKnownIds() {
        return WineGroups.allIds();
    }
}
