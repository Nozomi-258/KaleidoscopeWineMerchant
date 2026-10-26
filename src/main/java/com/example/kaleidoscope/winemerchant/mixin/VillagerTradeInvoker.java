package com.example.kaleidoscope.winemerchant.mixin;

import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露原版 {@code AbstractVillager.addOffersFromItemListings(...)}(protected)。
 *
 * <p><b>为什么需要它</b>{@code /wmc refresh} 会在运行时重写
 * {@code VillagerTrades.TRADES}。原版从不这么做 —— 那张表只在世界加载时构建一次,
 * 之后村民**每次升级**都按自己的 {@code level} 从表里抽 2 条**追加**到自己的 offers。
 *
 * <p>一旦运行中重写表,已存在的村民会因为各种原因重新走一遍抽取流程,
 * <b>而它们此前靠升级累积的条目就丢了</b> ——
 * 症状:老手(3 级)原本 4 条,refresh 后只剩 2 条;之后每升一级只加 2 条,
 * 满级也补不回 10 条。
 *
 * <p>修法是 refresh 之后**主动补齐**:对每个已加载的村民,按它的等级算出
 * 应有的条数(每级 2 条),缺多少就再抽多少追加进去 —— 既保留原有的条目,
 * 又恢复到正常上限。这需要调用原版那个 protected 方法,故用 {@link Invoker} 暴露。
 *
 * <p>原版实现(1.21.1 {@code AbstractVillager}):
 * <pre>
 *   protected void addOffersFromItemListings(MerchantOffers given, VillagerTrades.ItemListing[] newTrades, int maxNumbers) {
 *       ArrayList&lt;ItemListing&gt; list = Lists.newArrayList(newTrades);
 *       int i = 0;
 *       while (i &lt; maxNumbers &amp;&amp; !list.isEmpty()) {
 *           MerchantOffer offer = list.remove(this.random.nextInt(list.size())).getOffer(this, this.random);
 *           if (offer != null) { given.add(offer); i++; }
 *       }
 *   }
 * </pre>
 */
@Mixin(AbstractVillager.class)
public interface VillagerTradeInvoker {

    /** 直接调用原版的"从候选列表里抽 N 条追加到 offers"。 */
    @Invoker("addOffersFromItemListings")
    void wineMerchant$addOffers(MerchantOffers givenMerchantOffers,
                               VillagerTrades.ItemListing[] newTrades,
                               int maxNumbers);
}
