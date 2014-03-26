package org.sbuild.plugins.unzip.internal

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.text.DecimalFormat
import java.util.zip.ZipInputStream

import scala.Array.canBuildFrom
import scala.util.matching.Regex

import org.sbuild.CmdlineMonitor
import org.sbuild.Logger
import org.sbuild.NoopCmdlineMonitor
import org.sbuild.RichFile
import org.sbuild.SBuildException

object Util extends Util

class Util {

  private[this] val log = Logger[Util.type]
  private[sbuild] var monitor: CmdlineMonitor = NoopCmdlineMonitor

  def unzip(archive: File, targetDir: File, selectedFiles: String*) {
    unzip(archive, targetDir, selectedFiles.map(f => (f, null)).toList, monitor, None)
  }

  def unzip(archive: File, targetDir: File, _selectedFiles: List[(String, File)], monitor: CmdlineMonitor) {
    unzip(archive, targetDir, _selectedFiles, monitor, None)
  }

  /**
   * Extract files from a ZIP archive.
   * If the list of `selectedFiles` is empty and no `fileSelector` was given, than all files will be extracted.
   *
   * @param archive The file denoting a ZIP archive.
   * @param targetDir The base directory, where the extracted files will be stored.
   * @param selectedFiles A list of name-file pairs denoting which archive content should be extracted into which file.
   *   The name if the path inside the archive.
   *   The file will be the place that file will be extracted to.
   *   If the file value is `null`, that the file will be extracted into the `targetDir` without any sub directory created.
   *   @param monitor A `[CmdlineMonitor]` to report this functions progress and messages.
   * @param fileSelector A filter used to decide if a file in the archive should be extracted or not.
   *   `fileSelector` is not able to exclude files already selected with `selectedFiles`.
   *   If a selector is given (`[scala.Some]`), that only those files will be extracted, for which the selector returns `true`.
   *
   * @return A `Seq` of all extracted files.
   *
   * @since 0.7.1.9000
   */
  def unzip(archive: File, targetDir: File, selectedFiles: List[(String, File)], monitor: CmdlineMonitor, fileSelector: Option[String => Boolean]): Seq[File] = {

    if (!archive.exists || !archive.isFile) throw new FileNotFoundException("Zip file cannot be found: " + archive);
    targetDir.mkdirs

    monitor.info(CmdlineMonitor.Verbose, "Extracting zip archive '" + archive + "' to: " + targetDir)

    val partial = !selectedFiles.isEmpty || fileSelector.isDefined
    if (partial) log.debug("Only extracting some content of zip file")

    var filesToExtract = selectedFiles
    var extractedFilesInv: List[File] = Nil

    try {
      val zipIs = new ZipInputStream(new FileInputStream(archive))
      var zipEntry = zipIs.getNextEntry
      val finished = partial && fileSelector.isEmpty && filesToExtract.isEmpty
      while (zipEntry != null && !finished) {
        val extractFile: Option[File] = if (partial) {
          if (!zipEntry.isDirectory) {
            val candidate = filesToExtract.find { case (name, _) => name == zipEntry.getName }
            if (candidate.isDefined) {
              filesToExtract = filesToExtract.filterNot(_ == candidate.get)
              if (candidate.get._2 != null) {
                Some(candidate.get._2)
              } else {
                val full = zipEntry.getName
                val index = full.lastIndexOf("/")
                val name = if (index < 0) full else full.substring(index)
                Some(new File(targetDir + "/" + name))
              }
            } else {
              fileSelector match {
                case None => None
                case Some(s) => s(zipEntry.getName) match {
                  case false => None
                  case true => Some(new File(targetDir + "/" + zipEntry.getName))
                }
              }
            }
          } else {
            None
          }
        } else {
          if (zipEntry.isDirectory) {
            monitor.info(CmdlineMonitor.Verbose, "  Creating " + zipEntry.getName);
            new File(targetDir + "/" + zipEntry.getName).mkdirs
            None
          } else {
            Some(new File(targetDir + "/" + zipEntry.getName))
          }
        }

        if (extractFile.isDefined) {
          monitor.info(CmdlineMonitor.Verbose, "  Extracting " + zipEntry.getName);
          val targetFile = extractFile.get
          if (targetFile.exists
            && !targetFile.getParentFile.isDirectory) {
            throw new RuntimeException(
              "Expected directory is a file. Cannot extract zip content: "
                + zipEntry.getName);
          }
          // Ensure, that the directory exixts
          targetFile.getParentFile.mkdirs
          val outputStream = new BufferedOutputStream(new FileOutputStream(targetFile))
          copy(zipIs, outputStream);
          outputStream.close
          extractedFilesInv ::= targetFile
          if (zipEntry.getTime > 0) {
            targetFile.setLastModified(zipEntry.getTime)
          }
        }

        zipEntry = zipIs.getNextEntry()
      }

      zipIs.close
    } catch {
      case e: IOException =>
        throw new RuntimeException("Could not unzip file: " + archive,
          e)
    }

    if (!filesToExtract.isEmpty) {
      throw new FileNotFoundException(s"""Could not found file "${filesToExtract.head._1}" in zip archive "${archive}".""")
    }

    extractedFilesInv.reverse
  }

  def copy(in: InputStream, out: OutputStream) {
    val buf = new Array[Byte](1024)
    var len = 0
    while ({
      len = in.read(buf)
      len > 0
    }) {
      out.write(buf, 0, len)
    }
  }

}