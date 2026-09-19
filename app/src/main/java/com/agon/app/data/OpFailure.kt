/**
 * 云端同步失败的**分类**与那句 401 文案（09-19 #11c 从 `CloudSync.kt` 拆出）。
 *
 * 为什么单独一个文件：这三样是一件事——「一次同步失败怎么变成用户看得见的话」。
 * 它们与 WebDAV 请求怎么发、PROPFIND 怎么解析毫无关系，而 `toOpFailure` 认「凭据错」靠的正是
 * `NUTSTORE_AUTH_MESSAGE` 这个常量：抛出点与分类点必须引用同一个符号，
 * 一旦有人把文案抄成第二份，分类会**静默失效**（编译与测试都不报，只是所有 401 从此变成 Other）。
 */
package com.agon.app.data

import java.io.IOException

/**
 * 401 时给用户看的那句话。
 *
 * 抽成常量**不是为了少写几个字**：[toOpFailure] 要靠它把「凭据错」从其它失败里认出来。
 * 同一句话若在抛出点与分类点各写一遍，改一处忘一处就会**静默失去分类**（编译不报错、测试不报错，
 * 只是某天所有凭据错都变成 [OpFailure.Other]）。
 */
const val NUTSTORE_AUTH_MESSAGE = "账号或应用密码错误"

/**
 * 坚果云同步失败的**分类**（路线图 #4c，2026-09-18）。
 *
 * 改造前是什么样：`NutstoreSync` 的三个方法都返回 `Result`，但 VM 一句 `it.message ?: "上传失败"`
 * 就把它压平成 `(Boolean, String)` 交给界面 —— 类型信息全丢，界面只能"有字就显示"。后果有两个：
 * 1. **UI 无法按失败种类做事**：凭据错本该引导用户重填账号密码，网络错本该建议稍后重试，
 *    但两者到手都是同一个 `String`；
 * 2. 网络类异常（OkHttp 的 [IOException]：DNS 解析失败、连接超时…）的 `message` 是**英文技术串**，
 *    被原样甩给用户。
 *
 * ⚠️ **本轮只加类型、不改一个字的文案** —— 包括上面那个英文串照旧透出。
 * 改文案是用户可见的行为变更，按本仓规矩要单独提交 + 真机复测，已登记为待用户决策项
 * （见 `devlog/2026-09-18.md`）。所以 [message] 与改造前逐字相同，`OpFailureTest` 钉住这一点。
 *
 * 只有三类，不是四类：HTTP 状态码类失败（`上传失败（HTTP 507）`）**没有**单列一档，
 * 因为状态码本来就在文案里、用户看得见，而 UI 目前对 507 与 500 没有任何不同处理 ——
 * 为一档没人区分的情况加一个类型，只是让 `when` 多一个分支。真要按状态码分流时再加。
 */
sealed interface OpFailure {
    /** 给用户看的话（与改造前逐字一致）。 */
    val message: String

    /** 401：账号或应用密码不对。重试没用，得改凭据。 */
    data class Auth(override val message: String) : OpFailure

    /** 网络层失败（DNS / 超时 / 连接中断）：稍后重试有意义。 */
    data class Network(override val message: String) : OpFailure

    /** 其它：HTTP 状态码类、云端备份为空、格式不对……状态码已在 [message] 文本里。 */
    data class Other(override val message: String) : OpFailure
}

/**
 * 把 [NutstoreSync] 那三个 `Result` 里的异常归类。
 *
 * @param fallback 异常没带消息（或消息为空白）时给用户看的兜底话 —— 各调用点原本就各有各的兜底
 *   （"上传失败" / "获取备份列表失败" / "下载失败"），逐字保留。
 */
fun Throwable.toOpFailure(fallback: String): OpFailure {
    val text = message?.takeIf { it.isNotBlank() } ?: fallback
    return when {
        // UnknownHostException / SocketTimeoutException 都是 IOException 的子类，一并归到网络类。
        this is IOException -> OpFailure.Network(text)
        text == NUTSTORE_AUTH_MESSAGE -> OpFailure.Auth(text)
        else -> OpFailure.Other(text)
    }
}
