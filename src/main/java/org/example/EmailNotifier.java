package org.example;

import javax.mail.*;
import javax.mail.internet.*;
import java.util.Properties;

public class EmailNotifier {
    private static final String SMTP_HOST = "smtp.mail.ru";
    private static final String SMTP_PORT = "465";
    private static final String EMAIL = "test.federal@mail.ru";
    private static final String PASSWORD = "v0WAWZZfFiR5PqpLbZHd";

    public static void sendCurrencyUpdate(String subject, String messageBody) {
        Properties props = new Properties();
        props.put("mail.smtp.host", SMTP_HOST);
        props.put("mail.smtp.port", SMTP_PORT);
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.auth", "true");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(EMAIL, PASSWORD);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(EMAIL));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(EMAIL));
            message.setSubject(subject);
            message.setText(messageBody);
            Transport.send(message);
            System.out.println("Письмо отправлено на почту");

        } catch (MessagingException e) {
            System.err.println("Ошибка отправки: " + e.getMessage());
        }
    }
    public static String formatCurrencyMessage(String usdInvesting, String eurInvesting,
                                            String usdCbr, String eurCbr) {
        StringBuilder sb = new StringBuilder();
        sb.append("Курсы валют\n\n");
        sb.append("Investing.com:\n");
        sb.append("  USD/RUB: ").append(usdInvesting).append("\n");
        sb.append("  EUR/RUB: ").append(eurInvesting).append("\n\n");
        sb.append("Центробанк РФ:\n");
        sb.append("  USD/RUB: ").append(usdCbr).append("\n");
        sb.append("  EUR/RUB: ").append(eurCbr).append("\n");
        return sb.toString();
    }
    public static String formatDifferenceMessage(Float difUsdInvesting, Float difEurInvesting) {
        StringBuilder sb = new StringBuilder();
        sb.append("Investing.com:\n");
        if (difUsdInvesting != null){
            sb.append("  USD/RUB изменился на: ").append(String.format("%.2f",difUsdInvesting)).append("%\n");
        }
        if (difEurInvesting != null){
            sb.append("  EUR/RUB изменился на: ").append(String.format("%.2f",difEurInvesting)).append("%\n");
        }
        return sb.toString();
    }
}
