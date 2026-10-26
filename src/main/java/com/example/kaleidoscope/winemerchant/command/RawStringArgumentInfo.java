package com.example.kaleidoscope.winemerchant.command;

import com.google.gson.JsonObject;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.network.FriendlyByteBuf;

/**
 * {@link RawStringArgumentType} 的网络序列化器。
 *
 * <h2>为什么必须有这个类</h2>
 * 原版指令树是要**打包发给客户端**的:服务端用
 * {@code ClientboundCommandsPacket} 把整棵指令树(每个参数的<b>类型</b>)序列化,
 * 客户端再反序列化出来做补全和校验。
 *
 * <p>这个过程中,原版用 {@code ArgumentTypeInfos.byClass(argumentType)}
 * 在 {@link net.minecraft.core.registries.Registries#COMMAND_ARGUMENT_TYPE} 里查
 * "这个参数类型怎么序列化"。**查不到就抛**:
 * <pre>
 *   java.lang.IllegalArgumentException: Unrecognized argument type
 *     com.example...RawStringArgumentType
 *   at ArgumentTypeInfos.byClass(...)
 *   at ClientboundCommandsPacket$ArgumentNodeStub.&lt;init&gt;(...)
 *   at Commands.sendCommands(...)
 *   at PlayerList.placeNewPlayer(...)
 *   → "Couldn't place player in world"
 * </pre>
 *
 * <p>表现出来就是:**进世界时直接断开,客户端提示"无效的数据"**。
 * 1.1.4 第一版就踩了这个坑 —— 自定义参数类型忘了注册。
 *
 * <h2>为什么能直接写新类而不改 {@link RawStringArgumentType}</h2>
 * {@link ArgumentTypeInfo} 是独立于参数类型本身的接口,外部实现即可,
 * 原版 {@code SingletonArgumentInfo} 也是这么做的。
 *
 * <p>本类型**没有可变参数**,所以序列化/反序列化都是空操作;
 * 模板里的 {@code instantiate} 直接返回单例。
 */
public final class RawStringArgumentInfo
        implements ArgumentTypeInfo<RawStringArgumentType, RawStringArgumentInfo.Template> {

    /** 反序列化后交给客户端的模板 —— 本类型无参数,直接返回单例。 */
    public static final class Template implements ArgumentTypeInfo.Template<RawStringArgumentType> {

        static final Template INSTANCE = new Template();

        private Template() {
        }

        @Override
        public RawStringArgumentType instantiate(CommandBuildContext context) {
            return RawStringArgumentType.rawString();
        }

        @Override
        public ArgumentTypeInfo<RawStringArgumentType, ?> type() {
            return RawStringArgumentInfo.INSTANCE;
        }
    }

    /** 供 {@code DeferredRegister} 使用的单例。 */
    public static final RawStringArgumentInfo INSTANCE = new RawStringArgumentInfo();

    private RawStringArgumentInfo() {
    }

    @Override
    public void serializeToNetwork(Template template, FriendlyByteBuf buffer) {
        // 无参数可写
    }

    @Override
    public Template deserializeFromNetwork(FriendlyByteBuf buffer) {
        return Template.INSTANCE;
    }

    @Override
    public void serializeToJson(Template template, JsonObject json) {
        // 无参数可写
    }

    @Override
    public Template unpack(RawStringArgumentType argument) {
        return Template.INSTANCE;
    }
}
