param([Parameter(Mandatory=$true)][string]$FixturePath)
$ErrorActionPreference='Stop'
# This reads only subject UUIDs. The fixture can omit passwords entirely.
$fixture=Get-Content -Raw -LiteralPath $FixturePath | ConvertFrom-Json
$subjects=@('employee','manager','finance','admin') | ForEach-Object { "'"+[guid]::Parse($fixture.$_.subject).ToString()+"'" }
$sql="SELECT encode(convert_to(COALESCE(json_agg(n ORDER BY n.type,n.id)::text,'[]'),'UTF8'),'hex') FROM (SELECT n.id,n.type,n.recipient_email,n.subject,n.body,n.status FROM notifications n JOIN app_users u ON u.id=n.recipient_user_id WHERE u.external_subject IN ("+($subjects -join ',')+")) n;"
$hex=$sql | docker compose exec -T postgres psql -X -qAt -v ON_ERROR_STOP=1 -U workforce -d workforce
if($LASTEXITCODE -ne 0){throw 'Unable to read QA notification intents'}
$hex=($hex -join '').Trim()
$bytes=New-Object byte[] ($hex.Length/2)
for($i=0;$i -lt $bytes.Length;$i++){$bytes[$i]=[Convert]::ToByte($hex.Substring($i*2,2),16)}
$root=(Get-Location).Path
$target=Join-Path $root '.verification/phase8-mailpit-intents.json'
[IO.Directory]::CreateDirectory((Join-Path $root '.verification')) | Out-Null
[IO.File]::WriteAllBytes($target,$bytes)
$script=Join-Path $root 'infra/dev/verify-phase8-mailpit.mjs'
$ErrorActionPreference='Continue'
docker run --rm --network workforce_default --mount "type=bind,source=$target,target=/intents.json,readonly" --mount "type=bind,source=$script,target=/verify.mjs,readonly" -e MAILPIT_API_URL=http://mailpit:8025 workforce-browser-tests node /verify.mjs /intents.json
exit $LASTEXITCODE
