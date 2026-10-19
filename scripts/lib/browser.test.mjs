import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { isAbsolute, join, relative } from 'node:path';
import test from 'node:test';
import { startBrowser } from './browser.mjs';

async function withBinary(binary, action) {
  const previous = process.env.CHROME_PATH;
  process.env.CHROME_PATH = binary;
  try { await action(); }
  finally {
    if (previous === undefined) delete process.env.CHROME_PATH;
    else process.env.CHROME_PATH = previous;
  }
}

test('an exited browser reports its stderr and records an absolute profile', { timeout: 10000 }, async () => {
  const directory = await mkdtemp(join(tmpdir(), 'eventlite-browser-exit-'));
  try {
    // Node is a real executable that rejects Chrome flags and exits immediately.
    await withBinary(process.execPath, async () => {
      await assert.rejects(startBrowser(relative(process.cwd(), directory)), error => {
        assert.match(error.message, /Chrome exited before exposing/);
        assert.match(error.message, /Chrome stderr:/);
        assert.match(error.message, /--headless/);
        return true;
      });
    });
    const launch = JSON.parse(await readFile(join(directory, 'chrome-launch.json'), 'utf8'));
    assert.equal(launch.status, 'startup failed');
    assert.ok(isAbsolute(launch.profile));
    assert.notEqual(launch.exitCode, 0);
    assert.ok(!launch.args.includes('--no-sandbox'));
    assert.match(await readFile(join(directory, 'chrome-stderr.log'), 'utf8'), /--headless/);
  } finally { await rm(directory, { recursive: true, force: true }); }
});

test('an executable spawn failure is reported and saved without waiting for the endpoint timeout', { timeout: 10000 }, async () => {
  const directory = await mkdtemp(join(tmpdir(), 'eventlite-browser-spawn-'));
  try {
    // A directory exists but cannot be spawned as an executable on either OS.
    await withBinary(directory, async () => {
      await assert.rejects(startBrowser(directory), /Chrome could not start:/);
    });
    const launch = JSON.parse(await readFile(join(directory, 'chrome-launch.json'), 'utf8'));
    assert.equal(launch.status, 'startup failed');
    assert.ok(launch.spawnError);
  } finally { await rm(directory, { recursive: true, force: true }); }
});
