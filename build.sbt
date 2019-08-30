
// DEPENDENCIES
lazy val dependencyManager =
    new {
        // Versions
        val logbackV = "1.2.3"
        val scalaLoggingV = "3.9.2"
        val scalatestV = "3.0.8"
        //
        val catsV = "2.0.0-RC2"
        //
        val shapelessV = "2.3.3"
        //
        val EffV = "4.5.0"

        // Libraries
        val logback = "ch.qos.logback" % "logback-classic" % logbackV
        val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV
        val scalatest = "org.scalatest" %% "scalatest" % scalatestV
        //
        val catsCore = "org.typelevel" %% "cats-core" % catsV
        val catsEffect = "org.typelevel" %% "cats-effect" % catsV
        //
        val shapeless = "com.chuusai" %% "shapeless" % shapelessV
        //
        val eff = "org.atnos" %% "eff" % EffV
    }

lazy val dependencies = Seq(
    dependencyManager.scalatest % Test,
    dependencyManager.logback,
    dependencyManager.scalaLogging,
    //dependencyManager.catsCore,
    //dependencyManager.catsEffect,
    dependencyManager.shapeless,
    dependencyManager.eff
)

lazy val root = project.in(file("."))
        .settings(
            name := "TaglessFinalExample",
            version := "0.1",
            scalaVersion := "2.12.9",
            libraryDependencies ++= dependencies,
            scalacOptions ++= Seq(
                "-language:higherKinds",
                "-deprecation",
                "-encoding", "UTF-8",
                "-feature",
                "-language:_",
                "-Ypartial-unification"
            ),
            addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.4")
        )

// SETTINGS
lazy val settings = Seq(
    scalaSource in Compile := baseDirectory.value / "main/scala",
    scalaSource in Test := baseDirectory.value / "test/scala",
    resourceDirectory in Compile := baseDirectory.value / "main/resources",
    resourceDirectory in Test := baseDirectory.value / "test/resources",
    resolvers ++= Seq(
        "Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository",
        Resolver.sonatypeRepo("releases"),
        Resolver.sonatypeRepo("snapshots")
    )
)
