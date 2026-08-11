# User Profile Image API Guide

All user profile image endpoints live under `/users/me/profile/images` and require the same authenticated user token already used for the other `/users/me` routes.

## What the API stores

- Every uploaded image is compressed with Thumbnailator before persistence.
- Images are normalized and served back as `image/jpeg`.
- The database keeps all user images in `user_profile_images` with these relevant fields: `id` (UUIDv7), `oid`, `fk_user_profile`, `is_profile_pic`, `active`.
- Only one active profile picture is allowed per user.
- Up to 5 active gallery images are allowed per user.
- The profile picture does not count toward the 5 gallery images.
- When the gallery already has 5 active images, the backend returns `409 RESOURCE_CONFLICT` until one image is deactivated.

## Read profile with image metadata

### Request

```http
GET /users/me/profile
Authorization: Bearer <token>
```

### Response excerpt

```json
{
  "id": "01971b48-13cb-7f4d-a7f8-0c07bc3f0dd9",
  "bio": "Pessoa acessível e comunicativa",
  "profilePicture": {
    "id": "01971b50-6a4a-7b87-b4d6-65ad93dc1db8",
    "profilePicture": true,
    "active": true,
    "url": "/users/me/profile/images/01971b50-6a4a-7b87-b4d6-65ad93dc1db8"
  },
  "galleryImages": [
    {
      "id": "01971b51-2eb8-7df6-aeef-31d9c614676f",
      "profilePicture": false,
      "active": true,
      "url": "/users/me/profile/images/01971b51-2eb8-7df6-aeef-31d9c614676f"
    }
  ]
}
```

You can also request only the active images with:

```http
GET /users/me/profile/images
Authorization: Bearer <token>
```

## Upload the profile picture

### Request

```http
POST /users/me/profile/images/profile-picture
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

Form field name:

- `image`: the file to upload

### Example with JavaScript

```ts
const formData = new FormData();
formData.append("image", fileInput.files[0]);

const response = await fetch(`${API_URL}/users/me/profile/images/profile-picture`, {
  method: "POST",
  headers: {
    Authorization: `Bearer ${token}`,
  },
  body: formData,
});

const payload = await response.json();
```

Uploading a new profile picture automatically deactivates the previous active profile picture.

## Upload a gallery image

### Request

```http
POST /users/me/profile/images/gallery
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

Form field name:

- `image`: the file to upload

### Example with JavaScript

```ts
const formData = new FormData();
formData.append("image", selectedFile);

const response = await fetch(`${API_URL}/users/me/profile/images/gallery`, {
  method: "POST",
  headers: {
    Authorization: `Bearer ${token}`,
  },
  body: formData,
});

if (response.status === 409) {
  const conflict = await response.json();
  console.error(conflict.message);
}
```

## Deactivate an image

Use this when the user wants to free a gallery slot or remove the active profile picture.

```http
DELETE /users/me/profile/images/{imageId}
Authorization: Bearer <token>
```

### Example with JavaScript

```ts
await fetch(`${API_URL}/users/me/profile/images/${imageId}`, {
  method: "DELETE",
  headers: {
    Authorization: `Bearer ${token}`,
  },
});
```

## Display an image

Each metadata object contains a relative `url`. Use it directly with the API base URL.

```ts
const src = `${API_URL}${image.url}`;
```

The backend response is binary `image/jpeg`, so it can be used in `<img />` or any carousel component.

```tsx
<img src={`${API_URL}${image.url}`} alt="User gallery" />
```

## Expected errors

- `400 VALIDATION_INVALID_FORMAT`: missing file, empty file, or unsupported image content.
- `404 RESOURCE_NOT_FOUND`: the image does not exist for the authenticated user.
- `409 RESOURCE_CONFLICT`: the user already has 5 active gallery images.