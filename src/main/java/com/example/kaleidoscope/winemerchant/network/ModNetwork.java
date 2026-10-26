package com.example.kaleidoscope.winemerchant.network;

import com.example.kaleidoscope.winemerchant.KaleidoscopeWineMerchant;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 网络层:注册「连接兼容性」握手包,使客户端与服务端**版本必须一致**才能连上。
 *
 * <h2>它解决什么问题</h2>
 * 实测过一个真实的坏情况:<b>1.1.2 的客户端连 1.1.1 的服务器能进去,但功能全是 bug</b>。
 * 原因有两层:
 * <ol>
 *   <li>我们原先没有声明 {@code displayTest},而"能进游戏"说明连接期的版本校验没拦住;</li>
 *   <li>版本不一致时,{@code quality_priced_wine} 数据组件的编解码字段不同 ——
 *       一端写入、另一端解析失败,于是价格显示与实际支付不符之类的"怪 bug"。</li>
 * </ol>
 *
 * <h2>NeoForge 官方机制</h2>
 * {@code PayloadRegistrar.versioned(version)} 的文档写得很明确:
 * <blockquote>
 *   On Neo-Neo connections, the connection will only succeed if all registered
 *   payloads have the same version.
 * </blockquote>
 * 因此这里注册一个非 optional 的 payload、把版本设为**本模组的版本号**,
 * 版本不一致时 NeoForge 会在连接阶段拒绝,玩家得到的是明确的"版本不匹配"提示,
 * 而不是进服后满屏功能异常。
 *
 * <p>注意:{@code version} 不应等于"模组版本号"本身,而应等于<b>协议版本</b> ——
 * 详见 {@link #PROTOCOL_VERSION}。
 */
public final class ModNetwork {

    /**
     * <b>协议版本</b> —— 决定"能不能连上",与模组版本号解耦。
     *
     * <h2>为什么不能直接用模组版本号</h2>
     * 1.1.4 发布时,频道版本写的是当时的样子 {@code "1.1.4"}(实际效果等同于把版本号塞进去)。
     * 如果 1.1.4.1 把频道版本改成 {@code "1.1.4.1"},NeoForge 会认为两端"注册的 payload 版本不同"
     * 而**直接拒绝连接** —— 但 1.1.4.1 相对 1.1.4 **只换了贴图**,线格式完全一致,本不该拦。
     *
     * <p>所以这里冻结为 {@code "1.1.4"}:凡是**不改变线格式**的更新(贴图、文案、配方、
     * 价格默认值)都保持这个值,新老版本可互通。
     *
     * <h2>什么时候必须提升它</h2>
     * 只要改动了<b>跨端传输的数据结构</b>,就必须提升,否则会出现"能连上但数据解析错乱":
     * <ul>
     *   <li>{@code QualityPricedWine} 数据组件的字段增删改(它随交易条目同步);</li>
     *   <li>{@link VersionCheckPayload} 本身的编解码;</li>
     *   <li>任何新增/删除的自定义 payload。</li>
     * </ul>
     */
    public static final String PROTOCOL_VERSION = "1.1.4";

    private ModNetwork() {
    }

    /** 在模组事件总线上注册(由主类调用)。 */
    public static void register(RegisterPayloadHandlersEvent event, String modVersion) {
        // versioned(PROTOCOL_VERSION):用**协议版本**而非模组版本,
        // 这样 1.1.4 <-> 1.1.4.1 这类"只改资源"的更新可以互通。
        // 不调用 optional():缺失或协议不符时**让连接直接失败**,这正是我们要的。
        PayloadRegistrar registrar = event.registrar("1").versioned(PROTOCOL_VERSION);

        KaleidoscopeWineMerchant.LOGGER.debug(
                "[WineMerchant] 网络注册:模组版本 {} / 协议版本 {}", modVersion, PROTOCOL_VERSION);

        // commonBidirectional:配置阶段与游戏阶段都注册,覆盖完整连接流程。
        // 处理器只记一行日志 —— 真正的校验由 NeoForge 在连接阶段完成。
        registrar.commonBidirectional(
                VersionCheckPayload.TYPE,
                VersionCheckPayload.STREAM_CODEC,
                (payload, context) -> KaleidoscopeWineMerchant.LOGGER.debug(
                        "[WineMerchant] 对方模组版本:{}", payload.version()));
    }
}
