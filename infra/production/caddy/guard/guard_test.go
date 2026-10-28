package guard

import (
	"net/http"
	"net/http/httptest"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/caddyserver/caddy/v2"
	"github.com/caddyserver/caddy/v2/modules/caddyhttp"
)

func TestWindowAndCapacity(t *testing.T) {
	q := &quotas{items: make(map[string]window), maxKeys: 2}
	now := time.Unix(1000, 0)
	limits := []limit{{"client", 2, time.Minute}, {"global", 3, time.Minute}}
	if !q.take(now, limits...) || !q.take(now, limits...) || q.take(now, limits...) {
		t.Fatal("budget not enforced")
	}
	if q.take(now, limit{"new", 1, time.Minute}) {
		t.Fatal("unbounded map")
	}
	if !q.take(now.Add(time.Minute), limit{"new", 1, time.Minute}) {
		t.Fatal("expired keys not recovered")
	}
}
func TestConcurrentClaims(t *testing.T) {
	q := &quotas{items: make(map[string]window), maxKeys: 10}
	now := time.Unix(1000, 0)
	var accepted atomic.Int32
	var wg sync.WaitGroup
	for i := 0; i < 100; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			if q.take(now, limit{"global", 10, time.Minute}) {
				accepted.Add(1)
			}
		}()
	}
	wg.Wait()
	if accepted.Load() != 10 {
		t.Fatal(accepted.Load())
	}
}
func TestClientIdentity(t *testing.T) {
	for _, remote := range []string{"[::ffff:192.0.2.1]:123", "192.0.2.1:456"} {
		key, err := clientKey(remote)
		if err != nil || key != "192.0.2.1" {
			t.Fatal(remote, key, err)
		}
	}
	a, _ := clientKey("[2001:db8:1::1]:123")
	b, _ := clientKey("[2001:db8:1::ffff]:456")
	if a != b {
		t.Fatal("IPv6 rotation bypass")
	}
	if _, err := clientKey("invalid"); err == nil {
		t.Fatal("invalid peer accepted")
	}
}
func TestAuthenticationAndForwardedSpoofing(t *testing.T) {
	g := &Guard{Mode: "authentication"}
	if err := g.Provision(caddy.Context{}); err != nil {
		t.Fatal(err)
	}
	next := caddyhttp.HandlerFunc(func(w http.ResponseWriter, r *http.Request) error { w.WriteHeader(204); return nil })
	for i := 0; i < 13; i++ {
		req := httptest.NewRequest("GET", "https://auth.test/realms/workforce/login-actions/reset-credentials?token=not-logged", nil)
		req.RemoteAddr = "192.0.2.1:123"
		req.Header.Set("X-Forwarded-For", time.Now().String())
		res := httptest.NewRecorder()
		if err := g.ServeHTTP(res, req, next); err != nil {
			t.Fatal(err)
		}
		expected := 204
		if i == 12 {
			expected = 429
		}
		if res.Code != expected {
			t.Fatal(i, res.Code)
		}
		if i == 12 && (res.Header().Get("Retry-After") == "" || res.Header().Get("Cache-Control") != "no-store") {
			t.Fatal("missing retry/privacy headers")
		}
	}
}
func TestGlobalBudgetAndNoPartialConsumption(t *testing.T) {
	q := &quotas{items: make(map[string]window), maxKeys: 100}
	now := time.Unix(1000, 0)
	if !q.take(now, limit{"global", 1, time.Hour}, limit{"first", 5, time.Minute}) {
		t.Fatal("initial request")
	}
	if q.take(now, limit{"second", 5, time.Minute}, limit{"global", 1, time.Hour}) {
		t.Fatal("global bypass")
	}
	if _, ok := q.items["second"]; ok {
		t.Fatal("denied request consumed unrelated quota")
	}
}
func TestConcurrencyBoundAndMode(t *testing.T) {
	if (&Guard{Mode: "typo"}).Provision(caddy.Context{}) == nil {
		t.Fatal("invalid mode accepted")
	}
	g := &Guard{Mode: "application"}
	_ = g.Provision(caddy.Context{})
	for i := 0; i < cap(g.active); i++ {
		g.active <- struct{}{}
	}
	req := httptest.NewRequest("POST", "https://app.test/onboarding", nil)
	req.RemoteAddr = "192.0.2.2:123"
	res := httptest.NewRecorder()
	_ = g.ServeHTTP(res, req, caddyhttp.HandlerFunc(func(http.ResponseWriter, *http.Request) error { t.Fatal("overloaded request forwarded"); return nil }))
	if res.Code != 429 {
		t.Fatal(res.Code)
	}
}
func TestActionClassification(t *testing.T) {
	for _, path := range []string{"/realms/workforce/login-actions/registration", "/realms/workforce/login-actions/required-action", "/realms/workforce/login-actions/reset-credentials", "/realms/workforce/protocol/openid-connect/registrations", "/realms/workforce/login-actions;x=y/registration", "/realms/workforce/protocol/openid-connect/registrations;x=y/"} {
		if len(budgets("authentication", "client", "GET", path)) != 7 {
			t.Fatal("unprotected action", path)
		}
	}
	if len(budgets("authentication", "client", "GET", "/resources/theme.js")) != 2 {
		t.Fatal("static resources consume action quota")
	}
	if len(budgets("application", "client", "POST", "/any-server-action")) != 4 {
		t.Fatal("application mutation missing quota")
	}
}
