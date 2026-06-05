package com.example

import java.util.ArrayList

class FpsTracker {
    private val frameTimes = ArrayList<Long>()
    private val WINDOW_DURATION_NS = 500_000_000L // 500ms window for responsive updates
    
    // Statistics
    var currentFps = 0f
        get() = synchronized(this) { field }
        private set(value) = synchronized(this) { field = value }
        
    var averageFps = 0f
        get() = synchronized(this) { field }
        private set(value) = synchronized(this) { field = value }
        
    var minFps = 0f
        get() = synchronized(this) { field }
        private set(value) = synchronized(this) { field = value }
        
    var maxFps = 0f
        get() = synchronized(this) { field }
        private set(value) = synchronized(this) { field = value }
        
    var stutterCount = 0
        get() = synchronized(this) { field }
        private set(value) = synchronized(this) { field = value }
        
    private var totalFrames = 0L
    private var sessionStartTimeNanos = 0L

    fun reset() {
        synchronized(this) {
            frameTimes.clear()
            currentFps = 0f
            averageFps = 0f
            minFps = 0f
            maxFps = 0f
            stutterCount = 0
            totalFrames = 0L
            sessionStartTimeNanos = 0L
        }
    }

    fun recordFrame(nanoTime: Long) {
        synchronized(this) {
            if (sessionStartTimeNanos == 0L) {
                sessionStartTimeNanos = nanoTime
            }
            totalFrames++
            
            // Record frame timestamp
            frameTimes.add(nanoTime)
            
            // Clear older timestamps
            val cutoff = nanoTime - WINDOW_DURATION_NS
            while (frameTimes.isNotEmpty() && frameTimes[0] < cutoff) {
                frameTimes.removeAt(0)
            }
            
            val size = frameTimes.size
            if (size >= 2) {
                val elapsedNanoInWindow = frameTimes.last() - frameTimes.first()
                if (elapsedNanoInWindow > 0L) {
                    val computedFps = (size - 1) * 1_000_000_000f / elapsedNanoInWindow
                    currentFps = computedFps
                    
                    if (computedFps > 0f) {
                        // Max FPS
                        if (computedFps > maxFps) {
                            maxFps = computedFps
                        }
                        
                        // Min FPS: only register after 500ms to avoid initial cold start dip
                        val sessionElapsed = nanoTime - sessionStartTimeNanos
                        if (sessionElapsed > 500_000_000L) {
                            if (minFps == 0f || computedFps < minFps) {
                                minFps = computedFps
                            }
                        }
                    }
                }
            }
            
            // Detect stutter: if the time difference between the last two frames was > 33.3ms (missed VSYNC)
            if (frameTimes.size >= 2) {
                val lastFrameDiffNs = frameTimes[frameTimes.size - 1] - frameTimes[frameTimes.size - 2]
                if (lastFrameDiffNs > 33_333_333L) { // > 33.3 ms
                    stutterCount++
                }
            }
            
            // Calculate global session average
            val elapsedSessionNs = nanoTime - sessionStartTimeNanos
            if (elapsedSessionNs > 100_000_000L) {
                averageFps = (totalFrames - 1) * 1_000_000_000f / elapsedSessionNs
            }
        }
    }
}
