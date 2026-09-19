import io, re

SRC = 'app/src/main/java/com/agon/app/ui/components/app/AppText.kt'
DST = 'app/src/main/java/com/agon/app/ui/components/app/AppColors.kt'
lines = io.open(SRC, encoding='utf-8').read().split('\n')

MOVE = ['appSurfaceColor', 'appMutedColor', 'appErrorColor', 'appPrimaryContainerColor',
        'appOnPrimaryContainerColor', 'appPrimaryColor', 'appFaintColor',
        'appHighestContainerColor', 'appChartColors']

starts = [i for i, l in enumerate(lines)
          if re.match(r'^(?:@|fun |internal fun |private fun |enum class |class |data class )', l)]


def docs_up(i):
    k = i
    while k > 0:
        s = lines[k - 1].strip()
        if s == '' or s.startswith('package '):
            break
        if s.startswith('@') or s.startswith('*') or s.startswith('/**') or s.endswith('*/'):
            k -= 1
        else:
            break
    return k


items = []
for n, d in enumerate(starts):
    s = docs_up(d)
    e = (docs_up(starts[n + 1]) - 1) if n + 1 < len(starts) else len(lines) - 1
    while e > s and lines[e].strip() == '':
        e -= 1
    items.append((s, e, d))


def name_of(d):
    m = re.match(r'^(?:internal |private )?(?:fun|enum class|class|data class)\s+(\w+)', lines[d])
    return m.group(1) if m else '?'


picked = [it for it in items if name_of(it[2]) in MOVE]
assert len(picked) == len(MOVE), sorted(name_of(it[2]) for it in items)

cut = set()
for s, e, _ in picked:
    cut.update(range(s, e + 1))
    if e + 1 < len(lines) and lines[e + 1].strip() == '':
        cut.add(e + 1)

blocks = ['\n'.join(lines[s:e + 1]) for s, e, _ in picked]
moved_text = '\n\n'.join(blocks)
rest = re.sub(r'\n{3,}', '\n\n', '\n'.join(l for i, l in enumerate(lines) if i not in cut)).rstrip('\n') + '\n'

all_imports = [l for l in lines if l.startswith('import ')]


def code_only(t):
    t = re.sub(r'/\*.*?\*/', '', t, flags=re.S)
    return '\n'.join(l for l in t.split('\n')
                     if not l.strip().startswith(('//', '*', '/*'))
                     and not l.startswith('import ') and not l.startswith('package '))


def need(t):
    body = code_only(t)
    keep = []
    for imp in all_imports:
        simple = imp.split(' as ')[-1] if ' as ' in imp else imp.split('.')[-1]
        if re.search(r'\b%s\b' % re.escape(simple), body):
            keep.append(imp)
    return keep


rest_imp = need(rest[len('package com.agon.app.ui.components.app\n'):])
mv_imp = need(moved_text)
plain = sorted([i for i in mv_imp if ' as ' not in i])
aliased = sorted([i for i in mv_imp if ' as ' in i])

HEAD = '''package com.agon.app.ui.components.app

/**
 * 跨主题的**取色口子**：一个语义角色一个函数，内部只做「MD3 色板的哪个角色 vs Miuix 色板的哪个角色」这一件事。
 *
 * 2026-09-19 由 `AppText.kt` 抽出（路线图 #11a）。之前这 9 个 helper 与文本组件同住一个文件，
 * 后果不是"文件长"，而是**全仓事实上的颜色层叫 AppText.kt**：屏幕要取个弱化色、危险色、图表调色板，
 * 第一反应是去文本组件里找，而新增一档颜色时也容易顺手塞回那里（守卫 `AppColorLocationTest` 就是钉这条）。
 *
 * 三条约定随函数搬过来，一个字没改：
 * 1. **只在"组件层没有对应东西"时才开口子**（自绘图表、头像底、进度条轨道）；屏幕要弱化文字就用
 *    [AppHintText] / [AppMutedText]，别自己取色再拼一个 `Text`，否则两主题的色板角色又要各写一遍；
 * 2. **两主题的色板角色不是一一对应**，每个函数标了各自取的那一格（`appMutedColor` 是
 *    MD3 `onSurfaceVariant` / Miuix `onSurfaceVariantSummary`，`appFaintColor` 是 `outlineVariant` /
 *    `dividerLine`）⇒ 按角色名去"统一"就是改视觉，只有真机看得出来；
 * 3. 函数名刻意不叫 `appOutlineVariantColor` 那种"照抄 MD3 角色名"的形状 —— Miuix 侧没有那个角色。
 */

'''

io.open(DST, 'w', encoding='utf-8').write(HEAD + '\n'.join(plain + aliased) + '\n\n' + moved_text + '\n')

# 原文件：把不再需要的 import 删掉
new_rest = rest
for imp in [i for i in all_imports if i not in rest_imp]:
    new_rest = new_rest.replace(imp + '\n', '', 1)
new_rest = re.sub(r'\n{3,}', '\n\n', new_rest)
# 文件头 KDoc 里加一句去向说明（放在末行那句之后）
anchor = '// 2026-09-16 由 FoodDetailScreen + MiuixFoodDetailScreen 合并时抽出。'
assert new_rest.count(anchor) == 1
new_rest = new_rest.replace(
    anchor,
    anchor + '\n//\n'
    '// 2026-09-19（#11a）：本文件里的 9 个跨主题**取色** helper 已抽到同包 `AppColors.kt` —— 那件事与「文字」无关，\n'
    '// 只是当初一起住在这里。调用方的 import 路径**没变**（同包），新增取色口子请去那边加、别塞回来。',
    1)
io.open(SRC, 'w', encoding='utf-8').write(new_rest)

print('AppColors.kt 写出：%d 行；AppText.kt 余 %d 行' % (
    len(io.open(DST, encoding='utf-8').read().split('\n')), len(new_rest.split('\n'))))
print('AppText.kt 丢掉的 import:', [i for i in all_imports if i not in rest_imp])
print('AppColors.kt 的 import:'); print('\n'.join(plain + aliased))

# 逐字校验：搬走的 9 段在新文件里逐行相等
nsrc = io.open(DST, encoding='utf-8').read()
for b in blocks:
    assert b in nsrc, '逐字搬运失败：\n' + b[:80]
print('\n✓ 9 段逐字相等；原文件行数 = %d + 新文件正文 %d = 对账 %d' % (
    len(new_rest.split('\n')), len(moved_text.split('\n')), len(lines)))
