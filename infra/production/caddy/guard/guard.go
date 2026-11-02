// Package guard provides bounded single-proxy abuse protection without trusting
// client-supplied forwarding headers or adding a third-party Caddy plugin.
package guard

import (
	"fmt"
	"net"
	"net/http"
	"net/netip"
	"strings"
	"sync"
	"time"

	"github.com/caddyserver/caddy/v2"
	"github.com/caddyserver/caddy/v2/caddyconfig/caddyfile"
	"github.com/caddyserver/caddy/v2/caddyconfig/httpcaddyfile"
	"github.com/caddyserver/caddy/v2/modules/caddyhttp"
)

func init() {
	caddy.RegisterModule(Guard{})
	httpcaddyfile.RegisterHandlerDirective("workforce_guard", parse)
}

type Guard struct {
	Mode   string `json:"mode"`
	quota  *quotas
	active chan struct{}
}

func (Guard) CaddyModule() caddy.ModuleInfo {
	return caddy.ModuleInfo{ID: "http.handlers.workforce_guard", New: func() caddy.Module { return new(Guard) }}
}
func (g *Guard) Provision(caddy.Context) error {
	if g.Mode != "application" && g.Mode != "authentication" {
		return fmt.Errorf("invalid guard mode")
	}
	g.quota = &quotas{items: make(map[string]window), maxKeys: 10000}
	g.active = make(chan struct{}, 64)
	return nil
}
func parse(h httpcaddyfile.Helper) (caddyhttp.MiddlewareHandler, error) {
	g := new(Guard)
	if err := g.UnmarshalCaddyfile(h.Dispenser); err != nil {
		return nil, err
	}
	return g, nil
}
func (g *Guard) UnmarshalCaddyfile(d *caddyfile.Dispenser) error {
	for d.Next() {
		if !d.AllArgs(&g.Mode) {
			return d.ArgErr()
		}
		if d.NextBlock(0) {
			return d.Err("blocks are not supported")
		}
	}
	return nil
}

type window struct {
	end   time.Time
	count int
}
type limit struct {
	key     string
	maximum int
	period  time.Duration
}
type quotas struct {
	mu        sync.Mutex
	items     map[string]window
	maxKeys   int
	lastSweep time.Time
}

// Check and consume all budgets atomically; no external work under this mutex.
func (q *quotas) take(now time.Time, limits ...limit) bool {
	q.mu.Lock()
	defer q.mu.Unlock()
	if now.Sub(q.lastSweep) >= time.Minute {
		for key, w := range q.items {
			if !now.Before(w.end) {
				delete(q.items, key)
			}
		}
		q.lastSweep = now
	}
	additions := 0
	for _, l := range limits {
		w, exists := q.items[l.key]
		if !exists {
			additions++
		}
		if now.Before(w.end) && w.count >= l.maximum {
			return false
		}
	}
	if len(q.items)+additions > q.maxKeys {
		return false
	}
	for _, l := range limits {
		w := q.items[l.key]
		if !now.Before(w.end) {
			w = window{end: now.Add(l.period)}
		}
		w.count++
		q.items[l.key] = w
	}
	return true
}

func clientKey(remote string) (string, error) {
	host, _, err := net.SplitHostPort(remote)
	if err != nil {
		return "", err
	}
	ip, err := netip.ParseAddr(host)
	if err != nil {
		return "", err
	}
	ip = ip.Unmap()
	// One IPv6 /64 budget prevents trivial address rotation within a subnet.
	if ip.Is6() {
		return netip.PrefixFrom(ip, 64).Masked().String(), nil
	}
	return ip.String(), nil
}
func budgets(mode, key, method, path string) []limit {
	// JAX-RS can accept matrix parameters. They must not evade action budgets.
	parts := strings.Split(path, "/")
	for i, part := range parts {
		parts[i], _, _ = strings.Cut(part, ";")
	}
	path = strings.TrimRight(strings.Join(parts, "/"), "/")
	limits := []limit{{"request:" + key, 600, time.Minute}, {"request:global", 3000, time.Minute}}
	mutation := method != "GET" && method != "HEAD" && method != "OPTIONS"
	if mode == "authentication" {
		// All dynamic realm actions, including GET resend/required actions, share a
		// budget. Static theme resources do not consume this tighter budget.
		if strings.HasPrefix(path, "/realms/") {
			limits = append(limits, limit{"auth:" + key, 30, time.Minute}, limit{"auth:global", 600, time.Minute})
		}
		if strings.Contains(path, "/login-actions/") || strings.HasSuffix(path, "/registrations") {
			limits = append(limits, limit{"account:" + key, 12, time.Minute}, limit{"account-hour:" + key, 60, time.Hour}, limit{"account:global", 300, time.Hour})
		}
	} else if mutation {
		limits = append(limits, limit{"write:" + key, 30, time.Minute}, limit{"write:global", 300, time.Minute})
	}
	return limits
}
func deny(w http.ResponseWriter) error {
	w.Header().Set("Retry-After", "60")
	w.Header().Set("Cache-Control", "no-store")
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusTooManyRequests)
	_, err := w.Write([]byte(`{"code":"RATE_LIMITED","message":"Too many requests. Please try again later."}`))
	return err
}
func (g *Guard) ServeHTTP(w http.ResponseWriter, r *http.Request, next caddyhttp.Handler) error {
	key, err := clientKey(r.RemoteAddr)
	if err != nil {
		return deny(w)
	}
	if !g.quota.take(time.Now(), budgets(g.Mode, key, r.Method, r.URL.Path)...) {
		return deny(w)
	}
	select {
	case g.active <- struct{}{}:
		defer func() { <-g.active }()
	default:
		return deny(w)
	}
	return next.ServeHTTP(w, r)
}

var _ caddy.Provisioner = (*Guard)(nil)
var _ caddyfile.Unmarshaler = (*Guard)(nil)
var _ caddyhttp.MiddlewareHandler = (*Guard)(nil)
