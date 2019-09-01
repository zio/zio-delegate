import sbtcrossproject.CrossPlugin.autoImport.crossProject
import BuildHelper._

name := "delegate"

inThisBuild(
  List(
    organization := "com.schuwalow",
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
        url("https://github.com/mschuwalow/scala-macro-aop"),
        "scm:git:git@github.com:mschuwalow/scala-macro-aop.git"
      )
    ),
    licenses := Seq("Apache 2.0" -> url(s"${scmInfo.value.map(_.browseUrl).get}/blob/v${version.value}/LICENSE"))
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
    coreTests.js
  )
  .enablePlugins(ScalaJSPlugin)

lazy val core = crossProject(JSPlatform, JVMPlatform)
  .in(file("core"))
  .settings(stdSettings("delegate-core"))
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
  .in(file("core-tests"))
  .dependsOn(core)
  .settings(stdSettings("delegate-core-tests"))

lazy val examples = crossProject(JSPlatform, JVMPlatform)
  .in(file("examples"))
  .dependsOn(core)
  .settings(stdSettings("delegate-examples"))
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % "1.0.0-RC11-1"
    )
  )
