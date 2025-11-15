require("dotenv").config();

const express = require("express");
const cors = require("cors");
const PayOS = require("@payos/node");

const app = express();

// --- PAYOS --- //
const payOS = new PayOS(
  process.env.PAYOS_CLIENT_ID,
  process.env.PAYOS_API_KEY,
  process.env.PAYOS_CHECKSUM_KEY
);

// Render YÊU CẦU dùng PORT từ ENV
const PORT = process.env.PORT || 3030;

app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Serve static for success.html / cancel.html
app.use("/", express.static("public"));

/**
 * API nhận dữ liệu từ Android:
 * { title: "Tên hóa đơn", amount: 150000 }
 */
app.post("/create-payment-link", async (req, res) => {
  const { title, amount } = req.body;

  if (!amount || amount <= 0) {
    return res.status(400).json({ error: "Amount invalid" });
  }

  const YOUR_DOMAIN = `https://it3180-2025i-se-04.onrender.com`;

  // 🔥 Giới hạn description ≤ 25 ký tự
  const fullDesc = `${title}`;
  const shortDesc = fullDesc.slice(0, 25);

  const body = {
    orderCode: Number(String(Date.now()).slice(-6)),
    amount: amount,
    description: shortDesc,
    items: [
      {
        name: title,
        quantity: 1,
        price: amount,
      },
    ],
    returnUrl: `${YOUR_DOMAIN}/success.html`,
    cancelUrl: `${YOUR_DOMAIN}/cancel.html`,
  };

  try {
    console.log("Request gửi sang PayOS:", body);

    const paymentLinkResponse = await payOS.createPaymentLink(body);

    console.log("PayOS trả về:", paymentLinkResponse);

    if (paymentLinkResponse.checkoutUrl) {
      return res.json({ checkoutUrl: paymentLinkResponse.checkoutUrl });
    }

    if (paymentLinkResponse.data?.checkoutUrl) {
      return res.json({ checkoutUrl: paymentLinkResponse.data.checkoutUrl });
    }

    return res.status(500).json({ error: "No checkoutUrl from PayOS" });

  } catch (error) {
    console.log("PayOS ERROR:", error.response?.data || error);
    return res.status(500).json({
      error: "PayOS error",
      detail: error.message,
    });
  }
});

app.listen(PORT, () => {
  console.log("Server running at port:", PORT);
});
