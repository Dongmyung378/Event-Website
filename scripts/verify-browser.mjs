import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdtemp, readdir, writeFile, mkdir } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createServer } from 'node:net';
import { once } from 'node:events';
import { backupDatabase, restoreDatabase } from './database-snapshot.mjs';
import { startBrowser, waitFor } from './lib/browser.mjs';

const repository = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const args = process.argv.slice(2);
function option(name) {
  const index = args.indexOf(name);
  return index >= 0 ? args[index + 1] : undefined;
}
const outputParent = resolve(option('--output') || join(tmpdir(), 'eventlite-verification'));
await mkdir(outputParent, { recursive: true });
const work = await mkdtemp(join(outputParent, 'run-'));
const target = join(repository, 'target');
let jar = option('--jar') ? resolve(option('--jar')) : null;
if (!jar) {
  const jars = (await readdir(target)).filter(name => name.endsWith('.jar') && !name.endsWith('.original'));
  jar = jars.length === 1 ? join(target, jars[0]) : null;
}
if (!jar) throw new Error('Build one executable JAR with the Maven wrapper before running this script.');
const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
const portProbe = createServer();
portProbe.listen(0, '127.0.0.1');
await once(portProbe, 'listening');
const port = portProbe.address().port;
await new Promise(resolve => portProbe.close(resolve));
const origin = `http://127.0.0.1:${port}`;
let application;
let browser;
let page;
const checks = [];
const report = { format: 1, environment: { os: process.platform, architecture: process.arch, node: process.version },
  integrations: 'disabled, no real social posts or Mapbox requests', checks };

async function check(name, action) {
  const started = performance.now();
  await action();
  checks.push({ name, passed: true, milliseconds: Number((performance.now() - started).toFixed(2)) });
  console.log(`PASS ${name}`);
}

async function startApplication(database, label, executableJar = jar) {
  let log = '';
  let spawnError;
  const child = spawn(java, ['-Djava.rmi.server.hostname=127.0.0.1', '-Dcom.sun.management.jmxremote.local.only=true',
    '-jar', executableJar, '--spring.profiles.active=demo', '--server.address=127.0.0.1',
    `--server.port=${port}`, `--spring.datasource.url=jdbc:h2:file:${database.replaceAll('\\', '/')}`,
    '--spring.datasource.username=verification', '--spring.datasource.password=', '--spring.jpa.hibernate.ddl-auto=update',
    '--spring.application.admin.enabled=true',
    '--eventlite.admin.username=browser-admin', '--eventlite.admin.password=verification-password',
    '--eventlite.mastodon.instance=', '--eventlite.mastodon.token=', '--eventlite.mastodon.account-id=',
    '--eventlite.mapbox.token=', '--eventlite.mapbox.public-token='],
    { cwd: work, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
  child.stdout.on('data', data => { log += data; });
  child.stderr.on('data', data => { log += data; });
  child.on('error', error => { spawnError = error; });
  application = { child, label, getLog: () => log };
  const ready = await waitFor(async () => {
    if (spawnError) return { error: spawnError };
    if (child.exitCode !== null) return { error: new Error(`Application exited with ${child.exitCode}. See ${label}.log.`) };
    const response = await fetch(`${origin}/actuator/health`, {
      headers: { Accept: 'application/json' }, signal: AbortSignal.timeout(1000)
    });
    return response.ok && (await response.json()).status === 'UP' ? { started: true } : false;
  }, `Application did not start (${label})`, 120000);
  if (ready.error) throw ready.error;
  // Web health can become UP before CommandLineRunner has finished seeding the demo.
  await waitFor(async () => {
    const data = await records();
    return data.events.length >= 7 && data.venues.length >= 4;
  }, 'Demo data initialization did not finish', 30000);
}

async function stopApplication() {
  if (!application) return;
  const running = application;
  application = undefined;
  if (running.child.exitCode === null) {
    const stopped = once(running.child, 'exit');
    const controller = spawn(java, ['--add-modules', 'jdk.attach', '--source', '17',
      join(repository, 'scripts', 'StopApplication.java'), String(running.child.pid)],
      { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
    let shutdownLog = '';
    controller.stdout.on('data', bytes => { shutdownLog += bytes; });
    controller.stderr.on('data', bytes => { shutdownLog += bytes; });
    let timeout;
    try {
      await Promise.race([stopped, new Promise((_, reject) => {
        timeout = setTimeout(() => reject(new Error('Graceful application shutdown did not finish.')), 20000);
        controller.once('error', reject);
      })]);
    } catch (error) {
      running.child.kill('SIGTERM');
      throw error;
    } finally {
      clearTimeout(timeout);
      if (controller.exitCode === null) controller.kill();
      await writeFile(join(work, `${running.label}-shutdown.log`), shutdownLog);
      await writeFile(join(work, `${running.label}.log`), running.getLog());
    }
  }
  await writeFile(join(work, `${running.label}.log`), running.getLog());
}

async function api(path) {
  const response = await fetch(`${origin}${path}`, { headers: { Accept: 'application/json' } });
  assert.equal(response.status, 200, path);
  return response.json();
}
const embedded = model => Object.values(model._embedded || {}).flat();
async function records() {
  const events = embedded(await api('/api/events')).map(({ id, name, date, time, description, venue }) =>
    ({ id, name, date, time, description, venue: venue?.id })).sort((a, b) => a.id - b.id);
  const venues = embedded(await api('/api/venues')).map(({ id, name, road, postcode, capacity, latitude, longitude }) =>
    ({ id, name, road, postcode, capacity, latitude, longitude })).sort((a, b) => a.id - b.id);
  return { events, venues };
}

try {
  const sourceDatabase = join(work, 'source', 'eventlite');
  await mkdir(dirname(sourceDatabase), { recursive: true });
  let legacyRecords;
  if (option('--seed-jar')) {
    await startApplication(sourceDatabase, 'legacy-seed', resolve(option('--seed-jar')));
    legacyRecords = await records();
    await stopApplication();
  }
  await startApplication(sourceDatabase, 'initial');
  if (legacyRecords) assert.deepEqual(await records(), legacyRecords, 'Initial-version records changed during schema upgrade');
  browser = await startBrowser(work);
  report.environment.browser = browser.binary;
  page = await browser.newPage();
  let venueId;
  let eventId;
  await check('public lists and JSON API', async () => {
    await page.navigate(`${origin}/events`);
    assert.equal(await page.evaluate("document.querySelector('h1')?.textContent"), 'Event List');
    assert.equal(await page.evaluate("document.querySelector('a[href=\"/events/new\"]') === null"), true);
    const assets = await page.evaluate("[...document.querySelectorAll('link[rel=stylesheet][href], script[src]')].map(node => node.href || node.src)");
    for (const asset of assets) {
      assert.equal(new URL(asset).origin, origin, 'Disabled integrations must not add external assets');
      assert.equal((await fetch(asset)).status, 200, `Static asset did not load: ${new URL(asset).pathname}`);
    }
    const data = await records();
    assert.ok(data.events.length >= 7);
    assert.ok(data.venues.length >= 4);
  });
  await check('unauthenticated administrator routes and writes are rejected', async () => {
    const restricted = await fetch(`${origin}/venues/new`, { redirect: 'manual' });
    assert.equal(restricted.status, 302);
    assert.ok(restricted.headers.get('location').endsWith('/sign-in'));
    assert.equal((await fetch(`${origin}/events`, { method: 'POST' })).status, 403);
  });
  await check('invalid login gives a visible error', async () => {
    await page.navigate(`${origin}/sign-in`);
    await page.fill({ '#username': 'browser-admin', '#password': 'incorrect-password' });
    await page.submit();
    await page.wait("location.search.includes('error') && document.querySelector('.alert-danger')", 'Missing login failure');
  });
  await check('administrator login', async () => {
    await page.fill({ '#username': 'browser-admin', '#password': 'verification-password' });
    await page.submit();
    await page.wait("location.pathname === '/' && document.readyState === 'complete'", 'Administrator login did not redirect');
    // The original root page is static. Session controls are rendered on /home.
    await page.navigate(`${origin}/home`);
    assert.ok(await page.evaluate("document.body.innerText.includes('Signed in as browser-admin')"));
    assert.equal(await page.evaluate("fetch('/events', {method: 'POST'}).then(response => response.status)"), 403);
  });
  if (legacyRecords) {
    await check('initial-version file database retains its records and existing rows remain editable', async () => {
      const venue = legacyRecords.venues[0];
      await page.navigate(`${origin}/venues/update/${venue.id}`);
      assert.equal(await page.evaluate("document.querySelector('input[name=version]')?.value"), '0');
      await page.fill({ '#capacity': String(venue.capacity + 1) });
      await page.submit();
      await page.wait("location.pathname === '/venues'", 'Migrated venue could not be updated');
      assert.equal((await api(`/api/venues/${venue.id}`)).capacity, venue.capacity + 1);
      await page.navigate(`${origin}/venues/update/${venue.id}`);
      assert.equal(await page.evaluate("document.querySelector('input[name=version]')?.value"), '1');
      await page.fill({ '#capacity': String(venue.capacity) });
      await page.submit();
      await page.wait("location.pathname === '/venues'", 'Migrated venue could not be restored');
      const existingEvent = legacyRecords.events.find(event => event.date > new Date().toISOString().slice(0, 10));
      assert.ok(existingEvent, 'The initial fixture needs a future event');
      await page.navigate(`${origin}/events/update/${existingEvent.id}`);
      assert.equal(await page.evaluate("document.querySelector('input[name=version]')?.value"), '0');
      await page.fill({ '#name': `${existingEvent.name} migration check` });
      await page.submit();
      await page.wait("location.pathname === '/events'", 'Migrated event could not be updated');
      assert.equal((await api(`/api/events/${existingEvent.id}`)).name, `${existingEvent.name} migration check`);
      await page.navigate(`${origin}/events/update/${existingEvent.id}`);
      assert.equal(await page.evaluate("document.querySelector('input[name=version]')?.value"), '1');
      await page.fill({ '#name': existingEvent.name });
      await page.submit();
      await page.wait("location.pathname === '/events'", 'Migrated event could not be restored');
      assert.deepEqual(await records(), legacyRecords);
      report.legacyDatabaseUpgrade = { events: legacyRecords.events.length, venues: legacyRecords.venues.length,
        recordsPreserved: true, existingEventAndVenueUpdated: true };
    });
  }
  await check('server validation rejects invalid venue capacity', async () => {
    await page.navigate(`${origin}/venues/new`);
    await page.fill({ '#name': 'Browser validation venue', '#road': 'Verification Road', '#postcode': 'M1 1AA', '#capacity': '0' });
    assert.equal(await page.evaluate("document.querySelector('#capacity').checkValidity()"), false);
    await page.submit('main form', { noValidate: true });
    await page.wait("document.querySelector('#capacity-error')?.textContent.includes('positive')", 'Invalid capacity was not rejected');
  });
  await check('create venue through its rendered form', async () => {
    await page.fill({ '#name': 'Browser verification venue', '#capacity': '150' });
    await page.submit();
    await page.wait("location.pathname === '/venues' && document.body.innerText.includes('Browser verification venue')", 'Venue was not created');
    venueId = (await records()).venues.find(venue => venue.name === 'Browser verification venue').id;
  });
  await check('search venue through the browser', async () => {
    await page.fill({ '#venue-query': 'Browser verification' });
    await page.submit('main form[role="search"]');
    await page.wait("location.pathname === '/venues/search' && document.querySelector('tbody')?.innerText.includes('Browser verification venue')", 'Venue search failed');
  });
  const staleVenuePage = await browser.newPage();
  await check('update venue and reject a stale edit from another tab', async () => {
    await page.navigate(`${origin}/venues/update/${venueId}`);
    await staleVenuePage.navigate(`${origin}/venues/update/${venueId}`);
    await page.fill({ '#name': 'Browser updated venue' });
    await page.submit();
    await page.wait("location.pathname === '/venues' && document.body.innerText.includes('Browser updated venue')", 'Venue update failed');
    await staleVenuePage.fill({ '#name': 'Browser stale overwrite' });
    await staleVenuePage.submit();
    await staleVenuePage.wait("document.body.innerText.toLowerCase().includes('changed') || document.body.innerText.toLowerCase().includes('conflict')", 'Stale venue edit had no conflict message');
    assert.equal((await api(`/api/venues/${venueId}`)).name, 'Browser updated venue');
  });
  await check('server validation rejects an invalid event date', async () => {
    await page.navigate(`${origin}/events/new`);
    await page.fill({ '#name': 'Browser verification event', '#date': '', '#venue': String(venueId) });
    await page.submit('main form', { noValidate: true });
    await page.wait("document.querySelector('#date-error')?.textContent.trim().length > 0", 'Missing event date was not rejected');
  });
  await check('create event through its rendered form', async () => {
    const today = await page.evaluate("document.querySelector('#date').min");
    const date = new Date(`${today}T12:00:00Z`);
    date.setUTCDate(date.getUTCDate() + 7);
    await page.fill({ '#date': date.toISOString().slice(0, 10), '#time': '14:30', '#description': 'Browser validation and recovery fixture.' });
    await page.submit();
    await page.wait("location.pathname === '/events' && document.body.innerText.includes('Browser verification event')", 'Event was not created');
    eventId = (await records()).events.find(event => event.name === 'Browser verification event').id;
  });
  await check('search event through the browser', async () => {
    await page.fill({ '#event-query': 'Browser verification' });
    await page.submit('main form[role="search"]');
    await page.wait("location.pathname === '/events/search' && document.querySelector('tbody')?.innerText.includes('Browser verification event')", 'Event search failed');
  });
  const staleEventPage = await browser.newPage();
  await check('update event and reject a stale edit from another tab', async () => {
    await page.navigate(`${origin}/events/update/${eventId}`);
    await staleEventPage.navigate(`${origin}/events/update/${eventId}`);
    await page.fill({ '#name': 'Browser updated event' });
    await page.submit();
    await page.wait("location.pathname === '/events' && document.body.innerText.includes('Browser updated event')", 'Event update failed');
    await staleEventPage.fill({ '#name': 'Browser stale event overwrite' });
    await staleEventPage.submit();
    await staleEventPage.wait("document.body.innerText.toLowerCase().includes('changed') || document.body.innerText.toLowerCase().includes('conflict')", 'Stale event edit had no conflict message');
    assert.equal((await api(`/api/events/${eventId}`)).name, 'Browser updated event');
  });
  await check('venue with an event cannot be deleted', async () => {
    await page.navigate(`${origin}/venues/${venueId}`);
    await page.submit('main form[action="/venues/' + venueId + '"]');
    await page.wait("document.querySelector('.alert-danger')?.textContent.includes('cannot be deleted')", 'Referenced venue deletion was not explained');
    assert.equal((await api(`/api/venues/${venueId}`)).id, venueId);
  });
  await check('HTML and JSON next-three ordering agree', async () => {
    await page.navigate(`${origin}/venues/${venueId}/next3events`);
    const htmlIds = await page.evaluate("[...document.querySelectorAll('tbody a[href^=\"/events/\"]')].map(a => Number(a.pathname.split('/').pop()))");
    const apiIds = embedded(await api(`/api/venues/${venueId}/next3events`)).map(event => event.id);
    assert.deepEqual(htmlIds, apiIds);
    assert.ok(apiIds.includes(eventId));
  });
  await check('pagination stays opt-in and enforces bounds', async () => {
    const paged = await api('/api/events?page=0&size=2');
    assert.equal(paged.page.size, 2);
    assert.equal(embedded(paged).length, 2);
    assert.equal((await fetch(`${origin}/api/events?page=-1&size=2`, { headers: { Accept: 'application/json' } })).status, 400);
    assert.equal((await fetch(`${origin}/api/events?page=0&size=101`, { headers: { Accept: 'application/json' } })).status, 400);
  });
  await check('mobile event details have no horizontal overflow or script exceptions', async () => {
    await page.cdp.send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true });
    await page.navigate(`${origin}/events/${eventId}`);
    assert.equal(await page.evaluate('document.documentElement.scrollWidth <= innerWidth'), true);
    assert.deepEqual(page.errors, []);
    await writeFile(join(work, 'event-mobile.png'), await page.screenshot());
    await page.cdp.send('Emulation.clearDeviceMetricsOverride');
  });
  const beforeRestart = await records();
  await check('file database survives application restart', async () => {
    await stopApplication();
    await startApplication(sourceDatabase, 'restart');
    assert.deepEqual(await records(), beforeRestart);
  });
  await check('offline backup validates checksum and restores to a new database', async () => {
    await stopApplication();
    report.backup = await backupDatabase(sourceDatabase, join(work, 'snapshot'), { offlineConfirmed: true });
    const restored = await restoreDatabase(join(work, 'snapshot'), join(work, 'restored', 'eventlite'), { offlineConfirmed: true });
    assert.equal(restored.sha256, report.backup.sha256);
    await assert.rejects(restoreDatabase(join(work, 'snapshot'), sourceDatabase, { offlineConfirmed: true }), /never overwritten/);
  });
  await check('restored database serves identical records after independent startup', async () => {
    await startApplication(join(work, 'restored', 'eventlite'), 'restored');
    assert.deepEqual(await records(), beforeRestart);
    report.recoveredRecords = { events: beforeRestart.events.length, venues: beforeRestart.venues.length };
  });
  await check('restored application supports login and event deletion', async () => {
    await page.navigate(`${origin}/sign-in`);
    await page.fill({ '#username': 'browser-admin', '#password': 'verification-password' });
    await page.submit();
    await page.wait("location.pathname === '/' && document.readyState === 'complete'", 'Restored administrator login did not redirect');
    await page.navigate(`${origin}/home`);
    assert.ok(await page.evaluate("document.body.innerText.includes('Signed in as browser-admin')"));
    await page.navigate(`${origin}/events/${eventId}`);
    await page.submit('main form[action="/events/' + eventId + '"]');
    await page.wait("location.pathname === '/events'", 'Event deletion did not redirect');
    assert.equal((await fetch(`${origin}/api/events/${eventId}`, { headers: { Accept: 'application/json' } })).status, 404);
  });
  await check('unreferenced venue can be deleted after its event', async () => {
    await page.navigate(`${origin}/venues/${venueId}`);
    await page.submit('main form[action="/venues/' + venueId + '"]');
    await page.wait("location.pathname === '/venues'", 'Venue deletion did not redirect');
    assert.equal((await fetch(`${origin}/api/venues/${venueId}`, { headers: { Accept: 'application/json' } })).status, 404);
  });
  report.passed = true;
  report.summary = { passed: checks.length, failed: 0 };
} catch (error) {
  report.passed = false;
  report.error = error.message;
  report.summary = { passed: checks.length, failed: 1 };
  if (page) {
    try {
      await writeFile(join(work, 'failure-view.json'), JSON.stringify(await page.evaluate(
        "({url: location.href, text: document.body.innerText, title: document.title})"), null, 2));
    } catch (diagnosticError) { report.diagnosticError = diagnosticError.message; }
    try { await writeFile(join(work, 'failure.png'), await page.screenshot()); }
    catch (screenshotError) { report.screenshotError = screenshotError.message; }
  }
  console.error(error.stack);
  process.exitCode = 1;
} finally {
  try { if (browser) await browser.close(); }
  catch (error) { report.browserCleanupError = error.message; process.exitCode = 1; }
  try { await stopApplication(); }
  catch (error) { report.applicationCleanupError = error.message; process.exitCode = 1; }
  if (report.browserCleanupError || report.applicationCleanupError) {
    report.passed = false;
    report.summary = { passed: checks.length, failed: 1 };
  }
  report.shutdown = 'local JMX SpringApplication shutdown, then process exit before offline copy';
  await writeFile(join(work, 'verification.json'), `${JSON.stringify(report, null, 2)}\n`);
  console.log(`Verification report: ${join(work, 'verification.json')}`);
}
