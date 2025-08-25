package NamanDigital;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

    //Simple Library Management System

    public class LibraryManagement {

        // ===== Models =====
        static class Book {
            long id;
            String title;
            String author;
            int totalCopies;
            int availableCopies;

            Book(long id, String title, String author, int totalCopies, int availableCopies) {
                this.id = id;
                this.title = title;
                this.author = author;
                this.totalCopies = totalCopies;
                this.availableCopies = availableCopies;
            }
        }

        static class User {
            String username;
            String passwordHash; // sha-256
            String fullName;

            User(String username, String passwordHash, String fullName) {
                this.username = username;
                this.passwordHash = passwordHash;
                this.fullName = fullName;
            }
        }

        static class Loan {
            long id;
            long bookId;
            String username;
            LocalDate issueDate;
            LocalDate dueDate;
            LocalDate returnDate; // null if not returned

            Loan(long id, long bookId, String username, LocalDate issueDate, LocalDate dueDate, LocalDate returnDate) {
                this.id = id;
                this.bookId = bookId;
                this.username = username;
                this.issueDate = issueDate;
                this.dueDate = dueDate;
                this.returnDate = returnDate;
            }

            boolean isReturned() { return returnDate != null; }
        }

        // ===== Storage (CSV) =====
        static class CsvStore {
            private final Path dir;
            private final Path booksCsv;
            private final Path usersCsv;
            private final Path loansCsv;
            private final DateTimeFormatter DF = DateTimeFormatter.ISO_LOCAL_DATE;

            CsvStore(String base) {
                this.dir = Paths.get(base);
                this.booksCsv = dir.resolve("books.csv");
                this.usersCsv = dir.resolve("users.csv");
                this.loansCsv = dir.resolve("loans.csv");
            }

            void init() {
                try {
                    if (!Files.exists(dir)) Files.createDirectories(dir);
                    if (!Files.exists(booksCsv)) Files.write(booksCsv, Collections.singletonList("id,title,author,total,available"), StandardCharsets.UTF_8);
                    if (!Files.exists(usersCsv)) Files.write(usersCsv, Collections.singletonList("username,passwordHash,fullName"), StandardCharsets.UTF_8);
                    if (!Files.exists(loansCsv)) Files.write(loansCsv, Collections.singletonList("id,bookId,username,issueDate,dueDate,returnDate"), StandardCharsets.UTF_8);
                    // ensure default admin exists
                    List<User> us = loadUsers();
                    boolean hasAdmin = us.stream().anyMatch(u -> u.username.equals("admin"));
                    if (!hasAdmin) {
                        String hash = Security.sha256Hex("admin123");
                        try (BufferedWriter bw = Files.newBufferedWriter(usersCsv, StandardCharsets.UTF_8, StandardOpenOption.APPEND)) {
                            bw.write("admin," + hash + ",Administrator\n");
                        }
                    }
                } catch (IOException e) { throw new RuntimeException(e); }
            }

            // Books
            List<Book> loadBooks() {
                List<Book> list = new ArrayList<>();
                try (BufferedReader br = Files.newBufferedReader(booksCsv, StandardCharsets.UTF_8)) {
                    String line; boolean first = true;
                    while ((line = br.readLine()) != null) {
                        if (first) { first = false; continue; }
                        if (line.trim().isEmpty()) continue;
                        String[] p = splitCsv(line);
                        long id = Long.parseLong(p[0]);
                        String title = p[1];
                        String author = p[2];
                        int total = Integer.parseInt(p[3]);
                        int avail = Integer.parseInt(p[4]);
                        list.add(new Book(id, title, author, total, avail));
                    }
                } catch (IOException e) { throw new RuntimeException(e); }
                return list;
            }

            void saveBooks(List<Book> books) {
                List<String> lines = new ArrayList<>();
                lines.add("id,title,author,total,available");
                for (Book b : books) lines.add(String.join(",", String.valueOf(b.id), esc(b.title), esc(b.author), String.valueOf(b.totalCopies), String.valueOf(b.availableCopies)));
                try { Files.write(booksCsv, lines, StandardCharsets.UTF_8); } catch (IOException e) { throw new RuntimeException(e); }
            }

            // Users
            List<User> loadUsers() {
                List<User> list = new ArrayList<>();
                try (BufferedReader br = Files.newBufferedReader(usersCsv, StandardCharsets.UTF_8)) {
                    String line; boolean first = true;
                    while ((line = br.readLine()) != null) {
                        if (first) { first = false; continue; }
                        if (line.trim().isEmpty()) continue;
                        String[] p = splitCsv(line);
                        list.add(new User(p[0], p[1], p.length>2?p[2]:""));
                    }
                } catch (IOException e) { throw new RuntimeException(e); }
                return list;
            }

            void saveUsers(List<User> users) {
                List<String> lines = new ArrayList<>();
                lines.add("username,passwordHash,fullName");
                for (User u : users) lines.add(u.username + "," + u.passwordHash + "," + esc(u.fullName));
                try { Files.write(usersCsv, lines, StandardCharsets.UTF_8); } catch (IOException e) { throw new RuntimeException(e); }
            }

            // Loans
            List<Loan> loadLoans() {
                List<Loan> list = new ArrayList<>();
                try (BufferedReader br = Files.newBufferedReader(loansCsv, StandardCharsets.UTF_8)) {
                    String line; boolean first = true;
                    while ((line = br.readLine()) != null) {
                        if (first) { first = false; continue; }
                        if (line.trim().isEmpty()) continue;
                        String[] p = splitCsv(line);
                        long id = Long.parseLong(p[0]);
                        long bookId = Long.parseLong(p[1]);
                        String username = p[2];
                        LocalDate issue = LocalDate.parse(p[3], DF);
                        LocalDate due = LocalDate.parse(p[4], DF);
                        LocalDate ret = p.length>5 && !p[5].isEmpty() ? LocalDate.parse(p[5], DF) : null;
                        list.add(new Loan(id, bookId, username, issue, due, ret));
                    }
                } catch (IOException e) { throw new RuntimeException(e); }
                return list;
            }

            void saveLoans(List<Loan> loans) {
                List<String> lines = new ArrayList<>();
                lines.add("id,bookId,username,issueDate,dueDate,returnDate");
                for (Loan l : loans) lines.add(String.join(",", String.valueOf(l.id), String.valueOf(l.bookId), l.username, l.issueDate.format(DF), l.dueDate.format(DF), l.returnDate==null?"":l.returnDate.format(DF)));
                try { Files.write(loansCsv, lines, StandardCharsets.UTF_8); } catch (IOException e) { throw new RuntimeException(e); }
            }

            // CSV helpers
            private static String esc(String s) {
                if (s == null) return "";
                boolean need = s.contains(",") || s.contains("\"") || s.contains("\n");
                String t = s.replace("\"", "\"\"");
                return need ? "\"" + t + "\"" : t;
            }

            private static String[] splitCsv(String line) {
                List<String> parts = new ArrayList<>();
                StringBuilder cur = new StringBuilder();
                boolean inQ = false;
                for (int i=0;i<line.length();i++) {
                    char c = line.charAt(i);
                    if (inQ) {
                        if (c=='"') {
                            if (i+1<line.length() && line.charAt(i+1)=='"') { cur.append('"'); i++; }
                            else inQ = false;
                        } else cur.append(c);
                    } else {
                        if (c=='"') inQ = true;
                        else if (c==',') { parts.add(cur.toString()); cur.setLength(0); }
                        else cur.append(c);
                    }
                }
                parts.add(cur.toString());
                return parts.toArray(new String[0]);
            }
        }

        // ===== Utilities =====
        static class Security {
            static String sha256Hex(String in) {
                try {
                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    byte[] h = md.digest(in.getBytes(StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    for (byte b : h) sb.append(String.format("%02x", b));
                    return sb.toString();
                } catch (Exception e) { throw new RuntimeException(e); }
            }
        }

        // ===== Services =====
        static class BookService {
            private final CsvStore store;
            private List<Book> books;

            BookService(CsvStore store) { this.store = store; this.books = store.loadBooks(); }

            List<Book> all() { return new ArrayList<>(books); }

            Book find(long id) { return books.stream().filter(b->b.id==id).findFirst().orElse(null); }

            Book create(String title, String author, int copies) {
                long id = nextId();
                Book b = new Book(id, title, author, copies, copies);
                books.add(b);
                store.saveBooks(books);
                return b;
            }

            boolean update(long id, String title, String author, Integer totalCopies) {
                Book b = find(id); if (b==null) return false;
                if (title!=null) b.title = title;
                if (author!=null) b.author = author;
                if (totalCopies!=null) {
                    int diff = totalCopies - b.totalCopies;
                    b.totalCopies = totalCopies;
                    b.availableCopies += diff; // adjust availability accordingly
                    if (b.availableCopies < 0) b.availableCopies = 0;
                }
                store.saveBooks(books);
                return true;
            }

            boolean delete(long id) {
                Book b = find(id); if (b==null) return false;
                if (b.availableCopies != b.totalCopies) return false; // some copies lent out
                boolean removed = books.removeIf(x->x.id==id);
                if (removed) store.saveBooks(books);
                return removed;
            }

            void changeAvailable(long id, int delta) {
                Book b = find(id); if (b==null) throw new IllegalArgumentException("Book not found");
                int avail = b.availableCopies + delta;
                if (avail < 0 || avail > b.totalCopies) throw new IllegalArgumentException("Invalid availability change");
                b.availableCopies = avail;
                store.saveBooks(books);
            }

            private long nextId() { return books.stream().map(x->x.id).max(Long::compare).orElse(1000L) + 1; }
        }

        static class UserService {
            private final CsvStore store;
            private List<User> users;

            UserService(CsvStore store) { this.store = store; this.users = store.loadUsers(); }

            boolean authenticate(String username, String password) {
                String h = Security.sha256Hex(password);
                return users.stream().anyMatch(u->u.username.equals(username) && u.passwordHash.equals(h));
            }

            boolean addUser(String username, String password, String fullName) {
                if (users.stream().anyMatch(u->u.username.equals(username))) return false;
                users.add(new User(username, Security.sha256Hex(password), fullName==null?"":fullName));
                store.saveUsers(users);
                return true;
            }

            boolean deleteUser(String username, List<Loan> loans) {
                boolean hasActive = loans.stream().anyMatch(l->l.username.equals(username) && !l.isReturned());
                if (hasActive) return false;
                boolean removed = users.removeIf(u->u.username.equals(username));
                if (removed) store.saveUsers(users);
                return removed;
            }

            List<User> all() { return new ArrayList<>(users); }
        }

        static class LoanService {
            private final CsvStore store;
            private final BookService books;
            private List<Loan> loans;
            private final int loanDays = 14; // default loan period
            private final double finePerDay = 5.0; // currency units per day late

            LoanService(CsvStore store, BookService books) { this.store = store; this.books = books; this.loans = store.loadLoans(); }

            List<Loan> all() { return new ArrayList<>(loans); }

            List<Loan> activeForUser(String username) {
                List<Loan> out = new ArrayList<>();
                for (Loan l : loans) if (l.username.equals(username) && !l.isReturned()) out.add(l);
                return out;
            }

            Loan issue(long bookId, String username) {
                Book b = books.find(bookId); if (b==null) throw new IllegalArgumentException("Book not found");
                if (b.availableCopies <= 0) throw new IllegalArgumentException("No copies available");
                LocalDate issue = LocalDate.now();
                LocalDate due = issue.plusDays(loanDays);
                long id = nextId();
                Loan l = new Loan(id, bookId, username, issue, due, null);
                loans.add(l);
                books.changeAvailable(bookId, -1);
                store.saveLoans(loans);
                return l;
            }

            double returnBook(long loanId) {
                Loan l = loans.stream().filter(x->x.id==loanId).findFirst().orElse(null);
                if (l==null) throw new IllegalArgumentException("Loan not found");
                if (l.isReturned()) throw new IllegalArgumentException("Already returned");
                LocalDate ret = LocalDate.now();
                l.returnDate = ret;
                books.changeAvailable(l.bookId, +1);
                store.saveLoans(loans);
                long overdue = ChronoUnit.DAYS.between(l.dueDate, ret);
                if (overdue > 0) return overdue * finePerDay;
                return 0.0;
            }

            private long nextId() { return loans.stream().map(x->x.id).max(Long::compare).orElse(5000L) + 1; }
        }

        // ===== Console UI =====
        static class ConsoleApp {
            private final Scanner sc = new Scanner(System.in);
            private final UserService users;
            private final BookService books;
            private final LoanService loans;
            private String currentUser;

            ConsoleApp(UserService users, BookService books, LoanService loans) {
                this.users = users; this.books = books; this.loans = loans;
            }

            void run() {
                System.out.println("Simple Library Management System");
                while (true) {
                    if (currentUser == null) {
                        System.out.println("\n1) Login\n2) Register\n0) Exit");
                        System.out.print("Choose: ");
                        String c = sc.nextLine().trim();
                        switch (c) {
                            case "1": login(); break;
                            case "2": register(); break;
                            case "0": System.out.println("Bye"); return;
                            default: System.out.println("Invalid");
                        }
                    } else {
                        showMainMenu();
                    }
                }
            }

            private void login() {
                System.out.print("Username: "); String u = sc.nextLine().trim();
                System.out.print("Password: "); String p = sc.nextLine().trim();
                if (users.authenticate(u, p)) { currentUser = u; System.out.println("Welcome, " + u); }
                else System.out.println("Auth failed");
            }

            private void register() {
                System.out.print("Choose username: "); String u = sc.nextLine().trim();
                System.out.print("Full name: "); String name = sc.nextLine().trim();
                System.out.print("Password: "); String p = sc.nextLine().trim();
                boolean ok = users.addUser(u, p, name);
                System.out.println(ok?"Registered. You may login.":"Username exists.");
            }

            private void logout() { currentUser = null; System.out.println("Logged out."); }

            private void showMainMenu() {
                System.out.println("\n-- Main Menu (logged in as: " + currentUser + ") --");
                System.out.println("1) Browse Books\n2) My Loans\n3) Borrow Book\n4) Return Book\n5) Account (deregister)\n6) Admin Panel (admin only)\n7) Logout\n0) Exit");
                System.out.print("Choose: ");
                String c = sc.nextLine().trim();
                switch (c) {
                    case "1": listBooks(); break;
                    case "2": myLoans(); break;
                    case "3": borrow(); break;
                    case "4": returnBook(); break;
                    case "5": deregister(); break;
                    case "6": adminPanel(); break;
                    case "7": logout(); break;
                    case "0": System.out.println("Bye"); System.exit(0); break;
                    default: System.out.println("Invalid");
                }
            }

            private void listBooks() {
                System.out.println("ID | Title | Author | Available/Total");
                for (Book b : books.all()) {
                    System.out.printf("%d | %s | %s | %d/%d%n", b.id, b.title, b.author, b.availableCopies, b.totalCopies);
                }
            }

            private void myLoans() {
                List<Loan> my = loans.activeForUser(currentUser);
                if (my.isEmpty()) { System.out.println("No active loans."); return; }
                System.out.println("LoanID | BookID | Title | Issue | Due");
                for (Loan l : my) {
                    Book b = books.find(l.bookId);
                    System.out.printf("%d | %d | %s | %s | %s%n", l.id, l.bookId, b==null?"#"+l.bookId:b.title, l.issueDate, l.dueDate);
                }
            }

            private void borrow() {
                try {
                    System.out.print("Enter Book ID to borrow: "); long id = Long.parseLong(sc.nextLine().trim());
                    Loan l = loans.issue(id, currentUser);
                    System.out.println("Issued. Loan ID: " + l.id + ", due: " + l.dueDate);
                } catch (Exception e) { System.out.println("Error: " + e.getMessage()); }
            }

            private void returnBook() {
                try {
                    System.out.print("Enter Loan ID to return: "); long id = Long.parseLong(sc.nextLine().trim());
                    double fine = loans.returnBook(id);
                    if (fine > 0) System.out.println("Returned. Late fee: " + String.format("%.2f", fine));
                    else System.out.println("Returned on time. No fee.");
                } catch (Exception e) { System.out.println("Error: " + e.getMessage()); }
            }

            private void deregister() {
                System.out.print("Confirm deregister account (type DELETE): "); String c = sc.nextLine().trim();
                if (!c.equals("DELETE")) { System.out.println("Cancelled."); return; }
                boolean ok = users.deleteUser(currentUser, loans.all());
                if (ok) { System.out.println("Account removed."); currentUser = null; }
                else System.out.println("Cannot deregister: active loans exist or error.");
            }

            private void adminPanel() {
                if (!currentUser.equals("admin")) { System.out.println("Admin access only."); return; }
                while (true) {
                    System.out.println("\n-- Admin Panel --\n1) Add Book\n2) Update Book\n3) Delete Book\n4) List Users\n5) List Loans\n0) Back");
                    System.out.print("Choose: "); String c = sc.nextLine().trim();
                    switch (c) {
                        case "1": addBook(); break;
                        case "2": updateBook(); break;
                        case "3": deleteBook(); break;
                        case "4": listUsers(); break;
                        case "5": listAllLoans(); break;
                        case "0": return;
                        default: System.out.println("Invalid");
                    }
                }
            }

            private void addBook() {
                try {
                    System.out.print("Title: "); String t = sc.nextLine().trim();
                    System.out.print("Author: "); String a = sc.nextLine().trim();
                    System.out.print("Copies: "); int c = Integer.parseInt(sc.nextLine().trim());
                    Book b = books.create(t, a, c);
                    System.out.println("Added book ID: " + b.id);
                } catch (Exception e) { System.out.println("Error: " + e.getMessage()); }
            }

            private void updateBook() {
                try {
                    System.out.print("Book ID: "); long id = Long.parseLong(sc.nextLine().trim());
                    Book b = books.find(id); if (b==null) { System.out.println("Not found"); return; }
                    System.out.print("New title (blank keep): "); String t = sc.nextLine();
                    System.out.print("New author (blank keep): "); String a = sc.nextLine();
                    System.out.print("New total copies (blank keep): "); String tc = sc.nextLine();
                    Integer tot = tc.isBlank()?null:Integer.parseInt(tc);
                    boolean ok = books.update(id, t.isBlank()?null:t, a.isBlank()?null:a, tot);
                    System.out.println(ok?"Updated":"Failed");
                } catch (Exception e) { System.out.println("Error: " + e.getMessage()); }
            }

            private void deleteBook() {
                try {
                    System.out.print("Book ID to delete: "); long id = Long.parseLong(sc.nextLine().trim());
                    boolean ok = books.delete(id);
                    System.out.println(ok?"Deleted":"Cannot delete (may have active loans or not exist)");
                } catch (Exception e) { System.out.println("Error: " + e.getMessage()); }
            }

            private void listUsers() {
                System.out.println("Username | Full Name");
                for (User u : users.all()) System.out.println(u.username + " | " + u.fullName);
            }

            private void listAllLoans() {
                System.out.println("LoanID | BookID | User | Issue | Due | Returned");
                for (Loan l : loans.all()) System.out.printf("%d | %d | %s | %s | %s | %s%n", l.id, l.bookId, l.username, l.issueDate, l.dueDate, l.returnDate==null?"-":l.returnDate);
            }
        }

        // ===== Main =====
        public static void main(String[] args) {
            CsvStore store = new CsvStore("data");
            store.init();

            BookService bookService = new BookService(store);
            UserService userService = new UserService(store);
            LoanService loanService = new LoanService(store, bookService);

            ConsoleApp app = new ConsoleApp(userService, bookService, loanService);
            app.run();
        }
    }

