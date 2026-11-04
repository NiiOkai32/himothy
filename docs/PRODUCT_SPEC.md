PROMPT

You are a senior Android engineer and product designer. Build a complete Android application called CampusFit, a gamified fitness app for university students who struggle to find time to exercise. Students compete with friends to see who loses the most weight over a day, week, and month, using short, flexible workouts that fit between lectures.

1. Tech stack
- Kotlin, Jetpack Compose (Material 3), MVVM with clean architecture
- Hilt for dependency injection, Room for local storage, Retrofit for networking
- Firebase (Auth, Firestore, Cloud Messaging, Storage) for accounts, friends, leaderboards, and notifications
- Min SDK 26, target latest stable SDK
- Offline-first: workouts and weigh-ins must work without internet and sync later
2. Target users and core problem
 University students with packed timetables, limited budgets, and small living spaces (hostels). Workouts must be short (2 to 20 minutes), need no equipment, and be doable in a hostel room, corridor, or campus.

3. Core features

Onboarding and profile
- Sign up with email, Google, or university email
- Collect name, age, height, current weight, goal weight, fitness level, and free time per day
- Let the user pick their university and optionally join a campus or department group
- Privacy controls: the user can compete using percentage of body weight lost instead of raw kilograms, so competition stays fair across body sizes
Workout library (numerous and interactive)
- At least 60 exercises across categories: HIIT, bodyweight strength, cardio (jumping jacks, high knees, stair climbing, skipping), yoga and stretching, core, dance workouts, walking and running (GPS tracked), and “dorm-room” low-noise workouts
- Each exercise has an animated demo, step-by-step instructions, difficulty level, estimated calories burned, and target muscles
- “I have X minutes” quick-start: the user enters available time (5, 10, 15, 20 min) and the app generates a workout plan
- Guided workout player with timer, rest intervals, voice cues, haptic feedback, and a pause/skip control
- Optional camera-based rep counting for common exercises (squats, push-ups, jumping jacks) using ML Kit pose detection
- Step counter and activity tracking via Health Connect
Weight and progress tracking
- Daily weigh-in logging with a trend chart (day, week, month views)
- Calorie burn estimation based on exercise MET values and user weight
- Anti-cheating measures: limit weigh-ins to one verified entry per day, flag unrealistic losses (for example more than 2% body weight in a day), and show a “verified by activity” badge when logged workouts support the change
Competition (the game layer)
- Add friends by username, phone contact, or QR code
- Create challenges: Daily, Weekly, Monthly, each with a live leaderboard ranked by weight lost (or % lost), with an option to rank by workout minutes or calories burned
- Private friend groups and public campus-wide leaderboards
- Points (XP), levels, streaks, badges, and trophies (for example “7-Day Streak”, “Early Bird”, “Stair Master”)
- Weekly “league” system with promotion and relegation, like Duolingo
- In-app reactions, cheers, and friendly trash-talk messages
- Team mode: hostel vs hostel, department vs department, class vs class
Engagement and interactivity
- Smart notifications that suggest a workout during the user’s free gaps between lectures
- Timetable import so the app finds free slots automatically
- Home screen widget showing today’s rank and streak
- Daily mini-challenges (for example “30 squats before your next class”)
- Confetti and animations on completing workouts and winning challenges
- Dark mode and a clean, energetic, youth-oriented UI
4. Health and safety (important)
- Show a disclaimer and a health screening step at onboarding
- Warn users against unhealthy goals: cap suggested weight loss at a safe rate (about 0.5 to 1 kg per week), block goal weights with a BMI below 18.5, and show supportive messaging instead of rewarding extreme loss
- Avoid body-shaming language; focus on healthy habits, consistency, and streaks, not only weight
- Include a “rest day” option and injury-friendly alternatives
- Provide a way to opt out of weight-based competition and compete on activity only
- Comply with data protection best practices; store sensitive health data securely and let users delete their data
5. Screens to build
 Splash, Onboarding, Login/Signup, Home Dashboard, Workout Library, Workout Detail, Active Workout Player, Quick Workout Generator, Progress and Weight Log, Challenges List, Challenge Detail with Leaderboard, Friends, Profile and Settings, Badges and Achievements, Notifications.

6. Deliverables, in this order
1. A short architecture overview and folder structure
2. Firestore data model and security rules
3. Room entities and DAOs
4. Gradle files and dependencies
5. Navigation graph and the Compose theme
6. Full implementation, one feature module at a time (Auth, Workouts, Tracking, Challenges and Leaderboards, Notifications), with complete, compilable code
7. Seed data: a JSON file of 60+ exercises
8. Unit tests for the leaderboard and weight-loss calculations
9. A README with setup steps (Firebase configuration, API keys, how to run)
Write production-quality code with comments, handle loading, error, and empty states, and follow Material 3 design guidelines. After each module, pause and ask whether to continue to the next one.