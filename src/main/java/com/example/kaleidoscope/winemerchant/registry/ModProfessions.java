package com.example.kaleidoscope.winemerchant.registry;

import com.example.kaleidoscope.winemerchant.KaleidoscopeWineMerchant;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 「酒商」村民职业。
 *
 * <p>工作站点为 {@link ModPoiTypes#WINE_MERCHANT_COUNTER},工作音效沿用原版屠夫工作音。
 */
public final class ModProfessions {

    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(Registries.VILLAGER_PROFESSION, KaleidoscopeWineMerchant.MOD_ID);

    /** 酒商 */
    public static final DeferredHolder<VillagerProfession, VillagerProfession> WINE_MERCHANT =
            PROFESSIONS.register("wine_merchant", () -> new VillagerProfession(
                    "wine_merchant",
                    holder -> holder.is(ModPoiTypes.WINE_MERCHANT_COUNTER.getKey()),
                    holder -> holder.is(ModPoiTypes.WINE_MERCHANT_COUNTER.getKey()),
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    SoundEvents.VILLAGER_WORK_BUTCHER));

    private ModProfessions() {
    }

    public static void register(IEventBus modEventBus) {
        PROFESSIONS.register(modEventBus);
    }
}
