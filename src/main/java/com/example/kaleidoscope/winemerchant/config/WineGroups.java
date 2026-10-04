package com.example.kaleidoscope.winemerchant.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 物品分组表:把可交易物品分成三大类,并提供按「合成原料数」推算的默认价格。
 *
 * <h2>分类依据(来自模组配方数据,不是主观判断)</h2>
 * <ul>
 *   <li><b>鸡尾酒</b>:用<b>雪克杯(shaker)</b>配方产出 —— 共 19 种。</li>
 *   <li><b>酒类</b>:用<b>酒桶(barrel)</b>酿造的饮品 —— 共 40 种。</li>
 *   <li><b>饮料</b>:无酒精的日常饮品 —— 共 6 种。</li>
 * </ul>
 *
 * <h2>默认价格 = 按原料数递增</h2>
 * <pre>
 *   基准价(1 种原料):  品质1=0(不收) 2=1 3=1 4=2 5=2 6=3
 *   每多 1 种原料,六个品质全部 +1 绿宝石。
 *
 *   例:劲凉冰红茶用 3 种原料 → 基准 +2 → 普通品质 = 1+2 = 4 绿宝石 ✔
 * </pre>
 * 「原料数」取该物品所有配方中的最大值:酒桶配方看 {@code ingredients} 数组,
 * 有序合成看图案种类数,无序合成与雪克杯看 {@code ingredients} 数组长度。
 */
public final class WineGroups {

    /**
     * 基准价(1 种原料):品质 1~6
     *
     * <p>规则:每多 1 种原料,六个品质全部 +1 绿宝石。
     * 葡萄酒用 1 种原料(葡萄汁),所以普通品质 = 1 绿宝石。
     */
    public static final List<Integer> BASE_PRICES = List.of(0, 1, 1, 2, 2, 3);

    /** 一条物品的静态定义。 */
    public record Item(String namespace, String path, String cnName, int ingredients, boolean hasQuality) {
        public String id() {
            return namespace + ":" + path;
        }

        /** 该物品的 6 档默认价格 = 基准价 + (原料数 - 1)。 */
        public List<Integer> defaultPrices() {
            int bump = Math.max(0, ingredients - 1);
            List<Integer> out = new ArrayList<>(6);
            for (int base : BASE_PRICES) {
                // 0 表示"不收",加档不应把它变成"能收"
                out.add(base <= 0 ? 0 : base + bump);
            }
            return out;
        }

        /** 无品质物品的单一默认价(取普通品质档的值)。 */
        public int flatPrice() {
            return defaultPrices().get(2);
        }
    }

    private static Item i(String ns, String path, String cn, int ing, boolean hasQuality) {
        return new Item(ns, path, cn, ing, hasQuality);
    }

    private static Item i(String ns, String path, String cn, int ing) {
        return new Item(ns, path, cn, ing, true);
    }

    private static Item i(String ns, String path, String cn) {
        return new Item(ns, path, cn, 1, true); // 查不到配方者按最低档
    }

    /** 无品质(不能酿造出不同品质)的物品 —— 只给一个固定价。 */
    private static Item flat(String ns, String path, String cn, int ing) {
        return new Item(ns, path, cn, ing, false);
    }

    // ══════════════════════ 鸡尾酒(雪克杯,19 种)══════════════════════
    public static final List<Item> COCKTAILS = List.of(
            i("kaleidoscope_tavern", "mojito", "莫吉托", 3),
            i("kaleidoscope_tavern", "screwdriver", "螺丝起子", 3),
            i("kaleidoscope_tavern", "bloody_mary", "血腥玛丽", 3),
            i("kaleidoscope_tavern", "godfather", "教父", 3),
            i("kaleidoscope_tavern", "grasshopper", "绿色蚱蜢", 3),
            i("kaleidoscope_tavern", "depth_charge", "深水炸弹", 3),
            i("kaleidoscope_tavern", "white_lady", "白色佳人", 3),
            i("kaleidoscope_tavern", "nether_special", "下界特调", 3),
            i("kaleidoscope_tavern", "sculk_special", "幽匿特调", 3),
            i("kaleidoscope_tavern", "allium_garden", "紫罗兰花园", 3),
            i("kaleidoscope_tavern", "brass_heart", "黄铜之心", 3),
            // 注:该物品的官方名是"绿宝石",与交易产出的绿宝石同名 ——
            //     作为鸡尾酒被收购时需注意辨识(命名空间不同,不会混淆)。
            i("kaleidoscope_tavern", "emerald", "绿宝石鸡尾酒", 3),
            i("kaleidoscope_tavern", "mystery_cocktail", "谜之鸡尾酒", 3),
            i("kaleidoscope_world_liquor", "around_the_world", "环游世界", 3),
            i("kaleidoscope_world_liquor", "gin_tonic", "金汤力", 3),
            i("kaleidoscope_world_liquor", "jerk", "渣男", 3),
            i("kaleidoscope_world_liquor", "long_island_iced_tea", "长岛冰茶", 3),
            i("kaleidoscope_world_liquor", "pine_colada", "椰林飘香", 3),
            i("kaleidoscope_world_liquor", "shrimp_cocktail", "鲜虾鸡尾酒", 3));

    // ══════════════════════ 饮料(6 种)══════════════════════
    // 【无品质】西瓜汁、可乐、汤力水 —— 这三个没有酿造品质,只给一个固定价
    public static final List<Item> DRINKS = List.of(
            flat("kaleidoscope_tavern", "watermelon_juice", "西瓜汁", 1),
            i("kaleidoscope_world_liquor", "cool_tea", "劲凉冰红茶", 4),
            // ⚠ 命名空间是 smc,不是 kaleidoscope_world_liquor:
            //    世界酒类模组把这个物品注册在自己的 SMCItems 类里(namespace = smc),
            //    虽然贴图放在 assets/kaleidoscope_world_liquor/ 下。
            //    运行时日志确认过 kaleidoscope_world_liquor:ice_tea 并不存在。
            i("smc", "ice_tea", "冰红茶", 4),
            i("kaleidoscope_world_liquor", "sour_plum", "酸梅汤", 3),
            flat("kaleidoscope_world_liquor", "cola", "可乐", 3),
            flat("kaleidoscope_world_liquor", "tonic_water", "汤力水", 3));

    // ══════════════════════ 酒类(40 种)══════════════════════
    // 原料数 = 酒桶配方里的 fluid(液体原料)+ ingredients(固体原料)之和
    public static final List<Item> WINES = List.of(
            // 基础餐酒
            i("kaleidoscope_tavern", "wine", "葡萄酒", 1),
            i("kaleidoscope_tavern", "ice_wine", "冰葡萄酒", 1),
            i("kaleidoscope_tavern", "sweet_berry_wine", "甜浆果酒", 2),
            i("kaleidoscope_tavern", "sakura_wine", "樱花葡萄酒", 2),
            i("kaleidoscope_tavern", "plum_wine", "梅酒", 2),
            i("kaleidoscope_tavern", "honey_wine", "蜂蜜葡萄酒", 2),
            i("kaleidoscope_tavern", "glowflower_brew", "萤花酿", 2),
            // 烈酒与基酒
            i("kaleidoscope_tavern", "vodka", "伏特加", 2),
            i("kaleidoscope_tavern", "whiskey", "威士忌", 2),
            i("kaleidoscope_tavern", "brandy", "白兰地", 2),
            i("kaleidoscope_tavern", "rum", "朗姆酒", 2),
            i("kaleidoscope_tavern", "sherry", "雪莉", 2),
            i("kaleidoscope_tavern", "champagne", "香槟", 2),
            i("kaleidoscope_tavern", "carignan", "佳丽私酿", 2),
            i("kaleidoscope_tavern", "polaris_sweet_white", "北极星甜白", 2),
            // 葡萄酒款(三种原料)
            i("kaleidoscope_tavern", "riesling_dry_white", "雷司令干白", 3),
            i("kaleidoscope_tavern", "sauvignon_blanc_dry_white", "长相思干白", 3),
            // 特调(酒桶酿造)
            i("kaleidoscope_tavern", "sunset_glow", "落日余晖", 2),
            i("kaleidoscope_tavern", "luminous_bride", "夜光新娘", 2),
            i("kaleidoscope_tavern", "madame_shexiang", "奢香夫人", 2),
            i("kaleidoscope_tavern", "miners_star", "矿工之星", 2),
            i("kaleidoscope_tavern", "mother_snow", "雪婆婆", 2),
            i("kaleidoscope_tavern", "red_queen", "红皇后", 2),
            // 无配方或无品质者
            i("kaleidoscope_tavern", "vinegar", "醋", 1),
            flat("kaleidoscope_tavern", "molotov", "莫洛托夫", 1),
            // 世界酒类
            i("kaleidoscope_world_liquor", "strongbow", "诗庄堡", 3),
            i("kaleidoscope_world_liquor", "kwas_chlebowy", "格瓦斯", 3),
            i("kaleidoscope_world_liquor", "absolut_vodka", "绝对伏特加", 3),
            i("kaleidoscope_world_liquor", "smirnoff_red_vodka", "斯米诺红牌伏特加", 3),
            i("kaleidoscope_world_liquor", "bacardi_carta_blanca", "百加得白朗姆", 3),
            i("kaleidoscope_world_liquor", "bombay_sapphire_gin", "孟买蓝宝石金酒", 3),
            i("kaleidoscope_world_liquor", "skyy_vodka", "深蓝伏特加", 2),
            i("kaleidoscope_world_liquor", "jack_daniel", "杰克丹尼", 3),
            i("kaleidoscope_world_liquor", "johnnie_walker", "尊尼获加", 3),
            i("kaleidoscope_world_liquor", "bamboo_leaf_green_liquor", "竹叶青", 3),
            i("kaleidoscope_world_liquor", "dassai", "獭祭", 2),
            i("kaleidoscope_world_liquor", "spiryt_vodka", "生命之水96", 3),
            i("kaleidoscope_world_liquor", "maotai", "飞天茅台", 3),
            i("kaleidoscope_world_liquor", "lafite_1982", "拉菲1982", 2),
            i("kaleidoscope_world_liquor", "pina_colada", "马利宝椰子朗姆酒", 3));

    // ══════════════════════ 分档默认值(拓展选项)══════════════════════
    // 依据:原料数越多 = 越高级。1~2 种原料 → 第1档;3~4 种 → 第2档;5 种以上 → 第3档。

    private WineGroups() {
    }

    public static List<String> defaultTier1() {
        return byIngredients(1, 2);
    }

    public static List<String> defaultTier2() {
        return byIngredients(3, 4);
    }

    public static List<String> defaultTier3() {
        return byIngredients(5, 99);
    }

    private static List<String> byIngredients(int min, int max) {
        List<String> out = new ArrayList<>();
        for (Item item : WINES) {
            if (item.ingredients() >= min && item.ingredients() <= max) {
                out.add(item.id());
            }
        }
        return out;
    }

    /** 查某物品的默认 6 档价格;查不到用基准价。 */
    public static List<Integer> pricesFor(String id) {
        Item item = BY_ID.get(id);
        return item == null ? BASE_PRICES : item.defaultPrices();
    }

    /** 按 id 查物品定义;查不到返回 null。 */
    public static Item byId(String id) {
        return BY_ID.get(id);
    }

    private static final Map<String, Item> BY_ID = new HashMap<>();

    static {
        for (List<Item> group : List.of(WINES, DRINKS, COCKTAILS)) {
            for (Item item : group) {
                BY_ID.put(item.id(), item);
            }
        }
    }

    /** 全部可交易物品 ID。 */
    public static List<String> allIds() {
        List<String> out = new ArrayList<>();
        WINES.forEach(w -> out.add(w.id()));
        DRINKS.forEach(d -> out.add(d.id()));
        COCKTAILS.forEach(c -> out.add(c.id()));
        return out;
    }

    /** 供排查:三类清单的规模。 */
    public static Set<String> groupNames() {
        return Set.of("wines=" + WINES.size(), "drinks=" + DRINKS.size(), "cocktails=" + COCKTAILS.size());
    }
}
