package clients.mailClient;

import clients.Client;
import clients.apipClient.ApipClient;
import fcData.MailDetail;
import feip.feipData.Mail;
import javaTools.JsonTools;
import javaTools.PersistentSortedMap;
import java.util.*;

public class MailClient {
    private final ApipClient apipClient;
    private final String sid;
    private final byte[] symKey;
    private final String myPriKeyCipher;
    private PersistentSortedMap mailFileMap;
    private TreeMap<String, MailDetail> mailDetailMap = new TreeMap<>();
    private Map<String, Long> lastTimeMap;

    public MailClient(ApipClient apipClient, String sid, byte[] symKey, String myPriKeyCipher, Map<String, Long> lastTimeMap) {
        this.apipClient = apipClient;
        this.sid = sid;
        this.symKey = symKey;
        this.myPriKeyCipher = myPriKeyCipher;
        this.lastTimeMap = lastTimeMap;
        this.mailFileMap = new PersistentSortedMap(sid, constants.Strings.MAIL);
    }

    public void checkMail() {
        List<Mail> mailList = apipClient.freshMailSinceTime(lastTimeMap.get(constants.Strings.MAIL));
        if (mailList != null && !mailList.isEmpty()) {
            mailToMailDetail(mailList);
        }
        System.out.println("You have " + mailDetailMap.size() + " unread mails.");
        MailDetail.displayMailList(new ArrayList<>(mailDetailMap.values()));
    }

    private void mailToMailDetail(List<Mail> mailList) {
        byte[] priKey = Client.decryptPriKey(myPriKeyCipher, symKey);
        Set<String> fidSet = new HashSet<>();

        for (Mail mail : mailList) {
            fidSet.add(mail.getSender());
            fidSet.add(mail.getRecipient());
        }

        Map<String, String> fidCidMap = apipClient.getFidCidMap(new ArrayList<>(fidSet));

        for (Mail mail : mailList) {
            MailDetail mailDetail = MailDetail.fromMail(mail, priKey);
            String senderCid = fidCidMap.get(mail.getSender());
            String recipientCid = fidCidMap.get(mail.getRecipient());
            if(senderCid != null) mailDetail.setFromCid(senderCid);
            if(recipientCid != null) mailDetail.setToCid(recipientCid);
            mailDetailMap.put(mailDetail.getId(), mailDetail);
            mailFileMap.put(mailDetail.getIdBytes(), mailDetail.toBytes()); 
        }

        lastTimeMap.put(constants.Strings.MAIL, mailDetailMap.lastEntry().getValue().getTime());
    }

    public void saveMails() {
        JsonTools.saveToJsonFile(mailDetailMap, sid, constants.Strings.MAIL, false);
    }

    // Add other mail-related methods as needed
}