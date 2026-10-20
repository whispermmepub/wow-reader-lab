#!/usr/bin/env python3
"""Synthetic transfer latency comparison, not a live Drive or phone-speed prediction."""
import argparse
import json
from pathlib import Path
import statistics
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
HARNESS = r'''
package com.whisper.wowreader;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
public class RestoreTransferBenchmark {
    static class Task implements RestoreTransfers.Task {
        public boolean download(File scratch) throws Exception {
            Thread.sleep(12); Files.write(scratch.toPath(), new byte[4096]); return true;
        }
        public boolean install(File scratch) throws Exception {
            if (scratch.length() != 4096) throw new AssertionError("incomplete transfer");
            Thread.sleep(2); return true;
        }
    }
    public static void main(String[] args) throws Exception {
        File cache = new File(args[0]);
        for (int run = 0; run < 3; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < 120; i++) {
                File scratch = File.createTempFile("serial-", ".pending", cache);
                try { Task task = new Task(); task.download(scratch); task.install(scratch); }
                finally { scratch.delete(); }
            }
            long serial = (System.nanoTime() - start) / 1000000;
            List<RestoreTransfers.Task> tasks = new ArrayList<>();
            for (int i = 0; i < 120; i++) tasks.add(new Task());
            start = System.nanoTime();
            if (RestoreTransfers.run(cache, tasks) != 120) throw new AssertionError("lost objects");
            long bounded = (System.nanoTime() - start) / 1000000;
            if (cache.list().length != 0) throw new AssertionError("scratch leak");
            System.out.println(serial + "," + bounded);
        }
    }
}
'''

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--json', type=Path, required=True)
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix='wow-restore-benchmark-') as directory:
        out = Path(directory)
        harness = out / 'RestoreTransferBenchmark.java'
        harness.write_text(HARNESS)
        subprocess.run(['javac', '-d', str(out), str(ROOT / 'app/src/main/java/com/whisper/wowreader/RestoreTransfers.java'), str(harness)], check=True)
        cache = out / 'cache'
        cache.mkdir()
        text = subprocess.check_output(['java', '-cp', str(out), 'com.whisper.wowreader.RestoreTransferBenchmark', str(cache)], text=True)
    runs = [dict(zip(['serialMs', 'boundedMs'], map(int, line.split(',')))) for line in text.strip().splitlines()]
    serial = statistics.median(row['serialMs'] for row in runs)
    bounded = statistics.median(row['boundedMs'] for row in runs)
    report = {'kind': 'synthetic latency; not live Google Drive or phone timing', 'objects': 120,
              'simulatedTransferMs': 12, 'simulatedSerialCommitMs': 2, 'bytesPerObject': 4096,
              'maxParallelTransfers': 3, 'runs': runs, 'medianSerialMs': serial,
              'medianBoundedMs': bounded, 'speedup': round(serial / bounded, 2),
              'sameInstalledObjectCount': True, 'scratchCleanupVerified': True}
    args.json.parent.mkdir(parents=True, exist_ok=True)
    args.json.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))

if __name__ == '__main__':
    main()
