````md
---

# Randomized Match Retrieval Strategy

To avoid:
- repetitive recommendations
- social bubbles
- excessive algorithmic rigidity

The system MUST implement a hybrid retrieval strategy.

This strategy combines:
- high compatibility matches
- randomized discovery matches

---

# Retrieval Distribution

Recommended distribution:

| Type | Percentage |
|---|---|
| Score-Based Matches | 80% |
| Randomized Discovery Matches | 20% |

---

# IMPORTANT RULE

Even randomized matches MUST still respect:

## Mandatory Hard Filters
- Gender preference
- Maximum distance
- Basic account validity/safety rules

Randomization MUST NOT bypass:
- gender compatibility
- distance constraints
- blocked users
- banned users
- hidden profiles

---

# Retrieval Flow

---

# Step 1 — Retrieve Score-Based Candidates

Retrieve:
- highest compatibility scores
- ordered descending

Example:
```sql
ORDER BY match_score DESC
LIMIT 80
````

---

# Step 2 — Retrieve Randomized Candidates

Retrieve:

* random users
* from same hard-filtered pool
* ignoring low compatibility score ranking

IMPORTANT:
Random retrieval should:

* avoid extremely incompatible profiles
* avoid score = 0 users

Recommended minimum score:

```text
>= 30
```

---

## Example Query

```sql
WHERE:
gender compatible
AND distance <= maxDistance
AND match_score >= 30

ORDER BY RANDOM()
LIMIT 20
```

---

# Step 3 — Merge Results

Merge:

* 80% ranked matches
* 20% randomized matches

Then:

* shuffle slightly
* avoid obvious grouping

---

# Example Final Feed

Instead of:

```text
1
2
3
4
5
6
7
8
random
random
```

Prefer:

```text
1
2
random
3
4
5
random
6
7
8
```

This creates:

* discovery sensation
* feed freshness
* reduced predictability

---

# Randomization Constraints

Randomized users should still satisfy:

| Constraint           | Required |
| -------------------- | -------- |
| Gender compatibility | YES      |
| Max distance         | YES      |
| Age validity         | YES      |
| Not blocked          | YES      |
| Not reported/banned  | YES      |

---

# Soft Score Threshold

Randomized matches should still maintain minimum quality.

Recommended:

```text
minimumScore >= 30
```

This avoids:

* completely incompatible users
* poor UX
* irrelevant matches

---

# Optional Improvement — Controlled Randomness by Range

Instead of pure random:
retrieve users from:

```text
score range 30-60
```

Benefits:

* discovery
* still reasonably compatible
* safer recommendations

---

# Suggested Retrieval Architecture

---

## Step A — Retrieve Ranked Matches

```java
List<User> rankedMatches
```

---

## Step B — Retrieve Randomized Matches

```java
List<User> discoveryMatches
```

---

## Step C — Merge Feed

```java
List<User> finalFeed
```

---

# Suggested Merge Strategy

Example:

* every 4 ranked users
* insert 1 randomized user

Pseudo:

```java
ranked
ranked
ranked
ranked
random
```

---

# Anti-Repetition Rule

Randomized retrieval MUST:

* avoid recently shown users
* avoid already disliked users
* avoid already matched users

---

# Suggested Tables

```sql
user_match_impressions
```

Tracks:

```sql
viewer_user_id
shown_user_id
shown_at
interaction_type
```

---

# Recommended Cooldown

Avoid showing same profile again for:

```text
7-14 days
```

unless:

* explicit interaction exists
* profile changed significantly

---

# Benefits of Hybrid Retrieval

| Benefit                | Description                  |
| ---------------------- | ---------------------------- |
| Better discovery       | Users meet different people  |
| Less algorithm fatigue | Feed feels less repetitive   |
| More engagement        | Increased curiosity          |
| Reduced filter bubbles | Avoids excessive similarity  |
| Fairer exposure        | New users receive visibility |

---

# Final Recommendation

The feed should feel:

* intelligent
* dynamic
* human
* slightly unpredictable

NOT:

* overly optimized
* repetitive
* rigid
* deterministic

```
```
