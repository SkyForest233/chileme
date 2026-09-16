package com.agon.app

// ⚠️ 临时探针文件，**下一个提交就删**。目的：判断 detekt 到底有没有在跑规则。
// 本文件每一处都必然命中 detekt.yml 里 active 的规则，而 CI 报告的是「0 code smells」：
//   · canaryUnusedPrivate    → style>UnusedPrivateMember（private 且无人调用）
//   · canaryEightParams      → complexity>LongParameterList（阈值 6，这里 8 个）
//   · canaryEmptyCatch       → empty-blocks>EmptyCatchBlock + exceptions>SwallowedException
//   · canaryLongMethod       → complexity>LongMethod（阈值 60，这里 70 行）
// 若这一轮 detekt 仍报 0 条，说明门禁是空转的（1.23.8 内置 Kotlin 2.0.21 解析器 vs 工程 2.4.10），
// 而不是「代码干净」—— 那就要么升级 detekt，要么在文档里如实写明 detekt 不生效。

private fun canaryUnusedPrivate(): Int = 42

fun canaryEightParams(a: Int, b: Int, c: Int, d: Int, e: Int, f: Int, g: Int, h: Int): Int =
    a + b + c + d + e + f + g + h

fun canaryEmptyCatch() {
    try {
        canaryEightParams(1, 2, 3, 4, 5, 6, 7, 8)
    } catch (ignored: RuntimeException) {
    }
}

fun canaryLongMethod(): Int {
    var total = 0
    total += 1
    total += 2
    total += 3
    total += 4
    total += 5
    total += 6
    total += 7
    total += 8
    total += 9
    total += 10
    total += 11
    total += 12
    total += 13
    total += 14
    total += 15
    total += 16
    total += 17
    total += 18
    total += 19
    total += 20
    total += 21
    total += 22
    total += 23
    total += 24
    total += 25
    total += 26
    total += 27
    total += 28
    total += 29
    total += 30
    total += 31
    total += 32
    total += 33
    total += 34
    total += 35
    total += 36
    total += 37
    total += 38
    total += 39
    total += 40
    total += 41
    total += 42
    total += 43
    total += 44
    total += 45
    total += 46
    total += 47
    total += 48
    total += 49
    total += 50
    total += 51
    total += 52
    total += 53
    total += 54
    total += 55
    total += 56
    total += 57
    total += 58
    total += 59
    total += 60
    total += 61
    total += 62
    total += 63
    total += 64
    total += 65
    total += 66
    total += 67
    total += 68
    return total
}
