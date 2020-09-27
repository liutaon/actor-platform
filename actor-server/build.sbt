addCommandAlias("debianPackage", "debian:packageBin")
addCommandAlias("debianPackageSystemd",
  "; set serverLoading in Debian := com.typesafe.sbt.packager.archetypes.ServerLoader.Systemd ;debian:packageBin"
)

defaultLinuxInstallLocation in Docker := "/var/lib/actor"

javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint")

val ScalaVersion = "2.11.8"
  val BotKitVersion = "1.0"

  lazy val buildSettings =
    Defaults.coreDefaultSettings ++
      Seq(
        //version := Version,
        scalaVersion := ScalaVersion,
        scalaVersion in ThisBuild := ScalaVersion,
        crossPaths := false,
        organization := "im.actor.server",
        organizationHomepage := Some(url("https://actor.im")),
//        scalacOptions ++= Seq(
//          "-Ywarn-unused",
//          "-Ywarn-adapted-args",
//          "-Ywarn-nullary-override",
//          "-Ywarn-nullary-unit",
//          "-Ywarn-value-discard"
//        ),
        parallelExecution := true
      ) ++ Sonatype.sonatypeSettings

  lazy val defaultSettingsBotkit =
    buildSettings

  lazy val defaultSettingsServer =
      buildSettings

  lazy val root = Project(
    "actor",
    file(".")
  ).
    settings (
      defaultSettingsServer ++

      Seq(
        libraryDependencies ++= Dependencies.root,
        autoCompilerPlugins := true,
        scalacOptions in(Compile, doc) ++= Seq(
          "-Ywarn-unused-import",
          "-groups",
          "-implicits",
          "-diagrams"
        )
      )
  )
    .dependsOn(actorServerSdk)
    .aggregate(
      actorServerSdk,
      actorTestkit
    )
    .enablePlugins(JavaServerAppPackaging, JDebPackaging)

  lazy val actorActivation = Project(
    id = "actor-activation",
    base = file("actor-activation")
  ).settings(defaultSettingsServer ++
      Seq(
        libraryDependencies ++= Dependencies.activation,
        scalacOptions in Compile := (scalacOptions in Compile).value.filterNot(_ == "-Ywarn-unused-import")
      ))
    .dependsOn(actorCore, actorEmail, actorSms, actorPersist)

  lazy val actorBots = Project(
    id = "actor-bots",
    base = file("actor-bots"),
  ).settings(defaultSettingsServer ++ Seq(libraryDependencies ++= Dependencies.bots))
    .dependsOn(actorCore, actorHttpApi, actorTestkit % "test")

  lazy val actorBotsShared = Project(
    id = "actor-bots-shared",
    base = file("actor-bots-shared")
    ).settings(defaultSettingsBotkit ++ Seq(
      version := BotKitVersion,
      libraryDependencies ++= Dependencies.botShared,
      addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full))
  )

  lazy val actorBotkit = Project(
    id = "actor-botkit",
    base = file("actor-botkit")
    ).settings(defaultSettingsBotkit ++ Seq(
      version := BotKitVersion,
      libraryDependencies ++= Dependencies.botkit,
      addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full))
  )
    .dependsOn(actorBotsShared)
    .aggregate(actorBotsShared)

  lazy val actorCli = Project(
    id = "actor-cli",
    base = file("actor-cli")
  ).settings(defaultSettingsServer ++ Seq(
      libraryDependencies ++= Dependencies.cli
    )
  )
    .dependsOn(actorCore, actorFrontend)

  lazy val actorCore = Project(
    id = "actor-core",
    base = file("actor-core")
  ).
    settings(defaultSettingsServer ++ Seq(
      libraryDependencies ++= Dependencies.core
    )
  )
    .dependsOn(actorCodecs, actorFileAdapter, actorModels, actorPersist, actorRuntime)

  lazy val actorEmail = Project(
    id = "actor-email",
    base = file("actor-email")).
    settings ( defaultSettingsServer ++
      Seq(
        libraryDependencies ++= Dependencies.email
      )
  )
    .dependsOn(actorRuntime)

  lazy val actorEnrich = Project(
    id = "actor-enrich",
    base = file("actor-enrich")
  ).
    settings (defaultSettingsServer ++ Seq(
      libraryDependencies ++= Dependencies.enrich
    )
  )
    .dependsOn(actorRpcApi, actorRuntime)

  lazy val actorHttpApi = Project(
    id = "actor-http-api",
    base = file("actor-http-api")
  ).
    settings (defaultSettingsServer ++ Seq(
      libraryDependencies ++= Dependencies.httpApi
    )
  )
    .dependsOn(actorPersist, actorRuntime)//runtime deps because of ActorConfig

  lazy val actorNotify = Project(
    id = "actor-notify",
    base = file("actor-notify")
  ).
    settings(defaultSettingsServer ++
      Seq(libraryDependencies ++= Dependencies.shared)
  )
    .dependsOn(actorCore, actorEmail)

  lazy val actorOAuth = Project(
    id = "actor-oauth",
    base = file("actor-oauth")
  ).
    settings ( defaultSettingsServer ++
      Seq(
        libraryDependencies ++= Dependencies.oauth
      )
  )
    .dependsOn(actorPersist)

  lazy val actorSession = Project(
    id = "actor-session",
    base = file("actor-session")
  ).
    settings ( defaultSettingsServer ++ Seq(
      libraryDependencies ++= Dependencies.session
    )
  )
    .dependsOn(actorCodecs, actorCore, actorPersist, actorRpcApi)

  lazy val actorSessionMessages = Project(
    id = "actor-session-messages",
    base = file("actor-session-messages")
  ).
    settings (defaultSettingsServer ++ Seq(libraryDependencies ++= Dependencies.sessionMessages)
  )
    .dependsOn(actorCore)

  lazy val actorRpcApi = Project(
    id = "actor-rpc-api",
    base = file("actor-rpc-api")
  ).
    settings ( defaultSettingsServer ++ Seq(
      libraryDependencies ++= Dependencies.rpcApi
    )
  )
    .dependsOn(
    actorActivation,
    actorCore,
    actorOAuth,
    actorSessionMessages,
    actorSms)

  lazy val actorSms = Project(
    id = "actor-sms",
    base = file("actor-sms")
  ).
    settings ( defaultSettingsServer ++ Seq(libraryDependencies ++= Dependencies.sms)
  )
    .dependsOn(actorRuntime)

  lazy val actorFileAdapter = Project(
    id = "actor-fs-adapters",
    base = file("actor-fs-adapters")
  ).
    settings ( defaultSettingsServer ++ Seq(
      libraryDependencies ++= Dependencies.fileAdapter
    )
  )
    .dependsOn(actorHttpApi, actorPersist)

  lazy val actorFrontend = Project(
    id = "actor-frontend",
    base = file("actor-frontend")
  ).
    settings ( defaultSettingsServer ++ Seq(
      libraryDependencies ++= Dependencies.frontend
    )
  )
    .dependsOn(actorCore, actorSession)

  lazy val actorCodecs = Project(
    id = "actor-codecs",
    base = file("actor-codecs")
    ).settings ( defaultSettingsServer ++ Seq(
      libraryDependencies ++= Dependencies.codecs
    )
  )
    .dependsOn(actorModels)

  lazy val actorModels = Project(
    id = "actor-models",
    base = file("actor-models")
    ).settings ( defaultSettingsServer ++ Seq(
      libraryDependencies ++= Dependencies.models
    )
  )

  lazy val actorPersist = Project(
    id = "actor-persist",
    base = file("actor-persist"),
    ).settings ( defaultSettingsServer ++ Seq(
      libraryDependencies ++= Dependencies.persist
    )
  )
    .dependsOn(actorModels, actorRuntime)

  lazy val actorTestkit = Project(
    id = "actor-testkit",
    base = file("actor-testkit")
    ).settings ( defaultSettingsServer ++ Seq(
      libraryDependencies ++= Dependencies.tests
    )
  ).configs(Configs.all: _*)
    .dependsOn(
      actorCore,
      actorRpcApi,
      actorSession
    )

  lazy val actorRuntime = Project(
    id = "actor-runtime",
    base = file("actor-runtime")
    ).settings ( defaultSettingsServer ++ Seq(
      libraryDependencies ++= Dependencies.runtime
    )
  )

  lazy val actorServerSdk = Project(
    id = "actor-server-sdk",
    base = file("actor-server-sdk")
    ).settings ( defaultSettingsServer ++ Seq(
      libraryDependencies ++= Dependencies.sdk
    )
  )
    .dependsOn(
    actorActivation,
    actorBots,
    actorCli,
    actorEnrich,
    actorEmail,
    actorFrontend,
    actorHttpApi,
    actorNotify,
    actorOAuth,
    actorRpcApi
  ).aggregate(
    actorActivation,
    actorBots,
    actorCli,
    actorCodecs,
    actorCore,
    actorEmail,
    actorEnrich,
    actorFileAdapter,
    actorFrontend,
    actorHttpApi,
    actorModels,
    actorNotify,
    actorOAuth,
    actorPersist,
    actorRpcApi,
    actorRuntime,
    actorSession,
    actorSessionMessages,
    actorSms
  )
