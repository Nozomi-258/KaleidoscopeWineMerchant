package com.example.kaleidoscope.winemerchant.trade;

import com.example.kaleidoscope.winemerchant.KaleidoscopeWineMerchant;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 「酿造品质」({@code brew_level})组件的查表工具。
 *
 * <p>品质由<b>森罗厨房体系</b>注册:实测 {@code kaleidoscope_tavern} 的 {@code ModDataComponents}
 * 里以 {@code DataComponentType<Integer>} 定义了 {@code brew_level};
 * {@code kaleidoscope_world_liquor} 没有另建组件,复用同一个 —— 所以一个组件覆盖两个模组的酒。
 *
 * <p><b>为什么查表而不直接引用类型</b>:本模组的原则是只通过注册 ID(字符串)与森罗厨房体系交互,
 * 不引用其内部 Java 类。这样对方更新内部结构时,我们不会编译失败(最多是功能降级)。
 */
public final class BrewQuality {

    /** 品质档位数:1 难以下咽 / 2 劣质 / 3 普通 / 4 优质 / 5 精酿 / 6 典藏 */
    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 6;

    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath("kaleidoscope_tavern", "brew_level");
    private static final ResourceLocation ID_FALLBACK =
            ResourceLocation.fromNamespaceAndPath("kaleidoscope_world_liquor", "brew_level");

    private static DataComponentType<Integer> resolved;
    private static boolean looked;

    private BrewQuality() {
    }

    /** 取品质组件类型;模组未安装或改了名字时返回 null(功能降级,不崩溃)。 */
    @SuppressWarnings("unchecked")
    public static DataComponentType<Integer> component() {
        if (!looked) {
            looked = true;
            for (ResourceLocation id : new ResourceLocation[]{ID, ID_FALLBACK}) {
                if (!BuiltInRegistries.DATA_COMPONENT_TYPE.containsKey(id)) {
                    continue;
                }
                DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(id);
                if (type == null) {
                    continue;
                }
                try {
                    if (type.codec() != null) {
                        resolved = (DataComponentType<Integer>) type;
                        break;
                    }
                } catch (Throwable ignored) {
                    // 泛型不符就试下一个候选
                }
            }
            if (resolved == null) {
                KaleidoscopeWineMerchant.LOGGER.warn(
                        "[WineMerchant] 未找到 brew_level 品质组件 —— 品质相关功能将降级为不分品质");
            } else {
                KaleidoscopeWineMerchant.LOGGER.info(
                        "[WineMerchant] 已接入酿造品质组件:{}", ID);
            }
        }
        return resolved;
    }

    /** 读取物品的品质等级;没有该组件时返回 0。 */
    public static int get(ItemStack stack) {
        DataComponentType<Integer> type = component();
        if (type == null || stack == null || stack.isEmpty()) {
            return 0;
        }
        Integer v = stack.get(type);
        return v == null ? 0 : v;
    }

    /** 给物品附加品质等级(「谓词分档」退路构造交易条目时用)。 */
    public static ItemStack with(ItemStack stack, int level) {
        DataComponentType<Integer> type = component();
        if (type == null || stack == null || stack.isEmpty()) {
            return stack;
        }
        ItemStack copy = stack.copy();
        copy.set(type, clamp(level));
        return copy;
    }

    public static int clamp(int level) {
        return Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, level));
    }

    /** 品质档是否有效(1~6)。 */
    public static boolean isValid(int level) {
        return level >= MIN_LEVEL && level <= MAX_LEVEL;
    }

    /** 品质组件是否可用。 */
    public static boolean available() {
        return component() != null;
    }
}
