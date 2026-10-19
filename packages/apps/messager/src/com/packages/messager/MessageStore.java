package com.packages.messager;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;

/** Local drafts/outbox only. No message in this store implies network delivery. */
public final class MessageStore {
    private static final int MAGIC = 0x4d534731, LIMIT = 10000;
    public static final String DRAFT = "draft", OUTBOX = "local";
    private final Path file;
    public static final class Message {
        public final String id, recipient, body, kind;
        public final long time;
        public Message(String id, String recipient, String body, String kind, long time) {
            this.id = id; this.recipient = recipient; this.body = body; this.kind = kind; this.time = time;
        }
    }
    public MessageStore(Path file) { this.file = file.toAbsolutePath().normalize(); }
    public List<Message> load() throws IOException {
        List<Message> result = new ArrayList<>();
        if (Files.notExists(file)) return result;
        if (Files.size(file) > 80L * 1024 * 1024) throw new IOException("Message file is too large");
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (input.readInt() != MAGIC) throw new IOException("Invalid message file");
            int count = input.readInt();
            if (count < 0 || count > LIMIT) throw new IOException("Invalid message count");
            Set<String> ids = new HashSet<>(), drafts = new HashSet<>();
            for (int i = 0; i < count; i++) {
                Message message = new Message(input.readUTF(), input.readUTF(), input.readUTF(), input.readUTF(), input.readLong());
                validate(message);
                if (!ids.add(message.id) || (message.kind.equals(DRAFT) && !drafts.add(message.recipient))) throw new IOException("Duplicate message or draft");
                result.add(message);
            }
            if (input.read() != -1) throw new IOException("Unexpected message data");
        } catch (IllegalArgumentException e) { throw new IOException("Invalid message data: " + e.getMessage(), e); }
        result.sort(Comparator.comparingLong(message -> message.time));
        return result;
    }
    public List<Message> save(String recipient, String body, boolean draft) throws IOException {
        String phone = normalizeRecipient(recipient);
        Message message = new Message(UUID.randomUUID().toString(), phone, body.trim(), draft ? DRAFT : OUTBOX, System.currentTimeMillis());
        validate(message);
        return change(phone, message);
    }
    public List<Message> deleteConversation(String recipient) throws IOException { return change(normalizeRecipient(recipient), null); }
    private List<Message> change(String recipient, Message addition) throws IOException {
        Files.createDirectories(file.getParent());
        try (FileChannel channel = FileChannel.open(file.resolveSibling(file.getFileName() + ".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = channel.lock()) {
            List<Message> messages = load();
            messages.removeIf(message -> message.recipient.equals(recipient) && (addition == null || message.kind.equals(DRAFT)));
            if (addition != null) {
                if (messages.size() >= LIMIT) throw new IOException("Local message limit reached (10000)");
                messages.add(addition);
            }
            Path temporary = Files.createTempFile(file.getParent(), "messages-", ".tmp");
            try {
                try (FileOutputStream stream = new FileOutputStream(temporary.toFile());
                     DataOutputStream output = new DataOutputStream(new BufferedOutputStream(stream))) {
                    output.writeInt(MAGIC); output.writeInt(messages.size());
                    for (Message message : messages) {
                        output.writeUTF(message.id); output.writeUTF(message.recipient); output.writeUTF(message.body); output.writeUTF(message.kind); output.writeLong(message.time);
                    }
                    output.flush(); stream.getFD().sync();
                }
                try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException e) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temporary); }
            return messages;
        }
    }
    public static String normalizeRecipient(String recipient) {
        if (recipient == null || recipient.length() > 40 || !recipient.matches("[+0-9 ().-]+")) throw new IllegalArgumentException("Enter a phone number (3-20 digits)");
        String phone = recipient.replaceAll("[ ().-]", "");
        if (!phone.matches("\\+?[0-9]{3,20}")) throw new IllegalArgumentException("Enter a phone number (3-20 digits)");
        return phone;
    }
    public static void validate(Message message) {
        UUID.fromString(message.id);
        if (!normalizeRecipient(message.recipient).equals(message.recipient)) throw new IllegalArgumentException("Phone number is not normalized");
        if (!DRAFT.equals(message.kind) && !OUTBOX.equals(message.kind)) throw new IllegalArgumentException("Invalid message type");
        if (message.time < 0) throw new IllegalArgumentException("Invalid message timestamp");
        if (message.body.trim().isEmpty() || message.body.codePointCount(0, message.body.length()) > 2000
                || message.body.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n')) throw new IllegalArgumentException("Message must contain 1-2000 characters");
    }
}
