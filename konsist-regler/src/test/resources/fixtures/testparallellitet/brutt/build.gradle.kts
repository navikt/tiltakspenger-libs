tasks {
    test {
        useJUnitPlatform()
        systemProperty("junit.jupiter.execution.parallel.enabled", "true")
        systemProperty("junit.jupiter.execution.parallel.mode.default", "same_thread")
        // systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "concurrent") i en kommentar teller ikke.
    }
}
