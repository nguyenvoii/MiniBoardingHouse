// SMTP nhận thư tối giản cho kiểm thử: ghi mọi thư vào tệp JSON (không gửi đi đâu).
import net from 'node:net';
import fs from 'node:fs';
const out = process.argv[3] ?? 'smtp-sink.json';
fs.writeFileSync(out, '[]');
net.createServer((sock) => {
  let buf = '', data = false, msg = { to: [], raw: '' };
  sock.write('220 sink ESMTP\r\n');
  sock.on('data', (d) => {
    buf += d.toString('utf8');
    let i;
    while (data ? (i = buf.indexOf('\r\n.\r\n')) >= 0 : (i = buf.indexOf('\r\n')) >= 0) {
      if (data) {
        msg.raw = buf.slice(0, i); buf = buf.slice(i + 5); data = false;
        const all = JSON.parse(fs.readFileSync(out, 'utf8')); all.push(msg); fs.writeFileSync(out, JSON.stringify(all));
        msg = { to: [], raw: '' }; sock.write('250 OK queued\r\n');
      } else {
        const line = buf.slice(0, i); buf = buf.slice(i + 2);
        const u = line.toUpperCase();
        if (u.startsWith('EHLO') || u.startsWith('HELO')) sock.write('250-sink\r\n250 8BITMIME\r\n');
        else if (u.startsWith('RCPT TO:')) { msg.to.push(line.slice(8).replace(/[<>\s]/g, '')); sock.write('250 OK\r\n'); }
        else if (u === 'DATA') { data = true; sock.write('354 go\r\n'); }
        else if (u === 'QUIT') { sock.write('221 bye\r\n'); sock.end(); }
        else sock.write('250 OK\r\n');
      }
    }
  });
}).listen(Number(process.argv[2] ?? 2525), '127.0.0.1', () => console.log('smtp sink up'));
