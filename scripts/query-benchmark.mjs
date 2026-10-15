import {spawnSync} from 'node:child_process';
import {mkdirSync, readFileSync, statSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const sourceRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const projectRoot = path.resolve(process.env.EVENTLITE_BENCHMARK_PROJECT || sourceRoot);
const output = path.resolve(process.argv[2] || path.join(sourceRoot, 'target', 'benchmark', 'queries.json'));
const label = process.argv[3] || 'working-source';
const sizes = process.argv[4] || '1000,10000,100000';
if (!/^\d+(,\d+)*$/.test(sizes)) throw new Error('Use a comma-separated list of event counts.');
mkdirSync(path.dirname(output), {recursive: true});
const windows = process.platform === 'win32';
const wrapper = path.join(projectRoot, windows ? 'mvnw.cmd' : 'mvnw');
const classpathFile = path.join(projectRoot, 'target', 'benchmark-classpath.txt');
function run(command, args, options = {}) {
  const result = spawnSync(command, args, {cwd: projectRoot, stdio: 'inherit', ...options});
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status || 1);
}
const mavenArgs = ['-B', '--no-transfer-progress', '-DskipTests', 'compile', 'dependency:build-classpath',
  '-DincludeScope=runtime', `-Dmdep.outputFile=${classpathFile}`];
if (windows) {
  const quote = value => `'${value.replaceAll("'", "''")}'`;
  const script = `& ${[wrapper, ...mavenArgs].map(quote).join(' ')}; exit $LASTEXITCODE`;
  const powershell = path.join(process.env.SystemRoot || 'C:\\Windows', 'System32', 'WindowsPowerShell', 'v1.0', 'powershell.exe');
  run(powershell, ['-NoProfile', '-EncodedCommand', Buffer.from(script, 'utf16le').toString('base64')]);
} else run('sh', [wrapper, ...mavenArgs]);
const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', windows ? 'java.exe' : 'java') : 'java';
const dependencies = readFileSync(classpathFile, 'utf8').trim().split(path.delimiter)
  .filter(file => !path.basename(file).startsWith('spring-boot-devtools-'));
const classpath = [path.join(projectRoot, 'target', 'classes'), ...dependencies].join(path.delimiter);
const started = Date.now();
run(java, ['-Dspring.devtools.restart.enabled=false', '-Xms512m', '-Xmx1024m', '-cp', classpath, '--source', '17',
  path.join(sourceRoot, 'scripts', 'QueryBenchmark.java'), output, label, sizes]);
if (statSync(output).mtimeMs < started) throw new Error('Benchmark did not produce a fresh report.');
