# -*- mode: python ; coding: utf-8 -*-
import os
import sys

block_cipher = None

bridge_dir = os.path.abspath(SPECPATH)
desktop_dir = os.path.join(bridge_dir, 'desktop')

added_files = [
    (os.path.join(desktop_dir, 'assets', 'xterm', '*'), os.path.join('desktop', 'assets', 'xterm')),
    (os.path.join(desktop_dir, 'resources', 'icon.ico'), os.path.join('desktop', 'resources')),
]

hidden_imports = [
    'PySide6.QtWebEngineWidgets',
    'PySide6.QtWebEngineCore',
    'PySide6.QtCore',
    'PySide6.QtGui',
    'PySide6.QtWidgets',
    'pywinpty',
    'cryptography',
    'cryptography.hazmat.primitives.asymmetric.ec',
    'cryptography.hazmat.primitives.serialization',
    'qrcode',
    'PIL',
    'aiohttp',
    'yaml',
]

a = Analysis(
    [os.path.join(desktop_dir, 'app.py')],
    pathex=[bridge_dir, desktop_dir],
    binaries=[],
    datas=added_files,
    hiddenimports=hidden_imports,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    win_no_prefer_redirects=False,
    win_private_assemblies=False,
    cipher=block_cipher,
    noarchive=False,
)

pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name='AgentRemote',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    console=False,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
    icon=os.path.join(desktop_dir, 'resources', 'icon.ico'),
)

coll = COLLECT(
    exe,
    a.binaries,
    a.zipfiles,
    a.datas,
    strip=False,
    upx=True,
    upx_exclude=[],
    name='AgentRemote',
)
