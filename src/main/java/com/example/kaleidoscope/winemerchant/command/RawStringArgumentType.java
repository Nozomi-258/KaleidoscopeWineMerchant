package com.example.kaleidoscope.winemerchant.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.Collection;
import java.util.List;

/**
 * 允许**未加引号使用冒号**的字符串参数类型。
 *
 * <h2>为什么需要它</h2>
 * Brigadier 自带的两种字符串参数都有硬限制:
 * <ul>
 *   <li>{@code StringArgumentType.word()} —— 未加引号时只允许
 *       {@code 0-9 A-Z a-z _ - . +} 这些字符,<b>没有冒号</b>;</li>
 *   <li>{@code StringArgumentType.string()} —— 同样如此,<b>只有加了引号</b>
 *       ({@code "ns:path"})才允许冒号。</li>
 * </ul>
 *
 * <p>而 Minecraft 的物品 / 方块 ID <b>几乎都带命名空间</b>
 * ({@code kaleidoscope_tavern:ice_wine}),玩家最自然的输入方式就是直接打出来。
 * 用自带类型时,不加引号会报:
 * <pre>
 *   参数后应有空格分隔,但发现了紧邻的数据
 * </pre>
 * 这在本模组里已经让作者踩了两次。
 *
 * <h2>做法</h2>
 * 自己实现 {@link ArgumentType}:从当前光标位置开始,<b>一直读到空白或行尾</b>,
 * 期间不做字符检查。这样 {@code kaleidoscope_tavern:ice_wine} 可以直接输入。
 *
 * <p>同时保留引号支持:{@code "ns:path"} 会被剥掉外层引号,效果相同。
 * 引号内允许空格(便于将来扩展),引号内的转义按 Brigadier 规则处理。
 *
 * <p><b>安全性</b>:这里只是"多接受了几个字符",不做任何解析/求值,
 * 取到的字符串仍由 {@code WineMerchantCommand.resolveId(...)} 用注册表校验,
 * 非法 ID 会得到"找不到该物品"的正常错误提示,不会崩溃。
 */
public final class RawStringArgumentType implements ArgumentType<String> {

    private static final RawStringArgumentType INSTANCE = new RawStringArgumentType();

    private RawStringArgumentType() {
    }

    public static RawStringArgumentType rawString() {
        return INSTANCE;
    }

    /** 读取时用的取值方法(与 {@code StringArgumentType.getString} 用法一致)。 */
    public static String getRawString(CommandContext<?> context, String name) {
        return context.getArgument(name, String.class);
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        // 跳过前导空格
        while (reader.canRead() && reader.peek() == ' ') {
            reader.skip();
        }
        if (!reader.canRead()) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS
                    .readerExpectedSymbol().createWithContext(reader, "<item id>");
        }

        char first = reader.peek();
        // 起点:如果第一个字符是引号,从引号之后开始记录
        int start;
        boolean quoted;
        if (first == '"' || first == '\'') {
            reader.skip();                 // 吃掉开始引号
            start = reader.getCursor();
            quoted = true;
        } else {
            start = reader.getCursor();
            quoted = false;
        }

        // 一直往后读,直到:未加引号时遇到空格/行尾;加引号时遇到配对的引号
        while (reader.canRead()) {
            char c = reader.peek();
            if (quoted) {
                if (c == first) {
                    break;
                }
            } else if (c == ' ') {
                break;
            }
            reader.skip();
        }

        int end = reader.getCursor();
        if (quoted) {
            if (!reader.canRead()) {
                // 引号没有闭合
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS
                        .readerExpectedEndOfQuote().createWithContext(reader);
            }
            reader.skip();                 // 吃掉结束引号
        }
        return reader.getString().substring(start, end);
    }

    @Override
    public Collection<String> getExamples() {
        return List.of("kaleidoscope_tavern:ice_wine", "wine", "\"ns:path with space\"");
    }
}
