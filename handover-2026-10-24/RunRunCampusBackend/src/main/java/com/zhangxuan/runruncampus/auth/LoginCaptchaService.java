package com.zhangxuan.runruncampus.auth;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginCaptchaService {
    private static final int WIDTH = 160;
    private static final int HEIGHT = 56;
    private static final int MAX_ACTIVE_CHALLENGES = 10_000;
    private static final String[][] DIGITS = {
            {"11111", "10001", "10011", "10101", "11001", "10001", "11111"},
            {"00100", "01100", "00100", "00100", "00100", "00100", "01110"},
            {"11110", "00001", "00001", "11110", "10000", "10000", "11111"},
            {"11110", "00001", "00001", "01110", "00001", "00001", "11110"},
            {"10010", "10010", "10010", "11111", "00010", "00010", "00010"},
            {"11111", "10000", "10000", "11110", "00001", "00001", "11110"},
            {"01111", "10000", "10000", "11110", "10001", "10001", "01110"},
            {"11111", "00001", "00010", "00100", "01000", "01000", "01000"},
            {"01110", "10001", "10001", "01110", "10001", "10001", "01110"},
            {"01110", "10001", "10001", "01111", "00001", "00001", "11110"}
    };

    private final SecureRandom random;
    private final Clock clock;
    private final Map<String, State> challenges = new ConcurrentHashMap<>();

    public LoginCaptchaService() {
        this(new SecureRandom(), Clock.systemUTC());
    }

    LoginCaptchaService(SecureRandom random, Clock clock) {
        this.random = random;
        this.clock = clock;
    }

    public Challenge issue() {
        return issue("%04d".formatted(random.nextInt(10_000)));
    }

    Challenge issue(String answer) {
        Instant now = clock.instant();
        challenges.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        if (challenges.size() >= MAX_ACTIVE_CHALLENGES) challenges.clear();
        byte[] tokenBytes = new byte[24];
        random.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        Instant expiresAt = now.plusSeconds(180);
        challenges.put(token, new State(hash(answer), expiresAt));
        return new Challenge(token, "data:image/png;base64," + render(answer), expiresAt);
    }

    public void verify(String token, String answer) {
        if (token == null || answer == null) throw rejected();
        State state = challenges.remove(token);
        if (state == null || !state.expiresAt().isAfter(clock.instant())
                || !MessageDigest.isEqual(state.answerHash(), hash(answer.trim()))) {
            throw rejected();
        }
    }

    private String render(String answer) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(244, 248, 241));
            graphics.fillRect(0, 0, WIDTH, HEIGHT);
            for (int line = 0; line < 12; line++) {
                graphics.setColor(new Color(110 + random.nextInt(90), 120 + random.nextInt(80), 100 + random.nextInt(90)));
                graphics.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT), random.nextInt(WIDTH), random.nextInt(HEIGHT));
            }
            int scale = 5;
            int startX = 15;
            for (int digitIndex = 0; digitIndex < answer.length(); digitIndex++) {
                String[] glyph = DIGITS[answer.charAt(digitIndex) - '0'];
                graphics.setColor(new Color(22 + random.nextInt(30), 70 + random.nextInt(45), 40 + random.nextInt(35)));
                int x = startX + digitIndex * 34;
                int y = 10 + random.nextInt(4);
                for (int row = 0; row < glyph.length; row++) {
                    for (int column = 0; column < glyph[row].length(); column++) {
                        if (glyph[row].charAt(column) == '1') {
                            graphics.fillRect(x + column * scale, y + row * scale, scale, scale);
                        }
                    }
                }
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成登录验证码", exception);
        } finally {
            graphics.dispose();
        }
    }

    private static byte[] hash(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static ResponseStatusException rejected() {
        return new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, "请输入正确的图形验证码");
    }

    public record Challenge(String challengeToken, String imageDataUrl, Instant expiresAt) { }
    private record State(byte[] answerHash, Instant expiresAt) { }
}
