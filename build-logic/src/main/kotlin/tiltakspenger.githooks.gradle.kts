/**
 * Installerer repoets git-hooks som en del av bygget.
 * Pluginen hører hjemme på rotprosjektet: hooks er per utsjekk, ikke per modul.
 */

plugins {
    base
}

val gitHooks =
    tasks.register<Copy>("gitHooks") {
        group = "git hooks"
        description = "Installerer git-hooks fra .gitHooks/ til .git/hooks/."
        // I en worktree er .git en fil (gitdir-peker), ikke en katalog; hooks eies av hovedklonen, så tasken hopper over.
        // Verdien fanges utenfor lambdaen: configuration cache kan ikke serialisere referanser til byggskript-objekter.
        val erHovedklone = layout.projectDirectory.dir(".git").asFile.isDirectory
        onlyIf("kun i hovedklonen, ikke i worktrees") { erHovedklone }
        from(layout.projectDirectory.dir(".gitHooks"))
        into(layout.projectDirectory.dir(".git/hooks"))
        filePermissions { unix("rwxr-xr-x") }
    }

tasks.named("build") { dependsOn(gitHooks) }
