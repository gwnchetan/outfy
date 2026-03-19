# Outfyt — Premium Apparel Android App

An e-commerce Android application for premium apparel, built with Kotlin following clean MVVM architecture. Features Firebase authentication (Phone OTP + Google Sign-In), Firestore database, and a React-based admin panel.

---

## Features

### Authentication
- Phone number login with OTP verification
- Google Sign-In
- OTP rate limiting — max 3 attempts per 24 hours
- Unique email and phone enforcement across all accounts
- Age validation on registration (13+ only)

### User Onboarding
- New user registration form (name, email, gender, date of birth)
- Returning users skip registration and go straight to home
- Secure back stack management — no back navigation to OTP/login screens

### App
- Home screen with product listings
- MVVM architecture throughout
- Firebase Firestore as backend database

### Admin Panel _(in progress)_
- Separate React web app
- Dashboard with stats
- User, product, and order management
- Secured by Google Sign-In (single admin email)

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| Architecture | MVVM |
| Authentication | Firebase Auth (Phone + Google) |
| Database | Firebase Firestore |
| UI | XML layouts, Material Design 3 |
| Admin Panel | React + Firebase SDK |

---

## Project Structure

```
com.example.outfy
├── data
│   └── AuthRepository.kt       # All Firebase calls
├── model
│   ├── Users.kt                # User data model
│   └── Product.kt              # Product data model
├── viewmodel
│   └── AuthViewModel.kt        # Auth logic and state
├── adapter
│   └── ProductAdapter.kt       # RecyclerView adapter
├── LoginActivity.kt
├── OtpActivity.kt
├── RegisterActivity.kt
├── HomeActivity.kt
└── SplashActivity.kt
```

---

## Firestore Schema

### `users` collection
```json
{
  "uid": "string",
  "name": "string",
  "gender": "male | female | other",
  "phone": "string | null",
  "email": "string | null",
  "dob": "string | null",
  "profilePhoto": "string | null",
  "authProvider": "phone | google",
  "status": "active | banned",
  "createdAt": "timestamp"
}
```

### `uniqueIndex` collection
```json
"phone_+91xxxxxxxxxx": { "uid": "..." },
"email_x@gmail.com":   { "uid": "..." }
```

### `products` collection _(coming soon)_
```json
{
  "productId": "string",
  "name": "string",
  "description": "string",
  "price": "number",
  "category": "string",
  "imageUrl": "string",
  "stock": "number",
  "status": "active | draft | out_of_stock",
  "createdAt": "timestamp"
}
```

### `orders` collection _(coming soon)_
```json
{
  "orderId": "string",
  "userId": "string",
  "productId": "string",
  "productName": "string",
  "amount": "number",
  "status": "placed | processing | shipped | delivered | cancelled",
  "address": "string",
  "createdAt": "timestamp"
}
```

---

## Setup

### Prerequisites
- Android Studio Hedgehog or later
- JDK 17+
- Firebase project with Firestore and Authentication enabled

### Steps

1. Clone the repo
```bash
git clone https://github.com/gwnchetan/outfy.git
```

2. Open in Android Studio

3. Add your `google-services.json` to the `/app` directory (get this from Firebase Console → Project Settings)

4. Enable these in Firebase Console:
   - Authentication → Phone and Google sign-in methods
   - Firestore Database → Create in production mode

5. Set Firestore rules:
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{uid} {
      allow read, write: if request.auth != null && request.auth.uid == uid;
    }
    match /uniqueIndex/{doc} {
      allow read: if request.auth != null;
      allow write: if request.auth != null;
    }
  }
}
```

6. Build and run

---

## Architecture

This app strictly follows MVVM — every layer has one job:

```
Activity (UI)
    ↓  user input
ViewModel (logic)
    ↓  data calls
Repository (Firebase)
    ↓
Firebase / Firestore
```

No Activity ever calls Firebase directly.

---

## Known Issues / In Progress

- [ ] Profile photo upload
- [ ] Products screen (dynamic)
- [ ] Orders flow
- [ ] Admin panel completion
- [ ] Push notifications

---

## Author

**Chetan Sakre**  
BCA Student | Android Developer  
[GitHub](https://github.com/gwnchetan)

---

## License

This project is for educational and portfolio purposes.
