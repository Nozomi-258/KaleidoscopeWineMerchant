package com.example.kaleidoscope.winemerchant;

import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * 客户端专用入口:注册 NeoForge 内建的配置界面。
 *
 * <p>注册后,玩家可以在游戏内 `模组` 页面选中本模组,点击 `配置` 按钮直接编辑,
 * 不需要去翻配置文件。
 *
 * <p><b>权限说明</b>:本模组用的是 {@code ModConfig.Type.SERVER} 配置。
 * 单人游戏里可以自由编辑;一旦连接到服务器(或别人的 LAN 世界),
 * NeoForge 会<b>自动禁用</b>该配置的编辑能力 —— 玩家只能看,改不了。
 * 这正是"服务器里只有管理员能统一改动"所需要的语义,无需我们额外写权限代码。
 */
public final class WineMerchantClient {

    private WineMerchantClient() {
    }

    /** 注册配置屏工厂。必须在模组构造阶段调用。 */
    public static void registerConfigScreen(ModContainer container) {
        // ConfigurationScreen 的构造器是 public,直接 new 即可。
        // 注意不能用方法引用 ConfigurationScreen::new —— 它对应的是
        // (ModContainer, Screen) 构造器,而接口方法签名是 (ModContainer, Screen) -> Screen,
        // 参数一致但返回类型是 ConfigurationScreen(子类型),lambda 形式更明确。
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (mod, parent) -> new ConfigurationScreen(mod, parent));
    }
}
