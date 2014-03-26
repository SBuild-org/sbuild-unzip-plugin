package org.sbuild.plugins.unzip

import java.io.File

case class Unzip(
    schemeName: String,
    baseDir: File,
    regexCacheable: Boolean = true) {

  override def toString = getClass.getSimpleName() +
    "(schemeName=" + schemeName +
    ",baseDir=" + baseDir +
    ",regexCacheable=" + regexCacheable +
    ")"

}
