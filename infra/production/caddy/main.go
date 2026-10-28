package main

import (
	caddycmd "github.com/caddyserver/caddy/v2/cmd"
	_ "github.com/caddyserver/caddy/v2/modules/standard"
	_ "workforce.invalid/caddy-security-build/guard"
)

func main() { caddycmd.Main() }
