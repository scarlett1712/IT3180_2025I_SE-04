import express from "express";
import cors from "cors";
import PayOS from "@payos/node";
import dotenv from "dotenv";

dotenv.config();

const router = express.Router();

// 💡 Load PayOS configuration from environment variables
const payOS = new PayOS(
  process.env.PAYOS_CLIENT_ID,
  process.env.PAYOS_API_KEY,
  process.env.PAYOS_CHECKSUM_KEY
);

// Middleware for this router (assuming it's not already applied globally)
router.use(cors());
router.use(express.json());
router.use(express.urlencoded({ extended: false }));

// ⚠️ Note: Serving static files is usually done in the main app, but is kept here for reference.
// router.use("/", express.static("public"));

// --- MODIFIED ROUTE: Returns JSON for Mobile App ---
router.post("/create-payment-link", async (req, res) => {
  // Use a reliable public domain for the return/cancel URLs (e.g., your ngrok/production URL)
  // For production, use your actual domain (e.g., https://your-app-api.com)
  const YOUR_DOMAIN = req.headers.host.includes("localhost")
    ? `http://localhost:5000` // Use the correct port 5000 if testing locally
    : `https://${req.headers.host}`;

  // 💡 Ideally, amount and items should be passed from the client in req.body
  const { amount, description, items } = req.body;

  const body = {
    // Generate a unique order code
    orderCode: Number(String(Date.now()).slice(-6)),
    amount: amount || 2000,
    description: description || "Thanh toán hóa đơn",
    items: items || [
      {
        name: "Mì tôm Hảo Hảo ly",
        quantity: 1,
        price: 2000,
      },
    ],
    // The user's browser/WebView will be redirected here after payment
    returnUrl: `${YOUR_DOMAIN}/success.html`,
    cancelUrl: `${YOUR_DOMAIN}/cancel.html`,
  };

  try {
    const paymentLinkResponse = await payOS.createPaymentLink(body);

    // ✅ Key fix: Return the checkout URL as JSON for the client (Android) to open
    res.json({
      success: true,
      orderCode: body.orderCode,
      checkoutUrl: paymentLinkResponse.checkoutUrl,
    });
  } catch (error) {
    console.error("PayOS Error:", error);
    res.status(500).json({
      success: false,
      error: "Failed to create payment link",
      details: error.message
    });
  }
});

// --- Webhook Route: Server-to-Server Payment Status Notification ---
// This endpoint must be publicly accessible (e.g., via ngrok or your production domain)
// The JSON response acknowledges receipt of the webhook.
router.post("/receive-hook", (req, res) => {
  const event = req.body;
  console.log("=== 🔔 PayOS Webhook Received ===");
  console.log(event);
  console.log("===================================");

  // 💡 You should add logic here to:
  // 1. Validate the webhook signature (PayOS provides utilities for this)
  // 2. Update the status of the corresponding order in your database.

  // Must return 200 to acknowledge the webhook
  res.json({ message: "Webhook received successfully" });
});

export default router;