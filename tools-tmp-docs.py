import io, re

# ---------- 1) ROADMAP：#10 的「明确不动」行 + 验收① 里那句 StatsScreen 不拆 ----------
p = 'docs/ROADMAP.md'
s = io.open(p, encoding='utf-8').read()

old_row = "| — | **明确不动**：`AppChrome.kt` 479 / `AppListRow.kt` 406 / `StatsScreen.kt` 409 / `ExpiryCalendar.kt` 400 / `AppControls.kt` 376 / `AppText.kt` 349 / `AppSurface.kt` 353 | — |"
i = s.index(old_row)
j = s.index('\n', i)
new_row = ("| — | **按行数明确不动**（09-16 的判定，只覆盖「成对主题实现」那一类）：`AppChrome.kt` / `AppListRow.kt` / "
           "`AppControls.kt` / `AppText.kt` / `AppSurface.kt` —— 吸收主题差异正是它们的职责，消灭它等于把差异推回屏幕层。"
           "⚠️ **09-19 两次改动本行**：① 行数列**删掉**（#11a 把 `AppText.kt` 的取色 helper 抽走后它当场变短，"
           "抄来的数一晚上就烂 ⇒ 现值一律看 `doc-metrics`）；② 原行里的 `StatsScreen.kt` 与 `ExpiryCalendar.kt` "
           "**移出「不动」名单** —— 它们被 #11 按**职责边界**接手（11d / 11e），理由不是行数 |")
s = s[:i] + new_row + s[j:]

a2 = "**`StatsScreen.kt` 409（不拆，超 9 行，理由见「明确不动」表）**"
b2 = ("**`StatsScreen.kt`（当时 409、只超 9 行，当时判「不拆」；⚠️ 09-19 立项 #11 后按**职责边界**改判要拆 "
      "= 11d ⇒ 本行保留为当时口径）**")
assert s.count(a2) == 1
s = s.replace(a2, b2, 1)
io.open(p, 'w', encoding='utf-8').write(s)
print('ROADMAP 两处已改')

# ---------- 2) INDEX：#10 那条「不做」清单里的行数列 ----------
p2 = 'devlog/INDEX.md'
t = io.open(p2, encoding='utf-8').read()
a3 = """② 拆 App 级组件层那 5 个文件（`AppChrome` 479 / `AppListRow` 406 / `AppControls` 376 /
      `AppText` 349 / `AppSurface` 353）—— 吸收主题分支正是它们的职责。"""
b3 = """② 按**行数**拆 App 级组件层那 5 个文件（`AppChrome` / `AppListRow` / `AppControls` / `AppText` /
      `AppSurface`，行数不在此手抄：看 `tools/doc-metrics.sh` 的「App 级组件层」与「主代码超 400 行全清单」两行）——
      吸收主题分支正是它们的职责。⚠️ 09-19 改窄：这条只覆盖「成对主题实现」，**不是**「组件层文件不用管边界」的
      挡箭牌 ⇒ #11a 就从 `AppText.kt` 里抽走了 9 个取色 helper（那件事与文字无关）、#11b 接着拆 `AppChrome.kt` 的四件事。"""
assert t.count(a3) == 1, 'INDEX 锚点'
t = t.replace(a3, b3, 1)
io.open(p2, 'w', encoding='utf-8').write(t)
print('INDEX 已改窄')

# ---------- 3) DESIGN_SPEC §4.1 组件清单：AppText 行改小、新增 AppColors 行 ----------
p3 = 'docs/DESIGN_SPEC.md'
d = io.open(p3, encoding='utf-8').read()
m = re.search(r'^\| `AppText\.kt` \(349 行\) \|[^\n]*\n', d, re.M)
assert m, '找不到 AppText.kt 那行'
old_line = m.group(0).rstrip('\n')
keep_components = "`AppTextScale` `AppText` `AppEmojiText` `AppMutedText`"
new_text = ("| `AppText.kt` (213 行) | " + keep_components +
            " | 语义字号档位 + 文字组件；**取色访问器已于 09-19 #11a 移出去**，别再往这里加 |\n"
            "| `AppColors.kt` (165 行) | `appSurfaceColor` `appMutedColor` `appErrorColor` `appPrimaryContainerColor` "
            "`appOnPrimaryContainerColor` `appPrimaryColor` `appFaintColor` `appHighestContainerColor` `appChartColors` | "
            "跨主题取色口子（一个语义角色一个函数）；守卫 `AppColorLocationTest` 钉住「只此一个文件」 |")
d = d[:m.start()] + new_text + '\n' + d[m.end():]
d = d.replace("> **新增/删除组件时同批更新本表**（`CLAUDE.md` §3 记录规则已立此条）。",
              "> **新增/删除组件时同批更新本表**（`CLAUDE.md` §3 记录规则已立此条）。\n"
              "> ⚠️ 括号里的行数是**快照**：`AppText.kt` / `AppColors.kt` 两行为 2026-09-19 #11a 当天实测，其余行仍是 09-17 的数；\n"
              "> 全目录现值一律看 `tools/doc-metrics.sh` 的「App 级组件层」那行，本表这列别拿来当结论引用。", 1)
io.open(p3, 'w', encoding='utf-8').write(d)
print('DESIGN_SPEC 组件清单已同步（拆表 + 加两行 + 口径注）')
print('旧行内容预览:', old_line[:110])
