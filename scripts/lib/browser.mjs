import { access, mkdir } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { join } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';

export async function waitFor(check, message, timeout = 15000) {
  const deadline = Date.now() + timeout;
  let last;
  while (Date.now() < deadline) {
    try { const result = await check(); if (result) return result; }
    catch (error) { last = error; }
    await delay(75);
  }
  throw new Error(`${message}${last ? `: ${last.message}` : ''}`);
}

export async function chromePath() {
  const candidates = [process.env.CHROME_PATH, process.env.CHROME_BIN,
    process.env.PROGRAMFILES && join(process.env.PROGRAMFILES, 'Google/Chrome/Application/chrome.exe'),
    process.env['PROGRAMFILES(X86)'] && join(process.env['PROGRAMFILES(X86)'], 'Google/Chrome/Application/chrome.exe'),
    process.env.LOCALAPPDATA && join(process.env.LOCALAPPDATA, 'Google/Chrome/Application/chrome.exe'),
    '/usr/bin/google-chrome', '/usr/bin/google-chrome-stable', '/usr/bin/chromium', '/usr/bin/chromium-browser',
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'].filter(Boolean);
  for (const candidate of candidates) {
    if (await access(candidate).then(() => true, () => false)) return candidate;
  }
  throw new Error('Chrome or Chromium was not found. Set CHROME_PATH to its executable.');
}

class Cdp {
  constructor(socket) {
    this.socket = socket;
    this.nextId = 0;
    this.pending = new Map();
    this.listeners = new Map();
    socket.addEventListener('message', event => {
      const message = JSON.parse(event.data);
      if (message.id) {
        const pending = this.pending.get(message.id);
        if (!pending) return;
        this.pending.delete(message.id);
        clearTimeout(pending.timeout);
        if (message.error) pending.reject(new Error(message.error.message));
        else pending.resolve(message.result);
      } else {
        for (const handler of this.listeners.get(message.method) || []) handler(message.params);
      }
    });
    socket.addEventListener('close', () => {
      for (const pending of this.pending.values()) {
        clearTimeout(pending.timeout);
        pending.reject(new Error('Browser connection closed.'));
      }
      this.pending.clear();
    });
  }
  static async connect(url) {
    const socket = new WebSocket(url);
    await new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        try { socket.close(); } catch { /* A connecting socket may already have failed. */ }
        reject(new Error('Browser debugging connection timed out.'));
      }, 10000);
      socket.addEventListener('open', () => { clearTimeout(timeout); resolve(); }, { once: true });
      socket.addEventListener('error', () => {
        clearTimeout(timeout);
        reject(new Error('Browser debugging connection failed.'));
      }, { once: true });
    });
    return new Cdp(socket);
  }
  send(method, params = {}) {
    const id = ++this.nextId;
    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        this.pending.delete(id);
        reject(new Error(`Browser command timed out: ${method}`));
      }, 15000);
      this.pending.set(id, { resolve, reject, timeout });
      this.socket.send(JSON.stringify({ id, method, params }));
    });
  }
  on(method, handler) {
    const handlers = this.listeners.get(method) || [];
    handlers.push(handler);
    this.listeners.set(method, handlers);
  }
  close() { this.socket.close(); }
}

export async function startBrowser(workDirectory) {
  const binary = await chromePath();
  const profile = join(workDirectory, 'chrome-profile');
  await mkdir(profile, { recursive: true });
  const child = spawn(binary, ['--headless=new', '--disable-gpu', '--disable-background-networking',
    '--disable-component-update', '--no-first-run', '--no-default-browser-check', '--remote-debugging-port=0',
    '--window-size=1440,1100', `--user-data-dir=${profile}`, 'about:blank'],
    { windowsHide: true, stdio: ['ignore', 'ignore', 'pipe'] });
  let stderr = '';
  let startError;
  child.stderr.on('data', bytes => { stderr = `${stderr}${bytes}`.slice(-32000); });
  child.on('error', error => { startError = error; });
  let endpoint;
  try {
    endpoint = await waitFor(() => {
      if (startError) throw startError;
      return stderr.match(/DevTools listening on (ws:\/\/[^\s]+)/)?.[1];
    }, 'Chrome did not expose its local debugging endpoint');
  } catch (error) {
    child.kill();
    throw error;
  }
  let browser;
  try { browser = await Cdp.connect(endpoint); }
  catch (error) { child.kill(); throw error; }
  const httpOrigin = endpoint.replace(/^ws:/, 'http:').split('/devtools/')[0];
  const sessions = [];
  async function newPage() {
    const response = await fetch(`${httpOrigin}/json/new?about:blank`, { method: 'PUT' });
    const target = await response.json();
    const cdp = await Cdp.connect(target.webSocketDebuggerUrl);
    sessions.push(cdp);
    await cdp.send('Page.enable');
    await cdp.send('Runtime.enable');
    await cdp.send('Network.enable');
    const errors = [];
    cdp.on('Runtime.exceptionThrown', item => errors.push(item.exceptionDetails.text));
    async function evaluate(expression) {
      const result = await cdp.send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
      if (result.exceptionDetails) throw new Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text);
      return result.result?.value;
    }
    return {
      cdp, errors, evaluate,
      async navigate(url) {
        await cdp.send('Page.navigate', { url });
        await waitFor(() => evaluate(`location.href === ${JSON.stringify(url)} && document.readyState === 'complete'`),
          `Page did not finish loading ${url}`);
      },
      async wait(expression, message) { return waitFor(() => evaluate(expression), message); },
      async fill(values) {
        await evaluate(`(() => {
          for (const [selector, value] of Object.entries(${JSON.stringify(values)})) {
            const input = document.querySelector(selector);
            if (!input) throw new Error('Input not found: ' + selector);
            input.value = value;
            input.dispatchEvent(new Event('input', { bubbles: true }));
            input.dispatchEvent(new Event('change', { bubbles: true }));
          }
          return true;
        })()`);
      },
      async submit(selector = 'main form', { noValidate = false } = {}) {
        await evaluate(`(() => {
          const form = document.querySelector(${JSON.stringify(selector)});
          if (!form) throw new Error('Form not found');
          form.noValidate = ${noValidate}; form.requestSubmit(); return true;
        })()`);
      },
      async screenshot() {
        const image = await cdp.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true });
        return Buffer.from(image.data, 'base64');
      }
    };
  }
  return {
    newPage, binary,
    async close() {
      for (const session of sessions) session.close();
      try { await browser.send('Browser.close'); } catch { /* Chrome may close before acknowledging. */ }
      browser.close();
      if (child.exitCode === null) child.kill();
    }
  };
}
