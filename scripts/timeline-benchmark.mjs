import { spawnSync } from 'node:child_process';
import { readFileSync, statSync } from 'node:fs';
import { delimiter, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const source = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const project = resolve(process.env.EVENTLITE_BENCHMARK_PROJECT || source);
const output = resolve(process.argv[2] || join(source, 'target', 'benchmark', 'timeline.json'));
const label = process.argv[3] || 'working-source';
const delayMillis = process.argv[4] || '150';
if (!/^\d+$/.test(delayMillis) || Number(delayMillis) < 1 || Number(delayMillis) > 1000) {
  throw new Error('Use a simulated transport delay from 1 to 1000 ms.');
}
const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
let runtime;
try { runtime = readFileSync(join(project, 'target', 'benchmark-classpath.txt'), 'utf8').trim(); }
catch { throw new Error('Run scripts/query-benchmark.mjs first to prepare the runtime classpath.'); }
const classpath = [join(project, 'target', 'classes'), runtime].join(delimiter);
const started = Date.now();
const result = spawnSync(java, ['-cp', classpath, '--source', '17', join(source, 'scripts', 'TimelineBenchmark.java'),
  output, label, delayMillis], { cwd: project, stdio: 'inherit' });
if (result.error) throw result.error;
if (result.status !== 0) {
  console.error(result.signal ? `Timeline benchmark stopped with signal ${result.signal}.` : 'Timeline benchmark failed.');
  process.exitCode = result.status ?? 1;
} else {
  const receipt = JSON.parse(readFileSync(output, 'utf8'));
  if (statSync(output).mtimeMs < started || receipt.format !== 1 || receipt.source !== label
      || receipt.page_feed_calls !== 21 || receipt.injected_delay_ms !== Number(delayMillis)
      || !Array.isArray(receipt.samples_ms) || receipt.samples_ms.length !== 21
      || receipt.samples_ms.some(value => !Number.isFinite(value) || value < 0)) {
    throw new Error('Timeline benchmark did not produce a fresh, valid result.');
  }
}
