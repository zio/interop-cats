name := "interop-cats"

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")
addCommandAlias("testJVM", ";zioInteropCatsTestsJVM/test;zioTestInteropCatsJVM/test;coreOnlyTestJVM/test")
addCommandAlias("testJS", ";zioInteropCatsTestsJS/test;zioTestInteropCatsJS/test;coreOnlyTestJS/test")

lazy val root = project
  .in(file("."))
  .aggregate(docs)
  .settings(
    publish / skip := true
  )

lazy val docs = project
  .in(file("zio-interop-cats-docs"))
  .settings(
    publish / skip := true
  )
  .enablePlugins(WebsitePlugin)
