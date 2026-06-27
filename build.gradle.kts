val isCI = providers.environmentVariable("GITHUB_ACTIONS").map { it == "true" }.orElse(false)
if (!isCI.get()) {
    val hookDir = file(".gitHooks")
    val targetDir = file(".git/hooks")
    if (hookDir.isDirectory && targetDir.isDirectory) {
        hookDir.listFiles()?.filter { it.isFile }?.forEach { source ->
            val target = targetDir.resolve(source.name)
            if (!target.exists() || !source.readBytes().contentEquals(target.readBytes())) {
                source.copyTo(target, overwrite = true)
                target.setExecutable(true)
            }
        }
    }
}
