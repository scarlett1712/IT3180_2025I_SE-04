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

app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

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

  const YOUR_DOMAIN = `http://localhost:3030`;

  const body = {
    orderCode: Number(String(Date.now()).slice(-6)),
    amount: amount,
    description: `Thanh toán hóa đơn: ${title}`,
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
    const paymentLinkResponse = await payOS.createPaymentLink(body);
    return res.json({ checkoutUrl: paymentLinkResponse.checkoutUrl });
  } catch (error) {
    console.log(error);
    return res.status(500).json({ error: "Server error" });
  }
});

app.listen(3030, () => {
    console.log("Server running at http://localhost:3030");
});
