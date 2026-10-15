import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve, sep } from 'node:path';
import test from 'node:test';
import { backupDatabase, restoreDatabase } from '../../../scripts/database-snapshot.mjs';

async function fixture(action) {
  const temporaryRoot = resolve(tmpdir());
  const directory = await mkdtemp(join(temporaryRoot, 'eventlite-snapshot-test-'));
  try { await action(directory); }
  finally {
    const checked = resolve(directory);
    assert.ok(checked.startsWith(`${temporaryRoot}${sep}`));
    await rm(checked, { recursive: true, force: true });
  }
}

test('snapshot preserves bytes and refuses to overwrite existing data', () => fixture(async directory => {
  const source = join(directory, 'source');
  const target = join(directory, 'restored');
  const bytes = Buffer.from('database bytes, not a real application database');
  await writeFile(`${source}.mv.db`, bytes);
  const manifest = await backupDatabase(source, join(directory, 'snapshot'), { offlineConfirmed: true });
  assert.equal(manifest.bytes, bytes.length);
  assert.equal((await restoreDatabase(join(directory, 'snapshot'), target, { offlineConfirmed: true })).sha256, manifest.sha256);
  assert.deepEqual(await readFile(`${target}.mv.db`), bytes);
  await assert.rejects(restoreDatabase(join(directory, 'snapshot'), source, { offlineConfirmed: true }), /never overwritten/);
  assert.deepEqual(await readFile(`${source}.mv.db`), bytes);
}));

test('modified snapshots are rejected before any restored database is created', () => fixture(async directory => {
  const source = join(directory, 'source');
  const snapshot = join(directory, 'snapshot');
  await writeFile(`${source}.mv.db`, 'original');
  await backupDatabase(source, snapshot, { offlineConfirmed: true });
  await writeFile(join(snapshot, 'database.mv.db'), 'tampered');
  await assert.rejects(restoreDatabase(snapshot, join(directory, 'restored'), { offlineConfirmed: true }), /integrity/);
  await assert.rejects(readFile(join(directory, 'restored.mv.db')), { code: 'ENOENT' });
}));

test('offline confirmation and absent H2 lock file are required', () => fixture(async directory => {
  const source = join(directory, 'source');
  await writeFile(`${source}.mv.db`, 'original');
  await assert.rejects(backupDatabase(source, join(directory, 'not-created')), /Stop the application/);
  await writeFile(`${source}.lock.db`, 'locked');
  await assert.rejects(backupDatabase(source, join(directory, 'not-created'), { offlineConfirmed: true }), /lock file/);
  await assert.rejects(restoreDatabase(join(directory, 'missing'), join(directory, 'restored')), /Stop the application/);
}));

test('existing snapshot folders and database filename extensions are refused', () => fixture(async directory => {
  const source = join(directory, 'source');
  await writeFile(`${source}.mv.db`, 'original');
  await backupDatabase(source, join(directory, 'snapshot'), { offlineConfirmed: true });
  await assert.rejects(backupDatabase(source, join(directory, 'snapshot'), { offlineConfirmed: true }), { code: 'EEXIST' });
  await assert.rejects(backupDatabase(`${source}.mv.db`, join(directory, 'other'), { offlineConfirmed: true }), /without a file extension/);
}));

test('a fresh checkout can create a nested snapshot path while preserving exclusivity', () => fixture(async directory => {
  const source = join(directory, 'source');
  const snapshot = join(directory, 'target', 'snapshots', 'first-backup');
  await writeFile(`${source}.mv.db`, 'fresh checkout fixture');
  const manifest = await backupDatabase(source, snapshot, { offlineConfirmed: true });
  assert.equal(JSON.parse(await readFile(join(snapshot, 'manifest.json'), 'utf8')).sha256, manifest.sha256);
  await assert.rejects(backupDatabase(source, snapshot, { offlineConfirmed: true }), { code: 'EEXIST' });
}));
