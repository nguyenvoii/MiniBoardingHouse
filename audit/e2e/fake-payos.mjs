// payOS giả (HTTPS cục bộ) cho kiểm thử: kiểm chữ ký HMAC như payOS thật, trả checkoutUrl, báo PAID khi gọi /_pay/<order>.
import https from 'node:https';
import fs from 'node:fs';
import crypto from 'node:crypto';
const dir = process.argv[2];
const KEY = process.env.PAYOS_CHECKSUM ?? 'fake-checksum';
const orders = new Map();
const pem = fs.readFileSync(`${dir}/fake.pem`);
https.createServer({ key: pem, cert: pem }, (req, res) => {
  let body = '';
  req.on('data', (d) => (body += d));
  req.on('end', () => {
    const send = (o, c = 200) => { res.writeHead(c, { 'content-type': 'application/json' }); res.end(JSON.stringify(o)); };
    if (req.method === 'POST' && req.url === '/v2/payment-requests') {
      if (req.headers['x-client-id'] !== 'fake-client' || req.headers['x-api-key'] !== 'fake-key') return send({ code: '201', desc: 'bad credentials' }, 401);
      const b = JSON.parse(body);
      const val = `amount=${b.amount}&cancelUrl=${b.cancelUrl}&description=${b.description}&orderCode=${b.orderCode}&returnUrl=${b.returnUrl}`;
      const sig = crypto.createHmac('sha256', KEY).update(val).digest('hex');
      if (sig !== b.signature) return send({ code: '201', desc: 'bad signature' }, 400);
      if (orders.has(b.orderCode)) return send({ code: '231', desc: 'orderCode da ton tai' }, 400); // payOS that tu choi ma don trung
      orders.set(b.orderCode, { amount: b.amount, status: 'PENDING', amountPaid: 0 });
      return send({ code: '00', desc: 'success', data: { orderCode: b.orderCode, amount: b.amount, checkoutUrl: `https://localhost:5443/pay/${b.orderCode}` } });
    }
    let m = /^\/v2\/payment-requests\/(\d+)$/.exec(req.url);
    if (req.method === 'GET' && m) { const o = orders.get(Number(m[1])); if (!o) return send({ code: '101', desc: 'not found' }, 404); return send({ code: '00', data: { orderCode: Number(m[1]), amount: o.amount, status: o.status, amountPaid: o.amountPaid } }); }
    m = /^\/_pay\/(\d+)$/.exec(req.url);
    if (m) { const o = orders.get(Number(m[1])); if (!o) return send({}, 404); o.status = 'PAID'; o.amountPaid = o.amount; return send({ ok: true }); }
    m = /^\/pay\/(\d+)$/.exec(req.url);
    if (m) { res.writeHead(200, { 'content-type': 'text/html' }); return res.end(`<h1>payOS giả: thanh toán đơn ${m[1]}</h1>`); }
    send({ code: '404' }, 404);
  });
}).listen(5443, '127.0.0.1', () => console.log('fake payOS up'));
