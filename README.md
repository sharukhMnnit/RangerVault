# 🛡️ RangerVault: Zero-Trust Identity System
> **Team IdentifAI** | NIT Allahabad  
> *Problem Statement: Black Ranger's Identity Vault – QR Verification*

**RangerVault** is a secure, offline-first identity verification system designed to prevent unauthorized access to HQ. It uses cryptographically signed, time-based QR codes that can be verified even without an internet connection.

---

## 🚀 Key Features
* **Dynamic Identity:** QR codes regenerate every 30 seconds (TOTP) to prevent screenshot forgery.
* **Offline Verification (The Sentinel):** Scanners use Public Key Cryptography to verify IDs without needing Wi-Fi or a database connection.
* **Biometric Wallet:** Android BiometricPrompt ensures only the real user can generate a pass.
* **Real-Time Dashboard:** A "Zordon-View" web dashboard that logs entry attempts instantly (when online).

---

## 🛠️ Tech Stack
* **Mobile App:** Native Android (Kotlin), Jetpack Compose, ZXing (QR Gen), JourneyApps (Scanner).
* **Backend:** Node.js, Express.js.
* **Security:** RSA-2048 Asymmetric Encryption, SHA-256 Signatures.
* **Tools:** Retrofit (Networking), Coroutines.

---

## ⚙️ How to Run

### 1. Backend (HQ Server)
The backend issues the secure signatures.
```bash
cd RangerVault
npm install
node keygen.js   # Generates private.pem (Server) and public.pem (App)
node index.js    # Starts server on Port 3000
