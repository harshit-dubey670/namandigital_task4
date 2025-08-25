# namandigital_task4
📚 Library Management System 

A Java console-based Library Management System that manages books, users, and borrowing activities.  
The system uses CSV file storage so that data persists between runs — no database required.  

---

✨ Features

📖 Book Catalog
  - Add, update, delete, and list books

👤User Management**
  - Register new users  
  - Deregister users (only if no active loans)  
  - Default admin user created (`admin / admin123`)

🔄 Borrowing & Returns
  - Borrow (issue) books with due date tracking  
  - Return books with late fee calculation
   
💾 Persistence
  - Data stored in CSV files under `data/` folder  
  - Files: `books.csv`, `users.csv`, `loans.csv`  

---

🛠️ Tech Stack
- Language: Java (Core, Collections, Date/Time API)  
- Storage: CSV files (no database required)  
- IDE: IntelliJ IDEA  
