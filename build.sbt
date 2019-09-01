import sbtcrossproject.CrossPlugin.autoImport.crossProject
import BuildHelper._
import xerial.sbt.Sonatype._

inThisBuild(
  List(
    name := "delegate",
    organization := "com.schuwalow",
    homepage := Some(url("https://github.com/mschuwalow/delegate")),
    developers := List(
      Developer(
        "mschuwalow",
        "Maxim Schuwalow",
        "maxim.schuwalow@gmail.com",
        url("https://github.com/mschuwalow")
      )
    ),
    scmInfo := Some(
      ScmInfo(
        homepage.value.get,
        "scm:git:git@github.com:mschuwalow/delegate.git"
      )
    ),
    licenses := Seq("Apache-2.0" -> url(s"${scmInfo.value.map(_.browseUrl).get}/blob/v${version.value}/LICENSE")),
    pgpPublicRing := file("/tmp/public.asc"),
    pgpSecretRing := file("/tmp/secret.asc"),
    releaseEarlyWith := SonatypePublisher
  )
)

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")
addCommandAlias(
  "testJVM",
  ";coreTestsJVM/test;examplesJVM/compile"
)
addCommandAlias(
  "testJS",
  ";coreTestsJS/test;examplesJS/compile"
)

lazy val root = project
  .in(file("."))
  .settings(
    skip in publish := true
  )
  .aggregate(
    core.jvm,
    core.js,
    coreTests.jvm,
    coreTests.js,
    examples.jvm,
    examples.js
  )
  .enablePlugins(ScalaJSPlugin)

lazy val core = crossProject(JSPlatform, JVMPlatform)
  .in(file("core"))
  .settings(stdSettings("delegate"))
  .settings(
    scalacOptions --= Seq("-deprecation", "-Xfatal-warnings")
  )
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect"  % scalaVersion.value % "provided",
      "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided"
    )
  )

lazy val coreTests = crossProject(JSPlatform, JVMPlatform)
  .in(file("tests"))
  .dependsOn(core)
  .settings(stdSettings("delegate-tests"))
  .settings(
    skip in publish := true
  )

lazy val examples = crossProject(JSPlatform, JVMPlatform)
  .in(file("examples"))
  .dependsOn(core)
  .settings(stdSettings("delegate-examples"))
  .settings(
    skip in publish := true,
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % "1.0.0-RC11-1"
    )
  )
