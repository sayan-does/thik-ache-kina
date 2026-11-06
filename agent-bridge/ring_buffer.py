"""
In-memory circular ring buffer and state machine for terminal output replay.
Ensures mobile clients reconnect without screen flashing, lost context, or missed prompts.
"""

from collections import deque
import threading
import time
from typing import Any, Dict, List, Optional


class AgentState:
    IDLE = "IDLE"
    RUNNING = "RUNNING"
    WAITING_FOR_INPUT = "WAITING_FOR_INPUT"
    COMPLETED = "COMPLETED"
    ERROR = "ERROR"


class TerminalRingBuffer:
    def __init__(self, capacity: int = 1000):
        self.capacity = capacity
        self._buffer: deque[str] = deque(maxlen=capacity)
        self._lock = threading.Lock()
        self.state: str = AgentState.IDLE
        self.active_prompt: Optional[Dict[str, Any]] = None
        self.last_updated: float = time.time()
        self.session_id: str = ""

    def set_session(self, session_id: str):
        with self._lock:
            self.session_id = session_id
            self.state = AgentState.RUNNING
            self.active_prompt = None
            self._buffer.clear()
            self.last_updated = time.time()

    def append(self, text: str):
        if not text:
            return
        with self._lock:
            self._buffer.append(text)
            self.last_updated = time.time()

    def set_state(self, state: str, prompt: Optional[Dict[str, Any]] = None):
        with self._lock:
            self.state = state
            self.active_prompt = prompt
            self.last_updated = time.time()

    def dismiss_prompt(self):
        with self._lock:
            self.active_prompt = None
            if self.state == AgentState.WAITING_FOR_INPUT:
                self.state = AgentState.RUNNING
            self.last_updated = time.time()

    def get_snapshot(self) -> Dict[str, Any]:
        with self._lock:
            return {
                "session_id": self.session_id,
                "state": self.state,
                "history": "".join(self._buffer),
                "active_prompt": self.active_prompt,
                "timestamp": self.last_updated,
            }

    def clear(self):
        with self._lock:
            self._buffer.clear()
            self.active_prompt = None
            self.state = AgentState.IDLE
            self.last_updated = time.time()
