val scala3Version = "3.3.6" 
val zioHttpVersion = "3.0.1"

lazy val root = project
  .in(file("."))
  .settings(
    name := "simple-zio-server",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % "2.0.19",
      "dev.zio" %% "zio-http" % zioHttpVersion,
      "dev.zio" %% "zio-json" % "0.6.2"
    )
  )