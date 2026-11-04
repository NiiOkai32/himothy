# Deploy CampusFit to campus-fit-5e050

Android configuration and Google OAuth certificate matching are verified locally.
The user reports creating the default Firestore database. The local Tracking, Social
and Notifications integration suites now pass against the Firebase emulators, along
with all 57 backend unit/guard tests. This does not establish production access or
actual Google/phone/push behavior.

No production deployment has been made. This session has no configured Google outbound
identity and Firebase CLI `login:list` returns no authorized accounts. The Android JSON
file grants no deployment privileges. Do not share account passwords, OAuth tokens or
service-account private keys in chat.

## Publish the rules from Firebase Console

1. In project **Campus fit** (`campus-fit-5e050`), open **Databases and storage →
   Firestore Database → Rules**.
2. Replace the editor contents with the complete repository `firestore.rules` file.
3. Click **Publish**. This changes Firestore security rules; it does not publish the
   cloud development environment. The rules permit a verified user to read/write only
   their private profile, keep health/score writes server-only and protect social and
   notification access. No database records are deleted by publishing these rules.
4. Continue with backend deployment below. Publishing rules alone enables private profile
   access, but health uploads, account deletion and social features require the functions.

## Deploy from a terminal signed in to your project

Cloud Functions, scheduled jobs and the configured TTL policies require an appropriate
billing plan (Blaze). Enabling billing is an operator decision; it was not changed here.
Use your own trusted terminal or Google Cloud Shell with Node 22 and authorized access
to this specific Firebase project. The prepared ZIP includes only deployment source,
locked dependencies, tests and security configuration. It excludes Android API config,
private keys, local databases and installed node_modules.

1. Upload/extract `campusfit-firebase-deploy.zip` and enter its folder:

   ```sh
   unzip campusfit-firebase-deploy.zip
   cd campusfit-firebase-deploy
   npm ci --prefix functions --ignore-scripts --no-audit --no-fund
   npm test --prefix functions
   ```

2. Authenticate the Firebase CLI in that terminal if needed:

   ```sh
   ./functions/node_modules/.bin/firebase login --no-localhost
   ./functions/node_modules/.bin/firebase projects:list
   ```

   Complete Google's browser sign-in yourself. Confirm that `campus-fit-5e050` is listed.
   Do not copy authentication codes/tokens into chat. If Cloud Shell's existing Google
   credentials already work for the CLI, an additional login is unnecessary.

3. After reviewing the supplied source/configuration and billing requirements, deploy:

   ```sh
   ./functions/node_modules/.bin/firebase deploy --project campus-fit-5e050 --only firestore:rules,firestore:indexes,functions
   ```

   Review any prompts about enabling Google APIs, IAM roles, artifact retention or
   existing deployed resources. Do not use `--force` or accept deletion of unrelated
   functions/indexes. The command explicitly targets this project and excludes Storage.

4. Confirm the Firestore indexes become ready and the `expiresAt` TTL policies for
   `notifications`, `devices` and `notificationDeviceOwners` are enabled. Confirm all
   functions in `functions/index.js` exist in `us-central1`, including `socialAction`,
   `syncTracking`, `deleteCampusFitAccount`, `notificationAction`, game/event triggers
   and the hourly finalizer. See README and ENGAGEMENT.md for retention and app behavior.
5. Install the current debug APK on a test Android device with Google Play services.
   Test verified email and Google sign-in, private profile sync, health sync, social
   access and the deletion recovery flow using test accounts. Account deletion is
   permanent. Test notification opt-in and delivery explicitly before using push.

Local emulator checks use `demo-campusfit` and fake notification transport. They do not
send live push, change billing or deploy to `campus-fit-5e050`. The earlier blocked
emulator download is resolved: the published environment's enforced policy now allows
`storage.googleapis.com`, and the official Firestore emulator downloaded and ran.
