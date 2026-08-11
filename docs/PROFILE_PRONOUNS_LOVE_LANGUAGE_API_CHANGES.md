# Profile Pronouns And Love Language API Changes

## Database

- Added lookup table `pronouns` for the single-select profile pronouns field.
- Added lookup table `love_languages` for the multi-select profile love language field.
- Added foreign key column `user_profiles.fk_pronouns`.
- Added join table `user_profile_love_languages`.
- Added enum column `user_match_preferences.love_language_similarity`.

## Changed Endpoints

### `GET /users/me/profile`

Response additions:

- `pronouns`: `LookupOptionResponse | null`
- `loveLanguages`: `LookupOptionResponse[]`

### `PUT /users/me/profile`

Request body additions:

- `pronounsId`: `integer | null`
- `loveLanguageIds`: `integer[] | null`

Notes:

- This endpoint keeps full-replacement semantics for the profile slice.
- Sending `pronounsId: null` clears the stored pronouns.
- Sending `loveLanguageIds: null` or omitting it clears the stored love language list.

### `GET /users/me/match-preferences`

Response additions:

- `loveLanguageSimilarity`: `"ANY" | "SIMILAR" | "DIFFERENT" | null`

### `PUT /users/me/match-preferences`

Request body additions:

- `loveLanguageSimilarity`: `"ANY" | "SIMILAR" | "DIFFERENT" | null`

### `GET /users/me/profile/options`

Response additions:

- `pronouns`: `LookupOptionResponse[]`
- `loveLanguages`: `LookupOptionResponse[]`

### `GET /users/me/profile/completion`

Behavior additions:

- `missingProfileFields` may now include `"pronomes"` and `"linguagens do amor"`.
- `missingMatchPreferenceFields` may now include `"linguagem do amor preferida"`.

## Seeded Lookup Values

The new lookup tables are seeded through `import.sql` with default values for pronouns and the five love languages.