# Run from the repository root after docker compose build. Creates only isolated disposable containers.
$ErrorActionPreference='Stop'
$prefix='phase8-recovery-'+[guid]::NewGuid().ToString('N').Substring(0,10)
$pg=$prefix+'-pg'; $mock=$prefix+'-sap'; $mail=$prefix+'-mail'; $app=$prefix+'-app'
$owned=@($app,$mock,$mail,$pg)
$oldPassword=$env:POSTGRES_PASSWORD; $oldDatabasePassword=$env:DATABASE_PASSWORD; $oldToken=$env:SAP_API_TOKEN
$env:POSTGRES_PASSWORD=[guid]::NewGuid().ToString('N')
$env:DATABASE_PASSWORD=$env:POSTGRES_PASSWORD
$env:SAP_API_TOKEN=[guid]::NewGuid().ToString('N')
function Run-Docker([string[]]$Arguments) {
 $output=& docker @Arguments
 if($LASTEXITCODE -ne 0){throw "Isolated Docker operation failed: $($Arguments[0])"}
 return $output
}
function Query([string]$Sql) {
 $output=$Sql | docker exec -i $pg psql -X -qAt -v ON_ERROR_STOP=1 -U workforce -d workforce
 if($LASTEXITCODE -ne 0){throw 'Isolated SQL failed'}
 return ($output -join "`n").Trim()
}
function Until([scriptblock]$Check,[string]$Label,[int]$Seconds=180) {
 $ErrorActionPreference='Continue'
 $deadline=[DateTime]::UtcNow.AddSeconds($Seconds)
 do {if(& $Check){Write-Output "PASS $Label";return};Start-Sleep -Milliseconds 300} while([DateTime]::UtcNow -lt $deadline)
 throw "Timeout: $Label"
}
function Healthy {
 $ErrorActionPreference='Continue'
 $null=docker exec $app curl -fsS http://localhost:8080/actuator/health 2>$null
 return $LASTEXITCODE -eq 0
}
try {
 Run-Docker @('network','create',$prefix) | Out-Null
 Run-Docker @('run','-d','--name',$pg,'--network',$prefix,'--network-alias','postgres','-e','POSTGRES_PASSWORD','-e','POSTGRES_USER=workforce','-e','POSTGRES_DB=workforce','postgres:17-alpine') | Out-Null
 Until { $null=docker exec $pg pg_isready -U workforce -d workforce 2>$null; $LASTEXITCODE -eq 0 } 'isolated PostgreSQL ready'
 Run-Docker @('run','-d','--name',$mock,'--network',$prefix,'--network-alias','mock-sap','-e','SAP_API_TOKEN','workforce-mock-sap') | Out-Null
 Run-Docker @('run','-d','--name',$mail,'--network',$prefix,'--network-alias','mailpit','axllent/mailpit:v1.31.4') | Out-Null
 Run-Docker @('run','-d','--name',$app,'--network',$prefix,'-e','DATABASE_URL=jdbc:postgresql://postgres:5432/workforce','-e','DATABASE_USERNAME=workforce','-e','DATABASE_PASSWORD','-e','KEYCLOAK_ISSUER_URI=http://unused.invalid/realms/workforce','-e','KEYCLOAK_JWK_SET_URI=http://unused.invalid/certs','-e','SAP_BASE_URL=http://mock-sap:8090','-e','SAP_API_TOKEN','-e','SAP_EXPORT_WORKER_ENABLED=true','-e','NOTIFICATION_WORKER_ENABLED=true','-e','SAP_CONNECT_TIMEOUT=1s','-e','SAP_RESPONSE_TIMEOUT=8s','-e','SAP_EXPORT_PROCESSING_TIMEOUT=25s','-e','SAP_EXPORT_POLL_INTERVAL=1s','-e','SAP_RECONCILIATION_INTERVAL=1s','-e','SAP_EXPORT_INITIAL_BACKOFF=2s','-e','SAP_EXPORT_MAX_BACKOFF=4s','-e','NOTIFICATION_SCHEDULER_ENABLED=false','-e','NOTIFICATION_POLL_INTERVAL=PT1S','-e','NOTIFICATION_INITIAL_BACKOFF=PT2S','-e','NOTIFICATION_MAX_BACKOFF=PT4S','-e','MAIL_HOST=mailpit','-e','MAIL_PORT=1025','workforce-backend') | Out-Null
 Until {Healthy} 'isolated backend migrated and healthy'
 $sheet=[guid]::NewGuid().ToString();$export=[guid]::NewGuid().ToString();$job=[guid]::NewGuid().ToString()
 Query @"
INSERT INTO app_users(id,external_subject,email,display_name,role) VALUES ('00000000-0000-0000-0000-000000000008','recovery','recovery@example.test','Recovery Employee','EMPLOYEE');
INSERT INTO employees(id,user_id,employee_number,active) VALUES ('00000000-0000-0000-0000-000000000018','00000000-0000-0000-0000-000000000008','RECOVERY-E',true);
INSERT INTO timesheets(id,employee_id,year,month,status,total_hours,submitted_at,version) VALUES ('$sheet','00000000-0000-0000-0000-000000000018',2026,10,'APPROVED',6.25,now(),3);
INSERT INTO sap_timesheet_exports(id,timesheet_id,status,created_at,updated_at) VALUES ('$export','$sheet','QUEUED',now(),now());
INSERT INTO sap_export_jobs(id,export_id,timesheet_id,status,operation,payload,next_attempt_at) VALUES ('$job','$export','$sheet','PENDING','EXPORT',jsonb_build_object('timesheetId','$sheet','employeeNumber','RECOVERY-E','year',2026,'month',10,'entries',jsonb_build_array(jsonb_build_object('projectCode','MOCK-TIMEOUT','workDate','2026-10-05','hours',6.25,'description','Isolated kill/restart'))),now());
"@ | Out-Null
 $before=Query "SELECT md5(payload::text) FROM sap_export_jobs WHERE id='$job';"
 Until { (Query "SELECT status FROM sap_export_jobs WHERE id='$job'") -eq 'PROCESSING' } 'worker claimed durable job'
 $lookup="fetch('http://localhost:8090/sap/timesheets/by-identity?identity=SAP_TIMESHEET:$sheet',{headers:{Authorization:'Bearer '+process.env.SAP_API_TOKEN}}).then(r=>process.exit(r.ok?0:1))"
 Until { $null=docker exec $mock node -e $lookup; $LASTEXITCODE -eq 0 } 'SAP persisted before delayed response' 10
 Run-Docker @('kill','--signal','KILL',$app) | Out-Null
 if((Query "SELECT status FROM sap_export_jobs WHERE id='$job'") -ne 'PROCESSING'){throw 'Kill did not interrupt the in-flight operation'}
 Write-Output 'PASS actual SIGKILL left a committed PROCESSING claim'
 Run-Docker @('start',$app) | Out-Null
 Until {Healthy} 'backend restarted'
 Until { (Query "SELECT status FROM sap_timesheet_exports WHERE id='$export'") -eq 'SUCCEEDED' } 'expired lease reconciled to success'
 if((Query "SELECT attempt_count FROM sap_export_jobs WHERE id='$job'") -ne '1'){throw 'Unexpected second POST attempt'}
 if((Query "SELECT count(*) FROM sap_export_attempts WHERE export_id='$export' AND outcome='ABANDONED'") -ne '1'){throw 'Missing abandoned attempt'}
 if((Query "SELECT md5(payload::text) FROM sap_export_jobs WHERE id='$job'") -ne $before){throw 'Snapshot changed'}
 Write-Output 'PASS one export attempt; ABANDONED then reconciliation; snapshot unchanged'
 Run-Docker @('restart',$mock) | Out-Null
 Until { $null=docker exec $mock node -e $lookup; $LASTEXITCODE -eq 0 } 'mock SAP record survives its process restart'
 Run-Docker @('stop',$mail) | Out-Null
 $notification=[guid]::NewGuid().ToString()
 Query "INSERT INTO notifications(id,type,recipient_user_id,recipient_email,subject,body,status,deduplication_key,next_attempt_at) VALUES ('$notification','TIMESHEET_APPROVED','00000000-0000-0000-0000-000000000008','recovery@example.test','Isolated recovery verification','Safe recovery fixture','PENDING','$notification',now());" | Out-Null
 Until {(Query "SELECT attempt_count FROM notifications WHERE id='$notification'") -ge 1} 'SMTP unavailable recorded an attempt'
 Run-Docker @('start',$mail) | Out-Null
 Until {(Query "SELECT status FROM notifications WHERE id='$notification'") -eq 'SENT'} 'notification delivered after SMTP recovery'
 Run-Docker @('stop',$pg) | Out-Null
 Run-Docker @('start',$pg) | Out-Null
 Until {Healthy} 'backend reconnects after PostgreSQL restart'
 if((Query "SELECT status FROM sap_timesheet_exports WHERE id='$export'") -ne 'SUCCEEDED'){throw 'Committed export lost'}
 Write-Output 'PASS committed SAP and notification state survives database restart'
} finally {
 $ErrorActionPreference='Continue'
 # Only the exact containers created by this invocation; never touch the development Compose project.
 foreach($name in $owned){if(!$name.StartsWith($prefix+'-')){throw 'Invalid cleanup target'}; docker rm -f -v $name 2>$null | Out-Null}
 docker network rm $prefix 2>$null | Out-Null
 $env:POSTGRES_PASSWORD=$oldPassword;$env:DATABASE_PASSWORD=$oldDatabasePassword;$env:SAP_API_TOKEN=$oldToken
}
