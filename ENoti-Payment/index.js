require("dotenv").config();

const express = require("express");
const cors = require("cors");
const PayOS = require("@payos/node");

const app = express();

const payOS = new PayOS(
  process.env.PAYOS_CLIENT_ID,
  process.env.PAYOS_API_KEY,
  process.env.PAYOS_CHECKSUM_KEY
);

const PORT = process.env.PORT || 3030;

app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

app.use("/", express.static("public"));

app.post("/create-payment-link", async (req, res) => {
  // 🔥 1. NHẬN userId TỪ ANDROID (Quan trọng)
  // Android chỉ cần gửi: { title, amount, financeId, userId }
  const { title, amount, financeId, userId } = req.body;

  if (!amount || amount <= 0) {
    return res.status(400).json({ error: "Amount invalid" });
  }

  // Kiểm tra userId để tránh lỗi sau này
  if (!userId) {
      console.log("⚠️ Cảnh báo: Android chưa gửi userId!");
  }

  const YOUR_DOMAIN = `https://it3180-2025i-se-04.onrender.com`;

  const fullDesc = `${title}`;
  const shortDesc = fullDesc.slice(0, 25);

  // 🔥 2. SERVER TỰ TẠO ORDER CODE (Android không cần gửi)
  const orderCode = Number(String(Date.now()).slice(-6) + Math.floor(Math.random() * 10));

  // 🔥 3. GẮN userId VÀO LINK TRẢ VỀ
  // Đây là bước quan trọng nhất để lưu được hóa đơn
  const returnUrl = `${YOUR_DOMAIN}/success.html?user_id=${userId}&finance_id=${financeId}&amount=${amount}&description=${encodeURIComponent(title)}&ordercode=${orderCode}`;
  const cancelUrl = `${YOUR_DOMAIN}/cancel.html`;

  const body = {
    orderCode: orderCode,
    amount: amount,
    description: shortDesc,
    items: [
      {
        name: title,
        quantity: 1,
        price: amount,
      },
    ],
    returnUrl: returnUrl,
    cancelUrl: cancelUrl,
  };

  try {
    console.log("Request gửi sang PayOS:", body);

    const paymentLinkResponse = await payOS.createPaymentLink(body);

    console.log("PayOS trả về:", paymentLinkResponse);

    if (paymentLinkResponse.checkoutUrl) {
      return res.json({ checkoutUrl: paymentLinkResponse.checkoutUrl });
    } else if (paymentLinkResponse.data?.checkoutUrl) {
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