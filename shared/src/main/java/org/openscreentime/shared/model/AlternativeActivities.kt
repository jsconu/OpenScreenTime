package org.openscreentime.shared.model

/**
 * Shown alongside a block screen (see #16) - offering a concrete alternative in the
 * moment works better than a bare block, which just leaves the urge unresolved.
 * Deliberately not shown during a bedtime block, where the point is winding down for
 * sleep, not finding something else active to do.
 */
val ALTERNATIVE_ACTIVITIES = listOf(
    "Go for a walk",
    "Text a friend",
    "Read for a bit",
    "Stretch",
    "Draw something",
    "Tidy up your room",
    "Get a glass of water",
    "Step outside for some air"
)

fun randomAlternativeActivity(): String = ALTERNATIVE_ACTIVITIES.random()
