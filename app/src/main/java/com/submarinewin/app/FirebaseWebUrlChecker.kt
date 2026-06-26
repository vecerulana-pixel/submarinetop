package com.submarinewin.app

import com.google.firebase.database.FirebaseDatabase

object FirebaseWebUrlChecker {
    private const val DATABASE_URL = "https://submarine-tap-default-rtdb.europe-west1.firebasedatabase.app"
    private const val URL_NODE = "url"

    fun checkUrl(onUrlFound: (String) -> Unit) {
        try {
            val database = if (DATABASE_URL.isBlank()) {
                FirebaseDatabase.getInstance()
            } else {
                FirebaseDatabase.getInstance(DATABASE_URL)
            }

            database
                .reference
                .child(URL_NODE)
                .get()
                .addOnSuccessListener { snapshot ->
                    val url = snapshot.getValue(String::class.java)?.trim().orEmpty()
                    if (WebUrlStore.isWebUrl(url)) {
                        onUrlFound(url)
                    }
                }
                .addOnFailureListener {
                    // Firebase is optional until google-services.json is added.
                }
        } catch (_: Throwable) {
            // Stay in the native game when Firebase is missing, offline, or misconfigured.
        }
    }
}
