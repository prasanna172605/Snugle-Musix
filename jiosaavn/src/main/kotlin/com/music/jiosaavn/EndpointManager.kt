package com.music.jiosaavn



object EndpointManager {
    val SERVERS = listOf(
        "https://saavn.echomusic.fun",
        "https://jiosaavn-api.mac-adityadav9532.workers.dev",
        "https://jiosaavn-api.pc-adityadav9532.workers.dev"
    )

    var currentServerIndex = 0
        private set

    fun getCurrentServer(): String {
        return SERVERS[currentServerIndex]
    }

    fun reportSuccess(server: String) {
        val index = SERVERS.indexOf(server)
        if (index != -1 && currentServerIndex != index) {
            currentServerIndex = index
            println("[Saavn] Successfully connected. Caching endpoint ${index + 1} for session.")
        }
    }

    fun reportFailure(failedServer: String, reason: String) {
        val failedIndex = SERVERS.indexOf(failedServer)
        if (failedIndex != -1) {
            println("[Saavn] Endpoint ${failedIndex + 1} failed:\n$reason")
            // Only advance if the failure matches the current index (prevents double advancing if multiple requests fail concurrently)
            if (failedIndex == currentServerIndex) {
                currentServerIndex = (currentServerIndex + 1) % SERVERS.size
            }
        } else {
            println("[Saavn] Unknown endpoint failed:\n$failedServer - $reason")
        }
    }

    fun resetToPrimary() {
        currentServerIndex = 0
    }
}
