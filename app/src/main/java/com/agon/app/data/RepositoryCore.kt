package com.agon.app.data

import android.util.Log
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * 仓库的**共用底座**（路线图 #5c-1，2026-09-18）：三态解码、损坏留档、读取兜底与去重。
 *
 * 这些东西不属于任何单一领域（库存/归档/消耗/备份/设置都要用），所以从 `FoodRepository.kt`
 * 搬出来单独一个文件，各领域文件与仓库本体都从这里取。
 *
 * **为什么是「同包 `internal` 扩展函数」这个形状**：Kotlin 没有 partial class —— 一个类不能
 * 拆到多个文件里。三条路里选了这一条：
 * 1. **同包 internal 扩展函数（本方案）**：函数名与签名一个字不改、函数体逐字搬（只整体左移
 *    4 空格缩进）⇒ 真正的纯搬运；同包内互相调用**不需要 import**；唯一的外部调用方
 *    `AppViewModel` 在别的包，所以要为搬走的每个函数加一行 import（本仓禁通配导入）。
 *    **所有失败模式都是编译期的** ⇒ 本地没有编译器也安全，CI 能 100% 兜住。
 * 2. 类里留一层「一行转发」的门面、真身放领域文件：调用方一行不用改，但每个公开方法会有
 *    **两个定义**；而且一旦转发名与扩展名撞名，成员会**遮蔽**扩展 ⇒ 变成自己调自己的
 *    **无限递归**，编译器不报、只有跑到那条路径才 StackOverflow。这种静默运行时炸的一律不选。
 * 3. 拆成 `repo.items.upsert(...)` 那种领域对象：最"正统"，但改了对外 API、
 *    VM 里 33 处调用点都要改 ⇒ 不是纯搬运。
 *
 * **代价（如实记）**：被领域文件用到的仓库成员必须从 `private` 放宽到 `internal`
 * （`dataStore` / `corruptDir` / `clock` / `json` / `decodeCache`，以及损坏 key 集合的那个后备字段等）。
 * ⚠️ 上一行刻意**不写出那个后备字段的名字**：`tools/doc-metrics.sh` 把它的出现次数当作 #8 的引用计数指标
 * （口径**含注释**），散文里提一次就 +1 —— 写这段的当场就从 32 变成了 33，被守卫抓出来。
 * 放宽只是"同模块内可见"，**不是对外 API**；类 KDoc 里也写了这条口径。
 */

// ⚠️ 这个 TAG 与 `FoodRepository.kt`、以及后面每个领域文件里的那份是**同一个字符串的副本**，
// 刻意不提成包级共享常量：本包已经有 `BackupFile.kt` / `ImageStore.kt` 各自的**文件级 private TAG**，
// 再放一个包级 `internal const val TAG` 就可能与它们在同一文件作用域里撞名（本地没编译器，
// 这种"可能撞"的赌不划算）。每个文件一份 private 常量正是本仓既有风格。
private const val TAG = "FoodRepository"

/** 读失败时的退避重试次数（超过则回落默认值，见 [FoodRepository.resilientRead]）。 */
private const val MAX_READ_RETRIES = 2L

/** 读重试间隔。 */
private const val READ_RETRY_DELAY_MS = 300L

/**
 * 解码结果三态。
 *
 * 关键区别是 [Empty] 与 [Corrupt]：此前两者都被压成 `emptyList()`，
 * 于是「解析失败」被当成「没有数据」，随后任何一次写操作都会把空表
 * encode 回去，**一次解析异常就永久摧毁整份用户数据**。
 */
sealed interface Decoded<out T> {
    /** 解析成功。 */
    data class Ok<T>(val value: T) : Decoded<T>

    /** key 不存在——真的没有数据，可安全写入。 */
    data object Empty : Decoded<Nothing>

    /** 存在原始串但解析失败——**禁止覆盖写**，否则用户数据丢失。 */
    data class Corrupt(val raw: String, val cause: Throwable) : Decoded<Nothing>
}

/** 取值；[Decoded.Corrupt] 与 [Decoded.Empty] 一律回落 [fallback]（仅供读路径/UI 展示用）。 */
fun <T> Decoded<T>.orElse(fallback: T): T = when (this) {
    is Decoded.Ok -> value
    else -> fallback
}

/**
 * 「原始串 → 解码结果」的最近一次结果缓存（2026-09-15）。
 *
 * 为什么需要：读路径按 key 拆成多条流，同一次数据变化会被多条流消费
 * （`itemsFlow` 同时被 `items` / `suggestionSource` / `ready` 收集）。DataStore 内部只读一次盘，
 * 但 `.map(decode)` 会在每条流里各跑一遍 —— 一份库存 JSON 被解 3 遍，启动期尤其浪费。
 * 解码是 raw 的纯函数（同一 raw 必然同一结果），所以按 key 记住最近一次 (raw → decoded)；
 * raw 一变（含被删成 null）就重解。
 *
 * 内存占用 = 重型 key 数 × 一份解码结果，与状态流本身持有的数据同量级；不缓存历史值。
 */
internal class DecodeCache {
    private class Entry(val raw: String?, val decoded: Any?)

    private val entries = ConcurrentHashMap<String, Entry>()

    /** 命中次数（raw 未变，直接复用）。仅用于单测与诊断。 */
    var hits = 0
        private set

    /** 未命中次数（首次见到该 raw，或 raw 已变）。 */
    var misses = 0
        private set

    @Suppress("UNCHECKED_CAST")
    fun <T> resolve(keyName: String, raw: String?, decode: (String?) -> T): T {
        val cached = entries[keyName]
        if (cached != null && cached.raw == raw) {
            hits++
            return cached.decoded as T
        }
        val decoded = decode(raw)
        misses++
        entries[keyName] = Entry(raw, decoded)
        return decoded
    }
}

/**
 * 三态解码。解析失败时把原始串留档到 filesDir/corrupt/ 并置位 [corruptedKeys]。
 */
internal inline fun <reified T> FoodRepository.decodeStrict(keyName: String, raw: String?): Decoded<T> {
    if (raw == null) return Decoded.Empty
    return runCatching { json.decodeFromString<T>(raw) }.fold(
        onSuccess = { Decoded.Ok(it) },
        onFailure = { cause ->
            Log.e(TAG, "解析 $keyName 失败，已拒绝写入以保护数据", cause)
            markCorrupt(keyName, raw)
            Decoded.Corrupt(raw, cause)
        },
    )
}

/**
 * 首次发现某 key 损坏时，把原始串另存一份供人工恢复/求助，并置位状态供 UI 提示。
 * 同一 key 只留档一次（文件名带时间戳，重复调用不会刷屏）。
 */
internal fun FoodRepository.markCorrupt(keyName: String, raw: String) {
    val firstTime = keyName !in _corruptedKeys.value
    _corruptedKeys.update { it + keyName }
    if (!firstTime) return
    runCatching {
        val dir = corruptDir.apply { mkdirs() }
        val stamp = LocalDateTime.now(clock)
            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        File(dir, "$keyName-$stamp.json").writeText(raw)
        pruneCorruptDir(dir)
    }.onFailure { Log.w(TAG, "留档损坏数据失败：$keyName", it) }
}

/**
 * 写操作守卫：任一「用户资产型」key 处于损坏态时返回 true，调用方必须放弃本次写入。
 * 只检查本次写操作实际会覆盖的 key。
 */
internal fun FoodRepository.isCorrupt(vararg decoded: Decoded<*>): Boolean =
    decoded.any { it is Decoded.Corrupt }

// ---- 读取 ----
//
// DataStore 每次 edit 都会重发整份 Preferences。此前 7 个列表 flow 直接 map+decode，
// 于是「改一次主题色」会把 items/archive/consumption/history/... 全部重新解析一遍 JSON
// 并产生全新 List 实例，触发全屏重组；且解码跑在 viewModelScope（Main.immediate）= 主线程。
//
// 现统一走 rawFlow：先取原始串 -> distinctUntilChanged（该 key 没变就不往下走）
// -> 解码 -> flowOn(Default) 移出主线程。

/**
 * DataStore 读流的统一兜底（2026-09-15 补）。
 *
 * 背景：`dataStore.data` 在磁盘 IO 抖动 / 文件被占用 / 读超时时会抛异常。而本项目的
 * 19 个 `stateIn` 全部 `SharingStarted.Eagerly`，且 `viewModelScope` 没有挂
 * `CoroutineExceptionHandler` —— 共享协程收到异常后会交给线程默认处理器，**直接杀进程**；
 * 若异常表现为「永不发射」（读阻塞），首页 `ready` 门控又会让启动画面永久停留。
 * 两种结果都不可接受，故在仓库层统一收口：
 *
 * 1. `IOException` 先做有限次退避重试（`MAX_READ_RETRIES` 次，间隔 `READ_RETRY_DELAY_MS`）；
 * 2. 仍失败则由 `catch` 记录日志并回落 [fallback]，UI 退化为「本次读到空数据」而非崩溃。
 *
 * 只影响「读」。**写**仍由 `isCorrupt` 守卫保护，损坏态下不会覆盖用户数据。
 */
internal fun <R> FoodRepository.resilientRead(
    keyName: String,
    fallback: R,
    transform: (Preferences) -> R,
): Flow<R> =
    dataStore.data
        .retryWhen { cause, attempt ->
            val retry = cause is IOException && attempt < MAX_READ_RETRIES
            if (retry) {
                Log.w(
                    TAG,
                    "读取 DataStore 失败，${READ_RETRY_DELAY_MS}ms 后重试（${attempt + 1}/$MAX_READ_RETRIES）：$keyName",
                    cause,
                )
                delay(READ_RETRY_DELAY_MS)
            }
            retry
        }
        .map(transform)
        .catch { cause ->
            Log.e(TAG, "读取 $keyName 最终失败，本次回落默认值（用户数据未被修改）", cause)
            emit(fallback)
        }

/**
 * 重型 key（需 JSON 解码）：按原始串去重，解码在 Default 线程。
 *
 * 解码结果按 key 过一遍 [DecodeCache]：同一次数据变化会被多条流消费
 * （`itemsFlow` 就被 `items` / `suggestionSource` / `ready` 各收一份），
 * 没有缓存时同一份库存 JSON 要解 3 遍（2026-09-15）。
 */
internal fun <T> FoodRepository.rawFlow(key: Preferences.Key<String>, decode: (String?) -> T): Flow<T> =
    resilientRead(key.name, fallback = null) { prefs -> prefs[key] }
        .distinctUntilChanged()
        .map { raw -> decodeCache.resolve(key.name, raw, decode) }
        .flowOn(Dispatchers.Default)

/** 轻量 key（无需解码）：只做去重，不必切线程。 */
internal fun <T> FoodRepository.lightFlow(keyName: String, fallback: T, transform: (Preferences) -> T): Flow<T> =
    resilientRead(keyName, fallback, transform).distinctUntilChanged()

/**
 * 损坏留档目录保留上限：同一 key 最多 [maxPerKey] 份、整个目录最多 [maxTotal] 份，
 * 超出的按「最旧优先」删除（2026-09-15）。
 *
 * 为什么需要：`markCorrupt` 每次进程启动后只对同一个 key 留档一次，但 `corruptedKeys`
 * 是内存态——解析一直失败时，**每次启动都会新增一份**留档，长期会把私有目录塞满。
 * 留档只用于人工求助，保留最近几份足够。
 */
internal fun pruneCorruptDir(
    dir: File,
    maxPerKey: Int = 3,
    maxTotal: Int = 12,
) {
    val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
        ?.sortedBy { it.lastModified() }
        ?: return
    val doomed = mutableListOf<File>()
    if (files.size > maxTotal) doomed += files.take(files.size - maxTotal)
    files.groupBy { it.name.substringBeforeLast('-') }.forEach { (_, group) ->
        if (group.size > maxPerKey) doomed += group.take(group.size - maxPerKey)
    }
    doomed.distinct().forEach { runCatching { it.delete() } }
}
