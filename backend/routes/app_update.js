import express from "express";
import fetch from "node-fetch"; // Standard fetch in Node 18+
import dotenv from "dotenv";

dotenv.config();
const router = express.Router();

// Cache variables
let cachedLatestRelease = null;
let lastFetchTime = 0;
const CACHE_DURATION = 15 * 60 * 1000; // 15 minutes cache

router.get("/latest-version", async (req, res) => {
    const now = Date.now();

    // 1. Serve from cache if valid
    if (cachedLatestRelease && (now - lastFetchTime < CACHE_DURATION)) {
        console.log("Serving update info from cache");
        return res.json(cachedLatestRelease);
    }

    // 2. Fetch from GitHub with Token
    try {
        const githubUrl = "https://api.github.com/repos/scarlett1712/IT3180_2025I_SE-04/releases/latest";

        // Ensure you have GITHUB_TOKEN in your .env file
        const token = process.env.GITHUB_TOKEN;

        const headers = {
            "User-Agent": "Enoti-Server"
        };

        // Only add Authorization if token exists
        if (token) {
            headers["Authorization"] = `Bearer ${token}`;
        }

        const response = await fetch(githubUrl, { headers });

        if (!response.ok) {
            throw new Error(`GitHub Error: ${response.statusText}`);
        }

        const data = await response.json();

        // 3. Extract APK info
        const apkAsset = data.assets.find(a => a.name.endsWith(".apk"));

        if (!apkAsset) {
            return res.status(404).json({ error: "No APK file found in the latest release" });
        }

        // 4. Update Cache
        cachedLatestRelease = {
            version: data.tag_name, // e.g., "v1.0.2"
            download_url: apkAsset.browser_download_url,
            release_notes: data.body,
            published_at: data.published_at
        };
        lastFetchTime = now;

        console.log("Fetched new update info from GitHub");
        res.json(cachedLatestRelease);

    } catch (error) {
        console.error("Update Check Error:", error);
        // Serve stale cache if available, otherwise error
        if (cachedLatestRelease) return res.json(cachedLatestRelease);
        res.status(500).json({ error: "Failed to check for updates" });
    }
});

export default router;