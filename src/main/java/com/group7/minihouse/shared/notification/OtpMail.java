package com.group7.minihouse.shared.notification;

/** Thu gui ma OTP: tieu de + ban chu thuong + ban HTML (nguoi nhan thay HTML, thiet bi cu thay chu). */
public record OtpMail(String subject, String text, String html) {

    private static final String HTML = """
            <!doctype html><html lang="vi"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1"><title>{{heading}}</title></head>
            <body style="margin:0;padding:0;background:#f1f5f1;">
            <div style="display:none;max-height:0;overflow:hidden;opacity:0;">Mã của bạn là {{code}}. Có hiệu lực 10 phút.</div>
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#f1f5f1;padding:24px 12px;">
            <tr><td align="center">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:460px;background:#ffffff;border-radius:12px;overflow:hidden;font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#14231c;">
            <tr><td style="background:#0e6b4a;padding:18px 28px;color:#ffffff;font-size:20px;font-weight:700;">Stayly <span style="color:#f4c20d;">&#9679;</span></td></tr>
            <tr><td style="padding:28px 28px 8px;">
            <h1 style="margin:0 0 8px;font-size:20px;line-height:1.3;color:#14231c;">{{heading}}</h1>
            <p style="margin:0;font-size:15px;line-height:1.5;color:#3f5147;">{{lead}}</p>
            </td></tr>
            <tr><td align="center" style="padding:16px 28px;">
            <div style="background:#eaf3ee;border-radius:10px;padding:16px 0;font-family:Consolas,'Courier New',monospace;font-size:34px;font-weight:700;letter-spacing:10px;color:#0e6b4a;">{{code}}</div>
            </td></tr>
            <tr><td style="padding:0 28px 24px;font-size:14px;line-height:1.5;color:#3f5147;">Mã có hiệu lực <b>10 phút</b> và chỉ dùng được một lần.</td></tr>
            <tr><td style="padding:16px 28px 24px;border-top:1px solid #dae3dc;font-size:13px;line-height:1.5;color:#596b61;">Không phải bạn yêu cầu? Hãy bỏ qua email này, tài khoản của bạn vẫn an toàn. Đừng chia sẻ mã cho bất kỳ ai, kể cả người xưng là nhân viên Stayly.</td></tr>
            </table>
            <p style="margin:14px 0 0;font:12px 'Segoe UI',Arial,sans-serif;color:#596b61;">Email tự động từ Stayly, vui lòng không trả lời.</p>
            </td></tr></table></body></html>
            """;

    public static OtpMail recovery(String code) {
        return build("Mã đặt lại mật khẩu Stayly: " + code, "Đặt lại mật khẩu",
                "Nhập mã này vào Stayly để tiếp tục đặt lại mật khẩu.", code);
    }

    public static OtpMail emailVerification(String code) {
        return build("Mã xác thực email Stayly: " + code, "Xác thực email",
                "Nhập mã này vào Stayly để kích hoạt tài khoản.", code);
    }

    /** heading, lead la chuoi co dinh trong code; code chi gom chu so nen khong can escape HTML. */
    private static OtpMail build(String subject, String heading, String lead, String code) {
        String text = "Stayly - " + heading + "\n\n" + lead + "\n\nMã của bạn: " + code
                + "\n\nMã có hiệu lực 10 phút và chỉ dùng được một lần."
                + "\nKhông phải bạn yêu cầu? Hãy bỏ qua email này, tài khoản của bạn vẫn an toàn."
                + "\nĐừng chia sẻ mã cho bất kỳ ai, kể cả người xưng là nhân viên Stayly.";
        String html = HTML.replace("{{heading}}", heading).replace("{{lead}}", lead).replace("{{code}}", code);
        return new OtpMail(subject, text, html);
    }
}
