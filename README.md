# 森罗物语：酒商 · Kaleidoscope Wine Merchant

> 为 Minecraft **1.21.1 / NeoForge** 制作的「森罗物语」附属模组。
> 新增一位 **「酒商」村民职业** —— 他按**酿造品质**收购酒。
> 本模组虽然代码大部分由AI编写，但是能够保证根据反馈持续维护（目前主要是neoforge 1.21.1单个版本）

**最新版本 1.2.0** · 需要 [森罗物语:酒馆](https://modrinth.com/mod/kaleidoscopetavern)

![酒商外观：旧版 / 新版](https://raw.githubusercontent.com/Nozomi-258/KaleidoscopeWineMerchant/main/docs/images/appearance-before-after-1200.jpg)

---

## 关于这个仓库 / About this repository

**这里是模组的完整源码仓库,同时也是问题反馈入口。**
>（以下构建说明主要由AI编写，请谨慎判断）

仓库内容包括:

- **完整模组源码** —— `src/main/java/`(21 个 `.java`)+ `src/main/resources/`(语言文件、贴图、配方、`neoforge.mods.toml`)
- **构建脚本与 Gradle 包装器** —— `build.gradle`、`settings.gradle`、`gradle.properties`、`gradlew`、`gradlew.bat`、`gradle/wrapper/`
- **一个维护用的小工具** —— `tools/audit_item_groups.py`(用途见下面「仓库里没有什么」一节)
- **4 个 Mixin** —— 负责定价、`/wmc refresh` 补齐、升级补满与升级去重(见下面「Mixin」一节)
- 问题反馈模板(`.github/ISSUE_TEMPLATE/`)
- 许可证(`LICENSE`)与这份说明

**所以这个仓库是可以克隆下来自己构建的** —— 见下面「从源码构建」。

想报 bug、提建议、问问题,直接开 issue 就行:**[→ 新建 Issue](../../issues/new/choose)**

> **English**: This repository contains the **full source code** of the mod, and it doubles as
> the issue tracker. You can clone it and build it yourself — see "Building from source"
> below. For bug reports, feature requests and questions, please open an
> [issue](../../issues/new/choose). English is welcome.

## 从源码构建 / Building from source

### 前置要求

| 需要 | 要求 |
|---|---|
| **JDK** | **21**(Mojang 从 Minecraft 1.20.5 起要求 Java 21;本模组目标 1.21.1) |
| **网络** | 首次构建**必须联网**,要下载 Gradle 发行包、NeoForge 与 Minecraft 依赖 |
| 操作系统 | 任意平台都可构建;本文的验证是在 Windows 上做的 |

**JDK 21 怎么被找到:** 设置环境变量 `JAVA_HOME` 指向 JDK 21 即可 —— **不需要**修改 `gradle.properties`,更不需要写死任何本机路径。如果 `JAVA_HOME` 没设或指向了别的版本,`settings.gradle` 里的 `foojay-resolver-convention` 插件会尝试**自动下载**一个 JDK 21。

### 构建命令

```bash
# Windows
gradlew.bat build

# Linux / macOS
./gradlew build
```

构建使用 Gradle 包装器(wrapper),**不需要**自己安装 Gradle。

**产物:**

```
build/libs/kaleidoscope_wine_merchant-1.2.0.jar
```

把这个 jar 放进 `.minecraft/mods/` 即可(客户端与服务端都要放)。

> ⚠️ **首次构建会下载很多东西,请留足时间。**
> 包含 Gradle 发行包(约 130 MB)+ NeoForge + Minecraft 依赖,
> 视网络情况需要**十几分钟到半小时**;国内网络直连可能很慢甚至超时。
> 如果长时间卡在下载,可以:
> - 给 Gradle 配代理(在 `GRADLE_USER_HOME` 下的 `gradle.properties` 里加
>   `systemProp.http.proxyHost` / `proxyPort`、`systemProp.https.proxyHost` / `proxyPort`),或
> - 在 `build.gradle` 的 `repositories` 里加国内镜像。
>
> 第二次构建会走缓存,快得多。

### 关于 `libs/`(可选,不需要)

本模组通过**物品 ID** 与前置模组交互,**不在代码里引用前置模组的类**,所以:

**`libs/` 目录不存在、或者里面什么都没有,照样能构建成功。**

`build.gradle` 里那两行 `fileTree(dir: 'libs', ...)` 是给本地做编译期校验用的。如果你想放,把前置模组的 jar 丢进 `libs/` 即可;但**这些第三方 jar 不要提交到仓库**(`.gitignore` 已经忽略 `libs/`)。

### 验证状态(如实说明)

- ✅ **1.2.0 已在作者本机构建成功**:Windows 11 + JDK 21.0.12.1 + Gradle 8.10,`BUILD SUCCESSFUL`,产出 `kaleidoscope_wine_merchant-1.2.0.jar`。
- ✅ **本仓库的源码与 1.2.0 源码逐文件一致**(2026-10-26):对 `src/` 做了 **sha256 逐文件比对**,**39/39 个文件**(21 个 `.java` + 18 个资源文件)全部相同;`kaleidoscope_wine_merchant.mixins.json` 里注册的 **4 个 Mixin** 与源码里的 4 个 Mixin 类**一一对应**(这正是 1.1.2 启动崩溃的成因,见下面「Mixin」一节)。
- ✅ **Gradle 包装器已验证**:`gradlew` 会按 `gradle/wrapper/gradle-wrapper.properties` 下载 **Gradle 8.10**,实测下载与校验均正常。
- ✅ **历史上做过一次「干净克隆」验证**(2026-10-04,当时是 1.1.0):把仓库里要上传的文件复制到一个全新目录(排除 `local/`、`build/`、`.gradle/`、`runs/`),用**仓库自带的 `gradlew.bat build`**(而不是本机 Gradle)构建 → **BUILD SUCCESSFUL**,产物与作者发布 jar 的 **70/70 个 zip 条目逐字节一致**(整体 SHA256 不同仅因 zip 内嵌时间戳)。
- ⚠️ **「完全冷缓存」的真实耗时仍未实测。** `GRADLE_USER_HOME` 里已有 NeoForge / Minecraft 依赖,干净克隆那次构建用了约 2分40秒(暖缓存)。**首次从零下载 Gradle + NeoForge + Minecraft 依赖的耗时是估计值**(上面写的「十几分钟到半小时」),且**国内网络可能很慢或超时**。
- ⚠️ **本次(1.2.0)只做了文件级校验,没有在干净克隆里重新跑一遍构建。** 上面那条「39/39 一致」是 sha256 比对,证明仓库里的源码就是作者构建过的 1.2.0 源码,但**不等于**在一台全新机器上重新构建过。
- ⚠️ **1.2.0 的「升级稳定新增 2 条交易」修复尚未经玩家实机反馈**,有结果会更新在这里。定价与 `/wmc refresh` 在 1.1.3 及更早版本是实机实测过的。
- ⚠️ **只在 Windows 上验证过构建**,Linux / macOS 未实测(`gradlew` 已按 LF 换行提交,理论上可用)。

### 仓库里没有什么

- **`tools/` 里只有一个文件** —— `tools/audit_item_groups.py`,一个**维护用**的分类一致性审计脚本:它把 `WineGroups.java` 里每种酒的「有无品质 / 原料份数」与前置模组 jar 里的真实配方逐项对照,前置模组更新、加了新酒之后跑一遍,就能确认分类没漏、原料数没写错(详细说明写在脚本头部)。
  - 它**不含任何本机绝对路径**:模组目录用 `--mods "<你的 .minecraft/mods>"` 或环境变量 `KWM_MODS_DIR` 指定,`WineGroups.java` 自动从仓库里定位;只依赖 Python 标准库。
  - 用法:`python tools/audit_item_groups.py --mods "<你的 .minecraft/mods>"`(加 `--strict` 则在发现差异时返回非 0,可接进 CI)。
  - 作者本机其余的一次性辅助脚本(语言文件生成、贴图渲染、图标同步、UV 分析等)**没有**包含 —— 它们依赖本机绝对路径与本地游戏实例,不是构建所需内容。
- **没有**前置模组的 jar(`libs/`)、构建产物(`build/`)、运行目录(`run/`、`runs/`)—— 都已被 `.gitignore` 忽略。

## 下载 / Download

模组发布在 **CurseForge**:

**<https://www.curseforge.com/minecraft/mc-mods/kaleidoscope-wine-merchant>**

**Modrinth** 那边还在审核中,通过后即可访问:

**<https://modrinth.com/mod/kaleidoscope-wine-merchant>**

> 💡 **现在请从 CurseForge 下载。** Modrinth 项目正在审核,点开可能显示 404。

安装方式:把 jar 放进 `.minecraft/mods/`,并确认下面的**必需前置模组**都已安装。
**客户端与服务端都要安装。**

## 这个模组做什么

- 🍷 **按品质定价**:同一瓶酒,品质越高给的绿宝石越多(六个品质档位,可逐项配置)
- 🧾 **66 种可交易物品**:40 种酒 + 20 种鸡尾酒 + 6 种饮料,全部可在配置里开关和改价
- ⚙️ **全部可配置**:价格、池子构成、黑名单、醋的批量兑换、酒类分档、等级识货
- 🎮 **游戏内 OP 指令**:`/wmc` 系列,改完立刻生效,不用重启
- 🧱 **酒商柜台方块**:加入酒馆物品栏,工作台挖掘手感
- 🎨 **自绘外观**:酒商与僵尸酒商都是中世纪商人造型(蓝软帽金边、棕袍金饰、腰带挎包)

## 玩法

1. 合成 **酒商柜台**(8 个木板围 1 个木桶,与原版箱子/木桶的配方逻辑一致)
2. 把柜台放在村里,附近**没有职业**的村民会转职成 **酒商**
3. 右键酒商即可用酒换绿宝石

## 品质与定价

模组读取「森罗物语:酒馆」的 `brew_level` 数据组件来判断品质:

| 档位 | 品质 | 默认价(葡萄酒) |
|---|---|---|
| q1 | 难以下咽 | 不收 |
| q2 | 劣质 | 1 |
| q3 | 普通 | 1 |
| q4 | 优质 | 2 |
| q5 | 精酿 | 2 |
| q6 | 典藏 | 3 |

**定价规则**:1 种原料的酒用基准价(0/1/1/2/2/3);**每多 1 种原料,六档全部 +1**。

**想让某种酒统一价格?** 把六档填成**同一个数字**即可(填 `0` = 不收该品质)。

## 指令(需要 OP / 权限等级 2)

```
/wmc                                 列出全部子指令
/wmc info                            查看配置摘要
/wmc price <物品> <品质1-6> <价格>    改某档品质的收购价(0 = 不收)
/wmc enable|disable <物品>           开关某种物品是否收购
/wmc sell|buy <鸡尾酒> <true|false>  控制鸡尾酒的买卖方向
/wmc blacklist add|remove|list       管理黑名单
/wmc reload                          把配置重新读进内存
/wmc refresh [半径]                  按当前配置重建交易表并刷新附近酒商
```

物品 ID **不需要引号**,可以写完整 ID,也可以只用短名:

```
/wmc price kaleidoscope_tavern:wine 3 5
/wmc price wine 3 5
```

**改完价格或开关后执行 `/wmc refresh`,改动立即生效,不需要重启游戏。**

## 依赖

| 模组 | 要求 | 说明 |
|---|---|---|
| NeoForge | `[21.1.219, 21.2)` | 必需 |
| Minecraft | `[1.21.1, 1.21.2)` | 必需 |
| [森罗物语:酒馆](https://modrinth.com/mod/kaleidoscopetavern) | `1.2.0+` | **必需** —— 提供品质组件与酒类物品 |
| [森罗物语:世界酒](https://modrinth.com/mod/kaleidoscope-world-liquor) | `1.1.8+` | 可选 —— 多 26 种酒 |
| [森罗物语:厨房](https://modrinth.com/mod/kaleidoscope-cookery) | `1.4.1+` | 可选 —— 代码里并未引用 |

> （以上模组在CurseForge上也能找到）

**客户端与服务端都需要安装。**

> 前置模组本身的问题(崩溃、贴图、玩法)请到前置模组自己的页面反馈,
> 这里只处理「酒商」这个附属模组的问题。

## 仓库结构 / Repository layout

```
src/main/java/com/example/kaleidoscope/winemerchant/   模组源码(21 个 .java)
src/main/resources/                                    资源:语言文件、贴图、配方、neoforge.mods.toml
tools/audit_item_groups.py                             分类一致性审计脚本(维护用,非构建所需)
build.gradle                                           构建脚本(NeoForge 21.1.252)
settings.gradle                                        插件仓库 + foojay JDK 21 自动解析
gradle.properties                                      版本号与模组元数据(不含本机路径)
gradlew / gradlew.bat / gradle/wrapper/                Gradle 包装器(Gradle 8.10)
docs/images/                                           说明与更新日志里用到的图片
.github/ISSUE_TEMPLATE/                                问题反馈模板
LICENSE                                                许可证(MIT)
```

> `local/` 是作者本机的工作文档,已被 `.gitignore` 忽略,不会出现在 GitHub 上。

## Mixin 与一条必须遵守的约束 / Mixins

本模组用 **4 个 Mixin** 做原版事件做不到的事:

| Mixin 类 | 目标类 | 作用 |
|---|---|---|
| `mixin/MerchantContainerMixin` | `MerchantContainer` | **按品质定价**:`@Redirect` 掉 `MerchantOffer.assemble()` 的返回值,在产出进入结果槽之前按玩家实际放入那件酒的品质改数量 |
| `mixin/VillagerTradeInvoker` | `AbstractVillager` | 用 `@Invoker` 暴露原版 `protected` 的 `addOffersFromItemListings(...)`,让 `/wmc refresh` 之后能**补齐**已有村民的交易条数(不再越 refresh 越少) |
| `mixin/AbstractVillagerTradeMixin` | `AbstractVillager` | **升级补满**:`@Inject` 到 `addOffersFromItemListings` 的 HEAD,抽到与已有交易重复的就丢弃重抽,保证每级稳定新增 **2 条**(1.2.0 修复) |
| `mixin/VillagerTradeDedupeMixin` | `Villager` | **升级去重(安全网)**:`@Inject` 到 `increaseMerchantCareer` 的 TAIL,兜底去掉重复的收购交易 |

四个类都注册在 `src/main/resources/kaleidoscope_wine_merchant.mixins.json` 的 `mixins` 数组里,
**这个列表必须与源码里的 Mixin 类一一对应**:漏注册 → 功能静默失效;注册了不存在的类 → 启动即崩。

### ⚠️ 铁律:`@Mixin` 类里不能有非 `private` 的 `static` 方法

> **`@Mixin` 类中,除注入器方法(`@Inject` / `@Redirect` / `@ModifyVariable` 等)、
> `@Shadow`、`@Invoker`、`@Accessor` 之外,其他成员必须是 `private static`。**

`public static` / `protected static` 会被 Mixin 框架直接拒绝,后果是**游戏在启动阶段崩溃**,
而且**编译期完全发现不了**(`javac` 不报错、`build` 也能成功):

```
[FATAL] [mixin/]: Mixin apply for mod kaleidoscope_wine_merchant failed
org.spongepowered.asm.mixin.transformer.throwables.InvalidMixinException:
  Mixin ... contains non-private static method dedupeBuyOffers(...)
  at MixinApplicatorStandard.checkMethodVisibility
```

**1.1.2的某一个内测版本就是栽在这上面** —— 升级去重的辅助方法一开始写在了 Mixin 类里。1.1.3 的修法是:
**共享逻辑放进普通类**,Mixin 只留注入器并调用它 —— 去重逻辑现在在 `trade/TradeDedupe.java`,
`VillagerTradeDedupeMixin` 与 `AbstractVillagerTradeMixin` 都只负责调用。

**改完 Mixin 请自检**(构建后跑一次,能提前发现同类问题):

```bash
javap -p -cp build/libs/kaleidoscope_wine_merchant-1.2.0.jar \
  com.example.kaleidoscope.winemerchant.mixin.VillagerTradeDedupeMixin
```

输出里**不应出现任何 `static` 方法**(构造器除外)。

> **English**: This mod ships **4 Mixins** (quality-based pricing, an `@Invoker` exposing a
> vanilla `protected` method, a level-up top-up that hands out exactly 2 new trades per
> level, and trade de-duplication as a safety net). **Never put a non-`private static`
> method in a `@Mixin` class** — Mixin rejects it and the game crashes at startup, while
> compilation stays green. Keep shared helpers in normal classes
> (see `trade/TradeDedupe.java`).

## 反馈 / Feedback

**遇到问题请到 [Issues](../../issues) 反馈。** 新建 issue 时可以挑一个模板:

| 模板 | 什么时候用 |
|---|---|
| 🐛 **报告问题** | 报错、崩溃、行为不符合预期 |
| 💡 **功能建议** | 想要新物品、新定价方式、新开关 |
| ❓ **使用问题** | 不会用、配置看不懂、不确定是不是 bug |

**提 issue 前请确认:**

1. 用的是**最新版本**(旧版本的问题可能已经修好了)
2. 关掉游戏后**重新启动**过(NeoForge 只在启动时加载模组,退回主菜单不生效)
3. 在 [Issues](../../issues?q=is%3Aissue) 里搜索过,没有重复
4. 前置模组都装齐了(见上面「依赖」一节)

**可以的话报 bug 时请附上日志** —— 文件在 `.minecraft/logs/latest.log`,
搜索 `[WineMerchant]` 开头的行贴进来即可;如果游戏崩溃,再附上 `crash-reports` 里的文件。

**English is welcome.** You can write your issue in English.

## 更新日志 / Changelog

- **1.2.0** —— 酒商与僵尸酒商换新外观;修复「升级时只新增 1 条交易」(1.1.3 / 1.1.4 遗留问题)
- 更早版本见 [Releases](../../releases)

## 许可

[MIT](LICENSE) © 2026 Nozomi258

本模组是「森罗物语」的**非官方附属模组**,不包含前置模组的任何素材。
