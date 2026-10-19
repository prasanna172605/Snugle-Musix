package com.music.snugglemusix.models

data class SponsorBlockSegment(
    val segment: List<Float>,
    val UUID: String,
    val category: String,
    val actionType: String,
    val locked: Int,
    val votes: Int,
    val description: String,
)
