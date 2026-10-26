#!/usr/bin/env python3
"""ConfigSwitch 的 i18n 一致性检查。

检查四件事（前三条会直接坏在用户眼前）：

  1. 代码引用、lang 里没有        —— 界面上会显示 key 原文
  2. en_us / zh_cn 的 key 集合不同 —— 一种语言缺文案
  3. 占位符按【参数序号】比较不一致 —— 格式化错误 / 参数错位
  4. lang 里有、代码没引用        —— 死文案

用法：
    python tools/check_i18n.py

退出码：0 = 全通过；1 = 有问题。

【动态 key】
代码里有一处是拼出来的（`"configswitch.message." + name + ".head"`），
静态检查看不到它的结果。这类地方在代码上方写一行：

    // i18n-dynamic: configswitch.message.{push,fetch}.head

脚本会展开花括号，把结果算作"已引用"。知识留在现场，脚本里不硬编码特例。
"""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LANG_DIR = ROOT / "common/src/main/resources/assets/configswitch/lang"
SOURCE_DIRS = [ROOT / "common/src/main/java"]

# 命名约定里第二段的白名单（见 docs/i18n.md「命名约定」）。
# 不能只用 "configswitch." 当判据 —— logger 名、系统属性、configswitchcustom
# 都以它开头，会误报。
NAMESPACES = (
    "screen", "button", "label", "message", "error",
    "group", "confirm", "search", "help", "source", "transfer",
)

KEY_RE = re.compile(
    r'"('
    r'configswitch\.(?:' + "|".join(NAMESPACES) + r')(?:\.[A-Za-z0-9_]+)+'
    r'|key\.configswitch(?:\.[A-Za-z0-9_]+)+'
    r'|category\.configswitch'
    r')"'
)

DYNAMIC_RE = re.compile(r"//\s*i18n-dynamic:\s*(\S+)")

# Java 的 format 说明符。`%%` 和 `%n` 不吃参数，单独排除。
# 已知局限：`%t` / `%T`（日期）没有处理 —— 本项目没用。
FMT_RE = re.compile(r"%(\d+\$)?([-#+ 0,(]*)(\d+)?(?:\.(\d+))?([a-zA-Z%])")
CONSUMES_ARG = set("bBhHsScCdoxXeEfgGaA")


def strip_java_comments(text: str) -> str:
    """去掉 // 和 /* */ 注释，但保留字符串 / 字符字面量里的内容。

    保留换行，让报错行号还能对上。
    """
    out = []
    i, n = 0, len(text)
    state = "code"
    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if state == "code":
            if c == "/" and nxt == "/":
                state, i = "line", i + 2
                continue
            if c == "/" and nxt == "*":
                state, i = "block", i + 2
                continue
            if c == '"':
                state = "string"
            elif c == "'":
                state = "char"
            out.append(c)
            i += 1
        elif state == "line":
            if c == "\n":
                state = "code"
                out.append(c)
            i += 1
        elif state == "block":
            if c == "*" and nxt == "/":
                state, i = "code", i + 2
                continue
            if c == "\n":
                out.append(c)
            i += 1
        else:  # string / char
            out.append(c)
            if c == "\\" and i + 1 < n:
                out.append(text[i + 1])
                i += 2
                continue
            if (state == "string" and c == '"') or (state == "char" and c == "'"):
                state = "code"
            i += 1
    return "".join(out)


def expand_braces(spec: str):
    """configswitch.message.{push,fetch}.head -> 两个 key。"""
    m = re.search(r"\{([^{}]*)\}", spec)
    if not m:
        return [spec]
    out = []
    for option in m.group(1).split(","):
        out.extend(expand_braces(spec[: m.start()] + option + spec[m.end():]))
    return out


def format_args(value: str):
    """把 format 字符串归一化成 {参数序号: 转换符}。

    位置参数（%2$d）和顺序参数（%d）都展开成"第几个参数、是什么类型"，
    所以 en 写 %2$d files with %1$s、zh 写 %s 覆盖 %d 会被判为一致 ——
    它们本来就一致，只是语序不同。

    返回 (args, problem)。problem 非空时表示这个值本身有问题。
    """
    args = {}
    sequential = 1
    has_explicit = False
    has_implicit = False
    for m in FMT_RE.finditer(value):
        index, _flags, _width, _prec, conv = m.groups()
        if conv in ("%", "n"):
            continue
        if conv not in CONSUMES_ARG:
            continue
        if index:
            has_explicit = True
            args[int(index[:-1])] = conv
        else:
            has_implicit = True
            args[sequential] = conv
            sequential += 1
    if has_explicit and has_implicit:
        return args, "同一个值里混用了位置参数和顺序参数（Java 不允许）"
    return args, None


def load_lang(name: str):
    path = LANG_DIR / f"{name}.json"
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except FileNotFoundError:
        fail(f"找不到 {path}")
    except json.JSONDecodeError as e:
        fail(f"{path} 不是合法 JSON：{e}")


def collect_source_keys():
    literals = set()
    dynamic = set()
    for d in SOURCE_DIRS:
        if not d.is_dir():
            # 目录不在 = 跑错地方了。静默跳过会把所有 key 报成"未使用"，
            # 那个结果更难看懂。
            fail(f"找不到源码目录 {d}")
        for path in d.rglob("*.java"):
            raw = path.read_text(encoding="utf-8")
            for m in DYNAMIC_RE.finditer(raw):
                dynamic.update(expand_braces(m.group(1)))
            literals.update(KEY_RE.findall(strip_java_comments(raw)))
    return literals, dynamic


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except AttributeError:
        pass

    en = load_lang("en_us")
    zh = load_lang("zh_cn")
    literals, dynamic = collect_source_keys()
    used = literals | dynamic
    lang_keys = set(en)

    print("i18n 检查 —— ConfigSwitch")
    print(f"  lang: en_us {len(en)} / zh_cn {len(zh)} 个 key")
    print(f"  代码引用: {len(literals)} 条 + 动态声明 {len(dynamic)} 条")
    print()

    problems = []  # (标题, [细节行])

    missing = sorted(used - lang_keys)
    if missing:
        problems.append((
            f"代码引用了、lang 里没有（{len(missing)}）",
            [f"{k}   —— 界面会显示这串原文，请补进两个 lang 文件" for k in missing],
        ))

    only_en = sorted(set(en) - set(zh))
    only_zh = sorted(set(zh) - set(en))
    if only_en or only_zh:
        lines = [f"{k}   —— 只在 en_us" for k in only_en]
        lines += [f"{k}   —— 只在 zh_cn" for k in only_zh]
        problems.append((f"两个 lang 文件的 key 集合不同（{len(lines)}）", lines))

    fmt_lines = []
    for key in sorted(lang_keys & set(zh)):
        en_args, en_bad = format_args(en[key])
        zh_args, zh_bad = format_args(zh[key])
        if en_bad:
            fmt_lines.append(f"{key}   —— en_us: {en_bad}")
        if zh_bad:
            fmt_lines.append(f"{key}   —— zh_cn: {zh_bad}")
        if en_args != zh_args:
            fmt_lines.append(
                f"{key}   —— en_us 参数 {en_args} / zh_cn 参数 {zh_args}"
                "（同一参数序号的类型必须一致）"
            )
    if fmt_lines:
        problems.append((f"占位符不一致（{len(fmt_lines)}）", fmt_lines))

    unused = sorted(lang_keys - used)
    if unused:
        problems.append((
            f"lang 里有、代码没引用（{len(unused)}）",
            [
                f"{k}   —— 要么删掉；如果是拼出来的，在上方加 "
                f"// i18n-dynamic: <模式>"
                for k in unused
            ],
        ))

    for i, (title, lines) in enumerate(problems, 1):
        print(f"[错误 {i}] {title}")
        for line in lines:
            print(f"  · {line}")
        print()

    if problems:
        print(f"结论：{len(problems)} 类问题。")
        return 1

    print("结论：全部一致。")
    return 0


def fail(message: str):
    print(f"[错误] {message}", file=sys.stderr)
    sys.exit(1)


if __name__ == "__main__":
    sys.exit(main())
