import os
import sys
import pytest

# Ensure agent-bridge directory is on sys.path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from ring_buffer import AgentState, TerminalRingBuffer
from prompt_detector import PromptDetector


def test_ring_buffer_capacity():
    buf = TerminalRingBuffer(capacity=5)
    buf.set_session("test-1")
    for i in range(10):
        buf.append(f"line_{i}\n")

    snapshot = buf.get_snapshot()
    assert snapshot["session_id"] == "test-1"
    assert snapshot["state"] == AgentState.RUNNING
    # Should only retain last 5 items
    assert "line_0" not in snapshot["history"]
    assert "line_5\n" in snapshot["history"]
    assert "line_9\n" in snapshot["history"]


def test_ring_buffer_state_transitions():
    buf = TerminalRingBuffer(capacity=10)
    buf.set_session("sess-abc")
    assert buf.state == AgentState.RUNNING

    prompt = {"headline": "Confirm overwrite", "options": []}
    buf.set_state(AgentState.WAITING_FOR_INPUT, prompt)
    assert buf.state == AgentState.WAITING_FOR_INPUT
    assert buf.active_prompt == prompt

    buf.dismiss_prompt()
    assert buf.state == AgentState.RUNNING
    assert buf.active_prompt is None


def test_ansi_stripping():
    colored = "\x1b[32m[1] Yes\x1b[0m \x1b[31m[2] No\x1b[0m"
    clean = PromptDetector.strip_ansi(colored)
    assert clean == "[1] Yes [2] No"


def test_prompt_detection_numbered():
    detector = PromptDetector()
    output = """
    Writing files to disk...
    File already exists: src/auth/session.ts
    Do you want to overwrite?
    [1] Yes, overwrite
    [2] No, skip
    """
    res = detector.analyze(output)
    assert res is not None
    assert res["mode"] == "NUMBERED"
    assert len(res["options"]) == 2
    assert res["options"][0]["key"] == "1"
    assert "Yes" in res["options"][0]["label"]
    assert res["options"][1]["key"] == "2"


def test_prompt_detection_yn_confirm():
    detector = PromptDetector()
    output = "Do you want to proceed with database migration? (y/N)"
    res = detector.analyze(output)
    assert res is not None
    assert res["mode"] == "CONFIRM"
    assert len(res["options"]) == 2
    assert res["options"][0]["key"] == "y"


def test_prompt_detection_ink_question():
    detector = PromptDetector()
    output = "? Select the tool to execute:"
    res = detector.analyze(output)
    assert res is not None
    assert res["mode"] == "RAW_VT"
    assert "Select the tool to execute" in res["headline"]
