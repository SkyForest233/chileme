"""#11d ② 区块搬运器（一屏一个区块一刀；用法：python3 tools-tmp-11d2.py <C|D|E>）。"""
import io, re, sys

SRC = 'app/src/main/java/com/agon/app/ui/screens/StatsScreen.kt'
lines = io.open(SRC, encoding='utf-8').read().split('\n')
orig_imports = [l for l in lines if l.startswith('import ')]

# 每笔：标记注释、函数名、新文件、区块之外要审计的 StatsScreen 局部名
SPEC = {
    'C': dict(marker='// ---- 近 7 天消耗趋势（柱状图）----', fn='StatsTrendSection', file='StatsTrendSection.kt',
              note='柱状图的「有守卫的除法」与 14dp/8dp 最小柱高都是这里独有的口径，搬走时一个字没动'),
    'D': dict(marker='// ---- 库存分类占比（环图 + 图例）----', fn='StatsCategorySection', file='StatsCategorySection.kt',
              note='环图与图例共用同一份 `chartColors` 取色（下标同源才不会错行）⇒ 调色板必须一起当参数传'),
    'E': dict(marker='// ---- 消耗排行榜 ----', fn='StatsTopConsumedSection', file='StatsTopConsumedSection.kt',
              note='排行榜行带「点得进详情才可点」的判断（`findItemIdByName`），且用了 `by` 委托 ⇒ getValue 要跟着走'),
}
KEY = sys.argv[1]
spec = SPEC[KEY]

mi = next(i for i, l in enumerate(lines) if l.strip() == spec['marker'])
item_i = mi + 1
assert lines[item_i].strip() == 'item {', lines[item_i]
# item { ... } 的收尾 `}`：按花括号配平（块内 `${…}` 与 `if (…){` 都是配平的，注释里没有花括号）
depth, end = 0, None
for i in range(item_i, len(lines)):
    depth += lines[i].count('{') - lines[i].count('}')
    if depth == 0:
        end = i
        break
assert end is not None
block = lines[item_i + 1:end]              # item { 与 } 之间（不含两头的花括号行）
IND = min(len(l) - len(l.lstrip()) for l in block if l.strip())
ded = [l[IND:] if l.strip() else '' for l in block]
print('区块：第 %d-%d 行（item 体 %d 行，基准缩进 %d）' % (mi + 1, end + 1, len(block), IND))

# ① 自由标识符审计：StatsScreen 的局部名有哪些被这块用到 ⇒ 参数表
locals_of_entry = {
    'state': 'StatsUiState', 'metrics': 'AppStatsListMetrics', 'chartColors': 'List<Color>',
    'onOpenItem': '(String) -> Unit', 'onOpenConsumption': '() -> Unit', 'padding': 'PaddingValues',
    'viewModel': 'AppViewModel',
}
body = '\n'.join(ded)
used = [n for n in locals_of_entry if re.search(r'\b%s\b' % n, body)]
params = [n for n in ['state', 'metrics', 'chartColors', 'onOpenItem', 'onOpenConsumption'] if n in used]
print('用到的入口局部：%s ⇒ 参数表：%s' % (used, params))
unhandled = set(used) - set(params) - {'padding', 'viewModel'}
assert not unhandled, ('这块用了没能当参数传的入口局部：', sorted(unhandled))

if len(params) == 1:
    sig = 'internal fun %s(%s: %s) {\n' % (spec['fn'], params[0], locals_of_entry[params[0]])
elif params:
    sig = 'internal fun %s(\n%s) {\n' % (spec['fn'], ''.join('    %s: %s,\n' % (p, locals_of_entry[p]) for p in params))
else:
    sig = 'internal fun %s() {\n' % spec['fn']


def code_only(t):
    t = re.sub(r'/\*.*?\*/', '', t, flags=re.S)
    return '\n'.join(l for l in t.split('\n')
                     if not l.strip().startswith(('//', '*', '/*'))
                     and not l.startswith('import ') and not l.startswith('package '))


body_for_imports = code_only('\n'.join(ded)) + '\n@Composable\n'
picked = [i for i in orig_imports
          if re.search(r'\b%s\b' % re.escape(i.split(' as ')[-1] if ' as ' in i else i.split('.')[-1]), body_for_imports)]
forced = ['import androidx.compose.runtime.Composable']
if re.search(r'\bby\s', body_for_imports):
    forced.append('import androidx.compose.runtime.getValue')
    print('⚠️ 区块里有 `by` 委托 ⇒ 强制带 getValue（新文件）')
imp = sorted(set(picked) | set(forced))
HDR = """/**
 * 统计页的「%s」区块（09-19 #11d ② 从 `StatsScreen` 的装配体里搬出来）。
 *
 * 为什么单独一个文件：%s。
 * 与 #10e 的 `EditFood*Section.kt` 同形：同包 `internal` + 参数表按入口局部审计的结果给，
 * 所以调用点的 import 一处不改（判据②预测 0 = 实测 0）。
 */
package com.agon.app.ui.screens
""" % (spec['marker'].replace('// ---- ', '').replace(' ----', '').rstrip('-'), spec['note'])
marker_in_new = spec['marker']
new_src = HDR + '\n' + '\n'.join(imp) + '\n\n' + marker_in_new + '\n@Composable\n' + sig + '\n'.join('    ' + l if l else '' for l in ded) + '\n}\n'
io.open('app/src/main/java/com/agon/app/ui/screens/' + spec['file'], 'w', encoding='utf-8').write(new_src)
print('>>> %s：%d 行 / import %d 条' % (spec['file'], len(new_src.rstrip('\n').split('\n')), len(imp)))

# 调用点：整块换成一行
call = 'item { %s(%s) }' % (spec['fn'], ', '.join('%s = %s' % (p, p) for p in params))
pointer = '            // %s：画法在同包的 `%s.kt`（本文件只管装配）' % (spec['fn'].replace('Stats', '').replace('Section', '') or '区块', spec['file'][:-3])
new_lines = lines[:mi] + [pointer, '            ' + call] + lines[end + 1:]
rest = '\n'.join(new_lines)
# 剩余 import 收敛：只删「简单名在剩余代码里 0 命中」的；`by` 还在剩余代码里就必须留 getValue
rest_code = code_only(rest)
drop = [i for i in orig_imports
        if i in picked and not re.search(
            r'\b%s\b' % re.escape(i.split('.')[-1]), rest_code)
        and not (i.endswith('.getValue') and re.search(r'\bby\s', rest_code))]
for imp_line in drop:
    rest = rest.replace(imp_line + '\n', '', 1)
rest = re.sub(r'\n{3,}', '\n\n', rest)
io.open(SRC, 'w', encoding='utf-8').write(rest)
print('>>> StatsScreen.kt %d 行（原 %d）；剩 %d 条 import；删掉：%s'
      % (len(rest.rstrip('\n').split('\n')), len(lines), len([l for l in rest.split('\n') if l.startswith('import ')]),
         ', '.join(i.split('.')[-1] for i in drop) or '无'))
print('>>> 剩余文件里 ` by ` 次数 =', len(re.findall(r'\bby\s', rest_code)), '（决定 getValue 去留）')

# ② 等价性：新文件函数体（去缩进）必须与区块原文逐行相等
nb = io.open('app/src/main/java/com/agon/app/ui/screens/' + spec['file'], encoding='utf-8').read().split('\n')
fs = next(i for i, l in enumerate(nb) if l.endswith(') {'))
tmp = [l.strip() for l in nb[fs + 1:] if l.strip()]
body_new = tmp[:-1]          # 末元素是函数自己的 `}`，不属于区块体
body_old = [l.strip() for l in ded if l.strip()]
print('=== 逐行相等 === 新区块体 %d 行 / 原 %d 行 / 差异 %s'
      % (len(body_new), len(body_old), '✓ 0' if body_new == body_old else '✗'))
if body_new != body_old:
    for a, b in zip(body_new, body_old):
        if a != b:
            print('   新>', a[:80]); print('   原>', b[:80]); break
