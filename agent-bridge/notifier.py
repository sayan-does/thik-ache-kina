"""
Push notification dispatcher using open-source ntfy.sh relay.
Transmits strictly opaque metadata to prevent leakage of source code or secrets.
"""

import logging
import time
from typing import Any, Dict, Optional
import uuid
import requests

logger = logging.getLogger("agent-bridge.notifier")


class Notifier:
    def __init__(
        self,
        topic: str = "",
        server_url: str = "https://ntfy.sh",
        enabled: bool = True,
        cooldown_seconds: int = 15,
    ):
        self.server_url = server_url.rstrip("/")
        self.enabled = enabled
        self.cooldown_seconds = cooldown_seconds
        self.topic = topic or f"agent-remote-{uuid.uuid4().hex[:12]}"
        self.last_sent: float = 0.0

    def send_prompt_alert(self, session_id: str, prompt_data: Dict[str, Any]) -> bool:
        """
        Sends an alert that the agent is waiting for user decision.
        Transmits opaque metadata only (no raw code or secrets).
        """
        if not self.enabled:
            return False

        now = time.time()
        if now - self.last_sent < self.cooldown_seconds:
            logger.debug("Suppressing notification due to cooldown window")
            return False

        headline = prompt_data.get("headline", "Agent is waiting for approval")
        # Sanitize headline to keep it short and clean
        if len(headline) > 80:
            headline = headline[:77] + "..."

        url = f"{self.server_url}/{self.topic}"
        headers = {
            "Title": "CLI Agent Needs Input",
            "Priority": "high",
            "Tags": "warning,computer",
            "Click": f"agentremote://session/{session_id}",
        }

        body = f"{headline}\nTap to review and approve from your phone."

        try:
            resp = requests.post(url, data=body.encode("utf-8"), headers=headers, timeout=5)
            if resp.status_code == 200:
                self.last_sent = now
                logger.info(f"Push notification sent successfully to topic: {self.topic}")
                return True
            else:
                logger.warning(f"ntfy returned status code {resp.status_code}: {resp.text}")
                return False
        except Exception as e:
            logger.warning(f"Failed to transmit push notification: {e}")
            return False
