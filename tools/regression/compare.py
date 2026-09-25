"""
Compares the Java and the Python dumps line by line.

Usage:
    python compare.py <python dump> <java dump>

Prints only counts and the paths of the files that differ, never their content:
the dumps hold personal data.
"""

import json
import sys


def load(path):
    return {tuple(item['files']): item for item in map(json.loads, open(path, encoding='utf-8'))}


def main():
    python, java = load(sys.argv[1]), load(sys.argv[2])
    same, crashes, different = 0, 0, []
    for files, expected in python.items():
        actual = java.get(files)
        if actual == expected:
            same += 1
            crashes += 'crash' in expected
            continue
        fields = sorted(k for k in set(expected) | set(actual or {})
                        if (actual or {}).get(k) != expected.get(k))
        different.append((files, fields))
    print(f'same: {same} (both crashed: {crashes}), different: {len(different)}')
    for files, fields in different:
        print(' | '.join(files), '->', ', '.join(fields))


if __name__ == '__main__':
    main()
