````md
# Objective: Implement a Full Generic Matching Algorithm for a Dating/Community App

---

# Introduction

Implement a full generic matching algorithm for a dating/community app focused on accessibility and compatibility between users.

The algorithm must:
- support scalable matching logic
- allow insertion/removal of factors without major refactors
- support weighted compatibility scoring
- separate:
  - hard filters (database query reduction)
  - soft compatibility scoring

The matching process should prioritize:
- communication compatibility
- accessibility practicality
- emotional/social compatibility
- user preferences

---

# Core Architecture

The matching system must be divided into 2 stages:

## Stage 1 — Candidate Retrieval (Hard Filters)
Use strict filters directly in the database query to reduce the amount of candidate users.

This stage MUST:
- drastically reduce query size
- improve performance
- remove impossible matches

---

## Stage 2 — Compatibility Score Calculation (Soft Matching)
After retrieving candidates:
- calculate compatibility scores
- sort by score
- return highest compatibility matches

This stage MUST:
- allow partial compatibility
- never excessively penalize small incompatibilities
- be extensible for future factors

---

# Entities

---

## UserProfile.java

Contains information about the user itself.

### Example fields:
```java
UUID id;
Gender gender;
GenderPreference genderPreference;
Integer age;
GeoLocation location;

List<Interest> interests;

AccessibilityType accessibilityType;
AutonomyLevel autonomyLevel;
LifeStyleType lifeStyle;
LoveLanguageType loveLanguage;
EnergyLevel energyLevel;

List<ConnectionType> connectionTypes;
````

---

## UserMatchPreferences.java

Contains information about what the user wants in a match.

### Example fields:

```java
Integer minAge;
Integer maxAge;

Integer maxDistanceKm;

SimilarityPreference accessibilityPreference;
SimilarityPreference autonomyPreference;
SimilarityPreference lifeStylePreference;
SimilarityPreference loveLanguagePreference;
SimilarityPreference energyPreference;
```

---

## SimilarityPreference.java

```java
public enum SimilarityPreference {
    SIMILAR,
    INDIFFERENT,
    DIFFERENT
}
```

---

# Stage 1 — Candidate Retrieval

This stage applies EXCLUSIVE FILTERS.

These filters MUST be executed directly in the database query.

---

# Step 1 — Gender Preference Filter

This MUST be the FIRST filter applied.

Reason:

* drastically reduces database search size
* removes impossible matches early

## Example:

If user preference is:

```java
WOMEN
```

Retrieve only:

```sql
gender = 'WOMAN'
```

---

# Step 2 — Max Distance Filter

This MUST be the SECOND filter applied.

Use:

* geospatial query
* Haversine distance
* PostGIS if available

## Example:

```sql
distance(user.location, candidate.location) <= maxDistanceKm
```

---

# Step 3 — Age Range Filter

This MUST be the THIRD filter applied.

## Default query:

```sql
candidate.age BETWEEN minAge AND maxAge
```

---

## Age Expansion Strategy

If:

* query returns too few results
* or zero results

Expand range progressively:

* +5 years
* then +10 years

Example:

```text
Preferred:
25-30

Expanded:
20-35
```

IMPORTANT:
Even after expansion:

* age difference must affect final compatibility score

Smaller age gaps should receive higher scores.

---

# Stage 2 — Compatibility Score Calculation

After retrieving candidates:

* calculate a numeric compatibility score
* sort descending

---

# Final Score Structure

Maximum:

```text
100 points
```

---

# Score Weights

| Factor                        | Weight |
| ----------------------------- | ------ |
| Communication Compatibility   | 25     |
| Accessibility Compatibility   | 20     |
| Shared Interests              | 15     |
| Connection Type Compatibility | 15     |
| Life Style Compatibility      | 10     |
| Love Language Compatibility   | 5      |
| Energy Compatibility          | 5      |
| Distance Weight               | 3      |
| Age Difference Weight         | 2      |

---

# Step 4 — Communication Compatibility

## Goal

Ensure users can comfortably communicate.

---

## Calculation

Compare:

```java
user.communicationTypes
candidate.communicationTypes
```

---

## Formula

```text
(sharedCommunicationTypes / maxCommunicationTypes) * 25
```

---

# Step 5 — Accessibility Compatibility

## Goal

Evaluate compatibility between accessibility needs and user preference.

---

## Logic

Use:

```java
SimilarityPreference
```

---

## Rules

### SIMILAR

Higher score if both have same accessibility type.

### DIFFERENT

Higher score if accessibility types differ.

### INDIFFERENT

Always medium/high score.

---

## Example Formula

```text
SIMILAR:
same = 20
different = 5

DIFFERENT:
different = 20
same = 5

INDIFFERENT:
15
```

---

# Step 6 — Shared Interests

## Goal

Reward users with common hobbies/interests.

---

## Formula

```text
(sharedInterests / maxInterests) * 15
```

---

## IMPORTANT

Interests may contain:

* weighted relevance
* similarity levels

Example:

```java
Interest {
    name;
    weight;
}
```

This allows:

* favorite interests
* stronger compatibility scoring

---

# Step 7 — Connection Type Compatibility

## Goal

Ensure both users seek similar interactions.

---

## Examples

* friendship
* relationship
* community
* networking

---

## Rules

### Perfect overlap

```text
15 points
```

### Partial overlap

```text
7 points
```

### No overlap

```text
0 points
```

---

# Step 8 — Autonomy Compatibility

Use:

```java
SimilarityPreference
```

Compare:

```java
user.autonomyLevel
candidate.autonomyLevel
```

---

## Rules

### SIMILAR

Closer levels = higher score

### DIFFERENT

Opposite levels = higher score

### INDIFFERENT

Neutral score

---

# Step 9 — Life Style Compatibility

Compare:

```java
user.lifeStyle
candidate.lifeStyle
```

---

## Rules

### SIMILAR

Same styles preferred

### DIFFERENT

Opposite styles preferred

### INDIFFERENT

Neutral score

---

# Step 10 — Love Language Compatibility

Compare:

```java
user.loveLanguage
candidate.loveLanguage
```

Apply:

```java
SimilarityPreference
```

---

# Step 11 — Energy Compatibility

Compare:

```java
LOW
MEDIUM
HIGH
```

---

## Rules

### Same

```text
5 points
```

### Close

```text
3 points
```

### Opposite

```text
0 points
```

---

# Step 12 — Distance Weight

Even after filtering:

* closer users should receive slightly higher scores

---

## Formula

```text
closerDistance = higherScore
```

---

## Example

| Distance | Score |
| -------- | ----- |
| <= 10km  | 3     |
| <= 30km  | 2     |
| > 30km   | 1     |

---

# Step 13 — Age Difference Weight

Smaller age differences should slightly improve compatibility.

---

## Example

| Age Difference | Score |
| -------------- | ----- |
| <= 2 years     | 2     |
| <= 5 years     | 1     |
| > 5 years      | 0     |

---

# Final Formula

```text
MATCH_SCORE =
communication +
accessibility +
interests +
connectionType +
lifeStyle +
loveLanguage +
energy +
distance +
ageWeight
```

---

# Match Classification

| Score  | Compatibility      |
| ------ | ------------------ |
| 85-100 | Excellent Match    |
| 70-84  | Very Compatible    |
| 50-69  | Compatible         |
| 30-49  | Low Compatibility  |
| 0-29   | Weak Compatibility |

---

# Important Architectural Guidelines

---

# DO NOT Use Hard Blocking for Soft Factors

Avoid:

```text
"Do not show user"
```

Prefer:

```text
"Reduce ranking score"
```

---

# Introduce Controlled Randomness

Recommended:

* 80% score-based matches
* 20% random/discovery matches

Benefits:

* avoids social bubbles
* increases engagement
* improves discovery

---

# Precompute Match Scores

DO NOT calculate matches for all users in real time.

Recommended:

* async workers
* scheduled recalculations
* cache scores

---

# Suggested Table

```sql
user_match_scores
```

---

## Fields

```sql
user_id
candidate_user_id
score
calculated_at
```

---

# Recalculation Triggers

Recalculate when:

* profile updated
* preferences updated
* location changed
* interests changed

---

# Future Scalability

The algorithm MUST support adding new factors easily.

Recommended:

* factor strategy pattern
* modular scoring components

---

# Suggested Architecture

```java
interface MatchFactorScorer {
    Double calculate(User user, User candidate);
}
```

---

# Example Scorers

```text
CommunicationScorer
AccessibilityScorer
InterestScorer
LifeStyleScorer
LoveLanguageScorer
EnergyScorer
```

---

# Final Recommendation

The algorithm should prioritize:

1. Communication practicality
2. Accessibility compatibility
3. Emotional/social compatibility
4. Shared interests

Instead of:

* excessive rigid filtering
* over-segmentation
* medicalized matching

```
```
