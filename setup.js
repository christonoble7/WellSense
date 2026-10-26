// One-shot Android setup: node setup.js   (run inside this folder after "npm install")
const fs = require('fs'), path = require('path'), cp = require('child_process');
const run = c => { console.log('> ' + c); const r = cp.spawnSync(c, { shell: true, stdio: 'inherit' }); if (r.status) { console.log('FAILED: ' + c); process.exit(1); } };
const A = 'android', J = path.join(A, 'app/src/main/java/com/wellsense/app');
if (!fs.existsSync(A)) run('npx cap add android');
fs.mkdirSync(J, { recursive: true });
for (const f of ['UsageTrackerPlugin.java', 'MainActivity.java', 'HealthConnectPlugin.kt']) fs.copyFileSync(path.join('native', f), path.join(J, f));
const edit = (p, fn) => { const s = fs.readFileSync(p, 'utf8'), t = fn(s); if (t === s) console.log('(no change) ' + p); else fs.writeFileSync(p, t); };
const HC = ['STEPS', 'HEART_RATE', 'RESTING_HEART_RATE', 'SLEEP', 'OXYGEN_SATURATION', 'HEART_RATE_VARIABILITY', 'EXERCISE'];
// 1. Manifest: permissions, Health Connect rationale, queries
edit(path.join(A, 'app/src/main/AndroidManifest.xml'), s => {
  if (s.includes('wellsense-patched')) return s;
  if (!s.includes('xmlns:tools')) s = s.replace('<manifest ', '<manifest xmlns:tools="http://schemas.android.com/tools" ');
  const perms = '<!-- wellsense-patched -->\n    <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" tools:ignore="ProtectedPermissions"/>\n' +
    HC.map(h => `    <uses-permission android:name="android.permission.health.READ_${h}"/>`).join('\n') +
    '\n    <queries><package android:name="com.google.android.apps.healthdata"/></queries>\n    ';
  s = s.replace('<application', perms + '<application');
  const filt = '<intent-filter><action android:name="androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE"/></intent-filter>\n        </activity>\n' +
    '        <activity-alias android:name="ViewPermissionUsageActivity" android:exported="true" android:targetActivity=".MainActivity" android:permission="android.permission.START_VIEW_PERMISSION_USAGE">\n' +
    '            <intent-filter><action android:name="android.intent.action.VIEW_PERMISSION_USAGE"/><category android:name="android.intent.category.HEALTH_PERMISSIONS"/></intent-filter>\n        </activity-alias>';
  return s.replace('</activity>', filt);
});
// 2. Gradle: Kotlin + Health Connect library, minSdk 26
edit(path.join(A, 'build.gradle'), s => s.includes('kotlin-gradle-plugin') ? s :
  s.replace(/(classpath\s+['"]com\.android\.tools\.build:gradle:[^'"]+['"])/, '$1\n        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22"'));
edit(path.join(A, 'app/build.gradle'), s => {
  if (s.includes('connect-client')) return s;
  s = s.replace(/apply plugin: ['"]com\.android\.application['"]/, "$&\napply plugin: 'kotlin-android'");
  s = s.replace(/android\s*\{/, 'android {\n    kotlinOptions { jvmTarget = "17" }');
  const i = s.lastIndexOf('dependencies {');
  return s.slice(0, i) + 'dependencies {\n    implementation "androidx.health.connect:connect-client:1.1.0-alpha07"\n    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"' + s.slice(i + 'dependencies {'.length);
});
edit(path.join(A, 'variables.gradle'), s => s.replace(/minSdkVersion\s*=\s*\d+/, 'minSdkVersion = 26'));
run('npx cap sync android');
console.log('\nDONE. Now run: npx cap open android   then press the green Run button.');
