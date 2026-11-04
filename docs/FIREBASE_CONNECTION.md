# Firebase connection · campus-fit-5e050

The supplied Android configuration matches project `campus-fit-5e050` and package
`com.campusfit`. It is installed at `app/google-services.json` and excluded from Git.
The file configures the Android SDK; it does not enable authentication providers,
create the Firestore database, deploy security rules/functions or grant deployment access.
No live signup, email, Google sign-in, SMS or push was sent while adding the file.

The updated file now includes both a web OAuth client and an Android OAuth client
matching this debug build's SHA-1 fingerprint. The Google Services plugin generates the
web-client resource used by the Google sign-in button. No manual client ID or source-code
replacement is needed. This checks Android configuration, not a live Google login.

Current debug signing fingerprints:

SHA-1:
```text
91:7F:86:72:51:01:43:88:F3:CD:D0:C9:D5:FA:82:7D:FB:D2:05:C0
```

SHA-256:
```text
5D:CC:73:DE:9C:55:76:B3:D8:98:6C:DB:C2:28:B8:39:C6:D5:8A:56:2C:6B:0F:0F:86:F0:CA:F1:C6:3A:D1:37
```

These are for the current debug key. A release/distribution signing key needs its own
registered fingerprints and corresponding Android OAuth configuration.

Remaining console and device checks:

1. In **Security → Authentication → Sign-in method**, verify Email/Password and Google
   are enabled. The user completed the Google provider setup while supplying this file.
2. Open **Databases and storage → Firestore Database → Create database**. Choose
   Standard edition if prompted, the `(default)` database, and production-mode rules.
   `us-central1` matches the existing backend region. Deploy the provided owner-only
   rules and indexes before expecting private profile sync; production-mode defaults
   deny app access until those rules are deployed.
3. Follow FIREBASE_DEPLOYMENT.md and the README operator steps to deploy all provided functions, rules, indexes
   and TTL policies. Cloud Functions require Blaze billing and authorized project
   deployment access. No deployment or billing change was made while adding the file.
4. Install the updated APK on a test Android device with Google Play services. Check
   email signup/verification, Google login, private profile loading and account deletion
   before treating the live integration as verified. Use test accounts; deleting an
   account is a permanent operation. The file itself cannot verify any of these flows.

Obtain fingerprints again with `./gradlew :app:signingReport` if the debug key changes.
The Android configuration's API key is not an administrative credential. Do not put
service-account private keys in the app or share administrative credentials in chat.

Local Firebase integration follow-up: all three Tracking, Social and Notifications suites
passed against emulators. The user reports creating Firestore. Production deployment
remains pending authorized access; see FIREBASE_DEPLOYMENT.md.
