const express = require('express');
const crypto = require('crypto');
const fs = require('fs');
const bodyParser = require('body-parser');
const cors = require('cors');

const app = express();
app.use(bodyParser.json());
app.use(cors());

// Load Keys
let privateKey;
try {
    privateKey = fs.readFileSync('./private.pem', 'utf8');
} catch (e) {
    console.error("❌ ERROR: Keys not found. Run 'node keygen.js'");
    process.exit(1);
}

// --- NEW: IN-MEMORY LOG STORAGE ---
// In a real app, this would be PostgreSQL [cite: 17]
let scanLogs = []; 

// 1. Generate Identity Endpoint
app.post('/api/generate-identity', (req, res) => {
    const { userId, role } = req.body; 
    const timeWindow = Math.floor(Date.now() / 30000);
    const payload = `${userId}|${role}|${timeWindow}`;

    const sign = crypto.createSign('SHA256');
    sign.update(payload);
    sign.end();
    const signature = sign.sign(privateKey, 'base64');

    res.json({ payload, signature });
});

// 2. NEW: Receive Scan Logs Endpoint
app.post('/api/log-entry', (req, res) => {
    const { userId, status, timestamp } = req.body;
    
    const newLog = {
        id: scanLogs.length + 1,
        userId: userId || "Unknown",
        status: status, // "GRANTED" or "DENIED"
        time: new Date().toLocaleTimeString(),
        device: req.ip
    };
    
    scanLogs.unshift(newLog); // Add to top of list
    console.log(`📝 Log received: ${userId} -> ${status}`);
    res.json({ success: true });
});

// 3. NEW: Dashboard Endpoint (Frontend will call this)
app.get('/api/logs', (req, res) => {
    res.json(scanLogs);
});

const PORT = 3000;
app.listen(PORT, '0.0.0.0', () => { // '0.0.0.0' allows access from local network
    console.log(`🚀 RangerVault HQ running on port ${PORT}`);
});









// // File: index.js
// const express = require('express');
// const crypto = require('crypto');
// const fs = require('fs');
// const bodyParser = require('body-parser');
// const cors = require('cors');

// const app = express();
// app.use(bodyParser.json());
// app.use(cors()); // Allows your Android app to talk to this server

// // Load the Private Key (Must exist from Step 1)
// let privateKey;
// try {
//     privateKey = fs.readFileSync('./private.pem', 'utf8');
// } catch (e) {
//     console.error("❌ ERROR: private.pem not found! Run 'node keygen.js' first.");
//     process.exit(1);
// }

// // API Endpoint: Android App calls this to get a QR code
// app.post('/api/generate-identity', (req, res) => {
//     const { userId, role } = req.body; 

//     if (!userId) {
//         return res.status(400).json({ error: "User ID is required" });
//     }

//     console.log(`Received request for: ${userId}`);

//     // 1. Create a Time Window (30 seconds)
//     // This creates a "TOTP" effect. The code changes every 30 seconds.
//     const timeWindow = Math.floor(Date.now() / 30000);

//     // 2. Create the Payload String
//     const payload = `${userId}|${role || 'ranger'}|${timeWindow}`;

//     // 3. Sign the Payload using the Private Key
//     const sign = crypto.createSign('SHA256');
//     sign.update(payload);
//     sign.end();
//     const signature = sign.sign(privateKey, 'base64');

//     // 4. Send response
//     res.json({
//         payload: payload,
//         signature: signature
//     });
// });

// const PORT = 3000;
// app.listen(PORT, () => {
//     console.log(`🚀 RangerVault HQ Server running on port ${PORT}`);
//     console.log(`Waiting for requests...`);
// });