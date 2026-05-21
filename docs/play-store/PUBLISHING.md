# Publish Signal Scoop to Google Play ($2)

You must complete these steps in **your** Google account. A coding agent cannot register Play Console or accept payments for you.

## Costs (Google, not us)

| Fee | Amount |
|-----|--------|
| **Play Developer registration** | **$25 one-time** (required to publish any app) |
| **Your app price** | **$2.00** per install (you keep ~70% after Google’s fee; exact share depends on region and tax) |

There is **no** Play Store fee specifically for “$2 apps” — you set the price in Console.

---

## Phase 1 — Prepare the release bundle (on your PC)

### 1. Create an upload keystore (once)

```bash
chmod +x scripts/generate-release-keystore.sh scripts/build-play-bundle.sh
./scripts/generate-release-keystore.sh
```

**Back up** `release/signalsoop-upload.jks` and passwords. Losing them means you **cannot update** the app on Play.

### 2. Build the App Bundle (AAB)

```bash
./scripts/build-play-bundle.sh
```

Upload file: **`release/signal-scoop-play.aab`**

Play Store requires **AAB**, not APK, for new apps.

---

## Phase 2 — Google Play Console

### 1. Create a developer account

1. Go to [Google Play Console](https://play.google.com/console).
2. Pay the **$25** registration fee and complete identity verification (can take 24–48 hours).

### 2. Set up payments (required for paid apps)

1. **Settings → Payments profile** — complete tax and banking.
2. Without this, you cannot sell a paid app.

### 3. Create the app

1. **All apps → Create app**
2. Name: **Signal Scoop**
3. Default language, app/game = App, free/paid = **Paid** (you’ll set price next).

### 4. Set price to $2

1. **Monetize → Products → App pricing** (or **Pricing** in left menu).
2. Choose **Paid**.
3. Set base price **USD 2.00** (adjust other countries or use pricing templates).

Paid apps do **not** use in-app billing for the initial purchase — users pay at install time.

### 5. Store listing

Use text from [STORE_LISTING.md](STORE_LISTING.md).

**Graphics required:**

| Asset | Size |
|-------|------|
| App icon | 512×512 PNG (use logo) |
| Feature graphic | 1024×500 PNG |
| Phone screenshots | At least 2, min 320px short side |

Capture screenshots from a **debug** build on a real device (release builds set `FLAG_SECURE`). Scan tab with **Scan nearby signals** at top, findings + graph preview, **Graph → Scans** with insights card, **Graph → Map** full screen, scan detail sheet, privacy card.

### 6. App content (compliance)

| Item | What to declare |
|------|-----------------|
| **Privacy policy** | Public HTTPS URL hosting [PRIVACY_POLICY.md](../PRIVACY_POLICY.md) |
| **Ads** | No, app does not contain ads |
| **Data safety** | See [DATA_SAFETY.md](DATA_SAFETY.md) |
| **Content rating** | Complete IARC questionnaire (likely **Everyone** / low maturity) |
| **Target audience** | Not designed for children; age 13+ if asked |
| **News app / COVID / etc.** | No |

### 7. Release

1. **Release → Production → Create new release**
2. Upload `release/signal-scoop-play.aab`
3. Release name: `1.0.0 (1)`
4. Add release notes (what’s new).
5. Complete any remaining **Policy status** checks.
6. **Send for review** (first review often 1–7 days).

---

## Phase 3 — Host privacy policy (required)

Play requires a **public HTTPS** privacy policy because the app uses location and Bluetooth.

**Option A — GitHub Pages**

1. Push repo to GitHub.
2. Enable Pages from `main` / `docs` folder.
3. Add `docs/privacy-policy.html` (convert from PRIVACY_POLICY.md) or serve the markdown via Pages.
4. URL example: `https://yourusername.github.io/signal-scoop/PRIVACY_POLICY.html`

**Option B — Your own domain**

Upload `docs/PRIVACY_POLICY.md` as HTML on your site.

Paste the URL in Play Console → **App content → Privacy policy**.

---

## Checklist before submit

- [ ] `keystore.properties` and `*.jks` are **not** committed to git
- [ ] Privacy policy URL live and matches app behavior
- [ ] Support email in listing and privacy policy
- [ ] Data safety form matches [DATA_SAFETY.md](DATA_SAFETY.md)
- [ ] Tested release build on a physical device
- [ ] Payments profile complete for paid distribution

---

## After approval

- Updates: bump `versionCode` / `versionName` in `android/app/build.gradle.kts` (current: **10** / **1.6.2-beta**), rebuild AAB, upload new release.
- Refunds: handled by Google Play policy, not in-app.

## Common rejections

- **Missing privacy policy** for location/Bluetooth
- **Data safety** inconsistent with permissions
- **Misleading claims** — avoid “detect hidden cameras” in listing; we scan **radio signals only**
- **Broken signing** — upload AAB signed with the same upload key

---

## What we cannot do for you

- Pay the $25 developer fee
- Verify your identity in Play Console
- Link your bank account
- Click “Publish” in your Console account

If you share a GitHub repo URL, you can host the privacy policy in minutes; the scripts above produce the signed bundle ready to upload.
