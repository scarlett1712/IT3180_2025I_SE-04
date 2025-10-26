const express = require('express');
const router = express.Router();
const pool = require('../config/db');

/**
 * Tạo khoản phí mới (invoice) theo apartment_number (phòng)
 * Body gồm:
 * {
 *   title: "Phí gửi xe tháng 10",
 *   content: "Phí gửi xe theo tháng",
 *   fee_id: 1,
 *   amount: 200000,
 *   due_date: "2025-11-05",
 *   receiver_numbers: ["P101", "P102"],
 *   send_all: false
 * }
 */
router.post('/create', async (req, res) => {
  const client = await pool.connect();
  try {
    const {
      title,
      content,
      fee_id,
      amount,
      due_date,
      receiver_numbers,
      send_all
    } = req.body;

    if (!fee_id || !amount || !due_date) {
      return res.status(400).json({ error: 'Thiếu dữ liệu bắt buộc!' });
    }

    await client.query('BEGIN');

    let apartments = receiver_numbers;

    // Nếu chọn "gửi tất cả" → lấy toàn bộ apartment_number
    if (send_all) {
      const all = await client.query('SELECT apartment_number FROM apartment');
      apartments = all.rows.map(r => r.apartment_number);
    }

    for (const aptNumber of apartments) {
      // Lấy apartment_id theo apartment_number
      const apartmentRes = await client.query(
        `SELECT apartment_id FROM apartment WHERE apartment_number = $1`,
        [aptNumber]
      );

      if (apartmentRes.rows.length === 0) {
        console.warn(`Không tìm thấy phòng: ${aptNumber}`);
        continue;
      }

      const apartment_id = apartmentRes.rows[0].apartment_id;

      // Tạo hóa đơn
      const invoiceRes = await client.query(
        `INSERT INTO invoice (apartment_id, issue_date, due_date, total_amount, status)
         VALUES ($1, CURRENT_DATE, $2, $3, 'unpaid')
         RETURNING invoice_id`,
        [apartment_id, due_date, amount]
      );
      const invoiceId = invoiceRes.rows[0].invoice_id;

      // Thêm chi tiết hóa đơn
      await client.query(
        `INSERT INTO invoicedetail (invoice_id, fee_id, quantity, amount)
         VALUES ($1, $2, 1, $3)`,
        [invoiceId, fee_id, amount]
      );
    }

    await client.query('COMMIT');
    res.status(201).json({ message: 'Tạo khoản phí thành công!' });
  } catch (error) {
    await client.query('ROLLBACK');
    console.error(error);
    res.status(500).json({ error: 'Lỗi khi tạo khoản phí!' });
  } finally {
    client.release();
  }
});

module.exports = router;
