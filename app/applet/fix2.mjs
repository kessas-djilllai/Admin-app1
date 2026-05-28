import fs from 'fs';
const text = fs.readFileSync('../app/src/main/java/com/example/MainActivity.kt', 'utf-8');
const lines = text.split('\n');
const fixedLines = [...lines.slice(0, 71), ...lines.slice(286)]; // omitting 71-285 (inclusive) which is 215 lines
fs.writeFileSync('../app/src/main/java/com/example/MainActivity.kt', fixedLines.join('\n'));
console.log("Done");
