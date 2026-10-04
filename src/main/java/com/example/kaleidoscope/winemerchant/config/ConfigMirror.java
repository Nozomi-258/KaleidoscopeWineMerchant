package com.example.kaleidoscope.winemerchant.config;

import com.example.kaleidoscope.winemerchant.KaleidoscopeWineMerchant;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 直接从配置文件读值的小工具。
 *
 * <h2>为什么需要它 —— 一个真实的时序 bug</h2>
 * {@code VillagerTradesEvent}(村民交易表生成)触发得**比服务端配置加载更早**。
 * 实测日志:
 * <pre>
 *   23:33:25.974  交易生成     ← 读取配置,但此时还没加载
 *   23:33:26.172  TOML 才加载完成   ← 晚了约 0.2 秒
 * </pre>
 * 因此 {@code ModConfigSpec.ConfigValue.get()} 会抛 {@code IllegalStateException}。
 * 早期版本用 try/catch 吞掉异常、回退到内置默认值 —— 那样确实不会崩,
 * 但**读到的全是默认值**:玩家在配置里关掉了饮料,交易表里却照旧给饮料生成交易。
 *
 * <p>这个类在交易生成阶段**直接解析 TOML 文件**。文件是配置的唯一事实来源,
 * 且不依赖 NeoForge 的加载顺序,所以任何时刻读都是对的。
 *
 * <h2>支持的语法</h2>
 * 只覆盖本模组自己生成的配置文件,格式固定为:
 * <pre>
 *   [section]
 *       [section.sub]
 *           key = true
 *           key = 3
 *           key = ["a", "b"]
 * </pre>
 * 不处理行内表、多行字符串、日期等 —— 本模组的配置里没有这些。
 */
public final class ConfigMirror {

    /**
     * 已解析的键值对。
     *
     * <h2>为什么是 volatile + 整体替换,而不是普通 HashMap</h2>
     * {@link #load} 会在<b>多个线程</b>上被调用:
     * <ul>
     *   <li>模组构造时(mod 加载线程)</li>
     *   <li>{@code ModConfigEvent.Loading / Reloading}(配置事件线程、FileWatcher 线程)</li>
     *   <li>{@code /wmc refresh}(服务端主线程)</li>
     * </ul>
     * 而读取发生在服务端主线程(交易生成)与<b>客户端渲染线程</b>(Mixin 里的
     * {@code appraisalFactor})。
     *
     * <p>早先用的是普通 {@code HashMap},load() 里先 {@code clear()} 再逐条 put ——
     * 并发读会看到空表(价格静默回退到默认值),极端情况下还会抛
     * {@code ConcurrentModificationException}。
     *
     * <p>现在改为<b>先建好新表,再原子替换引用</b>:读线程要么看到完整的旧表,
     * 要么看到完整的新表,绝不会看到"清空到一半"的中间状态。
     */
    private volatile Map<String, String> values = Map.of();

    private volatile boolean loaded;

    /**
     * 读取配置文件。
     *
     * @param fileName 配置文件名(例如 {@code kaleidoscope_wine_merchant-server.toml})
     * @return 是否成功读到(文件不存在时返回 false,调用方应回退到内置默认值)
     */
    public boolean load(String fileName) {
        Path file = FMLPaths.CONFIGDIR.get().resolve(fileName);
        if (!Files.isRegularFile(file)) {
            // 文件不存在:保留旧快照(若有),不动 loaded 标志
            return false;
        }
        // 先建好新表,最后原子替换 —— 读线程绝不会看到"清空到一半"的状态
        Map<String, String> parsed = new HashMap<>();
        try {
            String section = "";
            for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("[")) {
                    int end = line.indexOf(']');
                    if (end > 1) {
                        section = line.substring(1, end).trim();
                    }
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim();
                String value = stripInlineComment(line.substring(eq + 1).trim());
                parsed.put(section + "." + key, value);
            }
        } catch (IOException e) {
            KaleidoscopeWineMerchant.LOGGER.warn(
                    "[WineMerchant] 直接读取配置文件失败,将回退到内置默认值: {}", e.toString());
            return false;
        }
        // 只要读到任何一条有效键就算加载成功。
        // (早先写成 > 1,若配置恰好只有 1 个键会被误判为"未加载";
        //  本模组实际有数百个键,但 > 0 才是正确的语义。)
        if (parsed.isEmpty()) {
            return false;
        }
        values = Map.copyOf(parsed);   // ← 原子发布
        loaded = true;
        return true;
    }

    /** 去掉行尾的 # 注释(值里不会出现 #,所以简单切分即可)。 */
    private static String stripInlineComment(String v) {
        int hash = v.indexOf('#');
        return (hash >= 0 ? v.substring(0, hash) : v).trim();
    }

    public boolean isLoaded() {
        return loaded;
    }

    /** 取布尔值;取不到时返回 fallback。 */
    public boolean bool(String path, boolean fallback) {
        String v = values.get(path);
        if (v == null) {
            return fallback;
        }
        return "true".equalsIgnoreCase(v);
    }

    /** 取整数值;取不到或解析失败时返回 fallback。 */
    public int integer(String path, int fallback) {
        String v = values.get(path);
        if (v == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** 取浮点值;取不到或解析失败时返回 fallback。 */
    public double decimal(String path, double fallback) {
        String v = values.get(path);
        if (v == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** 取字符串(已去掉引号);取不到时返回 fallback。 */
    public String string(String path, String fallback) {
        String v = values.get(path);
        if (v == null) {
            return fallback;
        }
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }

    /** 调试用:当前读到的条目数。 */
    public int size() {
        return values.size();
    }
}
