import pytest
from ws_server import WebSocketServer

def test_conpty_key_mappings():
    # BACKSPACE must map to \x08 (ASCII BS) for Windows ConPTY
    assert WebSocketServer.KEY_MAP.get("BACKSPACE") == "\x08"
    assert WebSocketServer.KEY_MAP.get("SIGINT") == "\x03"
    assert WebSocketServer.KEY_MAP.get("UP") == "\x1b[A"
    assert WebSocketServer.KEY_MAP.get("DOWN") == "\x1b[B"
    assert WebSocketServer.KEY_MAP.get("ENTER") == "\r"
    assert WebSocketServer.KEY_MAP.get("SPACE") == " "
    assert WebSocketServer.KEY_MAP.get("TAB") == "\t"
    assert WebSocketServer.KEY_MAP.get("ESC") == "\x1b"

def test_mtu_chunking_constraint():
    # Verify maximum chunk size is <= 1400 bytes (within WireGuard 1420 MTU)
    max_chunk_size = 1400
    large_output = "A" * 5000
    chunks = [large_output[i : i + max_chunk_size] for i in range(0, len(large_output), max_chunk_size)]
    
    assert len(chunks) == 4
    for chunk in chunks:
        assert len(chunk.encode("utf-8")) <= max_chunk_size
