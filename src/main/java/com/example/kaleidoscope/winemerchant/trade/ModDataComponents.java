package com.example.kaleidoscope.winemerchant.trade;

import com.example.kaleidoscope.winemerchant.KaleidoscopeWineMerchant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * 本模组自己的数据组件。
 *
 * <p>{@link #QUALITY_PRICED_WINE} 标记「这条交易的绿宝石数量要在成交时按品质重算」,
 * 并<b>自带 6 档品质各自该给几个绿宝石</b>。
 *
 * <h2>为什么价格要随产出一起携带</h2>
 * 改产出数量这一步会同时在<b>服务端与客户端</b>执行:
 * <ul>
 *   <li>服务端决定实际给玩家多少 → 影响真实结果</li>
 *   <li>客户端决定界面显示多少 → 影响玩家看到的数字</li>
 * </ul>
 * 若两边各自去查本地配置文件,一旦同步时机不同就会算出不同数字,
 * 轻则界面与实际不符,重则出现「显示 1 个、拿到 3 个」这类复制漏洞。
 * 把价目表随产出物品一起携带(物品数据本身就会同步到客户端),
 * 两边读到的必然是同一份数据。
 *
 * <h2>为什么标记打在产出上</h2>
 * 原版 {@code MerchantContainer.updateSellItem()}:
 * <pre>
 *   ItemStack stack = offer.assemble();   // 产出在这里生成
 *   this.setItem(2, stack);               // 直接进入结果槽
 * </pre>
 * 而 {@code MerchantResultSlot.onTake} <b>不会重新计算产出</b>,只是把槽位 2 的内容交给玩家。
 * 因此改 {@code assemble()} 的返回值这一处,就能同时决定预览与实际到手。
 */
public final class ModDataComponents {

    /**
     * 「按品质计价」标记的载荷。
     *
     * @param wineId       物品的注册 ID(用于判断该物品是否有酿造品质)
     * @param perQuality   品质 1~6 各自该给几个绿宝石;0 表示不收该品质
     * @param baselineLevel 该物品所属档位的<b>基准等级</b>(第1档=1、第2档=3、第3档=5),
     *                      用于「等级识货」比较
     * @param flat         该物品<b>没有酿造品质</b>,价格固定为 {@code perQuality} 的第 3 项
     *                     (即"普通品质"档的值),不按放入物品的品质变化
     */
    public record QualityPricedWine(String wineId, List<Integer> perQuality, int baselineLevel,
                                    boolean flat) {

        public static final Codec<QualityPricedWine> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("wine").forGetter(QualityPricedWine::wineId),
                Codec.INT.listOf().fieldOf("emeralds").forGetter(QualityPricedWine::perQuality),
                Codec.INT.optionalFieldOf("baseline", 5).forGetter(QualityPricedWine::baselineLevel),
                Codec.BOOL.optionalFieldOf("flat", false).forGetter(QualityPricedWine::flat)
        ).apply(inst, QualityPricedWine::new));

        /** 取某档品质该给的绿宝石数;不收或越界返回 0。 */
        public int emeraldsFor(int quality) {
            if (quality < 1 || quality > perQuality.size()) {
                return 0;
            }
            return perQuality.get(quality - 1);
        }

        /**
         * 实际要给的绿宝石数。
         *
         * <p>无品质物品(flat)只看"普通品质"那一档,不随放入物品的品质变化。
         */
        public int payout(int actualQuality) {
            return flat ? emeraldsFor(3) : emeraldsFor(actualQuality);
        }
    }

    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(
                    net.minecraft.core.registries.Registries.DATA_COMPONENT_TYPE,
                    KaleidoscopeWineMerchant.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<QualityPricedWine>>
            QUALITY_PRICED_WINE = DATA_COMPONENTS.registerComponentType(
                    "quality_priced_wine",
                    builder -> builder.persistent(QualityPricedWine.CODEC));

    private ModDataComponents() {
    }

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }

    /** 给产出物品打上「按品质计价」标记。 */
    public static ItemStack mark(ItemStack stack, QualityPricedWine payload) {
        if (stack.isEmpty() || payload == null) {
            return stack;
        }
        ItemStack copy = stack.copy();
        copy.set(QUALITY_PRICED_WINE.get(), payload);
        return copy;
    }

    /** 读取标记;没有则返回 null。 */
    public static QualityPricedWine payload(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.get(QUALITY_PRICED_WINE.get());
    }
}
