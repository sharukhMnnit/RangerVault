// File: keygen.js
const crypto = require('crypto');
const fs = require('fs');

// Generate the Key Pair (Public & Private)
// The 'privateKey' stays on this server to sign QRs.
// The 'publicKey' will be put into your Android App to verify them.
const { publicKey, privateKey } = crypto.generateKeyPairSync('rsa', {
  modulusLength: 2048,
  publicKeyEncoding: { type: 'spki', format: 'pem' },
  privateKeyEncoding: { type: 'pkcs8', format: 'pem' }
});

// Save keys to files
fs.writeFileSync('private.pem', privateKey);
fs.writeFileSync('public.pem', publicKey);

console.log("✅ Keys generated successfully!");
console.log("FILES CREATED: private.pem (Keep secret) & public.pem (Send to Android team)");