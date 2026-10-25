import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
export function assertScan(report) {
 assert.equal(report.SchemaVersion,2,'Unrecognized scanner report schema');
 assert.equal(report.ArtifactType,'container_image','Expected a final container-image scan');
 assert.ok(report.Metadata?.OS?.Family,'Missing runtime OS inventory');
 assert.notEqual(report.Metadata.OS.Eosl,true,'Unsupported runtime OS');
 assert.ok(Array.isArray(report.Results)&&report.Results.length>0,'Missing scan results');
 assert.ok(report.Results.some(r=>r.Class==='os-pkgs'),'Missing runtime OS package scan');
 for(const result of report.Results) {
  assert.ok(result.Type&&result.Target,'Incomplete scan target');
  assert.ok(result.Vulnerabilities===undefined||Array.isArray(result.Vulnerabilities),'Malformed findings');
  for(const finding of result.Vulnerabilities??[])assert.ok(['UNKNOWN','LOW','MEDIUM','HIGH','CRITICAL'].includes(finding.Severity),'Missing finding severity');
  assert.ok(!result.Vulnerabilities?.some(v=>['HIGH','CRITICAL'].includes(v.Severity)),'Unresolved high/critical findings; review report without blanket suppression');
 }
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url))assertScan(JSON.parse(readFileSync(process.argv[2],'utf8')));
