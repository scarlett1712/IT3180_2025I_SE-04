import express from "express";
import cors from "cors";
import dotenv from "dotenv";

dotenv.config();

// FIX: Use destructured import to correctly get the PayOS constructor
import { PayOS } from "@payos/node";
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

// --- MODIFIED ROUTE: Returns JSON for Mobile App ---
router.post("/create-payment-link", async (req, res) => {

  // Define the production domain from environment variable, falling back to the URL you provided.
  const PROD_DOMAIN = process.env.BASE_URL || 'https://nmcnpm-se-04.onrender.com';

  // Determine the correct domain based on the request host.
  // If running locally, use localhost:5000 (HTTP). Otherwise, use the production domain (HTTPS).
  const YOUR_DOMAIN = req.headers.host && req.headers.host.includes("localhost")
    ? `http://localhost:5000`
    : PROD_DOMAIN;

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
    // These paths must be accessible on your Render domain!
    returnUrl: `${YOUR_DOMAIN}/success.html`,
    cancelUrl: `${YOUR_DOMAIN}/cancel.html`,
  };

  try {
    const paymentLinkResponse = await payOS.createPaymentLink(body);

    // ✅ Return the checkout URL as JSON for the client (Android) to open
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
// This endpoint must be publicly accessible at YOUR_DOMAIN/receive-hook
router.post("/receive-hook", (req, res) => {
  const event = req.body;
  console.log("=== 🔔 PayOS Webhook Received ===");
  console.log(event);
  console.log("===================================");

  // 💡 Implement webhook validation and database update here.

  // Must return 200 to acknowledge the webhook
  res.json({ message: "Webhook received successfully" });
});

export default router;