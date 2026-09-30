# Privacy and data-safety declarations

Prepared from an inventory of the code, dependencies, permissions, storage and
network behaviour that Poravia 1.0.0 actually has. Dated 30 September 2026.

**These are prepared answers, not submitted ones.** Store declarations are
legal statements by the account holder. Nobody's declaration has been answered
on their behalf, and no agreement has been accepted. Verify each line against
the shipping build before submitting.

## Inventory the answers are derived from

| Question | Finding |
|---|---|
| Analytics SDKs | none |
| Advertising SDKs or identifiers | none |
| Crash-reporting SDKs | none |
| Social or login SDKs | none |
| Accounts | none; the app has no sign-in |
| Server-side user records | none; there is no Poravia user store |
| Outbound requests | release manifest and data packs from the Poravia origin, map tiles from the configured tile provider, and the operator's own site opened for booking |
| Identifiers attached to requests | none added by Poravia |
| Device permissions requested | location (optional), notifications (optional), file import (optional) |
| Permissions never requested | contacts, calendar, camera, microphone, broad storage, background location |
| Data stored on device | preferences, recent searches, favourites, saved trips, offline packs, imported ticket files, reminder schedules |
| Data leaving the device | none of the above |
| Encryption in transit | yes, HTTPS for every request |
| Encryption at rest | imported ticket files use platform file protection; Android excludes them from backup |
| Deletion | every stored category is individually deletable in the app |

## Apple, App Privacy

**Data collected: none.** Poravia qualifies for "Data Not Collected", because
nothing is transmitted off the device and linked to the user or the device.

Notes to keep with the submission:

- Location is used **on device only**, to centre a map and sort nearby stops.
  It is never transmitted to Poravia. If the reviewer asks, the answer is
  device-only use, not collection.
- Map tile requests necessarily reveal a viewport to the tile provider. That is
  the provider's processing, disclosed in the privacy policy, not Poravia
  collection.
- Imported ticket documents are user content that never leaves the device.

**Encryption export compliance:** the app uses only standard HTTPS and platform
cryptography, which is the usual exemption. **The account holder must confirm
this on the submission form. It has not been answered here.**

**Privacy manifest:** `PrivacyInfo.xcprivacy` is generated from the APIs the
code actually calls, including the required-reason APIs for file timestamps and
disk space. It declares no tracking domains, because there are none.

## Google Play, Data safety

| Section | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all user data encrypted in transit? | **Yes** |
| Do you provide a way for users to request data deletion? | **Yes**, in-app deletion for every stored category; there is no server-side data to request |
| Data types collected | none |
| Data types shared | none |
| Ephemeral processing | location is processed on device and not stored or transmitted |

Additional declarations:

- **Advertising id:** not used; the permission is not declared.
- **Target audience:** not directed at children.
- **Government app:** no.
- **Financial features:** none. Poravia does not sell or issue tickets and
  processes no payment. Booking happens on the operator's own site.
- **Health, financial or sensitive personal data:** none collected. Imported
  ticket documents are user content that stays on the device and is never
  uploaded.

## Declarations deliberately left unanswered

These require the account holder and were **not** filled in:

- Export compliance confirmation on the Apple submission form.
- Content rights declarations for any third-party material.
- The age rating questionnaire on either store.
- Acceptance of the Apple Developer Program Licence Agreement and the Play
  Developer Distribution Agreement.
- Any statement about real Greek coach coverage, which would be false while
  `dataMode` is `demo`.

## What must be rechecked before submission

1. That `dataMode` is `real`, because a listing for a Greek coach app whose
   every departure is invented would misrepresent the product.
2. That the shipping build still contains no analytics or advertising
   dependency: run the dependency inventory again, do not assume.
3. That the map tile provider named in the privacy policy is the one the
   shipping build actually calls.
4. That the privacy manifest still matches the APIs the code calls.
