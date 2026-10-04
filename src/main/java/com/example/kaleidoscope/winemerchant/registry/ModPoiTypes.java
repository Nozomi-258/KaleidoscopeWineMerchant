package com.example.kaleidoscope.winemerchant.registry;

import com.example.kaleidoscope.winemerchant.KaleidoscopeWineMerchant;
import com.google.common.collect.ImmutableSet;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 工作站点类型(POI)。村民靠它认领职业。
 *
 * <h2>参数取原版职业的相同值:(maxTickets = 1, validRange = 1)</h2>
 * 原版 1.21.1 全部 13 个职业的工作站点都是这两个值,例如:
 * <pre>
 *   register(registry, FARMER,    getBlockStates(Blocks.COMPOSTER),        1, 1);
 *   register(registry, BUTCHER,   getBlockStates(Blocks.SMOKER),           1, 1);
 *   register(registry, LIBRARIAN, getBlockStates(Blocks.LECTERN),          1, 1);
 *   register(registry, ARMORER,   getBlockStates(Blocks.BLAST_FURNACE),    1, 1);
 * </pre>
 *
 * <p><b>validRange 不是"搜索半径"</b>,而是判定"村民是否站在自己的工作站点旁"的
 * 容差(单位:方块)。原版用 1,即必须紧邻。早期版本这里写了 48,导致村民隔着
 * 很远就算"就位",认领行为看起来异常宽松。
 *
 * <p>真正的搜索半径由 {@code Villager} 的 {@code POI_RANGE}(48 格)决定,
 * 那是实体侧常量,与这里无关。
 */
public final class ModPoiTypes {

    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(
                    net.minecraft.core.registries.Registries.POINT_OF_INTEREST_TYPE,
                    KaleidoscopeWineMerchant.MOD_ID);

    /** 酒商的工作站点:酒商柜台。参数与原版职业一致(1, 1)。 */
    public static final DeferredHolder<PoiType, PoiType> WINE_MERCHANT_COUNTER = POI_TYPES.register(
            "wine_merchant_counter",
            () -> new PoiType(
                    ImmutableSet.copyOf(
                            ModBlocks.WINE_MERCHANT_COUNTER.get()
                                    .getStateDefinition()
                                    .getPossibleStates()),
                    1,
                    1)
    );

    private ModPoiTypes() {
    }

    public static void register(IEventBus modEventBus) {
        POI_TYPES.register(modEventBus);
    }
}
