package com.olegych.scastie

import java.security.Permission
import java.io.{File, FilePermission}

/**
  */
object ScriptSecurityManager extends SecurityManager {
  System.setProperty("actors.enableForkJoin", false + "")
  val sm = System.getSecurityManager
  var activated = false

  override def checkPermission(perm: Permission) {
    if (activated) {
      val read = perm.getActions == ("read")
      val allowedMethods = Seq("accessDeclaredMembers", "suppressAccessChecks", "createClassLoader",
        "accessClassInPackage.sun.reflect", "getStackTrace").contains(perm.getName)
      val file = perm.isInstanceOf[FilePermission]

      deactivate
      val notExistingFile = !new File(perm.getName).exists()

      val allowedFiles =
        Seq(""".*\.class""", """.*\.jar""", """.*classes.*""", """.*library\.properties""", """.*src/main/scala.*""")
      val isClass = allowedFiles.exists(perm.getName.replaceAll("\\\\", "/").matches(_))
      activate

      val readClass = file && isClass && read
      val readMissingFile = file && notExistingFile && read
      lazy val allowedClass = new Throwable().getStackTrace.exists { element =>
        val name = element.getFileName
        //todo apply more robust checks
        List("BytecodeWriters.scala", "Settings.scala", "PathResolver.scala").contains(name)
      }

      val allow = readMissingFile || readClass || (read && !file) || allowedMethods || allowedClass
      if (!allow) {
        throw new SecurityException(perm.toString)
      }
    } else {
      if (sm != null) {
        sm.checkPermission(perm)
      }
    }

  }

  def deactivate {
    activated = false
    System.setSecurityManager(sm)
  }

  def activate {
    System.setSecurityManager(this)
    activated = true
  }

  def hardenPermissions[T](f: => T): T = this.synchronized {
    try {
      activate
      f
    } finally {
      deactivate
    }
  }
}