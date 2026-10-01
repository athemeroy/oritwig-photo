#!/usr/bin/env python3
"""Verify all locally vendored primary engine inputs against the immutable lock."""
from pathlib import Path
import hashlib,json
root=Path(__file__).resolve().parents[1]
lock=json.loads((root/'vendor/telegram-engine/local-source-lock.json').read_text())
assert len(lock['engine_commit'])==40 and len(lock['engine_tree_sha'])==40
for relative,expected in lock['engine_files'].items():
    path=(root/'engine'/relative).resolve()
    assert path.is_relative_to((root/'engine').resolve()), relative
    assert hashlib.sha256(path.read_bytes()).hexdigest()==expected, relative
actual={str(p.relative_to(root/'engine')) for p in (root/'engine/src').rglob('*') if p.is_file()}|{'build.gradle'}
assert actual==set(lock['engine_files']), 'Unexpected engine source file'
print('Verified',len(actual),'engine inputs at',lock['engine_commit'])
