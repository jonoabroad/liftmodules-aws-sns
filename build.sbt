name := "aws-sns"

version := "2.5-0.0.3"

organization := "net.liftmodules"
 
scalaVersion := "2.9.2" 

crossScalaVersions := Seq("2.9.1","2.9.2")
 
resolvers += "Java.net Maven2 Repository" at "http://download.java.net/maven/2/"

resolvers += "snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"

libraryDependencies ++= {
  val liftVersion = "2.5" 
  Seq("net.liftweb"     %% "lift-webkit"  % liftVersion % "compile->default" )
}

// Customize any further dependencies as desired
libraryDependencies ++= Seq(
  "javax.servlet" % "servlet-api" % "2.5" % "provided->default",
  "ch.qos.logback" % "logback-classic" % "1.0.6" % "compile->default", // Logging
  "com.amazonaws" % "aws-java-sdk" % "1.3.13"
 )

 // To publish to the Cloudbees repos:

publishTo := Some("liftmodules repository" at "https://repository-liftmodules.forge.cloudbees.com/release/")
 
credentials += Credentials( file("/private/liftmodules/cloudbees.credentials") )

