package com.example.kaleidoscope.winemerchant.config;

import com.example.kaleidoscope.winemerchant.KaleidoscopeWineMerchant;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 酒商模组的全部配置(单文件 + 多层节)。
 *
 * <h2>层级结构</h2>
 * <pre>
 *   ├── 常规
 *   │   ├── 定价模式
 *   │   ├── 酒类分档(拓展,默认关闭)
 *   │   └── 等级识货(拓展,默认关闭)
 *   ├── 池子构成
 *   │   ├── 酒类      → 每种酒一个开关
 *   │   ├── 鸡尾酒    → 村民出售(玩家可买) / 玩家出售(村民可收),各一组开关
 *   │   ├── 饮料      → 每种饮料一个开关
 *   │   └── 黑名单排除 → 自由填任意物品 ID
 *   └── 价格设置
 *       ├── 酒类 / 鸡尾酒 / 饮料 → 每种 6 个品质价格
 *       └── 玩家出售(鸡尾酒收购价)
 * </pre>
 *
 * <h2>两个必须遵守的坑(已踩过)</h2>
 * <ol>
 *   <li><b>配置键名必须唯一</b>。在循环里定义开关时,若都用 {@code "enabled"},
 *       后一个会覆盖前一个 —— 表现是"32 种酒只出现 1 个开关"。</li>
 *   <li><b>节的翻译键要显式设置</b>。NeoForge 的
 *       {@code getLevelTranslationKey} 只认 {@code .translation()} 登记过的节;
 *       没登记就回退成 {@code modid.configuration.<path>}。
 *       同理,单个配置值的键是 {@code modid.configuration.<path>},
 *       所以 lang 里必须用 {@code .configuration.} 而不是 {@code .config.}。</li>
 * </ol>
 */
public final class WineMerchantConfig {

    /** 定价模式 */
    public enum TradeMode {
        /** 统一价格:不区分品质,全部按该物品的「普通品质」价格收购 */
        FLAT,
        /** 区分品质:按放入物品的实际品质给不同数量的绿宝石 */
        BY_QUALITY
    }

    public static final ModConfigSpec SPEC;
    public static final WineMerchantConfig CONFIG;

    // ── 常规 ──
    public final ModConfigSpec.EnumValue<TradeMode> tradeMode;
    public final ModConfigSpec.BooleanValue tierEnabled;
    public final ModConfigSpec.ConfigValue<List<? extends String>> tier1Wines;
    public final ModConfigSpec.ConfigValue<List<? extends String>> tier2Wines;
    public final ModConfigSpec.ConfigValue<List<? extends String>> tier3Wines;
    public final ModConfigSpec.BooleanValue ladderEnabled;
    public final ModConfigSpec.DoubleValue ladderStep;

    // ── 池子构成 ──
    /** 酒类 id -> 是否收购 */
    public final Map<String, ModConfigSpec.BooleanValue> winePool = new HashMap<>();
    /** 鸡尾酒 id -> 村民出售(玩家可买) */
    public final Map<String, ModConfigSpec.BooleanValue> cocktailSellPool = new HashMap<>();
    /** 鸡尾酒 id -> 玩家出售(村民可收) */
    public final Map<String, ModConfigSpec.BooleanValue> cocktailBuyPool = new HashMap<>();
    /** 饮料 id -> 是否收购 */
    public final Map<String, ModConfigSpec.BooleanValue> drinkPool = new HashMap<>();
    public final ModConfigSpec.IntValue cocktailPriceMin;
    public final ModConfigSpec.IntValue cocktailPriceMax;
    public final ModConfigSpec.ConfigValue<List<? extends String>> blacklist;
    /** 黑名单的原始 ConfigValue(指令写入时需要它做 set + save) */
    public final ModConfigSpec.ConfigValue<List<? extends String>> blacklistValue;

    // ── 价格设置 ──
    public final Map<String, ModConfigSpec.IntValue[]> winePrices = new HashMap<>();
    public final Map<String, ModConfigSpec.IntValue[]> cocktailPrices = new HashMap<>();
    public final Map<String, ModConfigSpec.IntValue[]> drinkPrices = new HashMap<>();
    /** 无品质物品的单一价格(物品 id -> 价格) */
    public final Map<String, ModConfigSpec.IntValue> flatPrices = new HashMap<>();
    public final ModConfigSpec.IntValue[] playerSellsPrices = new ModConfigSpec.IntValue[6];

    // ── 醋:特殊批量模式 ──
    public final ModConfigSpec.BooleanValue vinegarBulkEnabled;
    public final ModConfigSpec.IntValue vinegarBulkBottles;

    static {
        Pair<WineMerchantConfig, ModConfigSpec> pair =
                new ModConfigSpec.Builder().configure(WineMerchantConfig::new);
        CONFIG = pair.getLeft();
        SPEC = pair.getRight();
    }

    private WineMerchantConfig(ModConfigSpec.Builder b) {
        // ══════════════════ 常规 ══════════════════
        b.comment("════ 酒商模组设置 ════",
                        "本文件包含:池子构成、价格设置。",
                        "价格按『物品 → 6 个品质』组织。")
                .translation("kaleidoscope_wine_merchant.configuration.general")
                .push("general");

        tradeMode = b.comment("定价模式:",
                        "FLAT       = 统一价格,一律按『普通品质』那一档给钱",
                        "BY_QUALITY = 按物品的实际品质给钱(默认)")
                .translation("kaleidoscope_wine_merchant.configuration.general.trade_mode")
                .defineEnum("trade_mode", TradeMode.BY_QUALITY);

        b.comment("酒类分档(拓展选项)。",
                        "开启后按三档分配:1档→1、2级;2档→3、4级;3档→5级。",
                        "未列入任何一档的酒将不再被收购。")
                .translation("kaleidoscope_wine_merchant.configuration.general.tiers")
                .push("tiers");
        tierEnabled = b.comment("是否启用酒类分档。默认关闭。")
                .translation("kaleidoscope_wine_merchant.configuration.general.tiers.enabled")
                .define("enabled", false);
        tier1Wines = b.comment("第 1 档(1、2 级村民)的酒类 ID。")
                .translation("kaleidoscope_wine_merchant.configuration.general.tiers.tier1")
                .defineListAllowEmpty("tier1", WineGroups.defaultTier1(),
                        () -> "kaleidoscope_tavern:wine",
                        o -> o instanceof String s && s.contains(":"));
        tier2Wines = b.comment("第 2 档(3、4 级村民)的酒类 ID。")
                .translation("kaleidoscope_wine_merchant.configuration.general.tiers.tier2")
                .defineListAllowEmpty("tier2", WineGroups.defaultTier2(),
                        () -> "kaleidoscope_tavern:vodka",
                        o -> o instanceof String s && s.contains(":"));
        tier3Wines = b.comment("第 3 档(5 级村民)的酒类 ID。")
                .translation("kaleidoscope_wine_merchant.configuration.general.tiers.tier3")
                .defineListAllowEmpty("tier3", WineGroups.defaultTier3(),
                        () -> "kaleidoscope_world_liquor:maotai",
                        o -> o instanceof String s && s.contains(":"));
        b.pop();

        b.comment("等级识货(拓展选项)。",
                        "品质越高,给的钱越少(最低不低于原价的 10%)。")
                .translation("kaleidoscope_wine_merchant.configuration.general.ladder")
                .push("ladder");
        ladderEnabled = b.comment("是否启用等级识货。默认关闭。")
                .translation("kaleidoscope_wine_merchant.configuration.general.ladder.enabled")
                .define("enabled", false);
        ladderStep = b.comment("每差一档品质的压价比例(0.15 = 每高一档扣 15%)。")
                .translation("kaleidoscope_wine_merchant.configuration.general.ladder.step")
                .defineInRange("step", 0.15D, 0.0D, 0.9D);
        b.pop();

        b.pop(); // general

        // ══════════════════ 池子构成 ══════════════════
        b.comment("════ 池子构成 ════",
                        "控制哪些物品会进入村民的交易列表。逐个开关,不需要填 ID。")
                .translation("kaleidoscope_wine_merchant.configuration.pool")
                .push("pool");

        // 酒类:每个开关独立键名(item.path),放同一节里
        b.comment("酒类:每个开关表示『是否收购该酒』。默认全开。")
                .translation("kaleidoscope_wine_merchant.configuration.pool.wines")
                .push("wines");
        for (WineGroups.Item item : WineGroups.WINES) {
            winePool.put(item.id(), b.comment("收购「" + item.cnName() + "」(" + item.id() + ")")
                    .translation("kaleidoscope_wine_merchant.configuration.pool.wines." + item.path())
                    .define(item.path(), true));
        }
        b.pop();

        // 鸡尾酒
        b.comment("鸡尾酒。两个方向可分别控制,默认只开『村民出售』。")
                .translation("kaleidoscope_wine_merchant.configuration.pool.cocktails")
                .push("cocktails");

        b.comment("村民出售(玩家花绿宝石买鸡尾酒)。默认全部开启。")
                .translation("kaleidoscope_wine_merchant.configuration.pool.cocktails.sell")
                .push("sell");
        cocktailPriceMin = b.comment("售价下限(绿宝石)。")
                .translation("kaleidoscope_wine_merchant.configuration.pool.cocktails.sell.price_min")
                .defineInRange("price_min", 5, 0, 64);
        cocktailPriceMax = b.comment("售价上限(绿宝石)。")
                .translation("kaleidoscope_wine_merchant.configuration.pool.cocktails.sell.price_max")
                .defineInRange("price_max", 10, 0, 64);
        for (WineGroups.Item item : WineGroups.COCKTAILS) {
            cocktailSellPool.put(item.id(), b.comment("出售「" + item.cnName() + "」(" + item.id() + ")")
                    .translation("kaleidoscope_wine_merchant.configuration.pool.cocktails.sell." + item.path())
                    .define(item.path(), true));
        }
        b.pop();

        b.comment("玩家出售(村民用绿宝石收购玩家的鸡尾酒)。默认全部关闭。")
                .translation("kaleidoscope_wine_merchant.configuration.pool.cocktails.buy")
                .push("buy");
        for (WineGroups.Item item : WineGroups.COCKTAILS) {
            cocktailBuyPool.put(item.id(), b.comment("收购「" + item.cnName() + "」(" + item.id() + ")")
                    .translation("kaleidoscope_wine_merchant.configuration.pool.cocktails.buy." + item.path())
                    .define(item.path(), false));
        }
        b.pop();

        b.pop(); // cocktails

        // 饮料
        b.comment("饮料。每个开关表示是否收购该饮料。")
                .translation("kaleidoscope_wine_merchant.configuration.pool.drinks")
                .push("drinks");
        for (WineGroups.Item item : WineGroups.DRINKS) {
            drinkPool.put(item.id(), b.comment("收购「" + item.cnName() + "」(" + item.id() + ")")
                    .translation("kaleidoscope_wine_merchant.configuration.pool.drinks." + item.path())
                    .define(item.path(), false));
        }
        b.pop();

        // 黑名单
        b.comment("黑名单排除:列在这里的物品不会被任何交易使用。",
                        "格式:命名空间:路径,例如 minecraft:apple")
                .translation("kaleidoscope_wine_merchant.configuration.pool.blacklist")
                .push("blacklist");
        blacklist = b.comment("被排除的物品 ID 列表。")
                .translation("kaleidoscope_wine_merchant.configuration.pool.blacklist.excluded")
                .defineListAllowEmpty("excluded", List.of(),
                        () -> "minecraft:apple",
                        o -> o instanceof String s && s.contains(":"));
        blacklistValue = blacklist;
        b.pop();

        b.pop(); // pool

        // ══════════════════ 价格设置 ══════════════════
        b.comment("════ 价格设置 ════",
                        "0 = 不收该品质;N > 0 = 给 N 个绿宝石。",
                        "默认价按『合成原料数』推算,详见各物品条目。",
                        "※ 定价模式选 FLAT 时,这里只读『普通品质』那一档。")
                .translation("kaleidoscope_wine_merchant.configuration.prices")
                .push("prices");

        b.comment("酒类的收购价。")
                .translation("kaleidoscope_wine_merchant.configuration.prices.wines")
                .push("wines");

        // 醋单开一项:默认「4 瓶换 1 绿宝石」的批量模式
        b.comment("醋  (kaleidoscope_tavern:vinegar)",
                        "默认走【批量模式】:凑够指定瓶数换 1 个绿宝石,不看品质。",
                        "把『启用批量模式』设为 false,就会改用下面的 6 档品质定价。")
                .translation("kaleidoscope_wine_merchant.configuration.prices.wines.vinegar")
                .push("vinegar");
        vinegarBulkEnabled = b.comment("是否启用批量模式(默认开启)。")
                .translation("kaleidoscope_wine_merchant.configuration.prices.wines.vinegar.bulk_enabled")
                .define("bulk_enabled", true);
        vinegarBulkBottles = b.comment("批量模式:多少瓶换 1 个绿宝石。")
                .translation("kaleidoscope_wine_merchant.configuration.prices.wines.vinegar.bulk_bottles")
                .defineInRange("bulk_bottles", 4, 1, 64);
        winePrices.put("kaleidoscope_tavern:vinegar",
                priceSectionInline(b, WineGroups.byId("kaleidoscope_tavern:vinegar"),
                        "kaleidoscope_wine_merchant.configuration.prices.wines.vinegar"));
        b.pop();

        for (WineGroups.Item item : WineGroups.WINES) {
            if (isVinegar(item)) {
                continue; // 已在上面单独处理
            }
            String key = "kaleidoscope_wine_merchant.configuration.prices.wines." + item.path();
            if (item.hasQuality()) {
                winePrices.put(item.id(), priceSection(b, item, key));
            } else {
                flatPrices.put(item.id(), flatSection(b, item, key));
            }
        }
        b.pop();

        b.comment("鸡尾酒出售价:村民卖给玩家的价格,固定价(不看品质)。")
                .translation("kaleidoscope_wine_merchant.configuration.prices.cocktails")
                .push("cocktails");
        for (WineGroups.Item item : WineGroups.COCKTAILS) {
            cocktailPrices.put(item.id(), priceSection(b, item,
                    "kaleidoscope_wine_merchant.configuration.prices.cocktails." + item.path()));
        }
        b.pop();

        b.comment("饮料的价格。无品质的饮料只有一个固定价。")
                .translation("kaleidoscope_wine_merchant.configuration.prices.drinks")
                .push("drinks");
        for (WineGroups.Item item : WineGroups.DRINKS) {
            String key = "kaleidoscope_wine_merchant.configuration.prices.drinks." + item.path();
            if (item.hasQuality()) {
                drinkPrices.put(item.id(), priceSection(b, item, key));
            } else {
                flatPrices.put(item.id(), flatSection(b, item, key));
            }
        }
        b.pop();

        b.comment("鸡尾酒收购价:玩家把鸡尾酒卖给村民时,村民付的价格。",
                        "所有鸡尾酒共用这一套 6 档价格。")
                .translation("kaleidoscope_wine_merchant.configuration.prices.player_sells")
                .push("player_sells");
        for (int q = 1; q <= 6; q++) {
            playerSellsPrices[q - 1] = b.comment(qualityName(q) + "的收购价(0 = 不收)。")
                    .translation("kaleidoscope_wine_merchant.configuration.prices.player_sells.q" + q)
                    .defineInRange("q" + q, WineGroups.BASE_PRICES.get(q - 1), 0, 64);
        }
        b.pop();

        b.pop(); // prices
    }

    /** 醋的 id。 */
    private static boolean isVinegar(WineGroups.Item item) {
        return "kaleidoscope_tavern:vinegar".equals(item.id());
    }

    /** 无品质物品:单个价格。 */
    private ModConfigSpec.IntValue flatSection(ModConfigSpec.Builder b,
                                               WineGroups.Item item, String key) {
        b.comment(item.cnName() + "  (" + item.id() + ")",
                        "该物品没有酿造品质,所以只给一个固定价。")
                .translation(key)
                .push(item.path());
        ModConfigSpec.IntValue v = b.comment("价格(绿宝石/个;0 = 不收)。")
                .translation(key + ".price")
                .defineInRange("price", item.flatPrice(), 0, 64);
        b.pop();
        return v;
    }

    /**
     * 生成一种物品的 6 个品质价格项(不 push/pop —— 由调用方处理节)。
     *
     * <p>注意:这里的 6 个键名 {@code q1..q6} 在同一节内唯一
     * (因为每种物品各占一个 {@code push(item.path())} 子节),不会发生键名碰撞。
     */
    private ModConfigSpec.IntValue[] priceSectionInline(ModConfigSpec.Builder b,
                                                        WineGroups.Item item, String key) {
        ModConfigSpec.IntValue[] arr = new ModConfigSpec.IntValue[6];
        List<Integer> def = WineGroups.pricesFor(item.id());
        for (int q = 1; q <= 6; q++) {
            int v = def.get(q - 1);
            arr[q - 1] = b.comment(qualityName(q) + "的价格(0 = 不收)。默认 " + v + "。")
                    .translation(key + ".q" + q)
                    .defineInRange("q" + q, v, 0, 64);
        }
        return arr;
    }

    /** 生成一种物品的 6 个品质价格项(自带节)。 */
    private ModConfigSpec.IntValue[] priceSection(ModConfigSpec.Builder b,
                                                  WineGroups.Item item, String keyPrefix) {
        b.comment(item.cnName() + "  (" + item.id() + ")")
                .translation(keyPrefix)
                .push(item.path());
        ModConfigSpec.IntValue[] arr = priceSectionInline(b, item, keyPrefix);
        b.pop();
        return arr;
    }

    private static String qualityName(int q) {
        return switch (q) {
            case 1 -> "品质1(难以下咽)";
            case 2 -> "品质2(劣质)";
            case 3 -> "品质3(普通)";
            case 4 -> "品质4(优质)";
            case 5 -> "品质5(精酿)";
            default -> "品质6(典藏)";
        };
    }

    // ══════════════════════════════════════════════════════════════════
    //  安全读取层
    //
    //  ★ 这里有一个实测过的时序坑:
    //      VillagerTradesEvent(生成村民交易表)比 SERVER 配置加载**更早**触发。
    //      实测日志:
    //        23:33:25.974  交易生成
    //        23:33:26.172  TOML 才加载完成   ← 晚了约 0.2 秒
    //      所以那时 ConfigValue#get() 会抛 IllegalStateException。
    //
    //  早期做法是 try/catch 吞掉异常并回退到内置默认值 —— 不会崩,但**读到的
    //  全是默认值**:玩家明明在配置里关掉了饮料,交易表里却照样给饮料生成交易
    //  (实测:配置里饮料 6 项全为 false,日志却报告"收购 230 条" =
    //   40 种酒 × 5 级 + 6 种饮料 × 5 级)。
    //
    //  现在的读取顺序:
    //      ① 直接解析 TOML 文件(唯一事实来源,不受加载顺序影响)
    //      ② 文件读不到 → 用 ModConfigSpec 的值(配置已加载时)
    //      ③ 都拿不到 → 内置默认值(仅在首次启动、文件尚未生成时发生)
    // ══════════════════════════════════════════════════════════════════

    /** 配置文件名的镜像读取器。文件名要与 modContainer.registerConfig 时一致。 */
    private static final ConfigMirror MIRROR = new ConfigMirror();

    private static final String CONFIG_FILE = "kaleidoscope_wine_merchant-server.toml";

    /** 启动后读一次;配置文件被改动时(FileWatcher)会再次调用。 */
    public static void refreshMirror() {
        if (MIRROR.load(CONFIG_FILE)) {
            KaleidoscopeWineMerchant.LOGGER.info(
                    "[WineMerchant] 已直接读取配置文件,共 {} 个键", MIRROR.size());
        } else {
            KaleidoscopeWineMerchant.LOGGER.warn(
                    "[WineMerchant] 未能直接读取配置文件(可能首次启动尚未生成),"
                            + "将回退到 ModConfigSpec");
        }
    }

    /**
     * 读取一个布尔开关。
     *
     * @param section 配置节路径,如 {@code pool.drinks}
     * @param key     键名,如 {@code cola}
     */
    private boolean readBool(ModConfigSpec.BooleanValue spec, String section, String key,
                             boolean fallback) {
        if (MIRROR.isLoaded()) {
            return MIRROR.bool(section + "." + key, fallback);
        }
        return spec == null ? fallback : safe(spec::get, fallback);
    }

    private static boolean readBoolStatic(ModConfigSpec.BooleanValue spec, String path,
                                          boolean fallback) {
        if (MIRROR.isLoaded()) {
            return MIRROR.bool(path, fallback);
        }
        return spec == null ? fallback : safe(spec::get, fallback);
    }

    private int readInt(ModConfigSpec.IntValue spec, String path, int fallback) {
        if (MIRROR.isLoaded()) {
            return MIRROR.integer(path, fallback);
        }
        return spec == null ? fallback : safe(spec::get, fallback);
    }

    private double readDouble(ModConfigSpec.DoubleValue spec, String path, double fallback) {
        if (MIRROR.isLoaded()) {
            return MIRROR.decimal(path, fallback);
        }
        return spec == null ? fallback : safe(spec::get, fallback);
    }

    public boolean isTierEnabled() {
        return readBool(tierEnabled, "general.tiers", "enabled", false);
    }

    public List<? extends String> tier(int n) {
        // 列表项不常用;文件镜像不解析数组,直接走 spec
        return switch (n) {
            case 1 -> safe(() -> tier1Wines.get(), List.of());
            case 2 -> safe(() -> tier2Wines.get(), List.of());
            default -> safe(() -> tier3Wines.get(), List.of());
        };
    }

    public List<? extends String> blacklist() {
        return safe(() -> blacklist.get(), List.of());
    }

    /** 醋是否走「N 瓶换 1 绿宝石」批量模式。 */
    public boolean vinegarBulk() {
        return readBool(vinegarBulkEnabled, "prices.wines.vinegar", "bulk_enabled", true);
    }

    /** 醋批量模式的瓶数。 */
    public int vinegarBulkBottles() {
        return readInt(vinegarBulkBottles, "prices.wines.vinegar.bulk_bottles", 4);
    }

    public int cocktailPriceMin() {
        return readInt(cocktailPriceMin, "pool.cocktails.sell.price_min", 5);
    }

    public int cocktailPriceMax() {
        return readInt(cocktailPriceMax, "pool.cocktails.sell.price_max", 10);
    }

    /** FLAT 模式下定价用的固定档位(普通品质) */
    public static final int FLAT_QUALITY = 3;

    /**
     * 是否使用「统一价格」模式。
     *
     * <p>{@code FLAT} = 不看放入物品的实际品质,一律按<b>普通品质</b>(档位 3)那一档给钱。
     * {@code BY_QUALITY}(默认)= 按实际品质给不同数量。
     *
     * <p>读取的是 TOML 文件镜像,所以 {@code /wmc refresh} 会先重读文件再重建交易表,
     * 改动可以立即生效。
     */
    public boolean isFlatPricing() {
        if (MIRROR.isLoaded()) {
            return "FLAT".equalsIgnoreCase(MIRROR.string("general.trade_mode", "BY_QUALITY"));
        }
        return safe(() -> tradeMode.get() == TradeMode.FLAT, false);
    }

    /** 按「节.键」读取某个池子开关。 */
    private boolean flag(Map<String, ModConfigSpec.BooleanValue> table, String section,
                         String id, boolean fallback) {
        String key = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        ModConfigSpec.BooleanValue v = table.get(id);
        return readBool(v, section, key, fallback);
    }

    public boolean wineEnabled(String id) {
        return flag(winePool, "pool.wines", id, true);
    }

    public boolean cocktailSell(String id) {
        return flag(cocktailSellPool, "pool.cocktails.sell", id, true);
    }

    public boolean cocktailBuy(String id) {
        return flag(cocktailBuyPool, "pool.cocktails.buy", id, false);
    }

    public boolean drinkEnabled(String id) {
        return flag(drinkPool, "pool.drinks", id, false);
    }

    /** 取某物品某品质的价格。 */
    private int price(Map<String, ModConfigSpec.IntValue[]> table, String section,
                      String id, int quality) {
        int fallback = WineGroups.pricesFor(id).get(Math.max(0, Math.min(5, quality - 1)));
        if (quality < 1 || quality > 6) {
            return fallback;
        }
        String key = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        String path = section + "." + key + ".q" + quality;
        if (MIRROR.isLoaded()) {
            return Math.max(0, MIRROR.integer(path, fallback));
        }
        ModConfigSpec.IntValue[] arr = table.get(id);
        if (arr == null || quality > arr.length) {
            return fallback;
        }
        return safe(() -> Math.max(0, arr[quality - 1].get()), fallback);
    }

    public int priceWine(String id, int quality) {
        return price(winePrices, "prices.wines", id, quality);
    }

    public int priceCocktail(String id, int quality) {
        return price(cocktailPrices, "prices.cocktails", id, quality);
    }

    public int priceDrink(String id, int quality) {
        return price(drinkPrices, "prices.drinks", id, quality);
    }

    public int pricePlayerSellsCocktail(int quality) {
        if (quality < 1 || quality > 6) {
            return 0;
        }
        int fallback = WineGroups.BASE_PRICES.get(quality - 1);
        return readInt(playerSellsPrices[quality - 1],
                "prices.player_sells.q" + quality, fallback);
    }

    /**
     * 等级识货的压价系数。
     *
     * <p>{@code max(0.1, 1 - step × (品质 - 基准等级))};品质不超过基准等级时不压价。
     * 关闭该机制(默认)时恒为 1.0。
     */
    public double appraisalFactor(int quality, int baselineLevel) {
        if (!readBoolStatic(ladderEnabled, "general.ladder.enabled", false)) {
            return 1.0D;
        }
        int gap = quality - Math.max(1, Math.min(5, baselineLevel));
        if (gap <= 0) {
            return 1.0D;
        }
        // 与 ladderEnabled 保持一致的读取链(优先 TOML 文件镜像)。
        // 早先这里直接用 safe(spec::get),配置未加载时会回退到 0.15,
        // 而不是文件里的实际值 —— 风格不一致,已统一走 readDouble。
        double step = readDouble(ladderStep, "general.ladder.step", 0.15D);
        return Math.max(0.1D, 1.0D - step * gap);
    }

    /** 通用安全读取:任何异常都返回 fallback,绝不向外抛。 */
    private static <T> T safe(java.util.function.Supplier<T> getter, T fallback) {
        try {
            return getter.get();
        } catch (Throwable t) {
            return fallback;
        }
    }

    /**
     * 把配置重新读一遍(指令 {@code /wmc reload} 与配置重载事件调用)。
     *
     * <p>早期版本这里还会缓存一份快照给交易生成阶段用;现在交易生成直接安全读取
     * ConfigValue(见上面的 {@code safe} 系列),所以这里只做一次"踢一脚"，
     * 让任何缓存失效。配置未加载时静默跳过,绝不抛异常。
     */
    public void refreshSnapshot() {
        if (!SPEC.isLoaded()) {
            return;
        }
        KaleidoscopeWineMerchant.LOGGER.info("[WineMerchant] 配置已重新读入内存");
    }
}
