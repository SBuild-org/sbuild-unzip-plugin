package org.sbuild.plugins.unzip

import org.sbuild._

class UnzipPlugin(implicit project: Project) extends Plugin[Unzip] {

  def create(name: String): Unzip = {
    val schemeName = if (name == "") "zip" else name
    val baseDir = Path(s".sbuild/${schemeName}")
    Unzip(schemeName = schemeName,
      baseDir = baseDir)
  }

  def applyToProject(instances: Seq[(String, Unzip)]): Unit = instances foreach {
    case (name, unzip) =>
      SchemeHandler(unzip.schemeName, new UnzipSchemeHandler(unzip.baseDir, unzip.regexCacheable))
  }

}