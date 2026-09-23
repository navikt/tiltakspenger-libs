tasks {
    test {
        useJUnitPlatform()
        systemProperty("junit.jupiter.testinstance.lifecycle.default", "per_class")
        systemProperty("junit.jupiter.execution.parallel.enabled", "true")
        systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")
        systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "concurrent")
    }
}
