"""Optional report chart: Python 3 + matplotlib; no application dependency."""
import argparse
import json
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib import font_manager
from matplotlib.ticker import FuncFormatter


def korean_font():
    candidates = ("Malgun Gothic", "Noto Sans CJK KR", "Noto Sans KR",
                  "Apple SD Gothic Neo", "NanumGothic")
    for family in candidates:
        try:
            font_manager.findfont(font_manager.FontProperties(family=[family]),
                                  fallback_to_default=False)
        except ValueError:
            continue
        return family
    raise RuntimeError("Korean chart generation requires an installed Korean font. "
                       "Install one of: " + ", ".join(candidates))


root = Path(__file__).resolve().parents[1]
report = json.loads((root / "docs/reports/query-comparison.json").read_text(encoding="utf-8"))
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("language", nargs="?", choices=("en", "ko"), default="en")
parser.add_argument("destination", nargs="?", type=Path)
args = parser.parse_args()
language = args.language
filename = "query-cost.ko.svg" if language == "ko" else "query-cost.svg"
destination = args.destination or root / "docs/images" / filename
font_family = korean_font() if language == "ko" else "DejaVu Sans"
destination.parent.mkdir(parents=True, exist_ok=True)
plt.rcParams.update({"font.family": font_family,
                     "svg.fonttype": "path", "svg.hashsalt": "eventlite-query-cost",
                     "font.size": 10, "axes.spines.top": False,
                     "axes.spines.right": False})
operations = ["next-three-events", "popular-three-venues", "venue-next-three-events", "legacy-full-event-list"]
rows = {r["operation"]: r for r in report["rows"] if r["events"] == 100000}
labels = (["예정 행사 3개", "인기 장소 3개", "장소별 예정 행사 3개", "전체 목록 (유지)"] if language == "ko"
          else ["Next 3 events", "Top 3 venues", "Next 3 at a venue", "Full list (preserved)"])
fig, axes = plt.subplots(1, 2, figsize=(11.2, 4.4), layout="constrained", facecolor="#f8fafc")
for ax, field, title in zip(axes, ["sql", "loaded"],
                          ["SQL 실행 수", "로딩한 엔티티 수"] if language == "ko" else ["SQL statements", "Loaded entities"]):
    ax.set_facecolor("#f8fafc")
    for i, operation in enumerate(operations):
        a, b = rows[operation]["initial_" + field], rows[operation]["final_" + field]
        ax.plot([a, b], [i, i], color="#cbd5e1", linewidth=4, zorder=1)
        ax.scatter(a, i + .07, color="#ca8a04", s=50, label=("초기" if language == "ko" else "Initial") if i == 0 else None, zorder=3)
        ax.scatter(b, i - .07, color="#2563eb", s=50, label=("개선" if language == "ko" else "Improved") if i == 0 else None, zorder=4)
        ax.annotate(f"{a:,} → {b:,}", (1, i), xytext=(0, -20), textcoords="offset points", fontsize=9, color="#475569")
    ax.set_xscale("log")
    ax.set_xlim(.7, 200000 if field == "loaded" else 200)
    ax.set_xticks([1, 10, 100, 1000, 10000, 100000] if field == "loaded" else [1, 10, 100])
    ax.xaxis.set_major_formatter(FuncFormatter(lambda x, _: f"{x:,.0f}"))
    ax.set_yticks(range(4), labels)
    ax.set_ylim(3.6, -.6)
    ax.grid(axis="x", alpha=.2)
    ax.set_title(title, loc="left", fontweight="bold", color="#0f172a")
    ax.set_xlabel("로그 눈금" if language == "ko" else "Logarithmic scale")
axes[0].legend(loc="lower left", bbox_to_anchor=(0, -.35), ncol=2, frameon=False)
fig.suptitle("행사 100,000개와 장소 100개 | 같은 결과를 반환하는 조회" if language == "ko"
             else "100,000 events and 100 venues | identical returned results", fontsize=13, fontweight="bold")
fig.savefig(destination, metadata={"Date": None, "Description": "Counts from all four operations, not HTTP performance. See query-comparison.json."})
if destination.suffix.lower() == ".svg":
    content = destination.read_text(encoding="utf-8")
    destination.write_text("\n".join(line.rstrip() for line in content.splitlines()) + "\n",
                           encoding="utf-8", newline="\n")
print(destination)
