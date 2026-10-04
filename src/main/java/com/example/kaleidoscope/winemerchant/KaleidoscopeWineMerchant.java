package com.example.kaleidoscope.winemerchant;

import com.example.kaleidoscope.winemerchant.command.WineMerchantCommand;
import com.example.kaleidoscope.winemerchant.config.WineMerchantConfig;
import com.example.kaleidoscope.winemerchant.registry.ModBlocks;
import com.example.kaleidoscope.winemerchant.registry.ModPoiTypes;
import com.example.kaleidoscope.winemerchant.registry.ModProfessions;
import com.example.kaleidoscope.winemerchant.trade.ModDataComponents;
import com.example.kaleidoscope.winemerchant.trade.WineMerchantTrades;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 森罗厨房 · 酒商附属模组
 *
 * <p>新增一个「酒商」村民职业,专职收购玩家手中的酒类物品(绿宝石换酒)。
 *
 * <p>设计原则(为了兼容森罗厨房本体后续更新):
 * <ul>
 *   <li>只通过<b>注册 ID(字符串)</b>与森罗厨房体系交互,不引用其内部 Java 类;</li>
 *   <li>交易走 NeoForge 官方 {@code VillagerTradesEvent};</li>
 *   <li>所有物品查找都做存在性检查,缺哪个就跳过哪个,不会因此崩溃。</li>
 * </ul>
 */
@Mod(KaleidoscopeWineMerchant.MOD_ID)
public class KaleidoscopeWineMerchant {

    public static final String MOD_ID = "kaleidoscope_wine_merchant";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public KaleidoscopeWineMerchant(IEventBus modEventBus, ModContainer modContainer) {
        // 服务端配置:只有服务端那份生效,并会同步给客户端(客户端无权修改)。
        // 单文件 + 多层节:常规 / 池子构成 / 价格设置 / 黑名单排除。
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER,
                WineMerchantConfig.SPEC);

        // 注册顺序:数据组件 -> 方块 -> 工作站点(POI) -> 职业
        ModDataComponents.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModPoiTypes.register(modEventBus);
        ModProfessions.register(modEventBus);

        // 游戏事件总线:追加村民交易、注册指令
        NeoForge.EVENT_BUS.register(WineMerchantTrades.class);
        NeoForge.EVENT_BUS.register(WineMerchantCommand.class);

        // 配置改动时重新读一次文件镜像。
        // ★ 这一步不能省:村民交易表(VillagerTradesEvent)生成得比 SERVER 配置加载更早,
        //   实测早约 0.2 秒。若只依赖 ConfigValue#get(),那时会拿到内置默认值 ——
        //   表现就是"配置里关掉的物品照样被交易"。
        //   ConfigMirror 直接解析 TOML 文件,不受加载顺序影响。
        WineMerchantConfig.refreshMirror();
        modEventBus.addListener(this::onConfigChanged);
        modEventBus.addListener(this::onConfigChangedReload);

        // 把酒商柜台加进原版的「功能方块」创造模式标签页
        modEventBus.addListener(this::addToCreativeTab);

        // 客户端:注册内建配置界面(模组页 -> 本模组 -> 配置)
        if (FMLEnvironment.dist == Dist.CLIENT) {
            WineMerchantClient.registerConfigScreen(modContainer);
        }
    }

    /** 配置首次加载完成 */
    private void onConfigChanged(net.neoforged.fml.event.config.ModConfigEvent.Loading event) {
        WineMerchantConfig.refreshMirror();
    }

    /** 配置文件被改动后(FileWatcher 触发) */
    private void onConfigChangedReload(net.neoforged.fml.event.config.ModConfigEvent.Reloading event) {
        WineMerchantConfig.refreshMirror();
    }

    /**
     * 把酒商柜台放进**酒馆模组的创造模式标签页**,而不是原版的「功能方块」页。     *
     * <p>理由:柜台属于"酒馆玩法"的一部分,和酒类物品放在一起更容易被玩家找到。
     * 酒馆模组提供了两个标签页:
     * <pre>
     *   kaleidoscope_tavern:tavern_main   森罗物语:酒馆      ← 主内容(酒、设备)
     *   kaleidoscope_tavern:tavern_deco   森罗物语:酒馆装饰  ← 装饰方块
     * </pre>
     * 柜台是功能方块,所以进 {@code tavern_main}。
     *
     * <p>用 {@code ResourceKey} 直接比对键值,而不是引用酒馆模组的类 ——
     * 这样即使酒馆将来改了包名/类名,本模组也只需改一个字符串常量,
     * 不会因为类找不到而崩溃。
     */
    private void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().location().equals(TAVERN_MAIN_TAB)) {
            event.accept(ModBlocks.WINE_MERCHANT_COUNTER_ITEM);
        }
    }

    /** 酒馆模组主标签页的注册 ID */
    private static final net.minecraft.resources.ResourceLocation TAVERN_MAIN_TAB =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    "kaleidoscope_tavern", "tavern_main");
}
