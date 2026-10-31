const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..', 'src');
const allowedLegacy = new Set(['api.ts']);
const findings = [];
for (const name of fs.readdirSync(root)) {
  if (!name.endsWith('.ts') && !name.endsWith('.tsx')) continue;
  const file = path.join(root, name);
  const text = fs.readFileSync(file, 'utf8');
  for (const [index, line] of text.split(/\r?\n/).entries()) {
    if (line.includes('/api/v1')) findings.push({ file: name, line: index + 1, text: line.trim() });
  }
}
console.log(JSON.stringify({ allowedLegacyFiles: [...allowedLegacy], legacyV1Calls: findings }, null, 2));
const unexpected = findings.filter(item => !allowedLegacy.has(item.file));
if (unexpected.length) process.exitCode = 1;
