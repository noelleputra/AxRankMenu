

# AxRankMenu VotePoints currency

AxRankMenu exposes `VotePoints` as a separate currency backed by Allium on the
Velocity proxy. It is not the same balance as Allium Credits.

Configure `hooks.VotePoints.api-key` in `plugins/AxRankMenu/config.yml` using
the dedicated `api.vote-points.axrankmenu-api-key` from Allium/Velocity.
Rank purchases call the proxy asynchronously and debit the balance atomically
before purchase actions run. API errors fail closed and do not execute actions.

Select the currency for an individual rank in `plugins/AxRankMenu/ranks.yml`:

```yaml
VIP:
  rank: "VIP"
  price: 100
  currency: VotePoints
```

Use a separate secret from Allium's general API key and Votifae's credit key.
Use HTTPS for non-loopback API connections and keep the proxy endpoint private.
