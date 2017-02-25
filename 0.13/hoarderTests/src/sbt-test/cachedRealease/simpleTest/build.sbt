org.romanowski.hoarder.tests.PluginTests.testRecompilation

org.romanowski.hoarder.actions.CachedRelease.settings

publishTo := Some(Resolver.file("my-local", baseDirectory.value / "repo")(Resolver.defaultIvyPatterns))

publishMavenStyle := false
publishArtifact in Test := true
