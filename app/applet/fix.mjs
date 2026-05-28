import fs from 'fs';

let text = fs.readFileSync('app/src/main/java/com/example/MainActivity.kt', 'utf-8');
let lines = text.split('\n');

// 0-indexed
let start = 71; 
let end = 285;

console.log("Start line:", lines[start]);
console.log("End line:", lines[end-1]);

let block = lines.slice(start, end).join('\n');
lines.splice(start, end - start);

lines.push(block);

fs.writeFileSync('app/src/main/java/com/example/MainActivity.kt', lines.join('\n'));
console.log("Done");
