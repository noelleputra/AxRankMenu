

# AxRankMenu VotePoints currency

AxRankMenu exposes `VotePoints` as a separate currency owned by Votifae. It
is not the same balance as Allium Credits.

Install Nullaelib and Votifae on the same backend. AxRankMenu discovers the
asynchronous `VotePointsService` through Bukkit ServicesManager; no HTTP
endpoint, port, or API key is required. Rank purchases debit the balance
atomically before purchase actions run. Service/database errors fail closed
and do not execute actions.

When upgrading, remove old VotePoints URL, API-key, and timeout settings from
`plugins/AxRankMenu/config.yml`; only `hooks.VotePoints.register` is used now.

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
