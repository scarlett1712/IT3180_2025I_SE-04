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

const PORT = process.env.PORT || 3030;

app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

app.use("/", express.static("public"));

/**
 * API nhận dữ liệu từ Android:
 * { title: "...", amount: ..., financeId: ..., userId: ... }
 */
app.post("/create-payment-link", async (req, res) => {
  // 🔥 KHÔNG nhận orderCode từ Android nữa
  const { title, amount, financeId, userId } = req.body;

  // 🔥 SERVER TỰ TẠO MÃ ĐƠN
  // (Dùng timestamp cắt gọn để đảm bảo duy nhất và là số)
  const orderCode = Number(String(Date.now()).slice(-6) + Math.floor(Math.random() * 10));

  // Giới hạn description
  const fullDesc = `${title}`;
  const shortDesc = fullDesc.slice(0, 25);

  // Tạo URL trả về (Vẫn gắn orderCode vào để success.html lưu được)
  const YOUR_DOMAIN = `https://it3180-2025i-se-04.onrender.com`;
  const returnUrl = `${YOUR_DOMAIN}/success.html?user_id=${userId}&finance_id=${financeId}&amount=${amount}&description=${encodeURIComponent(title)}&ordercode=${orderCode}`;
  const cancelUrl = `${YOUR_DOMAIN}/cancel.html`;

  const body = {
    orderCode: orderCode, // Dùng mã server vừa tạo
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
    const paymentLinkResponse = await payOS.createPaymentLink(body);
    res.json({ checkoutUrl: paymentLinkResponse.checkoutUrl });
  } catch (error) {
    console.error(error);
    res.status(500).json({ error: error.message });
  }
});

app.listen(PORT, () => {
  console.log("Server running at port:", PORT);
});