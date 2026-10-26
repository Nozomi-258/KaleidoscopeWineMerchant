package com.example.kaleidoscope.winemerchant.trade;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 交易表去重工具。
 *
 * <h2>为什么需要去重</h2>
 * 未开启「酒类分档」时(默认),{@code buildItemBuys} 把每种酒登记到
 * <b>全部 5 个等级</b>的候选池里:
 * <pre>
 *   levels = ALL_LEVELS;   // {1,2,3,4,5}
 * </pre>
 * 而原版 {@code Villager.increaseMerchantCareer()} 每升一级就调用 {@code updateTrades()},
 * 从**该等级**的池子里抽 2 条**追加**到自己的交易表。由于每一级的池子都是"全部酒":
 * <pre>
 *   1 级抽到「梅酒」→ 追加
 *   2 级池子里也有梅酒 → 又抽到 → 再追加一条   ❌ 同一村民两条梅酒
 * </pre>
 *
 * <p>原版不会遇到这个问题 —— 它的各级池子与等级强绑定(学徒卖铁、大师卖钻石),
 * 内容差异很大,跨级重复概率极低。我们把"全部物品"塞进每一级,
 * 就打破了这个隐含前提,必须自己保证不重复。
 *
 * <h2>两种去重方式(重要)</h2>
 * <ol>
 *   <li><b>事后删除</b>({@link #dedupeBuyOffers}) —— 原版先追加 2 条,再把重复的删掉。
 *       <b>问题</b>:删掉之后就只新增了 0~1 条,玩家升级时看到"只多了一件商品"。
 *       这是 1.1.4 里真实发生过的 bug。</li>
 *   <li><b>抽取时就跳过重复</b>({@link #addDistinctOffers}) —— 从池子里随机取,
 *       若与已有交易重复就丢弃这一条、<b>继续取</b>,直到凑够数量。
 *       这样每级仍然稳定新增 2 条,且不重复。✅ 推荐</li>
 * </ol>
 *
 * <p><b>为什么这个类不是 Mixin</b>:Mixin 类不允许出现非 private 的 static 方法
 * (会抛 {@code InvalidMixinException: contains non-private static method}),
 * 而这段逻辑既被多个 Mixin 调用、也被 {@code /wmc refresh} 调用,
 * 所以放在普通类里共享。
 */
public final class TradeDedupe {

    private TradeDedupe() {
    }

    /**
     * 交易条目的去重键。
     *
     * <p>出售类(玩家花绿宝石买)的 {@code costA} 是绿宝石,**不参与去重**
     * —— 那本来就该有多条。返回 {@code null} 表示"这条不参与去重"。
     */
    private static String dedupeKey(MerchantOffer offer) {
        ItemStack costA = offer.getCostA();
        if (costA.is(Items.EMERALD)) {
            return null;
        }
        return costA.isEmpty()
                ? "?empty"
                : BuiltInRegistries.ITEM.getKey(costA.getItem()) + "|" + costA.getCount();
    }

    /** 收集一组交易里所有参与去重的键。 */
    private static Set<String> keys(MerchantOffers offers) {
        Set<String> set = new LinkedHashSet<>();
        for (MerchantOffer offer : offers) {
            String k = dedupeKey(offer);
            if (k != null) {
                set.add(k);
            }
        }
        return set;
    }

    /**
     * 按「被收购的物品」去掉重复的**收购类**交易。
     *
     * <p>只作安全网使用 —— 正常情况下 {@link #addDistinctOffers} 已经保证不重复。
     *
     * @return 移除的条数
     */
    public static int dedupeBuyOffers(MerchantOffers offers) {
        Set<String> seen = new LinkedHashSet<>();
        int removed = 0;
        Iterator<MerchantOffer> it = offers.iterator();
        while (it.hasNext()) {
            String key = dedupeKey(it.next());
            if (key == null) {
                continue;
            }
            if (!seen.add(key)) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    /**
     * 从候选池里挑 {@code max} 条**互不重复、也不与已有交易重复**的报价追加进去。
     *
     * <p>这就是"抽取时跳过重复"的实现:取到重复的就丢弃并继续取,
     * 所以只要池子里还有足够的**不同**物品,就一定能凑够 {@code max} 条。
     * 池子被抽空时提前结束。
     *
     * @return 实际追加的条数
     */
    public static int addDistinctOffers(Villager villager, MerchantOffers offers,
                                        VillagerTrades.ItemListing[] pool, int max) {
        if (pool == null || pool.length == 0 || max <= 0) {
            return 0;
        }
        List<VillagerTrades.ItemListing> candidates = new ArrayList<>(List.of(pool));
        Set<String> used = keys(offers);
        int added = 0;
        while (added < max && !candidates.isEmpty()) {
            // 随机取一条并从池中移除(取过的不再考虑,保证循环收敛)
            int index = villager.getRandom().nextInt(candidates.size());
            VillagerTrades.ItemListing listing = candidates.remove(index);

            MerchantOffer offer = listing.getOffer(villager, villager.getRandom());
            if (offer == null) {
                continue;
            }
            String key = dedupeKey(offer);
            if (key != null && !used.add(key)) {
                continue;   // 与已有交易重复 -> 丢弃,继续取下一个
            }
            offers.add(offer);
            added++;
        }
        return added;
    }
}
