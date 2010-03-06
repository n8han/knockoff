import sbt._

class KnockoffProject( info : ProjectInfo )
extends DefaultProject( info )
with    KnockoffLiterableProject {
  
  override def compileOptions = {
    List( MaxCompileErrors( 5 ), CompileOption("-unchecked") ) :::
    super.compileOptions.toList
  }
  
  override def mainClass = Some("com.tristanhunt.knockoff.DefaultDiscounter")
  
  Credentials(Path.userHome / ".ivy2" / ".credentials", log)

  val nexus = "tristanhunt" at "http://tristanhunt.com:8081/content/groups/public/"

  val ScalaTest = "org.scala-tools.testing" % "scalatest" % "0.9.5"
  
  override def managedStyle = ManagedStyle.Maven
  val publishTo = "tristanhunt releases" at
    "http://tristanhunt.com:8081/content/repositories/releases/"
}