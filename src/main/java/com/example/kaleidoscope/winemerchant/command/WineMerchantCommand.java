package com.example.kaleidoscope.winemerchant.command;

import com.example.kaleidoscope.winemerchant.KaleidoscopeWineMerchant;
import com.example.kaleidoscope.winemerchant.config.WineGroups;
import com.example.kaleidoscope.winemerchant.config.WineMerchantConfig;
import com.example.kaleidoscope.winemerchant.registry.ModProfessions;
import com.example.kaleidoscope.winemerchant.trade.WineMerchantTrades;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 酒商模组的指令。
 *
 * <h2>为什么需要这一套</h2>
 * NeoForge 的服务端配置在<b>多人服务器上无法在线编辑</b> —— 源码里写死了:
 * <pre>
 *   } else if (type == Type.SERVER &amp;&amp; minecraft.getCurrentServer() != null
 *              &amp;&amp; !minecraft.isSingleplayer()) {
 *       tooltip.append(TOOLTIP_CANNOT_EDIT_THIS_WHILE_ONLINE);   // "由服务端决定,且无法在线更改"
 *       btn.active = false;
 *   }
 * </pre>
 * 那段代码上面还有一句注释,承认"从客户端改服务端配置"这个功能<b>至今没实现</b>。
 * 所以服主以往只能去改服务器上的 TOML 文件。这套指令把同样的能力搬进了游戏内。
 *
 * <h2>改完配置为什么还要 {@code refresh}</h2>
 * 村民的交易在**认领职业那一刻**就烘焙进实体 NBT 了,改配置不会影响已有村民。
 * {@code refresh} 会先让范围内的酒商失业(职业清空),再重新认领 ——
 * 这等于让原版 {@code Villager.updateTrades()} 重新跑一遍,交易表随之重建。
 *
 * <h2>权限</h2>
 * 全部子指令要求权限等级 2(OP / 游戏管理员)。
 */
public final class WineMerchantCommand {

    private static final double DEFAULT_RADIUS = 16.0D;

    /** 补全:全部可交易物品 ID */
    private static final SuggestionProvider<CommandSourceStack> IDS =
            (ctx, b) -> {
                for (String id : WineGroups.allIds()) {
                    b.suggest(id);
                }
                return b.buildFuture();
            };

    /** 补全:仅鸡尾酒 */
    private static final SuggestionProvider<CommandSourceStack> COCKTAILS =
            (ctx, b) -> {
                for (WineGroups.Item i : WineGroups.COCKTAILS) {
                    b.suggest(i.id());
                }
                return b.buildFuture();
            };

    /** 指令反馈的翻译键前缀 */
    private static final String NS = "commands.kaleidoscope_wine_merchant.wmc.";

    private WineMerchantCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        d.register(Commands.literal("wmc")
                // 原版权限判定:等级 2 = 游戏管理员(OP)。
                // 注意不能用 Commands.hasPermission(...) —— 那是 Forge/Fabric 的扩展,
                // 原版 1.21.1 的 Commands 里没有这个方法。
                .requires(src -> src.hasPermission(2))
                .executes(WineMerchantCommand::help)

                .then(Commands.literal("price")
                        .then(Commands.argument("item", StringArgumentType.word())
                                .suggests(IDS)
                                .then(Commands.argument("quality", IntegerArgumentType.integer(1, 6))
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 64))
                                                .executes(WineMerchantCommand::setPrice)))))

                .then(Commands.literal("enable")
                        .then(Commands.argument("item", StringArgumentType.word())
                                .suggests(IDS)
                                .executes(c -> setEnabled(c, true))))
                .then(Commands.literal("disable")
                        .then(Commands.argument("item", StringArgumentType.word())
                                .suggests(IDS)
                                .executes(c -> setEnabled(c, false))))

                .then(Commands.literal("sell")
                        .then(Commands.argument("cocktail", StringArgumentType.word())
                                .suggests(COCKTAILS)
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(c -> setCocktail(c, true)))))
                .then(Commands.literal("buy")
                        .then(Commands.argument("cocktail", StringArgumentType.word())
                                .suggests(COCKTAILS)
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(c -> setCocktail(c, false)))))

                .then(Commands.literal("blacklist")
                        .then(Commands.literal("list").executes(WineMerchantCommand::blacklistList))
                        .then(Commands.literal("add")
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .executes(c -> blacklistModify(c, true))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .executes(c -> blacklistModify(c, false)))))

                .then(Commands.literal("mode")
                        .executes(WineMerchantCommand::showMode)
                        .then(Commands.literal("flat").executes(c -> setMode(c, true)))
                        .then(Commands.literal("quality").executes(c -> setMode(c, false))))

                .then(Commands.literal("reload").executes(WineMerchantCommand::reload))

                .then(Commands.literal("refresh")
                        .executes(c -> refresh(c, DEFAULT_RADIUS))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                                .executes(c -> refresh(c, IntegerArgumentType.getInteger(c, "radius")))))

                .then(Commands.literal("info").executes(WineMerchantCommand::info)));
    }

    // ──────────────────────────── 各子指令 ────────────────────────────

    private static int help(CommandContext<CommandSourceStack> c) {
        title(c, NS + "help.title");
        hint(c, NS + "help.price");
        hint(c, NS + "help.enable");
        hint(c, NS + "help.sell");
        hint(c, NS + "help.buy");
        hint(c, NS + "help.blacklist");
        hint(c, NS + "help.mode");
        hint(c, NS + "help.reload");
        hint(c, NS + "help.refresh");
        hint(c, NS + "help.refresh_hint");
        hint(c, NS + "help.info");
        hint(c, NS + "help.id_note1");
        hint(c, NS + "help.id_note2");
        return 1;
    }

    private static int setPrice(CommandContext<CommandSourceStack> c) {
        String id = resolveId(StringArgumentType.getString(c, "item"));
        if (id == null) {
            err(c, NS + "error.item_not_found");
            return 0;
        }
        int quality = IntegerArgumentType.getInteger(c, "quality");
        int value = IntegerArgumentType.getInteger(c, "value");

        ModConfigSpec.IntValue target = priceValue(id, quality);
        if (target == null) {
            err(c, NS + "price.not_quality_item", id);
            return 0;
        }
        if (!setAndSave(target, value)) {
            err(c, NS + "error.config_not_loaded");
            return 0;
        }
        ok(c, value == 0 ? NS + "price.set_free" : NS + "price.set", id, quality, value);
        hint(c, NS + "hint.run_refresh");
        return 1;
    }

    private static int setEnabled(CommandContext<CommandSourceStack> c, boolean enabled) {
        String id = resolveId(StringArgumentType.getString(c, "item"));
        if (id == null) {
            err(c, NS + "error.item_not_found");
            return 0;
        }
        ModConfigSpec.BooleanValue target = poolValue(id);
        if (target == null) {
            err(c, NS + "enable.not_in_pool", id);
            return 0;
        }
        if (!setAndSave(target, enabled)) {
            err(c, NS + "error.config_not_loaded");
            return 0;
        }
        ok(c, enabled ? NS + "enable.on" : NS + "enable.off", id);
        hint(c, NS + "hint.run_refresh");
        return 1;
    }

    private static int setCocktail(CommandContext<CommandSourceStack> c, boolean sellSide) {
        String id = resolveId(StringArgumentType.getString(c, "cocktail"));
        boolean value = BoolArgumentType.getBool(c, "value");
        if (id == null) {
            err(c, NS + "error.cocktail_not_found");
            return 0;
        }
        WineMerchantConfig cfg = WineMerchantConfig.CONFIG;
        Map<String, ModConfigSpec.BooleanValue> table =
                sellSide ? cfg.cocktailSellPool : cfg.cocktailBuyPool;
        ModConfigSpec.BooleanValue target = table.get(id);
        if (target == null) {
            err(c, NS + "cocktail.not_cocktail", id);
            return 0;
        }
        if (!setAndSave(target, value)) {
            err(c, NS + "error.config_not_loaded");
            return 0;
        }
        ok(c, (value ? NS + "cocktail.on." : NS + "cocktail.off.")
                + (sellSide ? "sell" : "buy"), id);
        hint(c, NS + "hint.run_refresh");
        return 1;
    }

    private static int blacklistList(CommandContext<CommandSourceStack> c) {
        List<? extends String> list = WineMerchantConfig.CONFIG.blacklist();
        if (list.isEmpty()) {
            hint(c, NS + "blacklist.empty");
        } else {
            title(c, NS + "blacklist.list", list.size(), String.join(", ", list));
        }
        return 1;
    }

    private static int blacklistModify(CommandContext<CommandSourceStack> c, boolean add) {
        String raw = StringArgumentType.getString(c, "item");
        String id = raw.contains(":") ? raw : null;   // 黑名单要求完整 ID
        if (id == null) {
            err(c, NS + "blacklist.need_full_id");
            return 0;
        }
        if (!WineMerchantConfig.SPEC.isLoaded()) {
            err(c, NS + "error.config_not_loaded");
            return 0;
        }
        List<String> cur = new ArrayList<>(WineMerchantConfig.CONFIG.blacklist());
        if (add) {
            if (cur.contains(id)) {
                hint(c, NS + "blacklist.already", id);
                return 1;
            }
            cur.add(id);
        } else if (!cur.remove(id)) {
            hint(c, NS + "blacklist.absent", id);
            return 1;
        }
        try {
            var holder = WineMerchantConfig.CONFIG.blacklistValue;
            setRaw(holder, List.copyOf(cur));
            WineMerchantConfig.SPEC.save();
            WineMerchantConfig.CONFIG.refreshSnapshot();
        } catch (Throwable t) {
            err(c, NS + "error.write_failed", String.valueOf(t));
            return 0;
        }
        ok(c, add ? NS + "blacklist.added" : NS + "blacklist.removed",
                id, cur.size());
        hint(c, NS + "hint.run_refresh");
        return 1;
    }

    // ──────────────────────────── 定价模式 ────────────────────────────

    /** 查看当前定价模式 */
    private static int showMode(CommandContext<CommandSourceStack> c) {
        boolean flat = WineMerchantConfig.CONFIG.isFlatPricing();
        title(c, flat ? NS + "mode.current_flat" : NS + "mode.current_quality");
        hint(c, flat ? NS + "mode.desc_flat" : NS + "mode.desc_quality");
        hint(c, NS + "mode.switch_hint");
        return 1;
    }

    /** 切换定价模式 */
    private static int setMode(CommandContext<CommandSourceStack> c, boolean flat) {
        ModConfigSpec.EnumValue<WineMerchantConfig.TradeMode> target =
                WineMerchantConfig.CONFIG.tradeMode;
        WineMerchantConfig.TradeMode value = flat
                ? WineMerchantConfig.TradeMode.FLAT
                : WineMerchantConfig.TradeMode.BY_QUALITY;

        if (!setAndSave(target, value)) {
            err(c, NS + "error.config_not_loaded");
            return 0;
        }
        ok(c, flat ? NS + "mode.switched_flat" : NS + "mode.switched_quality");
        hint(c, NS + "hint.run_refresh");
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> c) {
        WineMerchantConfig.CONFIG.refreshSnapshot();
        ok(c, NS + "reload.done");
        hint(c, NS + "reload.note");
        return 1;
    }

    /**
     * 让附近酒商重新抽取交易,并让配置改动真正生效。
     *
     * <h2>为什么不是简单"失业再就业"</h2>
     * 村民的交易表({@code VillagerTrades.TRADES})只在<b>世界加载时</b>由 NeoForge
     * 构建一次。之后 {@code Villager.updateTrades()} 只是从那张表里<b>重新抽样</b> ——
     * 它不会重新读配置。所以光让村民失业再就业,价格、开关、黑名单都不会变。
     *
     * <p>本指令做两件事:
     * <ol>
     *   <li>按当前配置重建条目,并<b>直接写回 {@code VillagerTrades.TRADES}</b>
     *       ({@link WineMerchantTrades#rebuildIntoVanillaRegistry()})</li>
     *   <li>让范围内酒商失业再就业,使它们从新表里抽取交易</li>
     * </ol>
     * 这样价格、物品开关、黑名单、鸡尾酒方向、酒类分档的改动都能立即生效,
     * 不需要重启游戏。
     */
    private static int refresh(CommandContext<CommandSourceStack> c, double radius) {
        CommandSourceStack src = c.getSource();
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            err(c, NS + "refresh.player_only");
            return 0;
        }

        // ① 重建条目并写回原版注册表
        int rebuilt = WineMerchantTrades.rebuildIntoVanillaRegistry();
        if (rebuilt < 0) {
            err(c, NS + "refresh.failed");
            return 0;
        }

        // ② 让范围内的酒商从新表里重新抽取
        ServerLevel level = src.getLevel();
        AABB box = player.getBoundingBox().inflate(radius);
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, box);

        int done = 0;
        for (Villager v : villagers) {
            if (!v.getVillagerData().getProfession().equals(ModProfessions.WINE_MERCHANT.get())) {
                continue;
            }
            v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NONE));
            v.setVillagerData(v.getVillagerData().setProfession(ModProfessions.WINE_MERCHANT.get()));
            done++;
        }

        KaleidoscopeWineMerchant.LOGGER.info(
                "[WineMerchant] 指令重建交易表 {} 条,并刷新了 {} 名酒商", rebuilt, done);

        if (done == 0) {
            hint(c, NS + "refresh.rebuilt_no_villager", rebuilt, (int) radius);
            return 0;
        }
        ok(c, NS + "refresh.rebuilt", rebuilt, done);
        return done;
    }

    private static int info(CommandContext<CommandSourceStack> c) {
        WineMerchantConfig cfg = WineMerchantConfig.CONFIG;
        title(c, NS + "info.title");
        hint(c, NS + "info.items", WineGroups.WINES.size(),
                WineGroups.COCKTAILS.size(), WineGroups.DRINKS.size());
        hint(c, NS + "info.features",
                Component.translatable(cfg.isTierEnabled() ? NS + "state.on" : NS + "state.off"),
                Component.translatable(cfg.appraisalFactor(6, 1) < 1.0D ? NS + "state.on" : NS + "state.off"));
        hint(c, NS + "info.blacklist", cfg.blacklist().size());
        hint(c, NS + "info.file");
        return 1;
    }

    // ──────────────────────────── 工具方法 ────────────────────────────

    /** 让 "wine" 这类短名也能用:补全成 命名空间:路径。 */
    private static String resolveId(String raw) {
        if (raw.contains(":")) {
            return raw;
        }
        for (String id : WineGroups.allIds()) {
            if (id.endsWith(":" + raw)) {
                return id;
            }
        }
        return null;
    }

    private static ModConfigSpec.IntValue priceValue(String id, int quality) {
        WineMerchantConfig cfg = WineMerchantConfig.CONFIG;
        ModConfigSpec.IntValue[] arr = cfg.winePrices.get(id);
        if (arr == null) {
            arr = cfg.cocktailPrices.get(id);
        }
        if (arr == null) {
            arr = cfg.drinkPrices.get(id);
        }
        if (arr == null || quality < 1 || quality > 6) {
            return null;
        }
        return arr[quality - 1];
    }

    private static ModConfigSpec.BooleanValue poolValue(String id) {
        WineMerchantConfig cfg = WineMerchantConfig.CONFIG;
        ModConfigSpec.BooleanValue v = cfg.winePool.get(id);
        if (v == null) {
            v = cfg.drinkPool.get(id);
        }
        if (v == null) {
            v = cfg.cocktailSellPool.get(id);
        }
        if (v == null) {
            v = cfg.cocktailBuyPool.get(id);
        }
        return v;
    }

    /** 设值 + 落盘 + 刷新快照。配置未加载时返回 false。 */
    private static <T> boolean setAndSave(ModConfigSpec.ConfigValue<T> value, T v) {
        if (!WineMerchantConfig.SPEC.isLoaded()) {
            KaleidoscopeWineMerchant.LOGGER.warn("[WineMerchant] 指令写入被拒绝:配置尚未加载");
            return false;
        }
        try {
            // 写入前记录旧值,便于对比"到底改没改"
            Object before = null;
            try {
                before = value.get();
            } catch (Throwable ignored) {
                // 读旧值失败不影响写入
            }
            setRaw(value, v);
            WineMerchantConfig.SPEC.save();
            WineMerchantConfig.CONFIG.refreshSnapshot();

            Object after = null;
            try {
                after = value.get();
            } catch (Throwable ignored) {
                // 读新值失败不影响返回
            }
            String path;
            try {
                path = String.join(".", value.getPath());
            } catch (Throwable t) {
                path = "?";
            }
            KaleidoscopeWineMerchant.LOGGER.info(
                    "[WineMerchant] 指令写入配置: path={} 旧值={} 新值={} 写后读回={}",
                    path, before, v, after);
            return true;
        } catch (Throwable t) {
            KaleidoscopeWineMerchant.LOGGER.warn("[WineMerchant] 写入配置失败:{}", t.toString());
            return false;
        }
    }

    /** 绕过泛型通配符,把值写进 ConfigValue。 */
    @SuppressWarnings("unchecked")
    private static <T> void setRaw(ModConfigSpec.ConfigValue<?> holder, T v) {
        ((ModConfigSpec.ConfigValue<T>) holder).set(v);
    }

    /**
     * 发送一条已本地化的反馈。
     *
     * <p>用 {@link Component#translatable} 而不是 {@code literal} —— 反馈组件随数据包
     * 发给客户端,由客户端按自己的语言解析,所以中文客户端看到中文、英文客户端看到英文。
     * <b>颜色用 {@code withStyle} 指定,不写进语言文件</b>,翻译者改文案时就不必跟
     * {@code §} 转义符搏斗。
     */
    private static void sayKey(CommandContext<CommandSourceStack> c, String key,
                               ChatFormatting style, Object... args) {
        Component msg = args.length == 0
                ? Component.translatable(key)
                : Component.translatable(key, args);
        c.getSource().sendSuccess(() -> msg.copy().withStyle(style), false);
    }

    /** 灰色提示 */
    private static void hint(CommandContext<CommandSourceStack> c, String key, Object... args) {
        sayKey(c, key, ChatFormatting.GRAY, args);
    }

    /** 金色标题 */
    private static void title(CommandContext<CommandSourceStack> c, String key, Object... args) {
        sayKey(c, key, ChatFormatting.GOLD, args);
    }

    /** 成功(绿色) */
    private static void ok(CommandContext<CommandSourceStack> c, String key, Object... args) {
        sayKey(c, key, ChatFormatting.GREEN, args);
    }

    /** 失败(红色) */
    private static void err(CommandContext<CommandSourceStack> c, String key, Object... args) {
        sayKey(c, key, ChatFormatting.RED, args);
    }
}
