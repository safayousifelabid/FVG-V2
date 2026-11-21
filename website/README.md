# FVG — Financial Vision Guide Website (Installable PWA)

This `/website` directory contains the complete, standalone, editable Progressive Web App (PWA) matching the Android **FVG – Financial Vision Guide (`دليل رؤيتك المالي`)** application.

## 1. Copy Brand Images Before Deploying (Optional Enhancement)
To bundle Zahra's portrait, the exact PNG logo, and the Ben Al-Zahra image with the static `/website` folder, copy these 4 files from `app/src/main/res/drawable/` into `website/assets/`:
- `fvg_brand_symbol.png`
- `fvg_app_icon.jpg`
- `zahra_yousif_portrait.png`
- `fvg_ben_alzahra_products.png`

*(Built-in vector/monogram fallbacks are included so the site works cleanly even before copying.)*

## 2. Deploy Live & Get Your Shareable HTTPS Link (Firebase Hosting)
1. Install Firebase CLI (`npm install -g firebase-tools`) and run `firebase login`.
2. In your Firebase Console (`gen-lang-client-0025296187`), register a **Web App**, copy its `apiKey` and `appId` into `website/firebase-config.js`, and verify Google Sign-In is enabled.
3. Configure hosting in `firebase.json` (add `"hosting": { "public": "website", "ignore": ["firebase.json", "**/.*"] }`) and run:
   ```bash
   firebase deploy --only hosting
   ```
4. Your live HTTPS link will be `https://gen-lang-client-0025296187.web.app` (or any custom domain, Vercel, Netlify Drop `app.netlify.com/drop`, or GitHub Pages URL).

## 3. How Anyone Can Install on iPhone (iOS) or Android
- **iPhone / iPad (Safari):** Open the website URL in **Safari** → tap the **Share** button → tap **Add to Home Screen (`إضافة إلى الشاشة الرئيسية`)** → tap **Add**. FVG installs with its own Home Screen icon and opens full-screen without browser bars.
- **Android (Chrome) / Desktop (Chrome/Edge):** Tap **Install App** in the browser address bar or menu.
