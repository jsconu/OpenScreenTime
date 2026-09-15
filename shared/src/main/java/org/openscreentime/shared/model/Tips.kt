package org.openscreentime.shared.model

/**
 * Ambient, not-in-a-moment-of-friction version of the same habit-replacement idea behind
 * #12 and #16: a concrete alternative works better than just removing screen time and
 * leaving the urge unresolved. Shown once per app open, not naggy - see #17.
 *
 * v1 is a static, hand-written list; loosely grouped by comment below (solo / together /
 * environment) but picked uniformly at random - a real category system is a v2 concern.
 */
val ACTIVITY_TIPS = listOf(
    // Solo activities
    "Read a few pages of a book.",
    "Stretch for five minutes.",
    "Draw or doodle something.",
    "Write down three things you're looking forward to.",
    "Reorganize one shelf or drawer.",
    "Practice an instrument for ten minutes.",
    "Go for a short walk.",
    "Water the plants.",
    "Try a new stretch or yoga pose.",
    "Write a letter to someone, on paper.",

    // With a parent or kid
    "Fly a kite together.",
    "Cook or bake something together.",
    "Play a board game or card game.",
    "Go for a bike ride together.",
    "Take the dog (or a neighbor's dog) for a walk.",
    "Build something with blocks or a construction set.",
    "Shoot some hoops or kick a ball around.",
    "Do a jigsaw puzzle together.",
    "Plan a weekend outing together.",
    "Teach each other something new - a card trick, a recipe, a skill.",

    // Environment / habit tweaks
    "Keep your phone on a magnet on the fridge instead of in your pocket.",
    "Charge your phone outside the bedroom tonight.",
    "Put your phone in another room during meals.",
    "Turn off notifications for one app you check out of habit.",
    "Leave your phone at home for a short errand.",
    "Set your phone to grayscale for the rest of the day."
)

fun randomTip(): String = ACTIVITY_TIPS.random()
