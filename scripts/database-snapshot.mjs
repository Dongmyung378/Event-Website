import { constants } from 'node:fs';
import { copyFile, mkdir, readFile, stat, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { dirname, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const digest = bytes => createHash('sha256').update(bytes).digest('hex');
const exists = async path => stat(path).then(() => true, error => {
  if (error.code === 'ENOENT') return false;
  throw error;
});

function databaseFile(base) {
  const path = resolve(base);
  if (path.endsWith('.mv.db') || path.endsWith('.lock.db')) {
    throw new Error('Provide the H2 database base path, without a file extension.');
  }
  return `${path}.mv.db`;
}

export async function backupDatabase(source, output, { offlineConfirmed = false } = {}) {
  if (!offlineConfirmed) throw new Error('Stop the application before confirming an offline backup.');
  const file = databaseFile(source);
  if (await exists(`${resolve(source)}.lock.db`)) throw new Error('An H2 lock file is present.');
  const directory = resolve(output);
  await mkdir(dirname(directory), { recursive: true });
  await mkdir(directory); // Refuse to replace an existing snapshot directory.
  const before = await stat(file);
  const bytes = await readFile(file);
  await copyFile(file, resolve(directory, 'database.mv.db'), constants.COPYFILE_EXCL);
  const after = await stat(file);
  const copied = await readFile(resolve(directory, 'database.mv.db'));
  if (before.size !== after.size || before.mtimeMs !== after.mtimeMs || digest(bytes) !== digest(copied)) {
    throw new Error('The database changed during backup. Keep the application stopped and retry with a new directory.');
  }
  const manifest = { format: 1, engine: 'H2 MVStore', method: 'offline-file-copy',
    bytes: copied.length, sha256: digest(copied) };
  await writeFile(resolve(directory, 'manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`, { flag: 'wx' });
  return manifest;
}

export async function restoreDatabase(snapshot, target, { offlineConfirmed = false } = {}) {
  if (!offlineConfirmed) throw new Error('Stop the application before confirming an offline restore.');
  const directory = resolve(snapshot);
  const manifest = JSON.parse(await readFile(resolve(directory, 'manifest.json'), 'utf8'));
  const bytes = await readFile(resolve(directory, 'database.mv.db'));
  if (manifest.format !== 1 || manifest.bytes !== bytes.length || manifest.sha256 !== digest(bytes)) {
    throw new Error('Snapshot integrity validation failed.');
  }
  const file = databaseFile(target);
  if (await exists(file) || await exists(`${resolve(target)}.lock.db`)) {
    throw new Error('Restore requires a new database base path. Existing data is never overwritten.');
  }
  await mkdir(dirname(file), { recursive: true });
  await copyFile(resolve(directory, 'database.mv.db'), file, constants.COPYFILE_EXCL);
  if (digest(await readFile(file)) !== manifest.sha256) throw new Error('Restored file checksum mismatch.');
  return manifest;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try {
    const [operation, source, target, confirmation] = process.argv.slice(2);
    if (!['backup', 'restore'].includes(operation) || !source || !target || confirmation !== '--offline-confirmed') {
      throw new Error('Usage: node scripts/database-snapshot.mjs backup <database-base> <new-snapshot-dir> --offline-confirmed\n'
        + '   or: node scripts/database-snapshot.mjs restore <snapshot-dir> <new-database-base> --offline-confirmed');
    }
    const action = operation === 'backup' ? backupDatabase : restoreDatabase;
    console.log(JSON.stringify(await action(source, target, { offlineConfirmed: true }), null, 2));
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
