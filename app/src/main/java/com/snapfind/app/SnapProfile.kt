package com.snapfind.app

/**
 * A profile intentionally published by its owner for Snapfind discovery.
 * Preview records use isPreview=true and are never sent to the backend.
 */
data class SnapProfile(
    val id: String,
    val displayName: String,
    val username: String,
    val country: String,
    val interests: String,
    val bio: String,
    val isPreview: Boolean = false,
    val isPublished: Boolean = true
)
