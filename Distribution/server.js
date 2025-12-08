const express = require('express');
const path = require('path');
const cors = require('cors');
const fs = require('fs');

const app = express();
const PORT = process.env.PORT || 8080;

// 1. Middleware
app.use(cors()); // Allow cross-origin requests
app.use(express.json());

// 2. Serve Static Files (HTML, CSS, Images)
// This tells Express to look in the 'public' folder for files
app.use(express.static(path.join(__dirname, 'public')));

// 3. APK Download Route
app.get('/download/android', (req, res) => {
    // Defines the path to your APK file
    const apkPath = path.join(__dirname, 'downloads', 'rangervault.apk');

    // Check if file exists before trying to send it
    if (fs.existsSync(apkPath)) {
        // res.download automatically sets the correct headers for file transfer
        // The second argument 'RangerVault.apk' is the name the user will see when saving
        res.download(apkPath, 'RangerVault.apk', (err) => {
            if (err) {
                console.error("Error downloading file:", err);
                if (!res.headersSent) {
                    res.status(500).send("Server Error could not download file.");
                }
            }
        });
    } else {
        console.error("APK file missing at path:", apkPath);
        res.status(404).send("APK file not found on server.");
    }
});

// 4. Fallback Route
// If someone goes to a weird link, send them back to the landing page
app.get('*', (req, res) => {
    res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

// 5. Start Server
app.listen(PORT, '0.0.0.0', () => {
    console.log(`🚀 RangerVault Landing Page running on port ${PORT}`);
    console.log(`📥 Download Link: http://localhost:${PORT}/download/android`);
});