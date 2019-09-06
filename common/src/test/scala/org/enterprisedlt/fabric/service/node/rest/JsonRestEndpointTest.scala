
import org.enterprisedlt.fabric.service.node.common.JsonRestEndpoint
import org.enterprisedlt.fabric.service.node.rest.{Get, Post}

/**
  * @author Alexey Polubelov
  */
object JREpTest extends App {
    println("Starting")
    val endpoint = new JsonRestEndpoint(7777, TestP)
    endpoint.start()

}

object TestP {

    @Get("/test")
    def test(p: Int): Either[String, Long] = if (p > 5) Right(p) else Left("less then 5")

    @Post("/p")
    def testP(x: String): Either[String, String] = Right(x)
}
