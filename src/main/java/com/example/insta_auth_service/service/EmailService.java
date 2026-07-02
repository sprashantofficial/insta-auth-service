package com.example.insta_auth_service.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Async
    public void sendOtpEmail(String toEmail, String username, String otp, String purpose) {
        try {
            MimeMessage message = mailSender.createMimeMessage();

            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject("Instagram - Your verification code: " + otp);
            helper.setText(buildOtpEmail(username, purpose, otp), true);

            mailSender.send(message);
            log.info("OTP email sent to: {}", toEmail);
        } catch (MessagingException e) {
            log.error("Failed to send OTP email to {}: {}", toEmail, e.getMessage());
        }
    }

    public void sendWelcomeEmail(String toEmail, String username) {
        try {
            MimeMessage message = mailSender.createMimeMessage();

            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject("Welcome to Instagram, " + username + "!");
            helper.setText(buildWelcomeEmail(username), true);

            mailSender.send(message);
            log.info("Welcome email sent to: {}", toEmail);
        } catch (MessagingException e) {
            log.error("Failed to send welcome email to {}: {}", toEmail, e.getMessage());
        }
    }

    public String buildOtpEmail(String username, String purpose, String otp) {
        return """
                <!DOCTYPE html>
                <html>
                <body style="font-family: Arial, sans-serif; background: #fafafa; padding: 20px;">
                  <div style="max-width: 400px; margin: auto; background: #fff; border: 1px solid #dbdbdb; border-radius: 8px; padding: 40px 30px; text-align: center;">
                    <h2 style="color: #262626;">Instagram</h2>
                    <p style="color: #262626;">Hi <strong>%s</strong>,</p>
                    <p style="color: #8e8e8e;">%s</p>
                    <div style="font-size: 32px; font-weight: bold; letter-spacing: 12px; color: #262626; margin: 24px 0;">%s</div>
                    <p style="color: #8e8e8e; font-size: 13px;">This code expires in 10 minutes. Do not share it with anyone.</p>
                  </div>
                </body>
                </html>
                """.formatted(username, purpose, otp);
    }

    public String buildWelcomeEmail(String username) {
        return """
                <!DOCTYPE html>
                <html>
                <body style="font-family: Arial, sans-serif; background: #fafafa; padding: 20px;">
                  <div style="max-width: 400px; margin: auto; background: #fff; border: 1px solid #dbdbdb; border-radius: 8px; padding: 40px 30px; text-align: center;">
                    <h2 style="color: #262626;">Welcome to Instagram!</h2>
                    <p style="color: #262626;">Hi <strong>%s</strong>, your account is ready.</p>
                    <p style="color: #8e8e8e;">Start sharing your moments with the world.</p>
                  </div>
                </body>
                </html>
                """.formatted(username);
    }

}
