"""SonarQube API client for local quality backlog extraction."""
from __future__ import annotations

import base64
import json
import urllib.error
import urllib.parse
import urllib.request

from quality_backlog.models import SonarIssue, SonarMetric

_TIMEOUT = 30
_PAGE_SIZE = 500


class SonarError(RuntimeError):
    """Raised when SonarQube data cannot be fetched."""


class SonarClient:
    def __init__(self, host_url: str, token: str, project_key: str) -> None:
        self.host_url = host_url.rstrip("/")
        self.token = token
        self.project_key = project_key

    def _request(self, path: str, query: dict[str, str | int]) -> dict:
        encoded = urllib.parse.urlencode(query)
        url = f"{self.host_url}{path}?{encoded}"
        basic = base64.b64encode(f"{self.token}:".encode()).decode()
        req = urllib.request.Request(
            url,
            headers={
                "Authorization": f"Basic {basic}",
                "Accept": "application/json",
            },
            method="GET",
        )
        try:
            with urllib.request.urlopen(req, timeout=_TIMEOUT) as response:
                return json.loads(response.read().decode())
        except urllib.error.HTTPError as exc:
            body = exc.read().decode(errors="replace")
            raise SonarError(f"GET {path} failed with HTTP {exc.code}: {body[:300]}") from exc
        except Exception as exc:
            raise SonarError(f"GET {path} failed: {exc}") from exc

    def health(self) -> str:
        data = self._request("/api/system/status", {})
        return str(data.get("status", "UNKNOWN"))

    def fetch_issues(self) -> list[SonarIssue]:
        issues: list[SonarIssue] = []
        page = 1
        while True:
            data = self._request(
                "/api/issues/search",
                {
                    "componentKeys": self.project_key,
                    "resolved": "false",
                    "types": "BUG,VULNERABILITY,CODE_SMELL",
                    "ps": _PAGE_SIZE,
                    "p": page,
                },
            )
            raw_issues = data.get("issues", [])
            issues.extend(self._parse_issue(raw) for raw in raw_issues)
            total = int(data.get("total", len(issues)))
            if not raw_issues or page * _PAGE_SIZE >= total:
                return issues
            page += 1

    def fetch_hotspots(self) -> list[SonarIssue]:
        issues: list[SonarIssue] = []
        page = 1
        while True:
            data = self._request(
                "/api/hotspots/search",
                {
                    "projectKey": self.project_key,
                    "status": "TO_REVIEW",
                    "ps": _PAGE_SIZE,
                    "p": page,
                },
            )
            raw_hotspots = data.get("hotspots", [])
            issues.extend(self._parse_hotspot(raw) for raw in raw_hotspots)
            total = int(data.get("paging", {}).get("total", len(issues)))
            if not raw_hotspots or page * _PAGE_SIZE >= total:
                return issues
            page += 1

    def fetch_metrics(self) -> list[SonarMetric]:
        data = self._request(
            "/api/measures/component",
            {
                "component": self.project_key,
                "metricKeys": ",".join(
                    [
                        "coverage",
                        "duplicated_lines_density",
                        "bugs",
                        "vulnerabilities",
                        "security_hotspots",
                        "code_smells",
                        "reliability_rating",
                        "security_rating",
                        "sqale_rating",
                    ],
                ),
            },
        )
        measures = data.get("component", {}).get("measures", [])
        result: list[SonarMetric] = []
        for item in measures:
            raw_value = str(item.get("value", ""))
            try:
                value = float(raw_value)
            except ValueError:
                continue
            result.append(SonarMetric(key=str(item.get("metric", "")), value=value, raw_value=raw_value))
        return result

    def _parse_issue(self, raw: dict) -> SonarIssue:
        component = str(raw.get("component", ""))
        path = _extract_path(component, self.project_key)
        key = str(raw.get("key", ""))
        rule = str(raw.get("rule", ""))
        return SonarIssue(
            key=key,
            rule=rule,
            rule_name=str(raw.get("message", rule)),
            issue_type=str(raw.get("type", "UNKNOWN")),
            severity=str(raw.get("severity", "UNKNOWN")),
            component=_component_from_path(path),
            path=path,
            line=_line(raw),
            message=str(raw.get("message", "")),
            status=str(raw.get("status", "")),
            url=f"{self.host_url}/project/issues?id={urllib.parse.quote(self.project_key)}&open={urllib.parse.quote(key)}",
        )

    def _parse_hotspot(self, raw: dict) -> SonarIssue:
        component = str(raw.get("component", ""))
        path = _extract_path(component, self.project_key)
        key = str(raw.get("key", ""))
        rule = str(raw.get("ruleKey", "security-hotspot"))
        return SonarIssue(
            key=key,
            rule=rule,
            rule_name=str(raw.get("message", rule)),
            issue_type="SECURITY_HOTSPOT",
            severity=str(raw.get("vulnerabilityProbability", "UNKNOWN")),
            component=_component_from_path(path),
            path=path,
            line=_line(raw),
            message=str(raw.get("message", "")),
            status=str(raw.get("status", "")),
            url=f"{self.host_url}/security_hotspots?id={urllib.parse.quote(self.project_key)}&hotspots={urllib.parse.quote(key)}",
        )


def _extract_path(component: str, project_key: str) -> str:
    prefix = f"{project_key}:"
    return component[len(prefix) :] if component.startswith(prefix) else component


def _component_from_path(path: str) -> str:
    first = path.split("/", 1)[0]
    if first in {"monitor-app", "engine-app", "telegram-bot-app", "platform-core"}:
        return first
    if first in {"scripts", ".github", "docs", "deploy", "config"}:
        return first
    return "project"


def _line(raw: dict) -> int | None:
    value = raw.get("line")
    if isinstance(value, int):
        return value
    text_range = raw.get("textRange") or {}
    start_line = text_range.get("startLine")
    return start_line if isinstance(start_line, int) else None
