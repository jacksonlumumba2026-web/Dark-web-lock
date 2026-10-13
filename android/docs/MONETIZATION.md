# Dark Web Lock — Monetization & Play Store launch guide

This explains the freemium model in the app today and the concrete steps left to
charge money and publish. Nothing here needs code I can run for you from a
phone — it needs your Google Play Console account.

## The model

- **Free:** full tracking (connect/disconnect history, left-behind alert) for all
  devices, plus last-known location for **one** device.
- **Pro (paid):** location tracking + history map for **all** devices.

Google Play Billing is now wired in (`BillingManager`, product id `pro_unlock`,
one-time purchase). It stays dormant until that product exists in Play Console
and the app is installed from a Play track: until then the Pro card says the
purchase is not available yet, and a debug-only "Test unlock" button lets you
exercise the gating. Once you create the product, no code change is needed — it
activates automatically (change `BillingManager.PRODUCT_ID` only if you use a
different id).

## What still needs your Play account

Billing cannot be built or tested without a Play Console, because the product
and price live there, not in the code. Steps:

1. **Create a Google Play developer account** (one-time US$25) at
   https://play.google.com/console .
2. **Create the app** in the console; reserve the name "Dark Web Lock".
3. **Set up an in-app product**:
   - For a one-time unlock: a *managed product* (e.g. `pro_unlock`).
   - For a subscription: a *subscription* (e.g. `pro_monthly`).
   Pick one; one-time is simpler and buyers often prefer it for a utility app.
4. Create the managed product with id **`pro_unlock`** (one-time). The billing
   code already queries, buys, acknowledges, persists and restores this product,
   so it starts working as soon as the product is live and the app is installed
   from a Play track (internal testing is enough). Use a different id only if you
   change `BillingManager.PRODUCT_ID` to match.

## Store listing (draft copy you can paste)

**Title:** Dark Web Lock — Find My Earbuds

**Short description (80 chars):**
Know when your earbuds disconnect and where you last had them. For any device.

**Full description:**
Lose your cheap wireless earbuds? Dark Web Lock watches your Bluetooth devices
and tells you the moment they disconnect — then saves where your phone was, so
you can retrace your steps on a map.

- Live connection status for every paired device
- A left-behind alert when a device disconnects
- A history of every connect and disconnect
- "Where you last had them" — a map pin from the moment they disconnected
- An ownership record for your devices

Honest by design: it reads your phone's Bluetooth and location only, stores
everything on your phone, and uploads nothing. It cannot track the earbuds
themselves or lock them — no app can do that with generic earbuds — but it can
tell you where you last had them, which is usually all you need.

Free tracks one device's location; Pro unlocks all your devices.

## Data safety form (answers for the console)

- **Does the app collect or share data?** Collects, does not share.
- **Location:** Collected. Purpose: app functionality. **Not** shared. Processed
  on-device; optional; user can disable.
- **Personal info (name/contact):** Collected (owner record). Not shared.
  On-device only, optional.
- **Data encrypted in transit?** No data leaves the device.
- **Can users request deletion?** Yes — in-app clear, or uninstall.
- **Privacy policy URL:** host `docs/privacy.html` (see below) and paste its URL.

## Permissions to declare / justify

- Bluetooth (scan, connect) — core function.
- Foreground service (connected device + location) — background monitoring.
- **Background / "all the time" location** — Play requires a short video and a
  written justification: "Location is recorded at the moment a Bluetooth device
  disconnects, while the app is closed, so the user can find a device they left
  behind." Keep the feature optional to ease review.
- Post notifications — alerts.

## Hosting the privacy policy (free)

`docs/privacy.html` is ready to publish. Your GitHub Pages site already serves
the repo root. To publish the policy without touching your existing site, copy
`android/docs/privacy.html` to the repository root as `privacy.html`; it will be
live at `https://<your-pages-domain>/privacy.html`. Fill in the contact email
first. (I did not place it at the root myself, to avoid changing your live site
without asking.)

## Realistic expectations

Utility apps rarely earn a lot. The honest pitch here is a useful, low-cost tool
for a real gap — generic earbuds with no "Find My". Treat revenue as modest
side-income, not a startup.
