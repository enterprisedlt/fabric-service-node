package org.enterprisedlt.fabric.service.node

import java.io._
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import org.apache.commons.compress.archivers.tar.{TarArchiveEntry, TarArchiveInputStream, TarArchiveOutputStream}
import org.apache.commons.compress.compressors.gzip.{GzipCompressorInputStream, GzipCompressorOutputStream}
import org.apache.commons.compress.utils.IOUtils
import org.enterprisedlt.fabric.service.node.Util.withResources
import org.enterprisedlt.fabric.service.node.shared.Property
import org.scalatest._
import org.scalatest.matchers.should.Matchers._

/**
 * @author Maxim Fedin
 */
class UtilTest extends FunSuite {

    def tarFile(file: File): File = {
        val tarZippedFile: File = File.createTempFile("tar-zipped-file", ".tgz")
        tarZippedFile.deleteOnExit()
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


    test("untarFile method should work fine") {
        val tempFolder = Files.createTempDirectory("temp-dir").toFile
        val tempZipFolder = Files.createTempDirectory("temp-zip-dir").toFile
        val initialFile = File.createTempFile("test", ".txt", tempFolder)
        tempFolder.deleteOnExit()
        tempZipFolder.deleteOnExit()
        //
        val out = new FileOutputStream(initialFile)
        out.write("Test message".getBytes(StandardCharsets.UTF_8))
        out.close()
        //
        val zippedFile: File = tarFile(initialFile)
        val zippedFileBytes = Files.readAllBytes(Paths.get(zippedFile.getPath))
        //
        Util.untarFile(zippedFileBytes, tempZipFolder.getAbsolutePath)
        //
        val unzippedFileByteArray = Files.readAllBytes(Paths.get(tempZipFolder.listFiles().head.getAbsolutePath))
        val unzippedFileContent = new String(unzippedFileByteArray, StandardCharsets.UTF_8)
        //
        val initialFileByteArray = Files.readAllBytes(Paths.get(initialFile.getPath))
        val initialFileContent = new String(initialFileByteArray, StandardCharsets.UTF_8)
        //
        assert(unzippedFileContent == initialFileContent)
    }


    test("getFileFromTar should work fine") {
        val initialMessage = "Test message"
        val initialMessageJson = Util.codec.toJson(initialMessage)
        val tempFolder = Files.createTempDirectory("temp-dir").toFile
        val initialFile = File.createTempFile("test", ".txt", tempFolder)
        tempFolder.deleteOnExit()
        //
        val out = new FileOutputStream(initialFile)
        out.write(initialMessageJson.getBytes(StandardCharsets.UTF_8))
        out.close()
        //
        val filename = tempFolder.listFiles().head.getName
        val zippedFile: File = tarFile(initialFile)
        //
        val text = withResources(
            new TarArchiveInputStream(
                new GzipCompressorInputStream(
                    Files.newInputStream(Paths.get(zippedFile.getPath))
                )
            )
        ) { inputStream =>
            Util.findInTar(inputStream, filename)(descriptorInputStream =>
                Util.codec.fromJson(new InputStreamReader(descriptorInputStream), classOf[String])
            )
        }
        assert(text === Some(initialMessage))
    }


    test("fillPlaceholdersEnvironmentVariables should work fine") {

        val targetProperty = Array(
            Property("a", "1"),
            Property("b", "2"),
            Property("c", "3")
        )

        val values = Array(
            Property("a", "1"),
            Property("b", "2"),
            Property("c", "3")
        )

        val dictionary = Array(
            Property("a", "1"),
            Property("b", s"$${b}"),
            Property("c", s"$${c}")
        )

        val filledProperty = Util.fillPlaceholdersEnvironmentVariables(values, dictionary)

        filledProperty shouldEqual  targetProperty

    }


}
