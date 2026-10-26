package com.example.kaleidoscope.winemerchant.mixin;

import com.example.kaleidoscope.winemerchant.registry.ModProfessions;
import com.example.kaleidoscope.winemerchant.trade.TradeDedupe;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让酒商**每升一级稳定新增 2 条不重复的交易**。
 *
 * <h2>它解决什么问题</h2>
 * 原版 {@code AbstractVillager.addOffersFromItemListings} 的循环是:
 * <pre>
 *   while (added &lt; max &amp;&amp; !list.isEmpty()) {
 *       offer = list.remove(random).getOffer(...);
 *       offers.add(offer);   // ← 不检查是否与已有交易重复
 *       added++;
 *   }
 * </pre>
 * 未开启「酒类分档」时,每一级的候选池都是"全部酒",所以新抽到的酒
 * 很可能与低等级时已有的那条重复。
 *
 * <p>1.1.4 的第一版处理方式是在升级**完成后**把重复的删掉
 * (见 {@link VillagerTradeDedupeMixin})。但这样一来:
 * <pre>
 *   原版追加 2 条 -> 其中 1 条重复被删 -> 玩家只看到多出 1 件商品   ❌
 * </pre>
 * 这正是"村民升到学徒/老手时只增加一件商品"的原因。
 *
 * <h2>本 Mixin 的做法</h2>
 * 直接接管这个方法:随机取一条,若与已有交易重复就**丢弃并继续取**,
 * 直到凑够 {@code max} 条或池子被抽空。这样:
 * <ul>
 *   <li>每级仍然新增 2 条(与原版一致);</li>
 *   <li>不会出现同一瓶酒的两条交易。</li>
 * </ul>
 *
 * <p>只对酒商职业生效;其他职业直接放行,走原版逻辑。
 * 逻辑本体在普通类 {@link TradeDedupe} 中(Mixin 不允许非 private 的 static 方法)。
 */
@Mixin(AbstractVillager.class)
public abstract class AbstractVillagerTradeMixin {

    /**
     * 接管候选抽取:限定酒商职业,其余职业不干预。
     *
     * @param offers 目标交易表
     * @param pool   该等级的候选池
     * @param max    本次要追加的条数(原版固定 2)
     */
    @Inject(method = "addOffersFromItemListings", at = @At("HEAD"), cancellable = true)
    private void wineMerchant$addDistinct(MerchantOffers offers, VillagerTrades.ItemListing[] pool,
                                          int max, CallbackInfo ci) {
        if (!((Object) this instanceof Villager villager)) {
            return;     // 流浪商人等,不干预
        }
        if (villager.getVillagerData().getProfession() != ModProfessions.WINE_MERCHANT.get()) {
            return;     // 其他职业,走原版
        }
        TradeDedupe.addDistinctOffers(villager, offers, pool, max);
        ci.cancel();
    }
}
