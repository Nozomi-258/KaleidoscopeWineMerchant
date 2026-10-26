package com.example.kaleidoscope.winemerchant.network;

import com.example.kaleidoscope.winemerchant.KaleidoscopeWineMerchant;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 「连接兼容性」握手包 —— 内容是连接方本模组的版本号。
 *
 * <h2>为什么需要它</h2>
 * 本模组注册了方块、物品、村民职业与数据组件,并且会用 Mixin 改写交易定价逻辑。
 * <b>客户端与服务端版本不一致时会静默出错</b>,而不是干脆连不上:
 * <ul>
 *   <li>{@code quality_priced_wine} 数据组件的编解码字段在版本间会变化 ——
 *       一边写入、另一边解析失败,价格显示与实际不符;</li>
 *   <li>交易表的结构与条目数量也可能不同;</li>
 *   <li>玩家看到的是"功能全是 bug",而不是一条明确的不兼容提示。</li>
 * </ul>
 *
 * <h2>机制</h2>
 * NeoForge 的 {@code PayloadRegistrar.versioned(String)} 官方说明:
 * <blockquote>
 *   On Neo-Neo connections, the connection will only succeed if all registered
 *   payloads have the same version.
 * </blockquote>
 * 我们把版本号作为频道版本注册,于是**版本不一致时 NeoForge 会在连接阶段直接拒绝**,
 * 客户端会看到清晰的"模组版本不匹配"提示,而不是进游戏后满屏 bug。
 *
 * <p>这个包的<b>处理器本身是空的</b> —— 它的作用只是"存在且带版本号",
 * 让 NeoForge 有东西可以比对。真正的校验由 NeoForge 在连接阶段完成。
 *
 * <p>数据内容(版本字符串)仍然发送,主要用于日志与排查。
 *
 * @param version 发送方本模组的版本,例如 {@code "1.1.3"}
 */
public record VersionCheckPayload(String version) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<VersionCheckPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            KaleidoscopeWineMerchant.MOD_ID, "version_check"));

    /** 版本号是短字符串,用 UTF-8 字符串编解码即可。 */
    public static final StreamCodec<ByteBuf, VersionCheckPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, VersionCheckPayload::version,
                    VersionCheckPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
