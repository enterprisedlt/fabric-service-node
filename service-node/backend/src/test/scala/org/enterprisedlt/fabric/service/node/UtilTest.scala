package org.enterprisedlt.fabric.service.node

import java.io.{BufferedOutputStream, File, FileInputStream, FileOutputStream}

import org.apache.commons.compress.archivers.tar.{TarArchiveEntry, TarArchiveOutputStream}
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.utils.IOUtils
import org.scalatest._

/**
 * @author Andrew Pudovikov
 */
class UtilTest extends FunSuite {

    def tarFile(file: File): File = {
        val tarZippedFile: File = File.createTempFile("tar-zipped-file", ".tgz")
        val tarOut = new TarArchiveOutputStream(
            new GzipCompressorOutputStream(
                new BufferedOutputStream(
                    new FileOutputStream(tarZippedFile))))
        tarOut.putArchiveEntry(new TarArchiveEntry(file, file.getName));
        IOUtils.copy(new FileInputStream(file), tarOut);
        tarOut.closeArchiveEntry()
        tarOut.close()
        tarZippedFile
    }


//    test("untarFile method should work fine") {
//        val tempFolder = Files.createTempDirectory("temp-dir").toFile
//        val tempZipFolder = Files.createTempDirectory("temp-zip-dir").toFile
//        val initialFile = File.createTempFile("test", ".txt", tempFolder)
//        initialFile.deleteOnExit()
//        //
//        val out = new FileOutputStream(initialFile)
//        out.write("Test message".getBytes(StandardCharsets.UTF_8))
//        out.close()
//        //
//        val zippedFile: File = tarFile(initialFile)
//        val zippedFileBytes = Files.readAllBytes(Paths.get(zippedFile.getPath))
//        //
//        untarFile(zippedFileBytes, tempZipFolder.getAbsolutePath)
//        //
//        val unzippedFileByteArray = Files.readAllBytes(Paths.get(tempZipFolder.listFiles().head.getAbsolutePath))
//        val unzippedFileContent = new String(unzippedFileByteArray, StandardCharsets.UTF_8)
//        //
//        val initialFileByteArray = Files.readAllBytes(Paths.get(initialFile.getPath))
//        val initialFileContent = new String(initialFileByteArray, StandardCharsets.UTF_8)
//        //
//        assert(unzippedFileContent == initialFileContent)
//    }
//
//
//    test("getFileFromTar should work fine") {
//
//        val initialMessage = "\"Test message\""
//        val tempFolder = Files.createTempDirectory("temp-dir").toFile
//        val initialFile = File.createTempFile("test", ".txt", tempFolder)
//        initialFile.deleteOnExit()
//        //
//        val out = new FileOutputStream(initialFile)
//        out.write(initialMessage.getBytes(StandardCharsets.UTF_8))
//        out.close()
//        //
//        val zippedFile: File = tarFile(initialFile)
//        val zippedFileBytes: Array[Byte] = Files.readAllBytes(Paths.get(zippedFile.getPath))
//        //
//        val filename = tempFolder.listFiles().head.getName
//
//        val text = getFileFromTar[String](zippedFileBytes, filename)
//
//        assert(text === Right(initialMessage.stripPrefix("\"").stripSuffix("\"")))
//    }


}
