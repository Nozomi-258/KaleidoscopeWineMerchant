package com.example.kaleidoscope.winemerchant.mixin;

import com.example.kaleidoscope.winemerchant.config.WineMerchantConfig;
import com.example.kaleidoscope.winemerchant.trade.BrewQuality;
import com.example.kaleidoscope.winemerchant.trade.ModDataComponents;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 按品质定价的唯一挂钩点。
 *
 * <h2>为什么必须用 Mixin</h2>
 * 原版交易流程(见 {@code MerchantContainer.updateSellItem()}):
 * <pre>
 *   MerchantOffer offer = offers.getRecipeFor(itemstack, itemstack1, hint);  // ① 匹配:只能判断"收不收"
 *   this.setItem(2, offer.assemble());                                        // ② 产出:价格在此固化
 * </pre>
 * ① 无法改价;价格由交易条目自己写死。因此"同一条交易按玩家实际放入的品质给出不同价格"
 * <b>只能</b>在 ② 处动手,而 ② 是原版方法,NeoForge 没有对应事件,只能字节码注入。
 *
 * <h2>为什么改这里不会刷钱、不会两端不一致</h2>
 * {@code MerchantResultSlot.onTake} <b>不会重新计算产出</b> —— 它只是校验成本、然后
 * 把结果槽(槽位 2)里的物品交给玩家。而槽位 2 的内容正是本方法改写后的返回值。所以:
 * <ul>
 *   <li>预览与实际到手<b>是同一个值</b>,不存在"显示 1 个、拿到 8 个"的复制漏洞;</li>
 *   <li>该值在服务端与客户端各算一次,但算法只依赖
 *       (a) 交易槽里的物品(品质组件会随物品同步)与
 *       (b) 产出物品自带的价目表(见 {@link ModDataComponents.QualityPricedWine})——
 *       两边输入完全相同,结果必然一致。</li>
 * </ul>
 *
 * <h2>付款槽必须同时看槽 0 和槽 1</h2>
 * 原版 {@code updateSellItem()} 在<b>槽 0 为空</b>时会把<b>槽 1</b>当作主付款物:
 * <pre>
 *   if (this.itemStacks.get(0).isEmpty()) {
 *       itemstack  = this.itemStacks.get(1);   // ← 主付款物变成槽 1
 *       itemstack1 = ItemStack.EMPTY;
 *   }
 * </pre>
 * 而 {@code MerchantResultSlot.onTake} 也会先试 {@code take(槽0,槽1)}、
 * 失败再试 {@code take(槽1,槽0)}。两个槽在 {@code MerchantMenu} 里都是普通 Slot、
 * 没有 {@code mayPlace} 限制,所以玩家完全可以把酒放进槽 1。
 * 早期版本只读槽 0,那种情况下会退回原版的 1 个绿宝石(少给钱),现已同时检查两槽。
 */
@Mixin(MerchantContainer.class)
public abstract class MerchantContainerMixin {

    @Shadow
    public abstract ItemStack getItem(int index);

    /**
     * 重定向 {@code offer.assemble()}:在产出交给结果槽之前,按玩家放入那件物品的真实品质定价。
     *
     * <p>规则:成本固定 1 个;给几个绿宝石由价格表里该品质对应的数值决定。
     * <b>数值为 0 表示不收该品质</b> —— 此时把产出置空,交易槽会显示为不可成交,
     * 玩家一眼就能看出"这东西他不收"。
     *
     * @param offer 正在结算的交易条目
     * @return 修正后的产出
     */
    @Redirect(
            method = "updateSellItem",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/trading/MerchantOffer;assemble()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack wineMerchant$assembleWithQualityPrice(MerchantOffer offer) {
        ItemStack original = offer.assemble();
        if (original.isEmpty() || !original.is(Items.EMERALD)) {
            return original;
        }

        ModDataComponents.QualityPricedWine payload = ModDataComponents.payload(original);
        if (payload == null) {
            return original; // 不是本模组标记的产出,原样放行
        }

        // ── 找出玩家实际付出的那件物品 ──
        // 槽 0 优先;槽 0 为空则看槽 1(与原版 updateSellItem 的取值顺序一致)。
        ItemStack offered = this.getItem(0);
        if (BrewQuality.get(offered) <= 0) {
            ItemStack second = this.getItem(1);
            if (BrewQuality.get(second) > 0) {
                offered = second;
            }
        }
        if (offered.isEmpty()) {
            return original;
        }

        int quality = BrewQuality.get(offered);
        boolean flat = payload.flat();
        if (!BrewQuality.isValid(quality)) {
            if (!flat) {
                return original; // 有品质的物品却读不到品质 → 保持原版数量
            }
            quality = 3; // 无品质物品:按"普通品质"那一档定价
        }

        // ── 定价模式 ──
        // FLAT(统一价格):不看实际品质,一律按普通品质那一档给钱。
        // BY_QUALITY(默认):按实际品质给不同数量。
        boolean uniform = WineMerchantConfig.CONFIG.isFlatPricing();
        int pricingQuality = uniform ? WineMerchantConfig.FLAT_QUALITY : quality;

        int base = payload.payout(pricingQuality);
        if (base <= 0) {
            // 该品质不收:置空结果槽,使交易无法成交
            return ItemStack.EMPTY;
        }

        // ── 等级识货(默认关闭):品质高于该档基准等级时压价 ──
        // 无品质物品不参与压价(它没有品质差异可言);
        // 统一价格模式下也不压价 —— 否则"统一价"就不是统一的了。
        double factor = (!flat && !uniform)
                ? WineMerchantConfig.CONFIG.appraisalFactor(quality, payload.baselineLevel())
                : 1.0D;

        // 四舍五入;但「收」的品质最低给 1 个 ——
        // 避免折扣把它算成 0,而 0 在本模组里表示「不收」
        int emeralds = (int) Math.max(1L, Math.round(base * factor));
        emeralds = Math.min(emeralds, original.getMaxStackSize());

        // 必须返回<b>干净</b>的绿宝石:若直接在 original 上改数量,
        // 会把本模组的数据组件一起带进玩家背包,导致这些绿宝石
        // 无法与普通绿宝石堆叠(组件不同即不同物品),而且组件没有
        // tooltip、玩家看不见,极难排查。
        ItemStack out = new ItemStack(Items.EMERALD, emeralds);
        out.remove(ModDataComponents.QUALITY_PRICED_WINE.get());
        return out;
    }
}
