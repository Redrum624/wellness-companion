# Daily Goals for Adult Women

Default targets for the Daily Wellness Companion app. These cover 9 of the app's 12 categories: Ideas, Cycle and Bad Habits are tracked without daily targets, and the code defines no goals for them either. All goals are derived from the Women's Health Guidelines document and based on recommendations from the WHO, CDC, National Sleep Foundation, National Academies of Medicine, and the 2025–2030 Dietary Guidelines for Americans.

Goals are designed to be achievable starting points. Users should be encouraged to adjust them based on their age, activity level, health conditions, and personal preferences.

---

## 1. Water Intake

| Goal | Value |
|------|-------|
| Daily total fluid | 2.7 L (91 oz / 11.5 cups) |
| From drinks | 2.2 L (74 oz / 9 cups) |
| Minimum plain water | 1.5 L (50 oz / 6 cups) |

**Spacing target:** drink water at least 6 times throughout the day. Avoid going more than 2–3 waking hours without fluid.

**Hydration check:** at least 2 urinations per day should produce pale or colorless urine.

**Adjustment triggers:**
- Active day (30+ min exercise): add 350–500 ml (12–17 oz)
- Hot weather: add 250–500 ml (8–17 oz)
- Illness: increase to 3+ L
- Pregnancy: increase to 2.9 L (10 cups)
- Breastfeeding: increase to 3.4 L (13 cups)

**App default bottle size:** 900 ml. Goal = 3 full bottles per day.

---

## 2. Food Intake

| Goal | Value |
|------|-------|
| Meals logged per day | 3 (breakfast, lunch, dinner) |
| Snacks | 1–2 as needed |
| Fruits + vegetables | 5+ servings |
| Whole grains | 2–4 servings |
| Protein | present at every meal |
| Added sugar limit | under 25 g total / under 10 g per meal |

**Meal timing targets:**
- Breakfast: within 1–2 hours of waking
- Lunch: 4–5 hours after breakfast
- Dinner: 4–5 hours after lunch, at least 2–3 hours before bed

**Daily checklist (simplified for app tracking):**
- [ ] Ate breakfast
- [ ] Ate lunch
- [ ] Ate dinner
- [ ] Included a fruit or vegetable at 3+ meals
- [ ] Chose whole grains over refined
- [ ] Drank water with meals
- [ ] Avoided or limited highly processed snacks

**Goal is not calorie counting.** The app tracks what was eaten (journaling approach), not how much. The aim is awareness and pattern recognition, not restriction.

---

## 3. Bathroom Breaks

| Goal | Value |
|------|-------|
| Urinations per day | 6–8 (normal range: 4–10) |
| Max gap between urinations | 4 hours during waking hours |
| Nighttime urinations | 0–1 |
| Bowel movements | at least 1 per day (range: 1–3) |

**Tracking purpose:** establish personal baseline patterns. Significant deviations from the user's own normal are more informative than comparing to population averages.

**Flags for the LLM to note in analysis:**
- Urinating more than 10 times per day consistently
- Waking 2+ times per night to urinate
- Going 3+ days without a bowel movement
- Sudden changes in frequency or patterns

---

## 4. General Health

| Goal | Value |
|------|-------|
| Energy level check-ins | 2–3 per day (morning, afternoon, evening) |
| Daily overall rating | 1 per day (end of day) |
| Target energy average | 6+ out of 10 |
| Target daily rating average | 6+ out of 10 |

**Energy check-in schedule:**
- Morning (within 1 hour of waking): baseline energy after sleep
- Afternoon (2:00–3:00 PM): captures the natural post-lunch dip
- Evening (6:00–8:00 PM): end-of-day energy before wind-down

**Symptom tracking goal:** log any notable symptoms the same day they occur. Patterns over 2+ weeks are more useful than individual entries.

**Flags for the LLM to note:**
- Energy consistently below 4 for 5+ consecutive days
- Daily rating trending downward over 2+ weeks
- Recurring symptoms on specific days of the week or menstrual cycle
- New or worsening symptoms

---

## 5. Sleep

| Goal | Value |
|------|-------|
| Total sleep | 7–9 hours per night |
| Ideal target | 8 hours |
| Time to fall asleep | under 20 minutes |
| Night wake-ups | 0–1 |
| Sleep efficiency | 85%+ (time asleep / time in bed) |
| Consistent bedtime | within ±30 minutes each night |
| Consistent wake time | within ±30 minutes each morning |

**Bedtime window recommendation:** aim to be in bed 8.5 hours before your target wake time (allows for falling asleep + brief awakenings).

**Sleep quality score formula (for the app):**
- Start at 10
- Subtract 1 for each hour under 7 hours total sleep
- Subtract 0.5 for each wake-up beyond the first
- Subtract 1 if it took more than 30 minutes to fall asleep
- Subtract 0.5 for going to bed more than 1 hour later than usual
- Floor at 1, cap at 10

**Weekly sleep goals:**
- 5+ nights hitting the 7–9 hour target
- No more than 2 nights under 6 hours
- Consistent schedule at least 5 of 7 days

**Flags for the LLM to note:**
- Average sleep under 6.5 hours for a week
- 3+ wake-ups per night consistently
- Bedtime varying by more than 2 hours across the week
- Declining sleep quality trend over 2+ weeks

---

## 6. Emotions

| Goal | Value |
|------|-------|
| Mood check-ins per day | 2–3 |
| Journal note (optional) | at least 1 per day |
| Target: more positive than negative days | 5+ of 7 days per week |

**Check-in schedule:**
- Morning: how do you feel starting the day?
- Midday or after a significant event: how are you feeling now?
- Evening: how was the overall emotional tone of the day?

**Mood categories and what they mean for tracking:**
- Happy, Calm, Content, Grateful = positive states
- Sad, Angry, Anxious, Tired, Frustrated, Overwhelmed = states to monitor
- No mood is "bad" — all are valid. The goal is awareness, not forced positivity.

**Flags for the LLM to note:**
- Persistent negative mood (same negative emotion 5+ consecutive days)
- Sudden mood shifts not explained by life events
- Correlation with menstrual cycle phase
- Mood patterns tied to sleep, exercise, or social interaction levels
- Emotional flatness or numbness (may indicate dissociation or depression)

---

## 7. Interactions / Journal

| Goal | Value |
|------|-------|
| Meaningful interactions per day | at least 1 |
| Journal entries per day | at least 1 (even brief) |
| In-person social interactions per week | 3+ |
| Quality interactions (rated 4–5 stars) per week | 5+ |

**What counts as a "meaningful interaction":** a conversation lasting more than a few minutes where you felt engaged, heard, or connected. Quick transactional exchanges (ordering coffee, brief work check-ins) don't count unless they felt personally meaningful.

**Journaling targets:**
- Minimum: 1–2 sentences noting who you interacted with and how it felt
- Ideal: a short paragraph reflecting on the interaction and your emotional response
- Extended (especially on PC): longer diary entries about relationships, personal growth, or processing difficult interactions

**Weekly social health check:**
- Did you have at least one interaction that made you feel supported?
- Did you reach out to someone you care about?
- Were there interactions that drained you? Note them for pattern analysis.

**Flags for the LLM to note:**
- No logged interactions for 3+ consecutive days
- Interaction quality ratings consistently below 3
- Declining frequency of in-person interactions
- One-sided patterns (always reaching out, never being reached out to)

---

## 8. Chores / Productivity

| Goal | Value |
|------|-------|
| Tasks completed per day | 3–5 (realistic, not aspirational) |
| Active housework time per day | 30+ minutes |
| Weekly active housework | 150+ minutes |
| Task completion rate target | 60–70% of planned tasks |

**Daily task planning guideline:**
- Plan 5–7 tasks maximum per day
- Include a mix of quick wins (under 10 min) and longer tasks
- Completing 60–70% is a successful day — do not set 100% as the expectation

**Time estimates for common chores:**
| Chore | Typical time | Intensity |
|-------|-------------|-----------|
| Dishes | 10–15 min | Light |
| Vacuuming | 15–20 min | Moderate |
| Laundry (per load) | 10 min active | Light |
| Cooking a meal | 20–45 min | Light to moderate |
| Bathroom cleaning | 15–20 min | Moderate |
| Grocery shopping | 30–60 min | Light (with walking) |
| Mopping | 15–20 min | Moderate |
| Tidying/organizing | 10–20 min | Light |
| Yard work/gardening | 30–60 min | Moderate to vigorous |

**Chore contribution to exercise goals:** moderate-intensity household activities (vacuuming, mopping, scrubbing, gardening) count toward the 150 minutes/week physical activity target.

**Weekly routine template:**
- Daily: dishes, kitchen wipe-down, quick tidy (15–20 min total)
- 2–3x per week: laundry, vacuuming, cooking from scratch
- Weekly: bathroom deep clean, floor mopping, change bed linens
- Monthly: oven/appliance cleaning, organizing one area, window cleaning

**Streak tracking:** completing at least 3 tasks per day for consecutive days builds a streak. Streaks of 7+ days should be celebrated.

**Flags for the LLM to note:**
- Task completion dropping below 40% for a week (may indicate low energy, depression, or over-planning)
- Consistently planning more than 10 tasks per day (unrealistic expectations)
- No logged chores for 3+ days (may correlate with mood or energy changes)
- Significant increase in time-per-chore (may indicate fatigue or health issue)

---

## 9. Hobbies / Quality Time

| Goal | Value |
|------|-------|
| Daily hobby time | 20–30 minutes minimum |
| Weekly hobby time | 120 minutes (2 hours) minimum |
| Variety | engage in 2+ different hobbies per week |
| Crane bowl target | 24 cranes per day (= 120 min = 2 hours) fills the bowl |
| Overflow target | 36 cranes (= 180 min = 3 hours) triggers the overflow |

**Quality time categories (encourage variety):**
- Creative: art, music, writing, crafting, cooking for fun
- Physical: dance, yoga, hiking, sports, gardening
- Social: group activities, game nights, classes
- Restorative: reading, meditation, nature walks, podcasts
- Learning: new skills, languages, instruments, online courses

**Daily minimum benchmark:** at least 1 logged hobby session per day, even if only 5–10 minutes. Consistency matters more than duration.

**Weekly variety goal:** at least 2 different hobbies across the week. This encourages balance between active and restorative leisure.

**Screen time guideline:** aim for passive screen time (social media, TV) to not exceed active hobby time. If you spent 30 minutes reading and 30 minutes sketching, that's a solid day — don't let 2 hours of scrolling erase the benefit.

**Flags for the LLM to note:**
- No hobby time logged for 3+ consecutive days
- Total weekly hobby time under 60 minutes
- Only one type of hobby (no variety)
- Hobby time declining over multiple weeks (may indicate burnout, depression, or over-commitment to work/chores)
- Hobby time consistently high on weekends but zero on weekdays

---

## Daily Summary Scorecard

At the end of each day, the app can generate a simple scorecard showing how many goals were met. A perfect score is not the expectation — the target is consistent effort across categories.

| Category | Daily goal (simplified) | Met? |
|----------|------------------------|------|
| Water | 9+ cups of fluid | |
| Food | 3 meals logged | |
| Bathroom | entries logged normally | |
| Health | 2+ energy check-ins | |
| Sleep | 7–9 hours last night | |
| Emotions | 2+ mood check-ins | |
| Interactions | 1+ meaningful interaction | |
| Chores | 3+ tasks completed | |
| Hobbies | 20+ minutes of hobby time | |

**Scoring:**
- 7–9 goals met = excellent day
- 5–6 goals met = good day
- 3–4 goals met = okay day — note which categories were missed
- Under 3 = the LLM should gently check for patterns (illness? stress? schedule disruption?)

**The goal is never perfection.** It is self-awareness. Tracking creates data. Data reveals patterns. Patterns enable change.

---

## Weekly Goals Summary

| Category | Weekly target |
|----------|--------------|
| Water | Hit daily fluid goal 5+ of 7 days |
| Food | Log all 3 meals 5+ of 7 days |
| Bathroom | Maintain personal baseline patterns |
| Health | Average energy 6+ / daily rating 6+ |
| Sleep | 7–9 hours on 5+ nights, consistent schedule |
| Emotions | More positive days than negative (5+/7) |
| Interactions | 3+ in-person social interactions, 5+ quality entries |
| Chores | 60–70% task completion rate, 150+ min active housework |
| Hobbies | 120+ minutes total, 2+ different activities |

---

*These goals are evidence-based starting points, not rigid rules. The app should allow users to customize every target. The LLM analysis should reference these defaults but adapt its feedback to the user's personal patterns and self-set goals.*
