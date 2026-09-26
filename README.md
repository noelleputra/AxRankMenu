

# AxRankMenu VotePoints currency

AxRankMenu exposes `VotePoints` as a separate currency owned by Votifae. It
is not the same balance as Allium Credits.

For Allium Credits, install the Allium Bukkit plugin on each backend and
configure its connection to the central Allium Velocity API. AxRankMenu uses
Allium's asynchronous Bukkit `AlliumCurrencyService`; it does not hold an
Allium API URL or key and does not connect to the database. The Bukkit provider
forwards balance and transaction requests to Velocity, where Allium owns the
ledger and performs atomic, idempotent operations.

Install Nullaelib and Votifae on the same backend. AxRankMenu discovers the
asynchronous `VotePointsService` through Bukkit ServicesManager; no HTTP
endpoint, port, or API key is required. Rank purchases debit the balance
atomically before purchase actions run. Service/database errors fail closed
and do not execute actions.

Enable the currencies with `hooks.Allium.register` and
`hooks.VotePoints.register` in `plugins/AxRankMenu/config.yml`. Remove any old
`hooks.Allium` URL, API-key, and timeout entries from AxRankMenu's config;
configure the Allium Velocity connection only in Allium Bukkit.

Select the currency for an individual rank in `plugins/AxRankMenu/ranks.yml`:

```yaml
VIP:
  rank: "VIP"
  price: 100
  currency: VotePoints
```
