"""
ConPTY Subprocess wrapper with dedicated background worker thread and Win32 power management.
Runs on native Windows 10/11 PowerShell with full 2D screen grid and color fidelity.
"""

import asyncio
import ctypes
import logging
import os
import threading
from typing import Callable, Optional
from winpty import PtyProcess

logger = logging.getLogger("agent-bridge.pty")

# Win32 Power Management Constants
ES_CONTINUOUS = 0x80000000
ES_SYSTEM_REQUIRED = 0x00000001
ES_AWAYMODE_REQUIRED = 0x00000040


class PowerManager:
    """Manages Windows system execution states to prevent sleep with lid closed."""

    @staticmethod
    def acquire_keep_awake():
        try:
            res = ctypes.windll.kernel32.SetThreadExecutionState(
                ES_CONTINUOUS | ES_SYSTEM_REQUIRED | ES_AWAYMODE_REQUIRED
            )
            if res != 0:
                logger.info("Win32 Keep-Awake state locked (system will not sleep on idle/lid close)")
            else:
                logger.warning("SetThreadExecutionState returned 0")
        except Exception as e:
            logger.warning(f"Failed to acquire Win32 power lock: {e}")

    @staticmethod
    def release_keep_awake():
        try:
            ctypes.windll.kernel32.SetThreadExecutionState(ES_CONTINUOUS)
            logger.info("Win32 Keep-Awake state released")
        except Exception as e:
            logger.warning(f"Failed to release Win32 power lock: {e}")


class ConPtyBridge:
    def __init__(
        self,
        command: str,
        on_data: Callable[[str], None],
        on_exit: Callable[[int], None],
        rows: int = 32,
        cols: int = 90,
    ):
        self.command = command
        self.on_data = on_data
        self.on_exit = on_exit
        self.rows = rows
        self.cols = cols
        self.running = False
        self._proc: Optional[PtyProcess] = None
        self._reader_thread: Optional[threading.Thread] = None

    def start(self):
        PowerManager.acquire_keep_awake()
        logger.info(f"Spawning ConPTY process: {self.command} ({self.cols}x{self.rows})")
        self._proc = PtyProcess.spawn(self.command, dimensions=(self.rows, self.cols))
        self.running = True

        self._reader_thread = threading.Thread(
            target=self._read_worker,
            name="ConPty-Reader-Worker",
            daemon=True,
        )
        self._reader_thread.start()

    def _read_worker(self):
        exit_code = 0
        try:
            while self.running and self._proc.isalive():
                try:
                    chunk = self._proc.read(1024)
                    if chunk:
                        self.on_data(chunk)
                except EOFError:
                    break
        except Exception as e:
            logger.error(f"Error in ConPTY read worker: {e}")
        finally:
            self.running = False
            if self._proc:
                exit_code = self._proc.exitstatus or 0
            PowerManager.release_keep_awake()
            self.on_exit(exit_code)

    def write(self, data: str):
        """Writes raw string or ANSI escape sequences directly to stdin."""
        if self._proc and self._proc.isalive():
            logger.info(f"ConPTY stdin write ({len(data)} chars): {repr(data)}")
            self._proc.write(data)
        else:
            logger.warning(f"ConPTY write failed: process is not alive")

    def resize(self, rows: int, cols: int):
        """Resizes the ConPTY grid geometry when mobile client dimensions change."""
        if self._proc and self._proc.isalive():
            try:
                self.rows = max(10, rows)
                self.cols = max(30, cols)
                self._proc.setwinsize(self.rows, self.cols)
                logger.debug(f"ConPTY resized to {self.cols}x{self.rows}")
            except Exception as e:
                logger.warning(f"Failed to resize ConPTY: {e}")

    def terminate(self):
        """Terminates child process."""
        self.running = False
        if self._proc and self._proc.isalive():
            try:
                self._proc.terminate(force=True)
            except Exception as e:
                logger.warning(f"Error terminating process: {e}")
        PowerManager.release_keep_awake()
