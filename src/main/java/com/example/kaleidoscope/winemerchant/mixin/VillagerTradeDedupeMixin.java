package com.example.kaleidoscope.winemerchant.mixin;

import com.example.kaleidoscope.winemerchant.KaleidoscopeWineMerchant;
import com.example.kaleidoscope.winemerchant.registry.ModProfessions;
import com.example.kaleidoscope.winemerchant.trade.TradeDedupe;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让酒商升级后不出现「同一瓶酒两条交易」。
 *
 * <h2>为什么需要它</h2>
 * 未开启「酒类分档」时,每种酒都被登记到**全部 5 个等级**的候选池里,
 * 而原版每升一级就从该等级的池子抽 2 条**追加** —— 同一种酒会被不同等级各抽中一次,
 * 玩家就看到同一个村民的交易表里出现两条「梅酒」/ 两条「白兰地」。
 * 详见 {@link TradeDedupe} 的说明。
 *
 * <h2>为什么逻辑不写在本类里</h2>
 * <b>Mixin 类不允许出现非 private 的 static 方法</b> —— 否则 Mixin 会抛:
 * <pre>
 *   InvalidMixinException: Mixin ... contains non-private static method xxx
 *   at MixinApplicatorStandard.checkMethodVisibility
 * </pre>
 * 这段去重逻辑需要被本 Mixin 与 {@code /wmc refresh} 共享,
 * 所以放在普通类 {@link TradeDedupe} 中,本类只保留注入器。
 *
 * <p>只对酒商职业生效,不影响其他职业。
 */
@Mixin(Villager.class)
public abstract class VillagerTradeDedupeMixin {

    /**
     * 升级完成后去掉重复的收购交易。
     *
     * <p>{@code increaseMerchantCareer} 是原版的私有方法,用 {@code @Inject} 在 TAIL 注入。
     * 若该方法在目标版本不存在或改名,Mixin 会直接报错(而不是静默失效),
     * 日志里会出现 "Mixin apply failed" —— 便于排查。
     */
    @Inject(method = "increaseMerchantCareer", at = @At("TAIL"))
    private void wineMerchant$dedupeAfterLevelUp(CallbackInfo ci) {
        Villager self = (Villager) (Object) this;
        if (self.getVillagerData().getProfession() != ModProfessions.WINE_MERCHANT.get()) {
            return;
        }
        int removed = TradeDedupe.dedupeBuyOffers(self.getOffers());
        if (removed > 0) {
            KaleidoscopeWineMerchant.LOGGER.debug(
                    "[WineMerchant] 升级后去重:移除了 {} 条重复的收购交易", removed);
        }
    }
}
