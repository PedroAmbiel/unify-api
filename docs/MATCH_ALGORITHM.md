# Objective: Implement a full generical matching algorithm for a dating app

## Introduction
Implement a full generical matching algorithm for a dating app. 
The algorithm should take into account various factors such as 
user preferences, interests, location, and compatibility scores 
to suggest potential matches. The algorithm is "generic" because 
it should be easy to insert new factors or modify existing ones 
without requiring a complete overhaul of the system.

## Factors to Consider (Exclusive List)
1. **User Gender Preference**: In user profile, it is a defining factor, when making the select in database, this should be the first filter to apply, as it will significantly reduce the number of potential matches.
2. **Max Location Distance**: In user matching preferences, this is also a defining factor, when making the select in database, this should be the second filter to apply, as it will significantly reduce the number of potential matches.
3. **Age Range**: In user matching preferences, this is also a defining factor, when making the select in database, this should be the third filter to apply, as it will significantly reduce the number of potential matches. (For age range, when retrieving goes down or retrieves nothing, add a +5 or +10 years to the range, this will allow to retrieve more matches, and then the algorithm can apply a weight to the age difference, so that matches with a smaller age difference will have a higher compatibility score.)

## Factors to Consider (Non-Exclusive List)
1. **Interests and Hobbies**: In user profile and profile matches, this can be used to calculate a compatibility score based on shared interests and hobbies. Use the similarity selected to apply an weight to each interest.
2. **Accessibility Needs**: In user profile this info is set and in match profile the user sets if he wants a SIMILAR, INDIFERENT or DIFFERENT from his actual accessibility.
3. **Connection Type**: In user profile preferences, he indicates what type of connections he wants to have, so make sure he finds similar types of connections of people.
4. **Autonomy Compatibility**: In user profile this info is set and in match profile the user sets if he wants a SIMILAR, INDIFERENT or DIFFERENT from his actual autonomy.
5. **Life Style Compatibility**: In user profile this info is set and in match profile the user sets if he wants a SIMILAR, INDIFERENT or DIFFERENT from his actual life style.
6. **Love Language Compatibility**: In user profile this info is set and in match profile the user sets if he wants a SIMILAR, INDIFERENT or DIFFERENT from his actual love language.
7. **Energy Level Compatibility**: In user profile this info is set and in match profile the user sets if he wants a SIMILAR, INDIFERENT or DIFFERENT from his actual energy level.

## Important Entities to use in the Algorithm
1. **UserProfile.java**: Contains information about the user information, such as its gender, location, and other relevant details.
2. **UserMatchPreferences.java**: Contains information about the user's preferences, in SimilarityPrecence.java, such as the maximum distance they are willing to consider for potential matches.