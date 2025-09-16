# Zota Signer for Burp (Montoya API, Java 21)

A Burp Suite extension that automatically signs Zota API requests (Deposit, Payout, Order Status, Orders Report CSV, Exchange Rates) and can generate pre-populated requests into Repeater.

**Highlights**

- Repeater-focused signing, with optional Proxy/Intruder toggles.
- Resigns requests in-place, including manual context-menu re-signs.
- Multiple profiles, persisted inside the active Burp project.
- Generates sample requests wired to Burp Collaborator hosts for callback testing.
- Logs warnings instead of blocking when required fields are empty.

> Built against Montoya API `2025.8`, Java 21.

## Build

Open in IntelliJ (Gradle project) or run:

```bash
./gradlew clean shadowJar
```

The shaded jar is written to `build/libs/`. Load it in **Extender → Extensions → Add**.

## Security Note

Credentials and settings are stored in the active Burp project using Montoya project-scoped persistence. No local files are written.

## What gets signed (per Zota docs)

- **Deposit Request** (`POST /api/v1/deposit/request/{EndpointID}/` or `/group/{EndpointGroupID}/`):
  `EndpointID|EndpointGroupID` + `merchantOrderID` + `orderAmount` + `customerEmail` + `MerchantSecretKey`

- **Payout Request** (`POST /api/v1/payout/request/{EndpointID}/`):
  `EndpointID` + `merchantOrderID` + `orderAmount` + `customerEmail` + `customerBankAccountNumber` + `MerchantSecretKey`

- **Order Status** (`GET /api/v1/query/order-status/`):
  `MerchantID` + `merchantOrderID` + `orderID` + `timestamp` + `MerchantSecretKey`

- **Orders Report CSV** (`GET /api/v1/query/orders-report/csv/`):
  `MerchantID` + `dateType` + `endpointIds` + `fromDate` + `requestID` + `statuses` + `timestamp` + `toDate` + `types` + `MerchantSecretKey`

- **Exchange Rates** (`GET /api/v1/query/exchange-rates/`):
  `MerchantID` + `requestID` + `date` + `timestamp` + `orderType` + `orderID` + `MerchantSecretKey`

Additional verifications (display-only in UI when seen in Proxy history):

- **Final Redirect (GET back to merchant)**:
  `status` + `orderID` + `merchantOrderID` + `MerchantSecretKey`

- **Callback (POST to merchant)**:
  `EndpointID` + `orderID` + `merchantOrderID` + `status` + `amount` + `customerEmail` + `MerchantSecretKey`

All signatures are SHA-256 lowercased hex. Missing fields are treated as empty strings. Notes and warnings are added as request annotations (no extra headers).

## Quick start

1. Load the JAR into Burp.
2. Open the **Zota** tab, add your profile(s) (Merchant ID, Secret, Endpoint ID).
3. Keep **Auto sign** enabled (default).
4. Use Repeater. The extension will sign outgoing Zota requests automatically.
5. Use the **Generate** buttons in the Zota tab to send example requests. Emails/callback URLs point to a fresh Burp Collaborator domain per generation.

## License

MIT — see [`LICENSE`](LICENSE).
