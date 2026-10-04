package com.example.kaleidoscope.winemerchant.registry;

import com.example.kaleidoscope.winemerchant.KaleidoscopeWineMerchant;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本模组的方块与对应物品。
 *
 * <p>「酒商柜台」是酒商村民的工作站点(Job Site)。刻意新建自己的方块,
 * 而不是复用其他模组的酒桶/酒架,以避免 POI 归属冲突。
 */
public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(KaleidoscopeWineMerchant.MOD_ID);

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(KaleidoscopeWineMerchant.MOD_ID);

    /**
     * 酒商柜台方块。
     *
     * <p>属性对齐**原版工作台**(它同样是"木制 + 工作性质"的方块):
     * <pre>
     *   CRAFTING_TABLE    : .mapColor(WOOD).instrument(BASS).strength(2.5F).sound(WOOD).ignitedByLava()
     *   CARTOGRAPHY_TABLE : 同上(制图台也是工作站点方块)
     * </pre>
     *
     * <p><b>刻意不调用 {@code requiresCorrectToolForDrops()}</b> ——
     * 工作台也没有这个属性,所以空手挖照样掉落。而"斧头挖得快"靠的是
     * 数据包标签 {@code data/minecraft/tags/block/mineable/axe.json} 里登记,
     * 与这个方法无关。两者是独立的:
     * <ul>
     *   <li>{@code mineable/axe} 标签 → 斧头**挖得快**</li>
     *   <li>{@code requiresCorrectToolForDrops()} → 用错工具**不掉落**</li>
     * </ul>
     * 只保留前者,就得到"和木板一样,斧头快、但空手也能挖掉"的手感。
     */
    public static final DeferredBlock<Block> WINE_MERCHANT_COUNTER = BLOCKS.register(
            "wine_merchant_counter",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .instrument(NoteBlockInstrument.BASS)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .ignitedByLava())
    );

    /** 对应物品形式(用于创造模式标签页、合成表与掉落) */
    public static final DeferredItem<BlockItem> WINE_MERCHANT_COUNTER_ITEM = ITEMS.registerSimpleBlockItem(
            "wine_merchant_counter", WINE_MERCHANT_COUNTER);

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}
