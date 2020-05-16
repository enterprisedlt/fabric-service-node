import sbt.addCompilerPlugin
import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}
import sbtcrossproject.Platform

ThisBuild / scalaVersion := "2.12.10"
ThisBuild / organization := "org.enterprisedlt"
ThisBuild / version := "1.4.2-rc-3"
ThisBuild / javacOptions ++= Seq("-source", "1.8", "-target", "1.8")
ThisBuild / scalacOptions += "-target:jvm-1.8"

val ContractSpecVersion = "2.0.0-RC1"
val CodecsVersion = "2.0.0-RC3"
val FabricChainCodeClientVersion = "1.4.3.200-RC6"
val FabricChainCodeScalaVersion = "1.4.0.200-RC4"

val BouncyCastleVersion = "1.60"
val JettyVersion = "9.4.26.v20200117"
val DockerApiVersion = "3.2.0-rc4" // "3.1.5"
val GRPCVersion = "1.9.0"
val MonocleVersion = "2.0.1"


lazy val root = project.in(file("."))
  .settings(
      name := "fabric-service-node"
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
      backend,
      service_chain_code,
      frontend
  )

lazy val backend = base.jvm
  .settings(name := "backend")
  .dependsOn(service_chain_code_model)


lazy val frontend = base.js
  .settings(name := "frontend")
  .disablePlugins(AssemblyPlugin)
  .enablePlugins(ScalaJSPlugin)

val BundlePath = file("service-node/frontend/bundle/js")

lazy val base = crossProject(JSPlatform, JVMPlatform)
  .crossType(BackendFrontendShared)
  .in(file("service-node"))
  .settings(
      libraryDependencies += "com.lihaoyi" %%% "upickle" % "0.9.5",
      libraryDependencies ++= Seq(
          "com.github.julien-truffaut" %%% "monocle-core" % MonocleVersion,
          "com.github.julien-truffaut" %%% "monocle-macro" % MonocleVersion,
          "com.github.julien-truffaut" %%% "monocle-law" % MonocleVersion % "test"
      ),

      addCompilerPlugin("org.scalamacros" %% "paradise" % "2.1.1" cross CrossVersion.full)
  )
  .jvmSettings(
      commonSettings,
      assemblySettings,
      libraryDependencies ++= FabricChainCodeClient ++ Jetty ++ DockerJava,
      libraryDependencies += "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.2",
      libraryDependencies += "com.github.oshi" % "oshi-core" % "5.0.1",

      mainClass in assembly := Some("org.enterprisedlt.fabric.service.node.ServiceNode"),
      assemblyJarName in assembly := "service-node.jar"
  )
  .jsSettings(
      //      name := "admin-console",
      scalacOptions ++= Seq("-P:scalajs:sjsDefinedByDefault"),
      scalaJSUseMainModuleInitializer := true,
      mainClass := Some("org.enterprisedlt.fabric.service.node.AdminConsole"),
      libraryDependencies ++= Seq(
          "org.scala-js" %%% "scalajs-dom" % "0.9.7",
          "com.github.japgolly.scalajs-react" %%% "core" % "1.6.0",
      ),
      jsDependencies ++= Seq(

          "org.webjars.npm" % "react" % "16.7.0"
            / "umd/react.development.js"
            minified "umd/react.production.min.js"
            commonJSName "React",

          "org.webjars.npm" % "react-dom" % "16.7.0"
            / "umd/react-dom.development.js"
            minified "umd/react-dom.production.min.js"
            dependsOn "umd/react.development.js"
            commonJSName "ReactDOM",

          "org.webjars.npm" % "react-dom" % "16.7.0"
            / "umd/react-dom-server.browser.development.js"
            minified "umd/react-dom-server.browser.production.min.js"
            dependsOn "umd/react-dom.development.js"
            commonJSName "ReactDOMServer"
      ),
      // Target files for Scala.js plugin
      Compile / fastOptJS / artifactPath := BundlePath / "admin-console.js",
      Compile / fullOptJS / artifactPath := BundlePath / "admin-console.js",
      Compile / packageJSDependencies / artifactPath := BundlePath / "admin-console-deps.js",
      Compile / packageMinifiedJSDependencies / artifactPath := BundlePath / "admin-console-deps.min.js",
  )

lazy val service_chain_code = project.in(file("./service-chain-code"))
  .settings(
      name := "service-chain-code",
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
      service_chain_code_model,
      service_chain_code_service
  )

lazy val service_chain_code_model = project.in(file("./service-chain-code/data-model"))
  .settings(
      name := "data-model",
      commonSettings
  )
  .disablePlugins(AssemblyPlugin)

lazy val service_chain_code_service = project.in(file("./service-chain-code/service"))
  .settings(
      name := "service",
      commonSettings,
      assemblySettings,
      libraryDependencies ++= FabricChainCodeScala,
      mainClass in assembly := Some("org.enterprisedlt.fabric.service.Main"),
      assemblyJarName in assembly := "chaincode.jar"
  )
  .dependsOn(service_chain_code_model)

//lazy val admin_console = project.in(file("admin-console"))
//  .settings(
//      name := "admin-console",
//      scalacOptions ++= Seq("-P:scalajs:sjsDefinedByDefault"),
//      scalaJSUseMainModuleInitializer := true,
//      mainClass := Some("org.enterprisedlt.fabric.service.node.AdminConsole"),
//      libraryDependencies ++= Seq(
//          "org.scala-js" %%% "scalajs-dom" % "0.9.7",
//          "com.github.japgolly.scalajs-react" %%% "core" % "1.6.0",
//          "com.lihaoyi" %%% "upickle" % "0.9.5"
//      ) ++ Monocle,
//      jsDependencies ++= Seq(
//
//          "org.webjars.npm" % "react" % "16.7.0"
//            / "umd/react.development.js"
//            minified "umd/react.production.min.js"
//            commonJSName "React",
//
//          "org.webjars.npm" % "react-dom" % "16.7.0"
//            / "umd/react-dom.development.js"
//            minified "umd/react-dom.production.min.js"
//            dependsOn "umd/react.development.js"
//            commonJSName "ReactDOM",
//
//          "org.webjars.npm" % "react-dom" % "16.7.0"
//            / "umd/react-dom-server.browser.development.js"
//            minified "umd/react-dom-server.browser.production.min.js"
//            dependsOn "umd/react-dom.development.js"
//            commonJSName "ReactDOMServer"
//      ),
//      // Target files for Scala.js plugin
//      Compile / fastOptJS / artifactPath := BundlePath / "admin-console.js",
//      Compile / fullOptJS / artifactPath := BundlePath / "admin-console.js",
//      Compile / packageJSDependencies / artifactPath := BundlePath / "admin-console-deps.js",
//      Compile / packageMinifiedJSDependencies / artifactPath := BundlePath / "admin-console-deps.js",
//
//      addCompilerPlugin("org.scalamacros" %% "paradise" % "2.1.1" cross CrossVersion.full)
//  )
//  .disablePlugins(AssemblyPlugin)
//  .enablePlugins(ScalaJSPlugin)

// ========================================================================
//
// ========================================================================

lazy val assemblySettings = Seq(
    assemblyMergeStrategy in assembly := {
        //concat all property files to one:
        case PathList("META-INF", xs@_*) if xs.lastOption.exists(_.endsWith(".properties")) => MergeStrategy.concat
        case x => (assemblyMergeStrategy in assembly).value(x)
    }
)

lazy val commonSettings = Seq(
    resolvers ++= Seq(
        Resolver.mavenLocal,
        Resolver.bintrayRepo("enterprisedlt", "general"),
        Resolver.bintrayRepo("enterprisedlt", "fabric")
    ),
    libraryDependencies ++= Seq(
        "org.slf4j" % "slf4j-api" % "1.7.25",
        "ch.qos.logback" % "logback-classic" % "1.2.3", // depends on logback-core
        // testing libraries
        "org.scalatest" %% "scalatest" % "3.0.5" % Test
    )
)

lazy val FabricChainCodeClient = Seq(
    ("org.enterprisedlt.fabric" % "fabric-chaincode-client" % FabricChainCodeClientVersion)
      .exclude("org.hyperledger.fabric-sdk-java", "fabric-sdk-java")
      .exclude("org.enterprisedlt.general", "codecs"),
    ("org.hyperledger.fabric-sdk-java" % "fabric-sdk-java" % "1.4.0")
      .exclude("commons-logging", "commons-logging")
      .exclude("io.grpc", "*")
      .exclude("io.netty", "netty-codec-http2")
      .exclude("com.google.protobuf", "protobuf-java")
      .exclude("com.google.protobuf", "protobuf-java-util")
      .exclude("org.miracl.milagro.amcl", "milagro-crypto-java")
      .exclude("org.bouncycastle", "bcprov-jdk15on")
      .exclude("org.bouncycastle", "bcpkix-jdk15on")
) ++ Codecs ++ GRPCCore ++ BouncyCastle

lazy val FabricChainCodeScala = Seq(
    ("org.enterprisedlt.fabric.chaincode" % "fabric-chaincode-scala" % FabricChainCodeScalaVersion)
      .exclude("commons-logging", "commons-logging")
      .exclude("io.grpc", "*")
      .exclude("io.netty", "netty-codec-http2")
      .exclude("com.google.protobuf", "protobuf-java")
      .exclude("com.google.protobuf", "protobuf-java-util")
      .exclude("org.miracl.milagro.amcl", "milagro-crypto-java")
      .exclude("org.bouncycastle", "bcprov-jdk15on")
      .exclude("org.bouncycastle", "bcpkix-jdk15on")
      .exclude("org.enterprisedlt.general", "codecs")
) ++ Codecs ++ GRPCCore ++ BouncyCastle

lazy val GRPCCore = Seq(
    "io.grpc" % "grpc-protobuf" % GRPCVersion,
    "io.grpc" % "grpc-stub" % GRPCVersion,
    "io.grpc" % "grpc-netty" % GRPCVersion,
    "io.grpc" % "grpc-netty-shaded" % GRPCVersion
)

lazy val Codecs = Seq(
    ("org.enterprisedlt.general" % "codecs" % CodecsVersion)
      .exclude("com.google.protobuf", "protobuf-java")
)

lazy val BouncyCastle = Seq(
    "org.bouncycastle" % "bcprov-jdk15on" % BouncyCastleVersion,
    "org.bouncycastle" % "bcpkix-jdk15on" % BouncyCastleVersion
)

lazy val Jetty = Seq(
    "org.eclipse.jetty" % "jetty-server" % JettyVersion,
    "org.eclipse.jetty" % "jetty-client" % JettyVersion,
    "org.eclipse.jetty.websocket" % "websocket-server" % JettyVersion
)

lazy val DockerJava = Seq(
    "com.github.docker-java" % "docker-java-api" % DockerApiVersion,
    "com.github.docker-java" % "docker-java-core" % DockerApiVersion,
    "com.github.docker-java" % "docker-java-transport-okhttp" % DockerApiVersion
    //      "docker-java-transport-netty" % DockerApiVersion
)

lazy val Monocle = Seq(
    "com.github.julien-truffaut" %% "monocle-core" % MonocleVersion,
    "com.github.julien-truffaut" %% "monocle-macro" % MonocleVersion,
    "com.github.julien-truffaut" %% "monocle-law" % MonocleVersion % "test"
)
