# Match Endpoints Frontend Guide

## Objective

This guide describes all match-related endpoints currently implemented in the backend so a frontend agent can integrate them without inferring missing behavior.

Scope of this guide:

- discovery feed retrieval;
- expanding discovery candidate profile details and gallery images;
- creating or answering a possible match;
- reading confirmed mutual matches;
- loading matched users' profile pictures;
- understanding the error format and cleanup behavior tied to the match flow.

This guide only documents what is already implemented.

## Authentication

All routes in this guide require:

- `Authorization: Bearer <access_token>`
- authenticated user with role `user`

Primary match base path:

- `/users/me/matches`

Related discovery profile routes:

- `/users/me/profile/public`

## Implemented Routes Overview

| Method | Route | Purpose |
| --- | --- | --- |
| `POST` | `/users/me/matches/discovery` | Returns a discovery feed as an array of profile UUIDs |
| `GET` | `/users/me/profile/public?userProfileId={uuid}` | Returns the public profile payload for one discovery candidate |
| `GET` | `/users/me/profile/public/images?userProfileId={uuid}` | Returns the active gallery image UUIDs for one discovery candidate |
| `GET` | `/users/me/profile/public/images/{imageId}?userProfileId={uuid}` | Returns one active gallery image from a discovery candidate |
| `POST` | `/users/me/matches` | Creates a new possible match or answers an inbound pending match |
| `GET` | `/users/me/matches/mutual` | Returns confirmed mutual matches |
| `GET` | `/users/me/matches/mutual/paged?page={page}&size={size}` | Returns confirmed mutual matches with pagination and user card data |
| `GET` | `/users/me/matches/images/{imageId}` | Returns the active profile picture of a mutual match |

## Shared Error Format

When these endpoints fail, the backend returns the standard `ErrorResponse` object:

```json
{
  "code": 3005,
  "error": "VALIDATION_INVALID_FORMAT",
  "message": "Formato de entrada inválido: Defina pelo menos um gênero desejado antes de buscar matches",
  "timestamp": 1778765432100
}
```

Important notes:

- `timestamp` is an epoch-milliseconds number.
- `error` is the enum name, not a localized string.
- `message` is Portuguese and may contain contextual details after the default error message.

Most common error categories used by the match endpoints:

- `USER_NOT_FOUND` with HTTP `404`
- `VALIDATION_INVALID_FORMAT` with HTTP `400`
- `RESOURCE_NOT_FOUND` with HTTP `404`
- `RESOURCE_CONFLICT` with HTTP `409`

## Route 1: Discovery Feed

### `POST /users/me/matches/discovery`

### Purpose

Returns a discovery feed of potential profile UUIDs for the authenticated user.

The response is intentionally lightweight:

- it returns only profile UUIDs;
- it does not return profile card data;
- it does not return images;
- it does not return the compatibility score.

### Request Body

```json
{
  "alreadyUsedProfileIds": [
    "019dcfdc-fa72-722b-89ac-e331eb4f119a",
    "019dd111-1234-7abc-90de-1234567890ab"
  ]
}
```

Field reference:

- `alreadyUsedProfileIds`: `string[]`
  - list of `user_profiles.id` values that were already shown to the frontend and should be ignored in the next discovery call.
  - duplicates and `null` values are ignored.
  - if omitted, empty, or the body itself is `null`, the backend treats it as an empty list.

### Success Response

HTTP `200 OK`

```json
[
  "019dd201-aaaa-7bcd-90de-111111111111",
  "019dd202-bbbb-7bcd-90de-222222222222",
  "019dd203-cccc-7bcd-90de-333333333333"
]
```

Response contract:

- response body is a raw JSON array;
- each item is a `user_profiles.id` UUID string;
- target size is `50` items;
- the backend may return fewer than `50` items if the candidate pool is too small.

### Current Backend Rules

The discovery algorithm currently applies these rules:

1. hard filters are applied in this order:
   - gender
   - age
   - distance using the Haversine formula
2. the candidate query only considers verified users.
3. the current user profile is always excluded.
4. `alreadyUsedProfileIds` are excluded.
5. profiles that the current user has already started a match with are excluded.
6. inbound requests that were already answered by the current user are excluded.
7. inbound pending requests are prioritized and inserted into the final feed unless they are already in `alreadyUsedProfileIds`.
8. age filtering starts with the saved preference range and may expand by `+5` and then `+10` years if needed.
9. after retrieval, candidates are scored using communication, accessibility, autonomy, interests, connection type, lifestyle, love language, energy, distance, and age gap.
10. the feed is blended organically:
    - approximately `80%` ranked candidates
    - approximately `20%` randomized discovery candidates
    - preferred merge pattern is `4 ranked + 1 discovery`
11. discovery candidates are randomized from profiles with score `>= 30`, preferring scores below `70` first.

### Important Discovery Notes For Frontend

- The route returns only UUIDs, not full cards.
- Use `GET /users/me/profile/public?userProfileId={uuid}` to expand one discovery UUID into a public profile payload.
- Use `GET /users/me/profile/public/images?userProfileId={uuid}` when the frontend only needs the active gallery image UUID list.
- Use `GET /users/me/profile/public/images/{imageId}?userProfileId={uuid}` to download one active gallery image.
- The `userProfileId` query param expects the `user_profiles.id` value returned by discovery, not `users.id`.
- The backend currently also allows profiles whose gender lookup id is `4` (`Prefiro não informar`) to pass the gender filter even if that id is not in the desired gender list.

### Discovery Preconditions

The backend returns HTTP `400` if the authenticated user is missing any of these prerequisites:

- profile exists;
- match preferences exist;
- profile gender is set;
- active location exists;
- age can be calculated from `users.birthdate`;
- `maxMatchDistanceKm` is valid;
- at least one desired gender exists.

Example validation error:

```json
{
  "code": 3005,
  "error": "VALIDATION_INVALID_FORMAT",
  "message": "Formato de entrada inválido: Defina uma localização ativa antes de buscar matches",
  "timestamp": 1778765432100
}
```

## Route 2: Discovery Profile Details

### `GET /users/me/profile/public?userProfileId={uuid}`

### Purpose

Returns the public profile fields needed to render or inspect one discovery candidate.

### Query Param

- `userProfileId`: `string`
  - required UUID from the discovery response array;
  - must be a `user_profiles.id` value.

### Success Response

HTTP `200 OK`

```json
{
  "userProfileId": "019dd201-aaaa-7bcd-90de-111111111111",
  "name": "Larissa",
  "age": 28,
  "bio": "Adoro conversar sobre livros e tecnologia assistiva",
  "gender": {
    "id": 1,
    "description": "Mulher"
  },
  "pronouns": {
    "id": 2,
    "description": "Ela/Dela"
  },
  "disabilities": [
    {
      "id": 1,
      "description": "Física",
      "ionicIcon": "walk-outline"
    }
  ],
  "accessibilityNeeds": [
    {
      "id": 1,
      "description": "Leitor de tela"
    }
  ],
  "autonomyLevel": {
    "id": 1,
    "description": "Independente"
  },
  "communicationForms": [
    {
      "id": 1,
      "description": "Texto"
    }
  ],
  "lifestyleTypes": [
    {
      "id": 1,
      "description": "Caseira"
    }
  ],
  "loveLanguages": [
    {
      "id": 2,
      "description": "Tempo de qualidade"
    }
  ],
  "energyLevel": {
    "id": 1,
    "description": "Moderada"
  },
  "interestTypes": [
    {
      "id": 1,
      "description": "Tecnologia"
    }
  ],
  "galleryImageIds": [
    "019dd211-aaaa-7bcd-90de-121212121212",
    "019dd212-bbbb-7bcd-90de-343434343434"
  ]
}
```

### Notes

- this route intentionally exposes only the candidate's public matching fields;
- `galleryImageIds` contains only active gallery images and never the profile picture;
- it does not return active coordinates or other private owner-only fields;
- the target profile must exist and belong to a verified user;
- if `userProfileId` is missing, the backend returns HTTP `400` with `VALIDATION_INVALID_FORMAT`;
- if the profile does not exist or is not public, the backend returns HTTP `404` with `RESOURCE_NOT_FOUND`.

## Route 3: Discovery Gallery Image IDs

### `GET /users/me/profile/public/images?userProfileId={uuid}`

### Purpose

Returns the active gallery image UUIDs for one public discovery candidate without downloading image bytes.

### Query Param

- `userProfileId`: `string`
  - required UUID from the discovery response array;
  - must be a `user_profiles.id` value.

### Success Response

HTTP `200 OK`

```json
{
  "userProfileId": "019dd201-aaaa-7bcd-90de-111111111111",
  "galleryImageIds": [
    "019dd211-aaaa-7bcd-90de-121212121212",
    "019dd212-bbbb-7bcd-90de-343434343434"
  ]
}
```

### Notes

- the response includes only active gallery image UUIDs;
- profile pictures are excluded;
- an empty array means the searched user currently has no active gallery images;
- if `userProfileId` is missing, the backend returns HTTP `400` with `VALIDATION_INVALID_FORMAT`;
- if the profile does not exist or is not public, the backend returns HTTP `404` with `RESOURCE_NOT_FOUND`.

## Route 4: Discovery Gallery Image Content

### `GET /users/me/profile/public/images/{imageId}?userProfileId={uuid}`

### Purpose

Returns one active gallery image from the searched public profile as binary JPEG.

### Request

Query param:

- `userProfileId`: `string`
  - required `user_profiles.id` value for the searched profile.

Path parameter:

- `imageId`: `string`
  - required UUID from `galleryImageIds` returned by Route 2 or Route 3.

Suggested headers:

- `Authorization: Bearer <access_token>`
- `Accept: image/jpeg`

### Success Response

HTTP `200 OK`

Response body:

- raw JPEG bytes

Response content type:

- `image/jpeg`

### Failure Cases

HTTP `400` is returned when:

- `userProfileId` is missing.

HTTP `404` is returned when:

- the searched profile does not exist;
- the searched profile is not public;
- `imageId` does not belong to the searched profile;
- `imageId` refers to an inactive image;
- `imageId` refers to a profile picture instead of a gallery image;
- authenticated user cannot be resolved.

## Route 5: Create Or Answer A Match

### `POST /users/me/matches`

### Purpose

This route has two behaviors depending on whether there is already an inbound pending match from the target profile.

Behavior A:

- create a new possible match started by the current user.

Behavior B:

- answer a pending match that the target user already started against the current user.

### Request Body

```json
{
  "targetProfileId": "019dd201-aaaa-7bcd-90de-111111111111",
  "accepted": true
}
```

Field reference:

- `targetProfileId`: `string`
  - required
  - must be a valid `user_profiles.id`
- `accepted`: `boolean`
  - required
  - `true` means either create a request or accept an inbound request
  - `false` only works when answering an inbound pending request

### Mode 1: Create New Outbound Possible Match

This happens when:

- there is no inbound pending match from `targetProfileId` to the current user;
- `accepted = true`.

The backend creates a row with this state:

- `starterProfileId = current user profile id`
- `pendingProfileId = targetProfileId`
- `starterAccepted = true`
- `pendingAccepted = null`

Example success response:

```json
{
  "id": "019dd301-aaaa-7bcd-90de-444444444444",
  "starterProfileId": "019dd401-aaaa-7bcd-90de-555555555555",
  "pendingProfileId": "019dd201-aaaa-7bcd-90de-111111111111",
  "createdAt": "2026-05-13T19:30:12.123456Z",
  "starterAccepted": true,
  "pendingAccepted": null,
  "mutualMatch": false
}
```

### Mode 2: Answer Existing Inbound Pending Match

This happens when:

- the target profile already started a pending match toward the current user;
- `pendingAccepted` is still `null`;
- `accepted` can be either `true` or `false`.

If `accepted = true`:

- the row becomes a confirmed mutual match.

If `accepted = false`:

- the row is marked as declined.
- it remains in the database until the cleanup scheduler removes it.

Example accept response:

```json
{
  "id": "019dd302-bbbb-7bcd-90de-666666666666",
  "starterProfileId": "019dd201-aaaa-7bcd-90de-111111111111",
  "pendingProfileId": "019dd401-aaaa-7bcd-90de-555555555555",
  "createdAt": "2026-05-13T14:12:00.000000Z",
  "starterAccepted": true,
  "pendingAccepted": true,
  "mutualMatch": true
}
```

Example reject response:

```json
{
  "id": "019dd302-bbbb-7bcd-90de-666666666666",
  "starterProfileId": "019dd201-aaaa-7bcd-90de-111111111111",
  "pendingProfileId": "019dd401-aaaa-7bcd-90de-555555555555",
  "createdAt": "2026-05-13T14:12:00.000000Z",
  "starterAccepted": true,
  "pendingAccepted": false,
  "mutualMatch": false
}
```

### Conflict And Validation Rules

HTTP `400` cases:

- request body missing;
- `targetProfileId` missing;
- `accepted` missing;
- trying to target your own profile;
- sending `accepted = false` when there is no inbound pending match to answer;
- authenticated user has no profile.

HTTP `404` cases:

- target profile does not exist;
- authenticated user not found.

HTTP `409` cases:

- current user already started a match with the same target profile;
- inbound pending match was already answered and cannot be answered again.

Example conflict response:

```json
{
  "code": 5002,
  "error": "RESOURCE_CONFLICT",
  "message": "Conflito ao processar recurso: Você já iniciou um match com este perfil",
  "timestamp": 1778765432100
}
```

### Frontend State Model For This Route

Treat the response as these states:

- `pendingAccepted = null` and `mutualMatch = false`
  - outbound request created and waiting for the other user
- `pendingAccepted = true` and `mutualMatch = true`
  - confirmed mutual match
- `pendingAccepted = false` and `mutualMatch = false`
  - inbound request rejected by the current user

## Route 6: Read Mutual Matches

### `GET /users/me/matches/mutual`

### Purpose

Returns all confirmed mutual matches for the authenticated user.

Only rows where both sides accepted are returned.

### Success Response

HTTP `200 OK`

```json
[
  {
    "userProfileId": "019dd201-aaaa-7bcd-90de-111111111111",
    "profileImage": {
      "id": "019dd501-aaaa-7bcd-90de-777777777777",
      "profilePicture": true,
      "active": true,
      "url": "/users/me/matches/images/019dd501-aaaa-7bcd-90de-777777777777"
    }
  },
  {
    "userProfileId": "019dd202-bbbb-7bcd-90de-222222222222",
    "profileImage": null
  }
]
```

Field reference:

- `userProfileId`: `string`
  - the other matched profile id, never the current user profile id
- `profileImage`: `UserProfileImageResponse | null`
  - `null` when the matched user has no active profile picture

`profileImage` object shape:

```json
{
  "id": "019dd501-aaaa-7bcd-90de-777777777777",
  "profilePicture": true,
  "active": true,
  "url": "/users/me/matches/images/019dd501-aaaa-7bcd-90de-777777777777"
}
```

### Frontend Notes

- This route returns the matched user's active profile picture URL.
- For paginated list UIs, prefer `GET /users/me/matches/mutual/paged?page={page}&size={size}` because it also returns `userId`, `fullName`, and `age`.
- Discovery gallery images are fetched through `GET /users/me/profile/public/images/{imageId}?userProfileId={uuid}`.
- The returned `url` is relative; prepend the API host when needed.
- Empty array means no confirmed mutual matches yet.

### Paginated Alternative: `GET /users/me/matches/mutual/paged?page={page}&size={size}`

Use this route when the frontend needs a paginated inbox/list of confirmed mutual matches.

Success response example:

```json
{
  "matches": [
    {
      "userId": "019f0c40-1111-7caa-9123-111111111111",
      "userProfileId": "019f0c41-2222-7caa-9123-222222222222",
      "fullName": "Ana Souza",
      "age": 29,
      "profilePicture": {
        "id": "019f0c42-3333-7caa-9123-333333333333",
        "profilePicture": true,
        "active": true,
        "url": "/users/me/matches/images/019f0c42-3333-7caa-9123-333333333333"
      }
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```

Field reference:

- `matches[].userId`: matched person's `users.id`
- `matches[].userProfileId`: matched person's `user_profiles.id`
- `matches[].fullName`: concatenated `users.name` + `users.lastName`
- `matches[].age`: calculated from `users.birthdate`
- `matches[].profilePicture`: same `UserProfileImageResponse` shape used by the image route
- `page`: zero-based current page
- `size`: requested page size
- `totalElements`: total confirmed mutual matches
- `totalPages`: total available pages
- `hasNext`: whether another page exists

## Route 7: Read Mutual Match Profile Image

### `GET /users/me/matches/images/{imageId}`

### Purpose

Returns the active profile picture of a mutual match as binary JPEG.

This route is restricted:

- image must exist;
- image must be active;
- image must be marked as profile picture;
- image owner must be in a confirmed mutual match with the current user.

### Request

Path parameter:

- `imageId`: `string` UUID from `MutualMatchResponse.profileImage.id` or `MutualMatchSummaryResponse.profilePicture.id`

Suggested headers:

- `Authorization: Bearer <access_token>`
- `Accept: image/jpeg`

### Success Response

HTTP `200 OK`

Response body:

- raw JPEG bytes

Response content type:

- `image/jpeg`

### Failure Cases

HTTP `404` is returned when:

- image id does not exist;
- image is inactive;
- image is not a profile picture;
- current user is not in a confirmed mutual match with that image owner;
- authenticated user cannot be resolved.

Example response:

```json
{
  "code": 5001,
  "error": "RESOURCE_NOT_FOUND",
  "message": "Recurso não encontrado: Imagem de match não encontrada",
  "timestamp": 1778765432100
}
```

## Internal Match Record Model

The backing table for this flow is `user_possible_matches`.

Stored fields:

- `id`
- `fk_starter_user_profile`
- `fk_pending_user_profile`
- `created_at`
- `starter_accepted`
- `pending_accepted`

Meaning of the acceptance fields:

- `starterAccepted`
  - always `true` for created rows in the current implementation
- `pendingAccepted = null`
  - waiting for response
- `pendingAccepted = true`
  - mutual match confirmed
- `pendingAccepted = false`
  - inbound request rejected by the pending user

Duplicate protection:

- the same starter profile cannot create a second row toward the same pending profile
- this is enforced at the database level and in service logic

## Cleanup Scheduler Behavior

There is no frontend route for this, but it affects UI state.

Current cleanup rule:

- every `10h`, the scheduler deletes rows where `pendingAccepted = false`

Rows that are kept:

- `pendingAccepted = null`
- `pendingAccepted = true`

Frontend implication:

- a rejected inbound request may disappear from backend state after the scheduler runs.

## Practical Frontend Integration Notes

### Recommended discovery call pattern

1. keep a local list of discovery profile ids already shown to the user during the day or session;
2. send that list in `alreadyUsedProfileIds` on every refresh;
3. expect a UUID array response;
4. load candidate card data through `GET /users/me/profile/public?userProfileId={uuid}`;
5. download gallery images on demand using the returned `galleryImageIds`.

### Recommended swipe call mapping

Use `POST /users/me/matches` like this:

- user liked someone:
  - send `accepted: true`
- user is answering an inbound request and accepts:
  - send `accepted: true`
- user is answering an inbound request and rejects:
  - send `accepted: false`

Important limitation:

- `accepted: false` is not a general dislike endpoint.
- it only works when answering an inbound pending request already started by the target user.

### What is not implemented in this change

The following routes were not added as part of this match feature:

- fetch inbound pending matches directly as a separate inbox route
- fetch outbound pending matches directly as a separate sent-requests route
- undo or cancel an outbound pending match

If the frontend needs any of those flows, they require additional backend work.
