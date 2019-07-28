package org.enterprisedlt.fabric.service.node

import java.io.{File, FileInputStream, InputStream}

object Tail {


    def follow(file: File): InputStream = {
        val maxRetries = 3
        val waitToOpen = 1000
        val waitBetweenReads = 100

        def sleep(msec: Long): () => Unit = () => Thread.sleep(msec)

        follow(file, maxRetries, sleep(waitToOpen), sleep(waitBetweenReads))
    }

    def follow(file: File, openTries: Int, openSleep: () => Unit, rereadSleep: () => Unit): InputStream = {
        import java.io.SequenceInputStream

        val e = new java.util.Enumeration[InputStream]() {
            def nextElement = new FollowingInputStream(file, rereadSleep)

            def hasMoreElements: Boolean = testExists(file, openTries, openSleep)
        }

        new SequenceInputStream(e)
    }

    def testExists(file: File, tries: Int, sleep: () => Unit): Boolean = {
        def tryExists(n: Int): Boolean =
            if (file.exists) true
            else if (n > tries) false
            else {
                sleep()
                tryExists(n + 1)
            }

        tryExists(1)
    }
}

class FollowingInputStream(val file: File, val waitNewInput: () => Unit) extends InputStream {
    private val underlying = new FileInputStream(file)

    def read: Int = handle(underlying.read)

    override def read(b: Array[Byte]): Int = read(b, 0, b.length)

    override def read(b: Array[Byte], off: Int, len: Int): Int = handle(underlying.read(b, off, len))

    override def close(): Unit = underlying.close

    protected def rotated_? : Boolean = try {
        underlying.getChannel.position > file.length
    }
    finally {
        false
    }

    protected def closed_? : Boolean = !underlying.getChannel.isOpen

    protected def handle(read: => Int): Int = read match {
        case -1 if rotated_? || closed_? => -1
        case -1 =>
            waitNewInput()
            handle(read)
        case i => i
    }

    require(file != null)
    assume(file.exists)
}
