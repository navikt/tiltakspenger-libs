val isCI = providers.environmentVariable("GITHUB_ACTIONS").map { it == "true" }.orElse(false)
if (!isCI.get()) {
    val hookSource = file(".scripts/pre-commit")
    val hookTarget = file(".git/hooks/pre-commit")
    if (hookSource.exists() && (!hookTarget.exists() || !hookSource.readBytes().contentEquals(hookTarget.readBytes()))) {
        hookSource.copyTo(hookTarget, overwrite = true)
        hookTarget.setExecutable(true)
    }
}
