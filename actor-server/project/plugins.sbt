resolvers ++= Seq(
  // "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
  "Sonatype OSS Releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
  Resolver.url("actor-sbt-plugins", url("https://dl.bintray.com/actor/sbt-plugins"))(Resolver.ivyStylePatterns),
  Resolver.url("typesafe-sbt", url("https://dl.bintray.com/typesafe/sbt-plugins"))(Resolver.ivyStylePatterns),
  Resolver.url("typesafe-ivy", url("https://dl.bintray.com/typesafe/ivy-releases"))(Resolver.ivyStylePatterns),
  ("Flyway" at "http://flywaydb.org/repo").withAllowInsecureProtocol(true),
  "Era7 maven releases" at "https://s3-eu-west-1.amazonaws.com/releases.era7.com",
  Classpaths.sbtPluginReleases
)

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.2")

addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")

// addSbtPlugin("im.actor" %% "sbt-actor-api" % "0.7.18")

// addSbtPlugin("com.trueaccord.scalapb" % "sbt-scalapb" % "0.5.43")

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "2.0.1")

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.9.4")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.13")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.7.5")

// addSbtPlugin("ohnosequences" % "sbt-github-release" % "0.7.1")

addSbtPlugin("com.lightbend.sbt" % "sbt-aspectj" % "0.11.0")

// addSbtPlugin("im.actor" % "actor-sbt-houserules" % "0.1.9")

addSbtPlugin("com.typesafe.sbt" % "sbt-multi-jvm" % "0.4.0")

libraryDependencies ++= Seq(
  "com.github.os72" % "protoc-jar" % "3.0.0-b3"
)
