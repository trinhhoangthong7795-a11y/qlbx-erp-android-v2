# QLBX ERP v1.2.2 – source audit

- Archive: QLBX_ERP_Android_Source_v1.2.zip
- Internal package version: 1.2.2
- Package ID: vn.qlbx.erp
- Baseline npm install exit: 0
- Baseline npm test exit: 1
- Baseline npm run build exit: 1
- Vite build using src as root exit: 1

## npm install

added 109 packages in 13s

## npm test

> qlbx-erp-android@1.2.2 test
> node -e "const fs=require('fs'); if(!fs.existsSync('index.html')) process.exit(1); console.log('Source HTML found');"


## npm run build

> qlbx-erp-android@1.2.2 build
> vite build

[36mvite v6.4.5 [32mbuilding for production...[36m[39m
[32m✓[39m 0 modules transformed.
[31m✗[39m Build failed in 14ms
[31merror during build:
[31mCould not resolve entry module "index.html".[31m
    at getRollupError (file:///home/runner/work/qlbx-erp-android-v2/qlbx-erp-android-v2/source-audit/QLBX_ERP_Android_Source_v1.2.2-1/node_modules/rollup/dist/es/shared/parseAst.js:345:41)
    at error (file:///home/runner/work/qlbx-erp-android-v2/qlbx-erp-android-v2/source-audit/QLBX_ERP_Android_Source_v1.2.2-1/node_modules/rollup/dist/es/shared/parseAst.js:341:42)
    at ModuleLoader.loadEntryModule (file:///home/runner/work/qlbx-erp-android-v2/qlbx-erp-android-v2/source-audit/QLBX_ERP_Android_Source_v1.2.2-1/node_modules/rollup/dist/es/shared/node-entry.js:23240:20)
    at async Promise.all (index 0)[39m

## Vite build with src root
[36mvite v6.4.5 [32mbuilding for production...[36m[39m
[33m
[1m(!)[22m outDir [37m[2m/home/runner/work/qlbx-erp-android-v2/qlbx-erp-android-v2/source-audit/QLBX_ERP_Android_Source_v1.2.2-1/dist-audit[22m[33m is not inside project root and will not be emptied.
Use --emptyOutDir to override.
[39m
[32m✓[39m 0 modules transformed.
[31m✗[39m Build failed in 109ms
[31merror during build:
[31m[vite:build-html] Unable to parse HTML; parse5 error code invalid-first-character-of-tag-name
 at /home/runner/work/qlbx-erp-android-v2/qlbx-erp-android-v2/source-audit/QLBX_ERP_Android_Source_v1.2.2-1/src/index.html:248:179
246|  </style></head><body><img src="${data}"><script>window.onload=()=>window.print()<\/script><script type="module" src="/src/native-bridge.js"></script>
247|  </body></html>`);win.document.close();}
248|  async function addLane(){const lanes=[...new Set(state.slots.map(s=>s.lane))];const lane=Math.max(0,...lanes)+1;const suffix=['T','TLD','PLD','P'];const add=[];for(let col=1;col<=4;col++)for(let row=1;row<=20;row++)add.push({id:`D${lane}-${col}-${row}`,lane,col,row,label:`${lane}${suffix[col-1]}-${row}`,vehicleId:null});for(const s of add)await put('slots',s);await refresh();toast(`Đã thêm Dãy ${lane}.`);render();}
   |                                                                                                                                                                                    ^
249|  async function addSlotRow(lane){const slots=state.slots.filter(s=>s.lane===lane);const next=Math.max(0,...slots.map(s=>s.row))+1;const suffix=['T','TLD','PLD','P'];for(let col=1;col<=4;col++)await put('slots',{id:`D${lane}-${col}-${next}`,lane,col,row:next,label:`${lane}${suffix[col-1]}-${next}`,vehicleId:null});await refresh();toast(`Đã thêm hàng ô ${next} cho Dãy ${lane}.`);render();}
250|  async function removeSlotRow(lane){const slots=state.slots.filter(s=>s.lane===lane);const last=Math.max(0,...slots.map(s=>s.row));const rowSlots=slots.filter(s=>s.row===last);if(!rowSlots.length){toast('Không còn hàng ô để xóa.');return;}if(rowSlots.some(s=>s.vehicleId)){toast('Hàng cuối đang có xe. Hãy di chuyển xe trước khi xóa.');return;}if(!confirm(`Xóa hàng ô ${last} của Dãy ${lane}?`))return;for(const s of rowSlots)await remove('slots',s.id);await refresh();toast(`Đã xóa hàng ô ${last}.`);render();}[31m
file: [36m/home/runner/work/qlbx-erp-android-v2/qlbx-erp-android-v2/source-audit/QLBX_ERP_Android_Source_v1.2.2-1/src/index.html[31m
    at handleParseError (file:///home/runner/work/qlbx-erp-android-v2/qlbx-erp-android-v2/source-audit/QLBX_ERP_Android_Source_v1.2.2-1/node_modules/vite/dist/node/chunks/dep-CkSfqg8D.js:36516:9)
    at Parser.onParseError (file:///home/runner/work/qlbx-erp-android-v2/qlbx-erp-android-v2/source-audit/QLBX_ERP_Android_Source_v1.2.2-1/node_modules/vite/dist/node/chunks/dep-CkSfqg8D.js:36429:7)
    at Tokenizer._err (file:///home/runner/work/qlbx-erp-android-v2/qlbx-erp-android-v2/source-audit/QLBX_ERP_Android_Source_v1.2.2-1/node_modules/vite/dist/node/chunks/dep-CvfTChi5.js:1513:89)
    at Tokenizer._stateTagOpen (file:///home/runner/work/qlbx-erp-android-v2/qlbx-erp-android-v2/source-audit/QLBX_ERP_Android_Source_v1.2.2-1/node_modules/vite/dist/node/chunks/dep-CvfTChi5.js:2260:26)
    at Tokenizer._callState (file:///home/runner/work/qlbx-erp-android-v2/qlbx-erp-android-v2/source-audit/QLBX_ERP_Android_Source_v1.2.2-1/node_modules/vite/dist/node/chunks/dep-CvfTChi5.js:1837:22)
    at Tokenizer._runParsingLoop (file:///home/runner/work/qlbx-erp-android-v2/qlbx-erp-android-v2/source-audit/QLBX_ERP_Android_Source_v1.2.2-1/node_modules/vite/dist/node/chunks/dep-CvfTChi5.js:1537:22)
    at Tokenizer.write (file:///home/runner/work/qlbx-erp-android-v2/qlbx-erp-android-v2/source-audit/QLBX_ERP_Android_Source_v1.2.2-1/node_modules/vite/dist/node/chunks/dep-CvfTChi5.js:1562:14)
    at Parser.parse (file:///home/runner/work/qlbx-erp-android-v2/qlbx-erp-android-v2/source-audit/QLBX_ERP_Android_Source_v1.2.2-1/node_modules/vite/dist/node/chunks/dep-CvfTChi5.js:5086:26)
    at parse (file:///home/runner/work/qlbx-erp-android-v2/qlbx-erp-android-v2/source-audit/QLBX_ERP_Android_Source_v1.2.2-1/node_modules/vite/dist/node/chunks/dep-CvfTChi5.js:8215:19)
    at traverseHtml (file:///home/runner/work/qlbx-erp-android-v2/qlbx-erp-android-v2/source-audit/QLBX_ERP_Android_Source_v1.2.2-1/node_modules/vite/dist/node/chunks/dep-CkSfqg8D.js:36424:15)[39m
