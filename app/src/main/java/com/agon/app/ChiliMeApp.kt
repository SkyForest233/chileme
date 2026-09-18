package com.agon.app

import android.app.Application
import com.agon.app.data.FoodRepository
import java.time.Clock

/**
 * 轻量依赖容器（路线图 **#5a**，2026-09-18）。
 *
 * **为什么手写而不用 Hilt / Koin**（用户在规划时定的口径：轻量、手写）：
 * 这个 App 目前只有**一个**需要注入的东西（仓库），加上 #5b 的时钟也就两个。
 * 为两个依赖引入注解处理器，代价是编译期多一轮 KSP/KAPT、构建时间变长、
 * 以及"依赖图由框架在运行时拼"这件在单测里更难看清的事 —— 收益却只是省掉下面这十几行。
 * 手写容器的另一个好处是**构造点可数**：`tools/doc-metrics.sh` 有一条守卫直接断言
 * "仓库的现场构造只允许出现在本文件"（改造前它在 `AppViewModel` 里，谁都能再 new 一个）。
 *
 * @param context 刻意收成 [Application] 而不是 [Context]：容器是**进程级**的，
 *   里面装的东西活得和进程一样久。若参数类型是 `Context`，某天有人把一个 Activity 传进来
 *   就会被容器一直握着 ⇒ 整个界面泄漏。收成 `Application` 让这种误用在编译期就写不出来。
 */
class AppContainer(context: Application) {

    /**
     * 全 App 唯一的时钟。#5a 先把它放进容器，**#5b** 再把数据层与 VM 里那 12 处
     * `LocalDate` / `LocalDateTime` 的 `now()` 硬调接到它上面（届时仓库构造会收这个参数）。
     *
     * ⚠️ 上一行刻意**不把那两个工厂写成「类名点 now()」的连写形式**：`tools/doc-metrics.sh`
     * 用同一个正则数**真实调用点**（口径含注释），散文里连写一次就把计数撑大一次 ——
     * 写这段的当天「now() 直接调用」就从 34 变成了 36。别"顺手改回更自然的写法"。
     *
     * 单测里换成固定时钟（`Clock.fixed(...)`）就能测「跨零点」这类行为 —— 生产路径用的是
     * 系统时钟，与今天的取值逐位相同。
     */
    val clock: Clock = Clock.systemDefaultZone()

    /**
     * 全 App 唯一的仓库实例。
     *
     * `by lazy` 而不是直接构造：`FoodRepository(context)` 会碰到 DataStore 的委托属性，
     * 放在进程启动那一刻做属于白白的冷启动开销；等到第一个 `AppViewModel` 真正需要它时再建。
     *
     * **共享一个实例比改造前更安全**：改造前每次构造 `AppViewModel` 都会 `FoodRepository(application)`
     * 现场 new 一个，各自持有独立的「损坏 key 集合」状态与解码缓存（就是仓库里那两个实例字段；
     * 这里刻意不写出字段名，理由同上：它被 `doc-metrics.sh` 当作 #8 的引用计数指标，散文提一次就 +1）——
     * 真出现两个 VM 实例时，一个看到的损坏状态另一个看不到。现在全进程一份，不存在这种分叉。
     */
    val repo: FoodRepository by lazy { FoodRepository(context) }
}

/**
 * `Application` 子类，只干一件事：持有 [AppContainer]（路线图 #5a，本仓第一个 `Application` 子类）。
 *
 * 挂在 `AndroidManifest.xml` 的 `<application android:name=".ChiliMeApp">` 上。
 *
 * **为什么容器用 `by lazy` 而不是「`onCreate` 里赋值 + `lateinit var`」那个常见写法**：
 * `ContentProvider.onCreate()` 的执行时机**早于** `Application.onCreate()`（本仓就注册了一个
 * `FileProvider`）。用 `lateinit` 的话，任何在 provider 里（或它拉起的东西里）取容器的代码
 * 都会撞上 `UninitializedPropertyAccessException` —— 而这种崩溃只在真机上出现、还离原因很远。
 * `by lazy` 把"必须先 onCreate"这个时序假设整个去掉：谁先要，谁触发构造。
 * 附带的好处是没有可写的 `var` 暴露出去，容器一旦建好就换不掉。
 */
class ChiliMeApp : Application() {

    /** App 级容器。VM 通过 `(application as ChiliMeApp).container` 取用。 */
    val container: AppContainer by lazy { AppContainer(this) }
}
