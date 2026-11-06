"""
ANSI-stripped prompt detection and action card context extractor.
Recognizes interactive prompts across AGY, OpenCode, and Claude Code.
"""

import re
from typing import Any, Dict, List, Optional

# Match all ANSI escape sequences, including color, cursor motion, and mode sets
ANSI_STRIP_REGEX = re.compile(
    r"\x1B(?:[@-Z\\-_]|\[[0-?]*[ -/]*[@-~])|"
    r"\x1B\][0-9];[^\x07\x1B]*[\x07\x1B\\]"
)


class PromptDetector:
    def __init__(self):
        # Common prompt patterns
        self.yn_pattern = re.compile(r"(\([yY]/[nN]\)|\[[yY]/[nN]\])", re.IGNORECASE)
        self.numbered_choice_pattern = re.compile(r"\[(\d+)\]\s*([^\[\n\r]+)")
        self.question_prefix_pattern = re.compile(r"^\s*(\?|[?？])\s*(.+)$")
        self.permission_pattern = re.compile(
            r"(allow|permission|overwrite|approve|proceed|continue|execute)\s*[\?:]",
            re.IGNORECASE,
        )
        self.file_path_pattern = re.compile(r"([a-zA-Z0-9_\-\.\/]+\.[a-zA-Z0-9_\-]+)")

    @staticmethod
    def strip_ansi(text: str) -> str:
        """Removes all ANSI escape codes, returning plain readable text."""
        if not text:
            return ""
        return ANSI_STRIP_REGEX.sub("", text)

    def extract_diff_preview(self, lines: List[str]) -> Optional[str]:
        """Extracts recent unified diff lines if present."""
        diff_lines = []
        for line in reversed(lines[-20:]):
            clean = line.strip()
            if clean.startswith(("+", "-", "@@")):
                diff_lines.insert(0, clean)
            elif diff_lines and not clean.startswith(("+", "-", "@@")):
                break
        return "\n".join(diff_lines[:6]) if diff_lines else None

    def analyze(self, raw_buffer: str) -> Optional[Dict[str, Any]]:
        """
        Analyzes the recent terminal output buffer to detect if the agent is waiting.
        Returns a structured prompt dict if detected, otherwise None.
        """
        clean_text = self.strip_ansi(raw_buffer)
        if not clean_text:
            return None

        lines = [l.strip() for l in clean_text.splitlines() if l.strip()]
        if not lines:
            return None

        recent_lines = lines[-10:]
        recent_block = "\n".join(recent_lines)
        last_line = lines[-1]

        # 1. Numbered options pattern (e.g. AGY "[1] Yes, overwrite [2] No, skip")
        numbered_matches = list(self.numbered_choice_pattern.finditer(recent_block))
        if len(numbered_matches) >= 2:
            options: List[Dict[str, str]] = []
            for match in numbered_matches:
                options.append({
                    "key": match.group(1),
                    "label": match.group(2).strip()
                })
            
            headline = "Action Approval Required"
            for candidate in reversed(recent_lines[:-1]):
                if not candidate.startswith("[") and ("?" in candidate or ":" in candidate):
                    headline = candidate
                    break

            target_match = self.file_path_pattern.search(recent_block)
            diff_preview = self.extract_diff_preview(lines)

            return {
                "mode": "NUMBERED",
                "headline": headline,
                "target": target_match.group(1) if target_match else None,
                "diff_preview": diff_preview,
                "options": options,
            }

        # 2. Binary Yes/No confirmation (e.g. "Do you want to proceed? (y/N)")
        if self.yn_pattern.search(last_line) or (
            self.permission_pattern.search(last_line) and any(kw in last_line.lower() for kw in ["(y/n)", "[y/n]", "y/n"])
        ):
            target_match = self.file_path_pattern.search(recent_block)
            return {
                "mode": "CONFIRM",
                "headline": last_line,
                "target": target_match.group(1) if target_match else None,
                "diff_preview": self.extract_diff_preview(lines),
                "options": [
                    {"key": "y", "label": "Yes / Approve"},
                    {"key": "n", "label": "No / Reject"},
                ],
            }

        # 3. Ink.js / Inquirer raw-mode cursor prompt (e.g. OpenCode / Claude Code "? Select action:")
        q_match = self.question_prefix_pattern.match(last_line)
        if q_match:
            target_match = self.file_path_pattern.search(recent_block)
            return {
                "mode": "RAW_VT",
                "headline": q_match.group(2),
                "target": target_match.group(1) if target_match else None,
                "diff_preview": self.extract_diff_preview(lines),
                "hint": "Use navigation toolbar (▲, ▼, Space, Enter)",
                "options": [],
            }

        return None
