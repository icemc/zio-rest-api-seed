import Dependencies.Libraries._

ThisBuild / scalaVersion     := "2.13.16"
ThisBuild / organization     := "io.hiis"
ThisBuild / organizationName := "HIIS"

val applicationName    = "zio-api-seed"
val applicationVersion = sys.env.getOrElse("VERSION", "0.0.1")
val dockerUser         = "hiis"

lazy val root = project
  .in(file("."))
  .settings(
    commonSettings,
    dockerSettings,
    name               := applicationName,
    version            := applicationVersion,
    evictionErrorLevel := Level.Warn,
    semanticdbEnabled  := true,
    semanticdbVersion  := scalafixSemanticdb.revision
  )
  .enablePlugins(DockerPlugin, BuildInfoPlugin)
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .aggregate(
    core,
    application,
    it
  )

lazy val core = project
  .in(file("modules/core"))
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .enablePlugins(SbtTwirl, BuildInfoPlugin)
  .settings(
    compilerPlugins.flatMap(addCompilerPlugin),
    commonSettings ++ coverageSettings ++ testSettings,
    consoleSettings,
    buildInfo,
    buildInfoOps,
    name := "core",
    TwirlKeys.templateImports += "io.hiis._",
    libraryDependencies ++=
      zio ++ metrics ++ cats ++ json ++ logging ++ apache ++ tests // Add all common library dependencies here. Have a look at Dependencies object
  )

lazy val application = project
  .in(file("modules/application"))
  .enablePlugins(BuildInfoPlugin, SbtTwirl)
  .settings(
    compilerPlugins.flatMap(addCompilerPlugin),
    commonSettings ++ coverageSettings ++ testSettings,
    consoleSettings,
    buildInfo,
    buildInfoOps,
    name    := "application",
    version := sys.env.getOrElse("VERSION", applicationVersion),
    assemblySettings,
    TwirlKeys.templateImports += "io.hiis._",
    libraryDependencies ++=
      (tapir ++ auth ++ zioConfig ++ mongodb ++ redis ++ tests).map(_.exclude("org.slf4j", "*"))
  )
  .dependsOn(core)

lazy val it = (project in file("modules/it"))
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings(
    compilerPlugins.flatMap(addCompilerPlugin),
    commonSettings ++ coverageSettings ++ testSettings,
    consoleSettings,
    name    := "integration-test",
    version := sys.env.getOrElse("VERSION", applicationVersion),
    libraryDependencies ++= tests
  )
  .dependsOn(core, application)

lazy val commonSettings = Seq(
  scalafmtOnCompile := true,
  scalacOptions ++= compilerOptions,
  javacOptions ++= Seq("-source", "21", "-target", "21"),
  semanticdbEnabled := true,
  semanticdbVersion := scalafixSemanticdb.revision,
  resolvers ++= Resolver.sonatypeOssRepos("snapshots"),
  resolvers += "hiis-repository-releases" at "https://artifacts.hiis.io/releases",
  resolvers += "hiis-repository-private" at "https://artifacts.hiis.io/private",
  credentials += Credentials(
    "Reposilite",
    "artifacts.hiis.io",
    sys.env.getOrElse("DEV_ID", "dev"),
    sys.env.getOrElse("ARTIFACT_TOKEN", "no-token-provided")
  )
)

lazy val consoleSettings = Seq(
  Compile / console / scalacOptions --= Seq("-Ywarn-unused", "-Ywarn-unused-import")
)

lazy val compilerOptions = Seq(
  "-unchecked",
  "-deprecation",
  "-encoding",
  "utf8",
  "-feature",
  "-language:implicitConversions",
  "-language:higherKinds",
  "-language:existentials",
  "-language:postfixOps",
  "-Ywarn-value-discard",
  "-Ymacro-annotations",
  "-Ywarn-unused:imports"
)

lazy val assemblySettings = Seq(
  assembly / assemblyJarName := applicationName + ".jar",
  assembly / assemblyMergeStrategy := {
    case PathList("META-INF", ps @ _*) =>
      ps.map(_.toLowerCase) match {
        case "services" :: xs =>
          MergeStrategy.filterDistinctLines
        case string =>
          if (string.exists(a => a.contains("swagger-ui")))
            MergeStrategy.singleOrError
          else MergeStrategy.discard
      }
    case x if x.endsWith("module-info.class") => MergeStrategy.discard
    case _                                    => MergeStrategy.first
  }
)

lazy val dockerSettings = Seq(
  docker / imageNames           := Seq(ImageName(s"$dockerUser/$applicationName:latest")),
  docker / dockerBuildArguments := dockerBuildArgs,
  docker / dockerfile := {
    // The assembly task generates a fat JAR file
    val artifact: File = new File(s"./modules/application/target/scala-2.13/$applicationName.jar")
    val artifactTargetPath = s"/app/${artifact.name}"

    new Dockerfile {
      from("openjdk:21-alpine")
      add(artifact, artifactTargetPath)
      expose(9090) // Make sure your app is serving at this port. See ApiGateway Class
      entryPoint("java", "-jar", artifactTargetPath)
    }
  }
)

def dockerBuildArgs: Map[String, String] = sys.env.foldLeft(Map.empty[String, String]) {
  case (acc, (k, v)) =>
    if (Set("UPX_COMPRESSION", "PRINT_REPORTS").contains(k)) acc + (k.toLowerCase -> v) else acc
}

// coverage
lazy val coverageSettings = Seq(
  coverageFailOnMinimum      := true,
  coverageMinimumStmtTotal   := 85,
  coverageMinimumBranchTotal := 70,
  coverageExcludedPackages   := ".*\\.hiis\\.Application;.*\\.hiis\\.ApiGatewayT"
)

// to start app in separate JVM and allow killing it without restarting SBT set to true (developing purpose)
lazy val testSettings = Seq(
  libraryDependencies += "com.vladsch.flexmark" % "flexmark-all" % "0.64.8" % Test,
  run / fork                                   := false,
  Test / fork                                  := true,
  Test / parallelExecution                     := false,
  Test / testOptions ++= Seq(
    Tests.Argument(TestFrameworks.ScalaTest, "-eNDXEHLO"),
    Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/test-reports")
  ),
  Test / testFrameworks ++= Seq(
    new TestFramework("zio.test.sbt.ZTestFramework"),
    TestFrameworks.ScalaTest
  )
)

lazy val buildInfo = Seq(
  buildInfoPackage             := "io.hiis.service.core.build",
  buildInfoObject              := "BuildInfo",
  buildInfoKeys                := Seq[BuildInfoKey](scalaVersion, sbtVersion),
  buildInfoKeys += "name"      -> applicationName,
  buildInfoKeys += "version"   -> applicationVersion,
  buildInfoKeys += "gitCommit" -> git.gitHeadCommit.value.getOrElse("Not Set"),
  buildInfoKeys += "gitBranch" -> git.gitCurrentBranch.value,
  buildInfoKeys += "build"     -> sys.env.getOrElse("CIRCLE_BUILD_NUM", "")
)

lazy val buildInfoOps = Seq(
  buildInfoOptions += BuildInfoOption.ToJson,
  buildInfoOptions += BuildInfoOption.BuildTime
)

addCommandAlias("build", ";clean; compile; test; assembly;")
addCommandAlias("build-docker", ";build; docker;")
addCommandAlias("fix-lint", ";scalafixAll; scalafmtSbt;")
addCommandAlias("start", "application/~reStart;")
