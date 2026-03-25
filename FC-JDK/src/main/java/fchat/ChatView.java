package fchat;

import java.io.BufferedReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Interactive terminal chat view.
 * Displays messages and handles user input in a conversation.
 * Works with all ImTypes (P2P, GROUP, TEAM, ROOM).
 *
 * Commands:
 *   /back       - exit chat
 *   /history N  - load last N messages
 *   /info       - show conversation info
 *   /status     - show online status (P2P only)
 */
public class ChatView implements ImManagerCli.MessageListener {
    private static final int DEFAULT_HISTORY = 30;
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("MM-dd HH:mm:ss");

    private final ImManagerCli imManager;
    private final ImType imType;
    private final String targetId;
    private final String displayName;
    private final BufferedReader br;

    private volatile boolean running = true;

    public ChatView(ImManagerCli imManager, ImType imType, String targetId,
                    String displayName, BufferedReader br) {
        this.imManager = imManager;
        this.imType = imType;
        this.targetId = targetId;
        this.displayName = displayName != null ? displayName : targetId;
        this.br = br;
    }

    /**
     * Enter the interactive chat loop. Blocks until the user types /back.
     */
    public void run() {
        imManager.setListener(this);
        imManager.markAsRead(imType, targetId);

        printHeader();
        loadAndDisplay(DEFAULT_HISTORY);
        printPrompt();

        running = true;
        while (running) {
            try {
                String line = br.readLine();
                if (line == null) { running = false; break; }
                line = line.trim();
                if (line.isEmpty()) { printPrompt(); continue; }

                if (line.startsWith("/")) {
                    handleCommand(line);
                } else {
                    sendText(line);
                }
            } catch (IOException e) {
                System.err.println("Input error: " + e.getMessage());
                running = false;
            }
        }

        imManager.setListener(null);
    }

    private void handleCommand(String cmd) {
        if ("/back".equalsIgnoreCase(cmd)) {
            running = false;
            return;
        }

        if (cmd.startsWith("/history")) {
            int n = DEFAULT_HISTORY;
            String[] parts = cmd.split("\\s+", 2);
            if (parts.length == 2) {
                try { n = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) {}
            }
            loadAndDisplay(n);
            printPrompt();
            return;
        }

        if ("/info".equalsIgnoreCase(cmd)) {
            printInfo();
            printPrompt();
            return;
        }

        if ("/status".equalsIgnoreCase(cmd)) {
            if (imType == ImType.P2P) {
                System.out.println("  [Presence check not available in CLI mode]");
            } else {
                System.out.println("  [Status only available for P2P chats]");
            }
            printPrompt();
            return;
        }

        System.out.println("  Unknown command. Available: /back, /history [N], /info, /status");
        printPrompt();
    }

    private void sendText(String text) {
        ImMessage msg = ImMessage.createText(imType, imManager.getLiveFid(), targetId, text);
        boolean ok = imManager.send(msg);
        if (ok) {
            printOwnMessage(msg);
        } else {
            System.out.println("  [FAILED] Could not send message");
        }
        printPrompt();
    }

    // ========== Display ==========

    private void printHeader() {
        String typeLabel = imType.name();
        System.out.println();
        System.out.println("═══════════════════════════════════════════════");
        System.out.printf("  %s Chat: %s%n", typeLabel, displayName);
        if (!displayName.equals(targetId)) {
            System.out.printf("  ID: %s%n", targetId);
        }
        System.out.println("  Type /back to exit, /history N to load more");
        System.out.println("═══════════════════════════════════════════════");
    }

    private void loadAndDisplay(int limit) {
        List<ImMessage> messages = imManager.getMessages(imType, targetId, limit);
        if (messages.isEmpty()) {
            System.out.println("  [No messages yet]");
            return;
        }
        System.out.println();
        for (ImMessage msg : messages) {
            printMessage(msg);
        }
    }

    private void printMessage(ImMessage msg) {
        String time = formatTime(msg.getTimestamp());
        String sender = shortId(msg.getSenderId());
        boolean isMe = msg.isOutgoing(imManager.getLiveFid());
        String arrow = isMe ? ">>>" : "<<<";
        String statusMark = isMe ? statusSymbol(msg.getStatus()) : "";
        String content = formatContent(msg);

        System.out.printf("  %s %s %s: %s %s%n", time, arrow, sender, content, statusMark);
    }

    private void printOwnMessage(ImMessage msg) {
        String time = formatTime(msg.getTimestamp());
        String sender = shortId(msg.getSenderId());
        String statusMark = statusSymbol(msg.getStatus());
        System.out.printf("  %s >>> %s: %s %s%n", time, sender, msg.getContent(), statusMark);
    }

    private void printPrompt() {
        System.out.print("> ");
        System.out.flush();
    }

    private void printInfo() {
        System.out.println("  ─────────────────────────");
        System.out.printf("  Type: %s%n", imType);
        System.out.printf("  Target: %s%n", targetId);
        System.out.printf("  Display: %s%n", displayName);
        System.out.printf("  My FID: %s%n", imManager.getLiveFid());
        List<ImMessage> msgs = imManager.getMessages(imType, targetId, 0);
        System.out.printf("  Messages: %d%n", msgs.size());
        System.out.println("  ─────────────────────────");
    }

    // ========== MessageListener ==========

    @Override
    public void onMessageReceived(ImMessage message) {
        if (!isForThisChat(message)) return;
        System.out.println();
        printMessage(message);
        imManager.markAsRead(imType, targetId);
        printPrompt();
    }

    @Override
    public void onMessageSent(ImMessage message) {
        // Already printed in sendText
    }

    @Override
    public void onStatusChanged(String messageId, MessageStatus status) {
        // Could update display but terminal doesn't support in-place updates easily
    }

    private boolean isForThisChat(ImMessage msg) {
        if (msg.getType() != imType) return false;
        if (imType == ImType.P2P) {
            String partner = msg.getConversationPartnerId(imManager.getLiveFid());
            return targetId.equals(partner);
        }
        return targetId.equals(msg.getTargetId());
    }

    // ========== Formatting helpers ==========

    private String formatTime(Long timestamp) {
        if (timestamp == null) return "          ";
        return TIME_FMT.format(new Date(timestamp));
    }

    private String shortId(String fid) {
        if (fid == null) return "???";
        if (fid.equals(imManager.getLiveFid())) return "Me";
        if (fid.length() > 10) return fid.substring(0, 4) + ".." + fid.substring(fid.length() - 4);
        return fid;
    }

    private String statusSymbol(MessageStatus status) {
        if (status == null) return "";
        return switch (status) {
            case PENDING -> "⏳";
            case SENT -> "✓";
            case DELIVERED -> "✓✓";
            case READ -> "✓✓✓";
            case FAILED -> "✗";
        };
    }

    private String formatContent(ImMessage msg) {
        if (msg.getContentType() == null) return msg.getContent() != null ? msg.getContent() : "";
        return switch (msg.getContentType()) {
            case TEXT -> msg.getContent() != null ? msg.getContent() : "";
            case HAT -> "[File: " + (msg.getContent() != null ? msg.getContent() : "?") + "]";
            case STREAM -> "[Stream]";
            case SYMKEY -> "[Symkey shared]";
            case MEMBERS -> "[Members list]";
            case HISTORY -> "[History shared]";
            case REQUEST -> "[Request: " + msg.getRequestType() + "]";
            case RESPONSE -> "[Response]";
            case TYPING -> "typing...";
            case RECEIPT -> "[Receipt]";
            case PRESENCE -> "[Presence]";
            case REACTION -> "[Reaction: " + msg.getContent() + "]";
            case EDIT -> "[Edit] " + (msg.getContent() != null ? msg.getContent() : "");
            case DELETE -> "[Deleted]";
            case FORWARD -> "[Fwd] " + (msg.getContent() != null ? msg.getContent() : "");
        };
    }
}
