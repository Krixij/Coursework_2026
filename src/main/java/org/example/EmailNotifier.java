package org.example;

import javax.mail.*;
import javax.mail.internet.*;
import java.util.Properties;

public class EmailNotifier {
    private static final String host = "smtp.mail.ru";
    private static final String port = "465";
    private static final String email = "test.federal@mail.ru";
    private static final String pass = "v0WAWZZfFiR5PqpLbZHd";

    public static void sendCurrencyMail(String subject, String messageBody) {
        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port);
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.auth", "true");
        Session session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(email, pass);
            }
        });
        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(email));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(email));
            message.setSubject(subject);
            message.setText(messageBody);
            Transport.send(message);
            System.out.println("Письмо отправлено на почту");

        } catch (MessagingException e) {
            System.err.println("Ошибка отправки: " + e.getMessage());
        }
    }
    public static String formatCurrencyMessage(String usdInvesting, String eurInvesting, String usdCbr, String eurCbr) {
        String messageMail = "Investing.com:\n" + "  USD/RUB: " + usdInvesting + "\n" + "  EUR/RUB: " + eurInvesting + "\n\n" + "ЦБ РФ:\n" + "  USD/RUB: " + usdCbr + "\n" + "  EUR/RUB: " + eurCbr + "\n";
        return messageMail;
    }
    public static String formatDifferenceMessage(float differentUsd, float differentEur) {
        String messageMail = "Investing.com:\n";
        if (differentUsd != 0){
            messageMail += "  USD/RUB изменился на: " + String.format("%.2f",differentUsd) + "%\n";
        }
        if (differentEur != 0){
            messageMail += "  EUR/RUB изменился на: " + String.format("%.2f",differentEur) + "%\n";
        }
        return messageMail;
    }
}
