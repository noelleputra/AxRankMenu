

# AxRankMenu VotePoints currency

AxRankMenu exposes `VotePoints` as a separate currency owned by Votifae. It
is not the same balance as Allium Credits.

Configure `hooks.VotePoints.api-key` in `plugins/AxRankMenu/config.yml` using
the `vote-points.api.api-key` from Votifae. Rank purchases call Votifae
asynchronously and debit the balance atomically before purchase actions run.
API errors fail closed and do not execute actions.
If upgrading an existing install, change the VotePoints `base-url` from the
old Allium endpoint to `http://127.0.0.1:8766` when both plugins share a host.

Select the currency for an individual rank in `plugins/AxRankMenu/ranks.yml`:

```yaml
VIP:
  rank: "VIP"
  price: 100
  currency: VotePoints
```

Use a unique secret of at least 32 characters. The Votifae endpoint binds to
loopback; if the plugins are on different hosts, connect through a private TLS
reverse proxy and configure its HTTPS URL here.
