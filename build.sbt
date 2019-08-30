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
    licenses := Seq("Apache 2.0" -> url(s"${scmInfo.value.map(_.browseUrl).get}/blob/v${version.value}/LICENSE")),
  )
)

lazy val root = project
  .in(file("."))
  .aggregate(
    core,
    coreTests
  )

lazy val core = project
  .in(file("core"))
  .settings(stdSettings("delegate-core"))
  .settings(
    scalacOptions --= Seq("-deprecation", "-Xfatal-warnings")
  )
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided",
      "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided"
    )
  )

lazy val coreTests = project
  .in(file("core-tests"))
  .dependsOn(core)
  .settings(stdSettings("delegate-core-tests"))
