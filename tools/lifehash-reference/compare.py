#!/usr/bin/env python3
"""Reproduce the C++ fixtures or compare seeded cases with a pinned C++ oracle."""

import argparse
import hashlib
import io
import itertools
from pathlib import Path
import subprocess
import tarfile
import tempfile


COMMIT = "0444dbed5615fbc9a98163608c6499c025b7873b"
CPP_NUMERIC_FLAGS = ["-std=c++17", "-O3", "-ffp-contract=off"]
# The pinned source omits these direct includes and otherwise fails to compile
# with newer libstdc++. Supply declarations without modifying its source.
CPP_HEADER_FLAGS = ["-include", "stdexcept", "-include", "cstring"]
SEED = "SeedSigner LifeHash public test fingerprint "
NAMED = ["73c5da0a", "a63dbf9c", "9a6087a6", "00000000", "ffffffff",
         "00000001", "00000100", "00010000", "01000000", "01234567",
         "12345678", "deadbeef", "f23c1da1", "b73ca8ab", "b8688df1"]
ROOT = Path(__file__).resolve().parents[2]


def cases(seed, count):
    # Decode the displayed fingerprint directly, with no byte reversal.
    fingerprints = dict.fromkeys(NAMED + [hashlib.sha256((seed + str(i)).encode()).hexdigest()[:8]
                                         for i in range(count)])
    for fingerprint in fingerprints:
        yield "data", 1, 1, 0, fingerprint
    # Exercise every version and public entry point, SHA-256 block boundaries,
    # module replication, and alpha insertion in addition to Sparrow's profile.
    for version in range(5):
        for size in (0, 1, 3, 4, 31, 32, 33, 55, 56, 63, 64, 65, 257):
            data = bytes(i % 256 for i in range(size)).hex() or "-"
            yield "data", version, 1, 0, data
        for module_size in (1, 2, 3):
            for alpha in (0, 1):
                digest = hashlib.sha256(b"Toucan LifeHash digest fixture").hexdigest()
                yield "digest", version, module_size, alpha, digest
        yield "utf8", version, 1, 0, "Hello".encode().hex()
        yield "utf8", version, 2, 1, "LifeHash \u03bb \U0001f426".encode().hex()


def export_reference(reference, destination):
    # Export committed objects, never a possibly edited working tree. This uses
    # only a local clone and cannot fetch code or modify that clone.
    archive = subprocess.check_output(["git", "-C", str(reference), "archive", COMMIT, "src"])
    with tarfile.open(fileobj=io.BytesIO(archive)) as source:
        for member in source.getmembers():
            path = Path(member.name)
            if path.is_absolute() or ".." in path.parts:
                raise ValueError("Unexpected reference archive entry: " + member.name)
            # src/include/lifehash.h is a packaging symlink; the oracle needs
            # only the regular source files directly inside src.
            if len(path.parts) != 2 or not member.isfile():
                continue
            target = destination / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(source.extractfile(member).read())


def read_result(line, expected_id):
    fields = line.split()
    if len(fields) != 4 or int(fields[0]) != expected_id:
        raise ValueError("Missing, reordered, or malformed oracle output at case " + str(expected_id))
    width, height = map(int, fields[1:3])
    return width, height, bytes.fromhex(fields[3])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference", type=Path, required=True, help="local bc-lifehash Git clone containing the pinned commit")
    parser.add_argument("--count", type=int, default=4096, help="number of deterministic four-byte inputs (default: 4096)")
    parser.add_argument("--seed", default=SEED, help="UTF-8 prefix hashed together with each decimal index")
    parser.add_argument("--cxx", default="c++")
    parser.add_argument("--java", default="java")
    parser.add_argument("--javac", default="javac")
    parser.add_argument("--write-fixtures", type=Path, help="write expected SHA-256 values generated only by C++")
    parser.add_argument("--write-numeric-fixtures", type=Path, help="write exact numeric boundary values generated only by C++")
    args = parser.parse_args()
    if args.count < 0:
        parser.error("--count must be non-negative")
    inputs = list(cases(args.seed, args.count))
    print(f"Reference: bc-lifehash {COMMIT}; flags: {' '.join(CPP_NUMERIC_FLAGS + CPP_HEADER_FLAGS)}", flush=True)
    print(f"Seed: {args.seed!r}; count: {args.count}; total cases: {len(inputs)}", flush=True)

    with tempfile.TemporaryDirectory(prefix="toucan-reference-") as temp:
        work = Path(temp)
        export_reference(args.reference, work)
        cpp = work / "oracle"
        subprocess.run([args.cxx, *CPP_NUMERIC_FLAGS, *CPP_HEADER_FLAGS, "-I", str(work / "src"),
                        str(Path(__file__).with_name("oracle.cpp")),
                        *map(str, sorted((work / "src").glob("*.cpp"))), "-o", str(cpp)], check=True)
        classes = work / "classes"
        classes.mkdir()
        subprocess.run([args.javac, "--release", "17", "-d", str(classes),
                        *map(str, sorted((ROOT / "src/main/java/com").rglob("*.java"))),
                        str(Path(__file__).with_name("LifeHashOracle.java"))], check=True)
        request = work / "inputs.txt"
        request.write_text("".join(f"{i} {' '.join(map(str, case))}\n" for i, case in enumerate(inputs)), encoding="ascii")
        outputs = [work / "cpp.txt", work / "java.txt"]
        commands = [[str(cpp)], [args.java, "-Xmx256m", "-cp", str(classes), "LifeHashOracle"]]
        processes = []
        try:
            for command, output in zip(commands, outputs):
                with request.open("rb") as stdin, output.open("wb") as stdout:
                    processes.append(subprocess.Popen(command, stdin=stdin, stdout=stdout))
            for process in processes:
                if process.wait() != 0:
                    raise RuntimeError("Oracle failed: " + str(process.args))
        finally:
            for process in processes:
                if process.poll() is None:
                    process.kill()
                    process.wait()

        fixtures = [f"# bc-lifehash {COMMIT}; {' '.join(CPP_NUMERIC_FLAGS)}\n",
                    f"# seed={ascii(args.seed)}; count={args.count}; see tools/lifehash-reference/README.md\n",
                    "# mode version module_size alpha input_hex width height sha256_of_pixel_bytes\n"]
        with outputs[0].open() as reference, outputs[1].open() as actual:
            for i, pair in enumerate(itertools.zip_longest(reference, actual)):
                if i >= len(inputs) or None in pair:
                    raise ValueError("Oracle result count does not match input count")
                expected_width, expected_height, expected = read_result(pair[0], i)
                width, height, colors = read_result(pair[1], i)
                mode, version, module_size, alpha, data = inputs[i]
                context = f"case={i} mode={mode} version={version} module={module_size} alpha={alpha} input={data}"
                components = 4 if alpha else 3
                if len(expected) != expected_width * expected_height * components:
                    raise ValueError("Invalid reference dimensions: " + context)
                if (width, height, len(colors)) != (expected_width, expected_height, len(expected)):
                    raise AssertionError("Image dimensions/length differ: " + context)
                if colors != expected:
                    offset = next(j for j, (a, b) in enumerate(zip(expected, colors)) if a != b)
                    pixel, channel = divmod(offset, components)
                    raise AssertionError(f"{context}; pixel=({pixel % width},{pixel // width}) "
                                         f"channel={'RGBA'[channel]} byte={offset} C++={expected[offset]} Java={colors[offset]}")
                fixtures.append(" ".join(map(str, (*inputs[i], width, height, hashlib.sha256(expected).hexdigest()))) + "\n")
            if len(fixtures) - 3 != len(inputs):
                raise ValueError("Oracle result count does not match input count")
        if args.write_fixtures:
            args.write_fixtures.write_text("".join(fixtures), encoding="ascii")
            print("Wrote C++ fixtures: " + str(args.write_fixtures))
        if args.write_numeric_fixtures:
            numeric = subprocess.check_output([str(cpp), "--numeric"], text=True)
            args.write_numeric_fixtures.write_text(fixtures[0] + numeric, encoding="ascii")
            print("Wrote C++ numeric fixtures: " + str(args.write_numeric_fixtures))
        print(f"PASS: {len(inputs)} images match the C++ reference byte-for-byte.")


if __name__ == "__main__":
    main()
