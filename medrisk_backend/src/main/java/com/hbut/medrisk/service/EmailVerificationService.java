package com.hbut.medrisk.service;

import com.hbut.medrisk.entity.EmailVerificationCodeEntity;
import com.hbut.medrisk.repository.EmailVerificationCodeRepository;
import java.nio.charset.StandardCharsets;
import java.net.SocketTimeoutException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import javax.net.ssl.SSLException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService {
    public static final String PURPOSE_REGISTER = "REGISTER";
    public static final String PURPOSE_PASSWORD_RESET = "PASSWORD_RESET";

    private static final int CODE_TTL_MINUTES = 10;
    private static final int MAX_ATTEMPTS = 5;

    private final EmailVerificationCodeRepository codes;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final SecureRandom random = new SecureRandom();

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.port:465}")
    private int mailPort;

    @Value("${spring.mail.properties.mail.smtp.ssl.enable:true}")
    private boolean sslEnabled;

    @Value("${spring.mail.properties.mail.smtp.starttls.enable:false}")
    private boolean startTlsEnabled;

    @Value("${medrisk.mail.from:}")
    private String mailFrom;

    @Value("${medrisk.mail.skip-send:false}")
    private boolean skipSend;

    @Value("${medrisk.mail.test-code:}")
    private String testCode;

    public EmailVerificationService(EmailVerificationCodeRepository codes, ObjectProvider<JavaMailSender> mailSender) {
        this.codes = codes;
        this.mailSender = mailSender;
    }

    @Transactional
    public void sendCode(String email, String purpose) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedPurpose = normalizePurpose(purpose);
        String code = generateCode();
        EmailVerificationCodeEntity row = new EmailVerificationCodeEntity();
        row.setEmail(normalizedEmail);
        row.setPurpose(normalizedPurpose);
        row.setCodeHash(hash(normalizedEmail, normalizedPurpose, code));
        row.setAttempts(0);
        row.setConsumed(false);
        row.setCreatedAt(LocalDateTime.now());
        row.setExpiresAt(LocalDateTime.now().plusMinutes(CODE_TTL_MINUTES));
        codes.save(row);
        sendMail(normalizedEmail, normalizedPurpose, code);
    }

    @Transactional
    public void verifyAndConsume(String email, String purpose, String code) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedPurpose = normalizePurpose(purpose);
        EmailVerificationCodeEntity row = codes.findTopByEmailAndPurposeAndConsumedFalseOrderByCreatedAtDesc(normalizedEmail, normalizedPurpose)
                .orElseThrow(() -> new IllegalArgumentException("请先获取邮箱验证码"));
        LocalDateTime now = LocalDateTime.now();
        if (row.getExpiresAt().isBefore(now)) {
            row.setConsumed(true);
            row.setConsumedAt(now);
            throw new IllegalArgumentException("邮箱验证码已过期，请重新获取");
        }
        if (row.getAttempts() >= MAX_ATTEMPTS) {
            row.setConsumed(true);
            row.setConsumedAt(now);
            throw new IllegalArgumentException("邮箱验证码错误次数过多，请重新获取");
        }
        if (!row.getCodeHash().equals(hash(normalizedEmail, normalizedPurpose, code))) {
            row.setAttempts(row.getAttempts() + 1);
            if (row.getAttempts() >= MAX_ATTEMPTS) {
                row.setConsumed(true);
                row.setConsumedAt(now);
            }
            throw new IllegalArgumentException("邮箱验证码不正确");
        }
        row.setConsumed(true);
        row.setConsumedAt(now);
    }

    public String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("邮箱不能为空");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePurpose(String purpose) {
        if (PURPOSE_REGISTER.equals(purpose) || PURPOSE_PASSWORD_RESET.equals(purpose)) {
            return purpose;
        }
        throw new IllegalArgumentException("验证码用途不正确");
    }

    private String generateCode() {
        if (testCode != null && !testCode.isBlank()) {
            return testCode.trim();
        }
        return String.format("%06d", random.nextInt(1_000_000));
    }

    private void sendMail(String email, String purpose, String code) {
        if (skipSend) {
            return;
        }
        if (mailHost == null || mailHost.isBlank() || mailFrom == null || mailFrom.isBlank()) {
            throw new IllegalArgumentException("邮件服务未配置，请设置 MEDRISK_MAIL_HOST、MEDRISK_MAIL_FROM 等 SMTP 环境变量");
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            throw new IllegalArgumentException("邮件服务未启用，请检查 Spring Mail 配置");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject(PURPOSE_REGISTER.equals(purpose) ? "MedRisk AI 注册验证码" : "MedRisk AI 密码重置验证码");
        message.setText("""
                您正在使用 MedRisk AI 疾病风险预测平台。

                验证码：%s

                验证码 10 分钟内有效，请勿转发给他人。本系统仅用于教学演示和健康风险提示，不能替代医生诊断。
                """.formatted(code));
        try {
            sender.send(message);
        } catch (MailAuthenticationException ex) {
            throw new IllegalArgumentException("邮件认证失败，请检查邮箱账号、SMTP 授权码和是否已开启 SMTP 服务");
        } catch (MailSendException ex) {
            throw new IllegalArgumentException(mailSendMessage(ex));
        } catch (MailException ex) {
            throw new IllegalArgumentException(mailSendMessage(ex));
        }
    }

    private String mailSendMessage(Exception ex) {
        Throwable root = rootCause(ex);
        if (root instanceof SocketTimeoutException) {
            return "邮件发送超时，请检查服务器到 smtp.163.com:465 的网络连通性和防火墙策略";
        }
        if (root instanceof SSLException) {
            return "邮件 SSL 握手失败，请确认 163 SMTP 使用 465 端口且 MEDRISK_MAIL_SSL=true、MEDRISK_MAIL_STARTTLS=false";
        }
        String lower = (root.getMessage() == null ? "" : root.getMessage()).toLowerCase(Locale.ROOT);
        if (lower.contains("authentication") || lower.contains("auth") || lower.contains("535")) {
            return "邮件认证失败，请使用 163 第三方客户端授权码作为 MEDRISK_MAIL_PASSWORD";
        }
        if (lower.contains("connection") || lower.contains("connect") || lower.contains("refused")) {
            return "邮件服务器连接失败，请检查 SMTP 主机、465 端口、SSL 配置和服务器出站网络";
        }
        return "邮件发送失败，请检查 SMTP 配置：当前主机 " + safeMailHost() + "，端口 " + mailPort
                + "，SSL=" + sslEnabled + "，STARTTLS=" + startTlsEnabled;
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private String safeMailHost() {
        return mailHost == null || mailHost.isBlank() ? "未配置" : mailHost;
    }

    private String hash(String email, String purpose, String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((email + "|" + purpose + "|" + code.trim()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
