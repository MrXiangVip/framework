package com.packages.contact;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.util.*;

/** Atomic local storage. Each writer merges against the latest file under an OS lock. */
public final class ContactStore {
    private static final int MAGIC = 0x43544b31, LIMIT = 10000;
    private final Path file;
    public static final class Contact {
        public final String id, name, phone, email;
        public Contact(String id, String name, String phone, String email) {
            this.id = id; this.name = name.trim(); this.phone = phone.trim(); this.email = email.trim();
        }
    }
    public ContactStore(File file) { this.file = file.toPath().toAbsolutePath(); }
    public List<Contact> load() throws IOException {
        List<Contact> contacts = new ArrayList<>();
        if (!Files.exists(file)) return contacts;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != MAGIC) throw new IOException("Invalid contacts file");
            int count = in.readInt();
            if (count < 0 || count > LIMIT) throw new IOException("Invalid contact count");
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < count; i++) {
                Contact contact = new Contact(in.readUTF(), in.readUTF(), in.readUTF(), in.readUTF());
                validate(contact);
                if (!ids.add(contact.id)) throw new IOException("Duplicate contact ID");
                contacts.add(contact);
            }
            if (in.read() != -1) throw new IOException("Unexpected contacts data");
        } catch (IllegalArgumentException e) { throw new IOException("Invalid contacts data: " + e.getMessage(), e); }
        contacts.sort(Comparator.comparing((Contact c) -> c.name.toLowerCase(Locale.ROOT)).thenComparing(c -> c.id));
        return contacts;
    }
    public void save(Contact contact, boolean creating) throws IOException {
        validate(contact);
        change(contact, creating, false);
    }
    public void delete(String id) throws IOException {
        change(new Contact(id, "", "", ""), false, true);
    }
    private void change(Contact contact, boolean creating, boolean deleting) throws IOException {
        Files.createDirectories(file.getParent());
        Path lockPath = file.resolveSibling(file.getFileName() + ".lock");
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = channel.lock()) {
            List<Contact> contacts = load();
            int index = -1;
            for (int i = 0; i < contacts.size(); i++) if (contacts.get(i).id.equals(contact.id)) { index = i; break; }
            if (creating && index >= 0) throw new IOException("Contact already exists");
            if (!creating && index < 0) throw new IOException("Contact was removed; reopen the list");
            if (deleting) contacts.remove(index);
            else if (creating) {
                if (contacts.size() >= LIMIT) throw new IOException("Contact limit reached");
                contacts.add(contact);
            } else contacts.set(index, contact);
            Path temporary = Files.createTempFile(file.getParent(), "contacts-", ".tmp");
            try {
                try (FileOutputStream stream = new FileOutputStream(temporary.toFile());
                     DataOutputStream out = new DataOutputStream(new BufferedOutputStream(stream))) {
                    out.writeInt(MAGIC); out.writeInt(contacts.size());
                    for (Contact value : contacts) {
                        out.writeUTF(value.id); out.writeUTF(value.name); out.writeUTF(value.phone); out.writeUTF(value.email);
                    }
                    out.flush(); stream.getFD().sync();
                }
                try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException e) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temporary); }
        }
    }
    public static void validate(Contact contact) {
        UUID.fromString(contact.id);
        if (contact.name.isEmpty() || contact.name.length() > 80) throw new IllegalArgumentException("Name is required (max 80 characters)");
        if (contact.phone.length() > 40 || !contact.phone.matches("[0-9+() #*.-]*"))
            throw new IllegalArgumentException("Phone: use digits, +, spaces or ()-#*");
        if (contact.email.length() > 120 || (!contact.email.isEmpty()
                && !contact.email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")))
            throw new IllegalArgumentException("Enter a valid email address");
        if (contact.phone.isEmpty() && contact.email.isEmpty()) throw new IllegalArgumentException("Phone or email is required");
        for (String value : new String[]{contact.name, contact.phone, contact.email})
            if (value.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Control characters are not allowed");
    }
}
