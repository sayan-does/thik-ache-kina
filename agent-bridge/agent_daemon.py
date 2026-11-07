"""
Standalone Entrypoint for Agent Remote Headless Daemon (v2.0)
Can be launched independently in background or supervised by Windows Task Scheduler.
"""
import sys
import os

# Ensure current directory is in sys.path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from bridge import main

if __name__ == "__main__":
    main()
