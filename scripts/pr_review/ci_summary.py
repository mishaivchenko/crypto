"""Build and parse structured CI summaries for AI PR review."""
from __future__ import annotations

import argparse
import base64
import datetime as dt
import json
import os
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

_DIR = os.path.dirname(os.path.abspath(__file__))
_SCRIPTS_DIR = os.path.dirname(_DIR)
if _SCRIPTS_DIR not in sys.path:
    sys.path.insert(0, _SCRIPTS_DIR)

from pr_review.models import CheckResult, CiSummary, QualityMetric


_JACOCO_COUNTERS = ("LINE", "BRANCH")
_SNAPSHOT_RE = re.compile(r"<!-- ai-pr-review-ci-summary: ([A-Za-z0-9+/=]+) -->")


def load_summary(raw: str) -> CiSummary | None:
    """Parse CI summary JSON from an environment variable or hidden comment payload."""
    if not raw.strip():
        return None
    try:
        data = json.loads(raw)
    except (json.JSONDecodeError, TypeError, ValueError):
        return None
    if not isinstance(data, dict):
        return None
    checks = tuple(
        CheckResult(
            name=str(item.get("name", "")),
            status=str(item.get("status", "")),
            conclusion=str(item.get("conclusion", "")),
            duration_seconds=int(item.get("durationSeconds", 0) or 0),
        )
        for item in data.get("checks", [])
        if isinstance(item, dict)
    )
    metrics = tuple(
        QualityMetric(
            key=str(item.get("key", "")),
            label=str(item.get("label", "")),
            value=float(item.get("value", 0.0) or 0.0),
            unit=str(item.get("unit", "")),
            lower_is_better=bool(item.get("lowerIsBetter", True)),
        )
        for item in data.get("metrics", [])
        if isinstance(item, dict)
    )
    return CiSummary(
        run_id=str(data.get("runId", "")),
        run_url=str(data.get("runUrl", "")),
        conclusion=str(data.get("conclusion", "")),
        checks=checks,
        metrics=metrics,
    )


def dumps_summary(summary: CiSummary) -> str:
    """Serialize a CI summary using stable compact JSON keys."""
    return json.dumps(
        {
            "runId": summary.run_id,
            "runUrl": summary.run_url,
            "conclusion": summary.conclusion,
            "checks": [
                {
                    "name": check.name,
                    "status": check.status,
                    "conclusion": check.conclusion,
                    "durationSeconds": check.duration_seconds,
                }
                for check in summary.checks
            ],
            "metrics": [
                {
                    "key": metric.key,
                    "label": metric.label,
                    "value": metric.value,
                    "unit": metric.unit,
                    "lowerIsBetter": metric.lower_is_better,
                }
                for metric in summary.metrics
            ],
        },
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    )


def hidden_snapshot(summary: CiSummary) -> str:
    """Return an HTML comment containing a compact CI summary snapshot."""
    payload = base64.b64encode(dumps_summary(summary).encode("utf-8")).decode("ascii")
    return f"<!-- ai-pr-review-ci-summary: {payload} -->"


def extract_latest_from_comments(comments: list[dict]) -> CiSummary | None:
    """Read the most recent CI summary snapshot from existing PR comments."""
    for comment in reversed(comments):
        body = comment.get("body") or ""
        match = _SNAPSHOT_RE.search(body)
        if not match:
            continue
        try:
            raw = base64.b64decode(match.group(1)).decode("utf-8")
        except (ValueError, UnicodeDecodeError):
            continue
        summary = load_summary(raw)
        if summary is not None:
            return summary
    return None


def build_summary(
    run_json: pathlib.Path,
    artifacts_dir: pathlib.Path,
    run_id: str,
    run_url: str,
    conclusion: str,
) -> CiSummary:
    """Build a structured summary from GitHub run JSON and downloaded CI artifacts."""
    checks = _read_checks(run_json)
    metrics = _collect_metrics(artifacts_dir)
    return CiSummary(
        run_id=run_id,
        run_url=run_url,
        conclusion=conclusion,
        checks=tuple(checks),
        metrics=tuple(metrics),
    )


def _read_checks(run_json: pathlib.Path) -> list[CheckResult]:
    if not run_json.exists():
        return []
    try:
        data = json.loads(run_json.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return []
    result = []
    for job in data.get("jobs", []):
        if not isinstance(job, dict):
            continue
        result.append(
            CheckResult(
                name=str(job.get("name", "")),
                status=str(job.get("status", "")),
                conclusion=str(job.get("conclusion") or "unknown"),
                duration_seconds=_duration_seconds(
                    str(job.get("startedAt", "")),
                    str(job.get("completedAt", "")),
                ),
            )
        )
    return result


def _duration_seconds(started_at: str, completed_at: str) -> int:
    try:
        started = dt.datetime.fromisoformat(started_at.replace("Z", "+00:00"))
        completed = dt.datetime.fromisoformat(completed_at.replace("Z", "+00:00"))
    except ValueError:
        return 0
    seconds = int((completed - started).total_seconds())
    return max(seconds, 0)


def _collect_metrics(artifacts_dir: pathlib.Path) -> list[QualityMetric]:
    metrics = []
    metrics.extend(_test_metrics(artifacts_dir))
    metrics.extend(_pmd_metrics(artifacts_dir))
    metrics.extend(_spotbugs_metrics(artifacts_dir))
    metrics.extend(_jacoco_metrics(artifacts_dir))
    metrics.extend(_sonar_backlog_metrics(artifacts_dir))
    return metrics


def _test_metrics(root: pathlib.Path) -> list[QualityMetric]:
    tests = failures = skipped = 0
    for path in root.rglob("TEST-*.xml"):
        try:
            tree = ET.parse(path)
        except ET.ParseError:
            continue
        suite = tree.getroot()
        tests += int(float(suite.attrib.get("tests", "0")))
        failures += int(float(suite.attrib.get("failures", "0")))
        failures += int(float(suite.attrib.get("errors", "0")))
        skipped += int(float(suite.attrib.get("skipped", "0")))
    if tests == failures == skipped == 0:
        return []
    return [
        QualityMetric("tests.total", "Тесты всего", float(tests), "", False),
        QualityMetric("tests.failed", "Тесты упали", float(failures), "", True),
        QualityMetric("tests.skipped", "Тесты skipped", float(skipped), "", True),
    ]


def _pmd_metrics(root: pathlib.Path) -> list[QualityMetric]:
    count = 0
    for path in root.rglob("pmd/**/*.xml"):
        try:
            tree = ET.parse(path)
        except ET.ParseError:
            continue
        count += len(tree.findall(".//violation"))
    return [QualityMetric("pmd.violations", "PMD замечания", float(count), "", True)]


def _spotbugs_metrics(root: pathlib.Path) -> list[QualityMetric]:
    count = 0
    for path in root.rglob("spotbugs/**/*.xml"):
        try:
            tree = ET.parse(path)
        except ET.ParseError:
            continue
        count += len(tree.findall(".//BugInstance"))
    return [QualityMetric("spotbugs.bugs", "SpotBugs bugs", float(count), "", True)]


def _jacoco_metrics(root: pathlib.Path) -> list[QualityMetric]:
    missed: dict[str, int] = {counter: 0 for counter in _JACOCO_COUNTERS}
    covered: dict[str, int] = {counter: 0 for counter in _JACOCO_COUNTERS}
    found = False
    for path in root.rglob("jacocoTestReport.xml"):
        try:
            tree = ET.parse(path)
        except ET.ParseError:
            continue
        found = True
        for counter in tree.getroot().findall("counter"):
            kind = counter.attrib.get("type", "")
            if kind not in _JACOCO_COUNTERS:
                continue
            missed[kind] += int(counter.attrib.get("missed", "0"))
            covered[kind] += int(counter.attrib.get("covered", "0"))
    if not found:
        return []
    result = []
    for kind in _JACOCO_COUNTERS:
        total = missed[kind] + covered[kind]
        value = (covered[kind] / total * 100.0) if total else 0.0
        label = "JaCoCo line coverage" if kind == "LINE" else "JaCoCo branch coverage"
        result.append(QualityMetric(f"jacoco.{kind.lower()}", label, value, "%", False))
    return result


def _sonar_backlog_metrics(root: pathlib.Path) -> list[QualityMetric]:
    paths = sorted(root.rglob("quality-backlog.json"))
    if not paths:
        return []
    try:
        data = json.loads(paths[-1].read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return []
    groups = data.get("groups", [])
    if not isinstance(groups, list):
        return []

    counts = {
        "bug": 0,
        "security": 0,
        "coverage": 0,
        "maintainability": 0,
    }
    total_findings = 0
    for group in groups:
        if not isinstance(group, dict):
            continue
        category = str(group.get("category", "")).lower()
        count = int(group.get("count", 0) or 0)
        total_findings += count
        if category in counts:
            counts[category] += count

    return [
        QualityMetric("sonar.findings", "Sonar findings", float(total_findings), "", True),
        QualityMetric("sonar.bugs", "Sonar bugs", float(counts["bug"]), "", True),
        QualityMetric("sonar.security", "Sonar security", float(counts["security"]), "", True),
        QualityMetric("sonar.coverage", "Sonar coverage groups", float(counts["coverage"]), "", True),
        QualityMetric(
            "sonar.maintainability",
            "Sonar maintainability",
            float(counts["maintainability"]),
            "",
            True,
        ),
    ]


def _render_markdown(summary: CiSummary) -> str:
    lines = [
        "# CI Summary",
        "",
        f"- conclusion: {summary.conclusion or 'unknown'}",
        f"- run id: {summary.run_id or 'unknown'}",
        f"- url: {summary.run_url or 'unknown'}",
        "",
        "## Checks",
    ]
    for check in summary.checks:
        duration = f" ({check.duration_seconds}s)" if check.duration_seconds else ""
        lines.append(f"- {check.name}: {check.conclusion or check.status}{duration}")
    lines.append("")
    lines.append("## Metrics")
    for metric in summary.metrics:
        lines.append(f"- {metric.label}: {metric.value:g}{metric.unit}")
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-json", type=pathlib.Path, required=True)
    parser.add_argument("--artifacts-dir", type=pathlib.Path, required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--run-url", required=True)
    parser.add_argument("--conclusion", required=True)
    parser.add_argument("--output", type=pathlib.Path, required=True)
    parser.add_argument("--markdown", type=pathlib.Path, required=True)
    args = parser.parse_args()

    summary = build_summary(
        args.run_json,
        args.artifacts_dir,
        args.run_id,
        args.run_url,
        args.conclusion,
    )
    args.output.write_text(dumps_summary(summary), encoding="utf-8")
    args.markdown.write_text(_render_markdown(summary), encoding="utf-8")


if __name__ == "__main__":
    main()
