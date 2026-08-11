# Mutual Matches Frontend Agent Guide

## Objective

This guide describes the new paginated mutual-match endpoint so a frontend agent can build a match inbox or list screen without guessing the payload shape.

All routes in this guide require:

- `Authorization: Bearer <access_token>`
- authenticated user with role `user`

Primary match base path:

- `/users/me/matches`

## Route Overview

| Method | Route | Purpose |
| --- | --- | --- |
| `GET` | `/users/me/matches/mutual/paged?page={page}&size={size}` | Returns confirmed mutual matches with pagination and card metadata |
| `GET` | `/users/me/matches/images/{imageId}` | Returns the active profile picture of a confirmed mutual match |

## Route 1: Paginated Mutual Matches

### `GET /users/me/matches/mutual/paged?page={page}&size={size}`

### Purpose

Returns only matches where both users accepted the match.

This route is intended for list UIs because each item already contains:

- the matched person's `users.id`;
- the matched person's `user_profiles.id`;
- full name;
- age;
- active profile-picture metadata and download URL when available.

### Query Params

- `page`: `number`
  - optional
  - zero-based page index
  - default is `0`
  - must be `>= 0`
- `size`: `number`
  - optional
  - page size
  - default is `20`
  - allowed range is `1` to `100`

### Success Response

HTTP `200 OK`

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
    },
    {
      "userId": "019f0c43-4444-7caa-9123-444444444444",
      "userProfileId": "019f0c44-5555-7caa-9123-555555555555",
      "fullName": "Lucas Lima",
      "age": 31,
      "profilePicture": null
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 2,
  "totalPages": 1,
  "hasNext": false
}
```

### Field Reference

- `matches`: `MutualMatchSummaryResponse[]`
- `page`: `number`
- `size`: `number`
- `totalElements`: `number`
- `totalPages`: `number`
- `hasNext`: `boolean`

Each `matches[]` item contains:

- `userId`: `string`
  - the matched person's `users.id`
- `userProfileId`: `string`
  - the matched person's `user_profiles.id`
- `fullName`: `string | null`
  - built from `users.name + users.lastName`
- `age`: `number | null`
  - computed from `users.birthdate`
- `profilePicture`: `UserProfileImageResponse | null`
  - `null` when the matched user has no active profile picture

## Route 2: Mutual Match Profile Picture Download

### `GET /users/me/matches/images/{imageId}`

### Purpose

Downloads the active profile picture of a matched user as binary JPEG.

Use this route only for confirmed mutual matches. The backend rejects image access when the requester is not part of a confirmed mutual match with the image owner.

### Image Mapping Rules

- Prefer the `profilePicture.url` field returned by the paginated response.
- If the frontend needs to build the path manually, use `/users/me/matches/images/{imageId}`.
- `imageId` must come from `matches[].profilePicture.id`.
- The URL is relative, so prepend the API base URL in the client.

Example:

```ts
const src = `${API_URL}${match.profilePicture?.url}`;
```

### Fallback Behavior

- If `profilePicture` is `null`, render a placeholder avatar.
- Do not call the image endpoint when `profilePicture` is `null`.

## Frontend Implementation Notes

- Use `page = 0` for the initial load.
- Increment `page` only when `hasNext` is `true`.
- Deduplicate by `userProfileId` in client state if multiple requests can overlap.
- Use `userProfileId` when navigating to profile-specific screens.
- Use `userId` only when a screen explicitly needs the base `users.id`.
- The route returns only mutual matches, so pending inbound or outbound matches never appear here.

## Expected Errors

- HTTP `400` with `VALIDATION_INVALID_FORMAT` when `page` or `size` is invalid.
- HTTP `404` with `USER_NOT_FOUND` when the authenticated user cannot be resolved.
- HTTP `404` with `RESOURCE_NOT_FOUND` when requesting an image that is unavailable or not accessible.