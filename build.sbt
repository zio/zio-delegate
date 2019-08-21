import BuildHelper._
val ZioVersion        = "1.0.0-RC11-1"
val ScmUrl = "https://github.com/mschuwalow/scala-macro-aop"

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
      ScmInfo(url(ScmUrl), "scm:git:git@github.com:mschuwalow/scala-macro-aop.git")
    ),
    licenses := Seq("MIT" -> url(s"${ScmUrl}/blob/v${version.value}/LICENSE")),
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
  .settings(stdSettings("delegate-macro"))
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % ZioVersion,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided",
      "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided"
    )
  )


lazy val coreTests = project
  .in(file("core-tests"))
  .dependsOn(core)
  .settings(stdSettings("core-tests"))
  .settings(
    mainClass := Some("com.schuwalow.delegate.Main"),
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % ZioVersion,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided",
      "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided"
    )
  )

//      compilerPlugin(("org.scalamacros" % "paradise"  % "2.1.1") cross CrossVersion.full)
