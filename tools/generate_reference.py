#!/usr/bin/env python3
"""Enumerate positions and query a separately installed exact solver.

Usage: python3 tools/generate_reference.py /path/to/c4solver --book /path/to/7x6.book
"""

import argparse
import json
import random
import selectors
import struct
import subprocess
import time
import zlib
from pathlib import Path

BOTTOM = sum(1 << (7 * c) for c in range(7))
MASK = sum(63 << (7 * c) for c in range(7))


def mirror(bits):
    return sum(((bits >> (7 * c)) & 127) << (7 * (6 - c)) for c in range(7))


def four(bits):
    for d in (1, 6, 7, 8):
        p = bits & (bits >> d)
        if p & (p >> (2 * d)):
            return True
    return False


def drop(own, mask, col):
    move = (mask + (1 << (col * 7))) & (63 << (col * 7))
    if not ((mask + BOTTOM) & MASK & move):
        return None
    if four(own | move):
        return None
    return own ^ mask, mask | move


def canonical(own, mask):
    key = own + mask
    reflected = mirror(own) + mirror(mask)
    return min(key, reflected), reflected < key


class Oracle:
    def __init__(self, exe, book):
        self.process = subprocess.Popen(
            [str(Path(exe).resolve()), '-a', '-b', str(Path(book).resolve())],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
            bufsize=1,
        )
        self.selector = selectors.DefaultSelector()
        self.selector.register(self.process.stdout, selectors.EVENT_READ)

    def scores(self, sequence, timeout=15):
        self.process.stdin.write(sequence + '\n')
        self.process.stdin.flush()
        if not self.selector.select(timeout):
            raise TimeoutError(sequence)
        line = self.process.stdout.readline().strip()
        parts = line.split()
        values = list(map(int, parts[-7:]))
        if len(values) != 7:
            raise RuntimeError(line)
        return [-128 if x == -1000 else x for x in values]

    def close(self):
        self.process.terminate()
        self.process.wait()
        self.selector.close()


def main():
    args = argparse.ArgumentParser()
    args.add_argument('solver')
    args.add_argument('--book', required=True)
    args.add_argument('--depth', type=int, default=8)
    args.add_argument('--skip-opening', action='store_true')
    opts = args.parse_args()
    root = Path(__file__).resolve().parent.parent
    oracle = Oracle(opts.solver, opts.book)
    try:
        if not opts.skip_opening:
            level = {0: (0, 0, '')}
            all_positions = {}
            began = time.monotonic()
            for depth in range(opts.depth + 1):
                for key, (own, mask, sequence) in level.items():
                    all_positions[key] = (own, mask, sequence)
                print('Opening ply', depth, 'new positions', len(level), flush=True)
                following = {}
                if depth < opts.depth:
                    for own, mask, sequence in level.values():
                        for c in range(7):
                            child = drop(own, mask, c)
                            if child is not None:
                                key, _ = canonical(*child)
                                following.setdefault(key, (*child, sequence + str(c + 1)))
                level = following
            payload = bytearray()
            for i, (key, (own, mask, seq)) in enumerate(sorted(all_positions.items())):
                scores = oracle.scores(seq)
                if canonical(own, mask)[1]:
                    scores.reverse()
                payload.extend(struct.pack('>Q7b', key, *scores))
                if i % 10000 == 0:
                    print('Analyzed', i, '/', len(all_positions), flush=True)
            header = struct.pack(
                '>IHBBII', 0x5034424B, 1, opts.depth, 0,
                len(all_positions), zlib.crc32(payload),
            )
            out = root / 'resources/Puissance4/openings.bin'
            out.write_bytes(header + payload)
            metadata = {
                'format': 1,
                'maximum_ply': opts.depth,
                'positions': len(all_positions),
                'bytes': out.stat().st_size,
                'generation': (
                    'Own exhaustive enumeration of legal non-terminal positions; '
                    'exact move scores queried with Pascal Pons Connect4 solver '
                    '(reference tool only).'
                ),
            }
            (root / 'resources/Puissance4/openings.json').write_text(
                json.dumps(metadata, indent=2) + '\n'
            )
            print(
                'Opening book finished:', out.stat().st_size, 'bytes,',
                round(time.monotonic() - began, 1), 'seconds', flush=True,
            )
        rng = random.Random(20261008)
        cases = []
        seen = set()
        targets = (
            [rng.randrange(8, 13) for _ in range(12)]
            + [rng.randrange(16, 25) for _ in range(36)]
            + [rng.randrange(26, 35) for _ in range(24)]
        )
        for target in targets:
            for attempt in range(300):
                own = mask = 0
                seq = ''
                for ply in range(target):
                    cols = list(range(7))
                    rng.shuffle(cols)
                    for c in cols:
                        child = drop(own, mask, c)
                        if child is not None:
                            own, mask = child
                            seq += str(c + 1)
                            break
                    else:
                        break
                if len(seq) != target or seq in seen:
                    continue
                possible = (mask + BOTTOM) & MASK
                if any(
                    four(own | ((mask + (1 << (c * 7))) & (63 << (c * 7))))
                    for c in range(7) if possible & (63 << (c * 7))
                ):
                    continue
                try:
                    scores = oracle.scores(seq, 15)
                except TimeoutError:
                    oracle.close()
                    oracle = Oracle(opts.solver, opts.book)
                    continue
                cases.append((seq, scores))
                seen.add(seq)
                break
            print('Reference case', len(cases), 'target', target, flush=True)
        out = root / 'tests/data/exact-positions.csv'
        out.write_text(
            '# Own deterministic positions; exact scores per column; -128 = full column.\n'
            '# Positive = forced win, zero = draw, negative = forced loss; '
            'magnitude prefers faster wins/slower losses.\n'
            + ''.join(
                seq + ',' + ','.join(map(str, scores)) + '\n'
                for seq, scores in cases
            )
        )
        print('Reference corpus:', len(cases), 'positions', flush=True)
    finally:
        oracle.close()


if __name__ == '__main__':
    main()
