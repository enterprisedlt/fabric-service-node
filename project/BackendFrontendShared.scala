import sbt.{File, RichFile}
import sbtcrossproject.CrossPlugin.autoImport.{CrossType, JVMPlatform}
import sbtcrossproject.Platform
import scalajscrossproject.ScalaJSCrossPlugin.autoImport.JSPlatform

/**
 * @author Alexey Polubelov
 */
object BackendFrontendShared extends CrossType {
    override def projectDir(crossBase: File, projectType: String): File = projectType match {
        case "js" => new File(crossBase, "frontend")
        case "jvm" => new File(crossBase, "backend")
        case other => throw new Exception(s"Unsupported projectType for this CrossType: $other")
    }

    override def projectDir(crossBase: File, platform: Platform): File = platform match {
        case JSPlatform => new File(crossBase, "frontend")
        case JVMPlatform => new File(crossBase, "backend")
        case other => throw new Exception(s"Unsupported platform for this CrossType: ${other.identifier}")
    }

    override def sharedSrcDir(projectBase: File, conf: String): Option[File] = {
        Option(new File(projectBase.getParentFile, s"shared/src/$conf/scala"))
    }
}