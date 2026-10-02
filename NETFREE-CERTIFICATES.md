# NetFree certificate support — beta 0.14

The app trusts Android system CAs everywhere. For `mitmachim.top` and its subdomains only, it additionally trusts five NetFree X2 roots and 17 unexpired provider-specific NetFree CA certificates. It does **not** trust arbitrary user-installed CAs or disable TLS verification. External media hosts do not inherit these additional trust anchors.

Provider certificates were retrieved from NetFree's [official provider certificate directory](https://netfree.link/wiki/Security_certificate_for_download_as_per_provider) at `https://netfree.link/cacert/isp/<provider>/ca.crt` on 2026-09-27. The bundled set covers: `018`, `099`, `amitnet`, `bezeq`, `hadran-vpn`, `hot`, `ib-itc`, `ib-partner`, `ib-spotnet`, `itc`, `kosher-sim-cellcom`, `ksim-itc`, `ksim-partner`, `rl-internet`, `sim-kasher-triple-c`, `x2one`, and `yossi`. All 22 bundled certificates were checked to be CA certificates, self-issued, distinct, and unexpired at build time.

Three certificates still linked in that directory were **not** bundled because they had expired: `019` (2026-02-28), `kosher-sim` (2026-05-22), and `netfree-anywhere` (2026-02-12). Their users may be covered by NetFree X2 roots, but this cannot be confirmed without testing on those networks. A new or rotated provider CA also requires a future app update. “All NetFree types” therefore cannot be guaranteed solely by packaging the currently published files.

The earlier global “no internet” banner used Android network capabilities as a proxy for real forum reachability; this could show a false offline warning on filtered connections. It was removed. Actual failed forum requests still display a Hebrew error and retry button, with a distinct message for TLS/certificate failures.

Before declaring NetFree compatibility verified, install the release APK on a real NetFree-connected device and test forum loading, login, posting, and external image loading. If it fails, collect the exact on-screen error, Android version, app version, provider name, and whether `https://mitmachim.top` opens in the device browser. Do not ask for private keys or passwords.
