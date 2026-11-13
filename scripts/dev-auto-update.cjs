const crypto = require('node:crypto');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const { spawn, spawnSync } = require('node:child_process');

const root = path.resolve(__dirname, '..');
const output = path.join(root, '.local', 'app-update');
const manifestPath = path.join(output, 'version.json');
const downloadsPath = path.join(output, 'downloads.json');
const gradle = path.join(root, '.tools', 'gradle-8.10.2', 'bin', 'gradle.bat');
const sdk = process.env.ANDROID_HOME || path.join(process.env.LOCALAPPDATA || '', 'Android', 'Sdk');
const adb = path.join(sdk, 'platform-tools', 'adb.exe');
const buildTools = path.join(sdk, 'build-tools');
const installedTools = fs.existsSync(buildTools) ? fs.readdirSync(buildTools).sort((a, b) => b.localeCompare(a, undefined, { numeric: true })) : [];
const aapt = installedTools.map(version => path.join(buildTools, version, 'aapt.exe')).find(fs.existsSync);
const port = Number(process.env.APP_UPDATE_PORT || 4310);
const bindHost = process.env.APP_UPDATE_BIND_HOST || '127.0.0.1';
let latest = fs.existsSync(manifestPath) ? JSON.parse(fs.readFileSync(manifestPath, 'utf8')) : null;
const downloads = fs.existsSync(downloadsPath) ? JSON.parse(fs.readFileSync(downloadsPath, 'utf8')) : { since: new Date().toISOString(), daily: {} };
let building = false;
let queued = false;
let timer;
let versionMismatchRetries = 0;

fs.mkdirSync(output, { recursive: true });

function apkVersionCode(apk) {
  if (!aapt) return null;
  const relativeApk = path.relative(root, apk);
  const readablePath = !relativeApk.startsWith('..') && !path.isAbsolute(relativeApk)
    ? relativeApk.replace(/\\/g, '/') : apk;
  const result = spawnSync(aapt, ['dump', 'badging', readablePath], { encoding: 'utf8', cwd: root });
  if (result.status !== 0) return null;
  const match = result.stdout.match(/versionCode='([0-9]+)'/);
  return match ? Number(match[1]) : null;
}

function hasSourceChanges() {
  if (!latest || !fs.existsSync(path.join(output, path.basename(latest.apkUrl)))) return true;
  if (apkVersionCode(path.join(output, path.basename(latest.apkUrl))) !== latest.versionCode) return true;
  const publishedAt = fs.statSync(path.join(output, path.basename(latest.apkUrl))).mtimeMs;
  if (fs.statSync(path.join(root, 'app', 'build.gradle.kts')).mtimeMs > publishedAt) return true;
  const pending = [path.join(root, 'app', 'src')];
  while (pending.length) {
    for (const entry of fs.readdirSync(pending.pop(), { withFileTypes: true })) {
      const filename = path.join(entry.parentPath, entry.name);
      if (entry.isDirectory()) pending.push(filename);
      else if (entry.isFile() && fs.statSync(filename).mtimeMs > publishedAt) return true;
    }
  }
  return false;
}

function log(message) {
  const line = `[${new Date().toLocaleString()}] ${message}`;
  console.log(line);
  fs.appendFileSync(path.join(output, 'service.log'), `${line}\n`);
}

function installOnEmulator(apk) {
  if (!fs.existsSync(adb)) return;
  const devices = spawnSync(adb, ['devices'], { encoding: 'utf8' });
  const serial = devices.stdout.match(/^(emulator-\d+)\s+device\s*$/m)?.[1];
  if (!serial) return;
  const install = spawnSync(adb, ['-s', serial, 'install', '-r', apk], { encoding: 'utf8' });
  log(install.status === 0 ? `Installed on ${serial}` : `Emulator install failed: ${install.stderr || install.stdout}`);
}

function build() {
  if (building) { queued = true; return; }
  building = true;
  const versionCode = Math.max(2, (latest?.versionCode || 1) + 1);
  const versionName = `0.1.${versionCode}`;
  log(`Building version ${versionName}`);
  const args = [':app:assembleDebug', '--offline', '--quiet',
    `-PappVersionCode=${versionCode}`, `-PappVersionName=${versionName}`];
  const child = spawn(gradle, args,
    { cwd: root, env: { ...process.env, ANDROID_HOME: sdk }, shell: true });
  let errors = '';
  child.stdout.on('data', data => { errors += data; });
  child.stderr.on('data', data => { errors += data; });
  child.on('error', error => finish(error.message));
  child.on('close', code => finish(code === 0 ? null : errors.trim() || `Build exited with ${code}`));

  let finished = false;
  function finish(error) {
    if (finished) return;
    finished = true;
    if (error) log(`Build failed: ${error}`);
    else try {
      const builtApk = path.join(root, 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk');
      const actualVersion = apkVersionCode(builtApk);
      if (actualVersion !== versionCode) {
        if (++versionMismatchRetries <= 2) queued = true;
        throw new Error(`APK version mismatch: expected ${versionCode}, got ${actualVersion ?? 'unknown'}`);
      }
      versionMismatchRetries = 0;
      const filename = `app-${versionCode}.apk`;
      const publishedApk = path.join(output, filename);
      fs.copyFileSync(builtApk, publishedApk);
      const sha256 = crypto.createHash('sha256').update(fs.readFileSync(publishedApk)).digest('hex');
      latest = { versionCode, versionName, apkUrl: `/apk/${filename}`, sha256 };
      fs.writeFileSync(`${manifestPath}.tmp`, JSON.stringify(latest, null, 2));
      fs.renameSync(`${manifestPath}.tmp`, manifestPath);
      log(`Published ${filename}`);
      installOnEmulator(publishedApk);
    } catch (publishError) { log(`Publish failed: ${publishError.message}`); }
    building = false;
    if (queued) {
      queued = false;
      if (hasSourceChanges()) build();
    }
  }
}

function scheduleBuild() {
  clearTimeout(timer);
  timer = setTimeout(build, 4000);
}

const server = http.createServer((request, response) => {
  if (request.method !== 'GET') { response.writeHead(405).end(); return; }
  if (request.url === '/version.json' && latest) {
    response.writeHead(200, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
    response.end(JSON.stringify(latest));
    return;
  }
  if (request.url === latest?.apkUrl) {
    const apk = path.join(output, path.basename(latest.apkUrl));
    if (fs.existsSync(apk)) {
      response.writeHead(200, { 'Content-Type': 'application/vnd.android.package-archive',
        'Content-Length': fs.statSync(apk).size, 'Cache-Control': 'no-store' });
      response.once('finish', () => {
        const day = new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Shanghai' });
        downloads.daily[day] = (downloads.daily[day] || 0) + 1;
        const temporary = `${downloadsPath}.tmp`;
        fs.writeFileSync(temporary, JSON.stringify(downloads));
        fs.renameSync(temporary, downloadsPath);
      });
      fs.createReadStream(apk).pipe(response);
      return;
    }
  }
  response.writeHead(404).end();
});

server.listen(port, bindHost, () => {
  log(`Update service listening on http://${bindHost}:${port}`);
  if (hasSourceChanges()) build();
  else log(`Current version ${latest.versionName} is up to date`);
});

fs.watch(path.join(root, 'app', 'src'), { recursive: true }, scheduleBuild);
fs.watch(path.join(root, 'app'), (_event, filename) => {
  if (filename === 'build.gradle.kts') scheduleBuild();
});
