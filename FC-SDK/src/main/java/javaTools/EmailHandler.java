package javaTools;

import appTools.Inputer;

import javax.mail.*;
import javax.mail.internet.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;

public class EmailHandler {
    private String emailAddress;
    private String password;
    private String smtpHost;
    private int smtpPort;
    private String imapHost;
    private int imapPort;
    public static void main(String[] args) {
        EmailHandler handler = new EmailHandler(
                "your_email@example.com",
                "your_password",
                "smtp.example.com",
                587,
                "imap.example.com",
                993
        );

        try {
            // Send an email
            handler.sendEmail("recipient@example.com", "Test Subject", "This is a test email body.");

            // Receive recent emails
            List<Map<String, String>> recentEmails = handler.receiveEmails("INBOX", 3);
            for (Map<String, String> email : recentEmails) {
                System.out.println("Subject: " + email.get("subject"));
                System.out.println("From: " + email.get("from"));
                System.out.println("Date: " + email.get("date"));
                System.out.println("Body: " + email.get("body").substring(0, Math.min(100, email.get("body").length())) + "...");
                System.out.println("---");
            }
        } catch (MessagingException | IOException e) {
            e.printStackTrace();
        }
    }
    public EmailHandler(String emailAddress, String password, String smtpHost, int smtpPort, String imapHost, int imapPort) {
        this.emailAddress = emailAddress;
        this.password = password;
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.imapHost = imapHost;
        this.imapPort = imapPort;
    }

    public static EmailHandler inputNewEmailHandler(BufferedReader br) throws IOException {
        // Gmail SMTP server details
        String smtpServer = "smtp.gmail.com";
        int smtpPort = 587; // or 465 for SSL

        // Gmail IMAP server details
        String imapServer = "imap.gmail.com";
        int imapPort = 993;

        System.out.println("Enter email address:");
        String emailAddress = br.readLine();

        System.out.println("Enter password:");
        String password = br.readLine();

        System.out.println("Enter SMTP host. Default is "+smtpServer+":");
        String ip = br.readLine();
        if(!"".equals(ip))smtpServer=ip;

        int input = Inputer.inputInteger(br,"Enter SMTP port. Default is "+smtpPort+":",65535);
        if(input!=0)smtpPort = input;

        System.out.println("Enter IMAP host. Default is "+imapServer+":");
        ip = br.readLine();
        if(!"".equals(ip))imapServer = ip;

        input = Inputer.inputInteger(br,"Enter IMAP port. Default is "+imapPort+":",65535);
        if(input!=0)imapPort=input;

        return new EmailHandler(emailAddress, password, smtpServer, smtpPort, imapServer, imapPort);
    }

    public void sendEmail(String toAddress, String subject, String body) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", smtpPort);

        Session session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(emailAddress, password);
            }
        });

        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(emailAddress));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toAddress));
        message.setSubject(subject);
        message.setText(body);

        Transport.send(message);
        System.out.println("Email sent successfully");
    }

    public List<Map<String, String>> receiveEmails(String mailbox, int limit) throws MessagingException, IOException {
        Properties props = new Properties();
        props.setProperty("mail.store.protocol", "imaps");

        Session session = Session.getInstance(props, null);
        Store store = session.getStore("imaps");
        store.connect(imapHost, imapPort, emailAddress, password);

        Folder folder = store.getFolder(mailbox);
        folder.open(Folder.READ_ONLY);

        int messageCount = folder.getMessageCount();
        int startIndex = Math.max(1, messageCount - limit + 1);
        Message[] messages = folder.getMessages(startIndex, messageCount);

        List<Map<String, String>> emails = new ArrayList<>();
        for (Message message : messages) {
            Map<String, String> emailMap = new HashMap<>();
            emailMap.put("subject", message.getSubject());
            emailMap.put("from", Arrays.toString(message.getFrom()));
            emailMap.put("date", message.getSentDate().toString());
            emailMap.put("body", getEmailBody(message));
            emails.add(emailMap);
        }

        folder.close(false);
        store.close();

        return emails;
    }

    private String getEmailBody(Message message) throws MessagingException, IOException {
        if (message.isMimeType("text/plain")) {
            return message.getContent().toString();
        } else if (message.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) message.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                if (bodyPart.isMimeType("text/plain")) {
                    return bodyPart.getContent().toString();
                }
            }
        }
        return "Unable to extract email body";
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSmtpHost() {
        return smtpHost;
    }

    public void setSmtpHost(String smtpHost) {
        this.smtpHost = smtpHost;
    }

    public int getSmtpPort() {
        return smtpPort;
    }

    public void setSmtpPort(int smtpPort) {
        this.smtpPort = smtpPort;
    }

    public String getImapHost() {
        return imapHost;
    }

    public void setImapHost(String imapHost) {
        this.imapHost = imapHost;
    }

    public int getImapPort() {
        return imapPort;
    }

    public void setImapPort(int imapPort) {
        this.imapPort = imapPort;
    }
}
